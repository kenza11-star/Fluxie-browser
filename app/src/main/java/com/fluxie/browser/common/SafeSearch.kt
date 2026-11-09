package com.fluxie.browser.common

/**
 * Adds each supported engine's "strict" SafeSearch parameter to its results-page URLs.
 * Only these engines are handled (by host and path); any other site, and search engines used
 * through a custom template that is not one of these hosts, are returned unchanged.
 */
object SafeSearch {

    private class Engine(val param: String, val value: String, val isResults: (host: String, path: String) -> Boolean)

    private val GOOGLE_HOST = Regex("^(www\\.)?google\\.[a-z]{2,3}(\\.[a-z]{2})?$")

    private val ENGINES = listOf(
        Engine("safe", "active") { host, path -> GOOGLE_HOST.matches(host) && path == "/search" },
        Engine("adlt", "strict") { host, path -> (host == "bing.com" || host == "www.bing.com") && path == "/search" },
        Engine("kp", "1") { host, path ->
            (host == "duckduckgo.com" || host == "www.duckduckgo.com") && (path == "/" || path == "/html" || path == "/html/")
        },
        Engine("safesearch", "strict") { host, path -> host == "search.brave.com" && path == "/search" }
    )

    /** [url] with SafeSearch forced on when it is a supported results page, else [url] unchanged. */
    fun apply(url: String): String {
        val host = DomainRules.hostOf(url) ?: return url
        val hashIndex = url.indexOf('#')
        val fragment = if (hashIndex >= 0) url.substring(hashIndex) else ""
        val withoutFragment = if (hashIndex >= 0) url.substring(0, hashIndex) else url

        val queryIndex = withoutFragment.indexOf('?')
        if (queryIndex < 0) return url // a results page always carries a query
        val base = withoutFragment.substring(0, queryIndex)
        val query = withoutFragment.substring(queryIndex + 1)
        val path = "/" + base.substringAfter("://").substringAfter('/', "")

        val engine = ENGINES.firstOrNull { it.isResults(host, path) } ?: return url
        val params = query.split('&').filter { it.isNotEmpty() }
        if (params.none { it.substringBefore('=') == "q" }) return url

        val current = params.firstOrNull { it.substringBefore('=') == engine.param }
        if (current == "${engine.param}=${engine.value}") return url
        val kept = params.filter { it.substringBefore('=') != engine.param }
        return base + "?" + (kept + "${engine.param}=${engine.value}").joinToString("&") + fragment
    }
}

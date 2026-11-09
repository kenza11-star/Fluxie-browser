package com.fluxie.browser.common

import java.net.URLEncoder

enum class SearchEngine(val displayName: String, val searchUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q=");

    /** The engine as a template: the search words replace [SearchTemplate.PLACEHOLDER]. */
    val template: String get() = searchUrl + SearchTemplate.PLACEHOLDER

    companion object {
        fun fromName(name: String?): SearchEngine? = entries.firstOrNull { it.name == name }
    }
}

/** Stored choice meaning "use the user's own URL template". */
const val CUSTOM_SEARCH = "CUSTOM"

object SearchTemplate {
    const val PLACEHOLDER = "%s"

    /** A usable template is an http(s) URL with a host that contains the placeholder. */
    fun isValid(template: String): Boolean {
        val t = template.trim()
        if (t.any { it.isWhitespace() } || !t.contains(PLACEHOLDER)) return false
        val rest = when {
            t.startsWith("https://", ignoreCase = true) -> t.substring(8)
            t.startsWith("http://", ignoreCase = true) -> t.substring(7)
            else -> return false
        }
        val host = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        // The placeholder may not be part of the host: that would send queries to arbitrary hosts.
        return host.isNotEmpty() && !host.contains(PLACEHOLDER)
    }

    /** Substitutes the URL-encoded [query] for every placeholder. */
    fun build(template: String, query: String): String =
        template.trim().replace(PLACEHOLDER, URLEncoder.encode(query, "UTF-8"))
}

/**
 * The template to search with for a stored [choice] (a [SearchEngine] name or [CUSTOM_SEARCH]).
 * Unknown choices and invalid custom templates fall back to Google rather than breaking search.
 */
fun resolveSearchTemplate(choice: String?, customTemplate: String?): String {
    if (choice == CUSTOM_SEARCH && customTemplate != null && SearchTemplate.isValid(customTemplate)) {
        return customTemplate.trim()
    }
    return (SearchEngine.fromName(choice) ?: SearchEngine.GOOGLE).template
}

/** Short name for the address bar hint: the engine name, or the custom template's domain. */
fun searchDisplayName(choice: String?, customTemplate: String?): String {
    if (choice == CUSTOM_SEARCH && customTemplate != null && SearchTemplate.isValid(customTemplate)) {
        return UrlUtils.extractDomain(customTemplate)
    }
    return (SearchEngine.fromName(choice) ?: SearchEngine.GOOGLE).displayName
}

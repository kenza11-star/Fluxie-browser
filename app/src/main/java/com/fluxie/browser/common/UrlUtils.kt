package com.fluxie.browser.common

/**
 * Pure-JVM URL helpers (no android.* dependencies) so they can be unit-tested without Robolectric.
 */
object UrlUtils {

    private val HTTP_SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)
    private val DOMAIN_LABEL = Regex("^[\\p{L}\\p{N}]([\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?$")
    private val TLD = Regex("^([\\p{L}]{2,}|xn--[a-z0-9-]+)$", RegexOption.IGNORE_CASE)
    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val PORT = Regex("^\\d{1,5}$")

    /**
     * Turns whatever the user typed into something a WebView can load:
     *  - explicit http(s) URLs are kept as typed (trimmed);
     *  - host-like input ("example.com", "example.com/a?b", "localhost:3000", "192.168.1.5")
     *    gets a scheme (https for domains, http for localhost / IP addresses);
     *  - everything else is URL-encoded and sent to [searchEngine].
     * Blank input returns "" — callers must not load an empty string.
     */
    fun resolveInput(input: String, searchEngine: SearchEngine): String =
        resolveInput(input, searchEngine.template)

    /** Same as above, searching with an arbitrary [searchTemplate] (see [SearchTemplate]). */
    fun resolveInput(input: String, searchTemplate: String): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        return asUrlOrNull(text) ?: SearchTemplate.build(searchTemplate, text)
    }

    /**
     * The loadable URL for [input] if it is one (explicit http(s) URL or a host-like string),
     * or null if it is search text. Used to validate a homepage.
     */
    fun asUrlOrNull(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        if (HTTP_SCHEME.containsMatchIn(text)) {
            val afterScheme = text.substringAfter("://")
            return if (afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }.isEmpty()) null else text
        }
        val kind = hostKind(text) ?: return null
        return (if (kind == HostKind.LOCAL) "http://" else "https://") + text
    }

    private enum class HostKind { DOMAIN, LOCAL }

    /** Classifies the authority part of scheme-less input, or null if it is not a host. */
    private fun hostKind(text: String): HostKind? {
        val authority = text.takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isEmpty() || authority.contains('@')) return null

        if (authority.startsWith("[")) { // IPv6 literal: [::1] or [::1]:8080
            val end = authority.indexOf(']')
            if (end < 0) return null
            val rest = authority.substring(end + 1)
            if (rest.isNotEmpty() && !(rest.startsWith(":") && PORT.matches(rest.drop(1)))) return null
            return if (authority.substring(1, end).contains(':')) HostKind.LOCAL else null
        }
        val colon = authority.lastIndexOf(':')
        val host = if (colon >= 0) {
            if (!PORT.matches(authority.substring(colon + 1))) return null
            authority.substring(0, colon)
        } else {
            authority
        }
        if (host.isEmpty()) return null

        if (host.equals("localhost", ignoreCase = true)) return HostKind.LOCAL
        IPV4.matchEntire(host)?.let { m ->
            return if (m.groupValues.drop(1).all { it.toInt() in 0..255 }) HostKind.LOCAL else null
        }

        val labels = host.split('.')
        if (labels.size < 2) return null
        if (!labels.all { DOMAIN_LABEL.matches(it) }) return null
        return if (TLD.matches(labels.last())) HostKind.DOMAIN else null
    }

    /**
     * The host to show in the address bar, without a leading "www."/"m.".
     * Returns "" for blank input and the trimmed input if it cannot be parsed as a URL.
     */
    fun extractDomain(url: String): String {
        val text = url.trim()
        if (text.isEmpty()) return ""
        val withoutScheme = text.substringAfter("://", text)
        var authority = withoutScheme.takeWhile { it != '/' && it != '?' && it != '#' }
        authority = authority.substringAfterLast('@')
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            return if (end > 0) authority.substring(0, end + 1) else authority
        }
        // Drop the port, but only when what follows the last ':' really is a port.
        val portIndex = authority.lastIndexOf(':')
        val host = if (portIndex >= 0 && PORT.matches(authority.substring(portIndex + 1))) {
            authority.substring(0, portIndex)
        } else {
            authority
        }
        return when {
            host.startsWith("www.") -> host.removePrefix("www.")
            host.startsWith("m.") && host.count { it == '.' } >= 2 -> host.removePrefix("m.")
            else -> host
        }
    }

    /**
     * Simplify a URL for address-bar display (like Chrome):
     * strip scheme, trivial subdomains (www., m.), and lone trailing slash.
     */
    fun simplifyForDisplay(url: String): String {
        var result = url
            .removePrefix("https://")
            .removePrefix("http://")
        if (result.startsWith("www.")) {
            result = result.removePrefix("www.")
        } else if (result.startsWith("m.")) {
            result = result.removePrefix("m.")
        }
        if (result.endsWith("/") && result.count { it == '/' } == 1) {
            result = result.removeSuffix("/")
        }
        return result
    }
}

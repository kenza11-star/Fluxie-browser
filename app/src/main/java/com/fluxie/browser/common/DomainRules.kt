package com.fluxie.browser.common

import java.net.IDN

/**
 * Pure-JVM domain helpers shared by the blocklist, the importer and the request filter.
 * A rule for "example.com" covers "example.com" and every subdomain ("ads.example.com"),
 * but not look-alikes such as "notexample.com".
 */
object DomainRules {

    private val LABEL = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
    private val TLD = Regex("^([a-z]{2,}|xn--[a-z0-9-]+)$")

    /**
     * Turns one typed or imported entry into a canonical rule, or null if it is not a domain.
     * Accepts "example.com", "*.example.com", ".example.com", "https://example.com/path?x",
     * "example.com:8080" and Unicode names (stored as punycode). IP addresses are rejected.
     */
    fun normalize(raw: String): String? {
        var text = raw.trim().lowercase()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        text = text.substringAfter("://", text)
        text = text.takeWhile { it != '/' && it != '?' && it != '#' }
        text = text.substringAfterLast('@')
        text = text.removePrefix("*.").removePrefix(".")
        // Drop a port, but only when what follows the last ':' is digits.
        val colon = text.lastIndexOf(':')
        if (colon >= 0) {
            if (!text.substring(colon + 1).all { it.isDigit() } || colon == text.length - 1) return null
            text = text.substring(0, colon)
        }
        text = text.trimEnd('.')
        if (text.isEmpty() || text.length > 253) return null
        val ascii = try {
            IDN.toASCII(text)
        } catch (_: IllegalArgumentException) {
            return null
        }.lowercase()
        val labels = ascii.split('.')
        if (labels.size < 2) return null
        if (labels.any { it.length > 63 || !LABEL.matches(it) }) return null
        return if (TLD.matches(labels.last())) ascii else null
    }

    /** The lower-case host of an http(s) URL, or null for other schemes / unparseable input. */
    fun hostOf(url: String): String? {
        val text = url.trim()
        val scheme = text.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return null
        var authority = text.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
        authority = authority.substringAfterLast('@')
        if (authority.startsWith("[")) return null // IPv6 literal: never a listed domain
        val colon = authority.lastIndexOf(':')
        if (colon >= 0 && authority.substring(colon + 1).all { it.isDigit() }) {
            authority = authority.substring(0, colon)
        }
        authority = authority.trimEnd('.').lowercase()
        return authority.takeIf { it.isNotEmpty() }
    }

    /** True if [host] or any parent domain of it is in [rules]. */
    fun matches(host: String, rules: Set<String>): Boolean {
        if (rules.isEmpty()) return false
        var candidate = host
        while (true) {
            if (candidate in rules) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }
}

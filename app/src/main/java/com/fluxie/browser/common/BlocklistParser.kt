package com.fluxie.browser.common

/** Result of reading a blocklist text: the distinct valid domains plus how many lines were unusable. */
data class ParsedBlocklist(val domains: List<String>, val skippedLines: Int)

/**
 * Reads a blocklist from plain text. One entry per line; supported line formats:
 *  - `example.com` (also `*.example.com`, `https://example.com/`)
 *  - hosts-file style `0.0.0.0 example.com` / `127.0.0.1 a.com b.com`
 *  - Adblock-style domain anchors `||example.com^`
 * Blank lines and `#` / `!` comments (whole line or trailing `#...`) are ignored silently.
 * Any other line (cosmetic filters, path rules, exceptions, garbage) is counted as skipped:
 * it is NOT interpreted, so nothing is blocked that the user did not clearly list.
 */
object BlocklistParser {

    private val TRAILING_COMMENT = Regex("\\s+#.*$")
    private val SINKHOLE_ADDRESSES = setOf("0.0.0.0", "127.0.0.1", "::1", "::", "0:0:0:0:0:0:0:0")

    fun parse(text: String): ParsedBlocklist {
        val seen = LinkedHashSet<String>()
        var skipped = 0
        for (rawLine in text.lineSequence()) {
            var line = rawLine.trim().removePrefix("﻿")
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue
            // A trailing comment needs whitespace before '#': `example.com##.ad` is a cosmetic filter, not a domain.
            line = line.replace(TRAILING_COMMENT, "").trim()
            if (line.isEmpty()) continue

            val entries = entriesOf(line)
            if (entries == null) {
                skipped++
            } else {
                seen.addAll(entries)
            }
        }
        return ParsedBlocklist(seen.toList(), skipped)
    }

    /** The normalized domains on [line], or null if the line is not a recognised, fully valid rule. */
    private fun entriesOf(line: String): List<String>? {
        val tokens = line.split(Regex("\\s+"))
        if (tokens.first() in SINKHOLE_ADDRESSES) {
            val domains = tokens.drop(1).filter { it.isNotEmpty() }
            // "0.0.0.0 localhost"-style housekeeping lines carry no real domain; not an error.
            val valid = domains.mapNotNull { normalizeStrict(it) }
            return if (valid.isNotEmpty() && valid.size == domains.size) valid else null
        }
        if (tokens.size != 1) return null
        var token = tokens[0]
        if (token.startsWith("||")) {
            token = token.removePrefix("||")
            token = if (token.endsWith("^")) token.removeSuffix("^") else return null
            // Anything beyond a bare host (path, wildcard, options) is a different rule type.
            if (token.any { it in "/*^$|?:" }) return null
        }
        return normalizeStrict(token)?.let { listOf(it) }
    }

    /**
     * Like [DomainRules.normalize] but only for entries that name a whole domain. A path, query or
     * fragment (`example.com/ads`, `example.com##.ad`) is a narrower rule type this app does not
     * implement, so it must not be widened into blocking the entire domain.
     */
    private fun normalizeStrict(token: String): String? {
        val afterScheme = token.substringAfter("://", token)
        val rest = afterScheme.dropWhile { it != '/' && it != '?' && it != '#' }
        if (rest.isNotEmpty() && rest != "/") return null
        if (afterScheme.any { it in "^$|,@" }) return null
        return DomainRules.normalize(token)
    }
}

package com.fluxie.browser.lite

data class LiteBookmark(val title: String, val url: String)

/**
 * Text format used to keep Lite bookmarks in one SharedPreferences string: one bookmark per line,
 * `title<TAB>url`, with backslash escapes for backslash, tab, newline and carriage return.
 * Unreadable lines are skipped rather than failing the whole list.
 */
object LiteBookmarkCodec {

    fun encode(items: List<LiteBookmark>): String =
        items.joinToString("\n") { escape(it.title) + "\t" + escape(it.url) }

    fun decode(text: String?): List<LiteBookmark> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split('\n').mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab < 0) return@mapNotNull null
            val url = unescape(line.substring(tab + 1))
            if (url.isBlank()) null else LiteBookmark(unescape(line.substring(0, tab)), url)
        }
    }

    private fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }

    private fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(n)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}

/** Which of [incoming] are not yet known by URL (first occurrence wins). Used to merge Lite and Normal bookmarks. */
fun missingBookmarks(knownUrls: Set<String>, incoming: List<LiteBookmark>): List<LiteBookmark> {
    val seen = HashSet(knownUrls)
    return incoming.filter { seen.add(it.url) }
}

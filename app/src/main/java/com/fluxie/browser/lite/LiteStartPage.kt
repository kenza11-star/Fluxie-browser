package com.fluxie.browser.lite

/** Minimal local start page for Lite mode: a heading and the user's bookmarks as links. */
object LiteStartPage {

    fun html(title: String, subtitle: String, emptyText: String, bookmarks: List<LiteBookmark>): String {
        val links = bookmarks.filter { isWebUrl(it.url) }.joinToString("") {
            "<a href=\"${esc(it.url)}\">${esc(it.title)}</a>"
        }
        val body = if (links.isEmpty()) "<p class=\"m\">${esc(emptyText)}</p>" else "<div class=\"l\">$links</div>"
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${esc(title)}</title><style>" +
            "body{margin:0;padding:32px 20px;background:#0a0a0b;color:#f2f2f3;font-family:sans-serif}" +
            "h1{font-size:24px;margin:0 0 4px}.m{color:#9a9aa0}" +
            ".l a{display:block;margin:10px 0;padding:14px 16px;background:#1c1c1f;border-radius:16px;" +
            "color:#f2f2f3;text-decoration:none;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}" +
            "</style></head><body><h1>${esc(title)}</h1><p class=\"m\">${esc(subtitle)}</p>$body</body></html>"
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}

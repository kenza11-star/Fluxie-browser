package com.fluxie.browser.lite

import android.content.SharedPreferences

/**
 * Lite bookmarks, kept in SharedPreferences (no database). Normal mode mirrors its own bookmarks
 * here too, so switching modes never loses them.
 */
class LiteBookmarkStore(private val prefs: SharedPreferences) {

    constructor(context: android.content.Context) : this(
        context.applicationContext.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
    )

    fun all(): List<LiteBookmark> = LiteBookmarkCodec.decode(prefs.getString(KEY, null))

    fun contains(url: String): Boolean = all().any { it.url == url }

    /** Adds the page; returns false if the URL is already bookmarked or the list is full. */
    fun add(title: String, url: String): Boolean {
        val current = all()
        if (current.size >= MAX_BOOKMARKS || current.any { it.url == url }) return false
        save(current + LiteBookmark(title.ifBlank { url }, url))
        // Bookmarking it again cancels an earlier, not yet applied removal.
        if (url in removedUrls()) prefs.edit().putStringSet(KEY_REMOVED, removedUrls() - url).commit()
        return true
    }

    fun remove(url: String) {
        save(all().filter { it.url != url })
        // Remembered so Normal mode can delete the same bookmark from its database next time it starts.
        prefs.edit().putStringSet(KEY_REMOVED, removedUrls() + url).commit()
    }

    /** URLs removed in Lite mode that Normal mode has not applied yet. */
    fun removedUrls(): Set<String> = HashSet(prefs.getStringSet(KEY_REMOVED, emptySet()) ?: emptySet())

    fun clearRemovedUrls() {
        prefs.edit().remove(KEY_REMOVED).commit()
    }

    fun replaceAll(items: List<LiteBookmark>) {
        save(items.take(MAX_BOOKMARKS))
    }

    private fun save(items: List<LiteBookmark>) {
        // commit(): a mode switch may follow immediately, and the new screen must see this write.
        prefs.edit().putString(KEY, LiteBookmarkCodec.encode(items)).commit()
    }

    companion object {
        const val FILE = "fluxie_lite_bookmarks"
        const val MAX_BOOKMARKS = 500
        private const val KEY = "bookmarks"
        private const val KEY_REMOVED = "removed_urls"
    }
}

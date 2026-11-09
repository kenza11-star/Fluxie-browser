package com.fluxie.browser.data

import com.fluxie.browser.lite.LiteBookmark
import com.fluxie.browser.lite.LiteBookmarkStore
import com.fluxie.browser.lite.missingBookmarks

/**
 * Keeps Normal mode's bookmarks (Room) and Lite mode's bookmarks (SharedPreferences) in step, so
 * switching modes never loses or resurrects a bookmark.
 *
 * Known limits: Lite is flat, so folders are not shown there and bookmarks added in Lite land in the
 * root; a bookmark removed in Lite is removed from every folder of Normal mode by URL.
 */
class LiteBookmarkSync(
    private val repository: BookmarkRepository,
    private val store: LiteBookmarkStore
) {
    /** Applies what happened in Lite mode since Normal mode last ran: removals first, then additions. */
    suspend fun mergeLiteChanges() {
        for (url in store.removedUrls()) {
            while (true) {
                val entry = repository.getBookmarkByUrl(url) ?: break
                repository.deleteEntry(entry.id)
            }
        }
        store.clearRemovedUrls()
        val known = repository.getAllBookmarks().mapNotNull { it.url }.toSet()
        for (item in missingBookmarks(known, store.all())) {
            repository.addBookmark(item.title, item.url)
        }
    }

    /** Writes the current Room bookmarks to the Lite store once. */
    suspend fun mirrorOnce() {
        write(repository.getAllBookmarks())
    }

    /** Keeps the Lite store current for as long as the caller's scope lives. */
    suspend fun mirror() {
        repository.observeAllBookmarks().collect { write(it) }
    }

    private fun write(entries: List<BookmarkEntry>) {
        store.replaceAll(entries.mapNotNull { e -> e.url?.let { LiteBookmark(e.title, it) } })
    }
}

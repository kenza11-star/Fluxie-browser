package com.fluxie.browser.lite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiteBookmarkStoreTest {

    private lateinit var store: LiteBookmarkStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(LiteBookmarkStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        store = LiteBookmarkStore(context)
    }

    @Test fun addAndListKeepsOrder() {
        assertTrue(store.add("A", "https://a.com"))
        assertTrue(store.add("B", "https://b.com"))
        assertEquals(listOf(LiteBookmark("A", "https://a.com"), LiteBookmark("B", "https://b.com")), store.all())
    }

    @Test fun duplicateUrlIsRejected() {
        assertTrue(store.add("A", "https://a.com"))
        assertFalse(store.add("A2", "https://a.com"))
        assertEquals(1, store.all().size)
    }

    @Test fun blankTitleFallsBackToUrl() {
        store.add("  ", "https://a.com")
        assertEquals("https://a.com", store.all().single().title)
    }

    @Test fun removeDeletesAndRemembersTheRemoval() {
        store.add("A", "https://a.com")
        store.remove("https://a.com")
        assertTrue(store.all().isEmpty())
        assertEquals(setOf("https://a.com"), store.removedUrls())
        store.clearRemovedUrls()
        assertTrue(store.removedUrls().isEmpty())
    }

    @Test fun bookmarkingAgainCancelsAPendingRemoval() {
        store.add("A", "https://a.com")
        store.remove("https://a.com")
        store.add("A", "https://a.com")
        assertTrue(store.removedUrls().isEmpty())
    }

    @Test fun survivesANewStoreInstance() {
        store.add("A", "https://a.com")
        val again = LiteBookmarkStore(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(listOf(LiteBookmark("A", "https://a.com")), again.all())
    }

    @Test fun replaceAllOverwritesAndCapsTheList() {
        store.add("old", "https://old.com")
        store.replaceAll((1..LiteBookmarkStore.MAX_BOOKMARKS + 5).map { LiteBookmark("t$it", "https://e.com/$it") })
        assertEquals(LiteBookmarkStore.MAX_BOOKMARKS, store.all().size)
        assertFalse(store.contains("https://old.com"))
        assertFalse(store.add("x", "https://new.com")) // full
    }
}

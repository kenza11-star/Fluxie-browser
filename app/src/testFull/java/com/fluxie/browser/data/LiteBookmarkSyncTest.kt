package com.fluxie.browser.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fluxie.browser.lite.LiteBookmark
import com.fluxie.browser.lite.LiteBookmarkStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiteBookmarkSyncTest {

    private lateinit var db: FluxieDatabase
    private lateinit var repository: BookmarkRepository
    private lateinit var store: LiteBookmarkStore
    private lateinit var sync: LiteBookmarkSync

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(LiteBookmarkStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, FluxieDatabase::class.java).allowMainThreadQueries().build()
        repository = BookmarkRepository(db.bookmarkDao())
        store = LiteBookmarkStore(context)
        sync = LiteBookmarkSync(repository, store)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun roomUrls() = repository.getAllBookmarks().mapNotNull { it.url }

    @Test
    fun `bookmarks added in Lite appear in Normal`() = runTest {
        store.add("Lite page", "https://lite.example/")
        sync.mergeLiteChanges()
        assertEquals(listOf("https://lite.example/"), roomUrls())
    }

    @Test
    fun `bookmarks made in Normal are mirrored to Lite, including ones inside folders`() = runTest {
        val folder = repository.createFolder("Work")
        repository.addBookmark("Root", "https://root.example/")
        repository.addBookmark("Inner", "https://inner.example/", parentId = folder.id)
        sync.mirrorOnce()
        assertEquals(
            setOf(LiteBookmark("Root", "https://root.example/"), LiteBookmark("Inner", "https://inner.example/")),
            store.all().toSet()
        )
    }

    @Test
    fun `a bookmark removed in Lite is removed from Normal and not brought back`() = runTest {
        repository.addBookmark("Keep", "https://keep.example/")
        repository.addBookmark("Drop", "https://drop.example/")
        sync.mirrorOnce()

        store.remove("https://drop.example/")
        sync.mergeLiteChanges()
        sync.mirrorOnce()

        assertEquals(listOf("https://keep.example/"), roomUrls())
        assertEquals(listOf("https://keep.example/"), store.all().map { it.url })
        assertEquals(emptySet<String>(), store.removedUrls())
    }

    @Test
    fun `a bookmark in two folders is removed from both`() = runTest {
        val a = repository.createFolder("A")
        val b = repository.createFolder("B")
        repository.addBookmark("X", "https://x.example/", parentId = a.id)
        repository.addBookmark("X", "https://x.example/", parentId = b.id)
        store.add("X", "https://x.example/")
        store.remove("https://x.example/")
        sync.mergeLiteChanges()
        assertEquals(emptyList<String>(), roomUrls())
    }

    @Test
    fun `merging twice changes nothing`() = runTest {
        store.add("Lite", "https://lite.example/")
        sync.mergeLiteChanges()
        sync.mergeLiteChanges()
        assertEquals(1, roomUrls().size)
    }

    @Test
    fun `switching Normal to Lite and back keeps every bookmark`() = runTest {
        repository.addBookmark("N1", "https://n1.example/")
        sync.mirrorOnce()                                   // Normal running: the Lite copy is current
        store.add("L1", "https://l1.example/")              // user adds one in Lite
        sync.mergeLiteChanges()                             // back in Normal
        sync.mirrorOnce()
        assertEquals(setOf("https://n1.example/", "https://l1.example/"), roomUrls().toSet())
        assertEquals(setOf("https://n1.example/", "https://l1.example/"), store.all().map { it.url }.toSet())
    }
}

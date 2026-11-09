package com.fluxie.browser.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs the real DAO queries against an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class BookmarkRepositoryTest {

    private lateinit var db: FluxieDatabase
    private lateinit var repository: BookmarkRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FluxieDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BookmarkRepository(db.bookmarkDao())
    }

    @After
    fun tearDown() = db.close()

    // ---- add ----
    @Test
    fun `add stores a bookmark at root and returns it with its id`() = runTest {
        val entry = repository.addBookmark("Example", "https://example.com")

        assertTrue(entry.id > 0)
        val root = repository.getChildrenOf(null).first()
        assertEquals(listOf("https://example.com"), root.map { it.url })
        assertEquals(false, root.single().isFolder)
    }

    @Test
    fun `positions increase within a folder`() = runTest {
        val a = repository.addBookmark("A", "https://a.com")
        val b = repository.addBookmark("B", "https://b.com")
        assertEquals(0, a.position)
        assertEquals(1, b.position)
    }

    @Test
    fun `bookmark added to a folder is listed there and not at root`() = runTest {
        val folder = repository.createFolder("Work")
        repository.addBookmark("Docs", "https://docs.com", parentId = folder.id)

        assertEquals(listOf("https://docs.com"), repository.getChildrenOf(folder.id).first().map { it.url })
        assertEquals(listOf("Work"), repository.getChildrenOf(null).first().map { it.title })
    }

    @Test
    fun `folders sort before bookmarks`() = runTest {
        repository.addBookmark("B", "https://b.com")
        repository.createFolder("Folder")
        assertEquals(listOf(true, false), repository.getChildrenOf(null).first().map { it.isFolder })
    }

    // ---- edit ----
    @Test
    fun `edit changes title and url`() = runTest {
        val entry = repository.addBookmark("Old", "https://old.com")

        repository.updateEntry(entry.copy(title = "New", url = "https://new.com"))

        val saved = repository.getById(entry.id)!!
        assertEquals("New", saved.title)
        assertEquals("https://new.com", saved.url)
    }

    @Test
    fun `renaming a folder keeps its contents`() = runTest {
        val folder = repository.createFolder("Work")
        repository.addBookmark("Docs", "https://docs.com", parentId = folder.id)

        repository.updateEntry(folder.copy(title = "Office"))

        assertEquals("Office", repository.getById(folder.id)?.title)
        assertEquals(1, repository.getChildrenOf(folder.id).first().size)
    }

    @Test
    fun `move puts a bookmark in another folder`() = runTest {
        val folder = repository.createFolder("Work")
        val entry = repository.addBookmark("Docs", "https://docs.com")

        repository.moveEntry(entry.id, folder.id)

        assertEquals(folder.id, repository.getById(entry.id)?.parentId)
        assertTrue(repository.getChildrenOf(null).first().none { it.id == entry.id })
    }

    @Test
    fun `a folder cannot be moved into its own descendant`() = runTest {
        val outer = repository.createFolder("Outer")
        val inner = repository.createFolder("Inner", outer.id)

        repository.moveEntry(outer.id, inner.id)

        assertNull(repository.getById(outer.id)?.parentId)
    }

    // ---- delete ----
    @Test
    fun `delete removes the bookmark`() = runTest {
        val entry = repository.addBookmark("Example", "https://example.com")
        repository.deleteEntry(entry.id)
        assertNull(repository.getById(entry.id))
        assertTrue(repository.getChildrenOf(null).first().isEmpty())
    }

    @Test
    fun `deleting a folder deletes its bookmarks`() = runTest {
        val folder = repository.createFolder("Work")
        val child = repository.addBookmark("Docs", "https://docs.com", parentId = folder.id)

        repository.deleteEntry(folder.id)

        assertNull(repository.getById(child.id))
    }

    @Test
    fun `undo re-inserts a deleted bookmark with the same id`() = runTest {
        val entry = repository.addBookmark("Example", "https://example.com")
        repository.deleteEntry(entry.id)

        repository.reInsert(entry)

        assertEquals("Example", repository.getById(entry.id)?.title)
    }

    // ---- query ----
    @Test
    fun `search matches title or url case-insensitively and skips folders`() = runTest {
        repository.addBookmark("Kotlin Guide", "https://kotlinlang.org")
        repository.addBookmark("Other", "https://example.com/kotlin-news")
        repository.addBookmark("Unrelated", "https://unrelated.org")
        repository.createFolder("Kotlin folder")

        val hits = repository.searchBookmarks("KOTLIN").first()

        assertEquals(setOf("Kotlin Guide", "Other"), hits.map { it.title }.toSet())
    }

    @Test
    fun `search with no match is empty`() = runTest {
        repository.addBookmark("A", "https://a.com")
        assertTrue(repository.searchBookmarks("zzz").first().isEmpty())
    }

    @Test
    fun `lookup by url finds the bookmark and ignores folders`() = runTest {
        repository.addBookmark("A", "https://a.com")
        assertNotNull(repository.getBookmarkByUrl("https://a.com"))
        assertNull(repository.getBookmarkByUrl("https://missing.com"))
        assertEquals("A", repository.observeBookmarkByUrl("https://a.com").first()?.title)
    }

    @Test
    fun `getAllFolders returns only folders sorted by name`() = runTest {
        repository.createFolder("Zeta")
        repository.createFolder("Alpha")
        repository.addBookmark("Not a folder", "https://a.com")
        assertEquals(listOf("Alpha", "Zeta"), repository.getAllFolders().first().map { it.title })
    }
}

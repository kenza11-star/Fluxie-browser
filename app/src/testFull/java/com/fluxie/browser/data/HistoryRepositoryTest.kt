package com.fluxie.browser.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {

    private lateinit var db: FluxieDatabase
    private lateinit var repository: HistoryRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FluxieDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HistoryRepository(db.historyDao())
    }

    @After
    fun tearDown() = db.close()

    // ---- add ----
    @Test
    fun `add stores url title and a timestamp`() = runTest {
        val before = System.currentTimeMillis()
        repository.addEntry("https://example.com", "Example")

        val entry = repository.getAllHistory().first().single()
        assertEquals("https://example.com", entry.url)
        assertEquals("Example", entry.title)
        assertTrue(entry.timestamp >= before)
    }

    @Test
    fun `visiting the same url twice in a row is recorded once`() = runTest {
        repository.addEntry("https://example.com", "Example")
        repository.addEntry("https://example.com", "Example")
        assertEquals(1, repository.getAllHistory().first().size)
    }

    @Test
    fun `revisiting an older url after another page is recorded again`() = runTest {
        val dao = db.historyDao()
        dao.insert(HistoryEntry(url = "https://a.com", title = "A", timestamp = 1_000))
        dao.insert(HistoryEntry(url = "https://b.com", title = "B", timestamp = 2_000))

        repository.addEntry("https://a.com", "A") // b.com is the most recent, so this is new

        assertEquals(3, repository.getAllHistory().first().size)
    }

    // ---- query ----
    @Test
    fun `history is newest first`() = runTest {
        val dao = db.historyDao()
        dao.insert(HistoryEntry(url = "https://old.com", title = "Old", timestamp = 1_000))
        dao.insert(HistoryEntry(url = "https://new.com", title = "New", timestamp = 3_000))
        dao.insert(HistoryEntry(url = "https://mid.com", title = "Mid", timestamp = 2_000))

        assertEquals(
            listOf("https://new.com", "https://mid.com", "https://old.com"),
            repository.getAllHistory().first().map { it.url }
        )
    }

    @Test
    fun `search matches title or url case-insensitively`() = runTest {
        repository.addEntry("https://kotlinlang.org", "Docs")
        repository.addEntry("https://example.com", "All about Kotlin")
        repository.addEntry("https://other.org", "Other")

        val hits = repository.searchHistory("kotlin").first()

        assertEquals(setOf("https://kotlinlang.org", "https://example.com"), hits.map { it.url }.toSet())
    }

    @Test
    fun `search with no match is empty`() = runTest {
        repository.addEntry("https://a.com", "A")
        assertTrue(repository.searchHistory("zzz").first().isEmpty())
    }

    @Test
    fun `favicon is attached to the latest entry for that url`() = runTest {
        repository.addEntry("https://a.com", "A")
        repository.updateFavicon("https://a.com", byteArrayOf(1, 2, 3))
        assertEquals(listOf<Byte>(1, 2, 3), repository.getAllHistory().first().single().favicon?.toList())
    }

    // ---- delete ----
    @Test
    fun `delete removes one entry`() = runTest {
        repository.addEntry("https://a.com", "A")
        repository.addEntry("https://b.com", "B")
        val a = repository.getAllHistory().first().first { it.url == "https://a.com" }

        repository.deleteEntry(a.id)

        assertEquals(listOf("https://b.com"), repository.getAllHistory().first().map { it.url })
    }

    @Test
    fun `undo re-inserts a deleted entry`() = runTest {
        repository.addEntry("https://a.com", "A")
        val entry = repository.getAllHistory().first().single()
        repository.deleteEntry(entry.id)

        repository.reInsert(entry)

        assertEquals(entry.id, repository.getAllHistory().first().single().id)
    }

    @Test
    fun `clear all removes everything`() = runTest {
        repository.addEntry("https://a.com", "A")
        repository.addEntry("https://b.com", "B")

        repository.clearAll()

        assertTrue(repository.getAllHistory().first().isEmpty())
    }
}

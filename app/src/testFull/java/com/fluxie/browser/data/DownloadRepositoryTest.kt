package com.fluxie.browser.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fluxie.browser.common.DownloadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** In-memory Room for the stored rows; the system DownloadManager is replaced by a recording fake. */
@RunWith(RobolectricTestRunner::class)
class DownloadRepositoryTest {

    private class FakeBackend : DownloadBackend {
        var nextId = 100L
        var failEnqueue = false
        val enqueued = mutableListOf<DownloadRequest>()
        val removed = mutableListOf<Long>()
        val snapshots = mutableMapOf<Long, DownloadSnapshot>()

        override fun enqueue(request: DownloadRequest): Long? {
            if (failEnqueue) return null
            enqueued += request
            return nextId++
        }
        override fun query(ids: List<Long>) = snapshots.filterKeys { it in ids }
        override fun contentUri(id: Long): Uri? = null
        override fun remove(id: Long) { removed += id; snapshots.remove(id) }
    }

    private lateinit var db: FluxieDatabase
    private lateinit var backend: FakeBackend
    private lateinit var repository: DownloadRepository

    private val request = DownloadRequest(
        url = "https://example.com/file.zip",
        fileName = "file.zip",
        mimeType = "application/zip",
        userAgent = "UA",
        cookie = "a=b"
    )

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FluxieDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backend = FakeBackend()
        repository = DownloadRepository(db.downloadDao(), backend)
    }

    @After
    fun tearDown() = db.close()

    private fun snapshot(state: DownloadState, done: Long = 0, total: Long = -1) =
        DownloadSnapshot(state, done, total, "application/zip")

    @Test
    fun `start queues the download and records a row`() = runTest {
        assertTrue(repository.start(request, record = true))

        assertEquals(listOf("https://example.com/file.zip"), backend.enqueued.map { it.url })
        val rows = db.downloadDao().observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(100L, rows.single().downloadId)
        assertEquals("file.zip", rows.single().fileName)
    }

    @Test
    fun `incognito download runs but leaves no row`() = runTest {
        assertTrue(repository.start(request, record = false))

        assertEquals(1, backend.enqueued.size)
        assertTrue(db.downloadDao().observeAll().first().isEmpty())
    }

    @Test
    fun `failed enqueue records nothing`() = runTest {
        backend.failEnqueue = true
        assertFalse(repository.start(request, record = true))
        assertTrue(db.downloadDao().observeAll().first().isEmpty())
    }

    @Test
    fun `list merges stored rows with live system status`() = runTest {
        repository.start(request, record = true)
        backend.snapshots[100L] = snapshot(DownloadState.COMPLETED, done = 2048, total = 2048)

        val item = repository.observeDownloads().first().single()

        assertEquals(DownloadState.COMPLETED, item.state)
        assertEquals(100, item.progressPercent)
        assertEquals("file.zip", item.entry.fileName)
    }

    @Test
    fun `running download reports progress`() = runTest {
        repository.start(request, record = true)
        backend.snapshots[100L] = snapshot(DownloadState.RUNNING, done = 250, total = 1000)

        val item = repository.observeDownloads().first().single()

        assertEquals(DownloadState.RUNNING, item.state)
        assertEquals(25, item.progressPercent)
    }

    @Test
    fun `download unknown to the system shows as missing`() = runTest {
        repository.start(request, record = true)
        // no snapshot: the user removed it from the system Downloads app
        assertEquals(DownloadState.MISSING, repository.observeDownloads().first().single().state)
    }

    @Test
    fun `newest download is listed first`() = runTest {
        repository.start(request, record = true)
        repository.start(request.copy2(fileName = "second.zip"), record = true)
        backend.snapshots[100L] = snapshot(DownloadState.COMPLETED, 1, 1)
        backend.snapshots[101L] = snapshot(DownloadState.COMPLETED, 1, 1)

        assertEquals(
            listOf("second.zip", "file.zip"),
            repository.observeDownloads().first().map { it.entry.fileName }
        )
    }

    @Test
    fun `delete removes the file through the system and forgets the row`() = runTest {
        repository.start(request, record = true)
        backend.snapshots[100L] = snapshot(DownloadState.COMPLETED, 1, 1)
        val item = repository.observeDownloads().first().single()

        repository.delete(item)

        assertEquals(listOf(100L), backend.removed)
        assertTrue(db.downloadDao().observeAll().first().isEmpty())
    }

    private fun DownloadRequest.copy2(fileName: String) =
        DownloadRequest(url, fileName, mimeType, userAgent, cookie)
}

package com.fluxie.browser.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.fluxie.browser.common.DownloadState
import com.fluxie.browser.common.downloadProgressPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

class DownloadRequest(
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val userAgent: String?,
    val cookie: String?
)

class DownloadSnapshot(
    val state: DownloadState,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val mimeType: String?
)

/** What the UI shows for one row: the stored entry merged with the system's live status. */
class DownloadItem(
    val entry: DownloadEntry,
    val state: DownloadState,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val mimeType: String?
) {
    val progressPercent: Int? get() = downloadProgressPercent(downloadedBytes, totalBytes)
}

/** The system side of downloading, kept behind an interface so the repository is testable. */
interface DownloadBackend {
    /** Queues the download; returns the system download id, or null if it could not be queued. */
    fun enqueue(request: DownloadRequest): Long?
    fun query(ids: List<Long>): Map<Long, DownloadSnapshot>
    fun contentUri(id: Long): Uri?
    /** Cancels the download if running and deletes the file. */
    fun remove(id: Long)
}

class AndroidDownloadBackend(context: Context) : DownloadBackend {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    override fun enqueue(request: DownloadRequest): Long? = try {
        val dm = DownloadManager.Request(Uri.parse(request.url)).apply {
            setTitle(request.fileName)
            request.mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
            request.userAgent?.takeIf { it.isNotBlank() }?.let { addRequestHeader("User-Agent", it) }
            request.cookie?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Cookie", it) }
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Public Downloads folder; needs no storage permission on Android 10+.
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, request.fileName)
            } else {
                // Before Android 10 the public folder needs WRITE_EXTERNAL_STORAGE, so use the
                // app's own external folder (no permission, removed on uninstall).
                setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, request.fileName)
            }
        }
        manager.enqueue(dm)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: SecurityException) {
        null
    }

    override fun query(ids: List<Long>): Map<Long, DownloadSnapshot> {
        if (ids.isEmpty()) return emptyMap()
        val result = HashMap<Long, DownloadSnapshot>()
        manager.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val doneCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val mimeCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)
            while (c.moveToNext()) {
                val state = when (c.getInt(statusCol)) {
                    DownloadManager.STATUS_PENDING -> DownloadState.PENDING
                    DownloadManager.STATUS_RUNNING -> DownloadState.RUNNING
                    DownloadManager.STATUS_PAUSED -> DownloadState.PAUSED
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadState.COMPLETED
                    else -> DownloadState.FAILED
                }
                result[c.getLong(idCol)] = DownloadSnapshot(
                    state = state,
                    downloadedBytes = c.getLong(doneCol),
                    totalBytes = c.getLong(totalCol),
                    mimeType = c.getString(mimeCol)
                )
            }
        }
        return result
    }

    override fun contentUri(id: Long): Uri? = manager.getUriForDownloadedFile(id)

    override fun remove(id: Long) {
        manager.remove(id)
    }
}

class DownloadRepository(
    private val dao: DownloadDao,
    private val backend: DownloadBackend
) {

    /**
     * Starts a download. Only downloads from normal tabs are recorded ([record] = true); an
     * incognito download still runs (and the system shows its notification) but leaves no row,
     * so it never appears in the Downloads screen. Returns false if it could not be queued.
     */
    suspend fun start(request: DownloadRequest, record: Boolean): Boolean {
        val systemId = backend.enqueue(request) ?: return false
        if (record) {
            dao.insert(
                DownloadEntry(
                    downloadId = systemId,
                    fileName = request.fileName,
                    url = request.url,
                    mimeType = request.mimeType,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        return true
    }

    /** Emits the list again every second while anything is still downloading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDownloads(): Flow<List<DownloadItem>> = dao.observeAll().transformLatest { entries ->
        while (true) {
            val snapshots = withContext(Dispatchers.IO) { backend.query(entries.map { it.downloadId }) }
            val items = entries.map { entry ->
                val snap = snapshots[entry.downloadId]
                if (snap == null) {
                    DownloadItem(entry, DownloadState.MISSING, 0L, -1L, entry.mimeType)
                } else {
                    DownloadItem(entry, snap.state, snap.downloadedBytes, snap.totalBytes, snap.mimeType ?: entry.mimeType)
                }
            }
            emit(items)
            if (items.none { it.state.isActive }) break
            delay(POLL_INTERVAL_MS)
        }
    }

    fun contentUri(item: DownloadItem): Uri? = backend.contentUri(item.entry.downloadId)

    /** Removes the file (cancelling it first if still running) and forgets the entry. */
    suspend fun delete(item: DownloadItem) {
        withContext(Dispatchers.IO) { backend.remove(item.entry.downloadId) }
        dao.deleteById(item.entry.id)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1000L
    }
}

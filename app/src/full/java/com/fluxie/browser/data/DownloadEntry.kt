package com.fluxie.browser.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One download started from a normal tab. [downloadId] is the id Android's DownloadManager
 * assigned; progress and the file itself live there, this row only remembers that the download
 * belongs to this app so the Downloads screen can list it.
 */
@Entity(tableName = "downloads", indices = [Index(value = ["downloadId"], unique = true)])
data class DownloadEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long,
    val fileName: String,
    val url: String,
    val mimeType: String? = null,
    val createdAt: Long
)

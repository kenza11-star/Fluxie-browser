package com.fluxie.browser.common

import java.util.Locale

/** Lifecycle of a download as shown in the Downloads screen. */
enum class DownloadState(val isActive: Boolean) {
    PENDING(true),
    RUNNING(true),
    PAUSED(true),
    COMPLETED(false),
    FAILED(false),
    /** The system no longer knows this download (e.g. removed from the system Downloads app). */
    MISSING(false)
}

/** 0..100, or null when the total size is unknown (DownloadManager reports -1 or 0). */
fun downloadProgressPercent(downloadedBytes: Long, totalBytes: Long): Int? {
    if (totalBytes <= 0L) return null
    // BigInteger: downloaded * 100 would overflow Long for files near Long.MAX_VALUE.
    val done = java.math.BigInteger.valueOf(downloadedBytes.coerceIn(0L, totalBytes))
    return done.multiply(java.math.BigInteger.valueOf(100))
        .divide(java.math.BigInteger.valueOf(totalBytes)).toInt()
}

/** "512 B", "1.5 KB", "2.4 MB" ... (1024-based). */
fun formatFileSize(bytes: Long): String {
    if (bytes < 0L) return ""
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

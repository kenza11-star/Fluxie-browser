package com.fluxie.browser.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFormatTest {

    @Test
    fun `progress is a percentage of total`() {
        assertEquals(0, downloadProgressPercent(0, 1000))
        assertEquals(50, downloadProgressPercent(500, 1000))
        assertEquals(100, downloadProgressPercent(1000, 1000))
    }

    @Test
    fun `progress rounds down and is clamped`() {
        assertEquals(33, downloadProgressPercent(333, 1000))
        assertEquals(100, downloadProgressPercent(5000, 1000))
        assertEquals(0, downloadProgressPercent(-5, 1000))
    }

    @Test
    fun `progress is unknown without a total size`() {
        assertNull(downloadProgressPercent(100, -1))
        assertNull(downloadProgressPercent(100, 0))
    }

    @Test
    fun `progress does not overflow on huge files`() {
        assertEquals(50, downloadProgressPercent(Long.MAX_VALUE / 2 + 1, Long.MAX_VALUE))
        assertEquals(49, downloadProgressPercent(Long.MAX_VALUE / 2, Long.MAX_VALUE))
    }

    @Test
    fun `file sizes are formatted with 1024 units`() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("1023 B", formatFileSize(1023))
        assertEquals("1.0 KB", formatFileSize(1024))
        assertEquals("1.5 KB", formatFileSize(1536))
        assertEquals("2.4 MB", formatFileSize((2.4 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", formatFileSize(1024L * 1024 * 1024))
        assertEquals("", formatFileSize(-1))
    }

    @Test
    fun `only in-flight states are active`() {
        assertTrue(DownloadState.PENDING.isActive)
        assertTrue(DownloadState.RUNNING.isActive)
        assertTrue(DownloadState.PAUSED.isActive)
        assertFalse(DownloadState.COMPLETED.isActive)
        assertFalse(DownloadState.FAILED.isActive)
        assertFalse(DownloadState.MISSING.isActive)
    }
}

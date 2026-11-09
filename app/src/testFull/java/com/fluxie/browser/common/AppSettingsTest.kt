package com.fluxie.browser.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `defaults are normal mode system theme and no dynamic color`() {
        val s = AppSettings()
        assertEquals(BrowserMode.NORMAL, s.mode)
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertFalse(s.dynamicColor)
    }

    @Test
    fun `parseEnum reads stored names`() {
        assertEquals(BrowserMode.LITE, parseEnum("LITE", BrowserMode.NORMAL))
        assertEquals(ThemeMode.DARK, parseEnum("DARK", ThemeMode.SYSTEM))
    }

    @Test
    fun `parseEnum falls back for null unknown and wrong-case values`() {
        assertEquals(BrowserMode.NORMAL, parseEnum(null, BrowserMode.NORMAL))
        assertEquals(BrowserMode.NORMAL, parseEnum("TURBO", BrowserMode.NORMAL))
        assertEquals(ThemeMode.SYSTEM, parseEnum("dark", ThemeMode.SYSTEM))
    }

    @Test
    fun `location status prefers precise over approximate`() {
        assertEquals(PermissionStatus.ALLOWED, locationStatus(fineGranted = true, coarseGranted = true))
        assertEquals(PermissionStatus.ALLOWED, locationStatus(fineGranted = true, coarseGranted = false))
        assertEquals(PermissionStatus.APPROXIMATE, locationStatus(fineGranted = false, coarseGranted = true))
        assertEquals(PermissionStatus.DENIED, locationStatus(fineGranted = false, coarseGranted = false))
    }
}

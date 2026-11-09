package com.fluxie.browser.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserAgentUtilsTest {

    private val mobile =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UP1A.231005.007; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.144 Mobile Safari/537.36"

    @Test
    fun `mobile WebView agent becomes desktop Linux Chrome`() {
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.6099.144 Safari/537.36",
            UserAgentUtils.toDesktop(mobile)
        )
    }

    @Test
    fun `desktop agent has no android or mobile markers`() {
        val desktop = UserAgentUtils.toDesktop(mobile)
        assertFalse(desktop.contains("Android"))
        assertFalse(desktop.contains("Mobile"))
        assertFalse(desktop.contains("Version/"))
        assertFalse(desktop.contains("wv"))
    }

    @Test
    fun `agent without mobile parts is unchanged`() {
        val ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        assertEquals(ua, UserAgentUtils.toDesktop(ua))
    }

    @Test
    fun `is idempotent`() {
        val once = UserAgentUtils.toDesktop(mobile)
        assertEquals(once, UserAgentUtils.toDesktop(once))
    }

    @Test
    fun `does not touch words that merely contain Mobile`() {
        val ua = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 Chrome/1.0 MobileFoo Safari/537.36"
        assertEquals(true, UserAgentUtils.toDesktop(ua).contains("MobileFoo"))
    }
}

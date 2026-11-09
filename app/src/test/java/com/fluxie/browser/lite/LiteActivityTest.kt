package com.fluxie.browser.lite

import android.content.ComponentCallbacks2
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiteActivityTest {

    @Test fun opensOnTheStartPageWithoutCrashing() {
        val controller = Robolectric.buildActivity(LiteActivity::class.java).setup()
        assertFalse(controller.get().isFinishing)
        controller.destroy()
    }

    @Test fun opensALinkFromAnIntent() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
        val controller = Robolectric.buildActivity(LiteActivity::class.java, intent).setup()
        assertFalse(controller.get().isFinishing)
        controller.destroy()
    }

    @Test fun lowMemoryWithASingleTabKeepsTheActivityWorking() {
        val controller = Robolectric.buildActivity(LiteActivity::class.java).setup()
        controller.get().onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertFalse(controller.get().isFinishing)
        controller.destroy()
        assertTrue(controller.get().isDestroyed)
    }
}

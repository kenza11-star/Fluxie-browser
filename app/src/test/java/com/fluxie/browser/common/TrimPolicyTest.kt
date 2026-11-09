package com.fluxie.browser.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimPolicyTest {

    @Test fun releasesFromRunningLowUpwards() {
        assertFalse(TrimPolicy.shouldRelease(TrimPolicy.TRIM_MEMORY_RUNNING_MODERATE))
        assertTrue(TrimPolicy.shouldRelease(TrimPolicy.TRIM_MEMORY_RUNNING_LOW))
        assertTrue(TrimPolicy.shouldRelease(TrimPolicy.TRIM_MEMORY_RUNNING_CRITICAL))
        assertTrue(TrimPolicy.shouldRelease(TrimPolicy.TRIM_MEMORY_UI_HIDDEN))
        assertTrue(TrimPolicy.shouldRelease(TrimPolicy.TRIM_MEMORY_BACKGROUND))
        assertFalse(TrimPolicy.shouldRelease(0))
    }

    @Test fun neverReleasesVisibleProtectedOrAlreadyReleasedTabs() {
        val result = TrimPolicy.tabsToRelease(
            tabIds = listOf(1, 2, 3, 4),
            visibleId = 2,
            aliveIds = setOf(1, 2, 4),       // 3 was released before
            protectedIds = setOf(4)          // incognito
        )
        assertEquals(listOf(1), result)
    }

    @Test fun singleTabReleasesNothing() {
        assertEquals(emptyList<Int>(), TrimPolicy.tabsToRelease(listOf(0), 0, setOf(0)))
    }
}

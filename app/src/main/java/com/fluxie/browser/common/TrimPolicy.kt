package com.fluxie.browser.common

/**
 * When and what to release on low memory. Shared by Normal and Lite mode. Pure logic: the Android
 * trim level constants are copied here (ComponentCallbacks2.TRIM_MEMORY_*) so it can be unit-tested.
 */
object TrimPolicy {
    const val TRIM_MEMORY_RUNNING_MODERATE = 5
    const val TRIM_MEMORY_RUNNING_LOW = 10
    const val TRIM_MEMORY_RUNNING_CRITICAL = 15
    const val TRIM_MEMORY_UI_HIDDEN = 20
    const val TRIM_MEMORY_BACKGROUND = 40

    /** Background tabs are released from "running low" upwards; "moderate" is only a hint. */
    fun shouldRelease(level: Int): Boolean = level >= TRIM_MEMORY_RUNNING_LOW

    /**
     * Tabs whose WebView should be released: alive, not the visible one, and not [protectedIds]
     * (incognito tabs, whose pages cannot be safely restored).
     */
    fun tabsToRelease(
        tabIds: List<Int>,
        visibleId: Int,
        aliveIds: Set<Int>,
        protectedIds: Set<Int> = emptySet()
    ): List<Int> = tabIds.filter { it != visibleId && it in aliveIds && it !in protectedIds }
}

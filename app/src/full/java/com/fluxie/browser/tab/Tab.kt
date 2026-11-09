package com.fluxie.browser.tab

import android.graphics.Bitmap

class Tab(
    val id: Int,
    var url: String = "",
    var title: String = "",
    var thumbnail: Bitmap? = null,
    val isIncognito: Boolean = false,
    var favicon: Bitmap? = null,
    var desktopSite: Boolean = false
) {
    val displayTitle: String
        get() = title.ifEmpty { url.ifEmpty { "New Tab" } }
}

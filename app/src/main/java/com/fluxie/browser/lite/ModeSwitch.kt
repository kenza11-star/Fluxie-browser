package com.fluxie.browser.lite

import android.app.Activity
import android.content.Intent
import android.net.Uri

/**
 * Moves between the Normal (Compose) screen and the Lite screen of the full edition. The target is
 * addressed by class name because the lite edition does not contain [NORMAL_ACTIVITY] at all.
 * Neither side deletes anything: bookmarks live in Room (Normal) and SharedPreferences (Lite) and
 * are reconciled when Normal starts; settings stay where they are.
 */
object ModeSwitch {
    private const val NORMAL_ACTIVITY = "com.fluxie.browser.MainActivity"
    private const val LITE_ACTIVITY = "com.fluxie.browser.lite.LiteActivity"

    /** Replaces the current task's screen with Lite. The caller must have saved the mode flag already. */
    fun openLite(from: Activity) {
        start(from, LITE_ACTIVITY, null, replaceTask = true)
    }

    /** Replaces the current task's screen with Normal. The caller must have cleared the lite flag already. */
    fun openNormal(from: Activity) {
        start(from, NORMAL_ACTIVITY, null, replaceTask = true)
    }

    /** Hands a link opened while in Lite mode from the Normal entry activity to Lite. */
    fun forwardToLite(from: Activity, data: Uri?) {
        start(from, LITE_ACTIVITY, data, replaceTask = false)
    }

    private fun start(from: Activity, className: String, data: Uri?, replaceTask: Boolean) {
        val intent = Intent().setClassName(from, className)
        if (data != null) {
            intent.action = Intent.ACTION_VIEW
            intent.data = data
        }
        if (replaceTask) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        from.startActivity(intent)
        from.finish()
    }
}

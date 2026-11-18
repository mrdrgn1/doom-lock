package com.lockin

import android.content.Context
import android.provider.Settings

object BringToFront {

    /**
     * Brings the lock screen forward. With "Display over other apps" granted we can start the
     * activity from the background; otherwise a full-screen notification is posted instead.
     */
    fun go(context: Context, title: String, text: String) {
        val intent = LockActivity.intent(context)
        val launched = if (Settings.canDrawOverlays(context)) {
            runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        } else {
            false
        }
        if (!launched) {
            Notifications.fullScreenReturn(context, title, text)
        }
    }
}

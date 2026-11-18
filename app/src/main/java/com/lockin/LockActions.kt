package com.lockin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Backs the buttons on the session and pause notifications. */
class LockActions : BroadcastReceiver() {

    companion object {
        const val ACTION_BACK = "com.lockin.action.BACK"
        const val ACTION_RESUME = "com.lockin.action.RESUME"
        const val ACTION_END = "com.lockin.action.END"
        const val ACTION_NOT_TODAY = "com.lockin.action.NOT_TODAY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RESUME -> resume(context)
            ACTION_END -> end(context)
            ACTION_BACK -> BringToFront.go(context, "Back to lock-in", "Focus is still running")
            ACTION_NOT_TODAY -> Notifications.cancel(context, Notifications.ID_ASK)
            else -> Unit
        }
    }

    private fun resume(context: Context) {
        val now = System.currentTimeMillis()
        SessionStore.load(context)
        if (SessionStore.phase != Phase.PAUSED) return
        SessionStore.endPause(context, now)
        Dnd.alarmsOnly(context)
        SessionService.stop(context)
        Notifications.cancel(context, Notifications.ID_PAUSE_REMINDER)
        TodayWidget.refresh(context)
        BringToFront.go(context, "Back to lock-in", "Focus resumed")
    }

    private fun end(context: Context) {
        val now = System.currentTimeMillis()
        SessionStore.load(context)
        val result = SessionStore.end(context, now) ?: return
        HistoryDb.get(context).addSession(result)
        Dnd.restore(context, result.prevFilter)
        SessionService.stop(context)
        Notifications.cancel(context, Notifications.ID_PAUSE_REMINDER)
        Notifications.cancel(context, Notifications.ID_RETURN)
        TodayWidget.refresh(context)
        runCatching {
            context.startActivity(
                Intent(context, SessionSummaryActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

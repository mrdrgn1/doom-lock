package com.lockin

import android.app.NotificationManager
import android.content.Context

object Dnd {

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    fun hasAccess(context: Context): Boolean =
        runCatching { manager(context)?.isNotificationPolicyAccessGranted == true }.getOrDefault(false)

    /** Current interruption filter, or -1 when we cannot read it. */
    fun currentFilter(context: Context): Int =
        if (hasAccess(context)) {
            runCatching { manager(context)?.currentInterruptionFilter ?: -1 }.getOrDefault(-1)
        } else {
            -1
        }

    private fun setFilter(context: Context, filter: Int) {
        if (!hasAccess(context)) return
        runCatching { manager(context)?.setInterruptionFilter(filter) }
    }

    /** Alarms only, which is the "Do Not Disturb on" state used during focus. */
    fun alarmsOnly(context: Context) = setFilter(context, NotificationManager.INTERRUPTION_FILTER_ALARMS)

    fun off(context: Context) = setFilter(context, NotificationManager.INTERRUPTION_FILTER_ALL)

    /** Puts back whatever was active before the session started. */
    fun restore(context: Context, previous: Int) {
        val filter = when (previous) {
            NotificationManager.INTERRUPTION_FILTER_ALL,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> previous
            else -> NotificationManager.INTERRUPTION_FILTER_ALL
        }
        setFilter(context, filter)
    }
}

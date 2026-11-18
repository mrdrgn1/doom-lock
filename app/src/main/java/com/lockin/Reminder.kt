package com.lockin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * An optional daily nudge at a time you choose. It never starts a session on its own: it asks,
 * and the notification carries START and NOT TODAY.
 */
object Reminder {

    /** Extra carried by the notification and the widget: start a session on arrival. */
    const val EXTRA_START = "start_now"

    private const val PREFS = "lockin_settings"
    private const val KEY_ON = "reminder_on"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    fun hour(context: Context): Int = prefs(context).getInt(KEY_HOUR, 19)

    fun minute(context: Context): Int = prefs(context).getInt(KEY_MINUTE, 0)

    fun set(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putBoolean(KEY_ON, true)
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
        schedule(context)
    }

    fun disable(context: Context) {
        prefs(context).edit().putBoolean(KEY_ON, false).apply()
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(pending(context)) }
    }

    /**
     * Re-arms the next nudge. Safe to call any time, from anywhere; does nothing when the
     * reminder is off. Exact when Android allows it, within two minutes otherwise.
     */
    fun schedule(context: Context) {
        if (!isOn(context)) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = nextTrigger(context, System.currentTimeMillis())
        val intent = pending(context)
        val mayBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
        runCatching {
            if (mayBeExact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                manager.setWindow(AlarmManager.RTC_WAKEUP, at, 2L * 60_000L, intent)
            }
        }.recoverCatching {
            manager.set(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun nextTrigger(context: Context, from: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour(context))
            set(Calendar.MINUTE, minute(context))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= from + 1000L) add(Calendar.DAY_OF_MONTH, 1)
        }
        return calendar.timeInMillis
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_ASK)
        return PendingIntent.getBroadcast(
            context, 61, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Posts the nudge at the chosen time, then arms the next one. Also re-arms after a reboot. */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ASK = "com.lockin.action.ASK"
        const val ACTION_BOOT = "android.intent.action.BOOT_COMPLETED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_BOOT) {
            Reminder.schedule(context)
            return
        }
        SessionStore.load(context)
        if (!SessionStore.isActive && Reminder.isOn(context)) {
            Notifications.askToStart(context)
        }
        Reminder.schedule(context)
    }
}

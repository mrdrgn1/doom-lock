package com.lockin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object Notifications {

    const val CHANNEL_SESSION = "session"
    const val CHANNEL_ALERTS = "alerts"

    const val ID_SERVICE = 100
    const val ID_RETURN = 101
    const val ID_PAUSE_REMINDER = 102
    const val ID_ASK = 103

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_SESSION) == null) {
            val channel = NotificationChannel(
                CHANNEL_SESSION, "Session countdown", NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(CHANNEL_ALERTS) == null) {
            val channel = NotificationChannel(
                CHANNEL_ALERTS, "Return to focus", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun post(context: Context, id: Int, notification: Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        runCatching { manager.notify(id, notification) }
    }

    fun cancel(context: Context, id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        runCatching { manager.cancel(id) }
    }

    /**
     * A button on the notification that is handled by [LockActions], so tapping it never has to
     * open the app first.
     */
    private fun action(
        context: Context,
        title: String,
        action: String,
        requestCode: Int
    ): Notification.Action {
        val intent = Intent(context, LockActions::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = Icon.createWithResource(context, R.drawable.ic_notification)
        return Notification.Action.Builder(icon, title, pending).build()
    }

    fun serviceNotification(
        context: Context,
        title: String,
        text: String,
        contentIntent: PendingIntent?,
        withBackAction: Boolean = false
    ): Notification {
        ensureChannels(context)
        val builder = Notification.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
        if (withBackAction) {
            builder.addAction(action(context, "BACK TO LOCK IN", LockActions.ACTION_BACK, 41))
        }
        return builder.build()
    }

    /**
     * Backup path for bringing the app back when the overlay permission is unavailable:
     * a full-screen intent notification (falls back to a heads-up notification if the
     * system withholds full-screen intent permission).
     */
    fun fullScreenReturn(context: Context, title: String, text: String) {
        ensureChannels(context)
        val intent = LockActivity.intent(context)
        val pending = PendingIntent.getActivity(
            context, 21, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .addAction(action(context, "BACK TO LOCK IN", LockActions.ACTION_BACK, 41))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        post(context, ID_RETURN, notification)
    }

    /** The daily nudge. It asks whether to start; it never starts a session by itself. */
    fun askToStart(context: Context) {
        ensureChannels(context)
        val start = PendingIntent.getActivity(
            context, 25,
            Intent(context, MainActivity::class.java)
                .putExtra(Reminder.EXTRA_START, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val later = PendingIntent.getBroadcast(
            context, 26,
            Intent(context, LockActions::class.java).setAction(LockActions.ACTION_NOT_TODAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = Icon.createWithResource(context, R.drawable.ic_notification)
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to lock in")
            .setContentText("Start a session now, or leave it for today.")
            .setContentIntent(start)
            .addAction(Notification.Action.Builder(icon, "START", start).build())
            .addAction(Notification.Action.Builder(icon, "NOT TODAY", later).build())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        post(context, ID_ASK, notification)
    }

    fun pauseReminder(context: Context, pausedFor: String, contentIntent: PendingIntent?) {
        ensureChannels(context)
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Still paused")
            .setContentText("Paused for $pausedFor. Resume when you're back.")
            .setContentIntent(contentIntent)
            .addAction(action(context, "RESUME", LockActions.ACTION_RESUME, 42))
            .addAction(action(context, "END", LockActions.ACTION_END, 43))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        post(context, ID_PAUSE_REMINDER, notification)
    }
}

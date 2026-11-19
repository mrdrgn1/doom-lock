package com.lockin

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Keeps the countdown alive while the app is not on screen: doomscroll breaks, the
 * always-available app window, and the pause reminders.
 */
class SessionService : Service() {

    companion object {
        const val MODE_BREAK = 1
        const val MODE_PAUSED = 2
        const val MODE_APPS = 3
        const val MODE_PANEL = 4

        private const val EXTRA_MODE = "mode"
        private const val REMINDER_FIRST_MS = 30L * 60_000L
        private const val REMINDER_EVERY_MS = 5L * 60_000L

        fun start(context: Context, mode: Int) {
            val intent = Intent(context, SessionService::class.java).putExtra(EXTRA_MODE, mode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var mode = 0
    private var reminderSlot = -1

    private val ticker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            SessionStore.load(this@SessionService)
            when (mode) {
                MODE_BREAK -> {
                    val left = SessionStore.breakLeftMs(now)
                    if (left <= 0L) {
                        finishBreak(now)
                        return
                    }
                    update("Doomscroll break", "${Fmt.clock(left)} left · tap to go back early")
                    handler.postDelayed(this, 1000L)
                }

                MODE_APPS -> {
                    val left = SessionStore.appsLeftMs(now)
                    if (left <= 0L) {
                        finishApps(now)
                        return
                    }
                    update("Allowed app window", "${Fmt.clock(left)} left · focus is still running")
                    handler.postDelayed(this, 1000L)
                }

                MODE_PANEL -> {
                    val left = SessionStore.panelLeftMs(now)
                    if (left <= 0L) {
                        finishPanel(now)
                        return
                    }
                    update("Quick panel", "${Fmt.clock(left)} left · back to lock-in after")
                    handler.postDelayed(this, 1000L)
                }

                MODE_PAUSED -> {
                    val paused = SessionStore.pauseMs(now)
                    update("Paused", "${Fmt.hms(paused)} paused · focus timer frozen")
                    maybeRemind(paused)
                    handler.postDelayed(this, 1000L)
                }

                else -> stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SessionStore.load(this)
        val requested = intent?.getIntExtra(EXTRA_MODE, 0) ?: 0
        mode = if (requested != 0) requested else modeForPhase()
        // startForeground must be honoured even when there is nothing left to count down,
        // otherwise the system kills the service with a RemoteServiceException.
        startForegroundCompat(
            Notifications.serviceNotification(
                this, titleOrIdle(), textOrIdle(), contentIntent(), hasBackAction()
            )
        )
        if (mode == 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        reminderSlot = -1
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun modeForPhase(): Int = when (SessionStore.phase) {
        Phase.BREAK -> MODE_BREAK
        Phase.PAUSED -> MODE_PAUSED
        Phase.APPS -> MODE_APPS
        Phase.PANEL -> MODE_PANEL
        else -> 0
    }

    private fun titleOrIdle(): String = when (mode) {
        MODE_BREAK -> "Doomscroll break"
        MODE_APPS -> "Allowed app window"
        MODE_PAUSED -> "Paused"
        MODE_PANEL -> "Quick panel"
        else -> "Lock In"
    }

    private fun textOrIdle(): String {
        val now = System.currentTimeMillis()
        return when (mode) {
            MODE_BREAK -> "${Fmt.clock(SessionStore.breakLeftMs(now))} left · tap to go back early"
            MODE_APPS -> "${Fmt.clock(SessionStore.appsLeftMs(now))} left · focus is still running"
            MODE_PAUSED -> "${Fmt.hms(SessionStore.pauseMs(now))} paused · focus timer frozen"
            MODE_PANEL -> "${Fmt.clock(SessionStore.panelLeftMs(now))} left · back to lock-in after"
            else -> "Session idle"
        }
    }

    private fun update(title: String, text: String) {
        Notifications.post(
            this, Notifications.ID_SERVICE,
            Notifications.serviceNotification(this, title, text, contentIntent(), hasBackAction())
        )
    }

    /** Breaks and app windows offer a "back to lock in" button on the notification. */
    private fun hasBackAction(): Boolean =
        mode == MODE_BREAK || mode == MODE_APPS || mode == MODE_PANEL

    private fun contentIntent(): PendingIntent {
        if (mode == 0) {
            return PendingIntent.getActivity(
                this, 24,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val intent = if (mode == MODE_PAUSED) {
            Intent(this, PauseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            LockActivity.intent(this)
        }
        return PendingIntent.getActivity(
            this, 22, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 30 minutes in, then every 5 minutes until the pause is resumed or ended. */
    private fun maybeRemind(pausedMs: Long) {
        if (pausedMs < REMINDER_FIRST_MS) return
        val slot = ((pausedMs - REMINDER_FIRST_MS) / REMINDER_EVERY_MS).toInt()
        if (slot <= reminderSlot) return
        reminderSlot = slot
        val intent = Intent(this, PauseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this, 23, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Notifications.pauseReminder(this, Fmt.human(pausedMs), pending)
    }

    private fun finishBreak(now: Long) {
        SessionStore.endBreak(this, now)
        Dnd.alarmsOnly(this)
        TodayWidget.refresh(this)
        BringToFront.go(this, "Break over", "Back to lock-in")
        stopSelf()
    }

    private fun finishApps(now: Long) {
        SessionStore.endApps(this, now)
        TodayWidget.refresh(this)
        BringToFront.go(this, "App window over", "Back to lock-in")
        stopSelf()
    }

    /** The minute is up: the panel closes itself and the lock screen comes back and pins again. */
    private fun finishPanel(now: Long) {
        SessionStore.endPanelPass(this, now)
        TodayWidget.refresh(this)
        BringToFront.go(this, "Panel time up", "Back to lock-in")
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notifications.ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notifications.ID_SERVICE, notification)
        }
    }
}

package com.lockin

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/**
 * Home screen widget. Idle it shows today's total; during a session it switches to the running
 * focus time and the doomscroll balance. It refreshes whenever the session changes or the app is
 * opened, which is as live as a widget gets without a background ticker.
 */
class TodayWidget : AppWidgetProvider() {

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context): RemoteViews {
            val now = System.currentTimeMillis()
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            SessionStore.load(context)

            if (SessionStore.isActive) {
                val focus = SessionStore.focusMs(now)
                val bank = SessionStore.bankMs(now)
                val label = when (SessionStore.phase) {
                    Phase.BREAK -> "ON A BREAK"
                    Phase.PAUSED -> "PAUSED"
                    Phase.APPS -> "IN AN APP"
                    Phase.PANEL -> "QUICK PANEL"
                    else -> "LOCKED IN"
                }
                views.setTextViewText(R.id.widgetLabel, label)
                views.setTextViewText(R.id.widgetTime, Fmt.human(focus))
                views.setTextViewText(
                    R.id.widgetMeta,
                    when (SessionStore.phase) {
                        Phase.BREAK ->
                            "${Fmt.human(SessionStore.breakLeftMs(now))} left on the break"
                        Phase.APPS ->
                            "${Fmt.human(SessionStore.appsLeftMs(now))} left in this app"
                        Phase.PANEL -> "focus frozen for a minute"
                        else -> "${Fmt.human(bank)} doomscroll banked"
                    }
                )
            } else {
                val totals = HistoryDb.get(context).dayTotals(Fmt.dayKey(now))
                views.setTextViewText(R.id.widgetLabel, "TODAY")
                views.setTextViewText(R.id.widgetTime, Fmt.human(totals[0]))
                views.setTextViewText(
                    R.id.widgetMeta,
                    if (totals[2] == 0L) {
                        "NO SESSIONS YET"
                    } else {
                        "${totals[2]} SESSION${if (totals[2] == 1L) "" else "S"}"
                    }
                )
            }

            val open = PendingIntent.getActivity(
                context, 31,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, open)

            val start = PendingIntent.getActivity(
                context, 32,
                Intent(context, MainActivity::class.java)
                    .putExtra(Reminder.EXTRA_START, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetStart, start)
            views.setViewVisibility(
                R.id.widgetStart, if (SessionStore.isActive) View.GONE else View.VISIBLE
            )
            return views
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = build(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }
}

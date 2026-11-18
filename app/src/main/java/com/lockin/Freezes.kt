package com.lockin

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Two streak freezes a week, and unused ones carry over. A missed day spends one freeze so a
 * single bad day does not wipe the streak; two missed days in a row still cost two, and a third
 * missed day ends the run.
 */
object Freezes {

    const val PER_WEEK = 2

    private const val PREFS = "lockin_freezes"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun left(context: Context): Int = prefs(context).getInt("bank", PER_WEEK)

    /** Day keys already covered by a freeze. */
    fun days(context: Context): Set<String> =
        prefs(context).getStringSet("days", emptySet())?.toSet() ?: emptySet()

    /**
     * Hands out this week's freezes and spends them on the days just missed. Only days that
     * touch a live streak are covered, so a long absence does not burn the whole bank.
     */
    fun ensure(context: Context, focusByDay: Map<String, Long>, now: Long) {
        val sp = prefs(context)
        var bank = sp.getInt("bank", PER_WEEK)
        val thisWeek = weekKey(now)
        val lastWeek = sp.getString("week", null)
        if (lastWeek == null) {
            bank = maxOf(bank, PER_WEEK)
        } else if (lastWeek != thisWeek) {
            bank += PER_WEEK * weeksBetween(lastWeek, thisWeek)
        }

        val frozen = days(context).toMutableSet()
        val cursor = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, -1)
        }
        val missed = ArrayList<String>()
        var streakAlive = false
        var guard = 0
        while (guard++ < 7) {
            val key = Fmt.dayKey(cursor.timeInMillis)
            if (frozen.contains(key) || (focusByDay[key] ?: 0L) > 0L) {
                streakAlive = true
                break
            }
            missed.add(key)
            cursor.add(Calendar.DAY_OF_MONTH, -1)
        }
        if (streakAlive && missed.isNotEmpty() && bank >= missed.size) {
            bank -= missed.size
            frozen.addAll(missed)
        }

        sp.edit()
            .putInt("bank", bank)
            .putString("week", thisWeek)
            .putStringSet("days", frozen)
            .apply()
    }

    private fun weekKey(now: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
        }
        return Fmt.dayKey(calendar.timeInMillis)
    }

    private fun weeksBetween(last: String, current: String): Int {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val from = format.parse(last)?.time ?: return 1
            val to = format.parse(current)?.time ?: return 1
            ((to - from) / (7L * 86_400_000L)).toInt().coerceIn(1, 52)
        } catch (e: Exception) {
            1
        }
    }
}

package com.lockin

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Fmt {

    /** HH:MM:SS */
    fun hms(ms: Long): String {
        val s = ms.coerceAtLeast(0L) / 1000L
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600L, (s % 3600L) / 60L, s % 60L)
    }

    /** m:ss, rounded up so a countdown never shows 0:00 while time is left. */
    fun clock(ms: Long): String {
        val s = (ms.coerceAtLeast(0L) + 999L) / 1000L
        return String.format(Locale.US, "%d:%02d", s / 60L, s % 60L)
    }

    /** "1h 24m" */
    fun human(ms: Long): String {
        if (ms <= 0L) return "0m"
        val min = Math.round(ms / 60000.0)
        if (min < 1L) return "<1m"
        val h = min / 60L
        val m = min % 60L
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** Compact calendar cell label: "2h14", "45m" or "". */
    fun tiny(ms: Long): String {
        if (ms < 60_000L) return if (ms > 0L) "<1m" else ""
        val min = ms / 60_000L
        val h = min / 60L
        val m = min % 60L
        return if (h > 0L) "${h}h${m.toString().padStart(2, '0')}" else "${m}m"
    }

    fun dayKey(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    fun prettyDay(key: String): String = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key)
        if (d == null) key else SimpleDateFormat("EEEE d MMMM", Locale.US).format(d)
    } catch (e: Exception) {
        key
    }

    fun prettyMonth(year: Int, month: Int): String {
        val c = Calendar.getInstance()
        c.set(year, month, 1)
        return SimpleDateFormat("MMMM yyyy", Locale.US).format(c.time)
    }

    fun timeOfDay(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))
}

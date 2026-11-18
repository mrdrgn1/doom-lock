package com.lockin

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.lockin.ui.DotMatrixView
import com.lockin.ui.WeekBarsView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Where the week went: totals, the hour you focus best, doomscroll earned and spent, freezes. */
class WeekActivity : Activity() {

    private lateinit var timer: DotMatrixView
    private lateinit var range: TextView
    private lateinit var sessionsLine: TextView
    private lateinit var goalLine: TextView
    private lateinit var doomLine: TextView
    private lateinit var hourLine: TextView
    private lateinit var freezeLine: TextView
    private lateinit var bars: WeekBarsView
    private lateinit var dayRows: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_week)

        timer = findViewById(R.id.weekTimer)
        timer.dotColor = getColor(R.color.accent)
        timer.offDotColor = 0x14D71921

        range = findViewById(R.id.weekRange)
        sessionsLine = findViewById(R.id.weekSessions)
        goalLine = findViewById(R.id.weekGoal)
        doomLine = findViewById(R.id.weekDoom)
        hourLine = findViewById(R.id.weekHour)
        freezeLine = findViewById(R.id.weekFreezes)
        bars = findViewById(R.id.weekBarsFull)
        bars.barColor = getColor(R.color.accent)
        bars.restColor = getColor(R.color.stroke_bright)
        dayRows = findViewById(R.id.weekDays)

        findViewById<View>(R.id.weekBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val monday = mondayOf(now)
        val db = HistoryDb.get(this)
        val sessions = db.sessionsBetween(monday, dayAfter(monday, 7))

        var focus = 0L
        var breaks = 0L
        val byHour = LongArray(24)
        val byDay = LongArray(7)

        sessions.forEach { row ->
            focus += row.focusMs
            breaks += row.breakMs
            val locked = db.segmentsFor(row.id).filter { it.kind == 'F' }
            if (locked.isEmpty()) {
                addToHours(byHour, row.startMs, row.endMs)
            } else {
                locked.forEach { addToHours(byHour, it.startMs, it.endMs) }
            }
            val index = dayIndex(monday, row.startMs)
            if (index in 0 until 7) byDay[index] += row.focusMs
        }

        val count = sessions.size.toLong()
        val active = byDay.count { it > 0L }
        val target = getSharedPreferences("lockin_settings", Context.MODE_PRIVATE)
            .getLong("daily_target_ms", 3L * 60L * 60L * 1000L)
        val hits = byDay.count { it > 0L && target > 0L && it >= target }
        val earned = count * SessionStore.DOOM_START_MS + focus / 6L

        val format = SimpleDateFormat("d MMM", Locale.US)
        range.text = "${format.format(Date(monday)).uppercase(Locale.US)} – " +
            "${format.format(Date(dayAfter(monday, 6))).uppercase(Locale.US)}"
        timer.text = Fmt.hms(focus)

        sessionsLine.text = when {
            count == 0L -> "NO SESSIONS YET THIS WEEK"
            else -> "$count SESSION${if (count == 1L) "" else "S"}  ·  " +
                "AVG ${Fmt.human(focus / count).uppercase()}  ·  $active ACTIVE DAY" +
                if (active == 1) "" else "S"
        }
        goalLine.text = if (target <= 0L) {
            "DAY GOAL  ·  NONE SET"
        } else {
            "DAY GOAL ${Fmt.human(target).uppercase()}  ·  $hits OF 7 DAYS HIT"
        }
        doomLine.text = "DOOMSCROLL  ${Fmt.human(breaks).uppercase()} USED  ·  " +
            "${Fmt.human(earned).uppercase()} EARNED"
        hourLine.text = bestHour(byHour)
        val freezes = Freezes.left(this)
        freezeLine.text = "FREEZES  $freezes IN THE BANK  ·  2 A WEEK, UNUSED CARRY"
        bars.values = byDay
        bars.todayIndex = dayIndex(monday, now)

        buildDayRows(monday, byDay)
    }

    private fun bestHour(byHour: LongArray): String {
        val best = byHour.indices.maxByOrNull { byHour[it] } ?: return "MOST FOCUS  ·  —"
        if (byHour[best] <= 0L) return "MOST FOCUS  ·  —"
        val end = (best + 1) % 24
        return "MOST FOCUS  %02d:00–%02d:00".format(Locale.US, best, end)
    }

    private fun buildDayRows(monday: Long, byDay: LongArray) {
        dayRows.removeAllViews()
        val format = SimpleDateFormat("EEE d", Locale.US)
        val cursor = Calendar.getInstance().apply { timeInMillis = monday }
        for (index in 0 until 7) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(7), 0, dp(7))
            }
            val name = TextView(this).apply {
                text = format.format(Date(cursor.timeInMillis)).uppercase(Locale.US)
                setTextColor(getColor(if (byDay[index] > 0L) R.color.dim else R.color.faint))
                textSize = 10f
                letterSpacing = 0.12f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val value = TextView(this).apply {
                text = if (byDay[index] > 0L) Fmt.human(byDay[index]).uppercase() else "—"
                setTextColor(getColor(if (byDay[index] > 0L) R.color.text else R.color.faint))
                textSize = 10f
            }
            row.addView(name)
            row.addView(value)
            dayRows.addView(row)
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun addToHours(buckets: LongArray, start: Long, end: Long) {
        var cursor = start
        var guard = 0
        while (cursor < end && guard++ < 600) {
            val hourStart = Calendar.getInstance().apply {
                timeInMillis = cursor
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val next = hourStart.timeInMillis + 3_600_000L
            val chunk = minOf(next, end) - cursor
            if (chunk > 0L) buckets[hourStart.get(Calendar.HOUR_OF_DAY)] += chunk
            cursor = next
        }
    }

    private fun dayIndex(monday: Long, moment: Long): Int {
        var start = monday
        for (index in 0 until 7) {
            val next = dayAfter(start, 1)
            if (moment >= start && moment < next) return index
            start = next
        }
        return -1
    }

    private fun mondayOf(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
    }.timeInMillis

    private fun dayAfter(moment: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = moment
        add(Calendar.DAY_OF_MONTH, days)
    }.timeInMillis

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Same as the back arrow")
    override fun onBackPressed() {
        finish()
    }
}

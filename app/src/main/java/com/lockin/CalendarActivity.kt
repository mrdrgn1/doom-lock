package com.lockin

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.lockin.ui.SessionTimelineView
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

class CalendarActivity : Activity() {

    private lateinit var db: HistoryDb
    private lateinit var grid: GridLayout
    private lateinit var monthTitle: TextView
    private lateinit var dayTitle: TextView
    private lateinit var dayStats: TextView
    private lateinit var dayTimeline: SessionTimelineView
    private lateinit var sessionRows: LinearLayout
    private lateinit var noteInput: EditText
    private lateinit var savedLabel: TextView
    private lateinit var undoBar: View
    private lateinit var undoText: TextView

    private var year = 0
    private var month = 0
    private var selectedDay = 1
    private var loadedNote = ""
    private var loading = false

    private val handler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    private var undoAction: (() -> Unit)? = null
    private val hideUndo = Runnable {
        undoBar.visibility = View.GONE
        undoAction = null
    }

    private val noteWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (loading) return
            saveRunnable?.let { handler.removeCallbacks(it) }
            val runnable = Runnable { saveNote() }
            saveRunnable = runnable
            handler.postDelayed(runnable, 400L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        db = HistoryDb.get(this)
        grid = findViewById(R.id.calGrid)
        monthTitle = findViewById(R.id.monthTitle)
        dayTitle = findViewById(R.id.dayTitle)
        dayStats = findViewById(R.id.dayStats)
        dayTimeline = findViewById(R.id.dayTimeline)
        dayTimeline.focusColor = getColor(R.color.accent)
        dayTimeline.unlockedColor = getColor(R.color.stroke_bright)
        sessionRows = findViewById(R.id.sessionRows)
        noteInput = findViewById(R.id.noteInput)
        savedLabel = findViewById(R.id.savedLabel)
        undoBar = findViewById(R.id.undoBar)
        undoText = findViewById(R.id.undoText)

        val now = Calendar.getInstance()
        year = now.get(Calendar.YEAR)
        month = now.get(Calendar.MONTH)
        selectedDay = now.get(Calendar.DAY_OF_MONTH)

        buildWeekHeader()
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.prevMonth).setOnClickListener { shiftMonth(-1) }
        findViewById<View>(R.id.nextMonth).setOnClickListener { shiftMonth(1) }
        findViewById<View>(R.id.addLogBtn).setOnClickListener { showAddLogDialog() }
        findViewById<View>(R.id.undoBtn).setOnClickListener {
            val action = undoAction
            handler.removeCallbacks(hideUndo)
            undoBar.visibility = View.GONE
            undoAction = null
            action?.invoke()
        }
        noteInput.addTextChangedListener(noteWatcher)

        buildGrid()
        loadDay()
    }

    override fun onPause() {
        flushNote()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildWeekHeader() {
        val header = findViewById<LinearLayout>(R.id.weekHeader)
        header.removeAllViews()
        val symbols = DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays
        // Monday first: shortWeekdays is 1..7 starting on Sunday.
        val labels = ArrayList<String>()
        for (i in 2..7) labels.add(symbols[i])
        labels.add(symbols[1])
        for (label in labels) {
            val view = TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTextColor(getColor(R.color.faint))
                gravity = Gravity.CENTER
                letterSpacing = 0.1f
            }
            header.addView(
                view,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun shiftMonth(direction: Int) {
        month += direction
        if (month < 0) {
            month = 11
            year--
        } else if (month > 11) {
            month = 0
            year++
        }
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        selectedDay = selectedDay.coerceAtMost(calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        buildGrid()
        loadDay()
    }

    private fun dayKey(day: Int): String =
        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)

    private fun buildGrid() {
        grid.removeAllViews()
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        // Calendar.DAY_OF_WEEK is 1=Sunday; shift so Monday is column 0.
        val offset = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val days = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val focusByDay = db.monthFocus(year, month)
        val maxFocus = (focusByDay.values.maxOrNull() ?: 0L).coerceAtLeast(1L)

        for (i in 0 until offset) grid.addView(emptyCell())
        for (day in 1..days) {
            val focus = focusByDay[dayKey(day)] ?: 0L
            grid.addView(dayCell(day, focus, maxFocus, day == selectedDay))
        }
    }

    private fun dayCell(day: Int, focusMs: Long, maxFocus: Long, selected: Boolean): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(56)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
            if (selected) setBackgroundResource(R.drawable.bg_day_selected)
        }

        cell.addView(TextView(this).apply {
            text = day.toString()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(getColor(if (selected) R.color.text else R.color.dim))
            gravity = Gravity.CENTER
        })

        cell.addView(TextView(this).apply {
            text = Fmt.tiny(focusMs)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            setTextColor(getColor(if (focusMs > 0L) R.color.accent else R.color.faint))
            gravity = Gravity.CENTER
            minHeight = dp(11)
        })

        cell.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(2))
            setBackgroundResource(R.drawable.bg_bar)
            alpha = if (focusMs <= 0L) {
                0f
            } else {
                (0.35f + 0.65f * (focusMs.toFloat() / maxFocus.toFloat())).coerceIn(0f, 1f)
            }
        })

        cell.setOnClickListener {
            flushNote() // persist the previous day's note before the fields swap
            selectedDay = day
            buildGrid()
            loadDay()
        }
        return cell
    }

    private fun emptyCell(): View = View(this).apply {
        layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = dp(56)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        }
    }

    private fun loadDay() {
        val key = dayKey(selectedDay)
        monthTitle.text = Fmt.prettyMonth(year, month)
        dayTitle.text = Fmt.prettyDay(key)

        val totals = db.dayTotals(key)
        dayStats.text = "${Fmt.human(totals[0])} focus · ${Fmt.human(totals[1])} doomscroll · " +
            "${totals[2]} session${if (totals[2] == 1L) "" else "s"}"

        val sessions = db.sessionsForDay(key)
        renderDayTimeline(sessions, totals[0])
        sessionRows.removeAllViews()
        if (sessions.isEmpty()) {
            sessionRows.addView(TextView(this).apply {
                text = "No sessions on this day."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(getColor(R.color.dim))
            })
        } else {
            sessions.forEach { sessionRows.addView(sessionRow(it)) }
        }

        loading = true
        loadedNote = db.note(key)
        noteInput.setText(loadedNote)
        noteInput.setSelection(noteInput.text.length)
        loading = false
        savedLabel.text = "Saved as you type"
    }

    /**
     * The same red/grey bar as the end-of-session screen, but for the whole selected day: every
     * session's segments back to back, with old sessions drawn as solid focus since they have
     * no segment detail.
     */
    private fun renderDayTimeline(sessions: List<SessionRow>, dayFocusMs: Long) {
        if (sessions.isEmpty()) {
            dayTimeline.visibility = View.GONE
            dayTimeline.segments = emptyList()
            return
        }
        val segments = ArrayList<SessionSegment>()
        sessions.forEach { session ->
            val own = db.segmentsFor(session.id)
            if (own.isEmpty()) {
                segments.add(SessionSegment('F', session.startMs, session.endMs))
            } else {
                segments.addAll(own)
            }
        }
        val from = sessions.first().startMs
        val to = sessions.last().endMs
        dayTimeline.visibility = View.VISIBLE
        dayTimeline.spanStart = from
        dayTimeline.spanEnd = to
        dayTimeline.fallbackFocusMs = dayFocusMs
        dayTimeline.fallbackUnlockedMs = (to - from - dayFocusMs).coerceAtLeast(0L)
        dayTimeline.segments = segments
    }

    /** One logged session: tap the text to edit it, the cross swallows it. */
    private fun sessionRow(session: SessionRow): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) }
            setOnClickListener { showEditDialog(session) }
        }

        val label = "${Fmt.timeOfDay(session.startMs)} – ${Fmt.timeOfDay(session.endMs)}" +
            "   ${Fmt.human(session.focusMs)}" +
            if (session.breakMs >= 60_000L) " · ${Fmt.human(session.breakMs)} break" else ""

        row.addView(TextView(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(getColor(R.color.dim))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.addView(TextView(this).apply {
            text = "×"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(getColor(R.color.faint))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener { confirmDelete(session) }
        })
        return row
    }

    // ------------------------------------------------------------------ undo

    private fun showUndo(message: String, action: () -> Unit) {
        undoAction = action
        undoText.text = message
        undoBar.visibility = View.VISIBLE
        handler.removeCallbacks(hideUndo)
        handler.postDelayed(hideUndo, 6000L)
    }

    // ------------------------------------------------------------------ add / edit / delete

    private fun confirmDelete(session: SessionRow) {
        AlertDialog.Builder(this)
            .setMessage(
                "Delete the ${Fmt.timeOfDay(session.startMs)} session " +
                    "(${Fmt.human(session.focusMs)} focus)?"
            )
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                val snapshot = db.snapshot(session.id)
                db.deleteSession(session.id)
                refreshDay()
                if (snapshot != null) {
                    showUndo("Session deleted") {
                        db.restore(snapshot)
                        refreshDay()
                    }
                }
            }
            .show()
    }

    private fun showAddLogDialog() {
        showTimesDialog("Add a log", "", "") { startMs, endMs ->
            val id = db.addManualSession(startMs, endMs)
            refreshDay()
            if (id > 0L) {
                showUndo("Log added") {
                    db.deleteSession(id)
                    refreshDay()
                }
            }
        }
    }

    private fun showEditDialog(session: SessionRow) {
        val snapshot = db.snapshot(session.id)
        showTimesDialog(
            "Edit session",
            Fmt.timeOfDay(session.startMs),
            Fmt.timeOfDay(session.endMs)
        ) { startMs, endMs ->
            db.updateSession(session.id, startMs, endMs)
            refreshDay()
            if (snapshot != null) {
                showUndo("Session updated") {
                    db.deleteSession(session.id)
                    db.restore(snapshot)
                    refreshDay()
                }
            }
        }
    }

    private fun refreshDay() {
        buildGrid()
        loadDay()
        TodayWidget.refresh(this)
    }

    /** Start and end as HH:MM; an end before the start is treated as crossing midnight. */
    private fun showTimesDialog(
        title: String,
        prefillStart: String,
        prefillEnd: String,
        onSave: (Long, Long) -> Unit
    ) {
        val startInput = timeField(prefillStart, "19:00")
        val endInput = timeField(prefillEnd, "21:30")

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        container.addView(fieldLabel("START"))
        container.addView(startInput)
        container.addView(fieldLabel("END"))
        container.addView(endInput)
        container.addView(fieldLabel("AN END BEFORE THE START COUNTS AS OVERNIGHT").apply {
            setTextColor(getColor(R.color.faint))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            setPadding(0, dp(10), 0, 0)
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val start = parseTime(startInput.text.toString())
                val end = parseTime(endInput.text.toString())
                if (start == null || end == null) {
                    Toast.makeText(this, "Use a time like 19:00 or 19.30", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                val startMs = atSelectedDay(start[0], start[1])
                var endMs = atSelectedDay(end[0], end[1])
                if (endMs <= startMs) endMs += 86_400_000L
                val length = endMs - startMs
                if (length < 60_000L || length > 86_400_000L) {
                    Toast.makeText(this, "That is not a usable time range", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                onSave(startMs, endMs)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun timeField(prefill: String, hintText: String) = EditText(this).apply {
        hint = hintText
        setText(prefill)
        setSelection(text.length)
        inputType = InputType.TYPE_CLASS_TEXT
        setTextColor(getColor(R.color.text))
        setHintTextColor(getColor(R.color.faint))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }

    private fun fieldLabel(text: String) = TextView(this).apply {
        this.text = text
        letterSpacing = 0.24f
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
        setTextColor(getColor(R.color.faint))
        setPadding(0, dp(14), 0, dp(4))
    }

    /** Accepts "19:00", "19.30", "1900" and "19". Returns hour, minute or null. */
    private fun parseTime(raw: String): IntArray? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val parts = text.split(':', '.', ' ')
        val hour: Int
        val minute: Int
        when (parts.size) {
            1 -> {
                val digits = parts[0]
                hour = when (digits.length) {
                    1, 2 -> digits.toIntOrNull() ?: return null
                    3, 4 -> (digits.substring(0, digits.length - 2)).toIntOrNull() ?: return null
                    else -> return null
                }
                minute = if (digits.length <= 2) {
                    0
                } else {
                    digits.substring(digits.length - 2).toIntOrNull() ?: return null
                }
            }

            2 -> {
                hour = parts[0].toIntOrNull() ?: return null
                minute = parts[1].toIntOrNull() ?: return null
            }

            else -> return null
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return intArrayOf(hour, minute)
    }

    private fun atSelectedDay(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(year, month, selectedDay, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun saveNote() {
        val text = noteInput.text.toString()
        if (text == loadedNote) return
        db.saveNote(dayKey(selectedDay), text)
        loadedNote = text
        savedLabel.text = "Saved · ${Fmt.timeOfDay(System.currentTimeMillis())}"
    }

    /** Cancels the debounce and writes immediately, for day switches and leaving the screen. */
    private fun flushNote() {
        saveRunnable?.let { handler.removeCallbacks(it) }
        saveRunnable = null
        saveNote()
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)
}

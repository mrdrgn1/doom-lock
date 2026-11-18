package com.lockin

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.lockin.ui.DotGridProgressView
import com.lockin.ui.DotMatrixView
import com.lockin.ui.WeekBarsView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private companion object {
        const val SETTINGS_PREFS = "lockin_settings"
        const val KEY_TARGET_INDEX = "target_index"
        const val DEFAULT_TARGET_INDEX = 3
        const val KEY_DAILY_TARGET = "daily_target_ms"
        const val DEFAULT_DAILY_TARGET = 3L * 60L * 60L * 1000L
        const val KEY_SETUP_CHOICE = "setup_choice"
        const val CHOICE_AUTO = 0
        const val CHOICE_SHOW = 1
        const val CHOICE_HIDE = 2
    }

    /** Tap the daily line to walk through these; 0 means no daily target. */
    private val dailyTargets = longArrayOf(
        60L * 60_000L, 2L * 60L * 60_000L, 3L * 60L * 60_000L,
        4L * 60L * 60_000L, 6L * 60L * 60_000L, 0L
    )

    /** Goals offered on the start screen. 0 means "no goal", the session runs open ended. */
    private val targets = longArrayOf(
        30L * 60_000L, 60L * 60_000L, 90L * 60_000L,
        120L * 60_000L, 180L * 60_000L, 0L
    )

    private val chipIds = intArrayOf(
        R.id.chip30, R.id.chip1h, R.id.chip1_5h, R.id.chip2h, R.id.chip3h, R.id.chipOpen
    )

    private lateinit var recapTimer: DotMatrixView
    private lateinit var recapMeta: TextView
    private lateinit var setupBody: View
    private lateinit var setupLabel: TextView
    private lateinit var setupToggle: TextView
    private lateinit var weekLine: TextView
    private lateinit var streakLine: TextView
    private lateinit var dailyBar: DotGridProgressView
    private lateinit var dailyLine: TextView
    private lateinit var weekBars: WeekBarsView
    private lateinit var dndState: TextView
    private lateinit var dndAction: TextView
    private lateinit var overlayState: TextView
    private lateinit var overlayAction: TextView
    private lateinit var npState: TextView
    private lateinit var npAction: TextView
    private lateinit var pinState: TextView
    private lateinit var pinAction: TextView
    private lateinit var appsState: TextView
    private lateinit var reminderState: TextView
    private lateinit var reminderAction: TextView

    private var targetIndex = DEFAULT_TARGET_INDEX

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recapTimer = findViewById(R.id.recapTimer)
        recapTimer.dotColor = getColor(R.color.accent)
        recapTimer.offDotColor = 0x0DFFFFFF
        recapMeta = findViewById(R.id.recapMeta)
        setupBody = findViewById(R.id.setupBody)
        setupLabel = findViewById(R.id.setupLabel)
        setupToggle = findViewById(R.id.setupToggle)
        weekLine = findViewById(R.id.weekLine)
        streakLine = findViewById(R.id.streakLine)

        dailyBar = findViewById(R.id.dailyBar)
        dailyBar.columns = 24
        dailyBar.rows = 1
        dailyBar.litColor = getColor(R.color.accent)
        dailyBar.frameColor = 0x33FFFFFF
        dailyBar.offColor = 0x00FFFFFF
        dailyLine = findViewById(R.id.dailyLine)
        dailyLine.setOnClickListener { cycleDailyTarget() }

        weekBars = findViewById(R.id.weekBars)
        weekBars.barColor = getColor(R.color.accent)
        weekBars.restColor = getColor(R.color.stroke_bright)
        dndState = findViewById(R.id.dndState)
        dndAction = findViewById(R.id.dndAction)
        overlayState = findViewById(R.id.overlayState)
        overlayAction = findViewById(R.id.overlayAction)
        npState = findViewById(R.id.npState)
        npAction = findViewById(R.id.npAction)
        pinState = findViewById(R.id.pinState)
        pinAction = findViewById(R.id.pinAction)
        appsState = findViewById(R.id.appsState)
        reminderState = findViewById(R.id.reminderState)
        reminderAction = findViewById(R.id.reminderAction)

        targetIndex = settings().getInt(KEY_TARGET_INDEX, DEFAULT_TARGET_INDEX)
        chipIds.forEachIndexed { index, id ->
            findViewById<TextView>(id).setOnClickListener { selectTarget(index) }
        }
        styleChips()

        findViewById<TextView>(R.id.startBtn).setOnClickListener { startLockIn() }
        findViewById<TextView>(R.id.calendarBtn).setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }
        findViewById<View>(R.id.dndRow).setOnClickListener { openDndSettings() }
        findViewById<View>(R.id.overlayRow).setOnClickListener { openOverlaySettings() }
        findViewById<View>(R.id.npRow).setOnClickListener { openNotificationAccess() }
        findViewById<View>(R.id.pinRow).setOnClickListener { openSecuritySettings() }
        findViewById<View>(R.id.setupHeader).setOnClickListener { toggleSetup() }
        findViewById<View>(R.id.appsCard).setOnClickListener {
            startActivity(Intent(this, AllowedAppsActivity::class.java))
        }
        findViewById<View>(R.id.reminderRow).setOnClickListener { pickReminderTime() }
        findViewById<View>(R.id.reminderAction).setOnClickListener { toggleReminder() }
        findViewById<View>(R.id.weekCard).setOnClickListener {
            startActivity(Intent(this, WeekActivity::class.java))
        }

        Reminder.schedule(this)
        if (intent?.getBooleanExtra(Reminder.EXTRA_START, false) == true) startLockIn()
        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        TodayWidget.refresh(this)
        LockInTileService.refresh(this)
        Reminder.schedule(this)
        SessionStore.load(this)
        when (SessionStore.phase) {
            Phase.IDLE -> refresh()
            Phase.PAUSED -> startActivity(Intent(this, PauseActivity::class.java))
            else -> startActivity(Intent(this, LockActivity::class.java))
        }
    }

    // ------------------------------------------------------------------ start screen

    private fun refresh() {
        val now = System.currentTimeMillis()
        val today = Fmt.dayKey(now)
        val totals = HistoryDb.get(this).dayTotals(today)
        refreshRecap(now)
        val byDay = HistoryDb.get(this).focusByDay(Fmt.dayKey(now - 120L * 86_400_000L), today)
        Freezes.ensure(this, byDay, now)
        refreshWeek(now, byDay)
        refreshDaily(totals[0])
        refreshSetup()
        refreshAllowedApps()
        refreshReminder()
    }

    private fun refreshAllowedApps() {
        val names = AllowedApps.slots(this).map { AllowedApps.label(this, it) }
        appsState.text = if (names.isEmpty()) {
            "None · nothing is reachable while locked in"
        } else {
            names.joinToString("  ·  ")
        }
    }

    private fun refreshReminder() {
        val on = Reminder.isOn(this)
        reminderState.text = if (on) {
            String.format(
                Locale.US, "Asks at %02d:%02d · it never starts one for you",
                Reminder.hour(this), Reminder.minute(this)
            )
        } else {
            "Off · tap to set a time for a daily nudge"
        }
        reminderAction.text = if (on) "OFF" else "ON"
        reminderAction.setTextColor(getColor(if (on) R.color.accent_dim else R.color.accent))
    }

    private fun pickReminderTime() {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                Reminder.set(this, hour, minute)
                refresh()
            },
            Reminder.hour(this),
            Reminder.minute(this),
            true
        ).show()
    }

    private fun toggleReminder() {
        if (Reminder.isOn(this)) {
            Reminder.disable(this)
            refresh()
        } else {
            pickReminderTime()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra(Reminder.EXTRA_START, false) == true) startLockIn()
    }

    /** The headline is the session you just finished: how long, when, and how much was locked. */
    private fun refreshRecap(now: Long) {
        val last = HistoryDb.get(this).lastSession()
        if (last == null) {
            recapTimer.text = "00:00:00"
            recapMeta.text = "Nothing logged yet · your first session lands here"
            return
        }
        recapTimer.text = Fmt.hms(last.focusMs)
        val span = (last.endMs - last.startMs).coerceAtLeast(1L)
        val locked = (last.focusMs * 100L / span).coerceIn(0L, 100L)
        val extra = if (last.breakMs > 0L) "  ·  ${Fmt.human(last.breakMs)} doomscroll" else ""
        recapMeta.text = "${dayWord(last.startMs, now)} ${Fmt.timeOfDay(last.startMs)}–" +
            "${Fmt.timeOfDay(last.endMs)}  ·  $locked% locked$extra"
    }

    private fun dayWord(value: Long, now: Long): String {
        val key = Fmt.dayKey(value)
        if (key == Fmt.dayKey(now)) return "TODAY"
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, -1)
        }
        if (key == Fmt.dayKey(yesterday.timeInMillis)) return "YESTERDAY"
        return SimpleDateFormat("EEE d MMM", Locale.US).format(Date(value)).uppercase(Locale.US)
    }

    private fun refreshWeek(now: Long, byDay: Map<String, Long>) {
        val monday = mondayOf(now)
        val mondayKey = Fmt.dayKey(monday)

        var week = 0L
        var days = 0L
        var best = 0L
        for ((day, focus) in byDay) {
            if (day < mondayKey) continue
            week += focus
            if (focus > 0L) days++
            if (focus > best) best = focus
        }
        weekLine.text = if (week <= 0L) {
            "THIS WEEK  0m  ·  NOTHING YET"
        } else {
            "THIS WEEK  ${Fmt.human(week).uppercase()}  ·  $days DAY" +
                if (days == 1L) "" else "S"
        }

        val streak = streakDays(byDay, now)
        val freezes = Freezes.left(this)
        val freezeWord = "$freezes FREEZE${if (freezes == 1) "" else "S"}"
        streakLine.text = if (streak <= 0L) {
            "NO STREAK YET  ·  $freezeWord IN THE BANK"
        } else {
            "$streak DAY${if (streak == 1L) "" else "S"} IN A ROW  ·  " +
                "BEST ${Fmt.human(best).uppercase()}  ·  $freezeWord"
        }

        val values = LongArray(7)
        val dayCursor = Calendar.getInstance().apply { timeInMillis = monday }
        for (index in 0 until 7) {
            values[index] = byDay[Fmt.dayKey(dayCursor.timeInMillis)] ?: 0L
            dayCursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        weekBars.values = values
        weekBars.todayIndex = (Calendar.getInstance().apply { timeInMillis = now }
            .get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    private fun refreshDaily(todayFocus: Long) {
        val target = settings().getLong(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET)
        if (target <= 0L) {
            dailyBar.progress = 0f
            dailyBar.alpha = 0.35f
            dailyLine.text = "NO DAILY TARGET  ·  TAP TO SET ONE"
            dailyLine.setTextColor(getColor(R.color.faint))
            return
        }
        dailyBar.alpha = 1f
        dailyBar.progress = todayFocus.toFloat() / target.toFloat()
        if (todayFocus >= target) {
            dailyLine.text = "DAILY ${Fmt.human(target).uppercase()}  ·  DONE"
            dailyLine.setTextColor(getColor(R.color.accent))
        } else {
            dailyLine.text = "DAILY ${Fmt.human(target).uppercase()}  ·  " +
                "${Fmt.human(target - todayFocus).uppercase()} TO GO"
            dailyLine.setTextColor(getColor(R.color.dim))
        }
    }

    private fun cycleDailyTarget() {
        val current = settings().getLong(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET)
        val index = dailyTargets.indexOfFirst { it == current }.let { if (it < 0) 2 else it }
        settings().edit()
            .putLong(KEY_DAILY_TARGET, dailyTargets[(index + 1) % dailyTargets.size])
            .apply()
        refresh()
    }

    private fun mondayOf(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
    }.timeInMillis

    /**
     * Consecutive focus days, ending today or yesterday. A day covered by a freeze keeps the run
     * alive without adding to it.
     */
    private fun streakDays(byDay: Map<String, Long>, now: Long): Long {
        val frozen = Freezes.days(this)
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        if ((byDay[Fmt.dayKey(now)] ?: 0L) <= 0L) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        var streak = 0L
        var guard = 0
        while (guard++ < 400) {
            val key = Fmt.dayKey(calendar.timeInMillis)
            val focus = byDay[key] ?: 0L
            if (focus > 0L) {
                streak++
            } else if (!frozen.contains(key)) {
                break
            }
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        return streak
    }

    private fun refreshSetup() {
        val dnd = Dnd.hasAccess(this)
        dndState.text =
            if (dnd) "Allowed · switches on with lock-in" else "Not allowed · DND will not switch"
        dndAction.text = if (dnd) "ON" else "ALLOW"
        dndAction.setTextColor(getColor(if (dnd) R.color.accent_dim else R.color.accent))

        val overlay = Settings.canDrawOverlays(this)
        overlayState.text = if (overlay) {
            "Allowed · break brings Doom Lock back"
        } else {
            "Not allowed · break returns via notification"
        }
        overlayAction.text = if (overlay) "ON" else "ALLOW"
        overlayAction.setTextColor(getColor(if (overlay) R.color.accent_dim else R.color.accent))

        val media = MediaWatcher.hasAccess(this)
        npState.text = if (media) {
            "Allowed · shows the song and can skip it"
        } else {
            "Not allowed · no now playing on the focus screen"
        }
        npAction.text = if (media) "ON" else "ALLOW"
        npAction.setTextColor(getColor(if (media) R.color.accent_dim else R.color.accent))

        pinState.text = "Pin windows must be on, with a PIN to unpin"
        pinAction.text = "SETTINGS"

        // Once everything is allowed the list folds itself away; SHOW brings it back.
        val collapsed = when (settings().getInt(KEY_SETUP_CHOICE, CHOICE_AUTO)) {
            CHOICE_SHOW -> false
            CHOICE_HIDE -> true
            else -> dnd && overlay
        }
        setupBody.visibility = if (collapsed) View.GONE else View.VISIBLE
        setupToggle.text = if (collapsed) "SHOW" else "HIDE"
        setupLabel.text = "SETUP"
    }

    private fun toggleSetup() {
        val collapsed = setupBody.visibility == View.GONE
        settings().edit()
            .putInt(KEY_SETUP_CHOICE, if (collapsed) CHOICE_SHOW else CHOICE_HIDE)
            .apply()
        refreshSetup()
    }

    // ------------------------------------------------------------------ goal picker

    private fun selectTarget(index: Int) {
        targetIndex = index
        settings().edit().putInt(KEY_TARGET_INDEX, index).apply()
        styleChips()
    }

    private fun styleChips() {
        chipIds.forEachIndexed { index, id ->
            val chip = findViewById<TextView>(id)
            val selected = index == targetIndex
            chip.setBackgroundResource(
                if (selected) R.drawable.bg_chip_on else R.drawable.bg_chip_off
            )
            chip.setTextColor(getColor(if (selected) R.color.accent else R.color.dim))
        }
    }

    // ------------------------------------------------------------------ actions

    private fun startLockIn() {
        SessionStore.load(this)
        if (SessionStore.isActive) {
            val target = if (SessionStore.phase == Phase.PAUSED) {
                PauseActivity::class.java
            } else {
                LockActivity::class.java
            }
            startActivity(Intent(this, target))
            return
        }
        if (!Dnd.hasAccess(this)) {
            Toast.makeText(
                this,
                "Give Doom Lock Do Not Disturb access first, otherwise the phone keeps buzzing.",
                Toast.LENGTH_LONG
            ).show()
        }
        SessionStore.start(this, System.currentTimeMillis(), targets[targetIndex])
        Dnd.alarmsOnly(this)
        startActivity(Intent(this, LockActivity::class.java))
    }

    private fun settings() = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    private fun openDndSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun openOverlaySettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun openNotificationAccess() {
        runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    private fun openSecuritySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
    }
}

package com.lockin

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.lockin.ui.DotGridProgressView
import com.lockin.ui.DotMatrixView
import com.lockin.ui.HoldButton

class LockActivity : Activity() {

    companion object {
        private const val DIM = 0.05f
        private const val BRIGHT = 0.85f
        private const val BRIGHT_MS = 4000L
        private const val SHIFT_MS = 60_000L

        fun intent(context: Context): Intent =
            Intent(context, LockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
    }

    private lateinit var timer: DotMatrixView
    private lateinit var goalGrid: DotGridProgressView
    private lateinit var goalLine: TextView
    private lateinit var dndLine: TextView
    private lateinit var metaLine: TextView
    private lateinit var nowPlaying: View
    private lateinit var npTitle: TextView
    private lateinit var npArtist: TextView
    private lateinit var doomBtn: TextView
    private lateinit var doomHint: TextView
    private lateinit var panelBtn: TextView
    private lateinit var appsRow: ViewGroup
    private lateinit var content: View
    private lateinit var bottom: View

    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var shiftIndex = 0
    private var leftForApp = false
    private var appAttempt = 0
    private var goalCued = false
    private var cachedDay: String? = null
    private var cachedFocusBefore = 0L
    private var nowPlayingKey: String? = null

    private val tick = object : Runnable {
        override fun run() {
            if (!resumed) return
            render(System.currentTimeMillis())
            // Once a panel pass or a break is over the screen pins itself again.
            if (SessionStore.phase == Phase.FOCUS && !inLockTask()) pin()
            handler.postDelayed(this, 1000L)
        }
    }

    private val dim = Runnable {
        if (resumed) setBrightness(DIM)
    }

    /** Nudges the content a few pixels every minute so the panel does not burn in. */
    private val shift = object : Runnable {
        override fun run() {
            if (!resumed) return
            val offsets = arrayOf(
                intArrayOf(0, 0), intArrayOf(-5, 4), intArrayOf(4, -3),
                intArrayOf(3, 5), intArrayOf(-4, -4), intArrayOf(5, 2)
            )
            val offset = offsets[shiftIndex % offsets.size]
            shiftIndex++
            val dx = dp(offset[0])
            val dy = dp(offset[1])
            content.animate().translationX(dx).translationY(dy).setDuration(700L).start()
            bottom.animate().translationX(-dx).setDuration(700L).start()
            handler.postDelayed(this, SHIFT_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()

        timer = findViewById(R.id.timer)
        timer.dotColor = getColor(R.color.accent)
        timer.offDotColor = 0x14D71921

        goalGrid = findViewById(R.id.goalGrid)
        goalGrid.litColor = getColor(R.color.accent)
        goalGrid.frameColor = 0x40D71921
        goalGrid.offColor = 0x0AD71921
        goalLine = findViewById(R.id.goalLine)

        dndLine = findViewById(R.id.dndLine)
        metaLine = findViewById(R.id.metaLine)

        nowPlaying = findViewById(R.id.nowPlaying)
        npTitle = findViewById(R.id.npTitle)
        npArtist = findViewById(R.id.npArtist)

        doomBtn = findViewById(R.id.doomBtn)
        doomHint = findViewById(R.id.doomHint)
        panelBtn = findViewById(R.id.panelBtn)
        appsRow = findViewById(R.id.appsRow)
        content = findViewById(R.id.lockContent)
        bottom = findViewById(R.id.lockBottom)

        findViewById<View>(R.id.lockRoot).setOnClickListener { brighten() }
        doomBtn.setOnClickListener { startBreak() }
        panelBtn.setOnClickListener { onPanelTap() }
        findViewById<View>(R.id.npNext).setOnClickListener { MediaWatcher.skipNext() }
        buildAppSlots()

        findViewById<HoldButton>(R.id.pauseBtn).apply {
            label = "HOLD TO PAUSE"
            holdMillis = 2000L
            labelColor = getColor(R.color.text)
            progressColor = getColor(R.color.text)
            onComplete = { startPause() }
        }
        findViewById<HoldButton>(R.id.endBtn).apply {
            label = "HOLD TO END"
            holdMillis = 3000L
            labelColor = getColor(R.color.accent)
            progressColor = getColor(R.color.accent)
            onComplete = { endSession() }
        }

        SessionStore.load(this)
        if (!SessionStore.isActive) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        SessionStore.load(this)
        val now = System.currentTimeMillis()
        when (SessionStore.phase) {
            Phase.IDLE -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return
            }

            Phase.PAUSED -> {
                startActivity(Intent(this, PauseActivity::class.java))
                finish()
                return
            }

            // Opening the app during a break ends it early; only the used time is charged.
            Phase.BREAK -> {
                SessionStore.endBreak(this, now)
                Dnd.alarmsOnly(this)
                SessionService.stop(this)
            }

            Phase.APPS -> {
                SessionStore.endApps(this, now)
                SessionService.stop(this)
            }

            // Coming back early ends the panel pass and the screen pins itself again.
            Phase.PANEL -> {
                SessionStore.endPanelPass(this, now)
                SessionService.stop(this)
            }

            Phase.FOCUS -> Unit
        }

        pin()
        setBrightness(DIM)
        render(now)
        MediaWatcher.listener = { renderNowPlaying() }
        MediaWatcher.refresh(this)
        handler.removeCallbacks(tick)
        handler.post(tick)
        handler.removeCallbacks(shift)
        handler.postDelayed(shift, SHIFT_MS)
    }

    override fun onPause() {
        resumed = false
        leftForApp = true
        MediaWatcher.listener = null
        handler.removeCallbacks(tick)
        handler.removeCallbacks(dim)
        handler.removeCallbacks(shift)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && resumed) immersive()
    }

    /** Back is swallowed: leaving is only possible through the hold-to-end button. */
    @Deprecated("Screen pinning already blocks it, this covers devices with pinning off")
    override fun onBackPressed() {
        // Intentionally empty.
    }

    // ---------------------------------------------------------------- rendering

    private fun render(now: Long) {
        val focus = SessionStore.focusMs(now)
        timer.text = Fmt.hms(focus)
        renderGoal(focus)
        dndLine.text = "Do not disturb on, since ${Fmt.timeOfDay(SessionStore.dndSince)}"
        val today = focusBeforeToday(now) + SessionStore.focusMs(now)
        metaLine.text = "TODAY  ${Fmt.human(today).uppercase()}"
        renderNowPlaying()
        renderPanelRow(now)

        val bank = SessionStore.bankMs(now)
        if (bank >= 1000L) {
            doomBtn.text = "DOOMSCROLL  ·  ${Fmt.human(bank)}"
            doomBtn.isEnabled = true
            doomBtn.alpha = 1f
            doomHint.text = "10 minutes to start, then 1 minute every 6"
        } else {
            doomBtn.text = "DOOMSCROLL"
            doomBtn.isEnabled = false
            doomBtn.alpha = 0.35f
            doomHint.text = "Refills in ${Fmt.clock((60_000L - bank) * 6L)}"
        }
    }

    /** The quick panel row: a normal offer, then a live countdown while the pass is running. */
    private fun renderPanelRow(now: Long) {
        if (SessionStore.phase == Phase.PANEL) {
            panelBtn.text = "PANEL PASS  ·  ${Fmt.clock(SessionStore.panelLeftMs(now))}  ·  TAP TO RETURN"
            panelBtn.setTextColor(getColor(R.color.accent))
            panelBtn.setBackgroundResource(R.drawable.bg_btn_red_outline)
        } else {
            panelBtn.text = "QUICK PANEL  ·  1 MIN"
            panelBtn.setTextColor(getColor(R.color.dim))
            panelBtn.setBackgroundResource(R.drawable.bg_btn_outline)
        }
    }

    /** The hollow dot rectangle fills in row by row as the session works towards its goal. */
    private fun renderGoal(focus: Long) {
        val planned = SessionStore.plannedMs
        if (planned <= 0L) {
            goalGrid.visibility = View.GONE
            goalLine.visibility = View.GONE
            return
        }
        val reached = focus >= planned
        goalGrid.visibility = View.VISIBLE
        goalLine.visibility = View.VISIBLE
        goalGrid.progress = focus.toFloat() / planned.toFloat()
        goalLine.text = if (reached) {
            "GOAL ${Fmt.human(planned).uppercase()} DONE  ·  HOLD TO END"
        } else {
            "GOAL ${Fmt.human(planned).uppercase()}  ·  " +
                "${Fmt.human(planned - focus).uppercase()} TO GO"
        }
        goalLine.setTextColor(getColor(if (reached) R.color.accent else R.color.faint))
        if (reached && !goalCued) {
            goalCued = true
            celebrateGoal()
        }
    }

    /** A short double buzz and a few flashes the moment the goal is hit. */
    private fun celebrateGoal() {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 60, 90, 60, 90, 140), -1)
            )
        }
        flash(0)
    }

    private fun flash(step: Int) {
        if (step >= 4) {
            goalLine.alpha = 1f
            return
        }
        goalLine.animate().alpha(0.15f).setDuration(140).withEndAction {
            goalLine.animate().alpha(1f).setDuration(140).withEndAction {
                flash(step + 1)
            }.start()
        }.start()
    }

    private fun renderNowPlaying() {
        val title = MediaWatcher.title
        if (title.isNullOrBlank()) {
            if (nowPlaying.visibility != View.GONE) {
                nowPlaying.visibility = View.GONE
                nowPlayingKey = null
            }
            return
        }
        val artist = MediaWatcher.artist.orEmpty()
        val key = "$title|$artist"
        if (key == nowPlayingKey && nowPlaying.visibility == View.VISIBLE) return
        nowPlayingKey = key
        nowPlaying.visibility = View.VISIBLE
        npTitle.text = title
        npArtist.text = artist
        npArtist.visibility = if (artist.isBlank()) View.GONE else View.VISIBLE
    }

    /** Finished sessions for today, cached so the one second ticker never hits SQLite. */
    private fun focusBeforeToday(now: Long): Long {
        val day = Fmt.dayKey(now)
        val cached = cachedDay
        if (cached != day) {
            cachedDay = day
            cachedFocusBefore = HistoryDb.get(this).dayTotals(day)[0]
        }
        return cachedFocusBefore
    }

    /** The three allowed apps, whatever they are, drawn with their own icon and name. */
    private fun buildAppSlots() {
        appsRow.removeAllViews()
        val density = resources.displayMetrics.density
        AllowedApps.slots(this).forEach { packageName ->
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(
                    0, (8 * density).toInt(), 0, (8 * density).toInt()
                )
            }
            val size = (24 * density).toInt()
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size)
                contentDescription = AllowedApps.label(this@LockActivity, packageName)
                setImageDrawable(AllowedApps.icon(this@LockActivity, packageName))
            }
            val name = TextView(this).apply {
                text = AllowedApps.label(this@LockActivity, packageName)
                setTextColor(getColor(R.color.faint))
                textSize = 9f
                letterSpacing = 0.08f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (6 * density).toInt() }
            }
            column.addView(icon)
            column.addView(name)
            column.setOnClickListener { openAllowedApp(packageName) }
            appsRow.addView(column)
        }
    }

    // ---------------------------------------------------------------- actions

    private fun startBreak() {
        val now = System.currentTimeMillis()
        SessionStore.load(this)
        if (SessionStore.phase != Phase.FOCUS) return
        if (!SessionStore.beginBreak(this, now)) {
            Toast.makeText(this, "No doomscroll time banked yet", Toast.LENGTH_SHORT).show()
            return
        }
        Dnd.off(this)
        unpin()
        SessionService.start(this, SessionService.MODE_BREAK)
        TodayWidget.refresh(this)
        goHome()
    }

    /**
     * Lets the quick panel down for one minute by releasing the pin. Focus freezes and the minute
     * counts as unlocked time, so it never inflates the session. Tapping again returns early.
     */
    private fun onPanelTap() {
        SessionStore.load(this)
        val now = System.currentTimeMillis()
        when (SessionStore.phase) {
            Phase.FOCUS -> {
                SessionStore.beginPanelPass(this, now)
                unpin()
                SessionService.start(this, SessionService.MODE_PANEL)
                TodayWidget.refresh(this)
                render(now)
            }

            Phase.PANEL -> {
                SessionStore.endPanelPass(this, now)
                SessionService.stop(this)
                pin()
                render(now)
                TodayWidget.refresh(this)
            }

            else -> Unit
        }
    }

    private fun startPause() {
        val now = System.currentTimeMillis()
        SessionStore.load(this)
        if (SessionStore.phase != Phase.FOCUS) return
        SessionStore.beginPause(this, now)
        Dnd.off(this)
        unpin()
        SessionService.start(this, SessionService.MODE_PAUSED)
        TodayWidget.refresh(this)
        startActivity(Intent(this, PauseActivity::class.java))
    }

    private fun endSession() {
        val now = System.currentTimeMillis()
        SessionStore.load(this)
        val result = SessionStore.end(this, now) ?: return
        HistoryDb.get(this).addSession(result)
        Dnd.restore(this, result.prevFilter)
        unpin()
        SessionService.stop(this)
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        startActivity(Intent(this, SessionSummaryActivity::class.java))
        finish()
    }

    /**
     * Tries to open the app inside the pinned task. If Android refuses, we release the pin for
     * a ten minute window: focus keeps running, DND stays on and no doomscroll time is used.
     */
    private fun openAllowedApp(packageName: String) {
        SessionStore.load(this)
        if (SessionStore.phase != Phase.FOCUS) return
        val intent = AllowedApps.launchIntent(this, packageName)
        if (intent == null) {
            Toast.makeText(
                this, "${AllowedApps.label(this, packageName)} is not installed",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appAttempt++
        val attempt = appAttempt
        leftForApp = false
        runCatching { startActivity(intent) }
        handler.postDelayed({
            if (attempt != appAttempt) return@postDelayed
            if (!leftForApp && resumed && SessionStore.phase == Phase.FOCUS) {
                enterAppsWindow(intent)
            }
        }, 1000L)
    }

    private fun enterAppsWindow(intent: Intent) {
        SessionStore.load(this)
        if (SessionStore.phase != Phase.FOCUS) return
        SessionStore.beginApps(this, System.currentTimeMillis())
        unpin()
        SessionService.start(this, SessionService.MODE_APPS)
        runCatching { startActivity(intent) }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
    }

    // ---------------------------------------------------------------- screen and pinning

    private fun brighten() {
        setBrightness(BRIGHT)
        handler.removeCallbacks(dim)
        handler.postDelayed(dim, BRIGHT_MS)
    }

    private fun setBrightness(value: Float) {
        val params = window.attributes
        params.screenBrightness = value
        window.attributes = params
    }

    private fun pin() {
        if (inLockTask()) return
        runCatching { startLockTask() }
    }

    private fun unpin() {
        if (!inLockTask()) return
        runCatching { stopLockTask() }
    }

    private fun inLockTask(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun immersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density
}

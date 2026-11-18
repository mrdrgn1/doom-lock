package com.lockin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.lockin.ui.DotMatrixView
import com.lockin.ui.HoldButton

class PauseActivity : Activity() {

    private lateinit var timer: DotMatrixView
    private lateinit var since: TextView
    private lateinit var meta: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false

    private val tick = object : Runnable {
        override fun run() {
            if (!resumed) return
            render()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pause)

        timer = findViewById(R.id.pauseTimer)
        timer.dotColor = getColor(R.color.accent)
        timer.offDotColor = 0x14D71921
        since = findViewById(R.id.pauseSince)
        meta = findViewById(R.id.pauseMeta)

        findViewById<TextView>(R.id.resumeBtn).setOnClickListener { resume() }
        findViewById<HoldButton>(R.id.endBtn).apply {
            label = "HOLD TO END LOCK-IN"
            holdMillis = 3000L
            labelColor = getColor(R.color.accent)
            progressColor = getColor(R.color.accent)
            onComplete = { endSession() }
        }

        SessionStore.load(this)
        if (SessionStore.phase != Phase.PAUSED) finish()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        SessionStore.load(this)
        if (SessionStore.phase != Phase.PAUSED) {
            finish()
            return
        }
        render()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val paused = SessionStore.pauseMs(now)
        timer.text = Fmt.hms(paused)
        since.text = "Paused since ${Fmt.timeOfDay(now - paused)}"
        meta.text = "FOCUS ${Fmt.human(SessionStore.focusMs(now)).uppercase()}" +
            "   ·   BREAK ${Fmt.human(SessionStore.breakMs(now)).uppercase()}"
    }

    private fun resume() {
        val now = System.currentTimeMillis()
        SessionStore.load(this)
        if (SessionStore.phase != Phase.PAUSED) {
            finish()
            return
        }
        SessionStore.endPause(this, now)
        Dnd.alarmsOnly(this)
        SessionService.stop(this)
        TodayWidget.refresh(this)
        startActivity(Intent(this, LockActivity::class.java))
        finish()
    }

    private fun endSession() {
        val now = System.currentTimeMillis()
        SessionStore.load(this)
        val result = SessionStore.end(this, now) ?: return
        HistoryDb.get(this).addSession(result)
        Dnd.restore(this, result.prevFilter)
        SessionService.stop(this)
        startActivity(Intent(this, SessionSummaryActivity::class.java))
        finish()
    }
}

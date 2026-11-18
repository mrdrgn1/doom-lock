package com.lockin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.lockin.ui.DotMatrixView
import com.lockin.ui.SessionTimelineView

/** Shown right after a session ends: how long you were locked in, how long you were not. */
class SessionSummaryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary)

        val timer = findViewById<DotMatrixView>(R.id.summaryTimer)
        timer.dotColor = getColor(R.color.accent)
        timer.offDotColor = 0x14D71921

        val timeline = findViewById<SessionTimelineView>(R.id.summaryTimeline)
        timeline.focusColor = getColor(R.color.accent)
        timeline.unlockedColor = getColor(R.color.stroke_bright)

        findViewById<TextView>(R.id.summaryDone).setOnClickListener { goHome() }

        val detail = HistoryDb.get(this).lastSession()
        if (detail == null) {
            goHome()
            return
        }

        val unlocked = detail.breakMs + detail.pauseMs
        val span = (detail.endMs - detail.startMs).coerceAtLeast(detail.focusMs + unlocked)

        timer.text = Fmt.hms(detail.focusMs)
        findViewById<TextView>(R.id.lockedValue).text = Fmt.human(detail.focusMs)
        findViewById<TextView>(R.id.unlockedValue).text = if (unlocked <= 0L) {
            "none"
        } else {
            "${Fmt.human(unlocked)}  ·  ${Fmt.human(detail.breakMs)} break, " +
                "${Fmt.human(detail.pauseMs)} pause"
        }
        findViewById<TextView>(R.id.spanValue).text =
            "${Fmt.timeOfDay(detail.startMs)} – ${Fmt.timeOfDay(detail.endMs)}" +
                "   ·   ${Fmt.human(span)}"
        findViewById<TextView>(R.id.lockedShare).text =
            "${share(detail.focusMs, span)}% of the session locked in"

        timeline.bind(object : SessionTimelineView.SessionDetailLike {
            override val startMs = detail.startMs
            override val endMs = detail.endMs
            override val focusMs = detail.focusMs
            override val unlockedMs = unlocked
            override val segments = detail.segments
        })

        TodayWidget.refresh(this)
    }

    private fun share(value: Long, total: Long): Long =
        if (total <= 0L) 0L else Math.round(value * 100.0 / total)

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    @Deprecated("Same as the DONE button")
    override fun onBackPressed() {
        goHome()
    }
}

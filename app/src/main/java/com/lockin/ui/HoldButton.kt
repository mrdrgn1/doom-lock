package com.lockin.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Minimal outline button that only fires after being held down, drawing a red progress
 * fill while the user keeps their finger on it. Used for pause (2s) and end (3s).
 */
class HoldButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var holdMillis: Long = 2000L

    var labelColor: Int = Color.parseColor("#EDEDED")
        set(value) {
            field = value
            invalidate()
        }

    var progressColor: Int = Color.parseColor("#D71921")

    var onComplete: (() -> Unit)? = null

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.parseColor("#333333")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var downAt = 0L
    private var progress = 0f
    private var holding = false
    private var ticked = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!holding) return
            val elapsed = SystemClock.uptimeMillis() - downAt
            progress = (elapsed.toFloat() / holdMillis.toFloat()).coerceIn(0f, 1f)
            // One small tick a second in, so the hold feels like it is registering.
            if (!ticked && elapsed >= 1000L) {
                ticked = true
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            invalidate()
            if (progress >= 1f) {
                holding = false
                progress = 0f
                invalidate()
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onComplete?.invoke()
            } else {
                postDelayed(this, 16L)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                holding = true
                ticked = false
                downAt = SystemClock.uptimeMillis()
                progress = 0f
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                removeCallbacks(ticker)
                post(ticker)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                holding = false
                progress = 0f
                removeCallbacks(ticker)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Accessibility services trigger this instead of a touch gesture. */
    override fun performClick(): Boolean {
        super.performClick()
        holding = false
        progress = 0f
        removeCallbacks(ticker)
        invalidate()
        onComplete?.invoke()
        return true
    }

    override fun onDetachedFromWindow() {
        holding = false
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (label.isEmpty()) return
        val radius = dp(14f)
        val inset = dp(0.75f)
        val rect = RectF(inset, inset, width - inset, height - inset)

        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        if (progress > 0f) {
            val fill = RectF(rect.left, rect.top, rect.left + rect.width() * progress, rect.bottom)
            val checkpoint = canvas.save()
            canvas.clipRect(fill)
            fillPaint.color = progressColor
            fillPaint.alpha = 40
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            canvas.restoreToCount(checkpoint)

            fillPaint.alpha = 255
            canvas.drawRect(
                fill.right - dp(1.5f), rect.top + dp(7f),
                fill.right, rect.bottom - dp(7f), fillPaint
            )
        }

        labelPaint.color = labelColor
        labelPaint.textSize = sp(11.5f)
        labelPaint.letterSpacing = 0.08f
        val metrics = labelPaint.fontMetrics
        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, width / 2f, baseline, labelPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics
    )
}

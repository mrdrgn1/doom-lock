package com.lockin.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.lockin.SessionSegment

/**
 * A thin horizontal bar of the whole session: red where you were locked in, grey where you were
 * not, so you can see when the breaks happened.
 */
class SessionTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var segments: List<SessionSegment> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var spanStart = 0L
        set(value) {
            field = value
            invalidate()
        }

    var spanEnd = 0L
        set(value) {
            field = value
            invalidate()
        }

    /** Used when there is no segment history, e.g. after the app was closed mid-session. */
    var fallbackFocusMs = 0L
    var fallbackUnlockedMs = 0L

    var focusColor: Int = 0xFFD71921.toInt()
        set(value) {
            field = value
            invalidate()
        }

    var unlockedColor: Int = 0xFF2A2A2A.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val clip = Path()
    private val bounds = RectF()

    fun bind(detail: SessionDetailLike) {
        spanStart = detail.startMs
        spanEnd = detail.endMs
        fallbackFocusMs = detail.focusMs
        fallbackUnlockedMs = detail.unlockedMs
        segments = detail.segments
    }

    interface SessionDetailLike {
        val startMs: Long
        val endMs: Long
        val focusMs: Long
        val unlockedMs: Long
        val segments: List<SessionSegment>
    }

    override fun onDraw(canvas: Canvas) {
        val span = spanEnd - spanStart
        if (span <= 0L || width <= 0) return
        val radius = height / 2f
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        clip.reset()
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)

        if (segments.isEmpty()) {
            val lockedWidth = width * (fallbackFocusMs.toFloat() / span.toFloat())
            paint.color = focusColor
            canvas.drawRect(0f, 0f, lockedWidth, height.toFloat(), paint)
            paint.color = unlockedColor
            canvas.drawRect(lockedWidth, 0f, width.toFloat(), height.toFloat(), paint)
        } else {
            segments.forEach { segment ->
                val from = (segment.startMs - spanStart).coerceIn(0L, span)
                val to = (segment.endMs - spanStart).coerceIn(from, span)
                val x0 = width * (from.toFloat() / span.toFloat())
                val x1 = width * (to.toFloat() / span.toFloat())
                paint.color = when (segment.kind) {
                    'F' -> focusColor
                    'A' -> 0xFF3A3A3A.toInt()
                    else -> unlockedColor
                }
                canvas.drawRect(x0, 0f, maxOf(x1, x0 + 1.5f), height.toFloat(), paint)
            }
        }
        canvas.restore()
    }
}

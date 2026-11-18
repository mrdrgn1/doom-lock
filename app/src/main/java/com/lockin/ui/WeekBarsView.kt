package com.lockin.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Seven bars, Monday to Sunday, scaled to the best day of the week. Today is the red one. */
class WeekBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var values: LongArray = LongArray(7)
        set(value) {
            field = value
            invalidate()
        }

    var todayIndex: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    var barColor: Int = 0xFFD71921.toInt()
        set(value) {
            field = value
            invalidate()
        }

    var restColor: Int = 0xFF3A3A3A.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val slot = width / 7f
        val barWidth = slot * 0.42f
        val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val stub = dp(2f)
        val radius = dp(2f)

        for (index in 0 until 7) {
            val value = values.getOrElse(index) { 0L }
            val barHeight = if (value <= 0L) {
                stub
            } else {
                (height * (value.toFloat() / max.toFloat())).coerceAtLeast(dp(4f))
            }
            val left = index * slot + (slot - barWidth) / 2f
            val top = height - barHeight
            paint.color = if (index == todayIndex) barColor else restColor
            canvas.drawRoundRect(left, top, left + barWidth, height.toFloat(), radius, radius, paint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

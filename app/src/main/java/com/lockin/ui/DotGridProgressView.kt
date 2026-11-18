package com.lockin.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * A rectangular field of dots. The outer ring of dots is always drawn as a dim frame, so an
 * empty grid reads as a hollow rectangle. As progress grows, dots light up in reading order
 * (top row first, left to right) until the whole rectangle is solid.
 */
class DotGridProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var columns: Int = 16
        set(value) {
            field = value.coerceAtLeast(1)
            requestLayout()
            invalidate()
        }

    var rows: Int = 4
        set(value) {
            field = value.coerceAtLeast(1)
            requestLayout()
            invalidate()
        }

    var litColor: Int = 0xFFD71921.toInt()
        set(value) {
            field = value
            invalidate()
        }

    /** Dots on the outline that are not filled yet. */
    var frameColor: Int = 0x40D71921
        set(value) {
            field = value
            invalidate()
        }

    /** Dots inside the outline that are not filled yet. */
    var offColor: Int = 0x0AD71921
        set(value) {
            field = value
            invalidate()
        }

    /** 0 = hollow frame only, 1 = every dot lit. */
    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped != field) {
                field = clamped
                invalidate()
            }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val cell = width.toFloat() / columns
        val desired = (cell * rows).toInt()
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desired, MeasureSpec.getSize(heightMeasureSpec))
            else -> desired
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val cell = minOf(width.toFloat() / columns, height.toFloat() / rows)
        if (cell <= 0f) return
        val total = columns * rows
        val litCount = Math.round(progress * total)
        val radius = cell * 0.3f
        val left = (width - cell * columns) / 2f
        val top = (height - cell * rows) / 2f

        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val index = row * columns + col
                paint.color = when {
                    index < litCount -> litColor
                    row == 0 || row == rows - 1 || col == 0 || col == columns - 1 -> frameColor
                    else -> offColor
                }
                val cx = left + col * cell + cell / 2f
                val cy = top + row * cell + cell / 2f
                canvas.drawCircle(cx, cy, radius, paint)
            }
        }
    }
}

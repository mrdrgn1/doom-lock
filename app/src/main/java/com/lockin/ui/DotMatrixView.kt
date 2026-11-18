package com.lockin.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Draws "HH:MM:SS" (or any digits, colon and space) on a 5x7 dot matrix.
 * Unlit dots are drawn very faintly so the display keeps its LCD texture.
 */
class DotMatrixView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var dotColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var offDotColor: Int = Color.argb(20, 255, 255, 255)
        set(value) {
            field = value
            invalidate()
        }

    private var value: String = "00:00:00"

    var text: String
        get() = value
        set(newValue) {
            if (newValue != value) {
                value = newValue
                requestLayout()
                invalidate()
            }
        }

    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val columns = columnsFor(value)
        val cell = width.toFloat() / columns
        val desired = (cell * ROWS).toInt()
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desired, heightSize)
            else -> desired
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val columns = columnsFor(value)
        if (columns <= 0) return
        val cell = minOf(width.toFloat() / columns, height.toFloat() / ROWS)
        val blockWidth = cell * columns
        val blockHeight = cell * ROWS
        val startX = (width - blockWidth) / 2f
        val startY = (height - blockHeight) / 2f
        val radius = cell * 0.33f

        onPaint.color = dotColor
        offPaint.color = offDotColor

        var x = startX
        for (char in value) {
            val glyph = GLYPHS[char] ?: GLYPHS[' ']!!
            val glyphWidth = glyph[0].length
            for (row in 0 until ROWS) {
                val line = glyph[row]
                for (col in 0 until glyphWidth) {
                    val lit = line[col] == '1'
                    val cx = x + col * cell + cell / 2f
                    val cy = startY + row * cell + cell / 2f
                    canvas.drawCircle(cx, cy, radius, if (lit) onPaint else offPaint)
                }
            }
            x += glyphWidth * cell + cell
        }
    }

    private fun columnsFor(s: String): Int {
        var total = 0
        for (char in s) {
            total += (GLYPHS[char]?.get(0)?.length ?: 1) + 1
        }
        return (total - 1).coerceAtLeast(1)
    }

    private companion object {
        const val ROWS = 7

        val GLYPHS: Map<Char, Array<String>> = mapOf(
            'A' to arrayOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
            'B' to arrayOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
            'C' to arrayOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
            'D' to arrayOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
            'E' to arrayOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
            'F' to arrayOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
            'G' to arrayOf("01110", "10001", "10000", "10111", "10001", "10001", "01110"),
            'H' to arrayOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
            'I' to arrayOf("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
            'J' to arrayOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
            'K' to arrayOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
            'L' to arrayOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
            'M' to arrayOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
            'N' to arrayOf("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
            'O' to arrayOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
            'P' to arrayOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
            'Q' to arrayOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
            'R' to arrayOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
            'S' to arrayOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
            'T' to arrayOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
            'U' to arrayOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
            'V' to arrayOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
            'W' to arrayOf("10001", "10001", "10001", "10101", "10101", "11011", "10001"),
            'X' to arrayOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
            'Y' to arrayOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
            'Z' to arrayOf("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
            '0' to arrayOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
            '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
            '2' to arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
            '3' to arrayOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
            '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
            '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
            '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
            '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
            '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
            '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
            ':' to arrayOf("0", "0", "1", "0", "1", "0", "0"),
            ' ' to arrayOf("0", "0", "0", "0", "0", "0", "0")
        )
    }
}

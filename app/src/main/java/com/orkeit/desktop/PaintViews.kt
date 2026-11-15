package com.orkeit.desktop

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View
import kotlin.math.abs

internal object Win11PaintTheme {
    val MICA_BG = Color.rgb(243, 245, 249)          // #F3F5F9
    val RIBBON_BG = Color.rgb(249, 250, 253)        // #F9FAFD
    val CANVAS_STAGE_BG = Color.rgb(229, 234, 241)  // #E5EAF1
    val ACCENT_BLUE = Color.rgb(0, 103, 192)        // #0067C0
    val ACCENT_SOFT = Color.rgb(224, 238, 252)      // #E0EEFC
    val BORDER_SUBTLE = Color.rgb(218, 224, 234)    // #DAE0EA
    val TEXT_DARK = Color.rgb(28, 34, 46)
    val TEXT_MUTED = Color.rgb(98, 108, 124)

    val ROW1_COLORS = listOf(
        Color.BLACK,
        Color.rgb(127, 127, 127),
        Color.rgb(136, 0, 21),
        Color.rgb(237, 28, 36),
        Color.rgb(255, 127, 39),
        Color.rgb(255, 242, 0),
        Color.rgb(34, 177, 76),
        Color.rgb(0, 162, 232),
        Color.rgb(63, 72, 204),
        Color.rgb(163, 73, 164)
    )

    val ROW2_COLORS = listOf(
        Color.WHITE,
        Color.rgb(195, 195, 195),
        Color.rgb(185, 122, 87),
        Color.rgb(255, 174, 201),
        Color.rgb(255, 201, 14),
        Color.rgb(239, 228, 176),
        Color.rgb(181, 230, 29),
        Color.rgb(153, 217, 234),
        Color.rgb(112, 146, 190),
        Color.rgb(200, 191, 231)
    )

    fun toolButtonBg(d: Float, selected: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (selected) ACCENT_SOFT else Color.TRANSPARENT)
        cornerRadius = 6f * d
        if (selected) setStroke((1.2f * d).toInt().coerceAtLeast(1), ACCENT_BLUE)
    }

    fun swatchCircle(d: Float, color: Int, selected: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        val strokeW = if (selected) (2f * d).toInt().coerceAtLeast(2) else (1f * d).toInt().coerceAtLeast(1)
        val strokeColor = when {
            selected -> ACCENT_BLUE
            color == Color.WHITE -> Color.rgb(196, 204, 216)
            else -> Color.rgb(208, 214, 224)
        }
        setStroke(strokeW, strokeColor)
    }

    fun primarySwatchCircle(d: Float, color: Int, active: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(
            if (active) (2.2f * d).toInt().coerceAtLeast(2) else (1.2f * d).toInt().coerceAtLeast(1),
            if (active) ACCENT_BLUE else Color.rgb(188, 196, 210)
        )
    }
}

internal class RainbowWheelView(context: Context, private val d: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width * 0.5f
        val cy = height * 0.5f
        val r = (minOf(width, height) * 0.5f) - 2f * d
        if (r <= 0f) return
        paint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                Color.BLUE, Color.MAGENTA, Color.RED
            ),
            null
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.WHITE, Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.72f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
        canvas.drawCircle(cx, cy, r * 0.22f, centerDotPaint)
    }
}

internal object PaintFloodFiller {
    fun floodFill(bmp: Bitmap, startX: Int, startY: Int, fillColor: Int): Boolean {
        val w = bmp.width
        val h = bmp.height
        if (startX !in 0 until w || startY !in 0 until h) return false
        val targetColor = bmp.getPixel(startX, startY)
        if (targetColor == fillColor) return false

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val tr = Color.red(targetColor)
        val tg = Color.green(targetColor)
        val tb = Color.blue(targetColor)
        val ta = Color.alpha(targetColor)

        fun matchesTarget(c: Int): Boolean {
            if (c == targetColor) return true
            return abs(Color.red(c) - tr) <= 18 &&
                abs(Color.green(c) - tg) <= 18 &&
                abs(Color.blue(c) - tb) <= 18 &&
                abs(Color.alpha(c) - ta) <= 18
        }

        val stack = IntArray(w * h)
        var sp = 0
        stack[sp++] = startY * w + startX
        val visited = BooleanArray(w * h)
        visited[startY * w + startX] = true

        while (sp > 0) {
            val idx = stack[--sp]
            pixels[idx] = fillColor
            val x = idx % w
            val y = idx / w
            if (x > 0) {
                val n = idx - 1
                if (!visited[n] && matchesTarget(pixels[n])) {
                    visited[n] = true; stack[sp++] = n
                }
            }
            if (x + 1 < w) {
                val n = idx + 1
                if (!visited[n] && matchesTarget(pixels[n])) {
                    visited[n] = true; stack[sp++] = n
                }
            }
            if (y > 0) {
                val n = idx - w
                if (!visited[n] && matchesTarget(pixels[n])) {
                    visited[n] = true; stack[sp++] = n
                }
            }
            if (y + 1 < h) {
                val n = idx + w
                if (!visited[n] && matchesTarget(pixels[n])) {
                    visited[n] = true; stack[sp++] = n
                }
            }
        }

        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return true
    }
}

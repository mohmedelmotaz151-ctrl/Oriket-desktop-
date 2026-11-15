package com.orkeit.desktop

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class Wmp11WaveCanvasView(context: Context) : View(context) {
    var isPlayingActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isMiniThumbnail: Boolean = false
    private var phase = 0f
    private var lastFrameNs = 0L
    private var attached = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    private val frameTick = object : Runnable {
        override fun run() {
            if (!attached) return
            val now = System.nanoTime()
            val dt = if (lastFrameNs == 0L) 0.016f else ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNs = now
            val speed = if (isPlayingActive) 1.65f else 0.35f
            phase = (phase + dt * speed) % (2f * PI.toFloat() * 10f)
            invalidate()
            if (!isMiniThumbnail) {
                postDelayed(this, if (isPlayingActive) 33L else 60L)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        lastFrameNs = 0L
        if (!isMiniThumbnail) {
            removeCallbacks(frameTick)
            post(frameTick)
        }
    }

    override fun onDetachedFromWindow() {
        attached = false
        removeCallbacks(frameTick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Deep sapphire-midnight backdrop
        bgPaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.rgb(2, 11, 29),
                Color.rgb(5, 29, 68),
                Color.rgb(3, 18, 44),
                Color.rgb(1, 7, 19)
            ),
            floatArrayOf(0f, 0.42f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Core luminous cyan glow behind the sweeping silk wave
        val glowX = w * (0.42f + 0.03f * sin(phase * 0.7f))
        val glowY = h * (0.48f + 0.03f * cos(phase * 0.5f))
        bgPaint.shader = RadialGradient(
            glowX, glowY, maxOf(w, h) * 0.62f,
            intArrayOf(
                Color.argb(175, 42, 195, 245),
                Color.argb(95, 11, 110, 198),
                Color.argb(0, 2, 15, 38)
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Ribbon 1: Wide bottom-left to top-right cyan silk fold
        drawSilkRibbon(
            canvas, w, h,
            yStart = 1.05f, yCtrl1 = 0.35f + 0.05f * sin(phase), yCtrl2 = 0.55f - 0.04f * cos(phase * 0.8f), yEnd = -0.10f,
            thickness = 0.46f,
            colors = intArrayOf(Color.argb(150, 75, 225, 255), Color.argb(190, 18, 138, 224), Color.argb(25, 3, 24, 62))
        )

        // Ribbon 2: Cross-arcing deep cerulean wave fold
        drawSilkRibbon(
            canvas, w, h,
            yStart = 0.72f + 0.04f * cos(phase * 0.9f), yCtrl1 = 0.16f, yCtrl2 = 0.86f + 0.04f * sin(phase * 0.7f), yEnd = 0.25f,
            thickness = 0.38f,
            colors = intArrayOf(Color.argb(135, 135, 244, 255), Color.argb(165, 12, 118, 206), Color.argb(15, 2, 16, 44))
        )

        // Ribbon 3: Lower horizon dark navy shadow wave for depth contrast (like WMP 11 screenshot)
        drawSilkRibbon(
            canvas, w, h,
            yStart = 0.92f, yCtrl1 = 0.50f + 0.03f * sin(phase * 1.1f), yCtrl2 = 0.44f, yEnd = 0.88f,
            thickness = 0.30f,
            colors = intArrayOf(Color.argb(195, 2, 18, 46), Color.argb(155, 6, 48, 104), Color.argb(200, 1, 10, 28))
        )

        // Ribbon 4: Bright specular ice-cyan diagonal crest
        drawSilkRibbon(
            canvas, w, h,
            yStart = 1.02f, yCtrl1 = 0.62f + 0.03f * cos(phase * 1.2f), yCtrl2 = 0.28f - 0.03f * sin(phase), yEnd = 0.02f,
            thickness = 0.20f,
            colors = intArrayOf(Color.argb(165, 168, 245, 255), Color.argb(130, 38, 188, 245), Color.argb(0, 6, 65, 138))
        )

        // Crisp luminous silk edge streaks
        if (!isMiniThumbnail) {
            val energy = if (isPlayingActive) 1.0f else 0.55f
            streakPaint.shader = LinearGradient(
                0f, h, w * 0.85f, 0f,
                intArrayOf(
                    Color.argb((140 * energy).toInt(), 180, 246, 255),
                    Color.argb((180 * energy).toInt(), 82, 220, 255),
                    Color.argb(0, 15, 110, 205)
                ),
                null,
                Shader.TileMode.CLAMP
            )
            streakPaint.strokeWidth = h * 0.012f
            path.reset()
            path.moveTo(w * 0.04f, h * 1.02f)
            path.cubicTo(
                w * 0.26f, h * (0.48f + 0.04f * sin(phase)),
                w * 0.52f, h * (0.62f - 0.04f * cos(phase * 0.9f)),
                w * 0.72f, -h * 0.04f
            )
            canvas.drawPath(path, streakPaint)

            streakPaint.strokeWidth = h * 0.006f
            path.reset()
            path.moveTo(0f, h * 0.74f)
            path.cubicTo(
                w * 0.30f, h * (0.28f + 0.03f * cos(phase * 0.8f)),
                w * 0.64f, h * (0.76f + 0.03f * sin(phase * 1.1f)),
                w * 1.02f, h * 0.44f
            )
            canvas.drawPath(path, streakPaint)

            // Subtle ambient spectrum bars at bottom-center when audio is playing
            if (isPlayingActive) {
                val barCount = 28
                val totalBarW = w * 0.42f
                val step = totalBarW / barCount
                val startX = (w - totalBarW) * 0.5f
                val baseBottom = h * 0.94f
                for (i in 0 until barCount) {
                    val v = (0.35f + 0.35f * sin(phase * 3.1f + i * 0.55f) + 0.30f * cos(phase * 2.2f - i * 0.37f)).coerceIn(0.12f, 1f)
                    val bh = h * 0.13f * v
                    val bx = startX + i * step
                    barPaint.color = Color.argb((115 * v + 45).toInt().coerceIn(0, 175), 110, 232, 255)
                    canvas.drawRoundRect(bx, baseBottom - bh, bx + step * 0.62f, baseBottom, 3f, 3f, barPaint)
                }
            }
        }
    }

    private fun drawSilkRibbon(
        canvas: Canvas,
        w: Float,
        h: Float,
        yStart: Float,
        yCtrl1: Float,
        yCtrl2: Float,
        yEnd: Float,
        thickness: Float,
        colors: IntArray
    ) {
        val band = h * thickness
        path.reset()
        path.moveTo(-w * 0.05f, h * yStart)
        path.cubicTo(w * 0.30f, h * yCtrl1, w * 0.66f, h * yCtrl2, w * 1.05f, h * yEnd)
        path.lineTo(w * 1.05f, h * yEnd + band)
        path.cubicTo(w * 0.66f, h * yCtrl2 + band * 0.9f, w * 0.30f, h * yCtrl1 + band * 1.1f, -w * 0.05f, h * yStart + band)
        path.close()
        ribbonPaint.shader = LinearGradient(
            0f, h * (yStart.coerceIn(0f, 1f)),
            w, h * (yEnd.coerceIn(0f, 1f)),
            colors,
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, ribbonPaint)
    }
}

internal class Wmp11PlayOrbView(context: Context, private val d: Float) : View(context) {
    var playing: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var pressedOrb: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width * 0.5f
        val cy = height * 0.5f
        val radius = (minOf(width, height) * 0.5f) - 1.5f * d
        if (radius <= 0f) return

        // Outer metallic chrome & shadow rim
        paint.shader = LinearGradient(
            cx, cy - radius, cx, cy + radius,
            intArrayOf(Color.rgb(122, 152, 194), Color.rgb(16, 28, 48), Color.rgb(72, 116, 174)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius + 1.2f * d, paint)

        // Core 3D cobalt-to-cyan glossy sphere
        val bright = if (pressedOrb) 1.12f else 1.0f
        paint.shader = RadialGradient(
            cx, cy + radius * 0.25f, radius * 1.15f,
            intArrayOf(
                Color.rgb((105 * bright).toInt().coerceAtMost(255), (218 * bright).toInt().coerceAtMost(255), 255),
                Color.rgb(18, 108, 224),
                Color.rgb(7, 36, 98),
                Color.rgb(18, 72, 156)
            ),
            floatArrayOf(0f, 0.48f, 0.86f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius - 1.2f * d, paint)

        // Classic Vista/WMP11 upper glass specular dome
        paint.shader = LinearGradient(
            cx, cy - radius * 0.92f, cx, cy + radius * 0.08f,
            intArrayOf(Color.argb(215, 255, 255, 255), Color.argb(72, 210, 240, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        val domeRect = RectF(cx - radius * 0.78f, cy - radius * 0.88f, cx + radius * 0.78f, cy + radius * 0.04f)
        canvas.drawOval(domeRect, paint)

        // Crisp white Play / Pause glyph
        paint.shader = null
        paint.color = Color.WHITE
        paint.setShadowLayer(2f * d, 0f, 1f * d, Color.argb(160, 0, 12, 36))
        if (playing) {
            val barW = radius * 0.20f
            val barH = radius * 0.66f
            val gap = radius * 0.14f
            canvas.drawRoundRect(cx - gap * 0.5f - barW, cy - barH * 0.5f, cx - gap * 0.5f, cy + barH * 0.5f, 1.5f * d, 1.5f * d, paint)
            canvas.drawRoundRect(cx + gap * 0.5f, cy - barH * 0.5f, cx + gap * 0.5f + barW, cy + barH * 0.5f, 1.5f * d, 1.5f * d, paint)
        } else {
            val triR = radius * 0.36f
            val offsetX = radius * 0.08f
            path.reset()
            path.moveTo(cx - triR * 0.68f + offsetX, cy - triR * 0.92f)
            path.lineTo(cx + triR * 1.02f + offsetX, cy)
            path.lineTo(cx - triR * 0.68f + offsetX, cy + triR * 0.92f)
            path.close()
            canvas.drawPath(path, paint)
        }
        paint.clearShadowLayer()
    }
}

internal object Wmp11Style {
    fun darkAeroBarBackground(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.rgb(34, 46, 68),
            Color.rgb(16, 24, 38),
            Color.rgb(10, 16, 28),
            Color.rgb(20, 30, 48)
        )
    )

    fun activeTabBackground(d: Float): Drawable {
        val outer = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb(235, 58, 134, 212),
                Color.argb(235, 22, 82, 152),
                Color.argb(240, 12, 46, 94),
                Color.argb(255, 24, 118, 206)
            )
        ).apply {
            cornerRadii = floatArrayOf(4f * d, 4f * d, 4f * d, 4f * d, 0f, 0f, 0f, 0f)
            setStroke((1f * d).toInt().coerceAtLeast(1), Color.argb(200, 122, 204, 255))
        }
        val glowBottom = GradientDrawable().apply {
            setColor(Color.rgb(92, 218, 255))
        }
        val layer = LayerDrawable(arrayOf(outer, glowBottom))
        layer.setLayerInset(1, (2 * d).toInt(), (24 * d).toInt(), (2 * d).toInt(), 0)
        return layer
    }

    fun inactiveTabBackground(d: Float): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.TRANSPARENT, Color.argb(40, 180, 210, 255))
    ).apply {
        cornerRadii = floatArrayOf(4f * d, 4f * d, 4f * d, 4f * d, 0f, 0f, 0f, 0f)
    }

    fun navOrbBackground(d: Float, enabled: Boolean): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (enabled) {
            intArrayOf(Color.rgb(95, 192, 255), Color.rgb(18, 92, 198), Color.rgb(8, 44, 112))
        } else {
            intArrayOf(Color.rgb(44, 56, 78), Color.rgb(22, 30, 44), Color.rgb(16, 22, 34))
        }
    ).apply {
        shape = GradientDrawable.OVAL
        setStroke((1f * d).toInt().coerceAtLeast(1), if (enabled) Color.rgb(142, 220, 255) else Color.rgb(72, 88, 114))
    }

    fun controlPodBackground(d: Float): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.rgb(34, 46, 66),
            Color.rgb(16, 22, 34),
            Color.rgb(10, 15, 25),
            Color.rgb(22, 31, 48)
        )
    ).apply {
        cornerRadius = 18f * d
        setStroke((1f * d).toInt().coerceAtLeast(1), Color.rgb(82, 102, 134))
    }

    fun podButtonBackground(d: Float, active: Boolean = false): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (active) {
            intArrayOf(Color.rgb(42, 112, 186), Color.rgb(18, 62, 122))
        } else {
            intArrayOf(Color.rgb(44, 56, 78), Color.rgb(20, 28, 42))
        }
    ).apply {
        cornerRadius = 12f * d
        setStroke((0.8f * d).toInt().coerceAtLeast(1), if (active) Color.rgb(102, 206, 255) else Color.rgb(76, 94, 124))
    }
}

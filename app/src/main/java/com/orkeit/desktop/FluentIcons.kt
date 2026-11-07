package com.orkeit.desktop

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.*

enum class FluentIcon {
    WINDOWS_START,
    SEARCH,
    THIS_PC,
    FILE_EXPLORER,
    RECYCLE_BIN,
    SETTINGS,
    EDGE_BROWSER,
    PAINT,
    NOTEPAD,
    CALCULATOR,
    DOCUMENTS,
    SPREADSHEET,
    DOWNLOADS,
    MUSIC,
    VIDEOS,
    PHOTOS,
    PDF_READER,
    YOUTUBE,
    YOUTUBE_MUSIC,
    TASK_MANAGER,
    TERMINAL,
    GAMES,
    WINDOWS_UPDATE,
    CIVIL_DEFENSE,
    FOLDER,
    FOLDER_DOCUMENTS,
    FOLDER_DOWNLOADS,
    FOLDER_PICTURES,
    FOLDER_VIDEOS,
    FOLDER_MUSIC,
    FOLDER_DESKTOP,
    FOLDER_PROJECTS,
    HARD_DRIVE,
    SMARTPHONE,
    WIFI,
    BLUETOOTH,
    SOUND,
    DISPLAY,
    BATTERY,
    NOTIFICATIONS,
    DATETIME,
    APPS,
    WALLPAPER,
    SECURITY,
    ABOUT,
    GENERIC_WINDOW
}

object FluentIcons {

    fun getIconForTitle(title: String): FluentIcon = when {
        title.contains("YouTube Music") -> FluentIcon.YOUTUBE_MUSIC
        title.contains("YouTube") -> FluentIcon.YOUTUBE
        title.contains("مشغل الموسيقى") || title == "الموسيقى" || title.contains("Music") -> FluentIcon.MUSIC
        title.contains("متصفح") || title.contains("الإنترنت") || title.contains("المتصفح") -> FluentIcon.EDGE_BROWSER
        title.contains("زيارات الدفاع المدني") -> FluentIcon.CIVIL_DEFENSE
        title.contains("هذا الكمبيوتر") || title.contains("الكمبيوتر") -> FluentIcon.THIS_PC
        title.contains("سلة المحذوفات") -> FluentIcon.RECYCLE_BIN
        title.contains("مستكشف الملفات") || title.contains("الملفات") -> FluentIcon.FILE_EXPLORER
        title.contains("PDF") -> FluentIcon.PDF_READER
        title.contains("الفيديو") -> FluentIcon.VIDEOS
        title.contains("الصور") -> FluentIcon.PHOTOS
        title.contains("الرسام") -> FluentIcon.PAINT
        title.contains("Word") || title.contains("معالج النصوص") -> FluentIcon.DOCUMENTS
        title.contains("Excel") || title.contains("الجداول") -> FluentIcon.SPREADSHEET
        title.contains("لوحة التحكم") || title.contains("الإعدادات") -> FluentIcon.SETTINGS
        title.contains("إدارة المهام") || title.contains("المهام") -> FluentIcon.TASK_MANAGER
        title.contains("Command") -> FluentIcon.TERMINAL
        title.contains("الألعاب") || title.contains("ألعاب") -> FluentIcon.GAMES
        title.contains("الحاسبة") -> FluentIcon.CALCULATOR
        title.contains("المفكرة") -> FluentIcon.NOTEPAD
        title.contains("تحديث") -> FluentIcon.WINDOWS_UPDATE
        title.contains("التنزيلات") -> FluentIcon.DOWNLOADS
        title.contains("المستندات") -> FluentIcon.DOCUMENTS
        title.contains("سطح المكتب") -> FluentIcon.FOLDER_DESKTOP
        title.contains("المشاريع") -> FluentIcon.FOLDER_PROJECTS
        title.contains("حول") || title.contains("About") -> FluentIcon.ABOUT
        title.contains("الشبكة") || title.contains("Wi-Fi") -> FluentIcon.WIFI
        title.contains("Bluetooth") -> FluentIcon.BLUETOOTH
        title.contains("الصوت") -> FluentIcon.SOUND
        title.contains("السطوع") || title.contains("الشاشة") -> FluentIcon.DISPLAY
        title.contains("البطارية") -> FluentIcon.BATTERY
        title.contains("الإشعارات") -> FluentIcon.NOTIFICATIONS
        title.contains("التاريخ") || title.contains("الوقت") -> FluentIcon.DATETIME
        title.contains("الخصوصية") -> FluentIcon.SECURITY
        title.contains("الخلفية") -> FluentIcon.WALLPAPER
        title.contains("التطبيقات") -> FluentIcon.APPS
        else -> FluentIcon.GENERIC_WINDOW
    }

    fun createBitmap(context: Context, icon: FluentIcon, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx.coerceAtLeast(16), sizePx.coerceAtLeast(16), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        drawIcon(canvas, icon, bounds)
        return bitmap
    }

    fun createDrawable(context: Context, icon: FluentIcon, sizePx: Int): Drawable {
        val bmp = createBitmap(context, icon, sizePx)
        return BitmapDrawable(context.resources, bmp)
    }

    fun drawIcon(canvas: Canvas, icon: FluentIcon, bounds: RectF) {
        val w = bounds.width()
        val h = bounds.height()
        val s = minOf(w, h)
        if (s <= 0f) return
        val cx = bounds.centerX()
        val cy = bounds.centerY()

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        when (icon) {
            FluentIcon.WINDOWS_START -> drawWindowsLogo(canvas, cx, cy, s)
            FluentIcon.SEARCH -> drawSearchIcon(canvas, cx, cy, s)
            FluentIcon.THIS_PC -> drawThisPC(canvas, cx, cy, s)
            FluentIcon.FILE_EXPLORER -> drawFileExplorer(canvas, cx, cy, s)
            FluentIcon.RECYCLE_BIN -> drawRecycleBin(canvas, cx, cy, s)
            FluentIcon.SETTINGS -> drawSettings(canvas, cx, cy, s)
            FluentIcon.EDGE_BROWSER -> drawEdgeBrowser(canvas, cx, cy, s)
            FluentIcon.PAINT -> drawPaint(canvas, cx, cy, s)
            FluentIcon.NOTEPAD -> drawNotepad(canvas, cx, cy, s)
            FluentIcon.CALCULATOR -> drawCalculator(canvas, cx, cy, s)
            FluentIcon.DOCUMENTS, FluentIcon.FOLDER_DOCUMENTS -> drawDocuments(canvas, cx, cy, s)
            FluentIcon.SPREADSHEET -> drawSpreadsheet(canvas, cx, cy, s)
            FluentIcon.DOWNLOADS, FluentIcon.FOLDER_DOWNLOADS -> drawDownloads(canvas, cx, cy, s)
            FluentIcon.MUSIC, FluentIcon.FOLDER_MUSIC -> drawMusic(canvas, cx, cy, s)
            FluentIcon.VIDEOS, FluentIcon.FOLDER_VIDEOS -> drawVideos(canvas, cx, cy, s)
            FluentIcon.PHOTOS, FluentIcon.FOLDER_PICTURES -> drawPhotos(canvas, cx, cy, s)
            FluentIcon.PDF_READER -> drawPdfReader(canvas, cx, cy, s)
            FluentIcon.YOUTUBE -> drawYouTube(canvas, cx, cy, s)
            FluentIcon.YOUTUBE_MUSIC -> drawYouTubeMusic(canvas, cx, cy, s)
            FluentIcon.TASK_MANAGER -> drawTaskManager(canvas, cx, cy, s)
            FluentIcon.TERMINAL -> drawTerminal(canvas, cx, cy, s)
            FluentIcon.GAMES -> drawGames(canvas, cx, cy, s)
            FluentIcon.WINDOWS_UPDATE -> drawWindowsUpdate(canvas, cx, cy, s)
            FluentIcon.CIVIL_DEFENSE -> drawCivilDefense(canvas, cx, cy, s)
            FluentIcon.FOLDER, FluentIcon.FOLDER_PROJECTS -> drawFolder(canvas, cx, cy, s)
            FluentIcon.FOLDER_DESKTOP -> drawDesktopFolder(canvas, cx, cy, s)
            FluentIcon.HARD_DRIVE -> drawHardDrive(canvas, cx, cy, s)
            FluentIcon.SMARTPHONE -> drawSmartphone(canvas, cx, cy, s)
            FluentIcon.WIFI -> drawWifi(canvas, cx, cy, s)
            FluentIcon.BLUETOOTH -> drawBluetooth(canvas, cx, cy, s)
            FluentIcon.SOUND -> drawSound(canvas, cx, cy, s)
            FluentIcon.DISPLAY -> drawDisplay(canvas, cx, cy, s)
            FluentIcon.BATTERY -> drawBattery(canvas, cx, cy, s)
            FluentIcon.NOTIFICATIONS -> drawNotifications(canvas, cx, cy, s)
            FluentIcon.DATETIME -> drawClock(canvas, cx, cy, s)
            FluentIcon.APPS -> drawApps(canvas, cx, cy, s)
            FluentIcon.WALLPAPER -> drawWallpaper(canvas, cx, cy, s)
            FluentIcon.SECURITY -> drawSecurity(canvas, cx, cy, s)
            FluentIcon.ABOUT -> drawAbout(canvas, cx, cy, s)
            FluentIcon.GENERIC_WINDOW -> drawGenericWindow(canvas, cx, cy, s)
        }
    }

    // 1. Windows 11 Start 4-Square Logo
    private fun drawWindowsLogo(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val size = s * 0.72f
        val half = size / 2f
        val gap = s * 0.045f
        val r = s * 0.055f

        val x0 = cx - half
        val x1 = cx - gap / 2f
        val x2 = cx + gap / 2f
        val x3 = cx + half

        val y0 = cy - half
        val y1 = cy - gap / 2f
        val y2 = cy + gap / 2f
        val y3 = cy + half

        p.shader = LinearGradient(x0, y0, x3, y3, intArrayOf(Color.rgb(0, 164, 239), Color.rgb(0, 120, 215)), null, Shader.TileMode.CLAMP)

        canvas.drawRoundRect(RectF(x0, y0, x1, y1), r, r, p)
        canvas.drawRoundRect(RectF(x2, y0, x3, y1), r, r, p)
        canvas.drawRoundRect(RectF(x0, y2, x1, y3), r, r, p)
        canvas.drawRoundRect(RectF(x2, y2, x3, y3), r, r, p)
    }

    // 2. Windows 11 Search Glass
    private fun drawSearchIcon(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (s * 0.095f).coerceAtLeast(2.5f)
            strokeCap = Paint.Cap.ROUND
            color = Color.rgb(0, 120, 215)
        }
        val r = s * 0.25f
        val ox = cx - s * 0.07f
        val oy = cy - s * 0.07f
        canvas.drawCircle(ox, oy, r, p)
        canvas.drawLine(ox + r * 0.707f, oy + r * 0.707f, cx + s * 0.32f, cy + s * 0.32f, p)
    }

    // 3. This PC (Modern Windows 11 Desktop Computer)
    private fun drawThisPC(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Screen bezel
        val sw = s * 0.84f
        val sh = s * 0.54f
        val sx = cx - sw / 2f
        val sy = cy - s * 0.38f
        val r = s * 0.08f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(sx, sy, sx, sy + sh, Color.rgb(42, 54, 71), Color.rgb(24, 33, 44), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(sx, sy, sx + sw, sy + sh), r, r, p)

        // Screen display
        val ins = s * 0.04f
        p.shader = LinearGradient(sx + ins, sy + ins, sx + sw - ins, sy + sh - ins, Color.rgb(65, 185, 255), Color.rgb(0, 115, 215), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(sx + ins, sy + ins, sx + sw - ins, sy + sh - ins), r * 0.7f, r * 0.7f, p)

        // Miniature Windows logo in display
        p.shader = null
        p.color = Color.argb(160, 255, 255, 255)
        val ls = s * 0.14f
        val lgap = s * 0.015f
        val lhalf = ls / 2f
        val lr = s * 0.015f
        val lcx = cx
        val lcy = sy + sh / 2f
        canvas.drawRoundRect(RectF(lcx - lhalf, lcy - lhalf, lcx - lgap / 2, lcy - lgap / 2), lr, lr, p)
        canvas.drawRoundRect(RectF(lcx + lgap / 2, lcy - lhalf, lcx + lhalf, lcy - lgap / 2), lr, lr, p)
        canvas.drawRoundRect(RectF(lcx - lhalf, lcy + lgap / 2, lcx - lgap / 2, lcy + lhalf), lr, lr, p)
        canvas.drawRoundRect(RectF(lcx + lgap / 2, lcy + lgap / 2, lcx + lhalf, lcy + lhalf), lr, lr, p)

        // Stand neck
        p.color = Color.rgb(150, 165, 185)
        canvas.drawRect(RectF(cx - s * 0.05f, sy + sh, cx + s * 0.05f, sy + sh + s * 0.12f), p)

        // Stand base plate
        p.shader = LinearGradient(cx - s * 0.22f, 0f, cx + s * 0.22f, 0f, Color.rgb(195, 208, 224), Color.rgb(140, 155, 175), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(cx - s * 0.22f, sy + sh + s * 0.10f, cx + s * 0.22f, sy + sh + s * 0.17f), s * 0.035f, s * 0.035f, p)
    }

    // 4. File Explorer (Windows 11 Yellow/Blue Fluent Folder)
    private fun drawFileExplorer(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Back blue tab
        val bw = s * 0.78f
        val bh = s * 0.42f
        val bx = cx - bw / 2f
        val by = cy - s * 0.28f
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(bx, by, bx, by + bh, Color.rgb(0, 164, 245), Color.rgb(0, 115, 215), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), s * 0.08f, s * 0.08f, p)

        // Front amber folder
        val fw = s * 0.86f
        val fh = s * 0.52f
        val fx = cx - fw / 2f
        val fy = cy - s * 0.14f

        val path = Path()
        val r = s * 0.09f
        path.moveTo(fx, fy + r)
        path.quadTo(fx, fy, fx + r, fy)
        path.lineTo(fx + fw * 0.38f, fy)
        path.lineTo(fx + fw * 0.46f, fy + s * 0.06f)
        path.lineTo(fx + fw - r, fy + s * 0.06f)
        path.quadTo(fx + fw, fy + s * 0.06f, fx + fw, fy + s * 0.06f + r)
        path.lineTo(fx + fw, fy + fh - r)
        path.quadTo(fx + fw, fy + fh, fx + fw - r, fy + fh)
        path.lineTo(fx + r, fy + fh)
        path.quadTo(fx, fy + fh, fx, fy + fh - r)
        path.close()

        p.shader = LinearGradient(fx, fy, fx, fy + fh, intArrayOf(Color.rgb(255, 213, 79), Color.rgb(255, 179, 0), Color.rgb(255, 143, 0)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, p)

        // Subtle highlight
        p.shader = null
        p.color = Color.argb(45, 255, 255, 255)
        canvas.drawRoundRect(RectF(fx + s * 0.04f, fy + s * 0.08f, fx + fw - s * 0.04f, fy + s * 0.16f), s * 0.04f, s * 0.04f, p)
    }

    // 5. Recycle Bin (Windows 11 Acrylic Bin)
    private fun drawRecycleBin(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Glass bin body
        val bw = s * 0.56f
        val bh = s * 0.64f
        val bx = cx - bw / 2f
        val by = cy - s * 0.28f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(bx, by, bx + bw, by + bh, intArrayOf(Color.argb(220, 140, 205, 255), Color.argb(240, 50, 145, 235)), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), s * 0.12f, s * 0.12f, p)

        // Rim
        p.shader = null
        p.color = Color.rgb(0, 120, 215)
        canvas.drawRoundRect(RectF(bx - s * 0.04f, by - s * 0.04f, bx + bw + s * 0.04f, by + s * 0.04f), s * 0.04f, s * 0.04f, p)

        // Recycle arrows symbol
        p.style = Paint.Style.STROKE
        p.strokeWidth = (s * 0.055f).coerceAtLeast(2f)
        p.color = Color.WHITE
        val r = s * 0.14f
        val ycenter = by + bh * 0.55f
        canvas.drawCircle(cx, ycenter, r, p)

        p.style = Paint.Style.FILL
        val head = Path().apply {
            moveTo(cx + r, ycenter)
            lineTo(cx + r + s * 0.05f, ycenter - s * 0.05f)
            lineTo(cx + r - s * 0.05f, ycenter - s * 0.05f)
            close()
        }
        canvas.drawPath(head, p)
    }

    // 6. Settings (Fluent Gear)
    private fun drawSettings(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val outerR = s * 0.38f
        val innerR = s * 0.28f
        val holeR = s * 0.14f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(cx - outerR, cy - outerR, cx + outerR, cy + outerR, Color.rgb(85, 105, 130), Color.rgb(45, 60, 80), Shader.TileMode.CLAMP)

        // 6 teeth
        for (i in 0 until 6) {
            val angle = i * (Math.PI / 3.0)
            val tx = cx + cos(angle).toFloat() * outerR * 0.85f
            val ty = cy + sin(angle).toFloat() * outerR * 0.85f
            canvas.drawCircle(tx, ty, s * 0.12f, p)
        }
        canvas.drawCircle(cx, cy, innerR, p)

        // Hollow center
        p.shader = null
        p.color = Color.rgb(238, 244, 252)
        canvas.drawCircle(cx, cy, holeR, p)

        // Center hub dot
        p.color = Color.rgb(70, 90, 115)
        canvas.drawCircle(cx, cy, holeR * 0.45f, p)
    }

    // 7. Edge / Web Browser (Windows 11 Edge Wave)
    private fun drawEdgeBrowser(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer wave circle
        val r = s * 0.38f
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.6f, intArrayOf(Color.rgb(0, 229, 255), Color.rgb(0, 120, 215), Color.rgb(0, 75, 175)), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, p)

        // Inner wave crest green accent
        val wave = Path()
        wave.moveTo(cx - r * 0.7f, cy + r * 0.2f)
        wave.cubicTo(cx - r * 0.4f, cy + r * 0.8f, cx + r * 0.3f, cy + r * 0.8f, cx + r * 0.8f, cy + r * 0.1f)
        wave.cubicTo(cx + r * 0.5f, cy + r * 0.4f, cx - r * 0.2f, cy + r * 0.4f, cx - r * 0.7f, cy + r * 0.2f)
        wave.close()

        p.shader = LinearGradient(cx - r, cy, cx + r, cy + r, Color.rgb(0, 230, 118), Color.rgb(0, 180, 216), Shader.TileMode.CLAMP)
        canvas.drawPath(wave, p)

        // Inner crest cutout
        p.shader = null
        p.color = Color.rgb(245, 248, 254)
        canvas.drawCircle(cx + r * 0.15f, cy - r * 0.12f, r * 0.38f, p)
    }

    // 8. Paint (Windows 11 Artist Palette & Brush)
    private fun drawPaint(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // White palette
        val pw = s * 0.78f
        val ph = s * 0.64f
        val px = cx - pw / 2f
        val py = cy - ph / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(px, py, px + pw, py + ph, Color.WHITE, Color.rgb(230, 235, 245), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(px, py, px + pw, py + ph), s * 0.22f, s * 0.22f, p)

        // Thumb hole
        p.shader = null
        p.color = Color.rgb(205, 218, 235)
        canvas.drawCircle(px + pw * 0.82f, py + ph * 0.72f, s * 0.08f, p)

        // 4 color spots
        val spots = listOf(
            Triple(px + pw * 0.25f, py + ph * 0.30f, Color.rgb(234, 67, 53)),
            Triple(px + pw * 0.52f, py + ph * 0.26f, Color.rgb(251, 188, 5)),
            Triple(px + pw * 0.75f, py + ph * 0.36f, Color.rgb(52, 168, 83)),
            Triple(px + pw * 0.32f, py + ph * 0.68f, Color.rgb(66, 133, 244))
        )
        spots.forEach { (sx, sy, col) ->
            p.color = col
            canvas.drawCircle(sx, sy, s * 0.075f, p)
        }

        // Overlaid paintbrush
        p.strokeCap = Paint.Cap.ROUND
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.08f
        p.color = Color.rgb(139, 87, 42) // wooden handle
        canvas.drawLine(cx - s * 0.35f, cy + s * 0.35f, cx + s * 0.15f, cy - s * 0.15f, p)

        p.color = Color.rgb(180, 190, 205) // silver ferrule
        canvas.drawLine(cx + s * 0.15f, cy - s * 0.15f, cx + s * 0.24f, cy - s * 0.24f, p)

        p.color = Color.rgb(0, 120, 215) // paint tip
        canvas.drawLine(cx + s * 0.24f, cy - s * 0.24f, cx + s * 0.34f, cy - s * 0.34f, p)
    }

    // 9. Notepad (Windows 11 Notepad)
    private fun drawNotepad(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val nw = s * 0.64f
        val nh = s * 0.76f
        val nx = cx - nw / 2f
        val ny = cy - nh / 2f

        // Blue notebook cover
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(nx, ny, nx + nw, ny + nh, Color.rgb(0, 120, 215), Color.rgb(0, 85, 175), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(nx, ny, nx + nw, ny + nh), s * 0.10f, s * 0.10f, p)

        // White sheet on top
        p.shader = null
        p.color = Color.rgb(250, 252, 255)
        canvas.drawRoundRect(RectF(nx + s * 0.09f, ny + s * 0.07f, nx + nw - s * 0.07f, ny + nh - s * 0.07f), s * 0.06f, s * 0.06f, p)

        // Text lines
        p.color = Color.rgb(180, 200, 225)
        p.strokeWidth = (s * 0.035f).coerceAtLeast(1.5f)
        p.style = Paint.Style.STROKE
        for (i in 0..3) {
            val ly = ny + s * 0.20f + i * s * 0.12f
            canvas.drawLine(nx + s * 0.15f, ly, nx + nw - s * 0.14f, ly, p)
        }

        // Orange/Yellow pencil on corner
        p.color = Color.rgb(255, 179, 0)
        p.strokeWidth = s * 0.07f
        p.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(nx + nw - s * 0.06f, ny + nh - s * 0.30f, nx + nw - s * 0.22f, ny + nh - s * 0.14f, p)
    }

    // 10. Calculator (Windows 11 Teal/Slate Calculator)
    private fun drawCalculator(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cw = s * 0.64f
        val ch = s * 0.80f
        val cx0 = cx - cw / 2f
        val cy0 = cy - ch / 2f

        // Body
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(cx0, cy0, cx0 + cw, cy0 + ch, Color.rgb(0, 137, 123), Color.rgb(0, 90, 80), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(cx0, cy0, cx0 + cw, cy0 + ch), s * 0.10f, s * 0.10f, p)

        // Display screen
        p.shader = null
        p.color = Color.rgb(224, 242, 241)
        val dw = cw - s * 0.12f
        val dh = s * 0.18f
        canvas.drawRoundRect(RectF(cx0 + s * 0.06f, cy0 + s * 0.08f, cx0 + s * 0.06f + dw, cy0 + s * 0.08f + dh), s * 0.04f, s * 0.04f, p)

        // Digit 0 in display
        p.color = Color.rgb(0, 77, 64)
        p.textSize = s * 0.12f
        p.typeface = Typeface.create("Arial", Typeface.BOLD)
        canvas.drawText("0", cx0 + dw - s * 0.02f, cy0 + s * 0.21f, p)

        // Keypad grid
        val btnW = (dw - s * 0.06f) / 3f
        val btnH = s * 0.10f
        for (row in 0..2) {
            for (col in 0..2) {
                val bx = cx0 + s * 0.06f + col * (btnW + s * 0.03f)
                val by = cy0 + s * 0.32f + row * (btnH + s * 0.035f)
                p.color = if (row == 2 && col == 2) Color.rgb(255, 152, 0) else Color.argb(190, 255, 255, 255)
                canvas.drawRoundRect(RectF(bx, by, bx + btnW, by + btnH), s * 0.025f, s * 0.025f, p)
            }
        }
    }

    // 11. Word / Documents (Microsoft Word Royal Blue Tile)
    private fun drawDocuments(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val dw = s * 0.74f
        val dh = s * 0.74f
        val dx = cx - dw / 2f
        val dy = cy - dh / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(dx, dy, dx + dw, dy + dh, Color.rgb(24, 90, 189), Color.rgb(16, 63, 145), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(dx, dy, dx + dw, dy + dh), s * 0.14f, s * 0.14f, p)

        // Layered page lines behind
        p.shader = null
        p.color = Color.argb(60, 255, 255, 255)
        canvas.drawRoundRect(RectF(dx + dw * 0.45f, dy + s * 0.12f, dx + dw - s * 0.10f, dy + dh - s * 0.12f), s * 0.06f, s * 0.06f, p)

        // Bold "W"
        p.color = Color.WHITE
        p.textSize = s * 0.36f
        p.typeface = Typeface.create("Arial", Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        canvas.drawText("W", cx - s * 0.06f, cy + s * 0.12f, p)
    }

    // 12. Spreadsheet (Microsoft Excel Forest Green Tile)
    private fun drawSpreadsheet(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val dw = s * 0.74f
        val dh = s * 0.74f
        val dx = cx - dw / 2f
        val dy = cy - dh / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(dx, dy, dx + dw, dy + dh, Color.rgb(16, 124, 65), Color.rgb(11, 90, 46), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(dx, dy, dx + dw, dy + dh), s * 0.14f, s * 0.14f, p)

        // Grid lines behind
        p.shader = null
        p.color = Color.argb(60, 255, 255, 255)
        canvas.drawRoundRect(RectF(dx + dw * 0.45f, dy + s * 0.12f, dx + dw - s * 0.10f, dy + dh - s * 0.12f), s * 0.06f, s * 0.06f, p)

        // Bold "X"
        p.color = Color.WHITE
        p.textSize = s * 0.36f
        p.typeface = Typeface.create("Arial", Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        canvas.drawText("X", cx - s * 0.06f, cy + s * 0.12f, p)
    }

    // 13. Photos (Windows 11 Violet/Magenta Tile with Mountains & Sun)
    private fun drawPhotos(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val pw = s * 0.76f
        val ph = s * 0.76f
        val px = cx - pw / 2f
        val py = cy - ph / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(px, py, px + pw, py + ph, Color.rgb(216, 27, 96), Color.rgb(142, 36, 170), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(px, py, px + pw, py + ph), s * 0.14f, s * 0.14f, p)

        // Golden Sun
        p.shader = null
        p.color = Color.rgb(255, 213, 79)
        canvas.drawCircle(px + pw * 0.32f, py + ph * 0.34f, s * 0.11f, p)

        // Mountains
        p.color = Color.WHITE
        val m = Path().apply {
            moveTo(px + s * 0.12f, py + ph - s * 0.12f)
            lineTo(px + pw * 0.44f, py + ph * 0.44f)
            lineTo(px + pw * 0.68f, py + ph * 0.65f)
            lineTo(px + pw - s * 0.12f, py + ph - s * 0.12f)
            close()
        }
        canvas.drawPath(m, p)
    }

    // 14. Music (Windows 11 Sunset Tile with Musical Note)
    private fun drawMusic(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val mw = s * 0.76f
        val mh = s * 0.76f
        val mx = cx - mw / 2f
        val my = cy - mh / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(mx, my, mx + mw, my + mh, Color.rgb(255, 87, 34), Color.rgb(233, 30, 99), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(mx, my, mx + mw, my + mh), s * 0.14f, s * 0.14f, p)

        // Musical note
        p.shader = null
        p.color = Color.WHITE
        val note = Path().apply {
            // Right note head
            addOval(RectF(cx + s * 0.08f, cy + s * 0.08f, cx + s * 0.24f, cy + s * 0.22f), Path.Direction.CW)
            // Left note head
            addOval(RectF(cx - s * 0.20f, cy + s * 0.14f, cx - s * 0.04f, cy + s * 0.28f), Path.Direction.CW)
        }
        canvas.drawPath(note, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = (s * 0.055f).coerceAtLeast(2f)
        p.strokeCap = Paint.Cap.ROUND
        // Stem 1
        canvas.drawLine(cx - s * 0.06f, cy + s * 0.20f, cx - s * 0.06f, cy - s * 0.16f, p)
        // Stem 2
        canvas.drawLine(cx + s * 0.22f, cy + s * 0.14f, cx + s * 0.22f, cy - s * 0.22f, p)
        // Beam
        p.strokeWidth = (s * 0.085f).coerceAtLeast(3f)
        canvas.drawLine(cx - s * 0.06f, cy - s * 0.16f, cx + s * 0.22f, cy - s * 0.22f, p)
    }

    // 15. Videos (Windows 11 Crimson Clapperboard)
    private fun drawVideos(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val vw = s * 0.76f
        val vh = s * 0.76f
        val vx = cx - vw / 2f
        val vy = cy - vh / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(vx, vy, vx + vw, vy + vh, Color.rgb(255, 61, 0), Color.rgb(198, 40, 40), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(vx, vy, vx + vw, vy + vh), s * 0.14f, s * 0.14f, p)

        // Film clapper / play triangle
        p.shader = null
        p.color = Color.WHITE
        val play = Path().apply {
            moveTo(cx - s * 0.10f, cy - s * 0.16f)
            lineTo(cx + s * 0.18f, cy)
            lineTo(cx - s * 0.10f, cy + s * 0.16f)
            close()
        }
        canvas.drawPath(play, p)
    }

    // 16. YouTube (Authentic Red Rounded Rectangle)
    private fun drawYouTube(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val yw = s * 0.84f
        val yh = s * 0.60f
        val yx = cx - yw / 2f
        val yy = cy - yh / 2f

        p.style = Paint.Style.FILL
        p.color = Color.rgb(255, 0, 0)
        canvas.drawRoundRect(RectF(yx, yy, yx + yw, yy + yh), s * 0.14f, s * 0.14f, p)

        p.color = Color.WHITE
        val triangle = Path().apply {
            moveTo(cx - s * 0.09f, cy - s * 0.14f)
            lineTo(cx + s * 0.14f, cy)
            lineTo(cx - s * 0.09f, cy + s * 0.14f)
            close()
        }
        canvas.drawPath(triangle, p)
    }

    // 17. YouTube Music (Authentic Concentric Rings)
    private fun drawYouTubeMusic(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = s * 0.38f

        p.style = Paint.Style.FILL
        p.color = Color.rgb(255, 0, 0)
        canvas.drawCircle(cx, cy, r, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = (s * 0.045f).coerceAtLeast(1.5f)
        p.color = Color.WHITE
        canvas.drawCircle(cx, cy, r * 0.72f, p)
        canvas.drawCircle(cx, cy, r * 0.44f, p)

        p.style = Paint.Style.FILL
        val triangle = Path().apply {
            moveTo(cx - s * 0.06f, cy - s * 0.10f)
            lineTo(cx + s * 0.10f, cy)
            lineTo(cx - s * 0.06f, cy + s * 0.10f)
            close()
        }
        canvas.drawPath(triangle, p)
    }

    // 18. PDF Reader (Scarlet Adobe Style)
    private fun drawPdfReader(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val pw = s * 0.74f
        val ph = s * 0.74f
        val px = cx - pw / 2f
        val py = cy - ph / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(px, py, px + pw, py + ph, Color.rgb(211, 47, 47), Color.rgb(183, 28, 28), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(px, py, px + pw, py + ph), s * 0.14f, s * 0.14f, p)

        // PDF curves
        p.shader = null
        p.color = Color.WHITE
        p.textSize = s * 0.24f
        p.typeface = Typeface.create("Arial", Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        canvas.drawText("PDF", cx, cy + s * 0.09f, p)
    }

    // 19. Task Manager (Graph Pulse)
    private fun drawTaskManager(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tw = s * 0.76f
        val th = s * 0.76f
        val tx = cx - tw / 2f
        val ty = cy - th / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(tx, ty, tx + tw, ty + th, Color.rgb(30, 41, 59), Color.rgb(15, 23, 42), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(tx, ty, tx + tw, ty + th), s * 0.14f, s * 0.14f, p)

        // Grid lines
        p.shader = null
        p.color = Color.argb(40, 255, 255, 255)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        canvas.drawLine(tx + s * 0.1f, cy, tx + tw - s * 0.1f, cy, p)
        canvas.drawLine(cx, ty + s * 0.1f, cx, ty + th - s * 0.1f, p)

        // ECG Heartbeat line
        p.color = Color.rgb(16, 185, 129)
        p.strokeWidth = (s * 0.06f).coerceAtLeast(2f)
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        val ecg = Path().apply {
            moveTo(tx + s * 0.12f, cy)
            lineTo(cx - s * 0.14f, cy)
            lineTo(cx - s * 0.06f, cy - s * 0.22f)
            lineTo(cx + s * 0.04f, cy + s * 0.18f)
            lineTo(cx + s * 0.12f, cy)
            lineTo(tx + tw - s * 0.12f, cy)
        }
        canvas.drawPath(ecg, p)
    }

    // 20. Terminal (Prompt Console)
    private fun drawTerminal(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tw = s * 0.78f
        val th = s * 0.68f
        val tx = cx - tw / 2f
        val ty = cy - th / 2f

        p.style = Paint.Style.FILL
        p.color = Color.rgb(24, 24, 27)
        canvas.drawRoundRect(RectF(tx, ty, tx + tw, ty + th), s * 0.12f, s * 0.12f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f
        p.color = Color.rgb(63, 63, 70)
        canvas.drawRoundRect(RectF(tx, ty, tx + tw, ty + th), s * 0.12f, s * 0.12f, p)

        // Prompt ">_"
        p.color = Color.rgb(6, 182, 212)
        p.strokeWidth = (s * 0.055f).coerceAtLeast(2f)
        p.strokeCap = Paint.Cap.ROUND
        val prompt = Path().apply {
            moveTo(cx - s * 0.18f, cy - s * 0.10f)
            lineTo(cx - s * 0.06f, cy)
            lineTo(cx - s * 0.18f, cy + s * 0.10f)
        }
        canvas.drawPath(prompt, p)
        canvas.drawLine(cx + s * 0.02f, cy + s * 0.10f, cx + s * 0.18f, cy + s * 0.10f, p)
    }

    // 21. Games (Xbox Controller)
    private fun drawGames(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val gw = s * 0.76f
        val gh = s * 0.76f
        val gx = cx - gw / 2f
        val gy = cy - gh / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(gx, gy, gx + gw, gy + gh, Color.rgb(16, 124, 16), Color.rgb(8, 80, 8), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(gx, gy, gx + gw, gy + gh), s * 0.14f, s * 0.14f, p)

        // Gamepad body
        p.shader = null
        p.color = Color.WHITE
        val pw = s * 0.52f
        val ph = s * 0.32f
        canvas.drawRoundRect(RectF(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f), s * 0.10f, s * 0.10f, p)

        // D-pad
        p.color = Color.rgb(16, 124, 16)
        p.strokeWidth = (s * 0.04f).coerceAtLeast(1.5f)
        p.style = Paint.Style.STROKE
        canvas.drawLine(cx - s * 0.14f, cy, cx - s * 0.06f, cy, p)
        canvas.drawLine(cx - s * 0.10f, cy - s * 0.04f, cx - s * 0.10f, cy + s * 0.04f, p)

        // Action buttons
        p.style = Paint.Style.FILL
        canvas.drawCircle(cx + s * 0.12f, cy, s * 0.025f, p)
        canvas.drawCircle(cx + s * 0.07f, cy - s * 0.04f, s * 0.025f, p)
        canvas.drawCircle(cx + s * 0.07f, cy + s * 0.04f, s * 0.025f, p)
    }

    // 22. Windows Update
    private fun drawWindowsUpdate(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = s * 0.34f

        p.style = Paint.Style.STROKE
        p.strokeWidth = (s * 0.075f).coerceAtLeast(2.5f)
        p.color = Color.rgb(0, 120, 215)
        p.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 40f, 130f, false, p)
        canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 220f, 130f, false, p)

        // Arrowheads
        p.style = Paint.Style.FILL
        val head1 = Path().apply {
            moveTo(cx + r * 0.95f, cy + s * 0.15f)
            lineTo(cx + r * 0.95f + s * 0.07f, cy + s * 0.03f)
            lineTo(cx + r * 0.95f - s * 0.07f, cy + s * 0.03f)
            close()
        }
        canvas.drawPath(head1, p)
        val head2 = Path().apply {
            moveTo(cx - r * 0.95f, cy - s * 0.15f)
            lineTo(cx - r * 0.95f - s * 0.07f, cy - s * 0.03f)
            lineTo(cx - r * 0.95f + s * 0.07f, cy - s * 0.03f)
            close()
        }
        canvas.drawPath(head2, p)
    }

    // 23. Civil Defense (Shield with Flame)
    private fun drawCivilDefense(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val sw = s * 0.72f
        val sh = s * 0.82f
        val sx = cx - sw / 2f
        val sy = cy - sh / 2f

        // Shield
        val shield = Path().apply {
            moveTo(cx, sy)
            lineTo(sx + sw, sy + sh * 0.22f)
            lineTo(sx + sw * 0.85f, sy + sh * 0.65f)
            lineTo(cx, sy + sh)
            lineTo(sx + sw * 0.15f, sy + sh * 0.65f)
            lineTo(sx, sy + sh * 0.22f)
            close()
        }
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(sx, sy, sx + sw, sy + sh, Color.rgb(211, 47, 47), Color.rgb(183, 28, 28), Shader.TileMode.CLAMP)
        canvas.drawPath(shield, p)

        // Flame in center
        p.shader = null
        p.color = Color.rgb(255, 193, 7)
        val flame = Path().apply {
            moveTo(cx, sy + sh * 0.28f)
            cubicTo(cx + s * 0.14f, sy + sh * 0.44f, cx + s * 0.14f, sy + sh * 0.68f, cx, sy + sh * 0.75f)
            cubicTo(cx - s * 0.14f, sy + sh * 0.68f, cx - s * 0.14f, sy + sh * 0.44f, cx, sy + sh * 0.28f)
            close()
        }
        canvas.drawPath(flame, p)
    }

    // 24. Downloads (Arrow into Tray)
    private fun drawDownloads(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = s * 0.36f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, Color.rgb(0, 164, 245), Color.rgb(0, 120, 215), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, p)

        // Arrow
        p.shader = null
        p.color = Color.WHITE
        p.strokeWidth = (s * 0.08f).coerceAtLeast(2.5f)
        p.strokeCap = Paint.Cap.ROUND
        p.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy - s * 0.18f, cx, cy + s * 0.12f, p)

        val head = Path().apply {
            moveTo(cx - s * 0.14f, cy + s * 0.02f)
            lineTo(cx, cy + s * 0.14f)
            lineTo(cx + s * 0.14f, cy + s * 0.02f)
        }
        canvas.drawPath(head, p)

        // Tray line
        canvas.drawLine(cx - s * 0.18f, cy + s * 0.22f, cx + s * 0.18f, cy + s * 0.22f, p)
    }

    // 25. Generic Windows 11 Folder
    private fun drawFolder(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        drawFileExplorer(canvas, cx, cy, s)
    }

    // 26. Desktop Folder (Folder with Mini Monitor)
    private fun drawDesktopFolder(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        drawFileExplorer(canvas, cx, cy, s)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(0, 120, 215)
        canvas.drawRoundRect(RectF(cx - s * 0.16f, cy + s * 0.04f, cx + s * 0.16f, cy + s * 0.26f), s * 0.03f, s * 0.03f, p)
        p.color = Color.WHITE
        canvas.drawRect(RectF(cx - s * 0.03f, cy + s * 0.26f, cx + s * 0.03f, cy + s * 0.32f), p)
        canvas.drawRect(RectF(cx - s * 0.10f, cy + s * 0.32f, cx + s * 0.10f, cy + s * 0.35f), p)
    }

    // 27. Hard Drive (C:)
    private fun drawHardDrive(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val dw = s * 0.82f
        val dh = s * 0.44f
        val dx = cx - dw / 2f
        val dy = cy - dh / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(dx, dy, dx + dw, dy + dh, Color.rgb(200, 215, 230), Color.rgb(155, 175, 195), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(dx, dy, dx + dw, dy + dh), s * 0.08f, s * 0.08f, p)

        // Windows blue badge on disk
        p.shader = null
        p.color = Color.rgb(0, 120, 215)
        canvas.drawRoundRect(RectF(dx + s * 0.08f, dy + s * 0.08f, dx + s * 0.26f, dy + dh - s * 0.08f), s * 0.04f, s * 0.04f, p)

        // Drive slot and LED
        p.color = Color.rgb(90, 110, 130)
        canvas.drawRoundRect(RectF(dx + s * 0.32f, cy - s * 0.03f, dx + dw - s * 0.18f, cy + s * 0.03f), s * 0.02f, s * 0.02f, p)
        p.color = Color.rgb(0, 230, 118) // Green LED
        canvas.drawCircle(dx + dw - s * 0.10f, cy, s * 0.035f, p)
    }

    // 28. Smartphone
    private fun drawSmartphone(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val pw = s * 0.48f
        val ph = s * 0.78f
        val px = cx - pw / 2f
        val py = cy - ph / 2f

        p.style = Paint.Style.FILL
        p.color = Color.rgb(35, 45, 60)
        canvas.drawRoundRect(RectF(px, py, px + pw, py + ph), s * 0.10f, s * 0.10f, p)

        p.shader = LinearGradient(px, py, px + pw, py + ph, Color.rgb(70, 185, 255), Color.rgb(0, 120, 215), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(px + s * 0.03f, py + s * 0.06f, px + pw - s * 0.03f, py + ph - s * 0.06f), s * 0.07f, s * 0.07f, p)
    }

    // 29. Wi-Fi
    private fun drawWifi(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (s * 0.085f).coerceAtLeast(2.5f)
            strokeCap = Paint.Cap.ROUND
            color = Color.rgb(0, 120, 215)
        }
        val r = s * 0.34f
        canvas.drawArc(RectF(cx - r, cy - r + s * 0.12f, cx + r, cy + r + s * 0.12f), 220f, 100f, false, p)
        canvas.drawArc(RectF(cx - r * 0.65f, cy - r * 0.65f + s * 0.12f, cx + r * 0.65f, cy + r * 0.65f + s * 0.12f), 220f, 100f, false, p)
        p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy + s * 0.22f, s * 0.065f, p)
    }

    // 30. Bluetooth
    private fun drawBluetooth(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (s * 0.085f).coerceAtLeast(2.5f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.rgb(0, 120, 215)
        }
        val b = Path().apply {
            moveTo(cx, cy - s * 0.32f)
            lineTo(cx, cy + s * 0.32f)
            moveTo(cx - s * 0.18f, cy - s * 0.16f)
            lineTo(cx + s * 0.16f, cy + s * 0.14f)
            lineTo(cx, cy + s * 0.30f)
            lineTo(cx + s * 0.16f, cy - s * 0.14f)
            lineTo(cx - s * 0.18f, cy + s * 0.16f)
        }
        canvas.drawPath(b, p)
    }

    // 31. Sound / Speaker
    private fun drawSound(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(0, 120, 215)
        }
        val sp = Path().apply {
            moveTo(cx - s * 0.25f, cy - s * 0.12f)
            lineTo(cx - s * 0.12f, cy - s * 0.12f)
            lineTo(cx + s * 0.05f, cy - s * 0.26f)
            lineTo(cx + s * 0.05f, cy + s * 0.26f)
            lineTo(cx - s * 0.12f, cy + s * 0.12f)
            lineTo(cx - s * 0.25f, cy + s * 0.12f)
            close()
        }
        canvas.drawPath(sp, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = (s * 0.065f).coerceAtLeast(2f)
        p.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(RectF(cx + s * 0.08f, cy - s * 0.16f, cx + s * 0.26f, cy + s * 0.16f), -60f, 120f, false, p)
        canvas.drawArc(RectF(cx + s * 0.14f, cy - s * 0.26f, cx + s * 0.38f, cy + s * 0.26f), -60f, 120f, false, p)
    }

    // 32. Display / Brightness
    private fun drawDisplay(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(255, 179, 0)
        canvas.drawCircle(cx, cy, s * 0.18f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = (s * 0.07f).coerceAtLeast(2f)
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 8) {
            val a = i * (Math.PI / 4.0)
            val x1 = cx + cos(a).toFloat() * s * 0.24f
            val y1 = cy + sin(a).toFloat() * s * 0.24f
            val x2 = cx + cos(a).toFloat() * s * 0.34f
            val y2 = cy + sin(a).toFloat() * s * 0.34f
            canvas.drawLine(x1, y1, x2, y2, p)
        }
    }

    // 33. Battery
    private fun drawBattery(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val bw = s * 0.64f
        val bh = s * 0.34f
        val bx = cx - bw / 2f
        val by = cy - bh / 2f

        p.style = Paint.Style.STROKE
        p.strokeWidth = (s * 0.06f).coerceAtLeast(2f)
        p.color = Color.rgb(0, 120, 215)
        canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), s * 0.06f, s * 0.06f, p)

        // Terminal
        p.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(bx + bw, cy - s * 0.07f, bx + bw + s * 0.05f, cy + s * 0.07f), s * 0.02f, s * 0.02f, p)

        // Green charge fill
        p.color = Color.rgb(16, 185, 129)
        canvas.drawRoundRect(RectF(bx + s * 0.05f, by + s * 0.05f, bx + bw * 0.75f, by + bh - s * 0.05f), s * 0.04f, s * 0.04f, p)
    }

    // 34. Notifications Bell
    private fun drawNotifications(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(0, 120, 215)

        val bell = Path().apply {
            moveTo(cx, cy - s * 0.28f)
            quadTo(cx + s * 0.22f, cy - s * 0.05f, cx + s * 0.25f, cy + s * 0.14f)
            lineTo(cx - s * 0.25f, cy + s * 0.14f)
            quadTo(cx - s * 0.22f, cy - s * 0.05f, cx, cy - s * 0.28f)
            close()
        }
        canvas.drawPath(bell, p)
        canvas.drawCircle(cx, cy + s * 0.20f, s * 0.055f, p)
    }

    // 35. Clock / DateTime
    private fun drawClock(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = s * 0.35f

        p.style = Paint.Style.STROKE
        p.strokeWidth = (s * 0.07f).coerceAtLeast(2f)
        p.color = Color.rgb(0, 120, 215)
        canvas.drawCircle(cx, cy, r, p)

        p.strokeCap = Paint.Cap.ROUND
        // Hour hand at 10
        canvas.drawLine(cx, cy, cx - s * 0.12f, cy - s * 0.12f, p)
        // Minute hand at 2
        canvas.drawLine(cx, cy, cx + s * 0.16f, cy - s * 0.10f, p)
    }

    // 36. Apps
    private fun drawApps(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val size = s * 0.24f
        val gap = s * 0.06f
        val r = s * 0.04f

        p.color = Color.rgb(66, 133, 244)
        canvas.drawRoundRect(RectF(cx - size - gap / 2, cy - size - gap / 2, cx - gap / 2, cy - gap / 2), r, r, p)
        p.color = Color.rgb(234, 67, 53)
        canvas.drawRoundRect(RectF(cx + gap / 2, cy - size - gap / 2, cx + size + gap / 2, cy - gap / 2), r, r, p)
        p.color = Color.rgb(251, 188, 5)
        canvas.drawRoundRect(RectF(cx - size - gap / 2, cy + gap / 2, cx - gap / 2, cy + size + gap / 2), r, r, p)
        p.color = Color.rgb(52, 168, 83)
        canvas.drawRoundRect(RectF(cx + gap / 2, cy + gap / 2, cx + size + gap / 2, cy + size + gap / 2), r, r, p)
    }

    // 37. Wallpaper
    private fun drawWallpaper(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val fw = s * 0.74f
        val fh = s * 0.54f
        val fx = cx - fw / 2f
        val fy = cy - fh / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(fx, fy, fx + fw, fy + fh, Color.rgb(0, 120, 215), Color.rgb(0, 180, 216), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(fx, fy, fx + fw, fy + fh), s * 0.08f, s * 0.08f, p)

        p.shader = null
        p.color = Color.rgb(255, 213, 79)
        canvas.drawCircle(fx + fw * 0.28f, fy + fh * 0.34f, s * 0.07f, p)

        p.color = Color.WHITE
        val wave = Path().apply {
            moveTo(fx, fy + fh)
            lineTo(fx + fw * 0.45f, fy + fh * 0.45f)
            lineTo(fx + fw * 0.75f, fy + fh * 0.70f)
            lineTo(fx + fw, fy + fh * 0.55f)
            lineTo(fx + fw, fy + fh)
            close()
        }
        canvas.drawPath(wave, p)
    }

    // 38. Security / Lock
    private fun drawSecurity(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val bw = s * 0.54f
        val bh = s * 0.42f
        val bx = cx - bw / 2f
        val by = cy - s * 0.04f

        // Shackle
        p.style = Paint.Style.STROKE
        p.strokeWidth = (s * 0.075f).coerceAtLeast(2f)
        p.color = Color.rgb(120, 140, 165)
        canvas.drawArc(RectF(cx - s * 0.16f, cy - s * 0.32f, cx + s * 0.16f, cy + s * 0.02f), 180f, 180f, false, p)

        // Lock Body
        p.style = Paint.Style.FILL
        p.color = Color.rgb(0, 120, 215)
        canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), s * 0.08f, s * 0.08f, p)

        // Keyhole
        p.color = Color.WHITE
        canvas.drawCircle(cx, by + bh * 0.42f, s * 0.05f, p)
        canvas.drawRect(RectF(cx - s * 0.02f, by + bh * 0.42f, cx + s * 0.02f, by + bh * 0.72f), p)
    }

    // 39. About
    private fun drawAbout(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(0, 120, 215)
        canvas.drawCircle(cx, cy, s * 0.36f, p)

        p.color = Color.WHITE
        p.textSize = s * 0.44f
        p.typeface = Typeface.create("Arial", Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        canvas.drawText("i", cx, cy + s * 0.15f, p)
    }

    // 40. Generic Window
    private fun drawGenericWindow(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val ww = s * 0.74f
        val wh = s * 0.60f
        val wx = cx - ww / 2f
        val wy = cy - wh / 2f

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(wx, wy, wx + ww, wy + wh, Color.rgb(240, 245, 255), Color.rgb(215, 225, 242), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(wx, wy, wx + ww, wy + wh), s * 0.08f, s * 0.08f, p)

        p.shader = null
        p.color = Color.rgb(0, 120, 215)
        canvas.drawRoundRect(RectF(wx, wy, wx + ww, wy + s * 0.14f), s * 0.08f, s * 0.08f, p)
    }
}

/**
 * Custom View for rendering any Windows 11 Fluent Icon crisply at any size.
 */
class FluentIconView(context: Context, var icon: FluentIcon, var sizeDp: Float = 48f) : View(context) {

    constructor(context: Context, title: String, sizeDp: Float = 48f) : this(context, FluentIcons.getIconForTitle(title), sizeDp)

    init {
        contentDescription = icon.name
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        FluentIcons.drawIcon(canvas, icon, bounds)
    }
}

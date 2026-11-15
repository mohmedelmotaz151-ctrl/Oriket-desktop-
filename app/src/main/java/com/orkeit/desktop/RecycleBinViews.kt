package com.orkeit.desktop

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View
import org.json.JSONObject
import java.io.File
import java.util.Locale

internal object RecycleBinTheme {
    val WINDOW_BG = Color.WHITE
    val RIBBON_BG = Color.rgb(245, 246, 247)         // #F5F6F7
    val RIBBON_TAB_BG = Color.WHITE
    val FILE_BLUE = Color.rgb(25, 121, 202)          // #1979CA
    val CTX_PURPLE_BG = Color.rgb(248, 224, 249)     // #F8E0F9
    val CTX_PURPLE_BAR = Color.rgb(168, 56, 174)     // #A838AE
    val CTX_PURPLE_TEXT = Color.rgb(126, 31, 126)    // #7E1F7E
    val BORDER = Color.rgb(217, 223, 231)            // #D9DFE7
    val BORDER_LIGHT = Color.rgb(229, 233, 240)
    val GROUP_HEADER_BLUE = Color.rgb(27, 94, 152)   // #1B5E98
    val SELECT_FILL = Color.rgb(203, 232, 246)       // #CBE8F6
    val SELECT_BORDER = Color.rgb(38, 160, 218)      // #26A0DA
    val SIDEBAR_ACTIVE_FILL = Color.rgb(229, 243, 255)
    val SIDEBAR_ACTIVE_BORDER = Color.rgb(153, 209, 255)
    val STATUS_BG = Color.rgb(248, 249, 252)
    val TEXT_DARK = Color.rgb(28, 34, 46)
    val TEXT_MUTED = Color.rgb(98, 108, 124)

    fun tabBg(d: Float, selected: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (selected) RIBBON_BG else Color.TRANSPARENT)
        cornerRadii = floatArrayOf(3f * d, 3f * d, 3f * d, 3f * d, 0f, 0f, 0f, 0f)
        if (selected) setStroke(1, BORDER)
    }

    fun itemCardBg(d: Float, selected: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (selected) SELECT_FILL else Color.TRANSPARENT)
        cornerRadius = 3f * d
        if (selected) setStroke((1.2f * d).toInt().coerceAtLeast(1), SELECT_BORDER)
    }

    fun ribbonBtnBg(d: Float, enabled: Boolean, highlighted: Boolean = false): GradientDrawable = GradientDrawable().apply {
        setColor(if (highlighted && enabled) Color.rgb(229, 241, 251) else Color.TRANSPARENT)
        cornerRadius = 4f * d
        if (highlighted && enabled) setStroke(1, Color.rgb(164, 206, 249))
    }
}

internal enum class RecycleGlyph {
    EMPTY_BIN,
    PROPERTIES,
    RESTORE_ALL,
    RESTORE_SELECTED,
    SELECT_ALL,
    DELETE_PERM,
    DETAILS_VIEW,
    LARGE_ICONS_VIEW
}

internal class RecycleRibbonGlyphView(
    context: Context,
    private val glyph: RecycleGlyph,
    private val d: Float
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var enabledVisual = true

    fun setGlyphEnabled(enabled: Boolean) {
        enabledVisual = enabled
        alpha = if (enabled) 1f else 0.42f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w * 0.5f
        val cy = h * 0.5f
        val s = minOf(w, h) * 0.42f

        when (glyph) {
            RecycleGlyph.EMPTY_BIN -> {
                // Blue-silver Windows mesh bin with crumpled paper
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(218, 232, 246)
                val bin = Path().apply {
                    moveTo(cx - s * 0.62f, cy - s * 0.52f)
                    lineTo(cx + s * 0.62f, cy - s * 0.52f)
                    lineTo(cx + s * 0.46f, cy + s * 0.78f)
                    lineTo(cx - s * 0.46f, cy + s * 0.78f)
                    close()
                }
                canvas.drawPath(bin, paint)
                paint.color = Color.rgb(248, 250, 252)
                canvas.drawCircle(cx - s * 0.14f, cy - s * 0.56f, s * 0.24f, paint)
                canvas.drawCircle(cx + s * 0.2f, cy - s * 0.50f, s * 0.20f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f * d
                paint.color = Color.rgb(92, 128, 168)
                canvas.drawPath(bin, paint)
                canvas.drawLine(cx - s * 0.22f, cy - s * 0.3f, cx - s * 0.16f, cy + s * 0.56f, paint)
                canvas.drawLine(cx + s * 0.22f, cy - s * 0.3f, cx + s * 0.16f, cy + s * 0.56f, paint)
                canvas.drawOval(RectF(cx - s * 0.66f, cy - s * 0.65f, cx + s * 0.66f, cy - s * 0.39f), paint)
            }

            RecycleGlyph.PROPERTIES -> {
                // White sheet with blue/green lines + yellow pencil
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                val r = RectF(cx - s * 0.72f, cy - s * 0.78f, cx + s * 0.45f, cy + s * 0.72f)
                canvas.drawRect(r, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.3f * d
                paint.color = Color.rgb(120, 134, 152)
                canvas.drawRect(r, paint)
                paint.strokeWidth = 1.6f * d
                paint.color = Color.rgb(42, 128, 200)
                canvas.drawLine(cx - s * 0.48f, cy - s * 0.36f, cx + s * 0.22f, cy - s * 0.36f, paint)
                paint.color = Color.rgb(66, 158, 92)
                canvas.drawLine(cx - s * 0.48f, cy + 0f, cx + s * 0.18f, cy + 0f, paint)
                paint.color = Color.rgb(120, 134, 152)
                canvas.drawLine(cx - s * 0.48f, cy + s * 0.36f, cx + s * 0.12f, cy + s * 0.36f, paint)
                // Yellow pencil overlay
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3.2f * d
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = Color.rgb(235, 168, 42)
                canvas.drawLine(cx + s * 0.08f, cy + s * 0.55f, cx + s * 0.76f, cy - s * 0.42f, paint)
                paint.strokeWidth = 1.6f * d
                paint.color = Color.rgb(72, 78, 88)
                canvas.drawPoint(cx + s * 0.02f, cy + s * 0.64f, paint)
            }

            RecycleGlyph.RESTORE_ALL -> {
                // 4 pale-blue squares (Window/Items grid) + green curved restore arrow
                paint.style = Paint.Style.FILL
                val gap = s * 0.12f
                val box = s * 0.44f
                val left = cx - s * 0.25f
                val top = cy - s * 0.22f
                listOf(
                    RectF(left, top, left + box, top + box),
                    RectF(left + box + gap, top, left + box * 2 + gap, top + box),
                    RectF(left, top + box + gap, left + box, top + box * 2 + gap),
                    RectF(left + box + gap, top + box + gap, left + box * 2 + gap, top + box * 2 + gap)
                ).forEach { cell ->
                    paint.style = Paint.Style.FILL
                    paint.color = Color.rgb(206, 232, 250)
                    canvas.drawRect(cell, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.1f * d
                    paint.color = Color.rgb(52, 134, 206)
                    canvas.drawRect(cell, paint)
                }
                drawGreenRestoreArrow(canvas, cx - s * 0.05f, cy - s * 0.26f, s * 0.85f)
            }

            RecycleGlyph.RESTORE_SELECTED -> {
                // Document page + green curved restore arrow
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                val doc = RectF(cx - s * 0.35f, cy - s * 0.60f, cx + s * 0.64f, cy + s * 0.78f)
                canvas.drawRect(doc, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.3f * d
                paint.color = Color.rgb(122, 134, 152)
                canvas.drawRect(doc, paint)
                drawGreenRestoreArrow(canvas, cx - s * 0.18f, cy - s * 0.18f, s * 0.90f)
            }

            RecycleGlyph.SELECT_ALL -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f * d
                paint.color = Color.rgb(38, 160, 218)
                val r = RectF(cx - s * 0.68f, cy - s * 0.68f, cx + s * 0.68f, cy + s * 0.68f)
                canvas.drawRect(r, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(203, 232, 246)
                canvas.drawRect(RectF(cx - s * 0.46f, cy - s * 0.46f, cx + s * 0.46f, cy + s * 0.46f), paint)
            }

            RecycleGlyph.DELETE_PERM -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.4f * d
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = Color.rgb(206, 48, 48)
                canvas.drawLine(cx - s * 0.56f, cy - s * 0.56f, cx + s * 0.56f, cy + s * 0.56f, paint)
                canvas.drawLine(cx + s * 0.56f, cy - s * 0.56f, cx - s * 0.56f, cy + s * 0.56f, paint)
            }

            RecycleGlyph.DETAILS_VIEW -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(68, 92, 122)
                for (i in -1..1) {
                    val y = cy + i * s * 0.46f
                    canvas.drawRect(cx - s * 0.72f, y - 1.2f * d, cx - s * 0.42f, y + 1.2f * d, paint)
                    canvas.drawRect(cx - s * 0.24f, y - 1.2f * d, cx + s * 0.72f, y + 1.2f * d, paint)
                }
            }

            RecycleGlyph.LARGE_ICONS_VIEW -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(27, 114, 198)
                val gap = s * 0.16f
                val box = s * 0.52f
                val left = cx - box - gap * 0.5f
                val top = cy - box - gap * 0.5f
                canvas.drawRect(left, top, left + box, top + box, paint)
                canvas.drawRect(left + box + gap, top, left + box * 2 + gap, top + box, paint)
                canvas.drawRect(left, top + box + gap, left + box, top + box * 2 + gap, paint)
                canvas.drawRect(left + box + gap, top + box + gap, left + box * 2 + gap, top + box * 2 + gap, paint)
            }
        }
    }

    private fun drawGreenRestoreArrow(canvas: Canvas, ax: Float, ay: Float, size: Float) {
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(54, 174, 72)
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            // Arrow head pointing upper-left
            moveTo(ax - size * 0.70f, ay - size * 0.20f)
            lineTo(ax - size * 0.18f, ay - size * 0.62f)
            lineTo(ax - size * 0.18f, ay - size * 0.34f)
            cubicTo(
                ax + size * 0.25f, ay - size * 0.34f,
                ax + size * 0.38f, ay - size * 0.02f,
                ax + size * 0.20f, ay + size * 0.36f
            )
            cubicTo(
                ax + size * 0.15f, ay + size * 0.04f,
                ax - size * 0.02f, ay - size * 0.06f,
                ax - size * 0.18f, ay - size * 0.06f
            )
            lineTo(ax - size * 0.18f, ay + size * 0.22f)
            close()
        }
        canvas.drawPath(path, arrowPaint)
        arrowPaint.style = Paint.Style.STROKE
        arrowPaint.strokeWidth = 0.9f * d
        arrowPaint.color = Color.rgb(34, 128, 48)
        canvas.drawPath(path, arrowPaint)
    }
}

internal data class RecycledEntry(
    val id: String,
    val isShortcut: Boolean,
    val displayName: String,
    val originalLocation: String,
    val deletedAtMs: Long,
    val sizeBytes: Long,
    val icon: FluentIcon,
    val recycledFile: File? = null
)

internal object RecycleBinStorage {
    private const val PREFS_NAME = "orkeit_desktop"
    private const val KEY_HIDDEN_SHORTCUTS = "hidden_shortcuts"
    private const val KEY_FILE_META = "recycle_file_meta_json"
    private const val KEY_SHORTCUT_TIMES = "recycle_shortcut_times_json"
    private const val KEY_CONFIRM_DELETE = "recycle_confirm_delete"

    fun recycleDir(context: Context): File = File(context.filesDir, "OriketRecycleBin").apply { mkdirs() }

    fun workspaceDir(context: Context): File = File(context.filesDir, "OriketWorkspace").apply { mkdirs() }

    fun isConfirmDeleteEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_CONFIRM_DELETE, true)

    fun setConfirmDeleteEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CONFIRM_DELETE, enabled).apply()
    }

    fun recordShortcutDeleted(context: Context, shortcutName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = try {
            JSONObject(prefs.getString(KEY_SHORTCUT_TIMES, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        json.put(shortcutName, System.currentTimeMillis())
        prefs.edit().putString(KEY_SHORTCUT_TIMES, json.toString()).apply()
    }

    fun moveFileToRecycleBin(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        val binDir = recycleDir(context)
        val workspace = workspaceDir(context)
        val storedId = "${System.currentTimeMillis()}_${file.name}"
        val dest = File(binDir, storedId)

        val copied = try {
            if (file.renameTo(dest)) true
            else if (file.isFile) {
                file.copyTo(dest, overwrite = true)
                file.delete()
            } else {
                file.copyRecursively(dest, overwrite = true) && file.deleteRecursively()
            }
        } catch (_: Exception) {
            false
        }
        if (!copied) return false

        val parentPath = file.parentFile?.absolutePath ?: workspace.absolutePath
        val relLoc = if (parentPath.startsWith(workspace.absolutePath)) {
            val sub = parentPath.removePrefix(workspace.absolutePath).trimStart(File.separatorChar)
            if (sub.isEmpty()) "Local Disk (C:)" else "C:\\$sub"
        } else parentPath

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val meta = try {
            JSONObject(prefs.getString(KEY_FILE_META, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        meta.put(storedId, JSONObject().apply {
            put("name", file.name)
            put("originDir", parentPath)
            put("relLoc", relLoc)
            put("deletedAt", System.currentTimeMillis())
            put("size", calculateSize(dest))
        })
        prefs.edit().putString(KEY_FILE_META, meta.toString()).apply()
        return true
    }

    fun listItems(context: Context): List<RecycledEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val result = mutableListOf<RecycledEntry>()

        // 1. Deleted Desktop Shortcuts
        val hidden = prefs.getStringSet(KEY_HIDDEN_SHORTCUTS, emptySet()).orEmpty()
        val shortcutTimes = try {
            JSONObject(prefs.getString(KEY_SHORTCUT_TIMES, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        hidden.forEach { key ->
            val label = prefs.getString("shortcut_label_$key", key) ?: key
            val ts = shortcutTimes.optLong(key, System.currentTimeMillis())
            result.add(
                RecycledEntry(
                    id = "shortcut:$key",
                    isShortcut = true,
                    displayName = label,
                    originalLocation = "Desktop",
                    deletedAtMs = ts,
                    sizeBytes = 1024L,
                    icon = FluentIcons.getIconForTitle(key)
                )
            )
        }

        // 2. Deleted Files in OriketRecycleBin
        val binDir = recycleDir(context)
        val meta = try {
            JSONObject(prefs.getString(KEY_FILE_META, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        binDir.listFiles()?.forEach { f ->
            val rec = meta.optJSONObject(f.name)
            val originalName = rec?.optString("name")?.takeIf { it.isNotBlank() } ?: f.name
            val relLoc = rec?.optString("relLoc")?.takeIf { it.isNotBlank() } ?: "C:\\OriketWorkspace"
            val ts = rec?.optLong("deletedAt", f.lastModified()) ?: f.lastModified()
            val sz = rec?.optLong("size", calculateSize(f)) ?: calculateSize(f)
            result.add(
                RecycledEntry(
                    id = "file:${f.name}",
                    isShortcut = false,
                    displayName = originalName,
                    originalLocation = relLoc,
                    deletedAtMs = ts,
                    sizeBytes = sz,
                    icon = iconForFileName(f, originalName),
                    recycledFile = f
                )
            )
        }

        return result.sortedByDescending { it.deletedAtMs }
    }

    fun restoreEntry(context: Context, entry: RecycledEntry): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (entry.isShortcut) {
            val key = entry.id.removePrefix("shortcut:")
            val updated = prefs.getStringSet(KEY_HIDDEN_SHORTCUTS, emptySet())?.toMutableSet() ?: mutableSetOf()
            updated.remove(key)
            prefs.edit().putStringSet(KEY_HIDDEN_SHORTCUTS, updated).apply()
            return true
        }
        val storedFile = entry.recycledFile ?: return false
        val meta = try {
            JSONObject(prefs.getString(KEY_FILE_META, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        val rec = meta.optJSONObject(storedFile.name)
        val originDirPath = rec?.optString("originDir")?.takeIf { it.isNotBlank() }
            ?: File(workspaceDir(context), "Documents").absolutePath
        val targetDir = File(originDirPath).apply { mkdirs() }
        var targetFile = File(targetDir, entry.displayName)
        if (targetFile.exists()) {
            val base = targetFile.nameWithoutExtension
            val ext = targetFile.extension
            val suffix = "_restored_${System.currentTimeMillis() % 10000}"
            targetFile = File(targetDir, if (ext.isNotEmpty()) "$base$suffix.$ext" else "$base$suffix")
        }
        val ok = try {
            if (storedFile.renameTo(targetFile)) true
            else if (storedFile.isFile) {
                storedFile.copyTo(targetFile, overwrite = true)
                storedFile.delete()
            } else {
                storedFile.copyRecursively(targetFile, overwrite = true) && storedFile.deleteRecursively()
            }
        } catch (_: Exception) {
            false
        }
        if (ok) {
            meta.remove(storedFile.name)
            prefs.edit().putString(KEY_FILE_META, meta.toString()).apply()
        }
        return ok
    }

    fun deleteEntryPermanently(context: Context, entry: RecycledEntry): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (entry.isShortcut) {
            val key = entry.id.removePrefix("shortcut:")
            val updated = prefs.getStringSet(KEY_HIDDEN_SHORTCUTS, emptySet())?.toMutableSet() ?: mutableSetOf()
            updated.remove(key)
            prefs.edit().putStringSet(KEY_HIDDEN_SHORTCUTS, updated).apply()
            return true
        }
        val storedFile = entry.recycledFile ?: return false
        val ok = if (storedFile.isDirectory) storedFile.deleteRecursively() else storedFile.delete()
        val meta = try {
            JSONObject(prefs.getString(KEY_FILE_META, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        meta.remove(storedFile.name)
        prefs.edit().putString(KEY_FILE_META, meta.toString()).apply()
        return ok
    }

    fun emptyAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_HIDDEN_SHORTCUTS, emptySet())
            .putString(KEY_FILE_META, "{}")
            .putString(KEY_SHORTCUT_TIMES, "{}")
            .apply()
        recycleDir(context).listFiles()?.forEach { f ->
            try {
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            } catch (_: Exception) {}
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    private fun calculateSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { calculateSize(it) } ?: 0L
    }

    private fun iconForFileName(file: File, name: String): FluentIcon = when {
        file.isDirectory -> FluentIcon.FOLDER
        name.endsWith(".pdf", true) -> FluentIcon.PDF_READER
        name.endsWith(".png", true) || name.endsWith(".jpg", true) ||
            name.endsWith(".jpeg", true) || name.endsWith(".webp", true) -> FluentIcon.PHOTOS
        name.endsWith(".mp3", true) || name.endsWith(".wav", true) || name.endsWith(".m4a", true) -> FluentIcon.MUSIC
        name.endsWith(".mp4", true) || name.endsWith(".mkv", true) -> FluentIcon.VIDEOS
        name.endsWith(".xls", true) || name.endsWith(".xlsx", true) || name.endsWith(".csv", true) -> FluentIcon.SPREADSHEET
        name.endsWith(".doc", true) || name.endsWith(".docx", true) -> FluentIcon.DOCUMENTS
        else -> FluentIcon.NOTEPAD
    }

    fun decodeThumbnail(file: File?, maxPx: Int): Bitmap? {
        if (file == null || !file.isFile) return null
        val n = file.name.lowercase(Locale.ROOT)
        if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp"))) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > maxPx * 2 || opts.outHeight / sample > maxPx * 2) sample *= 2
            BitmapFactory.Options().apply { inSampleSize = sample }.let {
                BitmapFactory.decodeFile(file.absolutePath, it)
            }
        } catch (_: Exception) {
            null
        }
    }
}

package com.orkeit.desktop

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.*

internal object Word365Theme {
    val SHELL_BG = Color.rgb(243, 245, 248)        // #F3F5F8 Windows 11 Word Chrome
    val STAGE_BG = Color.rgb(236, 240, 246)        // #ECF0F6 Canvas backdrop
    val RIBBON_CARD_BG = Color.WHITE               // Floating rounded ribbon card
    val RIBBON_BORDER = Color.rgb(220, 226, 235)
    val WORD_BLUE = Color.rgb(24, 90, 189)         // #185ABD Microsoft Word Blue
    val WORD_BLUE_SOFT = Color.rgb(230, 240, 253)  // #E6F0FD
    val TEXT_DARK = Color.rgb(31, 36, 48)
    val TEXT_MUTED = Color.rgb(102, 112, 128)
    val DIVIDER = Color.rgb(226, 231, 239)
    val PAGE_BORDER = Color.rgb(212, 219, 230)

    fun floatingRibbonBg(d: Float): GradientDrawable = GradientDrawable().apply {
        setColor(RIBBON_CARD_BG)
        cornerRadius = 8f * d
        setStroke((1f * d).toInt().coerceAtLeast(1), RIBBON_BORDER)
    }

    fun searchPillBg(d: Float): GradientDrawable = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = 14f * d
        setStroke(1, RIBBON_BORDER)
    }

    fun buttonBg(d: Float, active: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (active) WORD_BLUE_SOFT else Color.TRANSPARENT)
        cornerRadius = 4f * d
        if (active) setStroke(1, Color.rgb(168, 200, 245))
    }

    fun dropdownBg(d: Float): GradientDrawable = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = 4f * d
        setStroke(1, Color.rgb(206, 213, 224))
    }

    fun styleCardBg(d: Float, active: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = 5f * d
        setStroke(
            if (active) (1.6f * d).toInt().coerceAtLeast(2) else (1f * d).toInt().coerceAtLeast(1),
            if (active) WORD_BLUE else RIBBON_BORDER
        )
    }

    fun pageSheetBg(d: Float, paperColor: Int = Color.WHITE): GradientDrawable = GradientDrawable().apply {
        setColor(paperColor)
        setStroke(1, PAGE_BORDER)
    }
}

internal object WordRichTextHelper {
    fun toggleBold(editable: Editable, start: Int, end: Int) {
        toggleTypefaceStyle(editable, start, end, Typeface.BOLD)
    }

    fun toggleItalic(editable: Editable, start: Int, end: Int) {
        toggleTypefaceStyle(editable, start, end, Typeface.ITALIC)
    }

    private fun toggleTypefaceStyle(editable: Editable, rawStart: Int, rawEnd: Int, style: Int) {
        val (s, e) = normalizeRange(editable, rawStart, rawEnd)
        if (s >= e) return
        val existing = editable.getSpans(s, e, StyleSpan::class.java).filter { it.style == style }
        if (existing.isNotEmpty()) {
            existing.forEach { editable.removeSpan(it) }
        } else {
            editable.setSpan(StyleSpan(style), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun toggleUnderline(editable: Editable, rawStart: Int, rawEnd: Int) {
        val (s, e) = normalizeRange(editable, rawStart, rawEnd)
        if (s >= e) return
        val existing = editable.getSpans(s, e, UnderlineSpan::class.java)
        if (existing.isNotEmpty()) existing.forEach { editable.removeSpan(it) }
        else editable.setSpan(UnderlineSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun toggleStrikethrough(editable: Editable, rawStart: Int, rawEnd: Int) {
        val (s, e) = normalizeRange(editable, rawStart, rawEnd)
        if (s >= e) return
        val existing = editable.getSpans(s, e, StrikethroughSpan::class.java)
        if (existing.isNotEmpty()) existing.forEach { editable.removeSpan(it) }
        else editable.setSpan(StrikethroughSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun toggleSubscript(editable: Editable, rawStart: Int, rawEnd: Int) {
        val (s, e) = normalizeRange(editable, rawStart, rawEnd)
        if (s >= e) return
        val existing = editable.getSpans(s, e, SubscriptSpan::class.java)
        if (existing.isNotEmpty()) {
            existing.forEach { editable.removeSpan(it) }
        } else {
            editable.getSpans(s, e, SuperscriptSpan::class.java).forEach { editable.removeSpan(it) }
            editable.setSpan(SubscriptSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun toggleSuperscript(editable: Editable, rawStart: Int, rawEnd: Int) {
        val (s, e) = normalizeRange(editable, rawStart, rawEnd)
        if (s >= e) return
        val existing = editable.getSpans(s, e, SuperscriptSpan::class.java)
        if (existing.isNotEmpty()) {
            existing.forEach { editable.removeSpan(it) }
        } else {
            editable.getSpans(s, e, SubscriptSpan::class.java).forEach { editable.removeSpan(it) }
            editable.setSpan(SuperscriptSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun applyTextColor(editable: Editable, rawStart: Int, rawEnd: Int, color: Int) {
        val (s, e) = normalizeRange(editable, rawStart, rawEnd)
        if (s >= e) return
        editable.getSpans(s, e, ForegroundColorSpan::class.java).forEach { editable.removeSpan(it) }
        editable.setSpan(ForegroundColorSpan(color), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun toggleHighlight(editable: Editable, rawStart: Int, rawEnd: Int, color: Int) {
        val (s, e) = normalizeRange(editable, rawStart, rawEnd)
        if (s >= e) return
        val existing = editable.getSpans(s, e, BackgroundColorSpan::class.java)
        if (existing.isNotEmpty()) existing.forEach { editable.removeSpan(it) }
        else editable.setSpan(BackgroundColorSpan(color), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun applyRelativeSize(editable: Editable, rawStart: Int, rawEnd: Int, proportion: Float) {
        val (s, e) = normalizeRange(editable, rawStart, rawEnd)
        if (s >= e) return
        editable.getSpans(s, e, RelativeSizeSpan::class.java).forEach { editable.removeSpan(it) }
        editable.setSpan(RelativeSizeSpan(proportion), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun clearFormatting(editable: Editable, rawStart: Int, rawEnd: Int) {
        val (s, e) = normalizeRange(editable, rawStart, rawEnd)
        if (s >= e) return
        val spans = editable.getSpans(s, e, Any::class.java)
        spans.forEach { span ->
            if (span is StyleSpan || span is UnderlineSpan || span is StrikethroughSpan ||
                span is ForegroundColorSpan || span is BackgroundColorSpan ||
                span is RelativeSizeSpan || span is SubscriptSpan || span is SuperscriptSpan
            ) {
                editable.removeSpan(span)
            }
        }
    }

    fun toggleLinePrefix(editable: Editable, cursor: Int, prefix: String) {
        val txt = editable.toString()
        val pos = cursor.coerceIn(0, txt.length)
        val lineStart = txt.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = txt.indexOf('\n', pos).let { if (it < 0) txt.length else it }
        val line = txt.substring(lineStart, lineEnd)
        if (line.startsWith(prefix)) {
            editable.delete(lineStart, lineStart + prefix.length)
        } else {
            editable.insert(lineStart, prefix)
        }
    }

    fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split(Regex("\\s+")).count { it.isNotEmpty() }
    }

    private fun normalizeRange(editable: Editable, rawStart: Int, rawEnd: Int): Pair<Int, Int> {
        val len = editable.length
        if (len == 0) return 0 to 0
        val a = minOf(rawStart, rawEnd).coerceIn(0, len)
        val b = maxOf(rawStart, rawEnd).coerceIn(0, len)
        if (a < b) return a to b
        // Expand to current word or entire line if no selection
        var ws = a
        while (ws > 0 && !editable[ws - 1].isWhitespace()) ws--
        var we = a
        while (we < len && !editable[we].isWhitespace()) we++
        return if (ws < we) ws to we else 0 to len
    }
}

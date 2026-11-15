package com.orkeit.desktop

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import java.util.Locale
import kotlin.math.abs

internal object ExcelTheme {
    val EXCEL_GREEN = Color.rgb(33, 115, 70)        // #217346
    val EXCEL_GREEN_DARK = Color.rgb(24, 90, 54)    // #185A36
    val EXCEL_GREEN_SOFT = Color.rgb(211, 232, 220) // #D3E8DC
    val RIBBON_BG = Color.rgb(243, 243, 243)        // #F3F3F3
    val RIBBON_GROUP_DIV = Color.rgb(214, 214, 214) // #D6D6D6
    val HEADER_BG = Color.rgb(242, 242, 242)        // #F2F2F2
    val GRID_LINE = Color.rgb(212, 212, 212)        // #D4D4D4
    val TABLE_HEADER_BLUE = Color.rgb(68, 114, 196) // #4472C4 (like Excel CSV table screenshot)
    val TABLE_STRIPE_BLUE = Color.rgb(233, 240, 249)// #E9F0F9
    val TEXT_DARK = Color.rgb(32, 34, 38)
    val TEXT_MUTED = Color.rgb(96, 102, 112)

    fun activeRibbonTabDrawable(d: Float): LayerDrawable {
        val bg = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadii = floatArrayOf(3f * d, 3f * d, 3f * d, 3f * d, 0f, 0f, 0f, 0f)
        }
        val underline = GradientDrawable().apply {
            setColor(EXCEL_GREEN)
        }
        val layer = LayerDrawable(arrayOf(bg, underline))
        layer.setLayerInset(1, (4 * d).toInt(), (24 * d).toInt(), (4 * d).toInt(), 0)
        return layer
    }

    fun activeSheetTabDrawable(d: Float): LayerDrawable {
        val bg = GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(1, Color.rgb(206, 206, 206))
        }
        val underline = GradientDrawable().apply {
            setColor(EXCEL_GREEN)
        }
        val layer = LayerDrawable(arrayOf(bg, underline))
        layer.setLayerInset(1, (4 * d).toInt(), (22 * d).toInt(), (4 * d).toInt(), 0)
        return layer
    }

    fun ribbonToolBackground(d: Float, active: Boolean = false): GradientDrawable = GradientDrawable().apply {
        setColor(if (active) Color.rgb(214, 224, 238) else Color.TRANSPARENT)
        cornerRadius = 4f * d
        if (active) setStroke((1f * d).toInt().coerceAtLeast(1), Color.rgb(154, 174, 200))
    }

    fun cellBackground(
        d: Float,
        selected: Boolean,
        fillColor: Int?,
        tableHeader: Boolean,
        striped: Boolean
    ): GradientDrawable {
        val bgColor = when {
            tableHeader -> TABLE_HEADER_BLUE
            fillColor != null -> fillColor
            striped -> TABLE_STRIPE_BLUE
            else -> Color.WHITE
        }
        return GradientDrawable().apply {
            setColor(bgColor)
            if (selected) {
                setStroke((2f * d).toInt().coerceAtLeast(2), EXCEL_GREEN)
            } else {
                setStroke(1, if (tableHeader) Color.rgb(54, 94, 168) else GRID_LINE)
            }
        }
    }
}

internal object ExcelFormulaEngine {
    fun colName(colIndex: Int): String = ('A' + colIndex).toString()

    fun cellRef(row: Int, col: Int): String = "${colName(col)}${row + 1}"

    fun parseRef(ref: String, numCols: Int, numRows: Int): Pair<Int, Int>? {
        val s = ref.trim().uppercase(Locale.ROOT)
        if (s.length < 2) return null
        val col = s[0] - 'A'
        val row = s.substring(1).toIntOrNull()?.minus(1) ?: return null
        return if (col in 0 until numCols && row in 0 until numRows) row to col else null
    }

    fun formatNumber(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "#ERROR"
        val rounded = v.toLong()
        return if (abs(v - rounded.toDouble()) < 1e-9) rounded.toString()
        else String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.')
    }

    fun evaluateDisplay(
        raw: String,
        numRows: Int,
        numCols: Int,
        getRaw: (Int, Int) -> String,
        depth: Int = 0
    ): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("=")) return raw
        val v = evaluateNumeric(trimmed, numRows, numCols, getRaw, depth)
        return formatNumber(v)
    }

    fun evaluateNumeric(
        raw: String,
        numRows: Int,
        numCols: Int,
        getRaw: (Int, Int) -> String,
        depth: Int = 0
    ): Double {
        if (depth > 10) return Double.NaN
        val trimmed = raw.trim()
        if (!trimmed.startsWith("=")) {
            return trimmed.replace(",", ".").toDoubleOrNull() ?: 0.0
        }
        val expr = trimmed.drop(1).trim()
        if (expr.isEmpty()) return 0.0

        // Range functions: SUM, AVERAGE, AVG, MIN, MAX, COUNT
        val fnMatch = Regex("(?i)^(SUM|AVERAGE|AVG|MIN|MAX|COUNT)\\(([A-Z]\\d+):([A-Z]\\d+)\\)$").matchEntire(expr)
        if (fnMatch != null) {
            val fn = fnMatch.groupValues[1].uppercase(Locale.ROOT)
            val start = parseRef(fnMatch.groupValues[2], numCols, numRows) ?: return Double.NaN
            val end = parseRef(fnMatch.groupValues[3], numCols, numRows) ?: return Double.NaN
            val rMin = minOf(start.first, end.first)
            val rMax = maxOf(start.first, end.first)
            val cMin = minOf(start.second, end.second)
            val cMax = maxOf(start.second, end.second)
            val values = mutableListOf<Double>()
            for (r in rMin..rMax) {
                for (c in cMin..cMax) {
                    val cellStr = getRaw(r, c).trim()
                    if (cellStr.isNotEmpty()) {
                        val num = if (cellStr.startsWith("=")) {
                            evaluateNumeric(cellStr, numRows, numCols, getRaw, depth + 1)
                        } else {
                            cellStr.replace(",", ".").toDoubleOrNull()
                        }
                        if (num != null && !num.isNaN()) values.add(num)
                    } else if (fn == "SUM") {
                        values.add(0.0)
                    }
                }
            }
            if (values.isEmpty()) return 0.0
            return when (fn) {
                "SUM" -> values.sum()
                "AVERAGE", "AVG" -> values.average()
                "MIN" -> values.minOrNull() ?: 0.0
                "MAX" -> values.maxOrNull() ?: 0.0
                "COUNT" -> values.size.toDouble()
                else -> Double.NaN
            }
        }

        // General arithmetic parser supporting cell refs (A1, B2) and + - * / ( )
        return parseExpression(expr, numRows, numCols, getRaw, depth)
    }

    private fun parseExpression(
        input: String,
        numRows: Int,
        numCols: Int,
        getRaw: (Int, Int) -> String,
        depth: Int
    ): Double {
        val parser = object {
            var pos = 0
            val s = input.replace(" ", "")

            fun parseExpr(): Double {
                var x = parseTerm()
                while (pos < s.length) {
                    when (s[pos]) {
                        '+' -> { pos++; x += parseTerm() }
                        '-' -> { pos++; x -= parseTerm() }
                        else -> return x
                    }
                }
                return x
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (pos < s.length) {
                    when (s[pos]) {
                        '*' -> { pos++; x *= parseFactor() }
                        '/' -> {
                            pos++
                            val d = parseFactor()
                            x = if (d == 0.0) Double.NaN else x / d
                        }
                        else -> return x
                    }
                }
                return x
            }

            fun parseFactor(): Double {
                if (pos >= s.length) return Double.NaN
                if (s[pos] == '+') { pos++; return parseFactor() }
                if (s[pos] == '-') { pos++; return -parseFactor() }
                if (s[pos] == '(') {
                    pos++
                    val v = parseExpr()
                    if (pos < s.length && s[pos] == ')') pos++
                    return v
                }
                val start = pos
                if (s[pos].isLetter()) {
                    while (pos < s.length && s[pos].isLetterOrDigit()) pos++
                    val token = s.substring(start, pos)
                    val ref = parseRef(token, numCols, numRows) ?: return Double.NaN
                    return evaluateNumeric(getRaw(ref.first, ref.second), numRows, numCols, getRaw, depth + 1)
                }
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.' || s[pos] == ',')) pos++
                if (start == pos) return Double.NaN
                return s.substring(start, pos).replace(",", ".").toDoubleOrNull() ?: Double.NaN
            }
        }
        val result = parser.parseExpr()
        return if (parser.pos == parser.s.length) result else Double.NaN
    }
}

internal object ExcelCsvParser {
    fun parse(text: String): List<List<String>> {
        val clean = text.removePrefix("\uFEFF")
        if (clean.isEmpty()) return emptyList()
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < clean.length && clean[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < clean.length && clean[i + 1] == '\n') i++
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            rows.add(currentRow.toList())
        }
        return rows
    }
}

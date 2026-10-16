package io.github.maborokun.docscanner.domain.receipt

import kotlin.math.abs

/** One recognised text line with where it sits on the page (any consistent unit, e.g. pixels). */
data class OcrLine(
    val text: String,
    val left: Float,
    val centerY: Float,
    val height: Float,
)

private const val SAME_ROW_FRACTION = 0.6f

/**
 * Rebuilds printed rows from recognised lines. OCR engines often return a receipt as columns (all item names, then
 * all prices); lines whose vertical centres lie within [SAME_ROW_FRACTION] of a line height belong to the same
 * row and are joined left to right, so "Pasta" and "10.84" become "Pasta 10.84".
 */
fun joinIntoRows(lines: List<OcrLine>): String {
    val rows = mutableListOf<MutableList<OcrLine>>()
    for (line in lines.filter { it.text.isNotBlank() }.sortedBy { it.centerY }) {
        val row = rows.lastOrNull()
        val rowCenter = row?.map { it.centerY }?.average()?.toFloat()
        val tolerance = SAME_ROW_FRACTION * (row?.maxOf { it.height } ?: 0f).coerceAtLeast(line.height)
        if (row != null &&
            rowCenter != null &&
            abs(line.centerY - rowCenter) <= tolerance
        ) {
            row += line
        } else {
            rows += mutableListOf(line)
        }
    }
    return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
}

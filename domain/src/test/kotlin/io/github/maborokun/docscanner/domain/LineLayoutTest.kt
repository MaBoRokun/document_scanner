package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.receipt.OcrLine
import io.github.maborokun.docscanner.domain.receipt.ReceiptParser
import io.github.maborokun.docscanner.domain.receipt.joinIntoRows
import kotlin.test.Test
import kotlin.test.assertEquals

class LineLayoutTest {
    private fun line(
        text: String,
        left: Float,
        y: Float,
    ) = OcrLine(text, left, y, height = 20f)

    @Test
    fun columnsAreMergedBackIntoRows() {
        val columns =
            listOf(
                line("Pasta", 40f, 200f),
                line("Bread", 40f, 244f),
                line("TOTAL", 40f, 320f),
                line("10.84", 500f, 202f),
                line("17.78", 500f, 242f),
                line("EUR 28.62", 420f, 321f),
            )
        assertEquals("Pasta 10.84\nBread 17.78\nTOTAL EUR 28.62", joinIntoRows(columns))
    }

    @Test
    fun rowsAreOrderedTopToBottomAndLeftToRight() {
        val text = joinIntoRows(listOf(line("b", 100f, 50f), line("c", 10f, 90f), line("a", 10f, 50f)))
        assertEquals("a b\nc", text)
    }

    @Test
    fun blankLinesAndEmptyInputAreIgnored() {
        assertEquals("", joinIntoRows(emptyList()))
        assertEquals("x", joinIntoRows(listOf(line("  ", 0f, 0f), line("x", 0f, 10f))))
    }

    @Test
    fun rebuiltRowsFeedTheParserLikeAnyOtherReceiptText() {
        val rows =
            joinIntoRows(
                listOf(
                    line("BLUE BAKERY", 150f, 40f),
                    line("2026-04-19", 220f, 120f),
                    line("Pasta", 40f, 200f),
                    line("10.84", 500f, 201f),
                    line("TOTAL", 40f, 320f),
                    line("EUR 35.18", 420f, 321f),
                ),
            )
        val fields = ReceiptParser.parse(rows)
        assertEquals("BLUE BAKERY", fields.merchant)
        assertEquals(3518L, fields.totalMinor)
        assertEquals("EUR", fields.currency)
    }
}

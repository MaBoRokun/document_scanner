package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.receipt.ReceiptParser
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Texts mimic what OCR returns for the synthetic receipts from tools/generate_synthetic.py. */
class ReceiptParserTest {
    private class Case(
        val name: String,
        val text: String,
        val merchant: String?,
        val date: LocalDate?,
        val totalMinor: Long?,
        val currency: String?,
    )

    private val cases =
        listOf(
            Case(
                "generated receipt 0",
                "BLUE BAKERY\n2026-04-19\nPasta 10.84\nBread 17.78\nBread 1.60\nJuice 4.96\nTOTAL EUR 35.18",
                "BLUE BAKERY",
                LocalDate.of(2026, 4, 19),
                3518,
                "EUR",
            ),
            Case(
                "dollar sign and US month name",
                "Corner Cafe\nOct 5, 2026\nCoffee 3.50\nTax 0.28\nTotal \$3.78",
                "Corner Cafe",
                LocalDate.of(2026, 10, 5),
                378,
                "USD",
            ),
            Case(
                "european separators and day first",
                "Fresh Market\n05.10.2026\nMilch 1,29\nBrot 2,49\nSumme 3,78 €",
                "Fresh Market",
                LocalDate.of(2026, 10, 5),
                378,
                "EUR",
            ),
            Case(
                "thousands separators and pounds",
                "Night Owl Books\n3 Mar 2026\nAtlas 1,234.50\nTOTAL £1,234.50",
                "Night Owl Books",
                LocalDate.of(2026, 3, 3),
                123450,
                "GBP",
            ),
            Case(
                "subtotal and tax lines are ignored",
                "Green Pharmacy\n12/31/2026\nSubtotal 20.00\nVAT 4.00\nTOTAL 24.00\nCash 30.00\nChange 6.00",
                "Green Pharmacy",
                LocalDate.of(2026, 12, 31),
                2400,
                null,
            ),
            Case(
                "last total line wins",
                "Shop\n2026-01-02\nTotal 5.00\n...\nGrand Total 7.50",
                "Shop",
                LocalDate.of(2026, 1, 2),
                750,
                null,
            ),
            Case(
                "total amount on the next line",
                "Shop\n2026-01-02\nTOTAL\n12.34",
                "Shop",
                LocalDate.of(2026, 1, 2),
                1234,
                null,
            ),
            Case(
                "no keyword falls back to the largest amount",
                "Tiny Kiosk\n2026-02-03\nGum 1.20\nWater 2.50",
                "Tiny Kiosk",
                LocalDate.of(2026, 2, 3),
                250,
                null,
            ),
            Case(
                "two digit year",
                "Corner Cafe\n5/10/26\nTotal 3.00",
                "Corner Cafe",
                LocalDate.of(2026, 10, 5),
                300,
                null,
            ),
            Case(
                "header noise is skipped for the merchant",
                "RECEIPT\nTel 555 1234\n12345\nOld Mill Bakery\n2026-05-06\nTotal 9.99",
                "Old Mill Bakery",
                LocalDate.of(2026, 5, 6),
                999,
                null,
            ),
            Case(
                "impossible dates are rejected",
                "Shop\n2026-13-45\n31/02/2026\nTotal 1.00",
                "Shop",
                null,
                100,
                null,
            ),
            Case("empty text", "", null, null, null, null),
            Case("only noise", "...\n--\n123", null, null, null, null),
        )

    @Test
    fun parsesEveryCase() {
        cases.forEach { c ->
            val fields = ReceiptParser.parse(c.text)
            assertEquals(c.merchant, fields.merchant, "${c.name}: merchant")
            assertEquals(c.date, fields.date, "${c.name}: date")
            assertEquals(c.totalMinor, fields.totalMinor, "${c.name}: total")
            assertEquals(c.currency, fields.currency, "${c.name}: currency")
        }
    }

    @Test
    fun confidenceReflectsWhatWasFound() {
        val full = ReceiptParser.parse("Shop\n2026-01-02\nTotal 5.00")
        assertEquals(1f, full.confidence, 1e-5f)

        val noDate = ReceiptParser.parse("Shop\nTotal 5.00")
        assertEquals(0.7f, noDate.confidence, 1e-5f)

        val guessedTotal = ReceiptParser.parse("Shop\n2026-01-02\nGum 5.00")
        assertEquals(0.2f + 0.3f + 0.5f * 0.6f, guessedTotal.confidence, 1e-5f)

        assertEquals(0f, ReceiptParser.parse("").confidence, 0f)
    }

    @Test
    fun veryLongMerchantLinesAreTrimmed() {
        val fields = ReceiptParser.parse("A".repeat(100))
        assertEquals(40, fields.merchant?.length)
    }

    @Test
    fun nothingUsableGivesAllNulls() {
        val fields = ReceiptParser.parse("12 34\n--")
        assertNull(fields.merchant)
        assertNull(fields.date)
        assertNull(fields.totalMinor)
        assertTrue(fields.confidence == 0f)
    }
}

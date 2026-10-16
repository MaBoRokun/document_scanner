package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.model.ReceiptFields
import io.github.maborokun.docscanner.domain.receipt.FieldError
import io.github.maborokun.docscanner.domain.receipt.FieldsInput
import io.github.maborokun.docscanner.domain.receipt.FieldsResult
import io.github.maborokun.docscanner.domain.receipt.formatMinor
import io.github.maborokun.docscanner.domain.receipt.parseFieldsInput
import io.github.maborokun.docscanner.domain.receipt.parseMoney
import io.github.maborokun.docscanner.domain.receipt.toInput
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReceiptFieldsFormTest {
    @Test
    fun moneyParsingHandlesCommonFormats() {
        val cases =
            mapOf(
                "12" to 1200L,
                "12.5" to 1250L,
                "12,50" to 1250L,
                "1,234.56" to 123456L,
                "1.234,56" to 123456L,
                "1.234" to 123400L,
                " 0.05 " to 5L,
                "1 234,50" to 123450L,
            )
        cases.forEach { (text, minor) -> assertEquals(minor, parseMoney(text), text) }
    }

    @Test
    fun moneyParsingRejectsNonsense() {
        listOf("", "abc", "12 EUR", "-5", ".").forEach { assertNull(parseMoney(it), it) }
    }

    @Test
    fun formattingIsLocaleIndependentAndRoundTrips() {
        assertEquals("12.50", formatMinor(1250))
        assertEquals("0.05", formatMinor(5))
        assertEquals(1250L, parseMoney(formatMinor(1250)))
    }

    @Test
    fun validInputBecomesFieldsWithFullConfidence() {
        val result = parseFieldsInput(FieldsInput(" Shop ", "2026-10-15", "12,50", "eur"))
        assertEquals(
            FieldsResult.Valid(ReceiptFields("Shop", LocalDate.of(2026, 10, 15), 1250, "EUR", 1f)),
            result,
        )
    }

    @Test
    fun emptyInputClearsTheFields() {
        assertEquals(FieldsResult.Valid(null), parseFieldsInput(FieldsInput()))
    }

    @Test
    fun partialInputIsAccepted() {
        val result = parseFieldsInput(FieldsInput(merchant = "Shop")) as FieldsResult.Valid
        assertEquals("Shop", result.fields?.merchant)
        assertNull(result.fields?.totalMinor)
    }

    @Test
    fun everyBadFieldIsReported() {
        val result = parseFieldsInput(FieldsInput("x", "15.10.2026", "twelve", "EURO"))
        assertEquals(FieldsResult.Invalid(setOf(FieldError.Date, FieldError.Total, FieldError.Currency)), result)
    }

    @Test
    fun fieldsConvertBackToFormText() {
        val fields = ReceiptFields("Shop", LocalDate.of(2026, 1, 2), 1250, "USD", 0.9f)
        assertEquals(FieldsInput("Shop", "2026-01-02", "12.50", "USD"), fields.toInput())
        assertEquals(FieldsInput(), null.toInput())
    }
}

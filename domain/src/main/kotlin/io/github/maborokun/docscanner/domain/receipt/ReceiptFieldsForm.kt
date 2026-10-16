package io.github.maborokun.docscanner.domain.receipt

import io.github.maborokun.docscanner.domain.model.ReceiptFields
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

/** What the user typed into the editable receipt fields. */
data class FieldsInput(
    val merchant: String = "",
    val date: String = "",
    val total: String = "",
    val currency: String = "",
)

enum class FieldError { Date, Total, Currency }

sealed interface FieldsResult {
    /** [fields] is null when every field was left empty. */
    data class Valid(
        val fields: ReceiptFields?,
    ) : FieldsResult

    data class Invalid(
        val errors: Set<FieldError>,
    ) : FieldsResult
}

private const val MINOR_PER_MAJOR = 100
private val amountText = Regex("[0-9.,]+")
private const val CURRENCY_LENGTH = 3
private const val MAX_FRACTION_DIGITS = 2

/** Values the user typed are as sure as it gets, so a valid edit has confidence 1. */
fun parseFieldsInput(input: FieldsInput): FieldsResult {
    val errors = mutableSetOf<FieldError>()
    val date = optional(input.date) { parseDate(it) }.orError(FieldError.Date, errors)
    val total = optional(input.total) { parseMoney(it) }.orError(FieldError.Total, errors)
    val currency = optional(input.currency.uppercase()) { parseCurrency(it) }.orError(FieldError.Currency, errors)
    val merchant = input.merchant.trim().takeIf { it.isNotEmpty() }
    return when {
        errors.isNotEmpty() -> FieldsResult.Invalid(errors)
        listOf(merchant, date, total, currency).all { it == null } -> FieldsResult.Valid(null)
        else -> FieldsResult.Valid(ReceiptFields(merchant, date, total, currency, confidence = 1f))
    }
}

/** Blank input is fine (no value); otherwise [parse] must succeed, and its failure is a [Parsed.Bad]. */
private sealed interface Parsed<out T> {
    data class Ok<T>(
        val value: T?,
    ) : Parsed<T>

    data object Bad : Parsed<Nothing>
}

private fun <T> optional(
    text: String,
    parse: (String) -> T?,
): Parsed<T> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Parsed.Ok(null)
    return parse(trimmed)?.let { Parsed.Ok(it) } ?: Parsed.Bad
}

private fun <T> Parsed<T>.orError(
    error: FieldError,
    errors: MutableSet<FieldError>,
): T? =
    when (this) {
        is Parsed.Ok -> value
        Parsed.Bad -> {
            errors += error
            null
        }
    }

private fun parseDate(text: String): LocalDate? =
    try {
        LocalDate.parse(text)
    } catch (_: DateTimeException) {
        null
    }

private fun parseCurrency(code: String): String? =
    code.takeIf {
        it.length == CURRENCY_LENGTH && it.all(Char::isLetter)
    }

/** The text a form field starts with; the inverse of [parseFieldsInput]. */
fun ReceiptFields?.toInput(): FieldsInput =
    FieldsInput(
        merchant = this?.merchant.orEmpty(),
        date = this?.date?.toString().orEmpty(),
        total = this?.totalMinor?.let { formatMinor(it) }.orEmpty(),
        currency = this?.currency.orEmpty(),
    )

/** 1250 -> "12.50", independent of the device locale so it can be parsed back. */
fun formatMinor(minor: Long): String =
    String.format(
        Locale.ROOT,
        "%d.%02d",
        minor / MINOR_PER_MAJOR,
        minor % MINOR_PER_MAJOR,
    )

/**
 * Accepts "12.5", "12,50", "1,234.56" and "1.234,56": a separator followed by one or two trailing digits is the
 * decimal point, everything else is grouping. Returns minor units, or null when [raw] is not an amount.
 */
fun parseMoney(raw: String): Long? {
    val s = raw.trim().replace(" ", "")
    if (!amountText.matches(s)) return null
    val sep = s.lastIndexOfAny(charArrayOf('.', ','))
    val hasFraction = sep >= 0 && s.length - sep - 1 in 1..MAX_FRACTION_DIGITS
    val whole = (if (hasFraction) s.substring(0, sep) else s).filter(Char::isDigit)
    val fraction = if (hasFraction) s.substring(sep + 1).padEnd(MAX_FRACTION_DIGITS, '0') else "00"
    return whole.toLongOrNull()?.let { it * MINOR_PER_MAJOR + fraction.toLong() }
}

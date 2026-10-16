package io.github.maborokun.docscanner.domain.receipt

import io.github.maborokun.docscanner.domain.model.ReceiptFields
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Heuristics that pull merchant, date and total out of OCR text. Everything is a guess with a confidence, never a
 * fact: the UI shows the values as editable fields.
 */
object ReceiptParser {
    private const val MERCHANT_SEARCH_LINES = 6
    private const val MAX_MERCHANT_LENGTH = 40
    private const val MIN_LETTERS = 3
    private const val MIN_YEAR = 2000
    private const val MAX_YEAR = 2100
    private const val TWO_DIGIT_YEAR_BASE = 2000
    private const val MAX_MONTH = 12
    private const val MERCHANT_WEIGHT = 0.2f
    private const val DATE_WEIGHT = 0.3f
    private const val TOTAL_WEIGHT = 0.5f
    private const val FALLBACK_TOTAL_FACTOR = 0.6f

    private val months =
        listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monthPattern = months.joinToString("|") { "$it[a-z]*" }

    private val isoDate = Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""")
    private val numericDate = Regex("""(?<!\d)(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?!\d)""")
    private val dayMonthName = Regex("""(?<!\d)(\d{1,2})\s+($monthPattern)\.?,?\s+(\d{4})""", RegexOption.IGNORE_CASE)
    private val monthNameDay = Regex("""($monthPattern)\.?\s+(\d{1,2}),?\s+(\d{4})""", RegexOption.IGNORE_CASE)

    private val totalKeyword =
        Regex(
            """\b(grand\s+total|total\s+due|amount\s+due|balance\s+due|total|summe|gesamt|betrag|importe|montant)\b""",
            RegexOption.IGNORE_CASE,
        )
    private val notATotal =
        Regex("""sub\s*-?\s*total|tax|vat|mwst|tip|change|rounding|saving|discount""", RegexOption.IGNORE_CASE)
    private val amount = Regex("""(?<![\d.,])(\d{1,3}(?:[.,\s]\d{3})*[.,]\d{2}|\d+[.,]\d{2})(?!\d)""")
    private val currencyCode = Regex("""\b(EUR|USD|GBP|CHF|PLN|CAD|AUD|JPY|SEK|NOK|DKK|CZK|UAH)\b""")
    private val merchantRejects =
        Regex("""receipt|invoice|tel\b|phone|www\.|http|@|total|date|^\W*[\d\W]+$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): ReceiptFields {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val merchant = findMerchant(lines)
        val date = findDate(text)
        val total = findTotal(lines)
        val currency = findCurrency(total?.line, text)
        val confidence =
            (if (merchant != null) MERCHANT_WEIGHT else 0f) +
                (if (date != null) DATE_WEIGHT else 0f) +
                (total?.let { TOTAL_WEIGHT * (if (it.byKeyword) 1f else FALLBACK_TOTAL_FACTOR) } ?: 0f)
        return ReceiptFields(merchant, date, total?.minor, currency, confidence)
    }

    private fun findMerchant(lines: List<String>): String? =
        lines
            .take(MERCHANT_SEARCH_LINES)
            .firstOrNull { line ->
                line.count { it.isLetter() } >= MIN_LETTERS && !merchantRejects.containsMatchIn(line)
            }?.take(MAX_MERCHANT_LENGTH)

    private fun findDate(text: String): LocalDate? =
        sequence {
            isoDate.findAll(text).forEach {
                yield(date(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()))
            }
            numericDate.findAll(text).forEach { yield(numericDate(it)) }
            dayMonthName.findAll(text).forEach {
                yield(date(it.groupValues[3].toInt(), monthNumber(it.groupValues[2]), it.groupValues[1].toInt()))
            }
            monthNameDay.findAll(text).forEach {
                yield(date(it.groupValues[3].toInt(), monthNumber(it.groupValues[1]), it.groupValues[2].toInt()))
            }
        }.firstOrNull { it != null }

    /** `a/b/y`: day-first unless the second number cannot be a month (US order) and the first can. */
    private fun numericDate(match: MatchResult): LocalDate? {
        val a = match.groupValues[1].toInt()
        val b = match.groupValues[2].toInt()
        val raw = match.groupValues[3]
        val year = if (raw.length == 2) TWO_DIGIT_YEAR_BASE + raw.toInt() else raw.toInt()
        return if (b > MAX_MONTH && a <= MAX_MONTH) date(year, a, b) else date(year, b, a)
    }

    private fun monthNumber(name: String): Int = months.indexOf(name.take(3).lowercase()) + 1

    private fun date(
        year: Int,
        month: Int,
        day: Int,
    ): LocalDate? =
        try {
            LocalDate.of(year, month, day).takeIf { it.year in MIN_YEAR..MAX_YEAR }
        } catch (_: DateTimeException) {
            null
        }

    private class Total(
        val minor: Long,
        val line: String?,
        val byKeyword: Boolean,
    )

    private fun findTotal(lines: List<String>): Total? {
        // The real total is usually the last "total" line; subtotals and tax lines are skipped.
        val keywordLines =
            lines.withIndex().filter { (_, l) ->
                totalKeyword.containsMatchIn(l) &&
                    !notATotal.containsMatchIn(l)
            }
        for ((index, line) in keywordLines.reversed()) {
            val value = lastAmount(line) ?: lines.getOrNull(index + 1)?.let(::lastAmount)
            if (value != null) return Total(value, line, byKeyword = true)
        }
        val largest = lines.flatMap { l -> amount.findAll(l).mapNotNull { toMinor(it.value) }.toList() }.maxOrNull()
        return largest?.let { Total(it, null, byKeyword = false) }
    }

    private fun lastAmount(line: String): Long? = amount.findAll(line).lastOrNull()?.let { toMinor(it.value) }

    /** "1,234.56", "1.234,56", "35,18" and "35.18" all mean the last two digits are the fraction. */
    private fun toMinor(raw: String): Long? {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length < 3) null else digits.toLongOrNull()
    }

    private fun findCurrency(
        totalLine: String?,
        text: String,
    ): String? {
        fun symbolOrCode(s: String): String? =
            currencyCode.find(s)?.value
                ?: when {
                    '€' in s -> "EUR"
                    '£' in s -> "GBP"
                    '$' in s -> "USD"
                    else -> null
                }
        return totalLine?.let(::symbolOrCode) ?: symbolOrCode(text)
    }
}

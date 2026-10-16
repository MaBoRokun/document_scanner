package io.github.maborokun.docscanner.domain.classify

import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.model.ReceiptFields
import kotlin.math.abs

/**
 * What is known about a scanned page. [aspect] is the long side divided by the short side, so it is 1 or more
 * whatever the rotation.
 */
data class PageEvidence(
    val aspect: Float,
    val text: String,
    val receipt: ReceiptFields?,
)

/** One way of recognising a category; returns null when the evidence says nothing about it. */
fun interface CategoryRule {
    fun match(evidence: PageEvidence): DocumentCategory?
}

/**
 * Guesses what a scan shows by asking its [rules] in order; the first rule with an opinion wins, so the most
 * specific evidence goes first. A suggestion only: the user can always change it. New categories are added with
 * a new rule, without touching the existing ones.
 */
class DocumentClassifier(
    private val rules: List<CategoryRule> = DEFAULT_RULES,
    private val fallback: DocumentCategory = DocumentCategory.Document,
) {
    fun classify(
        aspect: Float,
        text: String,
        receipt: ReceiptFields?,
    ): DocumentCategory {
        val evidence = PageEvidence(aspect, text, receipt)
        return rules.firstNotNullOfOrNull { it.match(evidence) } ?: fallback
    }

    companion object {
        val DEFAULT_RULES: List<CategoryRule> = listOf(ReceiptRule, IdentityRule, A4Rule, BookPageRule)
    }
}

/** A confident parse of merchant, date and total means a receipt. */
object ReceiptRule : CategoryRule {
    private const val CONFIDENCE = 0.5f

    override fun match(evidence: PageEvidence) =
        DocumentCategory.Receipt.takeIf { (evidence.receipt?.confidence ?: 0f) >= CONFIDENCE }
}

/** Passport and ID cards, by the words printed on them or by the machine-readable zone. */
object IdentityRule : CategoryRule {
    private const val MRZ_FILLERS = 5
    private val words =
        Regex(
            """\b(passport|passeport|pasaporte|reisepass|identity\s+card|id\s+card|""" +
                """driving\s+licen[cs]e|national\s+id)\b""",
            RegexOption.IGNORE_CASE,
        )
    private val machineReadableZone = Regex("""P<[A-Z]{3}""")

    override fun match(evidence: PageEvidence): DocumentCategory? {
        val text = evidence.text
        val identity =
            words.containsMatchIn(text) ||
                machineReadableZone.containsMatchIn(text) ||
                text.count { it == '<' } >= MRZ_FILLERS
        return DocumentCategory.Passport.takeIf { identity }
    }
}

/** A4 and US Letter proportions. */
object A4Rule : CategoryRule {
    private const val A4_RATIO = 1.414f
    private const val LETTER_RATIO = 1.294f
    private const val A4_TOLERANCE = 0.07f
    private const val LETTER_TOLERANCE = 0.04f

    override fun match(evidence: PageEvidence) =
        DocumentCategory.A4Page.takeIf {
            abs(evidence.aspect - A4_RATIO) <= A4_TOLERANCE || abs(evidence.aspect - LETTER_RATIO) <= LETTER_TOLERANCE
        }
}

/** A dense page of text that is not A4-shaped. */
object BookPageRule : CategoryRule {
    private const val MIN_CHARS = 400
    private const val MIN_RATIO = 1.2f
    private const val MAX_RATIO = 1.8f

    override fun match(evidence: PageEvidence) =
        DocumentCategory.BookPage.takeIf {
            evidence.text.length >= MIN_CHARS && evidence.aspect in MIN_RATIO..MAX_RATIO
        }
}

/** Long side over short side of a [width] x [height] image; 1 for a degenerate size. */
fun pageAspect(
    width: Int,
    height: Int,
): Float {
    val short = minOf(width, height)
    return if (short <= 0) 1f else maxOf(width, height).toFloat() / short
}

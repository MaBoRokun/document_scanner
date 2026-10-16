package io.github.maborokun.docscanner.data

import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.Page
import io.github.maborokun.docscanner.domain.model.PageEdit
import io.github.maborokun.docscanner.domain.model.PageTint
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.ReceiptFields
import io.github.maborokun.docscanner.domain.model.Stroke
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Stores a quad as a JSON array of eight numbers: x,y per corner, clockwise from the top-left. */
internal fun Quad.toJson(): String =
    corners.joinToString(prefix = "[", postfix = "]", separator = ",") {
        "${it.x},${it.y}"
    }

/** Falls back to [Quad.FULL] for anything that is not exactly eight numbers, so one bad row never breaks the list. */
internal fun quadFromJson(json: String): Quad {
    val numbers =
        json
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .map { it.trim().toFloatOrNull() }
    if (numbers.size != QUAD_VALUES || numbers.any { it == null }) return Quad.FULL
    val (topLeft, topRight, bottomRight, bottomLeft) = numbers.filterNotNull().chunked(2).map { (x, y) -> Point(x, y) }
    return Quad(topLeft, topRight, bottomRight, bottomLeft)
}

/** Stores a page's edits as JSON; an unedited page is an empty string. */
internal fun PageEdit.toJson(): String {
    if (this == PageEdit()) return ""
    val strokeArray = JSONArray()
    for (stroke in strokes) {
        val points = JSONArray()
        stroke.points.forEach { point ->
            points.put(point.x.toDouble())
            points.put(point.y.toDouble())
        }
        strokeArray.put(
            JSONObject().put("c", stroke.color).put("w", stroke.width.toDouble()).put("p", points),
        )
    }
    return JSONObject()
        .put("mode", mode.name)
        .put("b", brightness.toDouble())
        .put("tint", tint.name)
        .put("strokes", strokeArray)
        .toString()
}

/** Falls back to "no edits" for anything unreadable, so one bad row never breaks a page. */
internal fun editFromJson(json: String): PageEdit {
    if (json.isBlank()) return PageEdit()
    return try {
        val root = JSONObject(json)
        val strokeArray = root.optJSONArray("strokes") ?: JSONArray()
        val strokes =
            (0 until strokeArray.length()).map { index ->
                val item = strokeArray.getJSONObject(index)
                val numbers = item.getJSONArray("p")
                Stroke(
                    color = item.getInt("c"),
                    width = item.getDouble("w").toFloat(),
                    points =
                        (0 until numbers.length() / 2).map { i ->
                            Point(numbers.getDouble(2 * i).toFloat(), numbers.getDouble(2 * i + 1).toFloat())
                        },
                )
            }
        PageEdit(
            mode = EnhanceMode.entries.firstOrNull { it.name == root.optString("mode") } ?: EnhanceMode.None,
            brightness = root.optDouble("b", 0.0).toFloat(),
            tint = PageTint.entries.firstOrNull { it.name == root.optString("tint") } ?: PageTint.None,
            strokes = strokes,
        )
    } catch (_: JSONException) {
        PageEdit()
    }
}

internal fun ReceiptFields.toEntity(
    title: String,
    createdAt: Long,
    category: DocumentCategory = DocumentCategory.Document,
): DocumentEntity =
    DocumentEntity(
        title = title,
        createdAt = createdAt,
        category = category.name,
        merchant = merchant,
        totalMinor = totalMinor,
        currency = currency,
        date = date?.toString(),
        confidence = confidence,
    )

internal fun DocumentEntity.toFields(): ReceiptFields? {
    if (listOf(merchant, totalMinor, currency, date).all { it == null }) return null
    val parsed =
        try {
            date?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) {
            null
        }
    return ReceiptFields(merchant, parsed, totalMinor, currency, confidence)
}

internal fun DocumentRow.toDomain() =
    Document(
        document.id,
        document.title,
        document.createdAt,
        pageCount,
        document.toFields(),
        thumbnailPath,
        DocumentCategory.fromName(document.category),
        document.comment,
    )

internal fun NewPage.toEntity(index: Int) =
    PageEntity(
        documentId = 0,
        index = index,
        imagePath = imagePath,
        width = width,
        height = height,
        quadJson = quad.toJson(),
        ocrText = ocrText,
        originalPath = originalPath,
        editJson = edit.toJson(),
    )

internal fun PageEntity.toDomain() =
    Page(
        id,
        documentId,
        index,
        imagePath,
        width,
        height,
        ocrText,
        quadFromJson(quadJson),
        originalPath,
        editFromJson(editJson),
    )

/**
 * Turns free user input into a safe FTS4 expression: every word becomes a quoted phrase ending in `*`, so
 * operators like `-` or `"` can't break the parse and the last token is prefix-matched.
 * Returns null when no usable word remains.
 */
internal fun ftsQuery(input: String): String? {
    val words = input.split(Regex("\\s+")).map { it.replace("\"", "") }.filter { it.isNotEmpty() }
    return words.takeIf { it.isNotEmpty() }?.joinToString(" ") { "\"$it*\"" }
}

/** Escapes `%`, `_` and `\` for a LIKE pattern using `\` as escape char. */
internal fun likePattern(input: String): String {
    val escaped = input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "%$escaped%"
}

private const val QUAD_VALUES = 8

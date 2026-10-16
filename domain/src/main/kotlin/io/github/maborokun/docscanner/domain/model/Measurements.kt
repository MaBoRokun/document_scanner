package io.github.maborokun.docscanner.domain.model

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A known paper size; [heightMm] is null for a roll of unknown length such as a till receipt. */
data class PaperFormat(
    val name: String,
    val widthMm: Float,
    val heightMm: Float?,
)

object PaperFormats {
    val A4 = PaperFormat("A4", 210f, 297f)
    val LETTER = PaperFormat("Letter", 215.9f, 279.4f)
    val RECEIPT_80 = PaperFormat("Receipt 80 mm", 80f, null)
    val ALL = listOf(A4, LETTER, RECEIPT_80)
}

enum class ScanHint {
    /** The page is rotated noticeably in the frame. */
    Tilted,

    /** The frame is out of focus or moving. */
    Blurry,

    /** The detector is unsure this is a page. */
    LowConfidence,

    /** The page fills too little of the frame. */
    TooSmall,

    /** The quad's proportions do not match the chosen paper: strong perspective, or a different paper. */
    AspectMismatch,
}

/**
 * Length of one quad edge. [relative] is the edge length divided by the long side of the frame (so it does not
 * depend on resolution); [mm] is only an estimate and only present when a paper format was chosen.
 */
data class EdgeMeasure(
    val relative: Float,
    val mm: Float?,
)

data class QuadMeasurement(
    val top: EdgeMeasure,
    val right: EdgeMeasure,
    val bottom: EdgeMeasure,
    val left: EdgeMeasure,
    /** Long side over short side of the page, at least 1. */
    val aspect: Float,
    val hints: Set<ScanHint>,
)

private const val TILT_LIMIT_DEGREES = 8f
private const val BLUR_LIMIT = 0.1f
private const val CONFIDENCE_LIMIT = 0.5f
private const val MIN_AREA_FRACTION = 0.15f
private const val ASPECT_TOLERANCE = 0.08f

/**
 * Edge lengths and quality hints for [quad] on a frame of [frame] pixels. With a [paper] the quad's long side is
 * taken to be the paper's long side (the short side for a roll), which scales every edge to millimetres; this is
 * an estimate that ignores lens and perspective distortion.
 */
fun measureQuad(
    quad: Quad,
    frame: FrameSize,
    paper: PaperFormat? = null,
    confidence: Float = 1f,
    skewDegrees: Float = 0f,
    blurScore: Float = 1f,
): QuadMeasurement {
    fun px(
        a: Point,
        b: Point,
    ) = hypot((a.x - b.x) * frame.width, (a.y - b.y) * frame.height)

    val top = px(quad.topLeft, quad.topRight)
    val right = px(quad.topRight, quad.bottomRight)
    val bottom = px(quad.bottomRight, quad.bottomLeft)
    val left = px(quad.bottomLeft, quad.topLeft)
    val width = (top + bottom) / 2f
    val height = (left + right) / 2f
    val longSide = max(width, height)
    val shortSide = min(width, height)
    val aspect = if (shortSide > 0f) longSide / shortSide else 1f

    val hints = mutableSetOf<ScanHint>()
    if (abs(skewDegrees) > TILT_LIMIT_DEGREES) hints += ScanHint.Tilted
    if (blurScore < BLUR_LIMIT) hints += ScanHint.Blurry
    if (confidence < CONFIDENCE_LIMIT) hints += ScanHint.LowConfidence
    if (quad.area < MIN_AREA_FRACTION) hints += ScanHint.TooSmall

    var mmPerPx: Float? = null
    if (paper != null && longSide > 0f) {
        val paperHeight = paper.heightMm
        if (paperHeight == null) {
            mmPerPx = paper.widthMm / shortSide
        } else {
            mmPerPx = max(paper.widthMm, paperHeight) / longSide
            val paperAspect = max(paper.widthMm, paperHeight) / min(paper.widthMm, paperHeight)
            if (abs(aspect - paperAspect) / paperAspect > ASPECT_TOLERANCE) hints += ScanHint.AspectMismatch
        }
    }

    val frameLong = max(frame.width, frame.height).toFloat()

    fun edge(lengthPx: Float) = EdgeMeasure(lengthPx / frameLong, mmPerPx?.let { it * lengthPx })
    return QuadMeasurement(edge(top), edge(right), edge(bottom), edge(left), aspect, hints)
}

package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.maborokun.docscanner.domain.model.Quad

private const val FILL_ALPHA = 0.18f
const val QUAD_OVERLAY_TAG = "quad-overlay"

/**
 * Draws [quad] (normalized coordinates of an image with ratio [imageAspect], fitted into this composable) with
 * corner handles and optional edge labels (top, right, bottom, left; null entries are skipped). When [editable],
 * a handle can be dragged and every move is reported through [onQuadChange].
 */
@Composable
fun QuadOverlay(
    quad: Quad,
    imageAspect: Float,
    color: Color,
    modifier: Modifier = Modifier,
    edgeLabels: List<String?> = emptyList(),
    /** Must match the inset of the picture underneath, see [ImageViewport]. */
    inset: Dp = 0.dp,
    editable: Boolean = false,
    onQuadChange: (Quad) -> Unit = {},
    /** Called when the finger lifts after moving a corner, so one drag can be one undo step. */
    onQuadChangeFinished: () -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    val currentQuad by rememberUpdatedState(quad)
    val currentOnChange by rememberUpdatedState(onQuadChange)
    val currentOnFinished by rememberUpdatedState(onQuadChangeFinished)
    val dragModifier =
        if (editable) {
            Modifier.pointerInput(imageAspect, inset) {
                val viewport = ImageViewport(size.width.toFloat(), size.height.toFloat(), imageAspect, inset.toPx())
                val slop = HANDLE_GRAB_DP.dp.toPx()
                awaitEachGesture {
                    // Hit-test where the finger lands, not where the touch slop is crossed.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val corner = nearestCorner(currentQuad, viewport, down.position, slop)
                    if (corner >= 0) {
                        down.consume()
                        drag(down.id) { change ->
                            change.consume()
                            currentOnChange(currentQuad.withCorner(corner, viewport.toImage(change.position)))
                        }
                        currentOnFinished()
                    }
                }
            }
        } else {
            Modifier
        }
    Canvas(modifier.fillMaxSize().testTag(QUAD_OVERLAY_TAG).then(dragModifier)) {
        drawQuad(
            quad,
            ImageViewport(size.width, size.height, imageAspect, inset.toPx()),
            color,
            edgeLabels,
            textMeasurer,
        )
    }
}

private const val HANDLE_GRAB_DP = 32
private const val HANDLE_RADIUS_DP = 9
private const val STROKE_DP = 3
private const val LABEL_PADDING_DP = 4
private const val LABEL_MARGIN_DP = 4

private fun nearestCorner(
    quad: Quad,
    viewport: ImageViewport,
    at: Offset,
    maxDistance: Float,
): Int {
    val distances = quad.corners.map { (viewport.toScreen(it) - at).getDistance() }
    val best = distances.indices.minByOrNull { distances[it] } ?: return -1
    return if (distances[best] <= maxDistance) best else -1
}

private fun DrawScope.drawQuad(
    quad: Quad,
    viewport: ImageViewport,
    color: Color,
    edgeLabels: List<String?>,
    textMeasurer: TextMeasurer,
) {
    val points = quad.corners.map(viewport::toScreen)
    val path =
        Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
    drawPath(path, color.copy(alpha = FILL_ALPHA))
    drawPath(path, color, style = Stroke(width = STROKE_DP.dp.toPx()))

    // Labels sit at the edge midpoints, pushed away from the centre so they stay outside the page.
    val centre =
        Offset(points.sumOf { it.x.toDouble() }.toFloat() / 4f, points.sumOf { it.y.toDouble() }.toFloat() / 4f)
    edgeLabels.forEachIndexed { i, label ->
        if (label == null) return@forEachIndexed
        val mid = (points[i] + points[(i + 1) % 4]) / 2f
        val outward = (mid - centre).let { if (it.getDistance() > 0f) it / it.getDistance() else Offset.Zero }
        val text = textMeasurer.measure(label, TextStyle(color = Color.White, fontSize = 12.sp))
        val pad = LABEL_PADDING_DP.dp.toPx()
        val box = Size(text.size.width + 2 * pad, text.size.height + 2 * pad)
        val raw = mid + outward * (box.height / 2f) - Offset(box.width / 2f, box.height / 2f)
        // Keep every label fully on the canvas, whatever the page does.
        val margin = LABEL_MARGIN_DP.dp.toPx()
        val pos =
            Offset(
                raw.x.coerceIn(margin, (size.width - box.width - margin).coerceAtLeast(margin)),
                raw.y.coerceIn(margin, (size.height - box.height - margin).coerceAtLeast(margin)),
            )
        drawRect(Color.Black.copy(alpha = 0.6f), pos, box)
        drawText(text, topLeft = pos + Offset(pad, pad))
    }

    val radius = HANDLE_RADIUS_DP.dp.toPx()
    points.forEach {
        drawCircle(Color.White, radius, it)
        drawCircle(color, radius, it, style = Stroke(width = STROKE_DP.dp.toPx() / 2f))
    }
}

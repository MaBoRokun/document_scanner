package io.github.maborokun.docscanner.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.maborokun.docscanner.core.designsystem.ImageViewport
import io.github.maborokun.docscanner.core.designsystem.OverlayColors
import io.github.maborokun.docscanner.core.designsystem.OverlayInset
import io.github.maborokun.docscanner.core.designsystem.PageImage
import io.github.maborokun.docscanner.core.designsystem.QuadOverlay
import io.github.maborokun.docscanner.core.designsystem.ScannerColors
import io.github.maborokun.docscanner.core.designsystem.rememberPageBitmap
import io.github.maborokun.docscanner.domain.model.Stroke

@Composable
internal fun Preview(
    state: EditorUiState,
    actions: EditorActions,
) {
    when {
        state.loading -> Center { CircularProgressIndicator() }
        state.notFound -> Center { Text(stringResource(R.string.page_gone), color = ScannerColors.OnCanvasMuted) }
        state.tool == EditorTool.Crop -> CropPreview(state, actions)
        else -> PagePreview(state, actions)
    }
}

@Composable
internal fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** The untouched photo with the crop outline on it; dragging a corner changes what the page will contain. */
@Composable
internal fun CropPreview(
    state: EditorUiState,
    actions: EditorActions,
) {
    val original = rememberPageBitmap(state.originalPath, PREVIEW_PX)
    if (original == null) {
        Center { CircularProgressIndicator() }
        return
    }
    // The same inset for picture and outline keeps the corner handles fully on screen.
    Box(Modifier.fillMaxSize().padding(OverlayInset)) {
        Image(
            original,
            stringResource(R.string.original_photo),
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
    QuadOverlay(
        quad = state.quad,
        imageAspect = original.width.toFloat() / original.height,
        color = OverlayColors.Good,
        inset = OverlayInset,
        editable = true,
        onQuadChange = actions.onQuad,
        onQuadChangeFinished = actions.onQuadFinished,
    )
}

/** The cropped page with brightness and tint applied live, and the pen strokes drawn on top. */
@Composable
internal fun PagePreview(
    state: EditorUiState,
    actions: EditorActions,
) {
    val base = state.base
    if (base == null) {
        Center { CircularProgressIndicator() }
        return
    }
    val filter =
        remember(
            state.edit.brightness,
            state.edit.tint,
        ) { ColorFilter.colorMatrix(ColorMatrix(state.edit.colorMatrix())) }
    Box(Modifier.fillMaxSize().padding(OverlayInset), contentAlignment = Alignment.Center) {
        PageImage(
            path = base.path,
            maxPx = PREVIEW_PX,
            contentDescription = stringResource(R.string.page_preview),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = filter,
        )
    }
    StrokeLayer(base.aspect, state.edit.strokes, state.tool == EditorTool.Draw, actions)
    if (state.rendering) Center { CircularProgressIndicator() }
}

/** Draws the strokes over the page and, in the draw tool, records new ones from finger movement. */
@Composable
internal fun StrokeLayer(
    aspect: Float,
    strokes: List<Stroke>,
    drawing: Boolean,
    actions: EditorActions,
) {
    val onStart by rememberUpdatedState(actions.onStrokeStart)
    val onMove by rememberUpdatedState(actions.onStrokeMove)
    val onEnd by rememberUpdatedState(actions.onStrokeEnd)
    val insetPx = with(LocalDensity.current) { OverlayInset.toPx() }
    val gestures =
        if (drawing) {
            Modifier.pointerInput(aspect) {
                val viewport = ImageViewport(size.width.toFloat(), size.height.toFloat(), aspect, insetPx)
                detectDragGestures(
                    onDragStart = { onStart(viewport.toImage(it)) },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onEnd() },
                ) { change, _ ->
                    change.consume()
                    onMove(viewport.toImage(change.position))
                }
            }
        } else {
            Modifier
        }
    Canvas(Modifier.fillMaxSize().then(gestures)) {
        val viewport = ImageViewport(size.width, size.height, aspect, insetPx)
        strokes.forEach { stroke ->
            val width = maxOf(1f, stroke.width * viewport.rect.width)
            val points = stroke.points.map { viewport.toScreen(it) }
            if (points.size == 1) {
                drawCircle(Color(stroke.color), radius = width / 2f, center = points.first())
            } else {
                points.zipWithNext().forEach { (from, to) ->
                    drawLine(Color(stroke.color), from, to, strokeWidth = width, cap = StrokeCap.Round)
                }
            }
        }
    }
}

internal const val PREVIEW_PX = 2000
internal const val BRIGHTNESS_RANGE = 0.5f
internal val SWATCH_SIZE = 36.dp
internal val PANEL_MIN_HEIGHT = 160.dp

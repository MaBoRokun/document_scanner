package io.github.maborokun.docscanner.feature.scan

import android.view.Surface
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.maborokun.docscanner.core.common.catching
import io.github.maborokun.docscanner.core.designsystem.OverlayInset
import io.github.maborokun.docscanner.core.designsystem.QuadOverlay
import io.github.maborokun.docscanner.core.designsystem.ScannerColors
import io.github.maborokun.docscanner.core.designsystem.ScannerScaffold
import io.github.maborokun.docscanner.core.designsystem.ShutterButton
import io.github.maborokun.docscanner.core.designsystem.confidenceColor
import io.github.maborokun.docscanner.domain.model.PaperFormat
import io.github.maborokun.docscanner.domain.model.PaperFormats
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
internal fun CameraScreen(
    viewModel: ScanViewModel,
    title: String,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val session = remember { CameraSession(viewModel.edgeDetector) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()
    val request by session.surfaceRequest.collectAsStateWithLifecycle()
    var cameraError by remember { mutableStateOf<ScanUiState.Failed?>(null) }

    LaunchedEffect(session, lifecycleOwner) {
        catching { session.bind(context, lifecycleOwner) }
            .onFailure { cameraError = ScanUiState.Failed(R.string.error_camera, it.message) }
    }
    // Restarts with the lifecycle, so analysis results are only collected while the screen is visible.
    LaunchedEffect(session) { viewModel.trackDetections(session.detections) }

    val error = cameraError ?: (state as? ScanUiState.Failed)
    if (error != null) {
        NoticeScreen(
            title = title,
            heading = stringResource(R.string.something_wrong),
            message = error.text(),
            action = stringResource(R.string.dismiss),
            onAction = {
                cameraError = null
                viewModel.onResultHandled()
            },
            onClose = onClose,
            modifier = modifier,
        )
        return
    }
    ScannerScaffold(
        title = title,
        onClose = onClose,
        modifier = modifier,
        trailing = { PaperMenu(overlay.paper, viewModel::selectPaper) },
        bottomBar = {
            CaptureBar(
                status = statusLine(overlay),
                resetVisible = overlay.manual,
                captureEnabled = request != null,
                onReset = viewModel::resetQuad,
                onCapture = {
                    scope.launch {
                        val file = File(context.cacheDir, "captures/${UUID.randomUUID()}.jpg")
                        catching { session.capture(context, file, view.display?.rotation ?: Surface.ROTATION_0) }
                            .onSuccess(viewModel::onCaptured)
                            .onFailure { viewModel.onCaptureFailed(R.string.error_capture, it.message) }
                    }
                },
            )
        },
    ) {
        // The preview and its outline share one inset, so handles and labels are never cut off at the screen edge.
        // Fit, not crop: the overlay maps image coordinates onto exactly this rectangle.
        Box(Modifier.fillMaxSize().padding(OverlayInset)) {
            request?.let {
                CameraXViewfinder(
                    surfaceRequest = it,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        val mmFormat = stringResource(R.string.edge_mm)
        val quad = overlay.quad
        val frame = overlay.frameSize
        if (quad != null && frame != null) {
            QuadOverlay(
                quad = quad,
                imageAspect = frame.width.toFloat() / frame.height,
                color = confidenceColor(overlay.confidence),
                edgeLabels =
                    overlay.measurement
                        ?.let { m ->
                            listOf(m.top, m.right, m.bottom, m.left).map { edge -> edgeLabel(edge, mmFormat) }
                        }.orEmpty(),
                inset = OverlayInset,
                editable = true,
                onQuadChange = viewModel::onQuadEdited,
            )
        }
    }
}

/** Status line above the shutter: what the detector sees and what to do about it. */
@Composable
internal fun CaptureBar(
    status: StatusLine,
    resetVisible: Boolean,
    captureEnabled: Boolean,
    onReset: () -> Unit,
    onCapture: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val (icon, tint) =
                when (status.tone) {
                    StatusTone.Searching -> Icons.Default.Search to ScannerColors.OnCanvasMuted
                    StatusTone.Warning -> Icons.Default.Warning to WARNING_TINT
                    StatusTone.Good -> Icons.Default.CheckCircle to GOOD_TINT
                }
            Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = tint)
            Text(
                stringResource(status.text),
                style = MaterialTheme.typography.bodyMedium,
                color = ScannerColors.OnCanvas,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterStart) {
                if (resetVisible) TextButton(onClick = onReset) { Text(stringResource(R.string.reset_corners)) }
            }
            ShutterButton(onClick = onCapture, enabled = captureEnabled)
            Box(Modifier.width(96.dp))
        }
    }
}

/** A pill in the top bar that opens the paper-format choice. */
@Composable
internal fun PaperMenu(
    selected: PaperFormat?,
    onSelect: (PaperFormat?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = 12.dp)) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(PILL_FILL)
                .clickable { open = true }
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected?.name ?: stringResource(R.string.no_paper_size), style = MaterialTheme.typography.labelLarge)
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.choose_paper_size),
                Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.no_paper_size)) }, onClick = {
                open = false
                onSelect(null)
            })
            PaperFormats.ALL.forEach { format ->
                DropdownMenuItem(text = { Text(format.name) }, onClick = {
                    open = false
                    onSelect(format)
                })
            }
        }
    }
}

internal val WARNING_TINT = Color(0xFFFFB955)
internal val GOOD_TINT = Color(0xFF6FD28A)
internal val PILL_FILL = Color(0x33FFFFFF)

/** The failure as a sentence in the app's language; an unknown cause reads "unknown error". */
@Composable
internal fun ScanUiState.Failed.text(): String =
    stringResource(
        message,
        detail ?: stringResource(R.string.error_unknown),
    )

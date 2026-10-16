package io.github.maborokun.docscanner.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.maborokun.docscanner.core.designsystem.OverlayColors
import io.github.maborokun.docscanner.core.designsystem.OverlayInset
import io.github.maborokun.docscanner.core.designsystem.PRIMARY_BUTTON_WEIGHT
import io.github.maborokun.docscanner.core.designsystem.PageImage
import io.github.maborokun.docscanner.core.designsystem.QuadOverlay
import io.github.maborokun.docscanner.core.designsystem.ScannerScaffold

/**
 * The captured photo with an editable outline; Retake throws it away, Save runs the pipeline. Enhancement, filters
 * and the rest are done afterwards in the page editor, so this step has just the two buttons.
 */
@Composable
internal fun ReviewScreen(
    review: ScanUiState.Reviewing,
    viewModel: ScanViewModel,
    modifier: Modifier = Modifier,
) {
    ScannerScaffold(
        title = stringResource(R.string.adjust_page),
        onClose = viewModel::onRetake,
        closeLabel = stringResource(R.string.retake),
        modifier = modifier,
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::onRetake,
                    Modifier.weight(1f),
                ) { Text(stringResource(R.string.retake)) }
                Button(
                    onClick = viewModel::onSave,
                    Modifier.weight(PRIMARY_BUTTON_WEIGHT),
                ) { Text(stringResource(R.string.save)) }
            }
        },
    ) {
        // Same inset as the live preview: the corner handles stay fully on screen.
        Box(Modifier.fillMaxSize().padding(OverlayInset)) {
            PageImage(
                path = review.image.path,
                maxPx = REVIEW_IMAGE_PX,
                contentDescription = stringResource(R.string.captured_photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        QuadOverlay(
            quad = review.quad,
            imageAspect = review.image.width.toFloat() / review.image.height,
            color = OverlayColors.Good,
            inset = OverlayInset,
            editable = true,
            onQuadChange = viewModel::onReviewQuadChanged,
        )
    }
}

private const val REVIEW_IMAGE_PX = 2000

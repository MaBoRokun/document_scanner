package io.github.maborokun.docscanner.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.maborokun.docscanner.core.designsystem.OverlayPill
import io.github.maborokun.docscanner.core.designsystem.PageImage
import io.github.maborokun.docscanner.core.designsystem.ScannerColors
import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.model.Page

/** The pages on a dark card, swipeable. Tapping a page opens the editor; the buttons edit or remove it. */
@Composable
internal fun PageViewer(
    document: DocumentWithPages,
    pager: PagerState,
    actions: DetailActions,
    onRemoveRequested: (Page) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(VIEWER_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(ScannerColors.Canvas),
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 12.dp) { index ->
            val page = document.pages[index]
            PageImage(
                path = page.imagePath,
                maxPx = PAGE_PX,
                contentDescription = stringResource(R.string.page_of_tap, index + 1, document.pages.size),
                modifier = Modifier.fillMaxSize().padding(12.dp).clickable { actions.onEditPage(page.id) },
                contentScale = ContentScale.Fit,
            )
        }
        if (document.pages.size > 1) {
            OverlayPill(
                "${pager.currentPage + 1}/${document.pages.size}",
                Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }
        val current = document.pages[pager.currentPage]
        Box(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            PageButton(Icons.Default.Create, stringResource(R.string.edit_page_cd)) { actions.onEditPage(current.id) }
        }
        if (document.pages.size > 1) {
            Box(Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                PageButton(
                    Icons.Default.Delete,
                    stringResource(R.string.remove_this_page_cd),
                ) { onRemoveRequested(current) }
            }
        }
    }
}

@Composable
internal fun PageButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = CircleShape, color = ScannerColors.Pill, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = description, tint = ScannerColors.OnCanvas, modifier = Modifier.padding(9.dp))
    }
}

internal const val PAGE_PX = 1600
internal const val COMMENT_SAVE_DELAY_MS = 600L
internal val VIEWER_HEIGHT = 380.dp

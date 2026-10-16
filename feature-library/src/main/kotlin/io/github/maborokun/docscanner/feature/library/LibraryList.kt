package io.github.maborokun.docscanner.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.maborokun.docscanner.core.designsystem.CategoryTag
import io.github.maborokun.docscanner.core.designsystem.PageImage
import io.github.maborokun.docscanner.core.designsystem.ScreenMargin
import io.github.maborokun.docscanner.core.designsystem.displayName
import io.github.maborokun.docscanner.core.designsystem.hairline
import io.github.maborokun.docscanner.domain.model.Document

@Composable
internal fun DocumentList(
    state: LibraryUiState,
    actions: LibraryScreenActions,
    selectedId: Long?,
    onDeleteRequested: (Document) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.documents.isEmpty()) {
            item {
                Text(
                    noMatchMessage(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(state.documents, key = { it.id }) { document ->
            SwipeToDeleteRow(enabled = !state.selecting, onDelete = { onDeleteRequested(document) }) {
                DocumentCard(
                    document = document,
                    highlighted = if (state.selecting) document.id in state.selected else document.id == selectedId,
                    selecting = state.selecting,
                    checked = document.id in state.selected,
                    onClick = { if (state.selecting) actions.onToggle(document.id) else actions.onOpen(document.id) },
                    onLongClick = {
                        if (state.selecting) actions.onToggle(document.id) else actions.onLongPress(document.id)
                    },
                )
            }
        }
    }
}

@Composable
internal fun noMatchMessage(state: LibraryUiState): String {
    val category = state.category
    val typed = state.query.trim()
    return when {
        typed.isNotEmpty() && category != null ->
            stringResource(R.string.no_match_category_query, category.displayName(), typed)
        typed.isNotEmpty() -> stringResource(R.string.no_match_query, typed)
        category != null -> stringResource(R.string.no_docs_in_category, category.displayName())
        else -> ""
    }
}

/** Swipe a row to the left to ask for its deletion; the row springs back and the confirmation decides. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToDeleteRow(
    enabled: Boolean,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
                false
            },
        )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        Modifier.padding(end = 24.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
        content = { content() },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DocumentCard(
    document: Document,
    highlighted: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val container =
        if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                ),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selecting) Checkbox(checked = checked, onCheckedChange = null)
            Surface(
                shape = RoundedCornerShape(8.dp),
                border = hairline(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                PageImage(
                    path = document.thumbnailPath,
                    maxPx = THUMBNAIL_PX,
                    contentDescription = null,
                    modifier = Modifier.size(width = 52.dp, height = 68.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatDate(document.createdAt)} · ${pages(document.pageCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryTag(document.category)
                    totalLabel(document)?.let { Tag(it, MaterialTheme.colorScheme.secondaryContainer) }
                }
            }
        }
    }
}

@Composable
internal fun Tag(
    text: String,
    color: Color,
) {
    Surface(shape = CircleShape, color = color) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

internal const val THUMBNAIL_PX = 200
internal val SEARCH_HEIGHT = 56.dp

package io.github.maborokun.docscanner.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.maborokun.docscanner.core.designsystem.ActionBar
import io.github.maborokun.docscanner.core.designsystem.ActionDialog
import io.github.maborokun.docscanner.core.designsystem.AppIcons
import io.github.maborokun.docscanner.core.designsystem.DialogAction
import io.github.maborokun.docscanner.core.designsystem.DialogTone
import io.github.maborokun.docscanner.core.designsystem.EmptyState
import io.github.maborokun.docscanner.core.designsystem.PRIMARY_BUTTON_WEIGHT
import io.github.maborokun.docscanner.core.designsystem.ScreenMargin
import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.model.Page

@Composable
fun DetailRoute(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onAddPage: () -> Unit,
    onExport: () -> Unit,
    onEditPage: (Long) -> Unit,
    onSharePage: (pageId: Long, asImage: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    resultMode: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel, onBack, onAddPage, onExport, onEditPage, onSharePage) {
            DetailActions(
                onBack = onBack,
                onDelete = { viewModel.delete(onDone = onBack) },
                onSaveFields = viewModel::saveFields,
                onFieldsEdited = viewModel::onFieldsEdited,
                onAddPage = onAddPage,
                onExport = onExport,
                onRemovePage = viewModel::removePage,
                onCategory = viewModel::setCategory,
                onComment = viewModel::setComment,
                onEditPage = onEditPage,
                onSharePage = onSharePage,
            )
        }
    DetailScreen(state, form, actions, modifier, resultMode)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    state: DetailUiState,
    form: FieldsFormState,
    actions: DetailActions,
    modifier: Modifier = Modifier,
    /** The last step of a scan: Done replaces Back and Delete, since the document was only just made. */
    resultMode: Boolean = false,
) {
    var showShare by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pageToRemove by remember { mutableStateOf<Page?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val content = state as? DetailUiState.Content
    val pager = rememberPagerState { content?.document?.pages?.size ?: 0 }
    ClearFocusWhenStopped()
    Scaffold(
        modifier = modifier,
        topBar = {
            DetailTopBar(
                title = content?.document?.document?.title ?: stringResource(R.string.document),
                resultMode = resultMode,
                canDelete = content != null,
                onBack = actions.onBack,
                onDelete = { confirmDelete = true },
            )
        },
        bottomBar = { if (content != null) DetailActionBar(actions) { showShare = true } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val inner = Modifier.padding(padding).consumeWindowInsets(padding).imePadding()
        when (state) {
            DetailUiState.Loading -> Box(inner.fillMaxSize())
            DetailUiState.NotFound ->
                EmptyState(
                    icon = AppIcons.Document,
                    title = stringResource(R.string.document_not_found),
                    message = stringResource(R.string.document_not_found_hint),
                    modifier = inner,
                )
            is DetailUiState.Content ->
                DocumentContent(state.document, pager, form, actions, { pageToRemove = it }, snackbar, inner)
        }
    }
    if (showShare && content != null) {
        val pageId =
            content.document.pages
                .getOrNull(pager.currentPage)
                ?.id
        ShareDialog(
            onDismiss = { showShare = false },
            onPage = { asImage ->
                showShare = false
                pageId?.let { actions.onSharePage(it, asImage) }
            },
            onDocument = {
                showShare = false
                actions.onExport()
            },
        )
    }
    if (confirmDelete && content != null) {
        ConfirmDeleteDialog(
            heading = stringResource(R.string.delete_document_title),
            message = deleteMessage(content.document.document.title),
            onConfirm = {
                confirmDelete = false
                actions.onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    pageToRemove?.let { page ->
        ConfirmDeleteDialog(
            heading = stringResource(R.string.remove_page_title),
            message = deleteMessage(stringResource(R.string.page_n, page.index + 1)),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = {
                pageToRemove = null
                actions.onRemovePage(page.id)
            },
            onDismiss = { pageToRemove = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    resultMode: Boolean,
    canDelete: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (!resultMode) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = {
            if (resultMode) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.done)) }
            } else if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_document_cd))
                }
            }
        },
    )
}

/** Drops the text focus when the screen leaves the foreground, so returning to it does not reopen the keyboard. */
@Composable
internal fun ClearFocusWhenStopped() {
    val focusManager = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, focusManager) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event ==
                    Lifecycle.Event.ON_STOP
                ) {
                    focusManager.clearFocus()
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** The two things you do with a document, pinned so they never cover content or hide behind the system bar. */
@Composable
internal fun DetailActionBar(
    actions: DetailActions,
    onShare: () -> Unit,
) {
    ActionBar {
        OutlinedButton(onClick = actions.onAddPage, Modifier.weight(1f)) { Text(stringResource(R.string.add_page)) }
        Button(onClick = onShare, Modifier.weight(PRIMARY_BUTTON_WEIGHT)) {
            Icon(AppIcons.Export, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(R.string.share), Modifier.padding(start = 8.dp))
        }
    }
}

/** What to share: the page being looked at as an image or a PDF, or the whole document as a PDF. */
@Composable
internal fun ShareDialog(
    onDismiss: () -> Unit,
    onPage: (asImage: Boolean) -> Unit,
    onDocument: () -> Unit,
) {
    ActionDialog(
        title = stringResource(R.string.share),
        onDismiss = onDismiss,
        actions =
            listOf(
                DialogAction(stringResource(R.string.share_page_image), { onPage(true) }, DialogTone.Destructive),
                DialogAction(stringResource(R.string.share_page_pdf), { onPage(false) }, DialogTone.Destructive),
                DialogAction(stringResource(R.string.share_document_pdf), onDocument, DialogTone.Destructive),
                DialogAction(stringResource(R.string.cancel), onDismiss, DialogTone.Negative),
            ),
    )
}

@Composable
internal fun DocumentContent(
    document: DocumentWithPages,
    pager: PagerState,
    form: FieldsFormState,
    actions: DetailActions,
    onRemoveRequested: (Page) -> Unit,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val current = document.pages.getOrNull(pager.currentPage)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize(),
            contentPadding = PaddingValues(ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PageViewer(document, pager, actions, onRemoveRequested) }
            item {
                Text(
                    stringResource(
                        R.string.scanned_on,
                        formatDate(document.document.createdAt),
                        pages(document.pages.size),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { DetailsCard(document.document, actions) }
            if (document.document.category == DocumentCategory.Receipt) {
                item {
                    ReceiptFieldsForm(
                        document.document.extractedFields,
                        form,
                        actions.onSaveFields,
                        actions.onFieldsEdited,
                    )
                }
            }
            // Only for the page being looked at, and only when it has any text.
            if (current != null && current.ocrText.isNotBlank()) {
                item(key = "text") { RecognisedText(current, document.pages.size, snackbar) }
            }
        }
    }
}

internal val MAX_CONTENT_WIDTH = 640.dp

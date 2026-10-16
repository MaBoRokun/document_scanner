package io.github.maborokun.docscanner.feature.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.maborokun.docscanner.core.designsystem.ActionBar
import io.github.maborokun.docscanner.core.designsystem.ActionDialog
import io.github.maborokun.docscanner.core.designsystem.AppIcons
import io.github.maborokun.docscanner.core.designsystem.DialogAction
import io.github.maborokun.docscanner.core.designsystem.DialogTone
import io.github.maborokun.docscanner.core.designsystem.EmptyState
import io.github.maborokun.docscanner.core.designsystem.PRIMARY_BUTTON_WEIGHT
import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.receipt.formatMinor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Everything the library screen can ask for; the route wires these to the view model and to navigation. */
class LibraryScreenActions(
    val onQueryChange: (String) -> Unit,
    val onCategory: (DocumentCategory?) -> Unit,
    val onApplySuggestion: (DocumentCategory) -> Unit,
    val onOpen: (Long) -> Unit,
    val onLongPress: (Long) -> Unit,
    val onToggle: (Long) -> Unit,
    val onCancelSelection: () -> Unit,
    val onDelete: (Long) -> Unit,
    val onDeleteSelected: () -> Unit,
    val onShareSelected: () -> Unit,
    val onScan: () -> Unit,
    val onImport: () -> Unit,
    val onSettings: () -> Unit,
    val onMessageShown: () -> Unit,
)

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    onOpen: (Long) -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onShare: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.import(it.toString()) }
        }
    val latest by rememberUpdatedState(state)
    val actions =
        remember(viewModel, onOpen, onScan, onSettings, onShare, picker) {
            LibraryScreenActions(
                onQueryChange = viewModel::onQueryChange,
                onCategory = viewModel::onCategoryChange,
                onApplySuggestion = viewModel::applySuggestion,
                onOpen = onOpen,
                onLongPress = viewModel::startSelecting,
                onToggle = viewModel::toggleSelected,
                onCancelSelection = viewModel::cancelSelecting,
                onDelete = viewModel::delete,
                onDeleteSelected = viewModel::deleteSelected,
                onShareSelected = {
                    onShare(latest.selected.toList())
                    viewModel.cancelSelecting()
                },
                onScan = onScan,
                onImport = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onSettings = onSettings,
                onMessageShown = viewModel::onMessageShown,
            )
        }
    LibraryScreen(state, actions, modifier, selectedId)
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryScreenActions,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Document?>(null) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    val messageText =
        state.message?.let { stringResource(it.text, it.detail ?: stringResource(R.string.error_unknown)) }
    LaunchedEffect(state.message) {
        messageText?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    BackHandler(enabled = state.selecting, onBack = actions.onCancelSelection)
    Scaffold(
        modifier = modifier,
        topBar = { LibraryHeader(state, actions) },
        bottomBar = { LibraryBottomBar(state, actions, onDeleteSelected = { confirmDeleteSelected = true }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (state.isEmpty) {
                EmptyState(
                    icon = AppIcons.Scan,
                    title = stringResource(R.string.no_scans_title),
                    message = stringResource(R.string.no_scans_message),
                )
            } else {
                DocumentList(state, actions, selectedId, onDeleteRequested = { pendingDelete = it })
            }
        }
    }
    pendingDelete?.let { document ->
        ConfirmDeleteDialog(
            heading = stringResource(R.string.delete_document_title),
            message = deleteMessage(document.title),
            onConfirm = {
                actions.onDelete(document.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
    if (confirmDeleteSelected) {
        ConfirmDeleteDialog(
            heading = pluralStringResource(R.plurals.delete_documents_title, state.selected.size, state.selected.size),
            message = stringResource(R.string.delete_documents_message),
            onConfirm = {
                actions.onDeleteSelected()
                confirmDeleteSelected = false
            },
            onDismiss = { confirmDeleteSelected = false },
        )
    }
}

/** The library's bottom bar: scan and import normally, share and delete while rows are being picked. */
@Composable
internal fun LibraryBottomBar(
    state: LibraryUiState,
    actions: LibraryScreenActions,
    onDeleteSelected: () -> Unit,
) {
    ActionBar {
        if (state.selecting) {
            val any = state.selected.isNotEmpty()
            OutlinedButton(onClick = actions.onShareSelected, Modifier.weight(1f), enabled = any) {
                ButtonLabel(AppIcons.Export, stringResource(R.string.share))
            }
            Button(
                onClick = onDeleteSelected,
                Modifier.weight(1f),
                enabled = any,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) { ButtonLabel(Icons.Default.Delete, stringResource(R.string.delete)) }
        } else {
            OutlinedButton(onClick = actions.onImport, Modifier.weight(1f)) {
                ButtonLabel(AppIcons.Image, stringResource(R.string.import_image))
            }
            Button(onClick = actions.onScan, Modifier.weight(PRIMARY_BUTTON_WEIGHT)) {
                ButtonLabel(AppIcons.Scan, stringResource(R.string.scan))
            }
        }
    }
}

@Composable
internal fun ButtonLabel(
    icon: ImageVector,
    text: String,
) {
    Icon(icon, contentDescription = null, Modifier.size(18.dp))
    Text(text, Modifier.padding(start = 8.dp))
}

@Composable
internal fun ConfirmDeleteDialog(
    heading: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.delete),
) {
    ActionDialog(
        title = heading,
        message = message,
        onDismiss = onDismiss,
        actions =
            listOf(
                DialogAction(confirmLabel, onConfirm, DialogTone.Destructive),
                DialogAction(stringResource(R.string.cancel), onDismiss, DialogTone.Negative),
            ),
    )
}

@Composable
internal fun deleteMessage(title: String) = stringResource(R.string.delete_message, title)

@Composable
internal fun pages(count: Int): String = pluralStringResource(R.plurals.pages, count, count)

/** "EUR 35.18", from the recognised or edited receipt fields; null when there is no total. */
internal fun totalLabel(document: Document): String? {
    val fields = document.extractedFields
    val total = fields?.totalMinor ?: return null
    return listOfNotNull(fields.currency, formatMinor(total)).joinToString(" ")
}

internal fun formatDate(epochMillis: Long): String =
    DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

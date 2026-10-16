package io.github.maborokun.docscanner.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.maborokun.docscanner.core.designsystem.ActionDialog
import io.github.maborokun.docscanner.core.designsystem.AppIcons
import io.github.maborokun.docscanner.core.designsystem.DialogAction
import io.github.maborokun.docscanner.core.designsystem.DialogTone
import io.github.maborokun.docscanner.core.designsystem.ScannerScaffold
import io.github.maborokun.docscanner.domain.model.PageTint
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.processing.EnhanceMode

/** Everything the editor can ask for; the route wires these to the view model. */
class EditorActions(
    val onClose: () -> Unit,
    val onSave: () -> Unit,
    val onUndo: () -> Unit,
    val onTool: (EditorTool) -> Unit,
    val onQuad: (Quad) -> Unit,
    val onQuadFinished: () -> Unit,
    val onMode: (EnhanceMode) -> Unit,
    val onBrightness: (Float) -> Unit,
    val onBrightnessFinished: () -> Unit,
    val onTint: (PageTint) -> Unit,
    val onPenColor: (Int) -> Unit,
    val onPenSize: (PenSize) -> Unit,
    val onStrokeStart: (Point) -> Unit,
    val onStrokeMove: (Point) -> Unit,
    val onStrokeEnd: () -> Unit,
)

@Composable
fun EditorRoute(
    viewModel: EditorViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestClose = { if (viewModel.hasChanges()) confirmDiscard = true else onClose() }
    BackHandler(onBack = requestClose)
    val actions =
        remember(viewModel, onClose) {
            EditorActions(
                onClose = requestClose,
                onSave = { viewModel.save(onDone = onClose) },
                onUndo = viewModel::undo,
                onTool = viewModel::selectTool,
                onQuad = viewModel::setQuad,
                onQuadFinished = viewModel::finishQuad,
                onMode = viewModel::setMode,
                onBrightness = viewModel::setBrightness,
                onBrightnessFinished = viewModel::finishBrightness,
                onTint = viewModel::setTint,
                onPenColor = viewModel::onPenColor,
                onPenSize = viewModel::onPenSize,
                onStrokeStart = viewModel::startStroke,
                onStrokeMove = viewModel::extendStroke,
                onStrokeEnd = viewModel::finishStroke,
            )
        }
    EditorScreen(state, actions, modifier)
    if (confirmDiscard) {
        ActionDialog(
            title = stringResource(R.string.discard_title),
            message = stringResource(R.string.discard_message),
            onDismiss = { confirmDiscard = false },
            actions =
                listOf(
                    DialogAction(stringResource(R.string.discard), onClose, DialogTone.Destructive),
                    DialogAction(
                        stringResource(R.string.keep_editing),
                        { confirmDiscard = false },
                        DialogTone.Negative,
                    ),
                ),
        )
    }
}

@Composable
fun EditorScreen(
    state: EditorUiState,
    actions: EditorActions,
    modifier: Modifier = Modifier,
) {
    ScannerScaffold(
        title = stringResource(R.string.edit_page),
        onClose = actions.onClose,
        modifier = modifier,
        trailing = {
            IconButton(onClick = actions.onUndo, enabled = state.canUndo) {
                Icon(AppIcons.Undo, contentDescription = stringResource(R.string.undo_cd))
            }
            Button(
                onClick = actions.onSave,
                enabled = state.canUndo && !state.saving,
                modifier = Modifier.padding(end = 8.dp),
            ) { Text(stringResource(if (state.saving) R.string.saving else R.string.save)) }
        },
        bottomBar = {
            ToolPanel(state, actions)
            ToolTabs(state.tool, actions.onTool)
        },
    ) {
        Preview(state, actions)
    }
}

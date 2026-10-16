package io.github.maborokun.docscanner.feature.library

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.maborokun.docscanner.core.designsystem.AppIcons
import io.github.maborokun.docscanner.core.designsystem.EmptyState
import kotlinx.coroutines.launch

/** Calls out of the library host to the rest of the app. */
class LibraryHostActions(
    val onScan: () -> Unit,
    val onSettings: () -> Unit,
    val onAddPage: (Long) -> Unit,
    /** Shares these documents as one PDF. */
    val onShare: (List<Long>) -> Unit,
    /** Shares one page of a document, as an image when the flag is true, otherwise as a one-page PDF. */
    val onSharePage: (documentId: Long, pageId: Long, asImage: Boolean) -> Unit,
    val onEditPage: (documentId: Long, pageId: Long) -> Unit,
)

/**
 * The library as an adaptive list-detail: one pane at a time on compact and medium windows, list and document side
 * by side on expanded ones, following window resizes (multi-window, foldables, desktop mode).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LibraryHost(
    libraryViewModel: LibraryViewModel,
    detailViewModel: @Composable (Long) -> DetailViewModel,
    actions: LibraryHostActions,
    modifier: Modifier = Modifier,
    adaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
) {
    val directive = calculatePaneScaffoldDirective(adaptiveInfo)
    // The navigator keeps the directive it was created with, so it is rebuilt when the number of panes changes
    // (window resize, fold, rotation); the selected document survives in saveable state of our own.
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    key(directive.maxHorizontalPartitions) {
        val navigator =
            rememberListDetailPaneScaffoldNavigator<Long>(
                scaffoldDirective = directive,
                initialDestinationHistory =
                    listOfNotNull(
                        ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
                        selectedId?.let { ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail, it) },
                    ),
            )
        val scope = rememberCoroutineScope()
        LaunchedEffect(navigator) {
            snapshotFlow { navigator.currentDestination?.contentKey }.collect { selectedId = it }
        }
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            modifier = modifier,
            listPane = {
                AnimatedPane {
                    LibraryRoute(
                        viewModel = libraryViewModel,
                        onOpen = { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } },
                        onScan = actions.onScan,
                        onSettings = actions.onSettings,
                        onShare = actions.onShare,
                        selectedId = navigator.currentDestination?.contentKey,
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val id = navigator.currentDestination?.contentKey
                    if (id == null) {
                        NoSelection()
                    } else {
                        DetailRoute(
                            viewModel = detailViewModel(id),
                            onBack = { scope.launch { navigator.navigateBack() } },
                            onAddPage = { actions.onAddPage(id) },
                            onExport = { actions.onShare(listOf(id)) },
                            onEditPage = { pageId -> actions.onEditPage(id, pageId) },
                            onSharePage = { pageId, asImage -> actions.onSharePage(id, pageId, asImage) },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun NoSelection() {
    EmptyState(
        icon = AppIcons.Document,
        title = stringResource(R.string.select_document),
        message = stringResource(R.string.select_document_hint),
    )
}

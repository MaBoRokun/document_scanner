package io.github.maborokun.docscanner

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.core.designsystem.DocScannerTheme
import io.github.maborokun.docscanner.core.designsystem.LocalAppDispatchers
import io.github.maborokun.docscanner.feature.editor.EditorRoute
import io.github.maborokun.docscanner.feature.export.ExportRequest
import io.github.maborokun.docscanner.feature.export.ExportRoute
import io.github.maborokun.docscanner.feature.library.DetailRoute
import io.github.maborokun.docscanner.feature.library.DetailViewModel
import io.github.maborokun.docscanner.feature.library.LibraryHost
import io.github.maborokun.docscanner.feature.library.LibraryHostActions
import io.github.maborokun.docscanner.feature.scan.ScanRoute
import io.github.maborokun.docscanner.feature.settings.SettingsRoute
import org.koin.android.ext.android.get
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Full-screen destinations on top of the library. The document detail is not one of them: it lives inside the
 * adaptive library host, next to the list on wide windows and in place of it on narrow ones.
 */
sealed interface Screen {
    data object Library : Screen

    /** [appendTo] adds the new page to that document; null starts a new one. */
    data class Scan(
        val appendTo: Long? = null,
    ) : Screen

    /** The last step of a scan: the new document with Done, Add page and Share. */
    data class Result(
        val documentId: Long,
    ) : Screen

    data class Export(
        val request: ExportRequest,
    ) : Screen

    data class Edit(
        val documentId: Long,
        val pageId: Long,
    ) : Screen

    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light theme only: dark system icons on the light bars, even when the system is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val dispatchers = get<AppDispatchers>()
        setContent {
            CompositionLocalProvider(LocalAppDispatchers provides dispatchers) {
                DocScannerTheme { AppNavigation() }
            }
        }
    }
}

@Composable
private fun AppNavigation() {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Library) }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<Screen.Library> {
                    LibraryHost(
                        libraryViewModel = koinViewModel(),
                        detailViewModel = { id ->
                            koinViewModel<DetailViewModel>(key = "detail-$id") { parametersOf(id) }
                        },
                        actions =
                            LibraryHostActions(
                                onScan = { backStack.add(Screen.Scan()) },
                                onSettings = { backStack.add(Screen.Settings) },
                                onAddPage = { backStack.add(Screen.Scan(appendTo = it)) },
                                onShare = { backStack.add(Screen.Export(ExportRequest(it))) },
                                onSharePage = { document, page, asImage ->
                                    backStack.add(Screen.Export(ExportRequest(listOf(document), page, asImage)))
                                },
                                onEditPage = { document, page -> backStack.add(Screen.Edit(document, page)) },
                            ),
                    )
                }
                entry<Screen.Scan> { key ->
                    ScanRoute(
                        koinViewModel { parametersOf(key.appendTo ?: 0L) },
                        onSaved = { id ->
                            backStack.removeLastOrNull()
                            if (key.appendTo == null) backStack.add(Screen.Result(id))
                        },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Screen.Result> { key ->
                    DetailRoute(
                        viewModel = koinViewModel { parametersOf(key.documentId) },
                        onBack = { backStack.removeLastOrNull() },
                        onAddPage = { backStack.add(Screen.Scan(appendTo = key.documentId)) },
                        onExport = { backStack.add(Screen.Export(ExportRequest(listOf(key.documentId)))) },
                        onEditPage = { page -> backStack.add(Screen.Edit(key.documentId, page)) },
                        onSharePage = { page, asImage ->
                            backStack.add(Screen.Export(ExportRequest(listOf(key.documentId), page, asImage)))
                        },
                        resultMode = true,
                    )
                }
                entry<Screen.Export> { key ->
                    ExportRoute(
                        koinViewModel { parametersOf(key.request) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Screen.Edit> { key ->
                    EditorRoute(
                        koinViewModel { parametersOf(key.documentId, key.pageId) },
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
                entry<Screen.Settings> {
                    SettingsRoute(koinViewModel(), onBack = { backStack.removeLastOrNull() })
                }
            },
    )
}

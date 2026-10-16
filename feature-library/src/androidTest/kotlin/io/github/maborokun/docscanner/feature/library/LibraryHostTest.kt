package io.github.maborokun.docscanner.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import io.github.maborokun.docscanner.domain.FakeDocumentRepository
import io.github.maborokun.docscanner.domain.FakePageImageStore
import io.github.maborokun.docscanner.domain.fakeImportDocument
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.usecase.DeleteDocument
import io.github.maborokun.docscanner.domain.usecase.ObserveDocuments
import io.github.maborokun.docscanner.domain.usecase.RemovePage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/** The list-detail host at compact, medium and expanded window sizes, and when the window is resized live. */
class LibraryHostTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeDocumentRepository()
    private val store = FakePageImageStore()

    private fun info(widthDp: Float) =
        WindowAdaptiveInfo(WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp, HEIGHT_DP), Posture())

    private fun show(width: androidx.compose.runtime.MutableState<Float>) {
        runBlocking { repository.insert("Trip receipt", 1L, null, listOf(NewPage("/none.jpg", 10, 10))) }
        val library =
            LibraryViewModel(
                ObserveDocuments(repository),
                fakeImportDocument(repository, store) {
                    0L
                },
                DeleteDocument(repository, store),
            )
        rule.setContent {
            Box(Modifier.size(width.value.dp, HEIGHT_DP.dp)) {
                LibraryHost(
                    libraryViewModel = library,
                    detailViewModel = { id ->
                        DetailViewModel(
                            id,
                            repository,
                            repository,
                            DeleteDocument(repository, store),
                            RemovePage(repository, store),
                        )
                    },
                    actions = LibraryHostActions({}, {}, {}, {}, { _, _, _ -> }, { _, _ -> }),
                    adaptiveInfo = info(width.value),
                )
            }
        }
    }

    @Test
    fun compactShowsOnlyTheList() {
        show(mutableStateOf(COMPACT))
        rule.onNodeWithText("Search titles, text, comments").assertIsDisplayed()
        rule.onNodeWithText("Select a document").assertDoesNotExist()
    }

    @Test
    fun mediumStillShowsOnePane() {
        show(mutableStateOf(MEDIUM))
        rule.onNodeWithText("Search titles, text, comments").assertIsDisplayed()
        rule.onNodeWithText("Select a document").assertDoesNotExist()
    }

    @Test
    fun expandedShowsListAndDetailSideBySide() {
        show(mutableStateOf(EXPANDED))
        rule.onNodeWithText("Search titles, text, comments").assertIsDisplayed()
        rule.onNodeWithText("Select a document").assertIsDisplayed()

        rule.onNodeWithText("Trip receipt").performClick()
        rule.onNodeWithText("Details").assertIsDisplayed()
        // the list stays visible next to the document
        rule.onNodeWithText("Search titles, text, comments").assertIsDisplayed()
    }

    @Test
    fun compactReplacesTheListWithTheDocument() {
        show(mutableStateOf(COMPACT))
        rule.onNodeWithText("Trip receipt").performClick()
        rule.onNodeWithText("Details").assertIsDisplayed()
        rule.onNodeWithText("Search titles, text, comments").assertDoesNotExist()
    }

    @Test
    fun resizingTheWindowRearrangesThePanes() {
        val width = mutableStateOf(EXPANDED)
        show(width)
        rule.onNodeWithText("Select a document").assertIsDisplayed()

        width.value = COMPACT
        rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText("Select a document").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Select a document").assertDoesNotExist()
        rule.onNodeWithText("Search titles, text, comments").assertIsDisplayed()

        width.value = EXPANDED
        rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText("Select a document").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Select a document").assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val HEIGHT_DP = 800f
        const val COMPACT = 400f
        const val MEDIUM = 700f
        const val EXPANDED = 1000f
    }
}

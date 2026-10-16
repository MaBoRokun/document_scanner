package io.github.maborokun.docscanner.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.maborokun.docscanner.domain.FakeDocumentRepository
import io.github.maborokun.docscanner.domain.FakePageImageStore
import io.github.maborokun.docscanner.domain.fakeImportDocument
import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.Page
import io.github.maborokun.docscanner.domain.model.ReceiptFields
import io.github.maborokun.docscanner.domain.receipt.FieldsInput
import io.github.maborokun.docscanner.domain.usecase.DeleteDocument
import io.github.maborokun.docscanner.domain.usecase.ObserveDocuments
import io.github.maborokun.docscanner.domain.usecase.RemovePage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Library, detail and adaptive layouts with synthetic documents (no thumbnails, so nothing depends on image decoding).
 * Baselines live in src/test/screenshots; re-record with
 * `./gradlew :feature-library:testDebugUnitTest -Proborazzi.record`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class LibraryScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    // Midday UTC, so the rendered date is the same in every time zone from UTC-11 to UTC+11.
    private val day = 86_400_000L
    private val noon = day / 2
    private val documents =
        listOf(
            Document(
                1,
                "Blue Bakery",
                20_000 * day + noon,
                1,
                ReceiptFields("Blue Bakery", LocalDate.of(2026, 4, 19), 3518, "EUR", 1f),
            ),
            Document(2, "Scan 2026-10-15", 20_100 * day + noon, 3),
            Document(3, "Fresh Market", 20_200 * day + noon, 2),
        )

    private val noActions =
        LibraryScreenActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun libraryWithDocuments() {
        rule.setContent {
            MaterialTheme { LibraryScreen(LibraryUiState(documents = documents), noActions) }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/library_documents.png")
    }

    @Test
    fun libraryEmptyAndNoMatches() {
        rule.setContent {
            MaterialTheme { LibraryScreen(LibraryUiState(query = "zzz"), noActions) }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/library_no_matches.png")
    }

    @Test
    fun receiptDetail() {
        val page = Page(1, 2, 0, "/none.jpg", 600, 800, "Pasta 10.84\nTOTAL EUR 35.18")
        val content = DetailUiState.Content(DocumentWithPages(documents[0], listOf(page)))
        val actions = DetailActions({}, {}, { _: FieldsInput -> }, {}, {}, {}, {}, {}, {}, {}, { _, _ -> })
        rule.setContent { MaterialTheme { DetailScreen(content, FieldsFormState(), actions) } }
        rule.onRoot().captureRoboImage("src/test/screenshots/detail_receipt.png")
    }

    private fun host(widthDp: Float) {
        val repository = FakeDocumentRepository()
        val store = FakePageImageStore()
        runBlocking {
            documents.forEach {
                repository.insert(it.title, it.createdAt, it.extractedFields, listOf(NewPage("/none.jpg", 10, 10)))
            }
        }
        val info = WindowAdaptiveInfo(WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp, 640f), Posture())
        val library =
            LibraryViewModel(
                ObserveDocuments(repository),
                fakeImportDocument(repository, store) {
                    0L
                },
                DeleteDocument(repository, store),
            )
        rule.setContent {
            MaterialTheme {
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
                    adaptiveInfo = info,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w1000dp-h640dp-xhdpi")
    fun expandedShowsListNextToTheSelectedDocument() {
        host(1000f)
        rule.onRoot().captureRoboImage("src/test/screenshots/adaptive_expanded_empty.png")
        rule.onNodeWithText("Blue Bakery").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/adaptive_expanded_selected.png")
    }

    @Test
    fun compactShowsOnePaneAtATime() {
        host(360f)
        rule.onRoot().captureRoboImage("src/test/screenshots/adaptive_compact_list.png")
        rule.onNodeWithText("Blue Bakery").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/adaptive_compact_detail.png")
    }
}

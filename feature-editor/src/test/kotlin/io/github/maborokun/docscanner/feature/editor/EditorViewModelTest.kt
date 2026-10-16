package io.github.maborokun.docscanner.feature.editor

import io.github.maborokun.docscanner.domain.FakeDocumentRepository
import io.github.maborokun.docscanner.domain.FakePageImageStore
import io.github.maborokun.docscanner.domain.FakePageRenderer
import io.github.maborokun.docscanner.domain.FakeTextRecognizer
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.PageTint
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import io.github.maborokun.docscanner.domain.usecase.EditPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val repository = FakeDocumentRepository()
    private val store = FakePageImageStore()
    private val renderer = FakePageRenderer()
    private val quad = Quad(Point(0.1f, 0.1f), Point(0.9f, 0.1f), Point(0.9f, 0.9f), Point(0.1f, 0.9f))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun editor(original: String? = "/p/original.jpg"): EditorViewModel {
        val document =
            repository.insert("Doc", 1L, null, listOf(NewPage("/p/flat.jpg", 600, 800, "text", quad, original)))
        val page =
            repository
                .observe(document)
                .first()!!
                .pages
                .single()
        return EditorViewModel(
            document,
            page.id,
            repository,
            EditPage(renderer, FakeTextRecognizer("edited"), repository, store),
            renderer,
            store,
        )
    }

    @Test
    fun opensWithTheStoredCropAndRendersAPreviewBase() =
        runTest {
            val viewModel = editor()
            val state = viewModel.uiState.value
            assertFalse(state.loading)
            assertEquals("/p/original.jpg", state.originalPath)
            assertEquals(quad, state.quad)
            assertEquals(BaseImage("/private/alloc-1.jpg", 600, 800), state.base)
            assertFalse(state.canUndo)
        }

    @Test
    fun aPageWithoutAKeptOriginalStartsWithTheFullImageAsCrop() =
        runTest {
            val state = editor(original = null).uiState.value
            assertEquals(Quad.FULL, state.quad)
            assertEquals("/p/flat.jpg", state.originalPath)
        }

    @Test
    fun oneSliderDragIsOneUndoStep() =
        runTest {
            val viewModel = editor()
            viewModel.setBrightness(0.1f)
            viewModel.setBrightness(0.2f)
            viewModel.setBrightness(0.3f)
            viewModel.finishBrightness()
            assertEquals(0.3f, viewModel.uiState.value.edit.brightness)
            assertTrue(viewModel.hasChanges())

            viewModel.undo()
            assertEquals(0f, viewModel.uiState.value.edit.brightness)
            assertFalse(viewModel.uiState.value.canUndo)
        }

    @Test
    fun changingTheEnhancementRendersANewBaseAndUndoRestoresIt() =
        runTest {
            val viewModel = editor()
            val before = renderer.renders.size
            viewModel.setMode(EnhanceMode.BlackWhite)
            assertEquals(before + 1, renderer.renders.size)
            assertEquals(
                EnhanceMode.BlackWhite,
                renderer.renders
                    .last()
                    .third.mode,
            )

            viewModel.setTint(PageTint.Sepia) // colour is applied live, no new render
            assertEquals(before + 1, renderer.renders.size)

            viewModel.undo()
            viewModel.undo()
            assertEquals(EnhanceMode.None, viewModel.uiState.value.edit.mode)
            assertEquals(PageTint.None, viewModel.uiState.value.edit.tint)
        }

    @Test
    fun aStrokeIsRecordedAndUndoRemovesIt() =
        runTest {
            val viewModel = editor()
            viewModel.startStroke(Point(0.1f, 0.1f))
            viewModel.extendStroke(Point(0.2f, 0.2f))
            viewModel.extendStroke(Point(0.3f, 0.3f))
            viewModel.finishStroke()

            val stroke =
                viewModel.uiState.value.edit.strokes
                    .single()
            assertEquals(3, stroke.points.size)
            assertEquals(PenSize.Medium.width, stroke.width)

            viewModel.undo()
            assertTrue(
                viewModel.uiState.value.edit.strokes
                    .isEmpty(),
            )
        }

    @Test
    fun aNewCropClearsTheDrawingAndUndoBringsBothBack() =
        runTest {
            val viewModel = editor()
            viewModel.startStroke(Point(0.5f, 0.5f))
            viewModel.finishStroke()
            val moved = quad.copy(topLeft = Point(0.2f, 0.2f))

            viewModel.setQuad(moved)
            viewModel.finishQuad()
            assertEquals(moved, viewModel.uiState.value.quad)
            assertTrue(
                viewModel.uiState.value.edit.strokes
                    .isEmpty(),
            )

            viewModel.undo()
            assertEquals(quad, viewModel.uiState.value.quad)
            assertEquals(1, viewModel.uiState.value.edit.strokes.size)
        }

    @Test
    fun saveRendersFromTheOriginalAndReportsDone() =
        runTest {
            val viewModel = editor()
            viewModel.setTint(PageTint.Warm)
            var done = false

            viewModel.save { done = true }

            assertTrue(done)
            val (original, _, edit) = renderer.renders.last()
            assertEquals("/p/original.jpg", original)
            assertEquals(PageTint.Warm, edit.tint)
        }

    @Test
    fun aMissingPageIsReported() =
        runTest {
            val viewModel =
                EditorViewModel(
                    1L,
                    404L,
                    repository,
                    EditPage(renderer, FakeTextRecognizer(), repository, store),
                    renderer,
                    store,
                )
            assertTrue(viewModel.uiState.value.notFound)
        }
}

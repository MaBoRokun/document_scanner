package io.github.maborokun.docscanner.feature.scan

import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.FakeDocumentRepository
import io.github.maborokun.docscanner.domain.FakeEdgeDetector
import io.github.maborokun.docscanner.domain.FakePageImageStore
import io.github.maborokun.docscanner.domain.FakePageProcessor
import io.github.maborokun.docscanner.domain.FakeSettingsRepository
import io.github.maborokun.docscanner.domain.FakeTextRecognizer
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import io.github.maborokun.docscanner.domain.usecase.ScanDocument
import kotlinx.coroutines.CoroutineDispatcher
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
import java.io.File
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ScanLogicTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeDocumentRepository()
    private val store = FakePageImageStore()
    private val processor = FakePageProcessor()
    private val recognizer = FakeTextRecognizer("Blue Bakery\n2026-04-19\nTotal EUR 35.18")
    private lateinit var viewModel: ScanViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val dispatchers =
            object : AppDispatchers {
                override val main: CoroutineDispatcher = dispatcher
                override val default: CoroutineDispatcher = dispatcher
                override val io: CoroutineDispatcher = dispatcher
            }
        val scan = ScanDocument(processor, recognizer, repository, repository, store, ZoneOffset.UTC) { 0L }
        viewModel =
            ScanViewModel(store, scan, dispatchers, FakeEdgeDetector(), FakeSettingsRepository(), { true })
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun grantedPermissionShowsCamera() {
        assertEquals(CameraAccess.Granted, cameraAccess(true, requestedBefore = true, shouldShowRationale = false))
    }

    @Test
    fun neverAskedExplainsFirst() {
        assertEquals(
            CameraAccess.NeedsExplanation,
            cameraAccess(false, requestedBefore = false, shouldShowRationale = false),
        )
    }

    @Test
    fun deniedOnceExplainsAgain() {
        assertEquals(
            CameraAccess.NeedsExplanation,
            cameraAccess(false, requestedBefore = true, shouldShowRationale = true),
        )
    }

    @Test
    fun deniedWithoutRationaleIsPermanent() {
        assertEquals(
            CameraAccess.PermanentlyDenied,
            cameraAccess(false, requestedBefore = true, shouldShowRationale = false),
        )
    }

    private fun capture(): File = File.createTempFile("capture", ".jpg")

    @Test
    fun captureIsStagedForReviewAndTheTemporaryFileRemoved() =
        runTest(dispatcher) {
            val file = capture()
            viewModel.onCaptured(file)

            val review = viewModel.uiState.value as ScanUiState.Reviewing
            assertTrue(review.image.path.startsWith("/private/"))
            assertEquals(EnhanceMode.Contrast, review.mode)
            assertEquals(0.05f, review.quad.topLeft.x, 1e-5f) // nothing detected: default outline
            assertFalse(file.exists())
        }

    @Test
    fun reviewQuadAndModeCanBeEdited() =
        runTest(dispatcher) {
            viewModel.onCaptured(capture())
            val quad = Quad(Point(0.2f, 0.2f), Point(0.8f, 0.2f), Point(0.8f, 0.8f), Point(0.2f, 0.8f))
            viewModel.onReviewQuadChanged(quad)
            viewModel.onModeSelected(EnhanceMode.BlackWhite)

            val review = viewModel.uiState.value as ScanUiState.Reviewing
            assertEquals(quad, review.quad)
            assertEquals(EnhanceMode.BlackWhite, review.mode)
        }

    @Test
    fun editsOutsideReviewAreIgnored() {
        viewModel.onModeSelected(EnhanceMode.None)
        assertEquals(ScanUiState.Ready, viewModel.uiState.value)
    }

    @Test
    fun saveRunsThePipelineWithTheReviewedQuadAndMode() =
        runTest(dispatcher) {
            viewModel.onCaptured(capture())
            val quad = Quad(Point(0.2f, 0.2f), Point(0.8f, 0.2f), Point(0.8f, 0.8f), Point(0.2f, 0.8f))
            viewModel.onReviewQuadChanged(quad)
            viewModel.onModeSelected(EnhanceMode.BlackWhite)
            viewModel.onSave()

            val saved = viewModel.uiState.value as ScanUiState.Saved
            assertEquals(quad, processor.warps.single().second)
            assertEquals(EnhanceMode.BlackWhite, processor.enhances.single().second)
            val doc = requireNotNull(repository.observe(saved.documentId).first())
            assertEquals("Blue Bakery", doc.document.title)
            assertEquals(3518L, doc.document.extractedFields?.totalMinor)

            viewModel.onResultHandled()
            assertEquals(ScanUiState.Ready, viewModel.uiState.value)
        }

    @Test
    fun saveWithoutAReviewDoesNothing() {
        viewModel.onSave()
        assertEquals(ScanUiState.Ready, viewModel.uiState.value)
    }

    @Test
    fun retakeDeletesThePhotoAndReturnsToTheCamera() =
        runTest(dispatcher) {
            viewModel.onCaptured(capture())
            val path = (viewModel.uiState.value as ScanUiState.Reviewing).image.path
            viewModel.onRetake()

            assertEquals(ScanUiState.Ready, viewModel.uiState.value)
            assertEquals(listOf(path), store.deleted)
        }

    @Test
    fun unreadablePhotoReportsAnErrorAndStillRemovesTheTemporaryFile() =
        runTest(dispatcher) {
            store.failImport = true
            val file = capture()
            viewModel.onCaptured(file)

            assertTrue((viewModel.uiState.value as ScanUiState.Failed).message == R.string.error_read_photo)
            assertFalse(file.exists())
        }

    @Test
    fun failedPipelineReportsAnError() =
        runTest(dispatcher) {
            viewModel.onCaptured(capture())
            repository.failNextInsert = true
            viewModel.onSave()
            assertTrue((viewModel.uiState.value as ScanUiState.Failed).message == R.string.error_save_scan)
        }

    @Test
    fun captureFailureIsSurfaced() {
        viewModel.onCaptureFailed(R.string.error_capture, "boom")
        assertEquals(ScanUiState.Failed(R.string.error_capture, "boom"), viewModel.uiState.value)
    }
}

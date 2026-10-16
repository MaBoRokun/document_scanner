package io.github.maborokun.docscanner.feature.scan

import app.cash.turbine.test
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.FakeDocumentRepository
import io.github.maborokun.docscanner.domain.FakeEdgeDetector
import io.github.maborokun.docscanner.domain.FakePageImageStore
import io.github.maborokun.docscanner.domain.FakePageProcessor
import io.github.maborokun.docscanner.domain.FakeSettingsRepository
import io.github.maborokun.docscanner.domain.FakeTextRecognizer
import io.github.maborokun.docscanner.domain.model.DetectionResult
import io.github.maborokun.docscanner.domain.model.EdgeMeasure
import io.github.maborokun.docscanner.domain.model.FrameSize
import io.github.maborokun.docscanner.domain.model.PaperFormats
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.ScanHint
import io.github.maborokun.docscanner.domain.model.measureQuad
import io.github.maborokun.docscanner.domain.usecase.ScanDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayStateTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ScanViewModel
    private val frame = FrameSize(480, 640)
    private val quad = Quad(Point(0.1f, 0.1f), Point(0.9f, 0.1f), Point(0.9f, 0.9f), Point(0.1f, 0.9f))

    private fun detection(
        quad: Quad? = this.quad,
        confidence: Float = 0.9f,
    ) = DetectionResult(quad, confidence, 0f, 1f, frame)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val dispatchers =
            object : AppDispatchers {
                override val main: CoroutineDispatcher = dispatcher
                override val default: CoroutineDispatcher = dispatcher
                override val io: CoroutineDispatcher = dispatcher
            }
        val repository = FakeDocumentRepository()
        val store = FakePageImageStore()
        val scan =
            ScanDocument(
                FakePageProcessor(),
                FakeTextRecognizer(),
                repository,
                repository,
                store,
                ZoneOffset.UTC,
            ) { 0L }
        viewModel =
            ScanViewModel(store, scan, dispatchers, FakeEdgeDetector(), FakeSettingsRepository(), { true })
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsEmpty() =
        runTest(dispatcher) {
            viewModel.overlay.test { assertNull(awaitItem().quad) }
        }

    @Test
    fun detectionsReachTheOverlayWithMeasurements() =
        runTest(dispatcher) {
            val source = MutableSharedFlow<DetectionResult>()
            viewModel.overlay.test {
                awaitItem()
                val job = launch { viewModel.trackDetections(source) }
                source.emit(detection())
                val state = awaitItem()
                assertEquals(quad, state.quad)
                assertEquals(0.9f, state.confidence, 1e-5f)
                assertNotNull(state.measurement)
                assertFalse(state.manual)
                job.cancel()
            }
        }

    @Test
    fun draggingTakesOverUntilReset() =
        runTest(dispatcher) {
            val source = MutableSharedFlow<DetectionResult>()
            viewModel.overlay.test {
                awaitItem()
                val job = launch { viewModel.trackDetections(source) }
                source.emit(detection())
                awaitItem()

                val edited = quad.withCorner(0, Point(0.2f, 0.2f))
                viewModel.onQuadEdited(edited)
                val manual = awaitItem()
                assertEquals(edited, manual.quad)
                assertTrue(manual.manual)
                assertEquals(1f, manual.confidence, 0f)

                source.emit(detection(confidence = 0.3f))
                expectNoEvents() // tracking is ignored while the user's quad is active

                viewModel.resetQuad()
                val back = awaitItem()
                assertFalse(back.manual)
                assertEquals(quad, back.quad)
                job.cancel()
            }
        }

    @Test
    fun choosingPaperAddsMillimetres() =
        runTest(dispatcher) {
            val source = MutableSharedFlow<DetectionResult>()
            viewModel.overlay.test {
                awaitItem()
                val job = launch { viewModel.trackDetections(source) }
                source.emit(detection())
                assertNull(awaitItem().measurement?.top?.mm)

                viewModel.selectPaper(PaperFormats.A4)
                val withPaper = awaitItem()
                assertEquals(PaperFormats.A4, withPaper.paper)
                assertNotNull(withPaper.measurement?.top?.mm)
                job.cancel()
            }
        }

    @Test
    fun overlayWithoutAQuadKeepsTheFrameSize() =
        runTest(dispatcher) {
            val source = MutableSharedFlow<DetectionResult>()
            viewModel.overlay.test {
                awaitItem()
                val job = launch { viewModel.trackDetections(source) }
                source.emit(detection(quad = null, confidence = 0f))
                val state = awaitItem()
                assertNull(state.quad)
                assertEquals(frame, state.frameSize)
                job.cancel()
            }
        }

    @Test
    fun labelsUseMillimetresWhenKnownAndRelativeUnitsOtherwise() {
        Locale.setDefault(Locale.US)
        assertEquals("≈ 210 mm (est.)", edgeLabel(EdgeMeasure(0.5f, 209.6f), "≈ %1\$d mm (est.)"))
        assertEquals("0.62 u", edgeLabel(EdgeMeasure(0.618f, null), "≈ %1\$d mm (est.)"))
    }

    @Test
    fun everyHintHasText() {
        ScanHint.entries.forEach { assertTrue(hintText(it) != 0) }
    }

    @Test
    fun statusLineFollowsWhatTheDetectorSees() {
        assertEquals(StatusTone.Searching, statusLine(OverlayState()).tone)

        val found =
            OverlayState(quad = quad, frameSize = frame, confidence = 0.9f, measurement = measureQuad(quad, frame))
        assertEquals(StatusLine(R.string.status_found, StatusTone.Good), statusLine(found))
        assertEquals(R.string.status_found_paper, statusLine(found.copy(paper = PaperFormats.A4)).text)
        assertEquals(R.string.status_manual, statusLine(found.copy(manual = true)).text)

        val tilted = found.copy(measurement = measureQuad(quad, frame, skewDegrees = 20f))
        assertEquals(StatusLine(hintText(ScanHint.Tilted), StatusTone.Warning), statusLine(tilted))
    }
}

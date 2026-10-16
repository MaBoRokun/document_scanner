package io.github.maborokun.docscanner.feature.scan

import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.FakeDocumentRepository
import io.github.maborokun.docscanner.domain.FakeEdgeDetector
import io.github.maborokun.docscanner.domain.FakePageImageStore
import io.github.maborokun.docscanner.domain.FakePageProcessor
import io.github.maborokun.docscanner.domain.FakeSettingsRepository
import io.github.maborokun.docscanner.domain.FakeTextRecognizer
import io.github.maborokun.docscanner.domain.model.PaperFormats
import io.github.maborokun.docscanner.domain.settings.AppSettings
import io.github.maborokun.docscanner.domain.settings.ScanEngine
import io.github.maborokun.docscanner.domain.usecase.ScanDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class QuickScanAndSettingsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeDocumentRepository()
    private val store = FakePageImageStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        settings: AppSettings = AppSettings(),
        quickScanAvailable: Boolean = true,
        appendTo: Long? = null,
    ): ScanViewModel {
        val dispatchers =
            object : AppDispatchers {
                override val main: CoroutineDispatcher = dispatcher
                override val default: CoroutineDispatcher = dispatcher
                override val io: CoroutineDispatcher = dispatcher
            }
        val scan =
            ScanDocument(
                FakePageProcessor(),
                FakeTextRecognizer("Shop\n2026-01-02\nTotal 5.00"),
                repository,
                repository,
                store,
                ZoneOffset.UTC,
            ) {
                0L
            }
        return ScanViewModel(
            store,
            scan,
            dispatchers,
            FakeEdgeDetector(),
            FakeSettingsRepository(settings),
            { quickScanAvailable },
            appendTo,
        )
    }

    @Test
    fun customEngineIsTheDefaultMode() =
        runTest(dispatcher) {
            val vm = viewModel()
            val job = launch { vm.mode.collect {} }
            assertEquals(ScanMode.Camera, vm.mode.value)
            job.cancel()
        }

    @Test
    fun quickScanModeNeedsBothTheSettingAndPlayServices() =
        runTest(dispatcher) {
            val on = AppSettings(engine = ScanEngine.QuickScan)
            val available = viewModel(on, quickScanAvailable = true)
            val unavailable = viewModel(on, quickScanAvailable = false)
            val jobs = listOf(launch { available.mode.collect {} }, launch { unavailable.mode.collect {} })

            assertEquals(ScanMode.QuickScan, available.mode.value)
            assertEquals(ScanMode.Camera, unavailable.mode.value) // graceful fallback to the built-in scanner
            jobs.forEach { it.cancel() }
        }

    @Test
    fun defaultPaperFromSettingsIsPreselected() =
        runTest(dispatcher) {
            val vm = viewModel(AppSettings(defaultPaperName = "Letter"))
            val job = launch { vm.overlay.collect {} }
            assertEquals(PaperFormats.LETTER, vm.overlay.value.paper)
            job.cancel()
        }

    @Test
    fun quickScanPagesBecomeOneDocument() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onQuickScanPages(listOf("content://scan/1.jpg", "content://scan/2.jpg", "content://scan/3.jpg"))

            val saved = vm.uiState.value as ScanUiState.Saved
            val doc = requireNotNull(repository.observe(saved.documentId).first())
            assertEquals(3, doc.pages.size)
            assertEquals("Shop", doc.document.title)
        }

    @Test
    fun quickScanCanAppendToAnExistingDocument() =
        runTest(dispatcher) {
            val first = viewModel()
            first.onQuickScanPages(listOf("content://scan/1.jpg"))
            val id = (first.uiState.value as ScanUiState.Saved).documentId

            viewModel(appendTo = id).onQuickScanPages(listOf("content://scan/2.jpg"))
            assertEquals(2, requireNotNull(repository.observe(id).first()).pages.size)
        }

    @Test
    fun emptyOrBrokenQuickScanResultsFail() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onQuickScanPages(emptyList())
            assertTrue((vm.uiState.value as ScanUiState.Failed).message == R.string.error_save_scan)

            store.failImport = true
            vm.onQuickScanPages(listOf("content://scan/1.jpg"))
            assertTrue(vm.uiState.value is ScanUiState.Failed)
        }
}

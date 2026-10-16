package io.github.maborokun.docscanner.feature.export

import io.github.maborokun.docscanner.domain.FakeDocumentRepository
import io.github.maborokun.docscanner.domain.FakeImageExporter
import io.github.maborokun.docscanner.domain.FakePdfExporter
import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.processing.ExportProgress
import io.github.maborokun.docscanner.domain.processing.PdfExporter
import io.github.maborokun.docscanner.domain.usecase.PrepareExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
class ExportViewModelTest {
    private val repository = FakeDocumentRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun documentWithPages(
        count: Int,
        title: String = "Trip",
    ): Long = repository.insert(title, 1L, null, List(count) { NewPage("/p$it", 10, 10) })

    private fun viewModel(
        id: Long,
        exporter: PdfExporter,
        pageId: Long? = null,
        asImage: Boolean = false,
        images: FakeImageExporter = FakeImageExporter(),
    ) = ExportViewModel(ExportRequest(listOf(id), pageId, asImage), PrepareExport(repository), exporter, images)

    @Test
    fun exportReportsProgressThenTheFinishedFile() =
        runTest {
            val progress = MutableSharedFlow<ExportProgress>()
            val exporter =
                object : PdfExporter {
                    override fun export(document: DocumentWithPages): Flow<ExportProgress> = progress
                }
            val viewModel = viewModel(documentWithPages(2), exporter)
            assertEquals(ExportUiState.Loading, viewModel.uiState.value)

            viewModel.start()
            assertEquals(ExportUiState.Exporting(0, 2), viewModel.uiState.value)
            progress.emit(ExportProgress.Pages(1, 2))
            assertEquals(ExportUiState.Exporting(1, 2), viewModel.uiState.value)
            progress.emit(ExportProgress.Pages(2, 2))
            assertEquals(ExportUiState.Exporting(2, 2), viewModel.uiState.value)
            progress.emit(ExportProgress.Finished("/cache/Trip.pdf", "Trip.pdf"))
            assertEquals(ExportUiState.Ready("/cache/Trip.pdf", "Trip.pdf"), viewModel.uiState.value)
        }

    @Test
    fun startingAgainAfterFinishingDoesNotExportTwice() =
        runTest {
            val exporter = FakePdfExporter()
            val viewModel = viewModel(documentWithPages(1), exporter)
            viewModel.start()
            viewModel.start()
            assertEquals(1, exporter.exported)
        }

    @Test
    fun cancellingStopsTheExporter() =
        runTest {
            val exporter = FakePdfExporter(hang = true)
            val viewModel = viewModel(documentWithPages(3), exporter)
            viewModel.start()
            assertEquals(ExportUiState.Exporting(1, 3), viewModel.uiState.value)
            assertFalse(exporter.cancelled)

            viewModel.cancel()
            assertTrue(exporter.cancelled)
        }

    @Test
    fun missingDocumentIsReported() =
        runTest {
            val viewModel = viewModel(404L, FakePdfExporter())
            viewModel.start()
            assertEquals(ExportUiState.NotFound, viewModel.uiState.value)
        }

    @Test
    fun failureCanBeRetried() =
        runTest {
            var attempts = 0
            val flaky =
                object : PdfExporter {
                    override fun export(document: DocumentWithPages): Flow<ExportProgress> =
                        flow {
                            if (attempts++ == 0) error("disk full")
                            emit(ExportProgress.Finished("/cache/x.pdf", "x.pdf"))
                        }
                }
            val viewModel = viewModel(documentWithPages(1), flaky)
            viewModel.start()
            assertEquals(ExportUiState.Failed(R.string.error_export, "disk full"), viewModel.uiState.value)

            viewModel.retry()
            assertEquals(ExportUiState.Ready("/cache/x.pdf", "x.pdf"), viewModel.uiState.value)
        }

    @Test
    fun retryIsIgnoredUnlessTheExportFailed() =
        runTest {
            val viewModel = viewModel(documentWithPages(1), FakePdfExporter())
            viewModel.retry()
            assertEquals(ExportUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun severalDocumentsBecomeOnePdf() =
        runTest {
            val seen = mutableListOf<DocumentWithPages>()
            val exporter =
                object : PdfExporter {
                    override fun export(document: DocumentWithPages): Flow<ExportProgress> {
                        seen += document
                        return flow { emit(ExportProgress.Finished("/cache/all.pdf", "all.pdf")) }
                    }
                }
            val first = documentWithPages(2, "One")
            val second = documentWithPages(1, "Two")
            val viewModel =
                ExportViewModel(
                    ExportRequest(listOf(first, second)),
                    PrepareExport(repository),
                    exporter,
                    FakeImageExporter(),
                )
            viewModel.start()
            assertEquals(3, seen.single().pages.size)
            assertEquals("Scans (2 documents)", seen.single().document.title)
        }

    @Test
    fun onePageIsSharedAsAnImage() =
        runTest {
            val id = documentWithPages(2)
            val pageId =
                repository
                    .observe(id)
                    .first()!!
                    .pages[1]
                    .id
            val images = FakeImageExporter()
            val viewModel = viewModel(id, FakePdfExporter(), pageId, asImage = true, images = images)
            viewModel.start()
            assertEquals(
                ExportUiState.Ready("/cache/Trip page 2.jpg", "Trip page 2.jpg", IMAGE_MIME),
                viewModel.uiState.value,
            )
            assertEquals(1, images.exported.size)
        }

    @Test
    fun onePageIsSharedAsAPdf() =
        runTest {
            val id = documentWithPages(3)
            val pageId =
                repository
                    .observe(id)
                    .first()!!
                    .pages[2]
                    .id
            val exporter = FakePdfExporter()
            val viewModel = viewModel(id, exporter, pageId)
            viewModel.start()
            assertEquals(ExportUiState.Ready("/cache/Trip page 3.pdf", "Trip page 3.pdf"), viewModel.uiState.value)
        }

    @Test
    fun theShareSheetOpensByItselfOnlyOnce() =
        runTest {
            val viewModel = viewModel(documentWithPages(1), FakePdfExporter())
            assertFalse(viewModel.takeAutoShare())
            viewModel.start()
            assertTrue(viewModel.takeAutoShare())
            assertFalse(viewModel.takeAutoShare())
        }
}

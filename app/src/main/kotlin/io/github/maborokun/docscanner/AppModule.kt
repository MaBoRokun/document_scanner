package io.github.maborokun.docscanner

import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.core.common.DefaultAppDispatchers
import io.github.maborokun.docscanner.data.AndroidImageExporter
import io.github.maborokun.docscanner.data.AndroidPdfExporter
import io.github.maborokun.docscanner.data.AppPrivateImageStore
import io.github.maborokun.docscanner.data.PreferencesSettingsRepository
import io.github.maborokun.docscanner.data.RoomDocumentRepository
import io.github.maborokun.docscanner.domain.classify.DocumentClassifier
import io.github.maborokun.docscanner.domain.processing.ImageExporter
import io.github.maborokun.docscanner.domain.processing.PageProcessor
import io.github.maborokun.docscanner.domain.processing.PageRenderer
import io.github.maborokun.docscanner.domain.processing.PdfExporter
import io.github.maborokun.docscanner.domain.processing.TextRecognizer
import io.github.maborokun.docscanner.domain.repository.DocumentReader
import io.github.maborokun.docscanner.domain.repository.DocumentRepository
import io.github.maborokun.docscanner.domain.repository.DocumentWriter
import io.github.maborokun.docscanner.domain.repository.PageImageStore
import io.github.maborokun.docscanner.domain.repository.PageWriter
import io.github.maborokun.docscanner.domain.settings.QuickScanAvailability
import io.github.maborokun.docscanner.domain.settings.SettingsRepository
import io.github.maborokun.docscanner.domain.usecase.DeleteDocument
import io.github.maborokun.docscanner.domain.usecase.EditPage
import io.github.maborokun.docscanner.domain.usecase.ImportDocument
import io.github.maborokun.docscanner.domain.usecase.ObserveDocuments
import io.github.maborokun.docscanner.domain.usecase.PrepareExport
import io.github.maborokun.docscanner.domain.usecase.RemovePage
import io.github.maborokun.docscanner.domain.usecase.ScanDocument
import io.github.maborokun.docscanner.domain.vision.EdgeDetector
import io.github.maborokun.docscanner.feature.editor.EditorViewModel
import io.github.maborokun.docscanner.feature.export.ExportRequest
import io.github.maborokun.docscanner.feature.export.ExportViewModel
import io.github.maborokun.docscanner.feature.library.DetailViewModel
import io.github.maborokun.docscanner.feature.library.LibraryViewModel
import io.github.maborokun.docscanner.feature.scan.ScanViewModel
import io.github.maborokun.docscanner.feature.settings.SettingsViewModel
import io.github.maborokun.docscanner.ocr.MlKitTextRecognizer
import io.github.maborokun.docscanner.vision.BitmapOps
import io.github.maborokun.docscanner.vision.NativeBitmapOps
import io.github.maborokun.docscanner.vision.NativeEdgeDetector
import io.github.maborokun.docscanner.vision.NativePageProcessor
import io.github.maborokun.docscanner.vision.NativePageRenderer
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.binds
import org.koin.dsl.module

/** The only place where implementations meet interfaces. */
val appModule =
    module {
        single<AppDispatchers> { DefaultAppDispatchers }
        single<DocumentRepository> { RoomDocumentRepository.create(androidContext()) } binds
            arrayOf(DocumentReader::class, DocumentWriter::class, PageWriter::class)
        single<EdgeDetector> { NativeEdgeDetector(get()) }
        single<BitmapOps> { NativeBitmapOps() }
        single<PageProcessor> { NativePageProcessor(get(), get()) }
        single<PageRenderer> { NativePageRenderer(get(), get()) }
        single<TextRecognizer> { MlKitTextRecognizer(get()) }
        single<PdfExporter> { AndroidPdfExporter(androidContext(), get()) }
        single<ImageExporter> { AndroidImageExporter(androidContext(), get()) }
        single<SettingsRepository> { PreferencesSettingsRepository(androidContext(), get()) }
        single<QuickScanAvailability> {
            val context = androidContext()
            // Google's scanner is run by Google Play services; without them the built-in scanner is used.
            QuickScanAvailability {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
            }
        }
        single<PageImageStore> { AppPrivateImageStore(androidContext(), get()) }

        factory { ObserveDocuments(get()) }
        single { DocumentClassifier() }
        factory {
            ScanDocument(
                get(),
                get(),
                get(),
                get(),
                get(),
                classifier = get(),
                clock = System::currentTimeMillis,
            )
        }
        factory { ImportDocument(get(), get()) }
        factory { DeleteDocument(get(), get()) }
        factory { RemovePage(get(), get()) }
        factory { PrepareExport(get()) }
        factory { EditPage(get(), get(), get(), get()) }

        viewModel { LibraryViewModel(get(), get(), get()) }
        viewModel { (appendTo: Long) ->
            ScanViewModel(get(), get(), get(), get(), get(), get(), appendTo.takeIf { it > 0 })
        }
        viewModel {
            SettingsViewModel(
                get(),
                get(),
                appFunctionsSupported =
                    Build.VERSION.SDK_INT >= APP_FUNCTIONS_MIN_SDK,
            )
        }
        viewModel { (id: Long) -> DetailViewModel(id, get(), get(), get(), get()) }
        viewModel { (request: ExportRequest) -> ExportViewModel(request, get(), get(), get()) }
        viewModel { (documentId: Long, pageId: Long) ->
            EditorViewModel(documentId, pageId, get(), get(), get(), get())
        }
    }

/** Android 16: the first release in which assistants can call app functions. */
private const val APP_FUNCTIONS_MIN_SDK = 36

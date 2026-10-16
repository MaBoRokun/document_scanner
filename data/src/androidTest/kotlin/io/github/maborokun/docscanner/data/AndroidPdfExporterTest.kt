package io.github.maborokun.docscanner.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.maborokun.docscanner.core.common.DefaultAppDispatchers
import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.model.Page
import io.github.maborokun.docscanner.domain.processing.ExportProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidPdfExporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val exporter = AndroidPdfExporter(context, DefaultAppDispatchers)

    private fun pageFile(
        name: String,
        width: Int,
        height: Int,
        color: Int,
    ): Page {
        val file = File(context.cacheDir, name)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.also { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        return Page(1, 1, 0, file.path, width, height)
    }

    private fun document(vararg pages: Page) =
        DocumentWithPages(Document(1, "Test: export", 0L, pages.size), pages.toList())

    @Test
    fun exportsEveryPageAndReportsProgress() =
        runBlocking {
            val doc =
                document(
                    pageFile("p1.jpg", 600, 800, Color.RED),
                    pageFile("p2.jpg", 800, 600, Color.GREEN),
                    pageFile("p3.jpg", 300, 1200, Color.BLUE),
                )
            val events = exporter.export(doc).toList()

            assertEquals(
                listOf(ExportProgress.Pages(1, 3), ExportProgress.Pages(2, 3), ExportProgress.Pages(3, 3)),
                events.dropLast(1),
            )
            val done = events.last() as ExportProgress.Finished
            assertEquals("Test export.pdf", done.fileName)

            val file = File(done.path)
            assertTrue(file.length() > 0)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    assertEquals(3, renderer.pageCount)
                    renderer.openPage(0).use { assertTrue(it.width < it.height) }
                    renderer.openPage(1).use { assertTrue(it.width > it.height) }
                }
            }
        }

    @Test
    fun cancellingLeavesNoFileBehind() =
        runBlocking {
            val doc = document(pageFile("c1.jpg", 600, 800, Color.RED), pageFile("c2.jpg", 600, 800, Color.RED))
            exporter.export(doc).first() // takes the first progress event, then cancels the rest

            val leftovers = File(context.cacheDir, "exports").listFiles().orEmpty().filter { it.name.endsWith(".pdf") }
            assertTrue("partial export should be removed: $leftovers", leftovers.isEmpty())
        }

    @Test
    fun anUnreadablePageFailsTheExportAndCleansUp() =
        runBlocking {
            val missing = Page(1, 1, 0, File(context.cacheDir, "nope.jpg").path, 100, 100)
            val result = runCatching { exporter.export(document(missing)).toList() }
            assertTrue(result.isFailure)
            assertFalse(File(context.cacheDir, "exports/Test export.pdf").exists())
        }
}

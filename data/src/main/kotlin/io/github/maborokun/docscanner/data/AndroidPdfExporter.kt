package io.github.maborokun.docscanner.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.model.Page
import io.github.maborokun.docscanner.domain.processing.ExportProgress
import io.github.maborokun.docscanner.domain.processing.PdfExporter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PDF export with the platform [PdfDocument]: one PDF page per scanned page, sized to fit A4 (portrait or landscape,
 * whichever matches the scan). Files go to `cacheDir/exports`, which the share FileProvider exposes.
 *
 * shortcut: pages are images only, with no invisible text layer, so the PDF is not text-searchable;
 * add one (for example with a PDF library) if search inside exported files matters.
 */
class AndroidPdfExporter(
    context: Context,
    private val dispatchers: AppDispatchers,
) : PdfExporter {
    private val directory = File(context.applicationContext.cacheDir, "exports")

    override fun export(document: DocumentWithPages): Flow<ExportProgress> =
        flow {
            directory.mkdirs()
            directory.listFiles()?.forEach { it.delete() } // earlier exports have been shared or abandoned by now
            val fileName = pdfFileName(document.document.title)
            val target = File(directory, fileName)
            var finished = false
            try {
                val pdf = PdfDocument()
                try {
                    val total = document.pages.size
                    document.pages.forEachIndexed { index, page ->
                        currentCoroutineContext().ensureActive()
                        writePage(pdf, index + 1, page)
                        emit(ExportProgress.Pages(index + 1, total))
                    }
                    currentCoroutineContext().ensureActive()
                    target.outputStream().use { pdf.writeTo(it) }
                } finally {
                    pdf.close()
                }
                finished = true
                emit(ExportProgress.Finished(target.path, fileName))
            } finally {
                // Cancelled or failed: never leave a half-written file that could be shared by mistake.
                if (!finished) target.delete()
            }
        }.flowOn(dispatchers.io)

    private fun writePage(
        pdf: PdfDocument,
        number: Int,
        page: Page,
    ) {
        val bitmap =
            BitmapFactory.decodeFile(page.imagePath, BitmapFactory.Options().apply { inSampleSize = sampleSize(page) })
                ?: error("Cannot read page $number")
        try {
            val (width, height) = pageSize(bitmap.width, bitmap.height)
            val pdfPage = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, number).create())
            pdfPage.canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(pdfPage)
        } finally {
            bitmap.recycle()
        }
    }

    /** Keeps roughly 240 dpi on A4, plenty for text and far smaller than the full camera resolution. */
    private fun sampleSize(page: Page): Int {
        var sample = 1
        while (max(page.width, page.height) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        return sample
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 2000
    }
}

private const val A4_SHORT_POINTS = 595
private const val A4_LONG_POINTS = 842

/** The page size in PDF points: the image scaled to fit A4 in its own orientation. */
internal fun pageSize(
    imageWidth: Int,
    imageHeight: Int,
): Pair<Int, Int> {
    val landscape = imageWidth > imageHeight
    val boxWidth = if (landscape) A4_LONG_POINTS else A4_SHORT_POINTS
    val boxHeight = if (landscape) A4_SHORT_POINTS else A4_LONG_POINTS
    val scale = min(boxWidth.toFloat() / imageWidth, boxHeight.toFloat() / imageHeight)
    return max(1, (imageWidth * scale).roundToInt()) to max(1, (imageHeight * scale).roundToInt())
}

/** "Blue Bakery: 2026/10" -> "Blue Bakery 2026 10.pdf": safe on every file system and in share sheets. */
internal fun pdfFileName(title: String): String = exportFileName(title, "pdf")

internal fun exportFileName(
    title: String,
    extension: String,
): String {
    val cleaned =
        title
            .replace(
                Regex("[^\\p{L}\\p{N} ._-]"),
                " ",
            ).replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_NAME_LENGTH)
    return (cleaned.ifEmpty { "Scan" }) + ".$extension"
}

private const val MAX_NAME_LENGTH = 60

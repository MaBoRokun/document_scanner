package io.github.maborokun.docscanner.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.processing.TextRecognizer
import io.github.maborokun.docscanner.domain.receipt.OcrLine
import io.github.maborokun.docscanner.domain.receipt.joinIntoRows
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** On-device ML Kit text recognition (bundled Latin model); returns printed rows, see [joinIntoRows]. */
class MlKitTextRecognizer(
    private val dispatchers: AppDispatchers,
) : TextRecognizer {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(imagePath: String): String {
        val bitmap = withContext(dispatchers.processing) { decode(imagePath) }
        try {
            val result =
                suspendCancellableCoroutine { continuation ->
                    client
                        .process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { continuation.resume(it) }
                        .addOnFailureListener { continuation.resumeWithException(it) }
                }
            val lines =
                result.textBlocks.flatMap { block ->
                    block.lines.mapNotNull { line ->
                        line.boundingBox?.let { box ->
                            OcrLine(line.text, box.left.toFloat(), box.exactCenterY(), box.height().toFloat())
                        }
                    }
                }
            // Rows when positions are known, ML Kit's own reading order as the fallback.
            return if (lines.isEmpty()) result.text else joinIntoRows(lines)
        } finally {
            bitmap.recycle()
        }
    }

    /** Large enough to read small print, small enough to keep recognition fast. */
    private fun decode(path: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not a decodable image: $path" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return requireNotNull(BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })) {
            "Not a decodable image: $path"
        }
    }

    private companion object {
        const val MAX_SIDE = 2400
    }
}

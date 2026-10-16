package io.github.maborokun.docscanner.ocr

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.maborokun.docscanner.core.common.DefaultAppDispatchers
import io.github.maborokun.docscanner.domain.receipt.ReceiptParser
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Real ML Kit on the generated receipts (tools/generate_synthetic.py): OCR text -> parser -> ground truth. */
@RunWith(AndroidJUnit4::class)
class MlKitTextRecognizerTest {
    private val recognizer = MlKitTextRecognizer(DefaultAppDispatchers)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun assetToFile(name: String): File {
        val out = File(instrumentation.targetContext.cacheDir, name.substringAfterLast('/'))
        instrumentation.context.assets
            .open(name)
            .use { src -> out.outputStream().use { src.copyTo(it) } }
        return out
    }

    @Test
    fun receiptsAreReadAndParsedBackToTheirGroundTruth() =
        runBlocking {
            val truth =
                JSONArray(
                    instrumentation.context.assets
                        .open("receipts/expected.json")
                        .bufferedReader()
                        .readText(),
                )
            var merchantHits = 0
            var dateHits = 0
            var totalHits = 0
            for (i in 0 until truth.length()) {
                val expected = truth.getJSONObject(i)
                val text = recognizer.recognize(assetToFile("receipts/" + expected.getString("file")).path)
                val fields = ReceiptParser.parse(text)
                Log.i("OcrTest", "${expected.getString("file")}: ${text.replace('\n', '|')} -> $fields")
                if (fields.merchant.equals(expected.getString("merchant"), ignoreCase = true)) merchantHits++
                if (fields.date == LocalDate.parse(expected.getString("date"))) dateHits++
                if (fields.totalMinor == expected.getLong("totalMinor")) totalHits++
            }
            // Clean synthetic print: everything should be read; a small allowance covers one odd glyph.
            assertTrue("merchants $merchantHits/${truth.length()}", merchantHits >= truth.length() - 1)
            assertTrue("dates $dateHits/${truth.length()}", dateHits >= truth.length() - 1)
            assertTrue("totals $totalHits/${truth.length()}", totalHits >= truth.length() - 1)
        }

    @Test
    fun aPageWithMostlyBarsStillReturnsText() =
        runBlocking {
            val text = recognizer.recognize(assetToFile("pages/page_0.png").path)
            assertTrue("the page title should be read: '$text'", text.contains("PAGE", ignoreCase = true))
        }
}

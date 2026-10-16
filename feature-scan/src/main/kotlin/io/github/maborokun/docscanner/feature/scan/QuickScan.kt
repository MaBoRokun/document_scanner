package io.github.maborokun.docscanner.feature.scan

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

private const val MAX_QUICK_SCAN_PAGES = 10

/**
 * The "Quick scan" engine: opens Google's document scanner (run by Google Play services) once, then feeds the
 * returned pages through the normal pipeline. Cancelling the scanner closes this screen.
 */
@Composable
internal fun QuickScanContent(
    viewModel: ScanViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var launched by rememberSaveable { mutableStateOf(false) }
    val scanner =
        remember {
            GmsDocumentScanning.getClient(
                GmsDocumentScannerOptions
                    .Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(MAX_QUICK_SCAN_PAGES)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build(),
            )
        }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val pages =
                if (result.resultCode == Activity.RESULT_OK) {
                    GmsDocumentScanningResult
                        .fromActivityResultIntent(
                            result.data,
                        )?.pages
                        ?.map { it.imageUri.toString() }
                } else {
                    null
                }
            if (pages.isNullOrEmpty()) onClose() else viewModel.onQuickScanPages(pages)
        }
    LaunchedEffect(Unit) {
        if (!launched && activity != null) {
            launched = true
            scanner
                .getStartScanIntent(activity)
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener {
                    viewModel.onCaptureFailed(R.string.error_quick_scan, it.message)
                }
        }
    }
    Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
}

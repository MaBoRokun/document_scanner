package io.github.maborokun.docscanner.feature.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.maborokun.docscanner.core.designsystem.ScannerScaffold
import io.github.maborokun.docscanner.core.designsystem.ScannerSurface
import kotlinx.coroutines.launch

@Composable
fun ScanRoute(
    viewModel: ScanViewModel,
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var requestedBefore by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
            requestedBefore = true
        }
    // The user may grant the permission in system settings and come back.
    LifecycleResumeEffect(Unit) {
        granted = hasCameraPermission(context)
        onPauseOrDispose {}
    }
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    // Google's scanner brings its own camera screen and permission handling, so it skips ours.
    val access =
        if (mode == ScanMode.QuickScan) {
            CameraAccess.Granted
        } else {
            cameraAccess(granted, requestedBefore, context.findActivity()?.shouldExplainCamera() ?: false)
        }
    val title = stringResource(if (viewModel.appendTo == null) R.string.scan else R.string.add_page)

    // Leaving takes a moment (the screen animates out and the camera is released), during which the live preview
    // would stay visible. Swapping it for a plain surface first makes the close look instant.
    var closing by remember { mutableStateOf(false) }
    val close = {
        closing = true
        onBack()
    }
    BackHandler(enabled = !closing, onBack = close)
    if (closing) {
        ScannerSurface(modifier.fillMaxSize()) {}
        return
    }

    when (access) {
        CameraAccess.Granted -> ScanContent(viewModel, title, onSaved, close, modifier)
        CameraAccess.NeedsExplanation ->
            NoticeScreen(
                title = title,
                heading = stringResource(R.string.camera_access),
                message = stringResource(R.string.camera_rationale),
                action = stringResource(R.string.allow_camera),
                onAction = { launcher.launch(Manifest.permission.CAMERA) },
                onClose = close,
                modifier = modifier,
            )
        CameraAccess.PermanentlyDenied ->
            NoticeScreen(
                title = title,
                heading = stringResource(R.string.camera_off),
                message = stringResource(R.string.camera_off_message),
                action = stringResource(R.string.open_settings),
                onAction = { context.startActivity(appSettingsIntent(context)) },
                onClose = close,
                modifier = modifier,
            )
    }
}

@Composable
internal fun ScanContent(
    viewModel: ScanViewModel,
    title: String,
    onSaved: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The state stays Saved on purpose: resetting it to the camera here would bring the live preview back for the
    // second the screen takes to animate out. The screen is replaced and its view model discarded right after.
    LaunchedEffect(state) { (state as? ScanUiState.Saved)?.let { onSaved(it.documentId) } }
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    when (val current = state) {
        is ScanUiState.Reviewing -> ReviewScreen(current, viewModel, modifier)
        // Between the camera and the saved document there is nothing to look at: the pipeline is running.
        ScanUiState.Saving, is ScanUiState.Saved ->
            BusyScreen(title, stringResource(R.string.busy_saving), modifier)
        else ->
            when (mode) {
                ScanMode.Loading -> BusyScreen(title, stringResource(R.string.busy_ready), modifier)
                ScanMode.Camera -> CameraScreen(viewModel, title, onClose, modifier)
                ScanMode.QuickScan ->
                    if (current is ScanUiState.Failed) {
                        NoticeScreen(
                            title,
                            stringResource(R.string.quick_scan_unavailable),
                            current.text(),
                            stringResource(R.string.close),
                            onClose,
                            onClose,
                            modifier,
                        )
                    } else {
                        ScannerScaffold(title, onClose, modifier) { QuickScanContent(viewModel, onClose) }
                    }
            }
    }
}

internal fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

internal fun Activity.shouldExplainCamera() = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

internal fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

internal fun appSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

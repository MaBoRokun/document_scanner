package io.github.maborokun.docscanner.feature.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.maborokun.docscanner.core.designsystem.AppIcons
import io.github.maborokun.docscanner.core.designsystem.EmptyState
import io.github.maborokun.docscanner.core.designsystem.ScreenMargin
import java.io.File

@Composable
fun ExportRoute(
    viewModel: ExportViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.start() }
    // The point of this screen is the share sheet, so it opens by itself as soon as the file is ready.
    LaunchedEffect(state) {
        val ready = state as? ExportUiState.Ready
        if (ready != null && viewModel.takeAutoShare()) context.startActivity(shareChooser(context, ready))
    }
    ExportScreen(
        state = state,
        onCancel = {
            viewModel.cancel()
            onBack()
        },
        onShare = { ready -> context.startActivity(shareChooser(context, ready)) },
        onRetry = viewModel::retry,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    state: ExportUiState,
    onCancel: () -> Unit,
    onShare: (ExportUiState.Ready) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(ScreenMargin), contentAlignment = Alignment.Center) {
            when (state) {
                ExportUiState.Loading -> CircularProgressIndicator()
                ExportUiState.NotFound ->
                    EmptyState(
                        AppIcons.Document,
                        stringResource(R.string.document_not_found),
                        stringResource(R.string.document_not_found_hint),
                    )
                is ExportUiState.Exporting ->
                    StatusCard(
                        AppIcons.Document,
                        stringResource(R.string.preparing_file),
                        stringResource(R.string.page_progress, state.done, state.total),
                    ) {
                        LinearProgressIndicator(
                            progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    }
                is ExportUiState.Ready ->
                    StatusCard(Icons.Default.Check, stringResource(R.string.ready_to_share), state.fileName) {
                        Button(onClick = { onShare(state) }, Modifier.fillMaxWidth()) {
                            Icon(AppIcons.Export, contentDescription = null, Modifier.size(18.dp))
                            Text(stringResource(R.string.share), Modifier.padding(start = 8.dp))
                        }
                        TextButton(onClick = onBack) { Text(stringResource(R.string.done)) }
                    }
                is ExportUiState.Failed ->
                    StatusCard(
                        Icons.Default.Warning,
                        stringResource(R.string.export_failed_title),
                        stringResource(state.message, state.detail ?: stringResource(R.string.error_unknown)),
                        error = true,
                    ) {
                        Button(onClick = onRetry, Modifier.fillMaxWidth()) { Text(stringResource(R.string.try_again)) }
                        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                    }
            }
        }
    }
}

/** A centred card: an icon in a tonal circle, a title, one line of detail, then the actions. */
@Composable
private fun StatusCard(
    icon: ImageVector,
    title: String,
    detail: String,
    error: Boolean = false,
    actions: @Composable () -> Unit,
) {
    val container = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val onContainer =
        if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, Modifier.size(32.dp), tint = onContainer)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            actions()
        }
    }
}

/** A system share sheet for [path] (inside cache/exports), readable by the chosen app for this share only. */
internal fun shareChooser(
    context: Context,
    ready: ExportUiState.Ready,
): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(ready.path))
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = ready.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, ready.fileName)
            clipData = ClipData.newRawUri(ready.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    return Intent.createChooser(send, context.getString(R.string.share))
}

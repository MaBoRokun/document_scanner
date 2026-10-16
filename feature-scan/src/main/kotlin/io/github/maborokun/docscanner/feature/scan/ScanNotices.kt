package io.github.maborokun.docscanner.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.maborokun.docscanner.core.designsystem.AppIcons
import io.github.maborokun.docscanner.core.designsystem.EmptyState
import io.github.maborokun.docscanner.core.designsystem.ScannerScaffold

/** A calm full-screen message with one action, for permissions and errors. */
@Composable
internal fun NoticeScreen(
    title: String,
    heading: String,
    message: String,
    action: String,
    onAction: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScannerScaffold(title, onClose, modifier) {
        EmptyState(icon = AppIcons.Scan, title = heading, message = message) {
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun BusyScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    ScannerScaffold(title, onClose = {}, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

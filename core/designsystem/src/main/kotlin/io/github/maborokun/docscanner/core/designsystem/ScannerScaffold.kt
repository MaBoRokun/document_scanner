package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** The dark canvas for looking at and adjusting pages: dark colours for every child, in light and dark theme alike. */
@Composable
fun ScannerSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    LightSystemBarIcons()
    MaterialTheme(
        colorScheme = scannerColorScheme(),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        Surface(modifier, color = ScannerColors.Canvas, contentColor = ScannerColors.OnCanvas, content = content)
    }
}

/**
 * Camera and review layout: a top bar, the content in the middle and a bottom panel, stacked, never overlapping.
 * The bars respect the system bars, so nothing sits under the status bar, the notch or the gesture area.
 */
@Composable
fun ScannerScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeIcon: ImageVector = Icons.Default.Close,
    closeLabel: String = stringResource(R.string.close),
    trailing: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable ColumnScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    ScannerSurface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(closeIcon, contentDescription = closeLabel) }
                Text(
                    title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                trailing()
            }
            Box(Modifier.weight(1f).fillMaxWidth(), content = content)
            Column(Modifier.fillMaxWidth().navigationBarsPadding(), content = bottomBar)
        }
    }
}

/** The canvas is dark in both themes, so the status and navigation bar icons must be light while it is shown. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window =
            generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<android.app.Activity>()
                .firstOrNull()
                ?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLightStatus = controller?.isAppearanceLightStatusBars
        val wasLightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            wasLightStatus?.let { controller.isAppearanceLightStatusBars = it }
            wasLightNav?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}

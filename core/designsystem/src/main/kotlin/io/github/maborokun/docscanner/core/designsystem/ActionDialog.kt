package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** How a dialog action reads: [Destructive] deletes or applies a change (red), [Negative] backs out (blue). */
enum class DialogTone { Neutral, Negative, Destructive }

class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val tone: DialogTone = DialogTone.Neutral,
)

/**
 * The app's dialog: a sheet at the bottom with a centred title and message, then one full-width row per action,
 * separated by hairlines. Order the actions as they should read, usually the change first and the way out last.
 */
@Composable
fun ActionDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: List<DialogAction>,
    message: String? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            // The area above the sheet: a tap there closes the dialog.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onDismiss()
                    },
            )
            ActionSheet(title, actions, message, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
internal fun ActionSheet(
    title: String,
    actions: List<DialogAction>,
    message: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.widthIn(max = SHEET_MAX_WIDTH).fillMaxWidth(),
        shape = RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                title,
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (message != null) {
                Text(
                    message,
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Box(Modifier.padding(top = 16.dp))
            actions.forEach { action ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    action.label,
                    Modifier.fillMaxWidth().clickable(onClick = action.onClick).padding(vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = action.tone.color(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DialogTone.color(): Color =
    when (this) {
        DialogTone.Neutral -> MaterialTheme.colorScheme.onSurface
        DialogTone.Negative -> MaterialTheme.colorScheme.primary
        DialogTone.Destructive -> MaterialTheme.colorScheme.error
    }

private val SHEET_RADIUS = 24.dp
private val SHEET_MAX_WIDTH = 640.dp

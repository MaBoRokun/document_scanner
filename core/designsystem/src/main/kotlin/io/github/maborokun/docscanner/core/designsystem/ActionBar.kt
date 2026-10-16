package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A bar of buttons pinned to the bottom of a screen: above the system navigation bar, never over content, and gone
 * while the keyboard is open so it cannot be squeezed between the keyboard and the content. Give each button
 * `Modifier.weight(...)`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionBar(
    modifier: Modifier = Modifier,
    maxWidth: Dp = ActionBarMaxWidth,
    content: @Composable RowScope.() -> Unit,
) {
    if (WindowInsets.isImeVisible) return
    Surface(
        modifier,
        shape = RoundedCornerShape(topStart = BAR_RADIUS, topEnd = BAR_RADIUS),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = ScreenMargin, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** Weight of the main button next to a secondary one, so the main action reads as the bigger target. */
const val PRIMARY_BUTTON_WEIGHT = 1.4f

val ActionBarMaxWidth: Dp = 640.dp

private val BAR_RADIUS = 20.dp

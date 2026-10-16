package io.github.maborokun.docscanner.feature.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.maborokun.docscanner.core.designsystem.AppIcons
import io.github.maborokun.docscanner.core.designsystem.ScannerColors
import io.github.maborokun.docscanner.core.designsystem.docFilterChipBorder
import io.github.maborokun.docscanner.core.designsystem.docFilterChipColors
import io.github.maborokun.docscanner.domain.model.PageEdit
import io.github.maborokun.docscanner.domain.model.PageTint
import io.github.maborokun.docscanner.domain.processing.EnhanceMode

@Composable
internal fun ToolPanel(
    state: EditorUiState,
    actions: EditorActions,
) {
    // A fixed minimum height keeps the preview from jumping when the tool changes.
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(
                min = PANEL_MIN_HEIGHT,
            ).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        when (state.tool) {
            EditorTool.Crop -> CropPanel(state)
            EditorTool.Adjust -> AdjustPanel(state.edit, actions)
            EditorTool.Filters -> FilterPanel(state.edit.tint, actions.onTint)
            EditorTool.Draw -> DrawPanel(state, actions)
        }
    }
}

@Composable
internal fun CropPanel(state: EditorUiState) {
    PanelHint(stringResource(R.string.crop_hint))
    if (state.edit.strokes.isNotEmpty()) PanelHint(stringResource(R.string.crop_removes_drawing))
}

@Composable
internal fun PanelHint(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = ScannerColors.OnCanvasMuted,
        textAlign = TextAlign.Center,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdjustPanel(
    edit: PageEdit,
    actions: EditorActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(AppIcons.Brightness, contentDescription = stringResource(R.string.brightness), Modifier.size(20.dp))
        Slider(
            value = edit.brightness,
            onValueChange = actions.onBrightness,
            onValueChangeFinished = actions.onBrightnessFinished,
            valueRange = -BRIGHTNESS_RANGE..BRIGHTNESS_RANGE,
            modifier = Modifier.weight(1f),
        )
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        MODE_LABELS.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = edit.mode == mode,
                onClick = { actions.onMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, MODE_LABELS.size),
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

@Composable
internal fun FilterPanel(
    selected: PageTint,
    onTint: (PageTint) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PageTint.entries, key = { it.name }) { tint ->
            FilterChip(
                selected = tint == selected,
                onClick = { onTint(tint) },
                colors = docFilterChipColors(onCard = true),
                border = docFilterChipBorder(tint == selected),
                label = { Text(stringResource(tint.labelRes)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DrawPanel(
    state: EditorUiState,
    actions: EditorActions,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        PEN_COLORS.forEach { (color, name) ->
            Swatch(color, name, selected = state.penColor == color) { actions.onPenColor(color) }
        }
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        PenSize.entries.forEachIndexed { index, size ->
            SegmentedButton(
                selected = state.penSize == size,
                onClick = { actions.onPenSize(size) },
                shape = SegmentedButtonDefaults.itemShape(index, PenSize.entries.size),
            ) { Text(stringResource(size.label), maxLines = 1) }
        }
    }
    PanelHint(stringResource(R.string.draw_hint))
}

@Composable
internal fun Swatch(
    color: Int,
    @StringRes name: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.pen_colour, stringResource(name))
    val ring = if (selected) BorderStroke(3.dp, Color.White) else BorderStroke(1.dp, ScannerColors.OnCanvasMuted)
    Box(
        Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(Color(color))
            .border(ring, CircleShape)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
    )
}

@Composable
internal fun ToolTabs(
    selected: EditorTool,
    onTool: (EditorTool) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        EditorTool.entries.forEach { tool ->
            ToolTab(tool, iconFor(tool), selected = tool == selected) { onTool(tool) }
        }
    }
}

@Composable
internal fun ToolTab(
    tool: EditorTool,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else ScannerColors.OnCanvasMuted
    Column(
        Modifier
            .clip(
                MaterialTheme.shapes.medium,
            ).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(stringResource(tool.label), style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

internal fun iconFor(tool: EditorTool): ImageVector =
    when (tool) {
        EditorTool.Crop -> AppIcons.Crop
        EditorTool.Adjust -> AppIcons.Brightness
        EditorTool.Filters -> AppIcons.Filters
        EditorTool.Draw -> AppIcons.Pen
    }

internal val MODE_LABELS =
    listOf(
        EnhanceMode.None to R.string.mode_original,
        EnhanceMode.Contrast to R.string.mode_document,
        EnhanceMode.BlackWhite to R.string.mode_bw,
    )

internal val PEN_COLORS =
    listOf(
        0xFF000000.toInt() to R.string.color_black,
        0xFFFFFFFF.toInt() to R.string.color_white,
        0xFFE53935.toInt() to R.string.color_red,
        0xFFFDD835.toInt() to R.string.color_yellow,
        0xFF1E88E5.toInt() to R.string.color_blue,
    )

@get:StringRes
internal val PageTint.labelRes: Int
    get() =
        when (this) {
            PageTint.None -> R.string.tint_none
            PageTint.Warm -> R.string.tint_warm
            PageTint.Cool -> R.string.tint_cool
            PageTint.Sepia -> R.string.tint_sepia
            PageTint.Mono -> R.string.tint_mono
            PageTint.Vivid -> R.string.tint_vivid
        }

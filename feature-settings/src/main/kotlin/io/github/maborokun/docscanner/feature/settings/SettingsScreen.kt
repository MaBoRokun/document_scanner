package io.github.maborokun.docscanner.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.maborokun.docscanner.core.designsystem.AppIcons
import io.github.maborokun.docscanner.core.designsystem.ScreenMargin
import io.github.maborokun.docscanner.core.designsystem.docSwitchColors
import io.github.maborokun.docscanner.domain.model.PaperFormats
import io.github.maborokun.docscanner.domain.settings.ScanEngine

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onEngine = viewModel::selectEngine,
        onPaper = viewModel::selectPaper,
        onAppFunctions = viewModel::setAppFunctionsEnabled,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEngine: (ScanEngine) -> Unit,
    onPaper: (String?) -> Unit,
    onAppFunctions: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noPaper = stringResource(R.string.none)
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(TOP_BAR_HEIGHT).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
        ) {
            Section(stringResource(R.string.section_scanning)) {
                SwitchRow(
                    title = stringResource(R.string.quick_scan_title),
                    detail =
                        if (state.quickScanAvailable) {
                            stringResource(R.string.quick_scan_on)
                        } else {
                            stringResource(R.string.quick_scan_off)
                        },
                    checked = state.settings.engine == ScanEngine.QuickScan,
                    enabled = state.quickScanAvailable,
                    onChange = { onEngine(if (it) ScanEngine.QuickScan else ScanEngine.Custom) },
                )
                Divider()
                SelectRow(
                    title = stringResource(R.string.paper_size),
                    detail = stringResource(R.string.paper_detail),
                    value = state.settings.defaultPaperName ?: noPaper,
                    options = listOf<String?>(null) + PaperFormats.ALL.map { it.name },
                    optionLabel = { it ?: noPaper },
                    onSelect = onPaper,
                )
            }
            Section(stringResource(R.string.section_assistants)) {
                SwitchRow(
                    title = stringResource(R.string.assistants_title),
                    detail =
                        if (state.appFunctionsSupported) {
                            stringResource(R.string.assistants_on)
                        } else {
                            stringResource(R.string.assistants_off)
                        },
                    checked = state.settings.appFunctionsEnabled,
                    enabled = state.appFunctionsSupported,
                    onChange = onAppFunctions,
                )
            }
            Section(stringResource(R.string.section_privacy)) {
                PRIVACY_POINTS.forEachIndexed { index, point ->
                    if (index > 0) Divider()
                    Text(
                        stringResource(point),
                        Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}

/** A grey label above a white rounded card holding rows, as in the system settings. */
@Composable
private fun Section(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(CARD_RADIUS),
                ).background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            content()
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        Modifier.padding(horizontal = ROW_PADDING),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun RowTitle(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = ROW_PADDING, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowTitle(title, detail, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = docSwitchColors(),
        )
    }
}

/** A row showing the current choice with up/down chevrons; tapping it opens a menu of the options. */
@Composable
private fun <T> SelectRow(
    title: String,
    detail: String?,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = ROW_PADDING, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RowTitle(title, detail, Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                AppIcons.UnfoldMore,
                contentDescription = null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** Mirrors the README privacy section; the manifest check in the build keeps the first point true. */
internal val PRIVACY_POINTS =
    listOf(
        R.string.privacy_network,
        R.string.privacy_no_tracking,
        R.string.privacy_storage,
        R.string.privacy_on_device,
        R.string.privacy_quick_scan,
    )

private val TOP_BAR_HEIGHT = 56.dp
private val SECTION_SPACING = 20.dp
private val CARD_RADIUS = 16.dp
private val ROW_PADDING = 16.dp

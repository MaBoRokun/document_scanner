package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Shared component styling, so every screen gets the same flat, rounded look: white pills and fields on the grey
// page, no outlines, a blue accent for what is selected or on.

/**
 * A white pill that turns blue-tinted when selected; no outline. [onCard] is for chips that sit inside a white
 * card, where they get a light grey fill instead so they stay visible.
 */
@Composable
fun docFilterChipColors(onCard: Boolean = false): SelectableChipColors {
    val scheme = MaterialTheme.colorScheme
    return FilterChipDefaults.filterChipColors(
        containerColor = if (onCard) scheme.surfaceContainerHighest else scheme.surfaceContainerLowest,
        labelColor = scheme.onSurface,
        selectedContainerColor = scheme.primaryContainer,
        selectedLabelColor = scheme.onPrimaryContainer,
    )
}

@Composable
fun docFilterChipBorder(selected: Boolean): BorderStroke =
    FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = Color.Transparent,
        selectedBorderColor = Color.Transparent,
    )

@Composable
fun docAssistChipColors(): ChipColors =
    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)

/** A white field with a blue outline only while it has focus; [onCard] gives it a light grey fill for use on a card. */
@Composable
fun docTextFieldColors(onCard: Boolean = false): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    val container = if (onCard) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        unfocusedBorderColor = Color.Transparent,
        focusedBorderColor = scheme.primary,
    )
}

/** Blue when on, a light grey track with a white thumb when off. */
@Composable
fun docSwitchColors(): SwitchColors {
    val scheme = MaterialTheme.colorScheme
    return SwitchDefaults.colors(
        checkedTrackColor = scheme.primary,
        checkedThumbColor = Color.White,
        checkedBorderColor = scheme.primary,
        uncheckedTrackColor = scheme.outlineVariant,
        uncheckedThumbColor = Color.White,
        uncheckedBorderColor = scheme.outlineVariant,
    )
}

package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Soft grey page, white cards, blue accent: the look of the system settings. See docs/design.md.
// Cards and sheets use the surfaceContainer* roles (white in light theme); `surface` is the grey page itself.
private val LightColors: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF2F6BEB),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE6FD),
        onPrimaryContainer = Color(0xFF0B2E73),
        secondary = Color(0xFF5B6B84),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4E9F2),
        onSecondaryContainer = Color(0xFF1B2433),
        tertiary = Color(0xFF0C7C89),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD6EEF2),
        onTertiaryContainer = Color(0xFF04343B),
        background = Color(0xFFEFF1F4),
        onBackground = Color(0xFF14171A),
        surface = Color(0xFFEFF1F4),
        onSurface = Color(0xFF14171A),
        surfaceVariant = Color(0xFFE3E6EA),
        onSurfaceVariant = Color(0xFF6B7078),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFF6F7F9),
        surfaceContainerHighest = Color(0xFFE3E6EA),
        outline = Color(0xFF9AA0A8),
        outlineVariant = Color(0xFFE1E4E8),
    )

private val DarkColors: ColorScheme =
    darkColorScheme(
        primary = Color(0xFF8AB4FF),
        onPrimary = Color(0xFF0A2A66),
        primaryContainer = Color(0xFF1F4A9E),
        onPrimaryContainer = Color(0xFFDCE6FD),
        secondary = Color(0xFFB8C4D9),
        onSecondary = Color(0xFF1F2A3D),
        secondaryContainer = Color(0xFF33415A),
        onSecondaryContainer = Color(0xFFE4E9F2),
        tertiary = Color(0xFF7FD6E3),
        onTertiary = Color(0xFF00363D),
        tertiaryContainer = Color(0xFF114E57),
        onTertiaryContainer = Color(0xFFD6EEF2),
        background = Color(0xFF0F1216),
        onBackground = Color(0xFFE3E6EA),
        surface = Color(0xFF0F1216),
        onSurface = Color(0xFFE3E6EA),
        surfaceVariant = Color(0xFF30363D),
        onSurfaceVariant = Color(0xFFB4BAC2),
        surfaceContainerLowest = Color(0xFF0B0D10),
        surfaceContainerLow = Color(0xFF1A1E23),
        surfaceContainer = Color(0xFF1E2328),
        surfaceContainerHigh = Color(0xFF262B31),
        surfaceContainerHighest = Color(0xFF30363D),
        outline = Color(0xFF7E858E),
        outlineVariant = Color(0xFF2C3239),
    )

private val DocShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

private val base = Typography()
private val DocTypography =
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Medium),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    )

/** The app has one theme, the light one, whatever the system's dark mode says. */
@Composable
fun DocScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = DocTypography,
        shapes = DocShapes,
        content = content,
    )
}

/** The dark colour scheme of the scanner canvas (see [ScannerSurface]), the only dark part of the app. */
internal fun scannerColorScheme(): ColorScheme = DarkColors

/** Colours of the dark canvas used wherever a page is shown or adjusted. */
object ScannerColors {
    val Canvas = Color(0xFF0B0F12)
    val Scrim = Color(0xCC0B0F12)
    val Pill = Color(0x99000000)
    val OnCanvas = Color.White
    val OnCanvasMuted = Color(0xFFB8C0C6)
}

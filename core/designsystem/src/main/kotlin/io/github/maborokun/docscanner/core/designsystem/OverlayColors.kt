package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.ui.graphics.Color

object OverlayColors {
    val Good = Color(0xFF2E7D32)
    val Warning = Color(0xFFF9A825)
    val Poor = Color(0xFFC62828)
}

/** Traffic-light colour for a detector confidence in 0..1. */
fun confidenceColor(confidence: Float): Color =
    when {
        confidence >= GOOD_CONFIDENCE -> OverlayColors.Good
        confidence >= WARNING_CONFIDENCE -> OverlayColors.Warning
        else -> OverlayColors.Poor
    }

private const val GOOD_CONFIDENCE = 0.7f
private const val WARNING_CONFIDENCE = 0.4f

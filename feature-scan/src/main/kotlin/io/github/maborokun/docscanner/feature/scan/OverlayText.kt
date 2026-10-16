package io.github.maborokun.docscanner.feature.scan

import androidx.annotation.StringRes
import io.github.maborokun.docscanner.domain.model.EdgeMeasure
import io.github.maborokun.docscanner.domain.model.ScanHint
import java.util.Locale
import kotlin.math.roundToInt

/**
 * "≈ 210 mm (est.)" with a paper chosen ([mmFormat] is that pattern, with `%1$d`), otherwise a resolution-independent
 * "0.62 u" (u = long frame side).
 */
fun edgeLabel(
    edge: EdgeMeasure,
    mmFormat: String,
): String =
    edge.mm?.let { String.format(Locale.getDefault(), mmFormat, it.roundToInt()) }
        ?: String.format(Locale.getDefault(), "%.2f u", edge.relative)

@StringRes
fun hintText(hint: ScanHint): Int =
    when (hint) {
        ScanHint.Tilted -> R.string.hint_tilted
        ScanHint.Blurry -> R.string.hint_blurry
        ScanHint.LowConfidence -> R.string.hint_low_confidence
        ScanHint.TooSmall -> R.string.hint_too_small
        ScanHint.AspectMismatch -> R.string.hint_aspect
    }

enum class StatusTone { Searching, Warning, Good }

/** One line under the viewfinder: what the detector sees and what to do about it. */
data class StatusLine(
    @StringRes val text: Int,
    val tone: StatusTone,
)

fun statusLine(overlay: OverlayState): StatusLine {
    if (overlay.quad == null) return StatusLine(R.string.status_point, StatusTone.Searching)
    val hint = overlay.measurement?.hints?.firstOrNull()
    return when {
        hint != null -> StatusLine(hintText(hint), StatusTone.Warning)
        overlay.paper != null -> StatusLine(R.string.status_found_paper, StatusTone.Good)
        overlay.manual -> StatusLine(R.string.status_manual, StatusTone.Good)
        else -> StatusLine(R.string.status_found, StatusTone.Good)
    }
}

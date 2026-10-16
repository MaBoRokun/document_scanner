package io.github.maborokun.docscanner.feature.scan

/** What the scan screen should show for the camera permission. */
enum class CameraAccess {
    Granted,

    /** Not granted yet; explain why the camera is needed, then ask. */
    NeedsExplanation,

    /** Denied with "don't ask again": the system dialog will not appear, so send the user to app settings. */
    PermanentlyDenied,
}

/**
 * [requestedBefore] is whether we already showed the system dialog; without it, "never asked" and
 * "permanently denied" look identical (both have no rationale to show).
 */
fun cameraAccess(
    granted: Boolean,
    requestedBefore: Boolean,
    shouldShowRationale: Boolean,
): CameraAccess =
    when {
        granted -> CameraAccess.Granted
        !requestedBefore || shouldShowRationale -> CameraAccess.NeedsExplanation
        else -> CameraAccess.PermanentlyDenied
    }

package io.github.maborokun.docscanner.domain.settings

import io.github.maborokun.docscanner.domain.model.PaperFormat
import io.github.maborokun.docscanner.domain.model.PaperFormats
import kotlinx.coroutines.flow.Flow

enum class ScanEngine {
    /** The app's own camera pipeline (default; fully on device, no Google Play services needed). */
    Custom,

    /** Google's ML Kit Document Scanner, run by Google Play services. */
    QuickScan,
}

data class AppSettings(
    val engine: ScanEngine = ScanEngine.Custom,
    /** Name of a [PaperFormats] entry preselected on the scan screen, or null for none. */
    val defaultPaperName: String? = null,
    /** Lets on-device assistants search documents (Android 16+); off until the user enables it. */
    val appFunctionsEnabled: Boolean = false,
) {
    val defaultPaper: PaperFormat? get() = PaperFormats.ALL.firstOrNull { it.name == defaultPaperName }
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(change: (AppSettings) -> AppSettings)
}

/** Whether the Quick scan engine can run here (it needs Google Play services). */
fun interface QuickScanAvailability {
    fun isAvailable(): Boolean
}

package io.github.maborokun.docscanner.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.maborokun.docscanner.domain.settings.AppSettings
import io.github.maborokun.docscanner.domain.settings.QuickScanAvailability
import io.github.maborokun.docscanner.domain.settings.ScanEngine
import io.github.maborokun.docscanner.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** Quick scan needs Google Play services; without it the option is shown but disabled. */
    val quickScanAvailable: Boolean = false,
    /** Assistants can only call the app on Android 16 (API 36) and newer. */
    val appFunctionsSupported: Boolean = false,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    quickScan: QuickScanAvailability,
    appFunctionsSupported: Boolean,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        repository.settings
            .map { SettingsUiState(it, quickScan.isAvailable(), appFunctionsSupported) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                SettingsUiState(AppSettings(), quickScan.isAvailable(), appFunctionsSupported),
            )

    fun selectEngine(engine: ScanEngine) = update { it.copy(engine = engine) }

    /** [name] is a paper format name, or null for no default. */
    fun selectPaper(name: String?) = update { it.copy(defaultPaperName = name) }

    fun setAppFunctionsEnabled(enabled: Boolean) = update { it.copy(appFunctionsEnabled = enabled) }

    private fun update(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(change) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

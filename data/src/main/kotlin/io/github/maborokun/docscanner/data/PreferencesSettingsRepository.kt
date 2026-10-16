package io.github.maborokun.docscanner.data

import android.content.Context
import android.content.SharedPreferences
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.settings.AppSettings
import io.github.maborokun.docscanner.domain.settings.ScanEngine
import io.github.maborokun.docscanner.domain.settings.SettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Settings in a private SharedPreferences file; small and synchronous enough that DataStore would add nothing. */
class PreferencesSettingsRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
) : SettingsRepository {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override val settings: Flow<AppSettings> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(read())
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged().flowOn(dispatchers.io)

    override suspend fun update(change: (AppSettings) -> AppSettings) {
        withContext(dispatchers.io) {
            val updated = change(read())
            prefs
                .edit()
                .putString(KEY_ENGINE, updated.engine.name)
                .also { editor ->
                    if (updated.defaultPaperName == null) {
                        editor.remove(KEY_PAPER)
                    } else {
                        editor.putString(KEY_PAPER, updated.defaultPaperName)
                    }
                }.putBoolean(KEY_APP_FUNCTIONS, updated.appFunctionsEnabled)
                .apply()
        }
    }

    private fun read(): AppSettings =
        AppSettings(
            engine =
                prefs.getString(KEY_ENGINE, null)?.let { name -> ScanEngine.entries.firstOrNull { it.name == name } }
                    ?: ScanEngine.Custom,
            defaultPaperName = prefs.getString(KEY_PAPER, null),
            appFunctionsEnabled = prefs.getBoolean(KEY_APP_FUNCTIONS, false),
        )

    private companion object {
        const val KEY_ENGINE = "engine"
        const val KEY_PAPER = "default_paper"
        const val KEY_APP_FUNCTIONS = "app_functions"
    }
}

package io.github.maborokun.docscanner.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.settings.AppSettings
import io.github.maborokun.docscanner.domain.settings.ScanEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesSettingsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatchers =
        object : AppDispatchers {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
        }

    private fun repository() = PreferencesSettingsRepository(context, dispatchers)

    @Test
    fun freshInstallHasTheDefaults() =
        runBlocking {
            val settings = repository().settings.first()
            assertEquals(AppSettings(), settings)
            assertFalse(settings.appFunctionsEnabled)
        }

    @Test
    fun updatesArePersistedAcrossInstances() =
        runBlocking {
            repository().update {
                it.copy(
                    engine = ScanEngine.QuickScan,
                    defaultPaperName = "A4",
                    appFunctionsEnabled = true,
                )
            }

            val reloaded = repository().settings.first()
            assertEquals(ScanEngine.QuickScan, reloaded.engine)
            assertEquals("A4", reloaded.defaultPaperName)
            assertEquals(true, reloaded.appFunctionsEnabled)
        }

    @Test
    fun clearingThePaperRemovesIt() =
        runBlocking {
            val repository = repository()
            repository.update { it.copy(defaultPaperName = "Letter") }
            repository.update { it.copy(defaultPaperName = null) }
            assertNull(repository.settings.first().defaultPaperName)
        }

    @Test
    fun unknownStoredEngineFallsBackToCustom() =
        runBlocking {
            context
                .getSharedPreferences(
                    "settings",
                    Context.MODE_PRIVATE,
                ).edit()
                .putString("engine", "Retired")
                .commit()
            assertEquals(ScanEngine.Custom, repository().settings.first().engine)
        }
}

package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.model.PaperFormats
import io.github.maborokun.docscanner.domain.settings.AppSettings
import io.github.maborokun.docscanner.domain.settings.ScanEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SettingsTest {
    @Test
    fun defaultsAreTheSafeChoices() {
        val defaults = AppSettings()
        assertEquals(ScanEngine.Custom, defaults.engine)
        assertNull(defaults.defaultPaper)
        assertFalse(defaults.appFunctionsEnabled)
    }

    @Test
    fun defaultPaperIsLookedUpByName() {
        assertEquals(PaperFormats.LETTER, AppSettings(defaultPaperName = "Letter").defaultPaper)
        assertNull(AppSettings(defaultPaperName = "Tabloid").defaultPaper)
    }

    @Test
    fun fakeRepositoryAppliesUpdates() =
        runTest {
            val repository = FakeSettingsRepository()
            repository.update { it.copy(engine = ScanEngine.QuickScan) }
            assertEquals(ScanEngine.QuickScan, repository.settings.first().engine)
        }
}

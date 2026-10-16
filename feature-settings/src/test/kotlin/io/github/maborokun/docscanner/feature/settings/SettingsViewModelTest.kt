package io.github.maborokun.docscanner.feature.settings

import app.cash.turbine.test
import io.github.maborokun.docscanner.domain.FakeSettingsRepository
import io.github.maborokun.docscanner.domain.settings.ScanEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val repository = FakeSettingsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        quickScan: Boolean = true,
        appFunctions: Boolean = true,
    ) = SettingsViewModel(repository, { quickScan }, appFunctions)

    @Test
    fun startsWithTheSafeDefaults() =
        runTest {
            viewModel().uiState.test {
                val state = expectMostRecentItem()
                assertEquals(ScanEngine.Custom, state.settings.engine)
                assertFalse(state.settings.appFunctionsEnabled)
                assertTrue(state.quickScanAvailable)
            }
        }

    @Test
    fun changesAreStoredAndReflectedInTheState() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                viewModel.selectEngine(ScanEngine.QuickScan)
                viewModel.selectPaper("A4")
                viewModel.setAppFunctionsEnabled(true)

                val state = expectMostRecentItem().settings
                assertEquals(ScanEngine.QuickScan, state.engine)
                assertEquals("A4", state.defaultPaperName)
                assertTrue(state.appFunctionsEnabled)

                viewModel.selectPaper(null)
                assertEquals(null, expectMostRecentItem().settings.defaultPaperName)
            }
        }

    @Test
    fun availabilityFlagsComeFromTheirProviders() =
        runTest {
            viewModel(quickScan = false, appFunctions = false).uiState.test {
                val state = expectMostRecentItem()
                assertFalse(state.quickScanAvailable)
                assertFalse(state.appFunctionsSupported)
            }
        }

    @Test
    fun privacyPointsNameTheNetworkAndThePlayServicesCaveat() {
        // The points are string resources now: check the English copy they resolve to.
        val english = java.io.File("src/main/res/values/strings.xml").readText()
        assertTrue("no network permission" in english)
        assertTrue("Google Play services" in english)
        assertEquals(PRIVACY_POINTS.size, PRIVACY_POINTS.toSet().size)
    }
}

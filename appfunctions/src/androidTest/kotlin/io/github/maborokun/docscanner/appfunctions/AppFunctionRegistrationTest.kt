package io.github.maborokun.docscanner.appfunctions

import android.util.Log
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.maborokun.docscanner.domain.FakeDocumentRepository
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.usecase.ObserveDocuments
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/**
 * Asks the system whether the function is indexed and whether it is off until the app enables it. Runs only on
 * Android 16+, where app functions exist. An app may always query its own functions.
 */
@SdkSuppress(minSdkVersion = 36)
@RunWith(AndroidJUnit4::class)
class AppFunctionRegistrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName

    @Before
    fun serveSyntheticDocuments() {
        val repository = FakeDocumentRepository()
        runBlocking {
            repository.insert(
                "Blue Bakery",
                0L,
                null,
                listOf(NewPage("/private/secret.jpg", 1, 1, "secret")),
            )
        }
        AppFunctionsHost.search = DocumentSearch(ObserveDocuments(repository), ZoneOffset.UTC)
    }

    private suspend fun awaitIndexed(manager: AppFunctionManager): List<String> {
        var ids = emptyList<String>()
        repeat(ATTEMPTS) {
            ids = manager.searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(packageName))).map { it.id }
            if (SEARCH_FUNCTION in ids) return ids
            delay(RETRY_MS)
        }
        return ids
    }

    @Test
    fun searchFunctionIsIndexedAndOffUntilTheAppEnablesIt() =
        runBlocking {
            val manager = AppFunctionManager.getInstance(context)
            assertNotNull("app functions should exist on this device", manager)
            manager!!

            val ids = awaitIndexed(manager)
            Log.i("AppFunctionsTest", "indexed functions: $ids")
            assertTrue("searchDocuments not indexed, saw $ids", SEARCH_FUNCTION in ids)

            AppFunctionsController.apply(context, enabled = false)
            assertFalse("must be off by default", manager.isAppFunctionEnabled(packageName, SEARCH_FUNCTION))

            AppFunctionsController.apply(context, enabled = true)
            assertTrue(manager.isAppFunctionEnabled(packageName, SEARCH_FUNCTION))

            AppFunctionsController.apply(context, enabled = false)
            assertEquals(false, manager.isAppFunctionEnabled(packageName, SEARCH_FUNCTION))
        }

    private companion object {
        const val SEARCH_FUNCTION = AppFunctionsController.SEARCH_FUNCTION_ID
        const val ATTEMPTS = 15
        const val RETRY_MS = 2_000L
    }
}

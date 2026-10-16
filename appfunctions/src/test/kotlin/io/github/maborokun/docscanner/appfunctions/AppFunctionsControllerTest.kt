package io.github.maborokun.docscanner.appfunctions

import android.content.Context
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFunctionsControllerTest {
    @Test
    fun belowAndroid16NothingHappensAndNothingIsTouched() =
        runTest {
            // Host tests run with SDK_INT 0, so this proves the guard returns before any Android 16 class is used.
            val context = mockk<Context>() // strict: any call fails the test
            AppFunctionsController.apply(context, enabled = true)
            confirmVerified(context)
        }

    @Test
    fun theFunctionIdMatchesTheGeneratedDescription() {
        assertEquals(
            "${BaseDocumentSearchService::class.java.name}#searchDocuments",
            AppFunctionsController.SEARCH_FUNCTION_ID,
        )
    }
}

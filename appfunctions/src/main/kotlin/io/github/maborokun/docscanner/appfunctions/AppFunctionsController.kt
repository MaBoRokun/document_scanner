package io.github.maborokun.docscanner.appfunctions

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionManager
import io.github.maborokun.docscanner.core.common.catching
import kotlinx.coroutines.delay

/** Turns the `searchDocuments` function on or off for assistants; the function is off until the user opts in. */
object AppFunctionsController {
    /** Android 16: the first release in which assistants can call app functions. */
    const val MIN_SDK = 36

    /** `<declaring class>#<function>`, as generated into the function's XML description. */
    const val SEARCH_FUNCTION_ID =
        "io.github.maborokun.docscanner.appfunctions.BaseDocumentSearchService#searchDocuments"

    private const val ATTEMPTS = 5
    private const val RETRY_DELAY_MS = 2_000L

    /**
     * Applies [enabled]. A no-op below Android 16. Right after install or app start the system may not have indexed
     * the function yet, so a failure is retried a few times before giving up quietly: the setting is applied again
     * on the next app start.
     */
    suspend fun apply(
        context: Context,
        enabled: Boolean,
    ) {
        if (Build.VERSION.SDK_INT < MIN_SDK) return
        repeat(ATTEMPTS) { attempt ->
            if (catching { setEnabled(context, enabled) }.isSuccess) return
            if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
        }
    }

    @RequiresApi(MIN_SDK)
    private suspend fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        val state =
            if (enabled) {
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED
            } else {
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED
            }
        checkNotNull(AppFunctionManager.getInstance(context)) { "App functions are not available" }
            .setAppFunctionEnabled(SEARCH_FUNCTION_ID, state)
    }
}

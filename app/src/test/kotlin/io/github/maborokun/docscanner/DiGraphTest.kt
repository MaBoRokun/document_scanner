package io.github.maborokun.docscanner

import android.content.Context
import io.github.maborokun.docscanner.feature.export.ExportRequest
import org.junit.Test
import org.koin.test.verify.verify
import java.time.ZoneId

/**
 * Every definition in [appModule] must be satisfiable from the module itself: a missing binding fails here, in a
 * plain unit test, instead of crashing the app the first time that screen opens. The extra types are values that
 * are supplied at the call site (navigation arguments, the Android context, the clock).
 */
class DiGraphTest {
    @Test
    fun everyDependencyInTheGraphIsDefined() {
        appModule.verify(
            extraTypes =
                listOf(
                    Context::class,
                    Long::class,
                    Boolean::class,
                    ExportRequest::class,
                    ZoneId::class,
                    Function0::class,
                ),
        )
    }
}

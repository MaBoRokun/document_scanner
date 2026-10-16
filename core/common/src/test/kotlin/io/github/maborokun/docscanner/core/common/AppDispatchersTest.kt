package io.github.maborokun.docscanner.core.common

import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertSame

class AppDispatchersTest {
    @Test
    fun defaultMapsToCoroutineDispatchers() {
        assertSame(Dispatchers.Default, DefaultAppDispatchers.default)
        assertSame(Dispatchers.IO, DefaultAppDispatchers.io)
    }
}

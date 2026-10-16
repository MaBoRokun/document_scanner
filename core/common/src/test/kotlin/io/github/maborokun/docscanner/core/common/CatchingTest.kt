package io.github.maborokun.docscanner.core.common

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatchingTest {
    @Test
    fun returnsValueOnSuccess() {
        assertEquals(3, catching { 1 + 2 }.getOrNull())
    }

    @Test
    fun capturesOrdinaryFailures() {
        val result = catching { error("boom") }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun rethrowsCancellation() {
        assertFailsWith<CancellationException> { catching { throw CancellationException("stop") } }
    }
}

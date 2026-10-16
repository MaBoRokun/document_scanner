package io.github.maborokun.docscanner.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but never swallows coroutine cancellation. The one place that catches broadly,
 * so call sites at UI boundaries can turn any failure into a message without catching `Exception` themselves.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> catching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

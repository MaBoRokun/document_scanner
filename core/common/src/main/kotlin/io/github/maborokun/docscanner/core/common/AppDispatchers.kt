package io.github.maborokun.docscanner.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Injected everywhere instead of touching [Dispatchers] directly, so tests can swap in a test dispatcher. */
interface AppDispatchers {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher

    /** CPU-heavy image and OCR work: at most two threads, so the camera and UI keep their share of the cores. */
    val processing: CoroutineDispatcher get() = default
}

object DefaultAppDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO

    @OptIn(ExperimentalCoroutinesApi::class)
    override val processing: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(PROCESSING_PARALLELISM)
}

private const val PROCESSING_PARALLELISM = 2

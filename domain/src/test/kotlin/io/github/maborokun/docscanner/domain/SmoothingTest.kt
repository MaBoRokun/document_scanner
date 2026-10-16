package io.github.maborokun.docscanner.domain

import app.cash.turbine.test
import io.github.maborokun.docscanner.domain.model.DetectionResult
import io.github.maborokun.docscanner.domain.model.FrameSize
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.vision.SmoothingConfig
import io.github.maborokun.docscanner.domain.vision.smoothed
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SmoothingTest {
    private val size = FrameSize(100, 100)
    private val config = SmoothingConfig(alpha = 0.5f, jumpDistance = 0.2f, maxMisses = 2)

    private fun quadAt(offset: Float) =
        Quad(
            Point(0.1f + offset, 0.1f),
            Point(0.9f + offset, 0.1f),
            Point(0.9f + offset, 0.9f),
            Point(0.1f + offset, 0.9f),
        )

    private fun found(
        offset: Float,
        confidence: Float = 1f,
        size: FrameSize = this.size,
    ) = DetectionResult(quadAt(offset), confidence, 0f, 1f, size)

    private val lost = DetectionResult(null, 0f, 0f, 1f, size)

    private suspend fun run(vararg inputs: DetectionResult) = flowOf(*inputs).smoothed(config).toList()

    @Test
    fun firstFrameIsPassedThrough() =
        runTest {
            assertEquals(found(0f), run(found(0f)).single())
        }

    @Test
    fun smallMovesAreBlended() =
        runTest {
            val out = run(found(0f), found(0.1f))
            assertEquals(0.15f, requireNotNull(out[1].quad).topLeft.x, 1e-5f)
        }

    @Test
    fun confidenceIsBlendedToo() =
        runTest {
            val out = run(found(0f, confidence = 1f), found(0f, confidence = 0.5f))
            assertEquals(0.75f, out[1].confidence, 1e-5f)
        }

    @Test
    fun bigJumpsSnapToTheNewPosition() =
        runTest {
            val out = run(found(0f), found(0.5f))
            assertEquals(0.6f, requireNotNull(out[1].quad).topLeft.x, 1e-5f)
        }

    @Test
    fun frameSizeChangeResetsTheFilter() =
        runTest {
            val out = run(found(0f), found(0.1f, size = FrameSize(50, 50)))
            assertEquals(0.2f, requireNotNull(out[1].quad).topLeft.x, 1e-5f)
        }

    @Test
    fun lostPageIsHeldBrieflyThenDropped() =
        runTest {
            val out = run(found(0f), lost, lost, lost)
            assertNotNull(out[1].quad)
            assertNotNull(out[2].quad)
            assertNull(out[3].quad)
        }

    @Test
    fun foundAfterLossStartsFresh() =
        runTest {
            val out = run(lost, found(0.3f))
            assertNull(out[0].quad)
            assertEquals(found(0.3f), out[1])
        }

    @Test
    fun missCounterResetsWhenThePageReturns() =
        runTest {
            val out = run(found(0f), lost, found(0f), lost, lost)
            assertNotNull(out[4].quad)
        }

    @Test
    fun emptyInputGivesEmptyOutput() =
        runTest {
            flowOf<DetectionResult>().smoothed().test { awaitComplete() }
        }
}

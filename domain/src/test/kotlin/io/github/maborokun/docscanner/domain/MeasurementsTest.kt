package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.model.FrameSize
import io.github.maborokun.docscanner.domain.model.PaperFormats
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.ScanHint
import io.github.maborokun.docscanner.domain.model.measureQuad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementsTest {
    private val frame = FrameSize(1000, 2000)

    /** A page 600 x 800 px (portrait), A4-like proportions are 1.414 so this one is 1.333. */
    private fun rect(
        widthPx: Float,
        heightPx: Float,
    ): Quad {
        val l = (1000f - widthPx) / 2000f
        val r = 1f - l
        val t = (2000f - heightPx) / 4000f
        val b = 1f - t
        return Quad(Point(l, t), Point(r, t), Point(r, b), Point(l, b))
    }

    @Test
    fun relativeLengthsUseTheLongFrameSide() {
        val m = measureQuad(rect(600f, 800f), frame)
        assertEquals(0.3f, m.top.relative, 1e-4f)
        assertEquals(0.4f, m.left.relative, 1e-4f)
        assertNull(m.top.mm)
        assertEquals(800f / 600f, m.aspect, 1e-4f)
    }

    @Test
    fun withoutPaperThereAreNoMillimetres() {
        assertNull(measureQuad(rect(600f, 800f), frame, paper = null).bottom.mm)
    }

    @Test
    fun a4ScalesTheLongSideTo297Millimetres() {
        val m = measureQuad(rect(600f, 800f), frame, PaperFormats.A4)
        assertEquals(297f, requireNotNull(m.left.mm), 1e-2f)
        assertEquals(222.75f, requireNotNull(m.top.mm), 1e-2f)
    }

    @Test
    fun landscapePagesUseTheLongSideToo() {
        val m = measureQuad(rect(800f, 600f), frame, PaperFormats.A4)
        assertEquals(297f, requireNotNull(m.top.mm), 1e-2f)
    }

    @Test
    fun receiptRollScalesTheShortSideToTheRollWidth() {
        val m = measureQuad(rect(300f, 1200f), frame, PaperFormats.RECEIPT_80)
        assertEquals(80f, requireNotNull(m.top.mm), 1e-2f)
        assertEquals(320f, requireNotNull(m.left.mm), 1e-2f)
        assertTrue(ScanHint.AspectMismatch !in m.hints)
    }

    @Test
    fun matchingAspectHasNoMismatchHintButWrongAspectDoes() {
        val a4Shaped = rect(600f, 600f * 297f / 210f)
        assertTrue(ScanHint.AspectMismatch !in measureQuad(a4Shaped, frame, PaperFormats.A4).hints)
        assertTrue(ScanHint.AspectMismatch in measureQuad(rect(600f, 600f), frame, PaperFormats.A4).hints)
    }

    @Test
    fun qualityHintsFollowTheThresholds() {
        val good = measureQuad(rect(600f, 800f), frame, confidence = 0.9f, skewDegrees = 2f, blurScore = 0.5f)
        assertTrue(good.hints.isEmpty())

        val bad = measureQuad(rect(100f, 100f), frame, confidence = 0.2f, skewDegrees = -12f, blurScore = 0.01f)
        assertEquals(
            setOf(ScanHint.Tilted, ScanHint.Blurry, ScanHint.LowConfidence, ScanHint.TooSmall),
            bad.hints,
        )
    }

    @Test
    fun degenerateQuadDoesNotDivideByZero() {
        val point = Quad(Point(0.5f, 0.5f), Point(0.5f, 0.5f), Point(0.5f, 0.5f), Point(0.5f, 0.5f))
        val m = measureQuad(point, frame, PaperFormats.A4)
        assertEquals(1f, m.aspect, 0f)
        assertNull(m.top.mm)
    }
}

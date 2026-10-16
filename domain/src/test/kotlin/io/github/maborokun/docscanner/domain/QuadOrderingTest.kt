package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.vision.Frame
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuadOrderingTest {
    private val quad = Quad(Point(0.1f, 0.2f), Point(0.8f, 0.1f), Point(0.9f, 0.9f), Point(0.2f, 0.8f))

    @Test
    fun fromUnorderedSortsClockwiseFromTopLeft() {
        val shuffled = listOf(quad.bottomRight, quad.topLeft, quad.bottomLeft, quad.topRight)
        assertEquals(quad, Quad.fromUnordered(shuffled))
    }

    @Test
    fun fromUnorderedRequiresFourPoints() {
        assertFailsWith<IllegalArgumentException> { Quad.fromUnordered(listOf(Point(0f, 0f))) }
    }

    @Test
    fun rotatingByZeroKeepsTheQuad() {
        assertEquals(quad, quad.rotatedClockwise(0))
    }

    @Test
    fun fourQuarterTurnsAreTheIdentity() {
        var q = quad
        repeat(4) { q = q.rotatedClockwise(90) }
        q.corners.zip(quad.corners).forEach { (a, b) ->
            assertEquals(b.x, a.x, 1e-5f)
            assertEquals(b.y, a.y, 1e-5f)
        }
    }

    @Test
    fun rotating90MovesTopLeftToTopRight() {
        val rotated = Quad.FULL.copy(topLeft = Point(0.1f, 0.1f)).rotatedClockwise(90)
        // Point (0.1, 0.1) of the unrotated image lands at (1 - 0.1, 0.1) = top-right after a clockwise quarter turn.
        assertEquals(Point(0.9f, 0.1f), rotated.topRight)
    }

    @Test
    fun rotating180MirrorsThroughTheCentre() {
        val rotated = quad.rotatedClockwise(180)
        assertEquals(1f - quad.bottomRight.x, rotated.topLeft.x, 1e-5f)
        assertEquals(1f - quad.bottomRight.y, rotated.topLeft.y, 1e-5f)
    }

    @Test
    fun rotating270IsTheInverseOf90() {
        val back = quad.rotatedClockwise(90).rotatedClockwise(270)
        quad.corners.zip(back.corners).forEach { (a, b) ->
            assertEquals(a.x, b.x, 1e-5f)
            assertEquals(a.y, b.y, 1e-5f)
        }
    }

    @Test
    fun withCornerReplacesOnlyThatCorner() {
        val moved = quad.withCorner(2, Point(0.5f, 0.5f))
        assertEquals(Point(0.5f, 0.5f), moved.bottomRight)
        assertEquals(quad.topLeft, moved.topLeft)
        assertEquals(quad.bottomLeft, moved.bottomLeft)
    }

    @Test
    fun rejectsOtherAngles() {
        assertFailsWith<IllegalArgumentException> { quad.rotatedClockwise(45) }
    }

    @Test
    fun frameValidatesItsGeometry() {
        val buffer = ByteBuffer.allocate(8 * 4)
        Frame(8, 4, 8, buffer, 90)
        assertFailsWith<IllegalArgumentException> { Frame(0, 4, 8, buffer) }
        assertFailsWith<IllegalArgumentException> { Frame(8, 4, 4, buffer) }
        assertFailsWith<IllegalArgumentException> { Frame(8, 5, 8, buffer) }
        assertFailsWith<IllegalArgumentException> { Frame(8, 4, 8, buffer, 45) }
    }
}

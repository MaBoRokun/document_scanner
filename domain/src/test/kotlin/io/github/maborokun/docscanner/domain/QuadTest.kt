package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuadTest {
    @Test
    fun fullQuadHasUnitAreaAndIsConvexAndNormalized() {
        assertEquals(1f, Quad.FULL.area, 1e-6f)
        assertTrue(Quad.FULL.isConvex)
        assertTrue(Quad.FULL.isNormalized)
    }

    @Test
    fun bowTieIsNotConvex() {
        val bowTie = Quad(Point(0f, 0f), Point(1f, 1f), Point(1f, 0f), Point(0f, 1f))
        assertFalse(bowTie.isConvex)
    }

    @Test
    fun degenerateQuadIsNotConvex() {
        val line = Quad(Point(0f, 0f), Point(0.5f, 0f), Point(1f, 0f), Point(0.2f, 0f))
        assertFalse(line.isConvex)
    }

    @Test
    fun cornerOutsideUnitSquareIsNotNormalized() {
        assertFalse(Quad.FULL.copy(topLeft = Point(-0.1f, 0f)).isNormalized)
    }

    @Test
    fun distanceIsEuclidean() {
        assertEquals(5f, Point(0f, 0f).distanceTo(Point(3f, 4f)), 1e-6f)
    }

    @Test
    fun areaIsOrientationIndependent() {
        val reversed = Quad(Point(0f, 0f), Point(0f, 1f), Point(1f, 1f), Point(1f, 0f))
        assertEquals(1f, reversed.area, 1e-6f)
        assertTrue(reversed.isConvex)
    }
}

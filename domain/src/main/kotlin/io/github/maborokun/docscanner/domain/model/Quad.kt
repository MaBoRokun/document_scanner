package io.github.maborokun.docscanner.domain.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** A point in normalized 0..1 image coordinates (origin top-left). */
data class Point(
    val x: Float,
    val y: Float,
) {
    fun distanceTo(other: Point): Float = hypot(x - other.x, y - other.y)
}

/** Document corners in normalized 0..1 coordinates, clockwise from the top-left. */
data class Quad(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point,
) {
    val corners: List<Point> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Polygon area via the shoelace formula. */
    val area: Float
        get() {
            var sum = 0f
            for (i in corners.indices) {
                val a = corners[i]
                val b = corners[(i + 1) % 4]
                sum += a.x * b.y - b.x * a.y
            }
            return abs(sum) / 2f
        }

    /** True when every corner lies inside the unit square. */
    val isNormalized: Boolean get() = corners.all { it.x in 0f..1f && it.y in 0f..1f }

    /** True when all corners turn the same way, i.e. the quad is convex and not self-intersecting. */
    val isConvex: Boolean
        get() {
            var sign = 0
            for (i in corners.indices) {
                val a = corners[i]
                val b = corners[(i + 1) % 4]
                val c = corners[(i + 2) % 4]
                val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
                val s =
                    if (cross > 0f) {
                        1
                    } else if (cross < 0f) {
                        -1
                    } else {
                        0
                    }
                if (s == 0) return false
                if (sign == 0) {
                    sign = s
                } else if (s != sign) {
                    return false
                }
            }
            return true
        }

    /** A copy with corner [index] (0 = top-left, clockwise) moved to [point]. */
    fun withCorner(
        index: Int,
        point: Point,
    ): Quad {
        val c = corners.toMutableList()
        c[index] = point
        return Quad(c[0], c[1], c[2], c[3])
    }

    /**
     * The quad on the image rotated clockwise by [degrees] (0, 90, 180 or 270), e.g. from sensor orientation to
     * upright. Corners are renamed so [topLeft] is again the top-left one.
     */
    fun rotatedClockwise(degrees: Int): Quad {
        val rotate: (Point) -> Point =
            when (degrees) {
                0 -> { p -> p }
                90 -> { p -> Point(1f - p.y, p.x) }
                180 -> { p -> Point(1f - p.x, 1f - p.y) }
                270 -> { p -> Point(p.y, 1f - p.x) }
                else -> throw IllegalArgumentException("degrees must be 0, 90, 180 or 270: $degrees")
            }
        return fromUnordered(corners.map(rotate))
    }

    companion object {
        /** The whole image, used when no document was detected. */
        val FULL = Quad(Point(0f, 0f), Point(1f, 0f), Point(1f, 1f), Point(0f, 1f))

        /** Orders four points clockwise (on screen, y down), starting at the one closest to the top-left corner. */
        fun fromUnordered(points: List<Point>): Quad {
            require(points.size == 4) { "A quad needs exactly four points" }
            val cx = points.sumOf { it.x.toDouble() }.toFloat() / 4f
            val cy = points.sumOf { it.y.toDouble() }.toFloat() / 4f
            val clockwise = points.sortedBy { atan2(it.y - cy, it.x - cx) }
            val start = clockwise.indices.minBy { clockwise[it].x + clockwise[it].y }
            val (a, b, c, d) = List(4) { clockwise[(start + it) % 4] }
            return Quad(a, b, c, d)
        }
    }
}

package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.ui.geometry.Offset
import io.github.maborokun.docscanner.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageViewportTest {
    @Test
    fun wideContainerLeavesBarsOnTheSides() {
        val viewport = ImageViewport(400f, 200f, imageAspect = 1f)
        assertEquals(100f, viewport.rect.left, 1e-3f)
        assertEquals(200f, viewport.rect.width, 1e-3f)
        assertEquals(0f, viewport.rect.top, 1e-3f)
    }

    @Test
    fun tallContainerLeavesBarsAboveAndBelow() {
        val viewport = ImageViewport(200f, 400f, imageAspect = 0.5f)
        // 200 wide image of ratio 0.5 would need 400 height: fits exactly.
        assertEquals(0f, viewport.rect.left, 1e-3f)
        assertEquals(400f, viewport.rect.height, 1e-3f)

        val wider = ImageViewport(200f, 400f, imageAspect = 1f)
        assertEquals(100f, wider.rect.top, 1e-3f)
        assertEquals(200f, wider.rect.height, 1e-3f)
    }

    @Test
    fun mappingRoundTrips() {
        val viewport = ImageViewport(300f, 500f, imageAspect = 0.75f)
        val p = Point(0.3f, 0.8f)
        val back = viewport.toImage(viewport.toScreen(p))
        assertEquals(p.x, back.x, 1e-4f)
        assertEquals(p.y, back.y, 1e-4f)
    }

    @Test
    fun cornersLandOnTheFittedRectangle() {
        val viewport = ImageViewport(400f, 200f, imageAspect = 1f)
        assertEquals(Offset(100f, 0f), viewport.toScreen(Point(0f, 0f)))
        assertEquals(Offset(300f, 200f), viewport.toScreen(Point(1f, 1f)))
    }

    @Test
    fun pointsOutsideTheImageAreClamped() {
        val viewport = ImageViewport(400f, 200f, imageAspect = 1f)
        assertEquals(Point(0f, 1f), viewport.toImage(Offset(-50f, 999f)))
    }

    @Test
    fun confidenceColoursFollowThresholds() {
        assertEquals(OverlayColors.Good, confidenceColor(0.9f))
        assertEquals(OverlayColors.Warning, confidenceColor(0.5f))
        assertEquals(OverlayColors.Poor, confidenceColor(0.1f))
    }
}

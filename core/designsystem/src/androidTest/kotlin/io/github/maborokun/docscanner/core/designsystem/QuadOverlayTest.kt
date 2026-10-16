package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class QuadOverlayTest {
    @get:Rule
    val rule = createComposeRule()

    private val quad = Quad(Point(0.25f, 0.25f), Point(0.75f, 0.25f), Point(0.75f, 0.75f), Point(0.25f, 0.75f))

    private fun androidx.compose.ui.graphics.ImageBitmap.pixelAt(
        x: Float,
        y: Float,
    ): Int {
        val buffer = IntArray(width * height)
        readPixels(buffer)
        return buffer[(y * height).toInt() * width + (x * width).toInt()]
    }

    @Test
    fun drawsHandleAndTintedInterior() {
        rule.setContent {
            Box(Modifier.size(200.dp).background(Color.Black)) {
                QuadOverlay(quad, imageAspect = 1f, color = OverlayColors.Good)
            }
        }
        val image = rule.onNodeWithTag(QUAD_OVERLAY_TAG).captureToImage()

        assertEquals("handle centre is white", Color.White.toArgb(), image.pixelAt(0.25f, 0.25f))
        assertNotEquals("interior is tinted", Color.Black.toArgb(), image.pixelAt(0.5f, 0.5f))
        assertEquals("outside stays untouched", Color.Black.toArgb(), image.pixelAt(0.05f, 0.5f))
    }

    @Test
    fun draggingAHandleReportsTheNewQuad() {
        var current by mutableStateOf(quad)
        rule.setContent {
            Box(Modifier.size(200.dp).background(Color.Black)) {
                QuadOverlay(
                    current,
                    imageAspect = 1f,
                    color = OverlayColors.Good,
                    editable = true,
                    onQuadChange = { current = it },
                )
            }
        }
        rule.onNodeWithTag(QUAD_OVERLAY_TAG).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.25f)
            down(start)
            moveTo(Offset(width * 0.1f, height * 0.15f))
            up()
        }
        rule.runOnIdle {
            assertEquals(0.1f, current.topLeft.x, 0.03f)
            assertEquals(0.15f, current.topLeft.y, 0.03f)
            assertEquals(quad.bottomRight, current.bottomRight)
        }
    }

    @Test
    fun touchesAwayFromHandlesDoNotMoveTheQuad() {
        var current by mutableStateOf(quad)
        rule.setContent {
            Box(Modifier.size(200.dp).background(Color.Black)) {
                QuadOverlay(current, 1f, OverlayColors.Good, editable = true, onQuadChange = { current = it })
            }
        }
        rule.onNodeWithTag(QUAD_OVERLAY_TAG).performTouchInput {
            down(Offset(width * 0.5f, height * 0.5f))
            moveTo(Offset(width * 0.6f, height * 0.6f))
            up()
        }
        rule.runOnIdle { assertEquals(quad, current) }
    }
}

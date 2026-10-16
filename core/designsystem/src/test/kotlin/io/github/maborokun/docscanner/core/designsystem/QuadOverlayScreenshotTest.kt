package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Baselines live in src/test/screenshots. Re-record with
 * `./gradlew :core:designsystem:testDebugUnitTest -Proborazzi.record`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h480dp-xhdpi")
class QuadOverlayScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val quad = Quad(Point(0.18f, 0.12f), Point(0.82f, 0.2f), Point(0.78f, 0.85f), Point(0.2f, 0.8f))

    private fun shot(
        name: String,
        confidence: Float,
        labels: List<String?> = emptyList(),
    ) {
        rule.setContent {
            Box(Modifier.size(360.dp, 480.dp).background(Color(0xFF2B3140))) {
                QuadOverlay(quad, imageAspect = 0.75f, color = confidenceColor(confidence), edgeLabels = labels)
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun confident() = shot("overlay_confident", 0.95f, listOf("0.62 u", "0.71 u", "0.60 u", "0.69 u"))

    @Test
    fun unsure() = shot("overlay_unsure", 0.5f, listOf("≈ 198 mm (estimate)", null, "≈ 201 mm (estimate)", null))

    @Test
    fun poor() = shot("overlay_poor", 0.1f)
}

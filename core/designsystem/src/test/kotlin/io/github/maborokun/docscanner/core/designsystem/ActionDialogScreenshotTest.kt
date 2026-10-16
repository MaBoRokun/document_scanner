package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sheet without its window, so it can be captured. Re-record with `-Proborazzi.record`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h480dp-xhdpi")
class ActionDialogScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun deleteDialog() {
        rule.setContent {
            DocScannerTheme {
                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.BottomCenter) {
                    ActionSheet(
                        title = "Delete document?",
                        message = "“Blue Bakery” and its images will be removed from this device.",
                        actions =
                            listOf(
                                DialogAction("Delete", {}, DialogTone.Destructive),
                                DialogAction("Cancel", {}, DialogTone.Negative),
                            ),
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/dialog_delete.png")
    }
}

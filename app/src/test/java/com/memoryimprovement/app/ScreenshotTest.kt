package com.memoryimprovement.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.memoryimprovement.app.theme.ContextualHelpBottomSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captureDesignSystemSurfaceAndTypography() {
        composeTestRule.setContent {
            MemoryImprovementTheme {
                Surface {
                    Text(
                        text = "Memory Studio Design System",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        val targetFile = File("../docs/screenshots/design_system.png")
        targetFile.parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(targetFile)
    }

    @Test
    fun captureContextualHelpBottomSheet() {
        composeTestRule.setContent {
            MemoryImprovementTheme {
                Surface {
                    ContextualHelpBottomSheet(
                        title = "Essential Learning Principles",
                        description = "1. Active Recall: Retrieve facts before seeing answers.\n2. Spaced Practice: Revisit over increasing time intervals.\n3. Distinctive Associations: Form vivid visual mental cues.",
                        onDismiss = {},
                    )
                }
            }
        }
        val targetFile = File("../docs/screenshots/contextual_help_sheet.png")
        targetFile.parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(targetFile)
    }
}

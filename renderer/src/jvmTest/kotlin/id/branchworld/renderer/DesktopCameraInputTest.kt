package id.branchworld.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class DesktopCameraInputTest {
    @Test
    fun primaryAndSecondaryMouseDragsReachTheCamera() = runComposeUiTest {
        val controller = PreviewController()
        setContent {
            Box(Modifier.size(160.dp).testTag("viewport").desktopCameraInput(controller))
        }
        onNodeWithTag("viewport").performMouseInput {
            moveTo(center)
            press(MouseButton.Primary)
            moveTo(center + Offset(30f, 0f))
            release(MouseButton.Primary)
        }
        waitForIdle()
        val orbited = controller.camera.value
        assertNotEquals(PreviewCamera().yaw, orbited.yaw)
        assertEquals(0f, orbited.targetX)

        onNodeWithTag("viewport").performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
            moveTo(center + Offset(30f, 0f))
            release(MouseButton.Secondary)
        }
        waitForIdle()
        val panned = controller.camera.value
        assertEquals(orbited.yaw, panned.yaw)
        assertNotEquals(0f, panned.targetX)
    }
}

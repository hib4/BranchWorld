package id.branchworld.renderer.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import id.branchworld.renderer.camera.PreviewCamera
import id.branchworld.renderer.camera.PreviewController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class PointerCameraInputTest {
    @Test
    fun focusedViewportHandlesKeyboardOrbitPanZoomAndReset() = runComposeUiTest {
        val controller = PreviewController()
        setContent {
            Box(Modifier.size(160.dp).testTag("viewport").pointerCameraInput(controller))
        }
        val viewport = onNodeWithTag("viewport")
        viewport.performMouseInput {
            moveTo(center)
            press(MouseButton.Primary)
            release(MouseButton.Primary)
        }
        viewport.assertIsFocused()
        viewport.performKeyInput { pressKey(Key.DirectionLeft) }
        val orbit = controller.camera.value
        assertNotEquals(PreviewCamera().yaw, orbit.yaw)
        viewport.performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionUp)
            keyUp(Key.ShiftLeft)
            pressKey(Key.Equals)
        }
        val zoomed = controller.camera.value
        assertEquals(orbit.yaw, zoomed.yaw)
        assertNotEquals(orbit.targetZ, zoomed.targetZ)
        assertNotEquals(orbit.distance, zoomed.distance)
        viewport.performKeyInput { pressKey(Key.MoveHome) }
        waitForIdle()
        assertEquals(PreviewCamera(), controller.camera.value)
    }

    @Test
    fun primaryAndSecondaryMouseDragsReachTheCamera() = runComposeUiTest {
        val controller = PreviewController()
        setContent {
            Box(Modifier.size(160.dp).testTag("viewport").pointerCameraInput(controller))
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

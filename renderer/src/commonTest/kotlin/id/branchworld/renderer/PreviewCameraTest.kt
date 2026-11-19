package id.branchworld.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PreviewCameraTest {
    @Test
    fun orbitKeepsTheCameraAwayFromThePoles() {
        val camera = PreviewCamera()

        assertEquals(0.17f, camera.orbit(0f, -20f).pitch)
        assertEquals(1.35f, camera.orbit(0f, 20f).pitch)
        assertNotEquals(camera.yaw, camera.orbit(0.1f, 0f).yaw)
    }

    @Test
    fun zoomRejectsInvalidFactorsAndClampsDistance() {
        val camera = PreviewCamera()

        assertEquals(camera, camera.zoom(Float.NaN))
        assertEquals(camera, camera.zoom(0f))
        assertEquals(3f, camera.zoom(20f).distance)
        assertEquals(18f, camera.zoom(0.01f).distance)
    }

    @Test
    fun panIsBoundedAndFocusRestoresTheWholePose() {
        val controller = PreviewController()
        controller.orbit(0.25f, -0.12f)
        controller.zoom(1.4f)
        controller.pan(12f, 14f)
        val moved = controller.camera.value

        assertTrue(moved.targetX in -5f..5f && moved.targetZ in -5f..5f)
        assertNotEquals(PreviewCamera(), moved)
        controller.focus()
        assertEquals(PreviewCamera(), controller.camera.value)
    }
}

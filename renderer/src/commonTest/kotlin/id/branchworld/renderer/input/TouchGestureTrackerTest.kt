package id.branchworld.renderer.input

import id.branchworld.renderer.camera.PreviewController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TouchGestureTrackerTest {
    private val controller = PreviewController()
    private val touch = TouchGestureTracker(controller)

    @Test
    fun aFingerHoldsTheCameraUntilItLeavesTheSlop() {
        val start = controller.camera.value
        touch.press(listOf(Finger(100f, 100f)), 0)
        touch.move(listOf(Finger(105f, 103f)), 400, 400, slop = 8f)
        assertEquals(start, controller.camera.value)
        touch.move(listOf(Finger(160f, 100f)), 400, 400, slop = 8f)
        assertNotEquals(start, controller.camera.value)
    }

    @Test
    fun pinchingApartZoomsInAndAddingAFingerNeverJumps() {
        touch.press(listOf(Finger(100f, 100f)), 0)
        val before = controller.camera.value
        touch.press(listOf(Finger(100f, 100f), Finger(200f, 100f)), 10)
        assertEquals(before, controller.camera.value)
        touch.move(listOf(Finger(80f, 100f), Finger(220f, 100f)), 400, 400, slop = 8f)
        assertTrue(controller.camera.value.distance < before.distance)
        val pinched = controller.camera.value
        touch.release(Finger(220f, 100f), listOf(Finger(80f, 100f)), 30, 400, 400, 24f)
        assertEquals(pinched, controller.camera.value)
    }

    @Test
    fun aCancelledTouchTurnsNothing() {
        val start = controller.camera.value
        touch.press(listOf(Finger(10f, 10f)), 0)
        touch.cancel()
        touch.release(Finger(10f, 10f), emptyList(), 50, 400, 400, 24f)
        assertEquals(start, controller.camera.value)
    }
}

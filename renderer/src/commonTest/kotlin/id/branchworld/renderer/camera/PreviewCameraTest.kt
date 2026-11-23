package id.branchworld.renderer.camera

import androidx.compose.runtime.MonotonicFrameClock
import id.branchworld.simulation.core.CreatureHeading
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.projection.CreaturePose
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.PropKind
import id.branchworld.simulation.terrain.TerrainKey
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PreviewCameraTest {
    @Test
    fun creatureFocusMovesTheInitialCameraOutFromBehindAForegroundTree() {
        val terrain = IslandGenerator.generate(TerrainKey(104729L))
        val pine = (0 until terrain.propCount).map(terrain::prop).first { prop ->
            prop.kind == PropKind.Pine && terrain.cellAt(prop.x, prop.z - 2)?.walkable == true
        }
        val cell = checkNotNull(terrain.cellAt(pine.x, pine.z - 2))
        val deer = CreaturePose(5, Species.Deer, GroundPoint(pine.x, pine.z - 2, cell.elevationTwice),
            CreatureHeading.PositiveZ, CreatureAction.Idle)
        val selected = creatureViewYaw(terrain, deer, PreviewCamera(yaw = 0f))
        assertTrue(abs(selected) >= 0.55f)
    }

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
    fun panIsBoundedAndFocusRestoresTheWholePose() = runTest {
        val frameClock = object : MonotonicFrameClock {
            private var time = 0L

            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                time += 16_666_667L
                return onFrame(time)
            }
        }
        val controller = PreviewController()
        controller.orbit(0.25f, -0.12f)
        controller.zoom(1.4f)
        controller.pan(12f, 14f)
        val moved = controller.camera.value

        assertTrue(moved.targetX in -5f..5f && moved.targetZ in -5f..5f)
        assertNotEquals(PreviewCamera(), moved)
        withContext(frameClock) { controller.focus(this).join() }
        assertEquals(PreviewCamera(), controller.camera.value)
    }
}

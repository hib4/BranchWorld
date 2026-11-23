package id.branchworld.renderer.camera

import id.branchworld.renderer.geometry.CellSize
import id.branchworld.renderer.geometry.Point3
import id.branchworld.renderer.geometry.heightAt
import id.branchworld.renderer.scene.cellPosition
import id.branchworld.simulation.core.CreatureHeading
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.projection.CreaturePose
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.terrain.Biome
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import id.branchworld.simulation.terrain.TerrainMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A tap on the drawn island lands on the cell the player sees, from any reasonable camera. No GPU needed. */
class GroundPickingTest {
    private val terrain = IslandGenerator.generate(TerrainKey(271828, 2))
    private val width = 1280f
    private val height = 800f

    @Test
    fun theCentreOfTheViewIsTheCameraTarget() {
        val camera = heroCamera(width / height, opening = false, terrain).copy(targetY = 0f)
        val hit = terrain.raycastGround(camera.viewRay(width / 2, height / 2, width, height))!!
        val surface = terrain.surfaceAt(camera.targetX, camera.targetZ)
        // The ray meets the ground near where the camera looks; a hill in front may catch it a little early.
        assertTrue(kotlin.math.abs(hit.point.x - camera.targetX) < 0.8f && kotlin.math.abs(hit.point.z - camera.targetZ) < 0.8f,
            "hit $hit target ${camera.targetX},${camera.targetZ} surface $surface")
    }

    @Test
    fun aCellDrawnOnScreenIsTheCellAClickThereFinds() {
        val cameras = listOf(
            heroCamera(width / height, opening = false, terrain),
            PreviewCamera(yaw = 2.1f, pitch = 0.55f, distance = 6f),
            PreviewCamera(yaw = -1.2f, pitch = 1.1f, distance = 9f, targetX = 1f, targetZ = -1f),
        )
        for (camera in cameras) {
            var matched = 0
            var tried = 0
            for (z in -24..24 step 3) for (x in -24..24 step 3) {
                val cell = terrain.cellAt(x, z) ?: continue
                if (!cell.walkable || cell.biome == Biome.Water) continue
                val center = cellPosition(x, z, cell.elevationTwice)
                val point = Point3(center.x, terrain.heightAt(center.x, center.z), center.z)
                val (sx, sy) = camera.screenPoint(point, width, height) ?: continue
                if (sx !in 0f..width || sy !in 0f..height) continue
                // Only cells the camera can actually see: a hill in front hides what lies behind it.
                val eye = camera.eye()
                val sight = terrain.raycastGround(Ray(eye, (point - eye).norm())) ?: continue
                val gap = sight.point - point
                if (gap dot gap > (CellSize * 0.75f) * (CellSize * 0.75f)) continue
                tried++
                val pick = pickWorld(camera, width, height, sx, sy, terrain, emptyList(), 16f)
                val ground = pick.ground ?: continue
                // Hills can hide a cell's centre behind the slope in front of it; the neighbour is what is drawn there.
                if (kotlin.math.abs(ground.x - x) <= 1 && kotlin.math.abs(ground.z - z) <= 1) matched++
                // A visible cell's own pixel finds that cell or a neighbour on its shared edge.
            }
            assertTrue(tried > 20, "too few visible cells: $tried")
            assertTrue(matched >= tried * 95 / 100, "camera $camera matched $matched of $tried")
        }
    }

    @Test
    fun theSkyAndTheSeaAreNotLand() {
        val camera = heroCamera(width / height, opening = false, terrain)
        assertNull(pickWorld(camera, width, height, width / 2, 2f, terrain, emptyList(), 16f).ground)
        val sea = (TerrainMap.MinCell..TerrainMap.MinCell + TerrainMap.Size - 1).firstNotNullOf { x ->
            terrain.cellAt(x, 0)?.takeIf { it.biome == Biome.Water }?.let { x }
        }
        val center = cellPosition(sea, 0, 0)
        val (sx, sy) = camera.screenPoint(Point3(center.x, 0f, center.z), width, height)!!
        val pick = pickWorld(camera, width, height, sx, sy, terrain, emptyList(), 16f)
        assertNull(pick.ground)
        assertTrue(pick.water)
    }

    @Test
    fun theNearestDrawnCreatureWinsWithinReach() {
        val camera = heroCamera(width / height, opening = false, terrain)
        fun pose(id: Long, x: Int, z: Int) = CreaturePose(id, Species.Rabbit, GroundPoint(x, z, terrain.cellAt(x, z)!!.elevationTwice),
            CreatureHeading.PositiveZ, CreatureAction.Idle)
        val near = pose(1, -2, 0)
        val other = pose(2, 4, 2)
        val center = cellPosition(-2, 0, near.ground.elevationTwice)
        val (sx, sy) = camera.screenPoint(Point3(center.x, center.y + 0.08f, center.z), width, height)!!
        assertEquals(1L, pickWorld(camera, width, height, sx + 3f, sy, terrain, listOf(other, near), 16f).creatureId)
        assertNull(pickWorld(camera, width, height, sx + 200f, sy, terrain, listOf(near), 16f).creatureId)
    }
}

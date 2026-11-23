package id.branchworld.renderer.motion

import id.branchworld.renderer.geometry.CellSize
import id.branchworld.renderer.geometry.cellPosition
import id.branchworld.renderer.geometry.heightAt
import id.branchworld.simulation.core.CreatureHeading
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.projection.CreaturePose
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shared creature motion every renderer backend draws animals with. */
class CreatureMotionTest {
    private val terrain = IslandGenerator.generate(TerrainKey(104729L))

    private fun landCell(): GroundPoint {
        for (z in -10..10) for (x in -10..10) {
            val cell = terrain.cellAt(x, z) ?: continue
            if (cell.elevationTwice > 2 && terrain.cellAt(x + 1, z)?.let { it.elevationTwice > 2 } == true) {
                return GroundPoint(x, z, cell.elevationTwice)
            }
        }
        error("No land near the centre")
    }

    private fun pose(ground: GroundPoint, action: CreatureAction) =
        CreaturePose(7L, Species.Rabbit, ground, CreatureHeading.PositiveX, action, destination = ground)

    @Test
    fun glidesAcrossOneCellAtTheRulesPaceAndStandsOnTheGround() {
        val start = landCell()
        val next = GroundPoint(start.x + 1, start.z, terrain.cellAt(start.x + 1, start.z)!!.elevationTwice)
        val motion = CreatureMotion(Species.Rabbit, terrain) { 1f }
        motion.retarget(0, pose(start, CreatureAction.Idle), interpolate = false, reducedMotion = false, nearVillage = 1f, dropIn = false)
        assertEquals(cellPosition(start.x, start.z, start.elevationTwice).x, motion.x)
        assertEquals("idle", motion.clip)
        motion.retarget(1, pose(next, CreatureAction.Walking), interpolate = true, reducedMotion = false, nearVillage = 1f, dropIn = false)
        assertEquals("walk", motion.clip)
        // A rabbit walks half a cell per tick: at 20 ticks a second, a cell takes a tenth of a second.
        repeat(5) { motion.animate(0.01f, 20f, reducedMotion = false) }
        val target = cellPosition(next.x, next.z, next.elevationTwice)
        val halfway = hypot(target.x - motion.x, target.z - motion.z)
        assertTrue(halfway in CellSize * 0.4f..CellSize * 0.6f, "Half a cell left after half the time: $halfway")
        repeat(20) { motion.animate(0.01f, 20f, reducedMotion = false) }
        assertTrue(abs(target.x - motion.x) < 1e-4f)
        assertEquals(terrain.heightAt(motion.x, motion.z), motion.y)
        assertEquals(90f, motion.yaw, 0.5f)
    }

    @Test
    fun reducedMotionJumpsStraightToTheCellAndHoldsTheClip() {
        val start = landCell()
        val motion = CreatureMotion(Species.Wolf, terrain) { 2f }
        motion.retarget(3, pose(start, CreatureAction.Chasing), interpolate = false, reducedMotion = true, nearVillage = 1f, dropIn = false)
        assertEquals("run", motion.clip)
        motion.animate(0.5f, 20f, reducedMotion = true)
        assertEquals(0f, motion.clipPhase)
    }

    @Test
    fun villagesShrinkNearbyWildlifeSmoothly() {
        val ground = GroundPoint(0, 0, 4)
        val pose = pose(ground, CreatureAction.Idle)
        assertEquals(1f, villageScale(pose, emptyList()))
        assertEquals(0.58f, villageScale(pose, listOf(GroundPoint(1, 0, 4))), 1e-6f)
        assertEquals(1f, villageScale(pose, listOf(GroundPoint(9, 0, 4))), 1e-6f)
    }
}

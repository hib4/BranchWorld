package id.branchworld.game.simulation

import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.EmberSeed
import id.branchworld.game.ecology.FireRulesVersion
import id.branchworld.game.ecology.PlanOption
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion
import id.branchworld.game.timeline.Timeline
import id.branchworld.game.runSimulationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmberEchoTest {
    @Test
    fun theBurnedAndSavedHistoriesShowTheirDifferentGroundTreesAndHomes() = runSimulationTest {
        val terrain = IslandGenerator.generate(TerrainKey(EmberSeed, WatershedWorldgenVersion))
        val timeline = Timeline.start(terrain, FireRulesVersion, Chapter.EmberSeason)
        while (timeline.state.shaping?.warningAt == null) timeline.advance()
        val forkAt = timeline.state.tick
        while (timeline.state.tick < 700) timeline.advance()
        val saved = timeline.state

        timeline.show(checkNotNull(timeline.seek(1, forkAt)).replay())
        assertTrue(timeline.fork("Keep the grove"))
        assertTrue(timeline.endorse(PlanOption.KeepGrove))
        while (timeline.state.tick < 700) timeline.advance()
        val burned = timeline.state

        val echo = livingEcho(terrain, burned, saved).sites
        assertTrue(echo.any { it.kind == EchoKind.Burned })
        assertTrue(echo.any { it.kind == EchoKind.Stand })
        assertTrue(echo.any { it.kind == EchoKind.Homes && it.here == -1 && it.other >= 2 })
        assertEquals(echo.map { it.copy(here = it.other, other = it.here) }, livingEcho(terrain, saved, burned).sites)
        assertTrue(livingEcho(terrain, burned, burned).sites.isEmpty())
    }
}

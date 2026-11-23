package id.branchworld.simulation.ecology

import id.branchworld.simulation.core.FireRulesVersion
import id.branchworld.simulation.core.RangeRulesVersion
import id.branchworld.simulation.core.VillageRulesVersion
import id.branchworld.simulation.core.WatershedWorldgenVersion
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.chapters.CrossingSeed
import id.branchworld.simulation.ecology.chapters.EmberSeed
import id.branchworld.simulation.ecology.chapters.FadingGroveSeed
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import id.branchworld.simulation.timeline.Timeline
import java.io.File
import kotlin.test.Test

/** Retained heap of one island's live history, for the Android memory investigation. Runs only on request. */
class MemoryProbe {
    private fun used(): Long {
        repeat(4) { System.gc(); Thread.sleep(60) }
        return Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    }

    @Test
    fun probe() {
        val root = File(System.getProperty("user.dir")).parentFile
        if (!File(root, "build/verification/living-worlds-09/memory.txt").exists()) return
        for ((name, seed, rules, chapter) in listOf(
            Quad("grove", FadingGroveSeed, VillageRulesVersion, null),
            Quad("crossing", CrossingSeed, RangeRulesVersion, Chapter.LastCrossing),
            Quad("ember", EmberSeed, FireRulesVersion, Chapter.EmberSeason),
        )) {
            val before = used()
            val terrain = IslandGenerator.generate(TerrainKey(seed, WatershedWorldgenVersion))
            val afterTerrain = used()
            val timeline = Timeline.start(terrain, rules, chapter)
            while (timeline.state.tick < 1200) timeline.advance()
            val after = used()
            println("MEMORY $name terrainKB=${(afterTerrain - before) / 1024} timelineKB=${(after - afterTerrain) / 1024} total=${(after - before) / 1024}")
            check(timeline.state.tick == 1200L)
        }
    }

    private data class Quad(val name: String, val seed: Long, val rules: Int, val chapter: Chapter?)
}

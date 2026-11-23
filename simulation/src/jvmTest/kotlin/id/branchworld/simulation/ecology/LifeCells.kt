package id.branchworld.simulation.ecology

import id.branchworld.simulation.core.WatershedWorldgenVersion
import id.branchworld.simulation.ecology.chapters.CrossingSeed
import id.branchworld.simulation.ecology.chapters.EmberSeed
import id.branchworld.simulation.ecology.chapters.FadingGroveSeed
import id.branchworld.simulation.ecology.life.octile
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import java.io.File
import kotlin.test.Test

/** Exploration only: prints cells for capture steps when build/verification/living-sandbox-10/cells.txt exists. */
class LifeCells {
    @Test
    fun cells() {
        if (!File("../build/verification/living-sandbox-10/cells.txt").exists()) return
        for (seed in listOf(CrossingSeed, FadingGroveSeed, EmberSeed)) {
            val island = IslandEcology(IslandGenerator.generate(TerrainKey(seed, WatershedWorldgenVersion)))
            val spawn = island.navigation.spawn
            val meadow = island.roamCells(Species.Rabbit).sortedBy { octile(it, spawn) }.filterIndexed { i, _ -> i % 5 == 0 }.take(8)
            val far = island.reachable.filter { octile(it, spawn) in 60..90 }.sortedBy { octile(it, spawn) * 1000 + cellId(it) }
            println("CELLS $seed spawn=$spawn meadow=${meadow.map { "${it.x} ${it.z}" }} near=${far.take(12).filterIndexed { i, _ -> i % 4 == 0 }.map { "${it.x} ${it.z}" }}")
        }
    }
}

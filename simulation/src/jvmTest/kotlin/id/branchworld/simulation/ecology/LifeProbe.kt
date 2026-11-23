package id.branchworld.simulation.ecology

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.core.LifeRulesVersion
import id.branchworld.simulation.core.WatershedWorldgenVersion
import id.branchworld.simulation.ecology.chapters.CrossingSeed
import id.branchworld.simulation.ecology.chapters.EmberSeed
import id.branchworld.simulation.ecology.chapters.FadingGroveSeed
import id.branchworld.simulation.ecology.life.octile
import id.branchworld.simulation.ecology.wildlife.WildlifeMilestone
import id.branchworld.simulation.engine.EcologyEngine
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import java.io.File
import kotlin.test.Test
import kotlin.time.TimeSource

/** Exploration only: runs when build/verification/living-sandbox-10/probe.txt exists. Prints what rabbits and wolves do. */
class LifeProbe {
    @Test
    fun probe() {
        val request = File("../build/verification/living-sandbox-10/probe.txt")
        if (!request.exists()) return
        val ticks = request.readText().trim().toLongOrNull() ?: 6000L
        for (seed in listOf(FadingGroveSeed, CrossingSeed, EmberSeed)) {
            val terrain = IslandGenerator.generate(TerrainKey(seed, WatershedWorldgenVersion))
            val island = IslandEcology(terrain)
            val engine = EcologyEngine(terrain, rulesVersion = LifeRulesVersion, island = island)
            val spawn = island.navigation.spawn
            val meadow = island.roamCells(Species.Rabbit).sortedBy { octile(it, spawn) }
            val far = island.reachable.filter { octile(it, spawn) in 110..140 }.sortedBy { octile(it, spawn) * 1000 + cellId(it) }
            meadow.filterIndexed { i, _ -> i % 5 == 0 }.take(6).forEach { check(engine.place(Species.Rabbit, it)) }
            far.take(3).forEachIndexed { i, cell -> check(engine.place(Species.Wolf, far[i * 3])) }
            val samples = LongArray(ticks.toInt())
            val log = StringBuilder()
            var seen = 0
            val causes = sortedMapOf<String, Int>()
            val trail = HashMap<Long, ArrayDeque<GridCell>>()
            var stuckShown = 0
            for (i in 0 until ticks.toInt()) {
                val before = engine.state.creatures.associateBy { it.id }
                val mark = TimeSource.Monotonic.markNow()
                val state = engine.advance()
                samples[i] = mark.elapsedNow().inWholeMicroseconds
                val events = state.chronicle!!.events
                for (event in events.filter { it.id > seen }) {
                    log.append("  t${state.tick} ${event.kind} s=${event.subject} d=${event.detail}\n")
                }
                seen = events.maxOfOrNull { it.id } ?: seen
                for (e in state.wildlife!!.events) if (e.tick == state.tick && e.kind != WildlifeMilestone.Birth && e.kind != WildlifeMilestone.Placed) {
                    causes.merge("${e.species}-${e.kind}", 1, Int::plus)
                    if (e.kind == WildlifeMilestone.Scarcity) before[e.id]?.let { b ->
                        val why = if (b.water <= 1) "thirst" else "hunger"
                        causes.merge("${e.species}-$why-${b.action}-${b.goal?.need}", 1, Int::plus)
                        val t = trail[e.id].orEmpty()
                        if (stuckShown++ < 6) log.append("    dead ${e.id} at ${b.cell} goal=${b.goal} distinct=${t.toSet().size} stride=${b.life?.stride} thr=${b.life?.threat} last=${t.takeLast(6)}\n")
                    }
                }
                if (state.tick % 600 == 0L) {
                    val c = Species.entries.associateWith { s -> state.creatures.count { it.species == s } }
                    val acts = state.creatures.groupingBy { it.action }.eachCount()
                    log.append("t${state.tick} catches=${state.wildlife!!.catches} $c packs=${state.life!!.packs.size} warrens=${state.life!!.warrens.size} acts=$acts\n")
                }
                if (state.tick % 300 == 0L) engine.validateSavedState()
                for (c in state.creatures) trail.getOrPut(c.id) { ArrayDeque() }.apply { addLast(c.cell); if (size > 120) removeFirst() }
            }
            samples.sort()
            log.append("deaths $causes\n")
            println("SEED $seed p50=${samples[samples.size / 2]}us p95=${samples[samples.size * 95 / 100]}us max=${samples.last()}us\n$log")
        }
    }
}

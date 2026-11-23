package id.branchworld.simulation.ecology

import id.branchworld.simulation.core.FireRulesVersion
import id.branchworld.simulation.core.WatershedWorldgenVersion
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.chapters.EmberSeed
import id.branchworld.simulation.ecology.fire.FireRules
import id.branchworld.simulation.ecology.fire.ScarKind
import id.branchworld.simulation.ecology.fire.TileCount
import id.branchworld.simulation.ecology.fire.TileGrid
import id.branchworld.simulation.ecology.fire.tileCell
import id.branchworld.simulation.ecology.fire.tileOf
import id.branchworld.simulation.ecology.settlement.PlanOption
import id.branchworld.simulation.ecology.settlement.StructureKind
import id.branchworld.simulation.ecology.settlement.VillageEventKind
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import id.branchworld.simulation.timeline.ChapterOutcome
import id.branchworld.simulation.timeline.Timeline
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

/** Experiments E2/E3: one Ember Season warning, four answers. Runs only on request. */
class EmberExperiment {
    @Test
    fun experiment(): Unit = runBlocking {
        val root = File(System.getProperty("user.dir")).parentFile
        if (!File(root, "build/verification/living-worlds-09/experiment.txt").exists()) return@runBlocking
        val horizon = File(root, "build/verification/living-worlds-09/experiment.txt").readText().trim().toLongOrNull() ?: 4800L
        val terrain = IslandGenerator.generate(TerrainKey(EmberSeed, WatershedWorldgenVersion))
        val timeline = Timeline.start(terrain, FireRulesVersion, Chapter.EmberSeason)
        while (timeline.state.shaping?.warningAt == null && timeline.state.tick < 3000) timeline.advance()
        val warning = timeline.state.tick
        val fire0 = timeline.state.fire!!
        println("WARNING at $warning site=${timeline.state.settlements!!.sites.map { it.center }} strikes=${fire0.strikes.map { tileCell(it.tile) to tileCell(it.tile).region() }} wind=${fire0.wind}")
        println("village=${timeline.state.settlements!!.sites.map { it.center }} petition=${timeline.openPetition()}")
        val mainId = timeline.branchId
        val fireRules = FireRules(IslandEcology(terrain))
        val strikeRegion = tileCell(fire0.strikes.first().tile).region()
        val villageRegion = timeline.state.settlements!!.sites.first().center.region()
        for (region in 0 until RegionCount) {
            val tiles = fireRules.firebreakTiles(region, fire0)
            if (tiles.isNotEmpty()) println("firebreak r$region tiles=${tiles.size} from ${tileCell(tiles.first())} to ${tileCell(tiles.last())}")
        }
        val breakRegion = System.getProperty("breakRegion")?.toInt()
            ?: File(root, "build/verification/living-worlds-09/break.txt").takeIf { it.exists() }?.readText()?.trim()?.toInt() ?: villageRegion
        data class Variant(val name: String, val act: (Timeline) -> Unit)
        val variants = listOf(
            Variant("A village decides") { },
            Variant("B keep + firebreak r$breakRegion") { it.endorse(PlanOption.KeepGrove); check(it.intervene(Intervention.Firebreak, breakRegion)) { "firebreak refused ${it.preview(Intervention.Firebreak, breakRegion)}" } },
            Variant("C keep grove") { it.endorse(PlanOption.KeepGrove) },
            Variant("D keep + rain r$strikeRegion") { it.endorse(PlanOption.KeepGrove); check(it.intervene(Intervention.Rain, strikeRegion)) },
        )
        for (variant in variants) {
            timeline.show(checkNotNull(timeline.seek(mainId, warning)).replay())
            check(timeline.fork(variant.name.take(24)))
            variant.act(timeline)
            var lastBurning = -1
            while (timeline.state.tick < horizon) {
                timeline.advance()
                val f = timeline.state.fire!!
                if (timeline.state.tick % 10 == 0L && f.burning.size != lastBurning && timeline.state.tick < warning + 900) {
                    lastBurning = f.burning.size
                    print("${timeline.state.tick}:${f.burning.size} ")
                }
            }
            println()
            val s = timeline.state
            val f = s.fire!!
            val out = ChapterOutcome.of(timeline.summaries().first { it.id == timeline.branchId }, s)
            val forestTiles = (0 until TileCount).count { t -> fireRules.landIn(t).count { terrain.terrainCell(it).biome == id.branchworld.simulation.terrain.Biome.PineForest } >= 2 }
            val burnedForest = f.scars.count { sc -> sc.kind == ScarKind.Burned && fireRules.landIn(sc.tile).count { terrain.terrainCell(it).biome == id.branchworld.simulation.terrain.Biome.PineForest } >= 2 }
            println("== ${variant.name}: fires=${f.fires} tilesBurned=${f.tilesBurned} forest=$burnedForest/$forestTiles homesBurned=${f.homesBurned} felled=${out.pinesFelled} pinesBurned=${out.pinesBurned} " +
                "people=${out.people} homes=${out.homes} left=${out.departed} deer=${out.deer} rabbits=${out.rabbits} foxes=${out.foxes} plan=${out.emberPlan} canClose=${timeline.canCloseChapter} hash=${s.canonicalHash()}")
            println("   village: " + s.settlements!!.village!!.chronicle.filter { it.kind in setOf(VillageEventKind.HouseholdLeft, VillageEventKind.Thirst, VillageEventKind.Hunger, VillageEventKind.HomeBurned, VillageEventKind.HouseholdArrived, VillageEventKind.WorkCompleted) }.joinToString { "${it.tick} ${it.kind}(${it.detail})" })
            println("   chronicle: " + s.chronicle!!.events.joinToString(" | ") { "${it.tick} ${it.kind}(${it.subject},${it.detail})<-${it.causes.map { c -> c.kind }}" })
            // Burn map
            val sb = StringBuilder()
            for (tz in 0 until TileGrid) {
                for (tx in 0 until TileGrid) {
                    val t = tz * TileGrid + tx
                    val c = tileCell(t)
                    val scar = f.scarAt(t)
                    val cell = terrain.cellAt(c.x, c.z)!!
                    sb.append(when {
                        s.settlements!!.village!!.structures.any { tileOf(it.cell) == t && it.kind == StructureKind.Home } -> if (s.settlements!!.village!!.structures.any { tileOf(it.cell) == t && it.burnedAt != null }) 'X' else 'H'
                        scar?.kind == ScarKind.Burned -> '#'
                        scar?.kind == ScarKind.Felled -> '='
                        !cell.land -> '~'
                        fireRules.burnable(t) -> if (fireRules.landIn(t).count { terrain.terrainCell(it).biome == id.branchworld.simulation.terrain.Biome.PineForest } >= 2) 'f' else '.'
                        else -> '^'
                    })
                }
                sb.append('\n')
            }
            println(sb)
        }
    }
}

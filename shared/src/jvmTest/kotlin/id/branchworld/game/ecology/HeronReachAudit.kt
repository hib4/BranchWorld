package id.branchworld.game.ecology

import id.branchworld.game.simulation.GridCell
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.PropKind
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.terrain.WatershedWorldgenVersion
import id.branchworld.game.timeline.Timeline
import java.io.File
import kotlin.test.Test

/** Experiment E1: what fuel, moisture and village layout Heron Reach offers a fire. Runs only on request. */
class HeronReachAudit {
    @Test
    fun audit() {
        val root = File(System.getProperty("user.dir")).parentFile
        if (!File(root, "build/verification/living-worlds-09/audit.txt").exists()) return
        for (seed in listOf(424242L, 271828L, 104729L)) {
            val terrain = IslandGenerator.generate(TerrainKey(seed, WatershedWorldgenVersion))
            val island = IslandEcology(terrain)
            println("=== seed $seed landscape=${terrain.watershed?.landscape}")
            val pines = (0 until terrain.propCount).map(terrain::prop).filter { it.kind == PropKind.Pine }
            println("pines=${pines.size}")
            for (region in 0 until RegionCount) {
                val cells = island.reachable.filter { it.region() == region }
                if (cells.isEmpty()) continue
                val biomes = cells.groupingBy { terrain.terrainCell(it).biome }.eachCount()
                val p = pines.count { GridCell(it.x, it.z).region() == region }
                val moist16 = cells.map { terrain.soilMoisture(it, 16) }.sorted()
                val moist64 = cells.map { terrain.soilMoisture(it, 64) }.sorted()
                println("r$region cells=${cells.size} pines=$p ${biomes.entries.joinToString { "${it.key}=${it.value}" }} " +
                    "m16 p10/50/90=${moist16[moist16.size / 10]}/${moist16[moist16.size / 2]}/${moist16[moist16.size * 9 / 10]} " +
                    "m64=${moist64[moist64.size / 2]}")
            }
            val timeline = Timeline.start(terrain, RangeRulesVersion)
            var lastWeather: Weather? = null
            var low = 0L
            while (timeline.state.tick < 9600) {
                timeline.advance()
                val env = timeline.state.environment!!
                if (env.weather != lastWeather) {
                    println("t=${timeline.state.tick} ${seasonAt(timeline.state.tick)} ${env.weather} recharge=${env.recharge} until=${env.nextChange}")
                    lastWeather = env.weather
                }
                if (env.recharge!! <= 24 && timeline.state.tick - low > 300) {
                    low = timeline.state.tick
                    println("  low recharge ${env.recharge} at ${timeline.state.tick} (day-phase ${timeline.state.tick % 2400})")
                }
            }
            val sites = timeline.state.settlements!!.sites
            for (site in sites) println("site ${site.id} center=${site.center} region=${site.center.region()} field=${site.field} lots=${site.lots}")
            val village = timeline.state.settlements!!.village!!
            println("households=${village.households.count { it.active }} homes=${village.structures.filter { it.kind == StructureKind.Home }.map { it.cell }}")
            val center = sites.first().center
            val nearPines = pines.filter { kotlin.math.abs(it.x - center.x) + kotlin.math.abs(it.z - center.z) <= 10 }
            println("pines within 10 of village: ${nearPines.size} nearest=${pines.minOfOrNull { kotlin.math.abs(it.x - center.x) + kotlin.math.abs(it.z - center.z) }}")
            val map = StringBuilder()
            for (z in TerrainMap.MinCell..31) {
                for (x in TerrainMap.MinCell..31) {
                    val cell = terrain.cellAt(x, z)!!
                    val c = GridCell(x, z)
                    map.append(when {
                        c == center -> '@'
                        village.structures.any { it.cell == c } -> 'H'
                        pines.any { it.x == x && it.z == z } -> 'P'
                        !cell.land -> '~'
                        cell.biome == Biome.PineForest -> 'f'
                        cell.biome == Biome.Meadow -> '.'
                        cell.biome == Biome.Wetland -> 'w'
                        cell.biome == Biome.RockyUpland -> '^'
                        cell.biome == Biome.Beach -> ','
                        else -> '?'
                    })
                }
                map.append('\n')
            }
            println(map)
        }
    }
}

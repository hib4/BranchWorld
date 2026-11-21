package id.branchworld.game.terrain

import id.branchworld.game.IslandSeeds
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.game.terrain.TerrainMap.Companion.MaxCorner
import id.branchworld.game.terrain.TerrainMap.Companion.MinCell
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IslandGeneratorTest {
    @Test
    fun terrainAndPropsRepeatForEachVersionedSeed() {
        for ((seed, digest) in TerrainDigests) {
            val map = IslandGenerator.generate(TerrainKey(seed))
            val repeat = IslandGenerator.generate(map.key)
            assertEquals(digest, map.digest, "seed=$seed")
            assertEquals(map.digest, repeat.digest)
            assertEquals(map.propCount, repeat.propCount)
            for (index in 0 until map.propCount) assertEquals(map.prop(index), repeat.prop(index))
            for (z in MinCell..MaxCorner) {
                for (x in MinCell..MaxCorner) {
                    assertEquals(map.cornerElevation(x, z), repeat.cornerElevation(x, z))
                    assertEquals(map.cellAt(x, z), repeat.cellAt(x, z))
                }
            }
        }
        assertFailsWith<IllegalArgumentException> { IslandGenerator.generate(TerrainKey(104729, 3)) }
    }

    @Test
    fun presetIslandsHaveDifferentCoastlinesAndUsableBiomes() {
        val maps = IslandSeeds.map { IslandGenerator.generate(TerrainKey(it)) }
        val land = maps.map { map ->
            val biomes = mutableSetOf<Biome>()
            val landCells = mutableSetOf<GridCell>()
            for (i in MinCell..MaxCorner) {
                assertTrue(map.cornerElevation(MinCell, i) < 0 && map.cornerElevation(MaxCorner, i) < 0)
                assertTrue(map.cornerElevation(i, MinCell) < 0 && map.cornerElevation(i, MaxCorner) < 0)
            }
            for (z in MinCell..MaxCell) {
                for (x in MinCell..MaxCell) {
                    val cell = assertNotNull(map.cellAt(x, z))
                    val corners = listOf(
                        map.cornerElevation(x, z), map.cornerElevation(x + 1, z),
                        map.cornerElevation(x, z + 1), map.cornerElevation(x + 1, z + 1),
                    )
                    assertTrue(corners.all { it in TerrainMap.MinElevation..TerrainMap.MaxElevation })
                    assertTrue(cell.moisture in 0..255 && cell.temperature in 0..255)
                    assertEquals(corners.first() + corners.last(), cell.elevationTwice)
                    assertEquals(corners.min() > 0, cell.land)
                    assertEquals(corners.max() <= 0, cell.biome == Biome.Water)
                    if (cell.walkable) assertTrue(cell.land && !cell.occupied && corners.max() - corners.min() <= 8)
                    if (cell.land) landCells.add(GridCell(x, z))
                    biomes.add(cell.biome)
                }
            }
            assertEquals(Biome.entries.filter { it != Biome.Wetland }.toSet(), biomes, "seed=${map.key.seed}")
            assertTrue(landCells.size in 600..2300)
            assertNull(map.cellAt(Int.MIN_VALUE, 0))
            assertFalse(map.canStep(0, 0, Int.MAX_VALUE, 0))
            landCells
        }
        for (i in land.indices) {
            for (j in i + 1 until land.size) {
                val union = land[i] union land[j]
                val overlap = land[i] intersect land[j]
                assertTrue((union.size - overlap.size) * 100 >= union.size * 15, "coastlines $i and $j are too similar")
            }
        }
    }

    @Test
    fun propsFitTheirBiomesAndNavigationAvoidsThem() {
        for (seed in IslandSeeds) {
            val map = IslandGenerator.generate(TerrainKey(seed))
            val props = (0 until map.propCount).map(map::prop)
            assertEquals(PropKind.entries.toSet(), props.map { it.kind }.toSet())
            for ((index, prop) in props.withIndex()) {
                val cell = assertNotNull(map.cellAt(prop.x, prop.z))
                assertEquals(if (prop.kind == PropKind.Pine) Biome.PineForest else Biome.RockyUpland, cell.biome)
                assertTrue(cell.land && cell.occupied && !cell.walkable)
                assertTrue(prop.yawDegrees in 0..359 && prop.scalePercent in 70..120)
                assertTrue(props.take(index).all { abs(prop.x - it.x) + abs(prop.z - it.z) >= 4 })
            }
            val route = TerrainNavigation(map)
            for (target in listOf(assertNotNull(route.west), assertNotNull(route.east))) {
                val path = route.distancesTo(target)
                var cell = route.spawn
                repeat(path[TerrainMap.cellIndex(cell.x, cell.z)]) {
                    val next = route.step(cell, path)
                    assertTrue(map.canStep(cell.x, cell.z, next.x, next.z))
                    cell = next
                }
                assertEquals(target, cell)
            }
        }
    }

    @Test
    fun extremeAndOrdinarySeedsYieldPlayableMaps() {
        for (seed in listOf(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 0L, 1L, 7L, 19L, 123456789L)) {
            val navigation = TerrainNavigation(IslandGenerator.generate(TerrainKey(seed)))
            assertTrue(navigation.contains(navigation.spawn))
            assertTrue(assertNotNull(navigation.west).x < navigation.spawn.x)
            assertTrue(assertNotNull(navigation.east).x > navigation.spawn.x)
        }
    }

    private companion object {
        val TerrainDigests = mapOf(
            104729L to 0xcb401f464692a079uL,
            271828L to 0xf1cc9009a8937ff7uL,
            424242L to 0x98e715eca2a3de11uL,
        )
    }
}

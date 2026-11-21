package id.branchworld.renderer

import id.branchworld.game.ecology.Weather
import id.branchworld.game.simulation.ClimatePose
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.game.terrain.TerrainMap.Companion.MinCell
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IslandMeshTest {
    @Test
    fun lightReadsTheDisplayedClimateAndRewindDiscardsTheCurrentWeather() {
        val morning = ClimatePose(1, true, Weather.Dry, 0.50f, 0f)
        val rain = ClimatePose(1, true, Weather.Rain, 1f, 330f / 1200f)
        val dusk = ClimatePose(1, false, Weather.Dry, 0.125f, 645f / 1200f)
        val opening = coastalLight(morning, opening = true)
        val shower = coastalLight(rain, opening = false)
        val evening = coastalLight(dusk, opening = false)
        assertTrue(shower.rain && shower.haze != opening.haze)
        assertTrue(evening.twilight > 0.8f && evening.sun < 0.2f)
        assertTrue(evening.direction.y > opening.direction.y)
        assertEquals(opening, coastalLight(morning, opening = true))
        assertTrue(coastalLight(morning, opening = false).sun < opening.sun)
    }

    @Test
    fun chunksHaveUpwardTrianglesAndShareExactCornerHeights() {
        for (seed in listOf(104729L, 271828L, 424242L)) {
            val terrain = IslandGenerator.generate(TerrainKey(seed))
            val corners = mutableMapOf<Pair<Float, Float>, Float>()
            var mixedShoreFacets = 0
            var count = 0
            for (z in MinCell..MaxCell step ChunkSize) {
                for (x in MinCell..MaxCell step ChunkSize) {
                    for (face in terrain.facets(x, z)) {
                        val normal = face.normal()
                        assertTrue(normal.y > 0f && normal.x.isFinite() && normal.z.isFinite())
                        val vertices = listOf(face.a, face.b, face.c)
                        if (face.biome == Biome.Beach && vertices.any { it.y < SeaLevel } && vertices.any { it.y > SeaLevel }) {
                            mixedShoreFacets++
                        }
                        for (vertex in vertices) {
                            val existing = corners.put(vertex.x to vertex.z, vertex.y)
                            if (existing != null) assertEquals(existing, vertex.y)
                            assertEquals(
                                terrain.cornerElevation((vertex.x / CellSize).roundToInt(), (vertex.z / CellSize).roundToInt()) / 32f,
                                vertex.y,
                            )
                        }
                        count++
                    }
                }
            }
            assertTrue(mixedShoreFacets > 0)
            assertTrue(count > 1000)
            for (index in 0 until terrain.propCount) {
                val prop = terrain.prop(index)
                val cell = assertNotNull(terrain.cellAt(prop.x, prop.z))
                val expectedY = (terrain.cornerElevation(prop.x, prop.z) + terrain.cornerElevation(prop.x + 1, prop.z + 1)) / 64f
                assertEquals(expectedY, cellPosition(prop.x, prop.z, cell.elevationTwice).y)
                assertTrue(expectedY > SeaLevel)
            }
        }
    }
}

package id.branchworld.renderer.geometry

import id.branchworld.simulation.terrain.Biome
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import id.branchworld.simulation.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.simulation.terrain.TerrainMap.Companion.MinCell
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The engine-free island geometry every renderer backend draws, checked on every target including iOS. */
class TerrainGeometryTest {
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

    @Test
    fun chunkArraysAreWellFormedAndSitOnTheDrawnGround() {
        val terrain = IslandGenerator.generate(TerrainKey(104729L))
        val chunks = terrain.terrainChunks()
        assertTrue(chunks.isNotEmpty())
        for (chunk in chunks) {
            val mesh = chunk.mesh
            assertEquals(mesh.vertexCount * 3, mesh.normals.size)
            assertEquals(mesh.vertexCount * 4, mesh.colors.size)
            assertEquals(0, mesh.indices.size % 3)
            assertEquals(terrain.facets(chunk.chunkX, chunk.chunkZ).size * 3, mesh.indices.size)
            assertTrue(mesh.indices.all { it in 0 until mesh.vertexCount })
            assertTrue(mesh.colors.all { it in 0f..1.2f })
            for (v in 0 until mesh.vertexCount) {
                val x = mesh.positions[v * 3]
                val z = mesh.positions[v * 3 + 2]
                assertTrue(mesh.normals[v * 3 + 1] > 0f)
                // Corners lie exactly on the cell grid, so the drawn height there is the corner's own height.
                assertTrue(abs(terrain.heightAt(x, z) - mesh.positions[v * 3 + 1]) < 1e-5f)
            }
        }
    }

    @Test
    fun seaIsOneFlatGridFromShallowsToOpenWater() {
        val sea = IslandGenerator.generate(TerrainKey(271828L)).seaSurface()
        assertTrue(sea.indices.all { it in 0 until sea.vertexCount })
        for (v in 0 until sea.vertexCount) {
            assertEquals(SeaLevel, sea.positions[v * 3 + 1])
            assertTrue(sea.colors[v * 4 + 3] in 0f..1f)
        }
        // The far corners are open water: fully transparent shallows weight.
        assertEquals(0f, sea.colors[3])
    }
}

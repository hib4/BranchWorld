package id.branchworld.renderer

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.game.terrain.TerrainMap.Companion.MinCell

internal const val ChunkSize = 16
internal const val CellSize = 0.16f
internal const val SeaLevel = 0f
private const val ElevationScale = 1f / 32f
private const val WaterDepth = 0.12f

internal fun cellPosition(x: Int, z: Int, elevationTwice: Int) =
    Vec3f((x + 0.5f) * CellSize, elevationTwice * (ElevationScale / 2), (z + 0.5f) * CellSize)

internal data class TerrainFacet(val a: Vec3f, val b: Vec3f, val c: Vec3f, val biome: Biome) {
    fun normal() = (b - a).cross(c - a, MutableVec3f()).norm()
}

internal fun TerrainMap.facets(chunkX: Int, chunkZ: Int): List<TerrainFacet> = buildList {
    fun corner(x: Int, z: Int) = Vec3f(x * CellSize, cornerElevation(x, z) * ElevationScale, z * CellSize)
    for (z in chunkZ until chunkZ + ChunkSize) {
        for (x in chunkX until chunkX + ChunkSize) {
            val biome = checkNotNull(cellAt(x, z)).biome
            if (biome == Biome.Water) continue
            val a = corner(x, z)
            val b = corner(x + 1, z)
            val c = corner(x, z + 1)
            val d = corner(x + 1, z + 1)
            add(TerrainFacet(a, c, d, biome))
            add(TerrainFacet(a, d, b, biome))
        }
    }
}

internal fun Scene.addIsland(terrain: TerrainMap) {
    val groundShader = KslBlinnPhongShader {
        color { vertexColor() }
        specularStrength(0.035f)
    }
    for (chunkZ in MinCell..MaxCell step ChunkSize) {
        for (chunkX in MinCell..MaxCell step ChunkSize) {
            val faces = terrain.facets(chunkX, chunkZ)
            if (faces.isEmpty()) continue
            addColorMesh("Terrain $chunkX,$chunkZ") {
                generate {
                    for (face in faces) {
                        color = face.biome.palette()
                        val normal = face.normal()
                        val a = vertex(face.a, normal)
                        val b = vertex(face.b, normal)
                        val c = vertex(face.c, normal)
                        addTriIndices(a, b, c)
                    }
                }
                shader = groundShader
            }
        }
    }
    addColorMesh("Sea") {
        generate {
            color = Color(0.12f, 0.46f, 0.53f)
            cylinder {
                radius = 5.6f
                height = WaterDepth
                steps = 48
                origin.y = SeaLevel - WaterDepth / 2
            }
        }
        shader = KslBlinnPhongShader {
            color { vertexColor() }
            specularStrength(0.12f)
        }
    }
}

private fun Biome.palette() = when (this) {
    Biome.Water -> Color(0.12f, 0.46f, 0.53f)
    Biome.Beach -> Color(0.67f, 0.53f, 0.30f)
    Biome.Meadow -> Color(0.36f, 0.54f, 0.23f)
    Biome.PineForest -> Color(0.24f, 0.42f, 0.24f)
    Biome.RockyUpland -> Color(0.43f, 0.43f, 0.36f)
}

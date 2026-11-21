package id.branchworld.renderer

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.max
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.game.terrain.TerrainMap.Companion.MinCell

internal const val ChunkSize = 16
internal const val CellSize = 0.16f
internal const val SeaLevel = 0f
internal const val ElevationScale = 1f / 32f

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
                        val source = face.biome.palette()
                        val patch = sin(face.a.x * 2.8f + face.a.z * 1.8f) * cos(face.a.z * 3.3f)
                        val grain = sin(face.b.x * 41f + face.b.z * 35f) * 0.028f
                        val tone = 1f + patch * 0.12f + grain
                        color = Color(source.r * tone, source.g * tone, source.b * tone)
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

    addWater(terrain)
}

private fun Scene.addWater(terrain: TerrainMap) {
    addColorMesh("Coastal water") {
        generate {
            val interior = (MinCell..TerrainMap.MaxCorner step 2).map { it * CellSize }
            val coordinates = listOf(-72f, -24f, -10f, -6.5f) + interior + listOf(6.5f, 10f, 24f, 72f)
            val vertices = coordinates.map { z ->
                coordinates.map { x ->
                    val cellX = (x / CellSize).toInt()
                    val cellZ = (z / CellSize).toInt()
                    val h = if (cellX in MinCell..TerrainMap.MaxCorner && cellZ in MinCell..TerrainMap.MaxCorner) {
                        terrain.cornerElevation(cellX, cellZ)
                    } else TerrainMap.MinElevation
                    val shallows = ((h + 17f) / 18f).coerceIn(0f, 1f)
                    val variation = sin(x * 3.4f + z * 1.6f) * cos(z * 4.6f) * 0.017f
                    color = Color(0.15f + shallows * 0.32f + variation, 0.40f + shallows * 0.31f + variation,
                        0.47f + shallows * 0.18f + variation)
                    vertex(Vec3f(x, SeaLevel, z), Vec3f.Y_AXIS)
                }
            }
            for (z in 0 until vertices.lastIndex) {
                for (x in 0 until vertices[z].lastIndex) {
                    val a = vertices[z][x]
                    val b = vertices[z][x + 1]
                    val c = vertices[z + 1][x]
                    val d = vertices[z + 1][x + 1]
                    addTriIndices(a, c, d)
                    addTriIndices(a, d, b)
                }
            }
        }
        shader = KslUnlitShader { color { vertexColor() } }
    }
}

internal fun Scene.addWavelets(terrain: TerrainMap) = addColorMesh("Shore ripples") {
    generate {
        for (z in MinCell + 2 until MaxCell - 2) {
            for (x in MinCell + 2 until MaxCell - 2) {
                val cell = terrain.cellAt(x, z) ?: continue
                val pattern = (x * 37 + z * 19) and 31
                if (cell.biome != Biome.Water || cell.elevationTwice !in -35..-3 || pattern > 2) continue
                val p = cellPosition(x, z, 0)
                val length = 0.12f + pattern * 0.038f
                val y = SeaLevel + 0.008f
                color = Color(0.79f, 0.92f, 0.80f, 0f)
                val a = vertex(Vec3f(p.x - length, y, p.z - 0.022f), Vec3f.Y_AXIS)
                val d = vertex(Vec3f(p.x + length, y, p.z + 0.018f), Vec3f.Y_AXIS)
                color = Color(0.84f, 0.96f, 0.86f, 0.36f)
                val b = vertex(Vec3f(p.x, y, p.z - 0.015f), Vec3f.Y_AXIS)
                val c = vertex(Vec3f(p.x + 0.026f, y, p.z + 0.016f), Vec3f.Y_AXIS)
                addTriIndices(a, c, b)
                addTriIndices(b, c, d)
            }
        }
    }
    shader = translucentSurface()
}

internal fun Scene.addRegionHighlight(terrain: TerrainMap, region: Int) =
    addColorMesh("Grove preview") {
        val x0 = MinCell + region % 4 * 16
        val z0 = MinCell + region / 4 * 16
        generate {
            terrain.facets(x0, z0).forEach { face ->
                val centerX = (face.a.x + face.b.x + face.c.x) / 3f
                val centerZ = (face.a.z + face.b.z + face.c.z) / 3f
                val edge = centerX < (x0 + 1) * CellSize || centerX > (x0 + 15) * CellSize ||
                    centerZ < (z0 + 1) * CellSize || centerZ > (z0 + 15) * CellSize
                color = Color(1f, 0.80f, 0.29f, if (edge) 0.58f else 0.15f)
                val normal = face.normal()
                fun raised(p: Vec3f) = Vec3f(p.x, max(p.y, SeaLevel) + 0.016f, p.z)
                val a = vertex(raised(face.a), normal)
                val b = vertex(raised(face.b), normal)
                val c = vertex(raised(face.c), normal)
                addTriIndices(a, b, c)
            }
        }
        shader = translucentSurface()
    }

internal fun translucentSurface() = KslUnlitShader {
    color { vertexColor() }
    pipeline { isWriteDepth = false; cullMethod = CullMethod.NO_CULLING }
}

private fun Biome.palette() = when (this) {
    Biome.Water -> Color(0.12f, 0.46f, 0.53f)
    Biome.Beach -> Color(0.76f, 0.64f, 0.41f)
    Biome.Meadow -> Color(0.43f, 0.59f, 0.25f)
    Biome.PineForest -> Color(0.22f, 0.41f, 0.27f)
    Biome.RockyUpland -> Color(0.49f, 0.48f, 0.39f)
}

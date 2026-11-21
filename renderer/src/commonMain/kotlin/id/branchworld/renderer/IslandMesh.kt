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
import kotlin.math.roundToInt
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.IslandLandscape
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

internal fun Scene.addIsland(terrain: TerrainMap, water: CoastalWater, groundShader: KslBlinnPhongShader) {
    val landscape = terrain.watershed?.landscape
    for (chunkZ in MinCell..MaxCell step ChunkSize) {
        for (chunkX in MinCell..MaxCell step ChunkSize) {
            val faces = terrain.facets(chunkX, chunkZ)
            if (faces.isEmpty()) continue
            addColorMesh("Terrain $chunkX,$chunkZ") {
                generate {
                    for (face in faces) {
                        val normal = face.normal()
                        fun painted(p: Vec3f): Int {
                            val drift = 0.5f + sin(p.x * 2.8f + p.z * 1.8f) * cos(p.z * 2.4f) * 0.5f
                            val detail = sin(p.x * 17f - p.z * 22f) * 0.025f
                            val x = (p.x / CellSize).roundToInt()
                            val z = (p.z / CellSize).roundToInt()
                            var total = 0
                            var source = face.biome.palette()
                            for (neighborZ in z - 1..z) {
                                for (neighborX in x - 1..x) {
                                    val biome = terrain.cellAt(neighborX, neighborZ)?.biome ?: continue
                                    if (biome == Biome.Water) continue
                                    val neighbor = biome.paint(landscape, drift)
                                    source = source.mix(neighbor, 1f / ++total)
                                }
                            }
                            val tone = 0.91f + (normal.y * 0.08f) + detail
                            color = Color(source.r * tone, source.g * tone, source.b * tone)
                            return vertex(p, normal)
                        }
                        val a = painted(face.a)
                        val b = painted(face.b)
                        val c = painted(face.c)
                        addTriIndices(a, b, c)
                    }
                }
                shader = groundShader
            }
        }
    }

    addWater(terrain, water)
}

private fun Scene.addWater(terrain: TerrainMap, water: CoastalWater) {
    addColorMesh("Coastal water") {
        generate {
            val interior = (MinCell..TerrainMap.MaxCorner).map { it * CellSize }
            val coordinates = listOf(-72f, -24f, -10f, -6.5f) + interior + listOf(6.5f, 10f, 24f, 72f)
            val vertices = coordinates.map { z ->
                coordinates.map { x ->
                    val cellX = (x / CellSize).roundToInt()
                    val cellZ = (z / CellSize).roundToInt()
                    val h = if (cellX in MinCell..TerrainMap.MaxCorner && cellZ in MinCell..TerrainMap.MaxCorner) {
                        terrain.cornerElevation(cellX, cellZ)
                    } else TerrainMap.MinElevation
                    val shallows = ((h + 18f) / 18f).coerceIn(0f, 1f)
                    color = Color(0.081f + shallows * 0.26f, 0.31f + shallows * 0.35f,
                        0.39f + shallows * 0.19f, shallows)
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
        shader = water
    }
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
    Biome.Meadow -> Color(0.32f, 0.51f, 0.20f)
    Biome.PineForest -> Color(0.19f, 0.34f, 0.21f)
    Biome.RockyUpland -> Color(0.42f, 0.41f, 0.33f)
    Biome.Wetland -> Color(0.33f, 0.42f, 0.24f)
}

private fun Biome.accent() = when (this) {
    Biome.Water -> Color(0.14f, 0.49f, 0.53f)
    Biome.Beach -> Color(0.80f, 0.70f, 0.48f)
    Biome.Meadow -> Color(0.54f, 0.62f, 0.27f)
    Biome.PineForest -> Color(0.34f, 0.44f, 0.23f)
    Biome.RockyUpland -> Color(0.52f, 0.48f, 0.36f)
    Biome.Wetland -> Color(0.52f, 0.53f, 0.27f)
}

private fun Biome.paint(landscape: IslandLandscape?, drift: Float): Color = when {
    this == Biome.RockyUpland && landscape == IslandLandscape.Tarn ->
        Color(0.38f, 0.41f, 0.46f).mix(Color(0.58f, 0.54f, 0.46f), drift * 0.7f)
    this == Biome.Meadow && landscape == IslandLandscape.Marsh ->
        Color(0.39f, 0.49f, 0.24f).mix(Color(0.65f, 0.61f, 0.32f), drift * 0.7f)
    this == Biome.Meadow && landscape == IslandLandscape.Valley ->
        Color(0.29f, 0.49f, 0.24f).mix(Color(0.53f, 0.63f, 0.26f), drift * 0.69f)
    else -> palette().mix(accent(), drift * 0.64f)
}

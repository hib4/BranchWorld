package id.branchworld.renderer

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
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
            val surfaceNormal = Vec3f.Y_AXIS
            val steps = 48
            color = Color(0.20f, 0.55f, 0.57f)
            val center = vertex(Vec3f(0f, SeaLevel, 0f), surfaceNormal)
            fun point(radius: Float, angle: Double, y: Float = SeaLevel) =
                Vec3f(radius * cos(angle).toFloat(), y, radius * sin(angle).toFloat())
            for (step in 0 until steps) {
                val angle = step * 2 * PI / steps
                val next = (step + 1) * 2 * PI / steps
                color = Color(0.18f, 0.52f, 0.55f)
                val near = vertex(point(3.2f, angle), surfaceNormal)
                val nearNext = vertex(point(3.2f, next), surfaceNormal)
                color = Color(0.08f, 0.31f, 0.39f)
                val rim = point(5.6f, angle)
                val rimNext = point(5.6f, next)
                val far = vertex(rim, surfaceNormal)
                val farNext = vertex(rimNext, surfaceNormal)
                addTriIndices(center, nearNext, near)
                addTriIndices(near, nearNext, far)
                addTriIndices(nearNext, farNext, far)

                color = Color(0.05f, 0.20f, 0.27f)
                val wall = Vec3f(cos(angle).toFloat(), 0f, sin(angle).toFloat())
                val wallTop = vertex(rim, wall)
                val wallNext = vertex(rimNext, wall)
                val bottom = vertex(point(5.6f, angle, SeaLevel - WaterDepth), wall)
                val bottomNext = vertex(point(5.6f, next, SeaLevel - WaterDepth), wall)
                addTriIndices(wallTop, wallNext, bottom)
                addTriIndices(wallNext, bottomNext, bottom)
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
    Biome.Beach -> Color(0.72f, 0.58f, 0.36f)
    Biome.Meadow -> Color(0.40f, 0.56f, 0.27f)
    Biome.PineForest -> Color(0.21f, 0.39f, 0.24f)
    Biome.RockyUpland -> Color(0.46f, 0.45f, 0.40f)
}

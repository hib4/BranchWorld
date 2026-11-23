package id.branchworld.renderer.geometry

import id.branchworld.simulation.terrain.Biome
import id.branchworld.simulation.terrain.IslandLandscape
import id.branchworld.simulation.terrain.TerrainMap
import id.branchworld.simulation.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.simulation.terrain.TerrainMap.Companion.MinCell
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A linear colour; [mix] matches the engine's own blend so every backend paints the same ground. */
internal data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {
    fun mix(that: Rgba, weight: Float) =
        Rgba(r + (that.r - r) * weight, g + (that.g - g) * weight, b + (that.b - b) * weight, a + (that.a - a) * weight)
}

/** One terrain triangle, wound so its normal faces up. */
internal data class TerrainFacet(val a: Point3, val b: Point3, val c: Point3, val biome: Biome) {
    fun normal() = ((b - a) cross (c - a)).norm()
}

/**
 * Plain mesh arrays any engine can upload: xyz [positions] and [normals], rgba [colors] per vertex, and triangle
 * [indices] into them.
 */
internal class SurfaceMesh(val positions: FloatArray, val normals: FloatArray, val colors: FloatArray, val indices: IntArray) {
    val vertexCount get() = positions.size / 3
}

internal class TerrainChunk(val chunkX: Int, val chunkZ: Int, val mesh: SurfaceMesh)

/** The land triangles of the chunk whose first cell is ([chunkX], [chunkZ]); water cells are left to the sea. */
internal fun TerrainMap.facets(chunkX: Int, chunkZ: Int): List<TerrainFacet> = buildList {
    fun corner(x: Int, z: Int) = Point3(x * CellSize, cornerElevation(x, z) * ElevationScale, z * CellSize)
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

/**
 * The painted land, chunk by chunk: flat-shaded triangles whose corners blend the neighbouring biomes' colours with a
 * slow drift and fine grain, so neighbouring cells melt into each other without a texture.
 */
internal fun TerrainMap.terrainChunks(): List<TerrainChunk> = buildList {
    val landscape = watershed?.landscape
    for (chunkZ in MinCell..MaxCell step ChunkSize) {
        for (chunkX in MinCell..MaxCell step ChunkSize) {
            val faces = facets(chunkX, chunkZ)
            if (faces.isEmpty()) continue
            val builder = MeshArrays(faces.size * 3)
            for (face in faces) {
                val normal = face.normal()
                fun painted(p: Point3): Int {
                    val drift = 0.5f + sin(p.x * 2.8f + p.z * 1.8f) * cos(p.z * 2.4f) * 0.5f
                    val detail = sin(p.x * 17f - p.z * 22f) * 0.025f
                    val x = (p.x / CellSize).roundToInt()
                    val z = (p.z / CellSize).roundToInt()
                    var total = 0
                    var source = face.biome.palette()
                    for (neighborZ in z - 1..z) {
                        for (neighborX in x - 1..x) {
                            val biome = cellAt(neighborX, neighborZ)?.biome ?: continue
                            if (biome == Biome.Water) continue
                            val neighbor = biome.paint(landscape, drift)
                            source = source.mix(neighbor, 1f / ++total)
                        }
                    }
                    val tone = 0.91f + (normal.y * 0.08f) + detail
                    return builder.vertex(p, normal, Rgba(source.r * tone, source.g * tone, source.b * tone))
                }
                val a = painted(face.a)
                val b = painted(face.b)
                val c = painted(face.c)
                builder.triangle(a, b, c)
            }
            add(TerrainChunk(chunkX, chunkZ, builder.build()))
        }
    }
}

/**
 * The sea around the island: a flat grid at sea level, fine over the island and coarse out to the horizon, whose
 * vertex colour and alpha deepen from the turquoise shallows to open water.
 */
internal fun TerrainMap.seaSurface(): SurfaceMesh {
    val interior = (MinCell..TerrainMap.MaxCorner).map { it * CellSize }
    val coordinates = listOf(-72f, -24f, -10f, -6.5f) + interior + listOf(6.5f, 10f, 24f, 72f)
    val builder = MeshArrays(coordinates.size * coordinates.size)
    val up = Point3(0f, 1f, 0f)
    val vertices = coordinates.map { z ->
        coordinates.map { x ->
            val cellX = (x / CellSize).roundToInt()
            val cellZ = (z / CellSize).roundToInt()
            val h = if (cellX in MinCell..TerrainMap.MaxCorner && cellZ in MinCell..TerrainMap.MaxCorner) {
                cornerElevation(cellX, cellZ)
            } else TerrainMap.MinElevation
            val shallows = ((h + 18f) / 18f).coerceIn(0f, 1f)
            builder.vertex(Point3(x, SeaLevel, z), up,
                Rgba(0.081f + shallows * 0.26f, 0.31f + shallows * 0.35f, 0.39f + shallows * 0.19f, shallows))
        }
    }
    for (z in 0 until vertices.lastIndex) {
        for (x in 0 until vertices[z].lastIndex) {
            val a = vertices[z][x]
            val b = vertices[z][x + 1]
            val c = vertices[z + 1][x]
            val d = vertices[z + 1][x + 1]
            builder.triangle(a, c, d)
            builder.triangle(a, d, b)
        }
    }
    return builder.build()
}

internal class MeshArrays(expectedVertices: Int) {
    private val positions = ArrayList<Float>(expectedVertices * 3)
    private val normals = ArrayList<Float>(expectedVertices * 3)
    private val colors = ArrayList<Float>(expectedVertices * 4)
    private val indices = ArrayList<Int>(expectedVertices * 2)
    private var count = 0

    fun vertex(p: Point3, n: Point3, color: Rgba): Int {
        positions += p.x; positions += p.y; positions += p.z
        normals += n.x; normals += n.y; normals += n.z
        colors += color.r; colors += color.g; colors += color.b; colors += color.a
        return count++
    }

    fun triangle(a: Int, b: Int, c: Int) {
        indices += a; indices += b; indices += c
    }

    fun build() = SurfaceMesh(positions.toFloatArray(), normals.toFloatArray(), colors.toFloatArray(), indices.toIntArray())
}

private fun Biome.palette() = when (this) {
    Biome.Water -> Rgba(0.12f, 0.46f, 0.53f)
    Biome.Beach -> Rgba(0.76f, 0.64f, 0.41f)
    Biome.Meadow -> Rgba(0.32f, 0.51f, 0.20f)
    Biome.PineForest -> Rgba(0.19f, 0.34f, 0.21f)
    Biome.RockyUpland -> Rgba(0.42f, 0.41f, 0.33f)
    Biome.Wetland -> Rgba(0.33f, 0.42f, 0.24f)
}

private fun Biome.accent() = when (this) {
    Biome.Water -> Rgba(0.14f, 0.49f, 0.53f)
    Biome.Beach -> Rgba(0.80f, 0.70f, 0.48f)
    Biome.Meadow -> Rgba(0.54f, 0.62f, 0.27f)
    Biome.PineForest -> Rgba(0.34f, 0.44f, 0.23f)
    Biome.RockyUpland -> Rgba(0.52f, 0.48f, 0.36f)
    Biome.Wetland -> Rgba(0.52f, 0.53f, 0.27f)
}

private fun Biome.paint(landscape: IslandLandscape?, drift: Float): Rgba = when {
    this == Biome.RockyUpland && landscape == IslandLandscape.Tarn ->
        Rgba(0.38f, 0.41f, 0.46f).mix(Rgba(0.58f, 0.54f, 0.46f), drift * 0.7f)
    this == Biome.Meadow && landscape == IslandLandscape.Marsh ->
        Rgba(0.39f, 0.49f, 0.24f).mix(Rgba(0.65f, 0.61f, 0.32f), drift * 0.7f)
    this == Biome.Meadow && landscape == IslandLandscape.Valley ->
        Rgba(0.29f, 0.49f, 0.24f).mix(Rgba(0.53f, 0.63f, 0.26f), drift * 0.69f)
    else -> palette().mix(accent(), drift * 0.64f)
}

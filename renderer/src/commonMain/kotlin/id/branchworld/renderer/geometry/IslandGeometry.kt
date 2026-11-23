package id.branchworld.renderer.geometry

import id.branchworld.simulation.terrain.TerrainMap
import id.branchworld.simulation.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.simulation.terrain.TerrainMap.Companion.MinCell
import kotlin.math.floor
import kotlin.math.sqrt

// The island's scale in scene units, shared by every renderer backend, picking and the camera.

/** Terrain is meshed in square chunks of this many cells. */
internal const val ChunkSize = 16
internal const val CellSize = 0.16f
internal const val SeaLevel = 0f

/** Scene height of one terrain elevation step. */
internal const val ElevationScale = 1f / 32f

/** A small value vector, kept apart from engine types so geometry, picking and the camera are plain Kotlin. */
internal data class Point3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Point3) = Point3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Point3) = Point3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Point3(x * s, y * s, z * s)
    infix fun dot(o: Point3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Point3) = Point3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun norm(): Point3 = sqrt(this dot this).let { length -> if (length == 0f) this else this * (1f / length) }
}

/** The centre of a cell at its own height ([elevationTwice] is twice the cell's elevation). */
internal fun cellPosition(x: Int, z: Int, elevationTwice: Int) =
    Point3((x + 0.5f) * CellSize, elevationTwice * (ElevationScale / 2), (z + 0.5f) * CellSize)

/** The drawn ground height at a scene point: the same two triangles per cell the terrain mesh draws. */
internal fun TerrainMap.heightAt(x: Float, z: Float): Float {
    val cellX = floor(x / CellSize).toInt().coerceIn(MinCell, MaxCell)
    val cellZ = floor(z / CellSize).toInt().coerceIn(MinCell, MaxCell)
    val fx = (x / CellSize - cellX).coerceIn(0f, 1f)
    val fz = (z / CellSize - cellZ).coerceIn(0f, 1f)
    val a = cornerElevation(cellX, cellZ)
    val d = cornerElevation(cellX + 1, cellZ + 1)
    val diagonal = if (fx <= fz) {
        val c = cornerElevation(cellX, cellZ + 1)
        a + (c - a) * fz + (d - c) * fx
    } else {
        val b = cornerElevation(cellX + 1, cellZ)
        a + (b - a) * fx + (d - b) * fz
    }
    return diagonal * ElevationScale
}

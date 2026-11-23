package id.branchworld.renderer.camera

import id.branchworld.renderer.geometry.CellSize
import id.branchworld.renderer.geometry.ElevationScale
import id.branchworld.renderer.geometry.Point3
import id.branchworld.renderer.geometry.SeaLevel
import id.branchworld.renderer.geometry.cellPosition
import id.branchworld.renderer.geometry.heightAt
import id.branchworld.simulation.projection.CreaturePose
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.WorldPick
import id.branchworld.simulation.terrain.Biome
import id.branchworld.simulation.terrain.TerrainMap
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** The diorama camera's vertical field of view. The scene and picking share it so a tap lands where it looks. */
internal const val ViewFovYDegrees = 47f

internal data class Ray(val origin: Point3, val direction: Point3)

/** Where the camera sits: the same orbit the scene places it at each frame. */
internal fun PreviewCamera.eye(): Point3 {
    val horizontal = distance * cos(pitch)
    return Point3(targetX + horizontal * sin(yaw), targetY + distance * sin(pitch), targetZ + horizontal * cos(yaw))
}

private class Basis(camera: PreviewCamera) {
    val eye = camera.eye()
    val forward = (Point3(camera.targetX, camera.targetY, camera.targetZ) - eye).norm()
    val right = (forward cross Point3(0f, 1f, 0f)).norm()
    val up = right cross forward
}

/** The ray from the eye through a pixel of a [width]×[height] view. */
internal fun PreviewCamera.viewRay(x: Float, y: Float, width: Float, height: Float, fovY: Float = ViewFovYDegrees): Ray {
    val basis = Basis(this)
    val half = tan(fovY * DegreesToRadians / 2f)
    val aspect = width / height.coerceAtLeast(1f)
    val ndcX = 2f * x / width - 1f
    val ndcY = 1f - 2f * y / height
    val direction = (basis.forward + basis.right * (ndcX * half * aspect) + basis.up * (ndcY * half)).norm()
    return Ray(basis.eye, direction)
}

/** The pixel a world point is drawn at, or null when it is behind the camera. */
internal fun PreviewCamera.screenPoint(point: Point3, width: Float, height: Float, fovY: Float = ViewFovYDegrees): Pair<Float, Float>? {
    val basis = Basis(this)
    val view = point - basis.eye
    val depth = view dot basis.forward
    if (depth <= 0.01f) return null
    val half = tan(fovY * DegreesToRadians / 2f)
    val aspect = width / height.coerceAtLeast(1f)
    val ndcX = (view dot basis.right) / (depth * half * aspect)
    val ndcY = (view dot basis.up) / (depth * half)
    return (ndcX + 1f) / 2f * width to (1f - ndcY) / 2f * height
}

internal data class GroundHit(val cellX: Int, val cellZ: Int, val water: Boolean, val point: Point3)

/** The surface a ray meets first: land, the sea or an inland pool. */
internal fun TerrainMap.surfaceAt(x: Float, z: Float): Float {
    val land = heightAt(x, z)
    val cellX = floor(x / CellSize).toInt()
    val cellZ = floor(z / CellSize).toInt()
    val pool = watershed?.waterAt(cellX, cellZ)?.surfaceTwice?.let { it * (ElevationScale / 2) }
    return maxOf(land, SeaLevel, pool ?: Float.NEGATIVE_INFINITY)
}

/** Marches [ray] over the island and refines the crossing; null when it misses the map. */
internal fun TerrainMap.raycastGround(ray: Ray, maxDistance: Float = 60f): GroundHit? {
    val step = CellSize / 2f
    var previous = 0f
    var t = 0f
    val low = -0.5f
    while (t < maxDistance) {
        val point = ray.origin + ray.direction * t
        if (point.y < low) break
        if (point.y <= surfaceAt(point.x, point.z)) {
            var near = previous
            var far = t
            repeat(8) {
                val mid = (near + far) / 2f
                val probe = ray.origin + ray.direction * mid
                if (probe.y <= surfaceAt(probe.x, probe.z)) far = mid else near = mid
            }
            val hit = ray.origin + ray.direction * far
            val cellX = floor(hit.x / CellSize).toInt()
            val cellZ = floor(hit.z / CellSize).toInt()
            val ground = cellAt(cellX, cellZ) ?: return null
            val pool = watershed?.waterAt(cellX, cellZ)
            val water = ground.biome == Biome.Water || pool != null && pool.surfaceTwice > ground.elevationTwice
            return GroundHit(cellX, cellZ, water, hit)
        }
        previous = t
        t += step
    }
    return null
}

/**
 * What the player pointed at in a [width]×[height] view: the ground cell under the pixel and, if one is drawn within
 * [radius] pixels, the nearest creature. Creatures are matched on screen, which is kinder to a finger than a hit test.
 */
internal fun pickWorld(
    camera: PreviewCamera,
    width: Float,
    height: Float,
    x: Float,
    y: Float,
    terrain: TerrainMap,
    creatures: List<CreaturePose>,
    radius: Float,
): WorldPick {
    val hit = terrain.raycastGround(camera.viewRay(x, y, width, height))
    val ground = hit?.let { GroundPoint(it.cellX, it.cellZ, terrain.cellAt(it.cellX, it.cellZ)?.elevationTwice ?: 0) }
    var nearest: Long? = null
    var best = radius
    for (animal in creatures) {
        val base = cellPosition(animal.ground.x, animal.ground.z, animal.ground.elevationTwice)
        val (sx, sy) = camera.screenPoint(Point3(base.x, base.y + 0.08f, base.z), width, height) ?: continue
        val distance = hypot(sx - x, sy - y)
        if (distance < best) {
            best = distance
            nearest = animal.id
        }
    }
    return WorldPick(ground?.takeIf { !hit.water }, water = hit?.water == true, creatureId = nearest)
}

private const val DegreesToRadians = 0.017453292f

package id.branchworld.renderer.camera

import id.branchworld.renderer.geometry.CellSize
import id.branchworld.renderer.geometry.cellPosition
import id.branchworld.renderer.geometry.heightAt
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldRenderProjection
import id.branchworld.simulation.terrain.TerrainMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the camera should rest to frame what the interface is looking at: a creature, a region, a landmark, a place in
 * the world's history or a village. A creature keeps the side it was first seen from until the player turns the view.
 */
internal class FocusFraming {
    private var creatureRevision: Int? = null
    private var creatureOrbit = 0L
    private var creatureYaw: Float? = null

    /** Forgets how the last creature was framed. */
    fun release() {
        creatureRevision = null
        creatureYaw = null
    }

    /** The pose that frames [request]'s focus from [previous], or null when there is nothing to frame. */
    fun target(
        request: WorldCamera,
        snapshot: WorldRenderProjection,
        terrain: TerrainMap,
        previous: PreviewCamera,
        narrow: Boolean,
        manualOrbit: Long,
    ): PreviewCamera? = when (val focus = request.focus) {
        is WorldCamera.Focus.Creature -> snapshot.creatures.firstOrNull { it.id == focus.id }?.let { animal ->
            if (creatureRevision != request.focusRequest) {
                creatureRevision = request.focusRequest
                creatureOrbit = manualOrbit
                creatureYaw = creatureViewYaw(terrain, animal, previous)
            } else if (creatureOrbit != manualOrbit) creatureYaw = null
            val ground = animal.ground
            val center = cellPosition(ground.x, ground.z, ground.elevationTwice)
            val distance = if (animal.intent != null) {
                if (narrow) 4.1f else 3.15f
            } else if (narrow) 4.4f else 3.4f
            previous.copy(yaw = creatureYaw ?: previous.yaw,
                targetX = center.x, targetY = center.y + if (narrow) -0.65f else 0.19f,
                targetZ = center.z, distance = distance)
        }
        is WorldCamera.Focus.Region -> focus.takeIf { it.index in 0..15 }?.let {
            val x = TerrainMap.MinCell + it.index % 4 * 16 + 8
            val z = TerrainMap.MinCell + it.index / 4 * 16 + 8
            val center = cellPosition(x, z, terrain.cellAt(x, z)?.elevationTwice ?: 0)
            val sites = request.targetSites
            if (snapshot.shaping == null) {
                previous.copy(targetX = center.x, targetY = center.y.coerceAtLeast(0f) + if (narrow) -0.78f else 0.25f,
                    targetZ = center.z, distance = if (narrow) 6.1f else 5.5f)
            } else {
                val centerX = if (sites.isEmpty()) center.x else (sites.map { point -> point.x }.average().toFloat() + 0.5f) * CellSize
                val centerZ = if (sites.isEmpty()) center.z else (sites.map { point -> point.z }.average().toFloat() + 0.5f) * CellSize
                val elevation = terrain.heightAt(centerX, centerZ).coerceAtLeast(0f)
                val offsetX = if (narrow) sin(previous.yaw) * 0.72f else cos(previous.yaw) * 0.62f
                val offsetZ = if (narrow) cos(previous.yaw) * 0.72f else -sin(previous.yaw) * 0.62f
                previous.copy(targetX = centerX + offsetX, targetZ = centerZ + offsetZ,
                    targetY = elevation + if (narrow) -0.62f else 0.27f, distance = if (narrow) 5.9f else 5.1f)
            }
        }
        is WorldCamera.Focus.Landmark -> terrain.watershed?.let { watershed ->
            watershed.landmarks.firstOrNull { it.kind == focus.kind }?.let { landmark ->
                val height = watershed.waterAt(landmark.x, landmark.z)?.surfaceTwice ?: 0
                val center = cellPosition(landmark.x, landmark.z, height)
                previous.copy(targetX = center.x, targetZ = center.z,
                    targetY = center.y + if (narrow) -0.65f else 0.12f,
                    distance = if (narrow) 5.3f else 4.6f)
            }
        }
        is WorldCamera.Focus.WildlifeLocation -> {
            val ground = focus.ground
            val center = cellPosition(ground.x, ground.z, ground.elevationTwice)
            previous.copy(targetX = center.x, targetZ = center.z,
                targetY = center.y + if (narrow) -0.65f else 0.15f, distance = if (narrow) 4.7f else 3.9f)
        }
        is WorldCamera.Focus.EchoSite -> focus.grounds.takeIf { it.isNotEmpty() }?.let { grounds ->
            val minX = grounds.minOf { it.x }
            val maxX = grounds.maxOf { it.x }
            val minZ = grounds.minOf { it.z }
            val maxZ = grounds.maxOf { it.z }
            val x = (minX + maxX + 1) * CellSize / 2f
            val z = (minZ + maxZ + 1) * CellSize / 2f
            val span = maxOf(maxX - minX, maxZ - minZ) * CellSize
            val offset = if (focus.sideDock) 0.88f else 0f
            previous.copy(targetX = x + cos(previous.yaw) * offset,
                targetZ = z - sin(previous.yaw) * offset,
                targetY = terrain.heightAt(x, z).coerceAtLeast(0f) + if (narrow) -0.72f else 0.13f,
                distance = (if (narrow) 4.9f else 4.4f) + span * 1.1f)
        }
        is WorldCamera.Focus.Village -> focus.grounds.takeIf { it.isNotEmpty() }?.let { grounds ->
            val x = (grounds.minOf { it.x } + grounds.maxOf { it.x } + 1) * CellSize / 2f
            val z = (grounds.minOf { it.z } + grounds.maxOf { it.z } + 1) * CellSize / 2f
            val span = maxOf(grounds.maxOf { it.x } - grounds.minOf { it.x }, grounds.maxOf { it.z } - grounds.minOf { it.z }) * CellSize
            previous.copy(targetX = x, targetZ = z,
                targetY = terrain.heightAt(x, z).coerceAtLeast(0f) + if (narrow) -0.55f else 0.08f,
                distance = ((if (narrow) 3.4f else 2.7f) + span * 0.7f).coerceAtMost(6f))
        }
        null -> null
    }
}

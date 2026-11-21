package id.branchworld.renderer

import id.branchworld.game.simulation.CreatureHeading
import id.branchworld.game.simulation.CreaturePose
import id.branchworld.game.terrain.PropKind
import id.branchworld.game.terrain.IslandLandscape
import id.branchworld.game.terrain.LandmarkKind
import id.branchworld.game.terrain.TerrainMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

internal data class PreviewCamera(
    val yaw: Float = 0.62f,
    val pitch: Float = 0.70f,
    val distance: Float = 8.05f,
    val targetX: Float = 0f,
    val targetZ: Float = 0f,
    val targetY: Float = 0.31f,
) {
    fun toward(target: PreviewCamera, fraction: Float): PreviewCamera {
        fun mix(start: Float, end: Float) = start + (end - start) * fraction
        val turn = (((target.yaw - yaw + Tau / 2f) % Tau + Tau) % Tau) - Tau / 2f
        return PreviewCamera(
            yaw = yaw + turn * fraction,
            pitch = mix(pitch, target.pitch),
            distance = mix(distance, target.distance),
            targetX = mix(targetX, target.targetX),
            targetZ = mix(targetZ, target.targetZ),
            targetY = mix(targetY, target.targetY),
        )
    }

    fun orbit(dx: Float, dy: Float): PreviewCamera = copy(
        yaw = (yaw - dx * 4f) % Tau,
        pitch = (pitch + dy * 3f).coerceIn(0.17f, 1.35f),
    )

    fun pan(dx: Float, dy: Float): PreviewCamera {
        val right = dx * distance
        val forward = dy * distance
        return copy(
            targetX = (targetX - right * cos(yaw) - forward * sin(yaw)).coerceIn(-5f, 5f),
            targetZ = (targetZ + right * sin(yaw) - forward * cos(yaw)).coerceIn(-5f, 5f),
        )
    }

    fun zoom(factor: Float): PreviewCamera =
        if (factor.isFinite() && factor > 0f) copy(distance = (distance / factor).coerceIn(3f, 18f)) else this
}

internal fun heroCamera(aspect: Float, opening: Boolean, terrain: TerrainMap? = null): PreviewCamera {
    val watershed = terrain?.watershed
    val camera = if (watershed == null) PreviewCamera() else {
        val basin = watershed.landmarks.first { it.kind == LandmarkKind.Basin }
        val yaw = when (watershed.landscape) {
            IslandLandscape.Valley -> 0.39f
            IslandLandscape.Tarn -> 0.80f
            IslandLandscape.Marsh -> 0.54f
        }
        PreviewCamera(yaw = yaw, pitch = 0.80f, distance = 8.8f,
            targetX = basin.x * CellSize * 0.49f, targetZ = basin.z * CellSize * 0.26f, targetY = 0.28f)
    }
    val narrow = aspect < 0.83f
    val distance = camera.distance + if (narrow) ((0.83f - aspect) * 8f).coerceAtMost(3f) else 0f
    if (!opening) return camera.copy(distance = distance)
    return if (aspect > 1.3f) {
        // Shift the look-at point left so the foreground clears the chapter introduction.
        camera.copy(distance = distance, targetX = camera.targetX - 1.75f * cos(camera.yaw),
            targetZ = camera.targetZ + 1.75f * sin(camera.yaw))
    } else {
        camera.copy(distance = distance, targetX = camera.targetX + 0.95f * sin(camera.yaw),
            targetZ = camera.targetZ + 0.95f * cos(camera.yaw))
    }
}

/** Pick an initial viewing angle when a creature is framed. Regular orbit takes over afterward. */
internal fun creatureViewYaw(terrain: TerrainMap, animal: CreaturePose, camera: PreviewCamera): Float {
    val ground = animal.ground
    val point = cellPosition(ground.x, ground.z, ground.elevationTwice)
    val eyeLine = point.y + 0.19f
    val incline = tan(camera.pitch)
    val face = when (animal.heading) {
        CreatureHeading.PositiveZ -> 0f
        CreatureHeading.PositiveX -> Tau / 4f
        CreatureHeading.NegativeZ -> Tau / 2f
        CreatureHeading.NegativeX -> -Tau / 4f
    }
    fun penalty(offset: Float): Float {
        val yaw = camera.yaw + offset
        val dx = sin(yaw)
        val dz = cos(yaw)
        var weight = abs(offset) * 0.29f + (1f - cos(yaw - face - 0.37f)) * 0.13f
        for (index in 0 until terrain.propCount) {
            val prop = terrain.prop(index)
            if (prop.kind != PropKind.Pine) continue
            val x = (prop.x + 0.5f) * CellSize
            val z = (prop.z + 0.5f) * CellSize
            val along = (x - point.x) * dx + (z - point.z) * dz
            if (along !in 0.07f..2.75f) continue
            val cross = abs((x - point.x) * dz - (z - point.z) * dx)
            val scale = prop.scalePercent / 100f
            val height = eyeLine + along * incline - terrain.heightAt(x, z)
            val crown = (if (animal.intent == null) 0.25f else 0.34f) * scale
            if (height in 0f..(1.03f * scale) && cross < crown) {
                weight += 1.5f + (1f - cross / crown)
            }
        }
        for (step in 1..6) {
            val along = step * 0.26f
            val groundHeight = terrain.heightAt(point.x + dx * along, point.z + dz * along)
            if (groundHeight > eyeLine + along * incline) weight += 1.8f
        }
        return weight
    }
    return camera.yaw + CreatureViewOffsets.minBy(::penalty)
}

private val CreatureViewOffsets = floatArrayOf(0f, -0.55f, 0.55f, -1.10f, 1.10f, -1.75f, 1.75f, -2.4f, 2.4f)

private const val Tau = 6.2831855f

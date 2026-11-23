package id.branchworld.renderer.motion

import id.branchworld.renderer.geometry.CellSize
import id.branchworld.renderer.geometry.Point3
import id.branchworld.renderer.geometry.cellPosition
import id.branchworld.renderer.geometry.heightAt
import id.branchworld.simulation.core.CreatureHeading
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.projection.CreaturePose
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.WorldRenderProjection
import id.branchworld.simulation.terrain.TerrainMap

/**
 * How one drawn creature moves between simulated cells: it glides at the pace the rules give it, turns toward where it
 * walks, leans into what it is doing and plays the matching clip at a matching speed. Cosmetic only, and shared by every
 * renderer backend so animals move the same everywhere. [clipDuration] gives each clip's length in seconds.
 */
internal class CreatureMotion(val species: Species, private val terrain: TerrainMap, private val clipDuration: (String) -> Float) {
    /** The clip to play: one of [CreatureClips][id.branchworld.renderer.assets.CreatureClips]. */
    var clip = "idle"
        private set
    /** Seconds into [clip]. */
    var clipPhase = 0f
        private set
    var x = 0f
        private set
    var y = 0f
        private set
    var z = 0f
        private set
    /** Degrees about the vertical axis. */
    var yaw = 0f
        private set
    /** Degrees about the creature's own sideways axis. */
    var lean = 0f
        private set
    var scale = 1f
        private set
    private var targetX = 0f
    private var targetZ = 0f
    private var targetYaw = 0f
    /** Cells per tick on the current gait. */
    private var pace = 0.5f
    private var clipRate = 0.5f
    private var held = false
    private var drop = 0f
    private var placed = false

    fun shown() = Point3(x, terrain.heightAt(x, z), z)

    fun retarget(tick: Long, pose: CreaturePose, interpolate: Boolean, reducedMotion: Boolean, nearVillage: Float, dropIn: Boolean) {
        val position = pose.ground
        val next = cellPosition(position.x, position.z, position.elevationTwice)
        targetX = next.x
        targetZ = next.z
        targetYaw = headingYaw(pose.heading)
        // A jump of more than a few cells is a seek or a new history, never a walk.
        val far = kotlin.math.hypot(targetX - x, targetZ - z) > CellSize * 3.5f
        if (!interpolate || !placed || far) {
            x = targetX
            z = targetZ
            yaw = targetYaw
            placed = true
        }
        if (dropIn) drop = DropSeconds
        val holdingRefuge = pose.action == CreatureAction.Fleeing && pose.ground == pose.destination
        val watchingRefuge = pose.action == CreatureAction.Stalking && pose.ground == pose.destination
        clip = when (pose.action) {
            CreatureAction.Walking -> "walk"
            CreatureAction.Stalking -> if (watchingRefuge) "idle" else "walk"
            CreatureAction.Fleeing -> if (holdingRefuge) "idle" else "run"
            CreatureAction.Chasing -> "run"
            CreatureAction.Eating, CreatureAction.Drinking, CreatureAction.Digging -> "eat"
            else -> "idle"
        }
        clipRate = when (pose.action) {
            CreatureAction.Walking -> if (species == Species.Rabbit) 1.22f else 0.87f
            CreatureAction.Fleeing -> 1.28f
            CreatureAction.Chasing -> 1.12f
            CreatureAction.Stalking -> 0.64f
            CreatureAction.Eating -> 0.73f
            CreatureAction.Digging -> 1.4f
            CreatureAction.Howling, CreatureAction.Alert -> 0.30f
            else -> 0.50f
        }
        pace = (pose.life?.pace?.takeIf { it > 0 }?.let { it / 100f } ?: when (pose.action) {
            CreatureAction.Fleeing, CreatureAction.Chasing -> 1f
            CreatureAction.Stalking -> 0.33f
            else -> if (species == Species.Rabbit) 0.5f else 0.34f
        }).coerceAtLeast(0.2f)
        scale = when (species) {
            Species.Rabbit -> 0.182f
            Species.Deer -> 0.253f
            Species.Fox -> 0.205f
            Species.Wolf -> 0.262f
        } * if (pose.intent != null) WildlifeBodyScale else 1f
        if (pose.juvenile) scale *= 0.73f
        // In a living world the animals are the subject: drawn a size up so they read across the island.
        if (pose.life != null) scale *= LivingBodyScale
        scale *= nearVillage
        lean = when (pose.action) {
            CreatureAction.Stalking -> 7f
            CreatureAction.Drinking -> 10f
            CreatureAction.Digging -> 14f
            CreatureAction.Howling -> -16f
            CreatureAction.Alert -> -6f
            else -> 0f
        }
        held = reducedMotion || pose.action == CreatureAction.Drinking || pose.action == CreatureAction.Resting ||
            pose.action == CreatureAction.Sleeping || holdingRefuge || watchingRefuge
        val duration = clipDuration(clip)
        if (held) clipPhase = if (pose.action == CreatureAction.Drinking) duration * 0.42f else 0f
        else if (!interpolate) clipPhase = ((tick * 0.05 * clipRate + (pose.id % 13L) * 0.071) % duration).toFloat()
        animate(0f, 0f, reducedMotion)
    }

    /** Glides toward the simulated cell at the creature's own pace, so a step lasts as long as the rules say it does. */
    fun animate(dt: Float, tickRate: Float, reducedMotion: Boolean) {
        val dx = targetX - x
        val dz = targetZ - z
        val distance = kotlin.math.hypot(dx, dz)
        if (distance > 0.0005f) {
            // Catch up a little faster when behind, so batched fast-forward never leaves a creature trailing.
            val speed = pace * CellSize * tickRate * (1f + (distance / CellSize - 1f).coerceAtLeast(0f) * 0.8f)
            val travel = minOf(distance, speed * dt)
            x += dx / distance * travel
            z += dz / distance * travel
            if (distance > CellSize * 0.15f) targetYaw = kotlin.math.atan2(dx, dz) * RadiansToDegrees
        }
        val turn = ((targetYaw - yaw + 540f) % 360f) - 180f
        yaw = if (reducedMotion) targetYaw else yaw + turn * (dt * 9f).coerceAtMost(1f)
        var lift = 0f
        if (drop > 0f) {
            drop = (drop - dt).coerceAtLeast(0f)
            val t = 1f - drop / DropSeconds
            // A short fall and a settle, like a toy set down on the table.
            lift = (1f - t) * (1f - t) * 0.45f - kotlin.math.sin(t * 3.1415927f) * 0.03f * (1f - t)
        }
        y = terrain.heightAt(x, z) + lift
        if (!held) clipPhase = (clipPhase + dt * clipRate * (tickRate / 20f).coerceIn(0f, 2.5f)) % clipDuration(clip)
    }
}

/**
 * Tracks the displayed tick and how fast ticks arrive, and decides when a new projection continues the last one
 * smoothly. Cosmetic only; shared so every backend paces its animals the same way.
 */
internal class CreaturePacing {
    private var displayedTick: Long? = null
    private var displayedBranch: Int? = null
    private var previousReducedMotion = false
    private var lastTickAt: Long? = null
    /** Simulated ticks per wall second, measured from the projections as they arrive. */
    var tickRate = 20f
        private set
    private var stillFor = 0f

    /**
     * Called every frame at [nanos] with the frame's [dt] in seconds. Returns null when [world] is the projection already
     * shown, or whether a fresh projection continues the last one smoothly.
     */
    fun frame(world: WorldRenderProjection, reducedMotion: Boolean, nanos: Long, dt: Float): Boolean? {
        val fresh = displayedTick != world.tick || displayedBranch != world.branchId || previousReducedMotion != reducedMotion
        if (!fresh) {
            stillFor += dt
            return null
        }
        val previousTick = displayedTick
        val continuous = !reducedMotion && !previousReducedMotion && world.interpolateCreatures && displayedBranch == world.branchId &&
            previousTick != null && world.tick - previousTick in 1..16
        if (continuous) {
            val wall = (lastTickAt?.let { (nanos - it) / 1_000L } ?: 50_000L).coerceAtLeast(1_000L) / 1_000_000f
            val measured = (world.tick - checkNotNull(previousTick)) / wall
            tickRate += (measured.coerceIn(2f, 200f) - tickRate) * 0.35f
        }
        lastTickAt = nanos
        stillFor = 0f
        displayedTick = world.tick
        displayedBranch = world.branchId
        previousReducedMotion = reducedMotion
        return continuous
    }

    /** Paused: creatures finish the step they were on, then stand. */
    val rate get() = if (stillFor > 0.25f) 0f else tickRate
}

internal fun headingYaw(heading: CreatureHeading) = when (heading) {
    CreatureHeading.PositiveZ -> 0f
    CreatureHeading.PositiveX -> 90f
    CreatureHeading.NegativeZ -> 180f
    CreatureHeading.NegativeX -> -90f
    CreatureHeading.PositiveXPositiveZ -> 45f
    CreatureHeading.NegativeXPositiveZ -> -45f
    CreatureHeading.PositiveXNegativeZ -> 135f
    CreatureHeading.NegativeXNegativeZ -> -135f
}

private const val DropSeconds = 0.42f
private const val LivingBodyScale = 1.35f
private const val RadiansToDegrees = 57.29578f

internal const val WildlifeBodyScale = 0.76f
private const val VillageWildlifeScale = 0.58f

/** Settlement structures and centres; wildlife eases down beside them so a village keeps its scale. */
internal fun villageGround(world: WorldRenderProjection): List<GroundPoint> = world.settlements.orEmpty().flatMap { site ->
    site.village?.let { it.structures.map { structure -> structure.ground } + it.center } ?: site.homes
}

/** 1 away from settlements, easing to [VillageWildlifeScale] within about a cell and a half of a structure. */
internal fun villageScale(pose: CreaturePose, village: List<GroundPoint>): Float {
    if (village.isEmpty()) return 1f
    val nearest = village.minOf { kotlin.math.hypot((it.x - pose.ground.x).toFloat(), (it.z - pose.ground.z).toFloat()) }
    val t = ((nearest - 1.5f) / 3f).coerceIn(0f, 1f)
    return VillageWildlifeScale + (1f - VillageWildlifeScale) * t * t * (3f - 2f * t)
}

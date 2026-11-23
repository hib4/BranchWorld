package id.branchworld.renderer

import de.fabmax.kool.Assets
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.modules.gltf.GltfFile
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.loadGltfFile
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.releaseWith
import id.branchworld.game.ecology.CreatureAction
import id.branchworld.game.ecology.Species
import id.branchworld.game.simulation.CreatureHeading
import id.branchworld.game.simulation.CreaturePose
import id.branchworld.game.simulation.WorldRenderProjection
import id.branchworld.game.terrain.TerrainMap
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal suspend fun loadCreatureAssets(wildlife: Boolean, living: Boolean = false): Map<Species, GltfFile> = buildMap {
    put(Species.Rabbit, Assets.loadGltfFile(DioramaAsset.Bunny.path).getOrThrow())
    put(Species.Deer, Assets.loadGltfFile(DioramaAsset.Deer.path).getOrThrow())
    if (wildlife) put(Species.Fox, Assets.loadGltfFile(DioramaAsset.Fox.path).getOrThrow())
    if (living) put(Species.Wolf, Assets.loadGltfFile(DioramaAsset.Wolf.path).getOrThrow())
}

/** A scene owns imported textures and recycles the model bodies of dead creatures. */
internal class CreatureModels(
    private val scene: Scene,
    private val assets: Map<Species, GltfFile>,
    private val terrain: TerrainMap,
) {
    private val active = mutableMapOf<Long, Body>()
    private val unused = mutableMapOf<Species, MutableList<Body>>()
    private val textures = mutableSetOf<Texture2d>()
    private var displayedTick: Long? = null
    private var displayedBranch: Int? = null
    private var previousReducedMotion = false
    private var lastFrame: TimeMark? = null
    private var lastTickAt: TimeMark? = null
    /** Simulated ticks per wall second, measured from the projections as they arrive. Cosmetic only. */
    private var tickRate = 20f
    private var stillFor = 0f
    val ready get() = textures.all { it.isLoaded }

    /** Where a creature is drawn right now, for rings and markers that follow it. */
    fun shownAt(id: Long): Vec3f? = active[id]?.shown()

    fun show(world: WorldRenderProjection, reducedMotion: Boolean = false) {
        val now = TimeSource.Monotonic.markNow()
        val dt = (lastFrame?.elapsedNow()?.inWholeMicroseconds ?: 0L).coerceIn(0L, 100_000L) / 1_000_000f
        lastFrame = now
        val fresh = displayedTick != world.tick || displayedBranch != world.branchId || previousReducedMotion != reducedMotion
        if (fresh) {
            val previousTick = displayedTick
            val continuous = !reducedMotion && !previousReducedMotion && world.interpolateCreatures && displayedBranch == world.branchId &&
                previousTick != null && world.tick - previousTick in 1..16
            if (continuous) {
                val wall = (lastTickAt?.elapsedNow()?.inWholeMicroseconds ?: 50_000L).coerceAtLeast(1_000L) / 1_000_000f
                val measured = (world.tick - checkNotNull(previousTick)) / wall
                tickRate += (measured.coerceIn(2f, 200f) - tickRate) * 0.35f
            }
            lastTickAt = now
            stillFor = 0f
            displayedTick = world.tick
            displayedBranch = world.branchId
            previousReducedMotion = reducedMotion
            retarget(world, continuous, reducedMotion)
        } else {
            stillFor += dt
        }
        // Paused: creatures finish the step they were on, then stand.
        val rate = if (stillFor > 0.25f) 0f else tickRate
        for (body in active.values) body.animate(dt, rate, reducedMotion)
    }

    private fun retarget(world: WorldRenderProjection, continuous: Boolean, reducedMotion: Boolean) {
        // Wildlife eases down beside homes and fields so a village keeps its scale. Cosmetic only.
        val village = world.settlements.orEmpty().flatMap { site ->
            site.village?.let { it.structures.map { structure -> structure.ground } + it.center } ?: site.homes
        }
        val arrivals = world.life?.arrivals.orEmpty()
        val speciesById = world.creatures.associate { it.id to it.species }
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val (id, body) = iterator.next()
            if (speciesById[id] == body.species) continue
            body.model.isVisible = false
            unused.getOrPut(body.species, ::mutableListOf).add(body)
            iterator.remove()
        }
        for (pose in world.creatures) {
            val previous = active[pose.id]
            val body = active.getOrPut(pose.id) {
                unused[pose.species]?.removeLastOrNull() ?: makeBody(pose.species)
            }
            body.retarget(world.tick, pose, continuous && previous != null, reducedMotion, villageScale(pose, village),
                dropIn = previous == null && pose.id in arrivals && !reducedMotion)
            body.model.isVisible = true
        }
    }

    private fun makeBody(species: Species): Body {
        val model = assets.getValue(species).makeModel(GltfLoadConfig(
            assetLoader = Assets.defaultLoader,
            pbrBlock = {
                if (Species.Fox in assets) {
                    metallic(0f)
                    roughness(0.89f)
                    modelCustomizer = { wildlifeCoat(species) }
                }
            },
        ))
        for (texture in model.textures.values) {
            if (textures.add(texture)) texture.releaseWith(scene)
        }
        // Kool's parsed species file reuses its textures for every model made from it.
        model.textures.clear()
        scene.addNode(model)
        return Body(species, model, terrain)
    }

    private class Body(val species: Species, val model: Model, val terrain: TerrainMap) {
        private val clips = CreatureClips.associateWith { name ->
            model.animations.indexOfFirst { it.name == name && it.duration > 0f }
                .also { check(it >= 0) { "The $species model is missing animation $name" } }
        }
        private var enabledClip = -1
        private var x = 0f
        private var z = 0f
        private var yaw = 0f
        private var targetX = 0f
        private var targetZ = 0f
        private var targetYaw = 0f
        /** Cells per tick on the current gait. */
        private var pace = 0.5f
        private var clipPhase = 0f
        private var clipRate = 0.5f
        private var held = false
        private var scale = 1f
        private var lean = 0f
        private var drop = 0f
        private var placed = false

        fun shown() = Vec3f(x, terrain.heightAt(x, z), z)

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
            val clip = clips.getValue(when (pose.action) {
                CreatureAction.Walking -> "walk"
                CreatureAction.Stalking -> if (watchingRefuge) "idle" else "walk"
                CreatureAction.Fleeing -> if (holdingRefuge) "idle" else "run"
                CreatureAction.Chasing -> "run"
                CreatureAction.Eating, CreatureAction.Drinking, CreatureAction.Digging -> "eat"
                else -> "idle"
            })
            if (clip != enabledClip) {
                enabledClip = clip
                model.enableAnimation(clip)
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
            val animation = model.animations[clip]
            if (held) clipPhase = if (pose.action == CreatureAction.Drinking) animation.duration * 0.42f else 0f
            else if (!interpolate) clipPhase = ((tick * 0.05 * clipRate + (pose.id % 13L) * 0.071) % animation.duration).toFloat()
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
            val y = terrain.heightAt(x, z) + lift
            model.transform.setIdentity()
                .translate(x, y, z)
                .rotate(yaw.deg, Vec3f.Y_AXIS)
                .rotate(lean.deg, Vec3f.X_AXIS)
                .scale(scale)
            val animation = model.animations[enabledClip]
            if (!held) clipPhase = (clipPhase + dt * clipRate * (tickRate / 20f).coerceIn(0f, 2.5f)) % animation.duration
            animation.progress = clipPhase
            model.applyAnimation(0f)
        }
    }
}

private fun headingYaw(heading: CreatureHeading) = when (heading) {
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

/** 1 away from settlements, easing to [VillageWildlifeScale] within about a cell and a half of a structure. */
private fun villageScale(pose: CreaturePose, village: List<id.branchworld.game.simulation.GroundPoint>): Float {
    if (village.isEmpty()) return 1f
    val nearest = village.minOf { kotlin.math.hypot((it.x - pose.ground.x).toFloat(), (it.z - pose.ground.z).toFloat()) }
    val t = ((nearest - 1.5f) / 3f).coerceIn(0f, 1f)
    return VillageWildlifeScale + (1f - VillageWildlifeScale) * t * t * (3f - 2f * t)
}

private fun KslProgram.wildlifeCoat(species: Species) {
    if (species == Species.Deer) return
    fragmentStage {
        main {
            val material = getFloat4Port("materialColor")
            val source = checkNotNull(material.input.input)
            val coat = if (species == Species.Rabbit) {
                val light = float1Var(dot(source.rgb, float3Value(0.35f.const, 0.50f.const, 0.15f.const)), "coatLight")
                mix(source.rgb, float3Value(light * 1.31f.const, light * 1.24f.const, light * 1.14f.const), 0.63f.const)
            } else if (species == Species.Wolf) {
                // A grey wolf's coat: the toy dog's colours drawn toward cool slate.
                val light = float1Var(dot(source.rgb, float3Value(0.30f.const, 0.55f.const, 0.15f.const)), "wolfLight")
                mix(source.rgb, float3Value(light * 0.84f.const, light * 0.88f.const, light * 0.95f.const), 0.80f.const)
            } else {
                source.rgb * float3Value(1.16f.const, 0.91f.const, 0.83f.const)
            }
            material.input(float4Value(coat, source.a))
        }
    }
}

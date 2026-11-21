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

internal suspend fun loadCreatureAssets(wildlife: Boolean): Map<Species, GltfFile> = buildMap {
    put(Species.Rabbit, Assets.loadGltfFile(DioramaAsset.Bunny.path).getOrThrow())
    put(Species.Deer, Assets.loadGltfFile(DioramaAsset.Deer.path).getOrThrow())
    if (wildlife) put(Species.Fox, Assets.loadGltfFile(DioramaAsset.Fox.path).getOrThrow())
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
    private var motionStarted: TimeMark? = null
    private var previousReducedMotion = false
    val ready get() = textures.all { it.isLoaded }

    fun show(world: WorldRenderProjection, reducedMotion: Boolean = false) {
        if (displayedTick == world.tick && displayedBranch == world.branchId && previousReducedMotion == reducedMotion) {
            if (!world.interpolateCreatures && motionStarted != null) {
                active.values.forEach { it.place(1f) }
                motionStarted = null
            }
            motionStarted?.let { started ->
                val progress = (started.elapsedNow().inWholeMicroseconds / 50_000f).coerceIn(0f, 1f)
                active.values.forEach { it.place(progress) }
                if (progress == 1f) motionStarted = null
            }
            return
        }
        val continuous = !reducedMotion && !previousReducedMotion && world.interpolateCreatures && displayedBranch == world.branchId &&
            displayedTick == world.tick - 1
        motionStarted = if (continuous) TimeSource.Monotonic.markNow() else null
        displayedTick = world.tick
        displayedBranch = world.branchId
        previousReducedMotion = reducedMotion
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
            body.show(world.tick, pose, continuous && previous != null, reducedMotion)
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
        private var origin = Vec3f.ZERO
        private var destination = Vec3f.ZERO
        private var fromYaw = 0f
        private var toYaw = 0f
        private var clipPhase = 0f
        private var clipAdvance = 0f
        private var scale = 1f
        private var lean = 0f

        fun show(tick: Long, pose: CreaturePose, interpolate: Boolean, reducedMotion: Boolean) {
            val heading = when (pose.heading) {
                CreatureHeading.PositiveZ -> 0f
                CreatureHeading.PositiveX -> 90f
                CreatureHeading.NegativeZ -> 180f
                CreatureHeading.NegativeX -> -90f
            }
            fromYaw = if (interpolate) toYaw else heading
            toYaw = heading
            val position = pose.ground
            val next = cellPosition(position.x, position.z, position.elevationTwice)
            origin = if (interpolate) destination else next
            destination = next
            val holdingRefuge = pose.action == CreatureAction.Fleeing && pose.ground == pose.destination
            val watchingRefuge = pose.action == CreatureAction.Stalking && pose.ground == pose.destination
            val clip = clips.getValue(when (pose.action) {
                CreatureAction.Walking -> "walk"
                CreatureAction.Stalking -> if (watchingRefuge) "idle" else "walk"
                CreatureAction.Fleeing -> if (holdingRefuge) "idle" else "run"
                CreatureAction.Chasing -> "run"
                CreatureAction.Eating -> "eat"
                CreatureAction.Drinking -> "eat"
                else -> "idle"
            })
            if (clip != enabledClip) {
                enabledClip = clip
                model.enableAnimation(clip)
            }
            val animation = model.animations[clip]
            val rate = when (pose.action) {
                CreatureAction.Walking -> if (species == Species.Rabbit) 1.22f else 0.87f
                CreatureAction.Fleeing -> 1.28f
                CreatureAction.Chasing -> 1.12f
                CreatureAction.Stalking -> 0.64f
                CreatureAction.Eating -> 0.73f
                else -> 0.50f
            }
            scale = when (species) {
                Species.Rabbit -> 0.182f
                Species.Deer -> 0.253f
                Species.Fox -> 0.205f
            } * if (pose.intent != null) WildlifeBodyScale else 1f
            if (pose.juvenile) scale *= 0.73f
            lean = when {
                pose.action == CreatureAction.Stalking -> 7f
                pose.action == CreatureAction.Drinking -> 10f
                else -> 0f
            }
            val held = reducedMotion || pose.action == CreatureAction.Drinking || pose.action == CreatureAction.Resting ||
                holdingRefuge || watchingRefuge
            clipPhase = if (held) {
                if (pose.action == CreatureAction.Drinking) animation.duration * 0.42f else 0f
            } else ((tick * 0.05 * rate + (pose.id % 13L) * 0.071) % animation.duration).toFloat()
            clipAdvance = if (interpolate && !held) 0.05f * rate else 0f
            place(if (interpolate) 0f else 1f)
        }

        fun place(progress: Float) {
            val x = origin.x + (destination.x - origin.x) * progress
            val z = origin.z + (destination.z - origin.z) * progress
            val y = terrain.heightAt(x, z)
            val yawDelta = ((toYaw - fromYaw + 540f) % 360f) - 180f
            model.transform.setIdentity()
                .translate(x, y, z)
                .rotate((fromYaw + yawDelta * progress).deg, Vec3f.Y_AXIS)
                .rotate(lean.deg, Vec3f.X_AXIS)
                .scale(scale)
            val animation = model.animations[enabledClip]
            animation.progress = (clipPhase + animation.duration - clipAdvance * (1f - progress)) % animation.duration
            model.applyAnimation(0f)
        }
    }
}

internal const val WildlifeBodyScale = 0.76f

private fun KslProgram.wildlifeCoat(species: Species) {
    if (species == Species.Deer) return
    fragmentStage {
        main {
            val material = getFloat4Port("materialColor")
            val source = checkNotNull(material.input.input)
            val coat = if (species == Species.Rabbit) {
                val light = float1Var(dot(source.rgb, float3Value(0.35f.const, 0.50f.const, 0.15f.const)), "coatLight")
                mix(source.rgb, float3Value(light * 1.31f.const, light * 1.24f.const, light * 1.14f.const), 0.63f.const)
            } else {
                source.rgb * float3Value(1.16f.const, 0.91f.const, 0.83f.const)
            }
            material.input(float4Value(coat, source.a))
        }
    }
}

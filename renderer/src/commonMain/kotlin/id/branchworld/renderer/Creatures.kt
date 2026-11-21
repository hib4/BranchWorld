package id.branchworld.renderer

import de.fabmax.kool.Assets
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
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

internal suspend fun loadCreatureAssets(): Map<Species, GltfFile> = mapOf(
    Species.Rabbit to Assets.loadGltfFile(DioramaAsset.Bunny.path).getOrThrow(),
    Species.Deer to Assets.loadGltfFile(DioramaAsset.Deer.path).getOrThrow(),
)

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
        val model = assets.getValue(species).makeModel(GltfLoadConfig(assetLoader = Assets.defaultLoader))
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
            val clip = clips.getValue(when (pose.action) {
                CreatureAction.Walking -> "walk"
                CreatureAction.Eating -> "eat"
                else -> "idle"
            })
            if (clip != enabledClip) {
                enabledClip = clip
                model.enableAnimation(clip)
            }
            val animation = model.animations[clip]
            val rate = when (pose.action) {
                CreatureAction.Walking -> if (species == Species.Rabbit) 1.22f else 0.87f
                CreatureAction.Eating -> 0.73f
                else -> 0.50f
            }
            val held = reducedMotion || pose.action == CreatureAction.Drinking || pose.action == CreatureAction.Resting
            clipPhase = if (held) 0f else ((tick * 0.05 * rate + (pose.id % 13L) * 0.071) % animation.duration).toFloat()
            clipAdvance = if (interpolate && !held) 0.05f * rate else 0f
            place(if (interpolate) 0f else 1f)
        }

        fun place(progress: Float) {
            val scale = if (species == Species.Rabbit) 0.182f else 0.253f
            val x = origin.x + (destination.x - origin.x) * progress
            val z = origin.z + (destination.z - origin.z) * progress
            val y = terrain.heightAt(x, z)
            val yawDelta = ((toYaw - fromYaw + 540f) % 360f) - 180f
            model.transform.setIdentity()
                .translate(x, y, z)
                .rotate((fromYaw + yawDelta * progress).deg, Vec3f.Y_AXIS)
                .scale(scale)
            val animation = model.animations[enabledClip]
            animation.progress = (clipPhase + animation.duration - clipAdvance * (1f - progress)) % animation.duration
            model.applyAnimation(0f)
        }
    }
}

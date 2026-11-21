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
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal suspend fun loadCreatureAssets(): Map<Species, GltfFile> = mapOf(
    Species.Rabbit to Assets.loadGltfFile(DioramaAsset.Bunny.path).getOrThrow(),
    Species.Deer to Assets.loadGltfFile(DioramaAsset.Deer.path).getOrThrow(),
)

/** A scene owns imported textures and recycles the model bodies of dead creatures. */
internal class CreatureModels(private val scene: Scene, private val assets: Map<Species, GltfFile>) {
    private val active = mutableMapOf<Long, Body>()
    private val unused = mutableMapOf<Species, MutableList<Body>>()
    private val textures = mutableSetOf<Texture2d>()
    private var displayedTick: Long? = null
    private var displayedBranch: Int? = null
    private var motionStarted: TimeMark? = null
    val ready get() = textures.all { it.isLoaded }

    fun show(world: WorldRenderProjection) {
        if (displayedTick == world.tick && displayedBranch == world.branchId) {
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
        val continuous = world.interpolateCreatures && displayedBranch == world.branchId &&
            displayedTick == world.tick - 1
        motionStarted = if (continuous) TimeSource.Monotonic.markNow() else null
        displayedTick = world.tick
        displayedBranch = world.branchId
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
            body.show(world.tick, pose, continuous && previous != null)
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
        return Body(species, model)
    }

    private class Body(val species: Species, val model: Model) {
        private val clips = CreatureClips.associateWith { name ->
            model.animations.indexOfFirst { it.name == name && it.duration > 0f }
                .also { check(it >= 0) { "The $species model is missing animation $name" } }
        }
        private var enabledClip = -1
        private var origin = Vec3f.ZERO
        private var destination = Vec3f.ZERO
        private var yaw = 0f

        fun show(tick: Long, pose: CreaturePose, interpolate: Boolean) {
            yaw = when (pose.heading) {
                CreatureHeading.PositiveZ -> 0f
                CreatureHeading.PositiveX -> 90f
                CreatureHeading.NegativeZ -> 180f
                CreatureHeading.NegativeX -> -90f
            }
            val position = pose.ground
            val next = cellPosition(position.x, position.z, position.elevationTwice)
            origin = if (interpolate) destination else next
            destination = next
            place(if (interpolate) 0f else 1f)
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
            animation.progress = when (pose.action) {
                CreatureAction.Drinking, CreatureAction.Resting -> 0f
                else -> (tick * 0.05 % animation.duration).toFloat()
            }
            model.applyAnimation(0f)
        }

        fun place(progress: Float) {
            val scale = if (species == Species.Rabbit) 0.23f else 0.29f
            model.transform.setIdentity()
                .translate(
                    origin.x + (destination.x - origin.x) * progress,
                    origin.y + (destination.y - origin.y) * progress,
                    origin.z + (destination.z - origin.z) * progress,
                )
                .rotate(yaw.deg, Vec3f.Y_AXIS)
                .scale(scale)
        }
    }
}

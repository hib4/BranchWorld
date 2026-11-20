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
    val ready get() = textures.all { it.isLoaded }

    fun show(world: WorldRenderProjection) {
        if (displayedTick == world.tick) return
        displayedTick = world.tick
        val ids = world.creatures.mapTo(mutableSetOf()) { it.id }
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val (id, body) = iterator.next()
            if (id in ids) continue
            body.model.isVisible = false
            unused.getOrPut(body.species, ::mutableListOf).add(body)
            iterator.remove()
        }
        for (pose in world.creatures) {
            val body = active.getOrPut(pose.id) {
                unused[pose.species]?.removeLastOrNull() ?: makeBody(pose.species)
            }
            body.show(world.tick, pose)
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

        fun show(tick: Long, pose: CreaturePose) {
            val yaw = when (pose.heading) {
                CreatureHeading.PositiveZ -> 0f
                CreatureHeading.PositiveX -> 90f
                CreatureHeading.NegativeZ -> 180f
                CreatureHeading.NegativeX -> -90f
            }
            val position = pose.ground
            val scale = if (species == Species.Rabbit) 0.23f else 0.29f
            model.transform.setIdentity()
                .translate(cellPosition(position.x, position.z, position.elevationTwice))
                .rotate(yaw.deg, Vec3f.Y_AXIS)
                .scale(scale)
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
    }
}

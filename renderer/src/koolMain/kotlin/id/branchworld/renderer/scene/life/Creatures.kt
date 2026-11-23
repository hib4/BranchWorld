package id.branchworld.renderer.scene.life

import de.fabmax.kool.Assets
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.gltf.GltfFile
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.loadGltfFile
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.releaseWith
import id.branchworld.renderer.assets.CreatureClips
import id.branchworld.renderer.assets.DioramaAsset
import id.branchworld.renderer.motion.CreatureMotion
import id.branchworld.renderer.motion.CreaturePacing
import id.branchworld.renderer.motion.villageGround
import id.branchworld.renderer.motion.villageScale
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.projection.CreaturePose
import id.branchworld.simulation.projection.WorldRenderProjection
import id.branchworld.simulation.terrain.TerrainMap
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
    private val pacing = CreaturePacing()
    private val clock = TimeSource.Monotonic.markNow()
    private var lastFrame: TimeMark? = null
    val ready get() = textures.all { it.isLoaded }

    /** Where a creature is drawn right now, for rings and markers that follow it. */
    fun shownAt(id: Long): Vec3f? = active[id]?.shown()

    fun show(world: WorldRenderProjection, reducedMotion: Boolean = false) {
        val now = TimeSource.Monotonic.markNow()
        val dt = (lastFrame?.elapsedNow()?.inWholeMicroseconds ?: 0L).coerceIn(0L, 100_000L) / 1_000_000f
        lastFrame = now
        val continuous = pacing.frame(world, reducedMotion, clock.elapsedNow().inWholeNanoseconds, dt)
        if (continuous != null) retarget(world, continuous, reducedMotion)
        val rate = pacing.rate
        for (body in active.values) body.animate(dt, rate, reducedMotion)
    }

    private fun retarget(world: WorldRenderProjection, continuous: Boolean, reducedMotion: Boolean) {
        val village = villageGround(world)
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

    private class Body(val species: Species, val model: Model, terrain: TerrainMap) {
        private val clips = CreatureClips.associateWith { name ->
            model.animations.indexOfFirst { it.name == name && it.duration > 0f }
                .also { check(it >= 0) { "The $species model is missing animation $name" } }
        }
        private val motion = CreatureMotion(species, terrain) { model.animations[clips.getValue(it)].duration }
        private var enabledClip = -1

        fun shown() = motion.shown().let { Vec3f(it.x, it.y, it.z) }

        fun retarget(tick: Long, pose: CreaturePose, interpolate: Boolean, reducedMotion: Boolean, nearVillage: Float, dropIn: Boolean) {
            motion.retarget(tick, pose, interpolate, reducedMotion, nearVillage, dropIn)
            apply()
        }

        fun animate(dt: Float, tickRate: Float, reducedMotion: Boolean) {
            motion.animate(dt, tickRate, reducedMotion)
            apply()
        }

        private fun apply() {
            val clip = clips.getValue(motion.clip)
            if (clip != enabledClip) {
                enabledClip = clip
                model.enableAnimation(clip)
            }
            model.transform.setIdentity()
                .translate(motion.x, motion.y, motion.z)
                .rotate(motion.yaw.deg, Vec3f.Y_AXIS)
                .rotate(motion.lean.deg, Vec3f.X_AXIS)
                .scale(motion.scale)
            model.animations[enabledClip].progress = motion.clipPhase
            model.applyAnimation(0f)
        }
    }
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

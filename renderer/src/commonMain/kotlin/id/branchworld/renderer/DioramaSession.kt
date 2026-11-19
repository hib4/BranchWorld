package id.branchworld.renderer

import de.fabmax.kool.Assets
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.loadGltfFile
import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.pipeline.backend.stats.BackendStats
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.InstanceLayouts
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.animation.Animation
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.releaseDelayed
import id.branchworld.game.simulation.BunnyHeading
import id.branchworld.game.simulation.WorldRenderProjection
import id.branchworld.game.simulation.WorldRenderSource
import id.branchworld.game.terrain.PropKind
import id.branchworld.game.terrain.TerrainMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.TimeSource
import kotlin.time.TimeMark

internal class DioramaSession(
    private val context: KoolContext,
    private val controller: PreviewController,
    private val output: DioramaOutput? = null,
    private val onIdle: (() -> Unit)? = null,
) {
    private var scene: Scene? = null
    private var renderedWorld: WorldRenderSource? = null
    private var load: Job? = null
    private var bunny: BunnyVisual? = null
    private var propModels = emptyList<Model>()
    private var assetFrame = -1
    private var idleAfterFrame: Int? = null
    private val timing = FrameTiming("renderer")

    init {
        context.onRender += { update() }
        context.onShutdown += { dispose(delay = false) }
    }

    private fun update() {
        // Scene creation, mutation, and removal run on Kool's frontend render thread.
        val world = controller.renderWorld
        if (world == null) {
            dispose(delay = true)
            if (idleAfterFrame?.let { Time.frameCount >= it } == true) {
                idleAfterFrame = null
                onIdle?.invoke()
            }
            return
        }
        if (renderedWorld !== world) dispose(delay = true)
        idleAfterFrame = null
        val current = scene ?: buildScene(world.terrain).also {
            scene = it
            renderedWorld = world
            controller.status.value = "Loading island…"
            output?.attach(it)
            context.addScene(it)
            load = it.coroutineScope.launch { loadModels(it, world) }
        }
        current.isVisible = controller.foreground.value
        if (!current.isVisible) return
        val pose = controller.camera.value
        val horizontal = pose.distance * cos(pose.pitch)
        current.camera.lookAt.set(pose.targetX, 0.30f, pose.targetZ)
        current.camera.position.set(
            pose.targetX + horizontal * sin(pose.yaw),
            0.30f + pose.distance * sin(pose.pitch),
            pose.targetZ + horizontal * cos(pose.yaw),
        )
        val animal = bunny
        animal?.show(world.poses.value)
        output?.update()
        if (animal != null && animal.model.textures.values.all { it.isLoaded } &&
            propModels.all { model -> model.textures.values.all { it.isLoaded } }
        ) {
            if (assetFrame < 0) assetFrame = Time.frameCount
            if (Time.frameCount == assetFrame + 1) {
                val size = context.window.framebufferSize
                val elapsed = controller.enteredAt.value?.elapsedNow()?.inWholeMilliseconds
                println("BranchWorldRenderer seed=${world.terrain.key.seed} firstAssetFrameMs=$elapsed backend=${context.backend.name} gpu=${context.backend.deviceName} nativeSurface=${size.x}x${size.y}")
                println("BranchWorldRenderer activeBytes buffers=${BackendStats.totalBufferSize} textures=${BackendStats.totalTextureSize}")
            }
            if (Time.frameCount > assetFrame) controller.status.value = "Island ready"
            timing.frame()
        }
    }

    private fun dispose(delay: Boolean) {
        val previous = scene ?: return
        scene = null
        renderedWorld = null
        load?.cancel()
        load = null
        bunny = null
        propModels = emptyList()
        assetFrame = -1
        timing.reset()
        output?.detach()
        context.removeScene(previous)
        if (onIdle != null) previous.onRelease {
            // Scene release schedules more GPU work, including texture disposal.
            idleAfterFrame = Time.frameCount + 3
        }
        if (delay) previous.releaseDelayed(2) else previous.release()
    }

    private fun buildScene(terrain: TerrainMap) = Scene("Island ${terrain.key.seed}").apply {
        mainRenderPass.clearColor = ClearColorFill(Color(0.11f, 0.21f, 0.26f))
        camera.setClipRange(0.1f, 60f)
        lighting.singleDirectionalLight {
            setup(Vec3f(-0.8f, -1.3f, -0.45f)).setColor(Color.WHITE, 1.2f)
        }
        lighting.addDirectionalLight {
            setup(Vec3f(0.6f, -0.45f, 1f)).setColor(Color(0.72f, 0.88f, 1f), 0.5f)
        }
        addIsland(terrain)
        onRelease { println("BranchWorldRenderer sceneReleased") }
    }

    private suspend fun loadModels(target: Scene, world: WorldRenderSource) {
        try {
            propModels = listOfNotNull(
                target.placeInstances(world.terrain, PropKind.Pine, DioramaAsset.Pine, 0.52f),
                target.placeInstances(world.terrain, PropKind.Rock, DioramaAsset.Rock, 0.55f),
            )
            val imported = Assets.loadGltfFile(DioramaAsset.Bunny.path).getOrThrow()
            val model = imported.makeModel(GltfLoadConfig(assetLoader = Assets.defaultLoader))
            target.attachWhileCurrent(model)
            val walk = model.animations.indexOfFirst { it.name == BunnyAnimation && it.duration > 0f }
            check(walk >= 0) { "The bunny is missing its walk clip" }
            model.enableAnimation(walk)
            bunny = BunnyVisual(model, model.animations[walk]).also { visual ->
                visual.show(world.poses.value)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (scene === target) controller.status.value = "Could not load the island assets"
            println("BranchWorldRenderer assetsFailed: ${error.stackTraceToString()}")
        }
    }

    private suspend fun Scene.placeInstances(
        terrain: TerrainMap,
        kind: PropKind,
        asset: DioramaAsset,
        assetScale: Float,
    ): Model? {
        val matrices = buildList {
            for (index in 0 until terrain.propCount) {
                val prop = terrain.prop(index)
                if (prop.kind != kind) continue
                val cell = checkNotNull(terrain.cellAt(prop.x, prop.z))
                val center = cellPosition(prop.x, prop.z, cell.elevationTwice)
                val scale = assetScale * (prop.scalePercent / 100f)
                // Both authored prop roots are offset 0.05 units below their mesh base.
                add(MutableMat4f()
                    .translate(center.x, center.y + 0.05f * scale, center.z)
                    .rotate(prop.yawDegrees.toFloat().deg, Vec3f.Y_AXIS)
                    .scale(scale))
            }
        }
        if (matrices.isEmpty()) return null
        val imported = Assets.loadGltfFile(asset.path).getOrThrow()
        return imported.makeModel(GltfLoadConfig(
            assetLoader = Assets.defaultLoader,
            applyTransforms = true,
            loadAnimations = false,
            instanceLayout = InstanceLayouts.ModelMat,
        )).also { model ->
            attachWhileCurrent(model)
            model.meshes.values.forEach { mesh ->
                mesh.isFrustumChecked = false
                mesh.addInstances<InstanceLayouts.ModelMat>(matrices.size) { buffer ->
                    for (matrix in matrices) buffer.put { set(it.modelMat, matrix) }
                }
            }
        }
    }

    private suspend fun Scene.attachWhileCurrent(model: Model) {
        if (scene !== this || !currentCoroutineContext().isActive) {
            model.release()
            throw CancellationException("The island scene was replaced while loading its models")
        }
        addNode(model)
    }
}

private class BunnyVisual(val model: Model, private val walk: Animation) {
    private var displayed: WorldRenderProjection? = null

    fun show(pose: WorldRenderProjection) {
        if (displayed == pose) return
        displayed = pose
        val yaw = when (pose.heading) {
            BunnyHeading.PositiveZ -> 0f
            BunnyHeading.PositiveX -> 90f
            BunnyHeading.NegativeZ -> 180f
            BunnyHeading.NegativeX -> -90f
        }
        model.transform.setIdentity()
            .translate(cellPosition(pose.x, pose.z, pose.elevationTwice))
            .rotate(yaw.deg, Vec3f.Y_AXIS)
            .scale(0.23f)
        // Sample the walk clip from the simulation tick even when render frames are skipped.
        if (pose.moving) walk.progress = (pose.tick * GaitSecondsPerTick % walk.duration).toFloat()
        model.applyAnimation(0f)
    }

    private companion object {
        const val GaitSecondsPerTick = 0.05
    }
}

internal interface DioramaOutput {
    fun attach(scene: Scene)
    fun update()
    fun detach()
}

internal class FrameTiming(private val kind: String) {
    private var start: TimeMark? = null
    private var previous: TimeMark? = null
    private val samples = ArrayList<Double>()
    private var reported = false

    fun reset() {
        start = null
        previous = null
        samples.clear()
        reported = false
    }

    fun frame() {
        if (reported) return
        val first = start ?: TimeSource.Monotonic.markNow().also { start = it }
        val prior = previous
        previous = TimeSource.Monotonic.markNow()
        val elapsed = first.elapsedNow().inWholeMilliseconds
        if (prior == null || elapsed < 2_000) return
        samples += prior.elapsedNow().inWholeMicroseconds / 1000.0
        if (elapsed < 12_000) return
        samples.sort()
        println("BranchWorldRenderer $kind frames=${samples.size} p50Ms=${samples[samples.lastIndex / 2]} p95Ms=${samples[(samples.lastIndex * 95) / 100]}")
        reported = true
    }
}

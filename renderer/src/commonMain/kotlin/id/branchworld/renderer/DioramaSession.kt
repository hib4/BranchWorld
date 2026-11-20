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
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.releaseDelayed
import id.branchworld.game.ecology.Weather
import id.branchworld.game.simulation.ClimatePose
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
    private var creatures: CreatureModels? = null
    private var forage: ForageMeshes? = null
    private var previousClimate: ClimatePose? = null
    private var propModels = emptyList<Model>()
    private var settlements: SettlementModels? = null
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
            forage = ForageMeshes(it, world.poses.value.springs)
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
        val wildlife = creatures
        val snapshot = world.poses.value
        if (snapshot.climate != previousClimate) {
            snapshot.climate?.let { current.showClimate(it) }
            previousClimate = snapshot.climate
        }
        wildlife?.show(snapshot)
        settlements?.show(snapshot.settlements.orEmpty())
        forage?.show(snapshot.forage)
        output?.update()
        if (wildlife != null && wildlife.ready && settlements?.ready != false &&
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
        creatures = null
        forage = null
        previousClimate = null
        propModels = emptyList()
        settlements = null
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

    private fun Scene.showClimate(climate: ClimatePose) {
        val rain = climate.weather == Weather.Rain
        val key = if (rain) Color(0.75f, 0.85f, 1f) else Color(1f, 0.98f, 0.90f)
        val ambient = Color(0.60f, 0.78f, 1f)
        lighting.lights[0].setColor(key, 0.06f + climate.daylight * if (rain) 0.70f else 1.18f)
        lighting.lights[1].setColor(ambient, 0.26f + climate.daylight * if (rain) 0.32f else 0.22f)
    }

    private suspend fun loadModels(target: Scene, world: WorldRenderSource) {
        try {
            propModels = listOfNotNull(
                target.placeInstances(world.terrain, PropKind.Pine, DioramaAsset.Pine, 0.52f),
                target.placeInstances(world.terrain, PropKind.Rock, DioramaAsset.Rock, 0.55f),
            )
            val assets = loadCreatureAssets()
            val towns = if (world.poses.value.settlements != null) SettlementModels.load(target) else null
            if (scene !== target || !currentCoroutineContext().isActive) {
                throw CancellationException("The island scene was replaced while loading wildlife")
            }
            settlements = towns
            creatures = CreatureModels(target, assets).also { it.show(world.poses.value) }
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

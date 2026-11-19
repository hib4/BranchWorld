package id.branchworld.renderer

import de.fabmax.kool.Assets
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.loadGltfFile
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.pipeline.backend.stats.BackendStats
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.releaseDelayed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private var load: Job? = null
    private var bunny: Model? = null
    private var assetFrame = -1
    private var idleAfterFrame: Int? = null
    private val timing = FrameTiming("renderer")

    init {
        context.onRender += { update() }
        context.onShutdown += { dispose(delay = false) }
    }

    private fun update() {
        // Scene creation, mutation, and removal run on Kool's frontend render thread.
        if (!controller.active.value) {
            dispose(delay = true)
            if (idleAfterFrame?.let { Time.frameCount >= it } == true) {
                idleAfterFrame = null
                onIdle?.invoke()
            }
            return
        }
        idleAfterFrame = null
        val current = scene ?: buildScene().also {
            scene = it
            controller.status.value = "Loading island…"
            output?.attach(it)
            context.addScene(it)
            load = it.coroutineScope.launch { loadModels(it) }
        }
        current.isVisible = controller.foreground.value
        if (!current.isVisible) return
        val pose = controller.camera.value
        val horizontal = pose.distance * cos(pose.pitch)
        current.camera.lookAt.set(pose.targetX, 0.35f, pose.targetZ)
        current.camera.position.set(
            pose.targetX + horizontal * sin(pose.yaw),
            0.35f + pose.distance * sin(pose.pitch),
            pose.targetZ + horizontal * cos(pose.yaw),
        )
        val animal = bunny
        if (!controller.paused.value) animal?.applyAnimation(Time.deltaT.coerceAtMost(0.05f))
        output?.update()
        if (animal != null && animal.textures.values.all { it.isLoaded }) {
            if (assetFrame < 0) assetFrame = Time.frameCount
            if (Time.frameCount == assetFrame + 1) {
                val size = context.window.framebufferSize
                val elapsed = controller.enteredAt.value?.elapsedNow()?.inWholeMilliseconds
                println("BranchWorldRenderer firstAssetFrameMs=$elapsed backend=${context.backend.name} gpu=${context.backend.deviceName} nativeSurface=${size.x}x${size.y}")
                println("BranchWorldRenderer activeBytes buffers=${BackendStats.totalBufferSize} textures=${BackendStats.totalTextureSize}")
                controller.status.value = "Island ready"
            }
            timing.frame()
        }
    }

    private fun dispose(delay: Boolean) {
        val previous = scene ?: return
        scene = null
        load?.cancel()
        load = null
        bunny = null
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

    private fun buildScene() = Scene("Diorama").apply {
        mainRenderPass.clearColor = ClearColorFill(Color(0.12f, 0.24f, 0.31f))
        camera.setClipRange(0.1f, 60f)
        lighting.singleDirectionalLight {
            setup(Vec3f(-0.8f, -1.3f, -0.45f)).setColor(Color.WHITE, 1.2f)
        }
        lighting.addDirectionalLight {
            setup(Vec3f(0.6f, -0.45f, 1f)).setColor(Color(0.72f, 0.88f, 1f), 0.5f)
        }
        addColorMesh("Island and water") {
            generate {
                color = Color(0.11f, 0.42f, 0.47f)
                cylinder {
                    radius = 4.7f
                    height = 0.12f
                    steps = 32
                    origin.y = -0.55f
                }
                color = Color(0.43f, 0.34f, 0.23f)
                cylinder {
                    radius = 2.85f
                    height = 0.42f
                    steps = 12
                    origin.y = -0.29f
                }
                color = Color(0.37f, 0.55f, 0.30f)
                cylinder {
                    radius = 2.9f
                    height = 0.18f
                    steps = 12
                    origin.y = -0.09f
                }
            }
            shader = KslBlinnPhongShader {
                color { vertexColor() }
                specularStrength(0.05f)
            }
        }
        onRelease { println("BranchWorldRenderer sceneReleased") }
    }

    private suspend fun loadModels(target: Scene) {
        try {
            target.place(DioramaAsset.Pine, -1.25f, -0.92f, 1.15f, 0.05f)
            target.place(DioramaAsset.Pine, 0.22f, -1.64f, 0.91f, 0.05f)
            target.place(DioramaAsset.Pine, -1.85f, 0.2f, 0.86f, 0.05f)
            target.place(DioramaAsset.Rock, 1.45f, -0.44f, 1.45f, 0.05f)
            target.place(DioramaAsset.Rock, 1.74f, 0.24f, 0.87f, 0.05f)
            bunny = target.place(DioramaAsset.Bunny, 0.35f, 0.65f, 0.76f, 0f).apply {
                val walk = animations.indexOfFirst { it.name == BunnyAnimation }
                check(walk >= 0) { "The bunny is missing its walk clip" }
                enableAnimation(walk)
                applyAnimation(0f)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            controller.status.value = "Could not load the island assets"
            println("BranchWorldRenderer assetsFailed: ${error.stackTraceToString()}")
        }
    }

    private suspend fun Scene.place(asset: DioramaAsset, x: Float, z: Float, scale: Float, groundOffset: Float): Model {
        val imported = Assets.loadGltfFile(asset.path).getOrThrow()
        return imported.makeModel(GltfLoadConfig(assetLoader = Assets.defaultLoader)).also { model ->
            model.transform.translate(x, groundOffset * scale, z).scale(scale)
            addNode(model)
        }
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

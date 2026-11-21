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
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.PerspectiveCamera
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.scene.InstanceLayouts
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.releaseDelayed
import id.branchworld.game.ecology.Weather
import id.branchworld.game.simulation.ClimatePose
import id.branchworld.game.simulation.WorldCamera
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
    private var previousRegion: Int? = null
    private var highlight: Node? = null
    private var waves: Node? = null
    private var rain: Node? = null
    private var previousClimate: ClimatePose? = null
    private var previousOpening = false
    private var propModels = emptyList<Model>()
    private var settlements: SettlementModels? = null
    private var assetFrame = -1
    private var idleAfterFrame: Int? = null
    private val timing = FrameTiming("renderer", measureDraws = true)

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
        val request = controller.presentation.value
        val snapshot = world.poses.value
        val focus = request.focus
        val region = (focus as? WorldCamera.Focus.Region)?.index?.takeIf { it in 0..15 }
        if (region != previousRegion) {
            highlight?.let {
                current.removeNode(it)
                it.releaseDelayed(2)
            }
            highlight = region?.let { current.addRegionHighlight(world.terrain, it) }
            previousRegion = region
        }
        if (controller.tracking) {
            val previous = controller.camera.value
            val narrow = controller.aspect.value < 0.8f
            val target = when (focus) {
                is WorldCamera.Focus.Creature -> snapshot.creatures.firstOrNull { it.id == focus.id }?.ground?.let {
                    val center = cellPosition(it.x, it.z, it.elevationTwice)
                    previous.copy(targetX = center.x, targetY = center.y + if (narrow) -0.65f else 0.19f,
                        targetZ = center.z, distance = if (narrow) 4.4f else 3.4f)
                }
                is WorldCamera.Focus.Region -> region?.let {
                    val x = TerrainMap.MinCell + it % 4 * 16 + 8
                    val z = TerrainMap.MinCell + it / 4 * 16 + 8
                    val center = cellPosition(x, z, world.terrain.cellAt(x, z)?.elevationTwice ?: 0)
                    previous.copy(targetX = center.x, targetY = center.y.coerceAtLeast(0f) + if (narrow) -0.78f else 0.25f,
                        targetZ = center.z, distance = if (narrow) 6.1f else 5.5f)
                }
                null -> null
            }
            if (target != null) {
                controller.camera.compareAndSet(previous, if (request.reducedMotion) target
                    else previous.toward(target, (Time.deltaT * 5.5f).coerceIn(0f, 1f)))
            }
        }
        val pose = controller.camera.value
        val horizontal = pose.distance * cos(pose.pitch)
        current.camera.lookAt.set(pose.targetX, pose.targetY, pose.targetZ)
        current.camera.position.set(
            pose.targetX + horizontal * sin(pose.yaw),
            pose.targetY + pose.distance * sin(pose.pitch),
            pose.targetZ + horizontal * cos(pose.yaw),
        )
        val wildlife = creatures
        val time = if (request.reducedMotion) 0f else Time.gameTime.toFloat()
        waves?.transform?.setIdentity()?.translate(0.042f * sin(time * 1.5f), 0f, 0.024f * cos(time * 1.1f))
        rain?.apply {
            isVisible = snapshot.climate?.weather == Weather.Rain && !request.reducedMotion
            if (isVisible) transform.setIdentity().translate(0f, -(time * 2.1f) % 1.7f, 0f)
        }
        if (snapshot.climate != previousClimate || request.opening != previousOpening) {
            snapshot.climate?.let { current.showClimate(it, request.opening) }
            previousClimate = snapshot.climate
            previousOpening = request.opening
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
        previousRegion = null
        highlight = null
        waves = null
        rain = null
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
        mainRenderPass.clearColor = ClearColorFill(Color(0.18f, 0.41f, 0.47f))
        camera.setClipRange(0.1f, 90f)
        (camera as? PerspectiveCamera)?.fovY = 47f.deg
        lighting.singleDirectionalLight {
            setup(Vec3f(-0.55f, -1.4f, -0.9f)).setColor(Color(1f, 0.91f, 0.73f), 1.25f)
        }
        lighting.addDirectionalLight {
            setup(Vec3f(0.6f, -0.72f, 1f)).setColor(Color(0.72f, 0.88f, 1f), 0.56f)
        }
        addIsland(terrain)
        addLandscapeDetails(terrain)
        waves = addWavelets(terrain)
        rain = addRain()
        onRelease { println("BranchWorldRenderer sceneReleased") }
    }

    private fun Scene.showClimate(climate: ClimatePose, opening: Boolean) {
        val rain = climate.weather == Weather.Rain
        val daylight = if (opening && climate.isDaylight && !rain) maxOf(0.85f, climate.daylight) else climate.daylight
        val key = if (rain) Color(0.75f, 0.86f, 1f) else Color(1f, 0.94f, 0.77f)
        val ambient = Color(0.68f, 0.82f, 1f)
        lighting.lights[0].setColor(key, 0.19f + daylight * if (rain) 0.74f else 1.02f)
        lighting.lights[1].setColor(ambient, 0.46f + daylight * 0.08f)
    }

    private fun Scene.addRain() = addColorMesh("Light rain") {
        isVisible = false
        generate {
            color = Color(0.73f, 0.86f, 1f, 0.37f)
            repeat(220) { index ->
                // Repeat the same field vertically so moving it by one layer loops cleanly.
                val x = ((index * 83) % 521) / 43f - 6f
                val z = ((index * 197) % 491) / 41f - 6f
                val height = ((index * 13) % 37) / 37f * 1.7f
                for (layer in 0..3) {
                    val y = height + layer * 1.7f
                    val a = vertex(Vec3f(x, y, z), Vec3f.Z_AXIS)
                    val b = vertex(Vec3f(x + 0.011f, y, z), Vec3f.Z_AXIS)
                    val c = vertex(Vec3f(x + 0.053f, y + 0.18f, z + 0.018f), Vec3f.Z_AXIS)
                    addTriIndices(a, b, c)
                }
            }
        }
        shader = translucentSurface()
    }

    private suspend fun loadModels(target: Scene, world: WorldRenderSource) {
        try {
            propModels = listOfNotNull(
                target.placeInstances(world.terrain, PropKind.Pine, DioramaAsset.Pine, 0.66f),
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
            if (scene === target) controller.status.value = "Island assets could not load. Go home and reopen the island to retry."
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
                val variation = 0.82f + ((prop.x * 17 + prop.z * 41) and 15) / 38f
                val scale = assetScale * variation * (prop.scalePercent / 100f)
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

internal class FrameTiming(private val kind: String, private val measureDraws: Boolean = false) {
    private var start: TimeMark? = null
    private var previous: TimeMark? = null
    private val samples = ArrayList<Double>()
    private var reported = false
    private var minimumDraws = Int.MAX_VALUE
    private var maximumDraws = 0

    fun reset() {
        start = null
        previous = null
        samples.clear()
        reported = false
        minimumDraws = Int.MAX_VALUE
        maximumDraws = 0
    }

    fun frame() {
        if (reported) return
        val first = start ?: TimeSource.Monotonic.markNow().also { start = it }
        val prior = previous
        previous = TimeSource.Monotonic.markNow()
        val elapsed = first.elapsedNow().inWholeMilliseconds
        if (prior == null || elapsed < 2_000) return
        samples += prior.elapsedNow().inWholeMicroseconds / 1000.0
        if (measureDraws) {
            minimumDraws = minOf(minimumDraws, BackendStats.numDrawCommands)
            maximumDraws = maxOf(maximumDraws, BackendStats.numDrawCommands)
        }
        if (elapsed < 12_000) return
        samples.sort()
        println("BranchWorldRenderer $kind frames=${samples.size} p50Ms=${samples[samples.lastIndex / 2]} p95Ms=${samples[(samples.lastIndex * 95) / 100]}")
        if (measureDraws) {
            println("BranchWorldRenderer drawCommands=$minimumDraws..$maximumDraws buffers=${BackendStats.totalBufferSize} textures=${BackendStats.totalTextureSize}")
        }
        reported = true
    }
}

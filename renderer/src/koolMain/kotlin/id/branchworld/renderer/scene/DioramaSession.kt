package id.branchworld.renderer.scene

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.modules.ksl.KslPbrShader
import de.fabmax.kool.pipeline.backend.stats.BackendStats
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.releaseDelayed
import id.branchworld.renderer.camera.FocusFraming
import id.branchworld.renderer.camera.PreviewController
import id.branchworld.renderer.camera.ViewportStatus
import id.branchworld.renderer.camera.eye
import id.branchworld.renderer.diagnostics.DioramaOutput
import id.branchworld.renderer.diagnostics.FrameTiming
import id.branchworld.renderer.lighting.coastalLight
import id.branchworld.renderer.scene.effects.FireDetails
import id.branchworld.renderer.scene.effects.ShapingDetails
import id.branchworld.renderer.scene.life.CreatureModels
import id.branchworld.renderer.scene.life.ForageMeshes
import id.branchworld.renderer.scene.life.HerdTrail
import id.branchworld.renderer.scene.life.LifeDetails
import id.branchworld.renderer.scene.life.LivingEcho
import id.branchworld.renderer.scene.life.LocalDetails
import id.branchworld.renderer.scene.life.VillageModels
import id.branchworld.renderer.scene.life.loadCreatureAssets
import id.branchworld.simulation.ecology.environment.Weather
import id.branchworld.simulation.projection.ClimatePose
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldRenderSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Draws the world the [controller] shows, every frame on Kool's render thread: builds the island scene when a world
 * opens, moves the camera toward its focus, lights it for the weather, and hands each frame's poses to the scene's
 * details. Asset loading runs in the scene's own coroutine scope and is dropped if the world changes meanwhile.
 */
internal class DioramaSession(
    private val context: KoolContext,
    private val controller: PreviewController,
    private val output: DioramaOutput? = null,
    private val onIdle: (() -> Unit)? = null,
) {
    private var island: IslandScene? = null
    private var renderedWorld: WorldRenderSource? = null
    private var load: Job? = null
    private var creatures: CreatureModels? = null
    private var forage: ForageMeshes? = null
    private var previousRegion: WorldCamera.Focus.Region? = null
    private var highlight: Node? = null
    private var shapingDetails: ShapingDetails? = null
    private var fireDetails: FireDetails? = null
    private var livingEcho: LivingEcho? = null
    private var herdTrail: HerdTrail? = null
    private var localDetails: LocalDetails? = null
    private var lifeDetails: LifeDetails? = null
    private var previousClimate: ClimatePose? = null
    private var previousOpening = false
    private val framing = FocusFraming()
    private var pines: PineForest? = null
    private var propModels = emptyList<Model>()
    private var settlements: VillageModels? = null
    private var assetFrame = -1
    private var idleAfterFrame: Int? = null
    private val timing = FrameTiming("renderer", measureDraws = true)
    private var timingMode: String? = null

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
        val parts = island ?: open(world)
        val current = parts.scene
        current.isVisible = controller.foreground.value
        if (!current.isVisible) return
        val request = controller.presentation.value
        val snapshot = world.poses.value
        if (request.focus !is WorldCamera.Focus.Creature) framing.release()
        showRegion(current, world, request, snapshot.shaping != null)
        if (controller.tracking) {
            val previous = controller.camera.value
            val target = framing.target(request, snapshot, world.terrain, previous, narrow = controller.aspect.value < 0.8f,
                manualOrbit = controller.manualOrbit.value)
            if (target != null) {
                controller.camera.compareAndSet(previous, if (request.reducedMotion) target
                    else previous.toward(target, (Time.deltaT * 5.5f).coerceIn(0f, 1f)))
            }
        }
        val pose = controller.camera.value
        val eye = pose.eye()
        current.camera.lookAt.set(pose.targetX, pose.targetY, pose.targetZ)
        current.camera.position.set(eye.x, eye.y, eye.z)

        val wildlife = creatures
        val time = if (request.reducedMotion) 0f else Time.gameTime.toFloat()
        val fire = snapshot.fire
        val dryStorm = !fire?.storm.isNullOrEmpty() && snapshot.climate?.weather == Weather.Dry
        val strikeFlash = if (!request.reducedMotion && fire?.lightning != null) {
            0.22f * (1f - fire.lightningAge / 5f).coerceIn(0f, 1f)
        } else 0f
        if (snapshot.climate != previousClimate || request.opening != previousOpening || assetFrame < 0 ||
            dryStorm != parts.light.dryStorm || strikeFlash != parts.light.flash) {
            val nextLight = coastalLight(snapshot.climate, request.opening, dryStorm, strikeFlash)
            if (parts.light != nextLight) parts.show(nextLight)
            previousClimate = snapshot.climate
            previousOpening = request.opening
        }
        val light = parts.light
        livingEcho?.show(snapshot.livingEcho)
        herdTrail?.show(snapshot.ranges, snapshot.settlements.orEmpty())
        val mode = when {
            snapshot.livingEcho?.sites?.isNotEmpty() == true -> "echo-on"
            !fire?.burning.isNullOrEmpty() -> "fire-active"
            dryStorm -> "dry-storm"
            fire != null -> "fire-quiet"
            else -> "echo-off"
        }
        if (mode != timingMode) {
            timingMode = mode
            timing.reset()
        }
        val shower = shapingDetails?.show(snapshot.shaping, request.targetSites, time, request.reducedMotion)
        fireDetails?.show(fire, time, request.reducedMotion, light.sun, targeting = request.targetSites.isNotEmpty())
        parts.water.time = time
        parts.inland?.apply {
            this.time = time
            recharge = (snapshot.climate?.watershed?.recharge ?: 64) / 255f
            localShower = shower ?: Vec4f.ZERO
        }
        parts.wind.apply {
            this.time = time
            amplitude = if (request.reducedMotion) 0f else if (light.storm || light.dryStorm) 0.077f else if (light.rain) 0.057f else 0.026f
        }
        val canopyWind = Vec2f(time, if (request.reducedMotion) 0f else if (light.rain || light.dryStorm) 0.045f else 0.019f)
        for (shader in parts.canopy) shader.uniform2f("uPineWind").set(canopyWind)
        // The fine blades are subpixel at survey height on a smaller render budget.
        parts.fineGrass.isVisible = output != null || pose.distance < 12.4f
        parts.rain.apply {
            isVisible = light.rain && !request.reducedMotion
            if (isVisible) transform.setIdentity().translate(0f, -(time * if (light.storm) 5.6f else 3.6f) % 1.7f, 0f)
        }
        wildlife?.show(snapshot, request.reducedMotion)
        lifeDetails?.show(request.placement, request.selected?.let { wildlife?.shownAt(it) }, snapshot.life, time, request.reducedMotion)
        pines?.show(snapshot.settlements.orEmpty(), snapshot.fire?.stands.orEmpty())
        settlements?.show(snapshot.settlements.orEmpty(), snapshot.tick, snapshot.climate?.dayPhase ?: 0.2f, world.terrain,
            request.reducedMotion, time)
        forage?.show(snapshot, time, request.reducedMotion)
        localDetails?.show(snapshot, light, time, request.reducedMotion)
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
            if (Time.frameCount > assetFrame) controller.status.value = ViewportStatus.Ready
            timing.frame(mode)
        }
    }

    /** Builds the scene for a newly shown world and starts loading its models. */
    private fun open(world: WorldRenderSource): IslandScene {
        val parts = IslandScene(world.terrain, compact = output == null)
        val scene = parts.scene
        island = parts
        renderedWorld = world
        val opening = world.poses.value
        forage = ForageMeshes(scene, world.terrain, opening.springs, opening.wildlife != null, opening.shaping != null)
        localDetails = LocalDetails(scene, world.terrain)
        livingEcho = LivingEcho(scene, world.terrain)
        herdTrail = HerdTrail(scene, world.terrain, world.herdRoute)
        if (opening.shaping != null) shapingDetails = ShapingDetails(scene, world.terrain, compact = output == null)
        if (opening.fire != null) fireDetails = FireDetails(scene, world.terrain, compact = output == null)
        if (opening.life != null) lifeDetails = LifeDetails(scene, world.terrain)
        controller.status.value = ViewportStatus.Loading
        output?.attach(scene)
        context.addScene(scene)
        load = scene.coroutineScope.launch { loadModels(parts, world) }
        return parts
    }

    /** Outlines the region the interface is shaping (earlier chapters), replacing the previous outline. */
    private fun showRegion(scene: Scene, world: WorldRenderSource, request: WorldCamera, subtle: Boolean) {
        val region = (request.focus as? WorldCamera.Focus.Region)?.takeIf { it.index in 0..15 }
        if (region == previousRegion) return
        highlight?.let {
            scene.removeNode(it)
            it.releaseDelayed(2)
        }
        highlight = region?.let { scene.addRegionHighlight(world.terrain, it.index, rain = it.rain, subtle = subtle) }
        previousRegion = region
    }

    private fun dispose(delay: Boolean) {
        val previous = island?.scene ?: return
        island = null
        renderedWorld = null
        load?.cancel()
        load = null
        creatures = null
        forage = null
        previousClimate = null
        framing.release()
        previousRegion = null
        highlight = null
        shapingDetails = null
        fireDetails = null
        livingEcho = null
        herdTrail = null
        timingMode = null
        localDetails = null
        lifeDetails = null
        propModels = emptyList()
        pines = null
        settlements = null
        assetFrame = -1
        timing.reset()
        output?.detach()
        context.removeScene(previous)
        if (onIdle != null) previous.onRelease {
            // Scene release schedules more GPU work, including texture disposal.
            idleAfterFrame = Time.frameCount + 3
        }
        // Releasing the scene releases every node the details added to it.
        if (delay) previous.releaseDelayed(2) else previous.release()
    }

    private suspend fun loadModels(target: IslandScene, world: WorldRenderSource) {
        val isCurrent = { island === target }
        try {
            val forest = PineForest(target.scene, world.terrain, isCurrent).also { pines = it }
            propModels = forest.plant()
            target.useCanopy(propModels.flatMap { model -> model.meshes.values.mapNotNull { it.shader as? KslPbrShader } })
            val assets = loadCreatureAssets(world.poses.value.wildlife != null, world.poses.value.life != null)
            val towns = if (world.poses.value.settlements != null) VillageModels.load(target.scene) else null
            if (!isCurrent() || !currentCoroutineContext().isActive) {
                throw CancellationException("The island scene was replaced while loading wildlife")
            }
            settlements = towns
            creatures = CreatureModels(target.scene, assets, world.terrain).also {
                it.show(world.poses.value, controller.presentation.value.reducedMotion)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent()) controller.status.value = ViewportStatus.Failed("Island assets could not load. Go home and reopen the island to retry.")
            println("BranchWorldRenderer assetsFailed: ${error.stackTraceToString()}")
        }
    }
}

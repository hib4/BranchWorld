package id.branchworld.renderer

import de.fabmax.kool.Assets
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.loadGltfFile
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.modules.ksl.KslPbrShader
import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.pipeline.backend.stats.BackendStats
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.PerspectiveCamera
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.scene.InstanceLayouts
import de.fabmax.kool.scene.Light
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.releaseDelayed
import id.branchworld.game.ecology.Succession
import id.branchworld.game.ecology.Weather
import id.branchworld.game.simulation.ClimatePose
import id.branchworld.game.simulation.WorldCamera
import id.branchworld.game.simulation.SettlementPose
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
    private var previousRegion: WorldCamera.Focus.Region? = null
    private var highlight: Node? = null
    private var water: CoastalWater? = null
    private var inland: InlandWater? = null
    private var wind: MeadowWind? = null
    private var ground: KslBlinnPhongShader? = null
    private var canopy = emptyList<KslPbrShader>()
    private var fineGrass: Node? = null
    private var rain: Node? = null
    private var shapingDetails: ShapingDetails? = null
    private var fireDetails: FireDetails? = null
    private var livingEcho: LivingEcho? = null
    private var herdTrail: HerdTrail? = null
    private var localDetails: LocalDetails? = null
    private var light = coastalLight(null, false)
    private var previousClimate: ClimatePose? = null
    private var previousOpening = false
    private var creatureFocusRevision: Int? = null
    private var creatureFocusOrbit = 0L
    private var creatureFocusYaw: Float? = null
    private var propModels = emptyList<Model>()
    /** Each pine model's instances with their cells, so settlements can clear the trees on their plots. */
    private var pineStands = emptyList<Pair<Model, List<PineInstance>>>()
    private var shownStands: Map<Int, Succession>? = null
    private var clearedCells: Set<Long>? = null
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
        val current = scene ?: buildScene(world.terrain).also {
            scene = it
            renderedWorld = world
            val opening = world.poses.value
            forage = ForageMeshes(it, world.terrain, opening.springs, opening.wildlife != null, opening.shaping != null)
            localDetails = LocalDetails(it, world.terrain)
            livingEcho = LivingEcho(it, world.terrain)
            herdTrail = HerdTrail(it, world.terrain, world.herdRoute)
            if (opening.shaping != null) shapingDetails = ShapingDetails(it, world.terrain, compact = output == null)
            if (opening.fire != null) fireDetails = FireDetails(it, world.terrain, compact = output == null)
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
        if (focus !is WorldCamera.Focus.Creature) {
            creatureFocusRevision = null
            creatureFocusYaw = null
        }
        val region = (focus as? WorldCamera.Focus.Region)?.takeIf { it.index in 0..15 }
        if (region != previousRegion) {
            highlight?.let {
                current.removeNode(it)
                it.releaseDelayed(2)
            }
            highlight = region?.let { current.addRegionHighlight(world.terrain, it.index, rain = it.rain, subtle = snapshot.shaping != null) }
            previousRegion = region
        }
        if (controller.tracking) {
            val previous = controller.camera.value
            val narrow = controller.aspect.value < 0.8f
            val target = when (focus) {
                is WorldCamera.Focus.Creature -> snapshot.creatures.firstOrNull { it.id == focus.id }?.let { animal ->
                    val orbit = controller.manualOrbit.value
                    if (creatureFocusRevision != request.focusRequest) {
                        creatureFocusRevision = request.focusRequest
                        creatureFocusOrbit = orbit
                        creatureFocusYaw = creatureViewYaw(world.terrain, animal, previous)
                    } else if (creatureFocusOrbit != orbit) creatureFocusYaw = null
                    val ground = animal.ground
                    val center = cellPosition(ground.x, ground.z, ground.elevationTwice)
                    val distance = if (animal.intent != null) {
                        if (narrow) 4.1f else 3.15f
                    } else if (narrow) 4.4f else 3.4f
                    previous.copy(yaw = creatureFocusYaw ?: previous.yaw,
                        targetX = center.x, targetY = center.y + if (narrow) -0.65f else 0.19f,
                        targetZ = center.z, distance = distance)
                }
                is WorldCamera.Focus.Region -> region?.let {
                    val x = TerrainMap.MinCell + it.index % 4 * 16 + 8
                    val z = TerrainMap.MinCell + it.index / 4 * 16 + 8
                    val center = cellPosition(x, z, world.terrain.cellAt(x, z)?.elevationTwice ?: 0)
                    val sites = request.targetSites
                    if (snapshot.shaping == null) {
                        previous.copy(targetX = center.x, targetY = center.y.coerceAtLeast(0f) + if (narrow) -0.78f else 0.25f,
                            targetZ = center.z, distance = if (narrow) 6.1f else 5.5f)
                    } else {
                        val centerX = if (sites.isEmpty()) center.x else (sites.map { point -> point.x }.average().toFloat() + 0.5f) * CellSize
                        val centerZ = if (sites.isEmpty()) center.z else (sites.map { point -> point.z }.average().toFloat() + 0.5f) * CellSize
                        val elevation = world.terrain.heightAt(centerX, centerZ).coerceAtLeast(0f)
                        val offsetX = if (narrow) sin(previous.yaw) * 0.72f else cos(previous.yaw) * 0.62f
                        val offsetZ = if (narrow) cos(previous.yaw) * 0.72f else -sin(previous.yaw) * 0.62f
                        previous.copy(targetX = centerX + offsetX, targetZ = centerZ + offsetZ,
                            targetY = elevation + if (narrow) -0.62f else 0.27f, distance = if (narrow) 5.9f else 5.1f)
                    }
                }
                is WorldCamera.Focus.Landmark -> world.terrain.watershed?.let { watershed ->
                    watershed.landmarks.firstOrNull { it.kind == focus.kind }?.let { landmark ->
                        val height = watershed.waterAt(landmark.x, landmark.z)?.surfaceTwice ?: 0
                        val center = cellPosition(landmark.x, landmark.z, height)
                        previous.copy(targetX = center.x, targetZ = center.z,
                            targetY = center.y + if (narrow) -0.65f else 0.12f,
                            distance = if (narrow) 5.3f else 4.6f)
                    }
                }
                is WorldCamera.Focus.WildlifeLocation -> {
                    val ground = focus.ground
                    val center = cellPosition(ground.x, ground.z, ground.elevationTwice)
                    previous.copy(targetX = center.x, targetZ = center.z,
                        targetY = center.y + if (narrow) -0.65f else 0.15f, distance = if (narrow) 4.7f else 3.9f)
                }
                is WorldCamera.Focus.EchoSite -> focus.grounds.takeIf { it.isNotEmpty() }?.let { grounds ->
                    val minX = grounds.minOf { it.x }
                    val maxX = grounds.maxOf { it.x }
                    val minZ = grounds.minOf { it.z }
                    val maxZ = grounds.maxOf { it.z }
                    val x = (minX + maxX + 1) * CellSize / 2f
                    val z = (minZ + maxZ + 1) * CellSize / 2f
                    val span = maxOf(maxX - minX, maxZ - minZ) * CellSize
                    val offset = if (focus.sideDock) 0.88f else 0f
                    previous.copy(targetX = x + cos(previous.yaw) * offset,
                        targetZ = z - sin(previous.yaw) * offset,
                        targetY = world.terrain.heightAt(x, z).coerceAtLeast(0f) + if (narrow) -0.72f else 0.13f,
                        distance = (if (narrow) 4.9f else 4.4f) + span * 1.1f)
                }
                is WorldCamera.Focus.Village -> focus.grounds.takeIf { it.isNotEmpty() }?.let { grounds ->
                    val x = (grounds.minOf { it.x } + grounds.maxOf { it.x } + 1) * CellSize / 2f
                    val z = (grounds.minOf { it.z } + grounds.maxOf { it.z } + 1) * CellSize / 2f
                    val span = maxOf(grounds.maxOf { it.x } - grounds.minOf { it.x }, grounds.maxOf { it.z } - grounds.minOf { it.z }) * CellSize
                    previous.copy(targetX = x, targetZ = z,
                        targetY = world.terrain.heightAt(x, z).coerceAtLeast(0f) + if (narrow) -0.55f else 0.08f,
                        distance = ((if (narrow) 3.4f else 2.7f) + span * 0.7f).coerceAtMost(6f))
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
        val fire = snapshot.fire
        val dryStorm = !fire?.storm.isNullOrEmpty() && snapshot.climate?.weather == Weather.Dry
        val strikeFlash = if (!request.reducedMotion && fire?.lightning != null) {
            0.22f * (1f - fire.lightningAge / 5f).coerceIn(0f, 1f)
        } else 0f
        if (snapshot.climate != previousClimate || request.opening != previousOpening || assetFrame < 0 ||
            dryStorm != light.dryStorm || strikeFlash != light.flash) {
            val nextLight = coastalLight(snapshot.climate, request.opening, dryStorm, strikeFlash)
            if (light != nextLight) {
                light = nextLight
                current.showLight(nextLight)
            }
            previousClimate = snapshot.climate
            previousOpening = request.opening
        }
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
        water?.time = time
        inland?.apply {
            this.time = time
            recharge = (snapshot.climate?.watershed?.recharge ?: 64) / 255f
            localShower = shower ?: Vec4f.ZERO
        }
        wind?.apply {
            this.time = time
            amplitude = if (request.reducedMotion) 0f else if (light.storm || light.dryStorm) 0.077f else if (light.rain) 0.057f else 0.026f
        }
        val canopyWind = Vec2f(time, if (request.reducedMotion) 0f else if (light.rain || light.dryStorm) 0.045f else 0.019f)
        for (shader in canopy) shader.uniform2f("uPineWind").set(canopyWind)
        // The fine blades are subpixel at survey height on a smaller render budget.
        fineGrass?.isVisible = output != null || pose.distance < 12.4f
        rain?.apply {
            isVisible = light.rain && !request.reducedMotion
            if (isVisible) transform.setIdentity().translate(0f, -(time * if (light.storm) 5.6f else 3.6f) % 1.7f, 0f)
        }
        wildlife?.show(snapshot, request.reducedMotion)
        if (pineStands.isNotEmpty()) clearPines(snapshot.settlements.orEmpty(), snapshot.fire?.stands.orEmpty())
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
            if (Time.frameCount > assetFrame) controller.status.value = "Island ready"
            timing.frame(mode)
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
        creatureFocusRevision = null
        creatureFocusYaw = null
        previousRegion = null
        highlight = null
        water = null
        inland = null
        wind = null
        ground = null
        canopy = emptyList()
        fineGrass = null
        rain = null
        shapingDetails = null
        fireDetails = null
        livingEcho = null
        herdTrail = null
        timingMode = null
        localDetails = null
        propModels = emptyList()
        pineStands = emptyList()
        clearedCells = null
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
        lighting.singleDirectionalLight { setup(Vec3f(-0.55f, -1.4f, -0.9f)) }
        lighting.addDirectionalLight { setup(Vec3f(0.6f, -0.72f, 1f)) }
        val waterMaterial = CoastalWater().also { water = it }
        val inlandMaterial = terrain.watershed?.let { InlandWater().also { shader -> inland = shader } }
        val meadowMaterial = MeadowWind().also { wind = it }
        val groundMaterial = KslBlinnPhongShader {
            color { vertexColor() }
            specularStrength(0.035f)
            modelCustomizer = { coastalHaze() }
        }.also { ground = it }
        light = coastalLight(null, false)
        showLight(light)
        addIsland(terrain, waterMaterial, groundMaterial)
        if (inlandMaterial != null) {
            addInlandWater(terrain, inlandMaterial)
            addReedBanks(terrain, meadowMaterial, compact = output == null)
        }
        fineGrass = addLandscapeDetails(terrain, meadowMaterial)
        rain = addRain()
        onRelease { println("BranchWorldRenderer sceneReleased") }
    }

    private fun Scene.showLight(light: CoastalLight) {
        (lighting.lights[0] as Light.Directional).setup(light.direction).setColor(light.lightColor, light.strength)
        lighting.lights[1].setColor(light.fillColor, light.fill)
        water?.apply {
            haze = light.haze
            atmosphere = Vec4f(light.sun, light.twilight, if (light.rain) 1f else 0f, 0f)
        }
        inland?.apply {
            haze = light.haze
            atmosphere = Vec4f(light.sun, light.twilight, if (light.rain) 1f else 0f, 0f)
        }
        wind?.tint = Color(0.56f, 0.63f, 0.70f).mix(Color(1.08f, 1.02f, 0.85f), light.sun)
        updateLandHaze(light)
    }

    private fun updateLandHaze(light: CoastalLight) {
        val haze = light.haze
        val mist = Vec4f(haze.r * haze.r, haze.g * haze.g, haze.b * haze.b, if (light.rain) 0.49f else 0.31f)
        ground?.uniform4f("uLandHaze")?.set(mist)
        for (shader in canopy) shader.uniform4f("uLandHaze").set(mist)
    }

    private fun Scene.addRain() = addColorMesh("Light rain") {
        isVisible = false
        generate {
            color = Color(0.71f, 0.83f, 0.95f, 0.22f)
            repeat(if (output == null) 136 else 196) { index ->
                // Repeat the same field vertically so moving it by one layer loops cleanly.
                val x = ((index * 83) % 521) / 43f - 6f
                val z = ((index * 197) % 491) / 41f - 6f
                val height = ((index * 13) % 37) / 37f * 1.7f
                for (layer in 0..3) {
                    val y = height + layer * 1.7f
                    val a = vertex(Vec3f(x, y, z), Vec3f.Z_AXIS)
                    val b = vertex(Vec3f(x + 0.014f, y, z), Vec3f.Z_AXIS)
                    val c = vertex(Vec3f(x + 0.047f, y + 0.19f, z + 0.018f), Vec3f.Z_AXIS)
                    addTriIndices(a, b, c)
                }
            }
        }
        shader = translucentSurface()
    }

    private suspend fun loadModels(target: Scene, world: WorldRenderSource) {
        try {
            propModels = PineVariants.mapNotNull { variant -> target.placePines(world.terrain, variant) }
            canopy = propModels.flatMap { model -> model.meshes.values.mapNotNull { it.shader as? KslPbrShader } }
            updateLandHaze(light)
            val assets = loadCreatureAssets(world.poses.value.wildlife != null)
            val towns = if (world.poses.value.settlements != null) VillageModels.load(target) else null
            if (scene !== target || !currentCoroutineContext().isActive) {
                throw CancellationException("The island scene was replaced while loading wildlife")
            }
            settlements = towns
            creatures = CreatureModels(target, assets, world.terrain).also {
                it.show(world.poses.value, controller.presentation.value.reducedMotion)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (scene === target) controller.status.value = "Island assets could not load. Go home and reopen the island to retry."
            println("BranchWorldRenderer assetsFailed: ${error.stackTraceToString()}")
        }
    }

    /**
     * Trees standing on settlement plots are hidden while those plots are in use (cosmetic). Pines the fire took or the
     * axe felled come down, and come back as young trees, from the authoritative stand record (rules v11).
     */
    private fun clearPines(sites: List<SettlementPose>, stands: Map<Int, Succession>) {
        val cells = buildSet {
            for (site in sites) {
                val village = site.village ?: continue
                for (ground in village.structures.map { it.ground } + village.center) {
                    for (dz in -1..1) for (dx in -1..1) add(cellKey(ground.x + dx, ground.z + dz))
                }
            }
        }
        if (cells == clearedCells && stands == shownStands) return
        clearedCells = cells
        shownStands = stands
        for ((model, stand) in pineStands) {
            val kept = stand.filter { it.cell !in cells && stands[it.prop].let { stage -> stage == null || stage == Succession.Scrub || stage == Succession.Grove } }
            model.meshes.values.forEach { mesh ->
                mesh.addInstances<InstanceLayouts.ModelMat>(kept.size, clear = true) { buffer ->
                    for (pine in kept) {
                        val young = stands[pine.prop] == Succession.Scrub
                        val matrix = if (young) MutableMat4f(pine.matrix).scale(YoungPine) else pine.matrix
                        buffer.put { set(it.modelMat, matrix) }
                    }
                }
            }
        }
    }

    private suspend fun Scene.placePines(terrain: TerrainMap, variant: PineVariant): Model? {
        val cells = mutableListOf<Long>()
        val props = mutableListOf<Int>()
        val matrices = buildList {
            for (index in 0 until terrain.propCount) {
                val prop = terrain.prop(index)
                if (prop.kind != PropKind.Pine) continue
                val pattern = (prop.x * 37 xor prop.z * 53) and 15
                val chosen = if (pattern < 7) PineVariants[0] else if (pattern < 12) PineVariants[1] else PineVariants[2]
                if (variant != chosen) continue
                val cell = checkNotNull(terrain.cellAt(prop.x, prop.z))
                val center = cellPosition(prop.x, prop.z, cell.elevationTwice)
                val variation = 0.82f + ((prop.x * 17 + prop.z * 41) and 15) / 38f
                val scale = variant.height / variant.sourceHeight * variation * (prop.scalePercent / 100f)
                // The Kenney scene nodes put the base 0.05 units below their file origin.
                cells.add(cellKey(prop.x, prop.z))
                props.add(index)
                add(MutableMat4f()
                    .translate(center.x, terrain.heightAt(center.x, center.z) + 0.05f * scale, center.z)
                    .rotate(prop.yawDegrees.toFloat().deg, Vec3f.Y_AXIS)
                    .scale(scale))
            }
        }
        if (matrices.isEmpty()) return null
        val imported = Assets.loadGltfFile(variant.asset.path).getOrThrow()
        return imported.makeModel(GltfLoadConfig(
            assetLoader = Assets.defaultLoader,
            applyTransforms = true,
            loadAnimations = false,
            instanceLayout = InstanceLayouts.ModelMat,
            pbrBlock = { primitive ->
                val tint = when (primitive.material) {
                    0 -> Color(0.28f, 0.161f, 0.084f)
                    1 -> variant.foliage
                    else -> variant.foliage.mix(Color(0.22f, 0.31f, 0.11f), 0.35f)
                }
                color { constColor(tint) }
                metallic(0f)
                roughness(0.94f)
                modelCustomizer = {
                    pineSway()
                    coastalHaze()
                }
            },
        )).also { model ->
            attachWhileCurrent(model)
            pineStands = pineStands + (model to cells.indices.map { PineInstance(props[it], cells[it], matrices[it]) })
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

private class PineInstance(val prop: Int, val cell: Long, val matrix: MutableMat4f)

/** Young pines regrowing on burned or felled ground stand at this fraction of their grown height. */
private const val YoungPine = 0.42f

private fun cellKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)

private data class PineVariant(val asset: DioramaAsset, val height: Float, val sourceHeight: Float, val foliage: Color)

private val PineVariants = listOf(
    PineVariant(DioramaAsset.Pine, 0.89f, 1.3664f, Color(0.070f, 0.206f, 0.114f)),
    PineVariant(DioramaAsset.RoundedPine, 0.72f, 1.1959f, Color(0.130f, 0.244f, 0.103f)),
    PineVariant(DioramaAsset.TallPine, 1.04f, 1.5296f, Color(0.058f, 0.158f, 0.108f)),
)

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

    fun frame(mode: String? = null) {
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
        val label = if (mode == null) kind else "$kind $mode"
        println("BranchWorldRenderer $label frames=${samples.size} p50Ms=${samples[samples.lastIndex / 2]} p95Ms=${samples[(samples.lastIndex * 95) / 100]}")
        if (measureDraws) {
            println("BranchWorldRenderer drawCommands=$minimumDraws..$maximumDraws buffers=${BackendStats.totalBufferSize} textures=${BackendStats.totalTextureSize}")
        }
        reported = true
    }
}

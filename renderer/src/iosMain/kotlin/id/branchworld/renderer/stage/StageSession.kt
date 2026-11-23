package id.branchworld.renderer.stage

import id.branchworld.renderer.WorldStage
import id.branchworld.renderer.assets.CreatureClips
import id.branchworld.renderer.assets.DioramaAsset
import id.branchworld.renderer.camera.FocusFraming
import id.branchworld.renderer.camera.PreviewController
import id.branchworld.renderer.camera.ViewFovYDegrees
import id.branchworld.renderer.camera.ViewportStatus
import id.branchworld.renderer.camera.eye
import id.branchworld.renderer.geometry.Mat4
import id.branchworld.renderer.geometry.PineBark
import id.branchworld.renderer.geometry.PineVariants
import id.branchworld.renderer.geometry.Rgba
import id.branchworld.renderer.geometry.SurfaceMesh
import id.branchworld.renderer.geometry.canopyShade
import id.branchworld.renderer.geometry.inlandWaterSurface
import id.branchworld.renderer.geometry.meadowBlades
import id.branchworld.renderer.geometry.meadowFlowers
import id.branchworld.renderer.geometry.reedBanks
import id.branchworld.renderer.geometry.weatheredStones
import id.branchworld.renderer.geometry.YoungPine
import id.branchworld.renderer.geometry.cellPosition
import id.branchworld.renderer.geometry.forageTuft
import id.branchworld.renderer.geometry.heightAt
import id.branchworld.renderer.geometry.instanced
import id.branchworld.renderer.geometry.pinePlacements
import id.branchworld.renderer.geometry.pitchQuaternion
import id.branchworld.renderer.geometry.placementPeg
import id.branchworld.renderer.geometry.quaternionProduct
import id.branchworld.renderer.geometry.sapling
import id.branchworld.renderer.geometry.seaSurface
import id.branchworld.renderer.geometry.selectionRing
import id.branchworld.renderer.geometry.terrainChunks
import id.branchworld.renderer.geometry.underFoliage
import id.branchworld.renderer.geometry.warrensAndDens
import id.branchworld.renderer.geometry.yawQuaternion
import id.branchworld.renderer.lighting.CoastalLight
import id.branchworld.renderer.lighting.coastalLight
import id.branchworld.renderer.motion.CreatureMotion
import id.branchworld.renderer.motion.CreaturePacing
import id.branchworld.renderer.motion.villageGround
import id.branchworld.renderer.motion.villageScale
import id.branchworld.simulation.ecology.ForageKind
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.environment.Weather
import id.branchworld.simulation.ecology.fire.Succession
import id.branchworld.simulation.projection.ForagePose
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.PlaceTool
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldRenderProjection
import id.branchworld.simulation.projection.WorldRenderSource
import id.branchworld.simulation.terrain.TerrainMap
import kotlin.math.sin

/**
 * Draws the world the [controller] shows on a RealityKit [stage], once per display frame on the main thread: builds the
 * island when a world opens, moves the camera toward its focus, lights it for the weather and poses the animals. The
 * same shared geometry, lighting and creature motion as the Kool renderer, so iOS shows the same island.
 */
internal class StageSession(private val stage: WorldStage, private val controller: PreviewController) {
    private val ids = StageIds()
    private val batch = TransformBatch()
    private val framing = FocusFraming()
    private var shown: WorldRenderSource? = null
    private var scene: IslandStage? = null
    private var lastNanos = 0L
    private var seconds = 0f
    private var timedFrames = 0
    private var timedSince = 0L

    fun frame(nanos: Long) {
        val dt = if (lastNanos == 0L) 0f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = nanos
        seconds += dt
        // A frame-rate line every ten seconds while a world is shown, like the Kool renderer's timing logs.
        if (scene != null) {
            if (timedSince == 0L) timedSince = nanos
            timedFrames++
            if (nanos - timedSince >= 10_000_000_000L) {
                println("BranchWorldRenderer ios fps=${(timedFrames * 1e9 / (nanos - timedSince) * 10).toInt() / 10.0} creatures=${controller.renderWorld?.poses?.value?.creatures?.size}")
                timedFrames = 0
                timedSince = nanos
            }
        }
        val world = controller.renderWorld
        if (world == null) {
            if (shown != null) clear()
            return
        }
        if (world !== shown) {
            clear()
            shown = world
            scene = try {
                IslandStage(stage, ids, world).also { controller.status.value = ViewportStatus.Loading }
            } catch (error: Throwable) {
                println("BranchWorldRenderer iosAssetsFailed: ${error.stackTraceToString()}")
                controller.status.value = ViewportStatus.Failed("Island assets could not load. Go home and reopen the island to retry.")
                null
            }
        }
        val island = scene ?: return
        if (!controller.foreground.value) return
        val request = controller.presentation.value
        val snapshot = world.poses.value
        if (request.focus !is WorldCamera.Focus.Creature) framing.release()
        if (controller.tracking) {
            val previous = controller.camera.value
            val target = framing.target(request, snapshot, world.terrain, previous, narrow = controller.aspect.value < 0.8f,
                manualOrbit = controller.manualOrbit.value)
            if (target != null) {
                controller.camera.compareAndSet(previous, if (request.reducedMotion) target
                    else previous.toward(target, (dt * 5.5f).coerceIn(0f, 1f)))
            }
        }
        val pose = controller.camera.value
        val eye = pose.eye()
        stage.setCamera(eye.x, eye.y, eye.z, pose.targetX, pose.targetY, pose.targetZ, ViewFovYDegrees)
        island.light(snapshot, request)
        island.show(snapshot, request, pose.distance, dt, seconds, nanos, batch)
        if (!batch.isEmpty) stage.setTransforms(batch.drain())
        if (controller.status.value == ViewportStatus.Loading) controller.status.value = ViewportStatus.Ready
    }

    private fun clear() {
        scene = null
        shown = null
        framing.release()
        stage.clearScene()
        ids.reset()
    }
}

/** One open island on the stage: its land, sea, pines, plants, markers and animals. */
private class IslandStage(private val stage: WorldStage, private val ids: StageIds, world: WorldRenderSource) {
    private val terrain: TerrainMap = world.terrain
    private val textures = mutableMapOf<String, Int>()
    private val creatures = StageCreatures(stage, ids, terrain, textures, world.poses.value.wildlife != null)
    private val pines = StagePines(stage, ids, terrain)
    private val forage = StageForage(stage, ids, terrain, world.poses.value.wildlife != null)
    private val markers = StageMarkers(stage, ids, terrain)
    private val atmosphere = StageAtmosphere(stage, ids, terrain)
    private val fire = StageFire(stage, ids, terrain)
    private var light: CoastalLight? = null
    private var lightMotion = false
    private var recharge = -1f
    private val fineGrass: Int

    init {
        val land = ids.take().also { stage.defineMaterial(it, StageShading.Land, -1, 1f, 1f, 1f, 1f, 0) }
        for (chunk in terrain.terrainChunks()) {
            val mesh = ids.take()
            stage.define(mesh, chunk.mesh)
            stage.addEntity(ids.take(), -1, mesh, land)
        }
        val sea = ids.take().also { stage.defineMaterial(it, StageShading.Sea, -1, 1f, 1f, 1f, 1f, 0) }
        val seaMesh = ids.take().also { stage.define(it, terrain.seaSurface()) }
        stage.addEntity(ids.take(), -1, seaMesh, sea)
        val plain = ids.take().also { stage.defineMaterial(it, StageShading.Plain, -1, 1f, 1f, 1f, 1f, 0) }
        val shade = ids.take().also { stage.defineMaterial(it, StageShading.Translucent, -1, 1f, 1f, 1f, 1f, 0) }
        val wind = ids.take().also { stage.defineMaterial(it, StageShading.Wind, -1, 1f, 1f, 1f, 1f, 0) }
        fun add(mesh: SurfaceMesh, material: Int): Int {
            val id = ids.take().also { stage.define(it, mesh) }
            return ids.take().also { stage.addEntity(it, -1, id, material) }
        }
        add(terrain.weatheredStones(), plain)
        add(terrain.meadowFlowers(), plain)
        add(terrain.canopyShade(), shade)
        fineGrass = add(terrain.meadowBlades(), wind)
        if (terrain.watershed != null) {
            add(terrain.inlandWaterSurface(), ids.take().also { stage.defineMaterial(it, StageShading.River, -1, 1f, 1f, 1f, 1f, 0) })
            // Phones draw the compact reed banks, as the Kool renderer does on phones and the web.
            add(terrain.reedBanks(compact = true), wind)
        }
    }

    fun light(snapshot: WorldRenderProjection, request: WorldCamera) {
        val fire = snapshot.fire
        val dryStorm = !fire?.storm.isNullOrEmpty() && snapshot.climate?.weather == Weather.Dry
        val flash = if (!request.reducedMotion && fire?.lightning != null) 0.22f * (1f - fire.lightningAge / 5f).coerceIn(0f, 1f) else 0f
        val next = coastalLight(snapshot.climate, request.opening, dryStorm, flash)
        val level = (snapshot.climate?.watershed?.recharge ?: 64) / 255f
        if (next == light && request.reducedMotion == lightMotion && level == recharge) return
        light = next
        lightMotion = request.reducedMotion
        recharge = level
        stage.setLighting(lightingTexels(next, request.reducedMotion, level).data(), Backdrop.r, Backdrop.g, Backdrop.b)
    }

    fun show(snapshot: WorldRenderProjection, request: WorldCamera, cameraDistance: Float, dt: Float, seconds: Float, nanos: Long, batch: TransformBatch) {
        // The fine blades are subpixel at survey height.
        batch.put(fineGrass, 0f, 0f, 0f, TransformBatch.Identity, 1f, 1f, 1f, visible = cameraDistance < 12.4f)
        pines.show(snapshot)
        forage.show(snapshot)
        creatures.show(snapshot, request.reducedMotion, dt, nanos, batch)
        markers.show(request, request.selected?.let { creatures.shownAt(it) }, snapshot, seconds, batch)
        atmosphere.show(snapshot, light, seconds, request.reducedMotion, batch)
        fire.show(snapshot.fire, seconds, request.reducedMotion, targeting = request.targetSites.isNotEmpty(), batch)
    }
}

/** The island's pines, baked per variant into one swaying mesh and rebuilt when fire takes or returns a stand. */
private class StagePines(private val stage: WorldStage, private val ids: StageIds, private val terrain: TerrainMap) {
    private val placements = terrain.pinePlacements()
    private val models = PineVariants.associateWith { loadBundledModel(it.asset.path) }
    private val material = ids.take().also { stage.defineMaterial(it, StageShading.Foliage, -1, 1f, 1f, 1f, 1f, 0) }
    private val meshes = PineVariants.associateWith { variant ->
        ids.take().also { mesh -> stage.addEntity(ids.take(), -1, mesh, material).also { stage.define(mesh, bake(variant, emptyMap(), emptySet())) } }
    }
    private var shownStands: Map<Int, Succession>? = null
    private var shownCells: Set<Long>? = null

    fun show(snapshot: WorldRenderProjection) {
        val stands = snapshot.fire?.stands.orEmpty()
        val cells = buildSet {
            for (site in snapshot.settlements.orEmpty()) {
                val village = site.village ?: continue
                for (ground in village.structures.map { it.ground } + village.center) {
                    for (dz in -1..1) for (dx in -1..1) add(((ground.x + dx).toLong() shl 32) or ((ground.z + dz).toLong() and 0xffffffffL))
                }
            }
        }
        if (stands == shownStands && cells == shownCells) return
        val first = shownStands == null
        shownStands = stands
        shownCells = cells
        if (first && stands.isEmpty() && cells.isEmpty()) return
        for ((variant, mesh) in meshes) stage.define(mesh, bake(variant, stands, cells))
    }

    private fun bake(variant: id.branchworld.renderer.geometry.PineVariant, stands: Map<Int, Succession>, cells: Set<Long>) =
        bakeModel(
            checkNotNull(models[variant]),
            placements.filter { it.variant == variant && it.cell !in cells }
                .filter { stands[it.prop].let { stage -> stage == null || stage == Succession.Scrub || stage == Succession.Grove } }
                .map { pine ->
                    val young = stands[pine.prop] == Succession.Scrub
                    val q = yawQuaternion(pine.yawDegrees)
                    val s = pine.scale * if (young) YoungPine else 1f
                    Mat4.trs(pine.x, pine.y, pine.z, q[0], q[1], q[2], q[3], s, s, s)
                },
            paint = { material ->
                when (material) {
                    0 -> PineBark
                    1 -> variant.foliage
                    else -> variant.underFoliage()
                }
            },
            // The crown sways; the trunk stands (Kool's pineSway uses the same band).
            crown = { y -> ((y - 0.17f) / (1.29f - 0.17f)).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) } },
        )
}

/** Meadow grass, berry bushes and planted trees, each kind merged into one mesh and rebuilt when the patches change. */
private class StageForage(private val stage: WorldStage, private val ids: StageIds, private val terrain: TerrainMap, wildlife: Boolean) {
    private val material = ids.take().also { stage.defineMaterial(it, StageShading.Plain, -1, 1f, 1f, 1f, 1f, 0) }
    private val shapes = ForageKind.entries.associateWith { forageTuft(it, wildlife) }
    private val tree = sapling()
    private val meshes = (ForageKind.entries.map { it.name } + "Planted").associateWith { ids.take() }
    private val entities = meshes.mapValues { (_, mesh) -> ids.take().also { stage.addEntity(it, -1, mesh, material) } }
    private var shown: List<ForagePose>? = null

    fun show(snapshot: WorldRenderProjection) {
        val patches = snapshot.forage
        if (patches == shown) return
        shown = patches
        for (kind in ForageKind.entries) {
            update(kind.name, checkNotNull(shapes[kind]), patches.filter { it.kind == kind && !it.planted && it.biomassTier > 0 }, snapshot)
        }
        update("Planted", tree, patches.filter { it.planted }, snapshot)
    }

    private fun update(key: String, shape: id.branchworld.renderer.geometry.SurfaceMesh, visible: List<ForagePose>, snapshot: WorldRenderProjection) {
        val placements = visible.map { patch ->
            val p = cellPosition(patch.ground.x, patch.ground.z, patch.ground.elevationTwice)
            val height = when {
                !patch.planted -> patch.biomassTier / 4f
                snapshot.shaping != null -> 0.22f + patch.biomassTier * 0.25f
                else -> 0.6f + patch.biomassTier * 0.13f
            }
            Mat4.trs(p.x, p.y, p.z, 0f, 0f, 0f, 1f, 1f, height, 1f)
        }
        stage.define(checkNotNull(meshes[key]), instanced(shape, placements))
    }
}

/** The placement pegs, the ring under a picked creature, and the warrens and dens the animals dug. */
private class StageMarkers(private val stage: WorldStage, private val ids: StageIds, private val terrain: TerrainMap) {
    private val material = ids.take().also { stage.defineMaterial(it, StageShading.Plain, -1, 1f, 1f, 1f, 1f, 0) }
    private val valid = entity(placementPeg(Rgba(0.30f, 0.62f, 0.22f), Rgba(0.96f, 0.93f, 0.80f)))
    private val invalid = entity(placementPeg(Rgba(0.74f, 0.20f, 0.13f), Rgba(0.98f, 0.86f, 0.80f)))
    private val ring = entity(selectionRing())
    private val homesMesh = ids.take()
    private var homes: Pair<List<GroundPoint>, List<GroundPoint>>? = null

    init {
        stage.addEntity(ids.take(), -1, homesMesh, material)
    }

    private fun entity(mesh: id.branchworld.renderer.geometry.SurfaceMesh): Int {
        val meshId = ids.take().also { stage.define(it, mesh) }
        return ids.take().also { stage.addEntity(it, -1, meshId, material) }
    }

    fun show(request: WorldCamera, selected: id.branchworld.renderer.geometry.Point3?, snapshot: WorldRenderProjection, seconds: Float, batch: TransformBatch) {
        val placement = request.placement
        val bob = if (request.reducedMotion) 0f else sin(seconds * 4.2f) * 0.012f
        if (placement != null) {
            val p = cellPosition(placement.ground.x, placement.ground.z, placement.ground.elevationTwice)
            val y = terrain.heightAt(p.x, p.z).coerceAtLeast(0f)
            // Plants mark a smaller patch than animals.
            val size = if (placement.tool == PlaceTool.Tree || placement.tool == PlaceTool.Meadow) 0.8f else 1f
            val shownPeg = if (placement.valid) valid else invalid
            batch.put(shownPeg, p.x, y - 0.01f + bob, p.z, TransformBatch.Identity, size, size, size, visible = true)
            batch.hide(if (placement.valid) invalid else valid)
        } else {
            batch.hide(valid)
            batch.hide(invalid)
        }
        if (selected != null) {
            val turn = (seconds * 0.6f) % 6.2831855f
            batch.put(ring, selected.x, selected.y + 0.025f, selected.z, yawQuaternion(turn * 57.29578f), 1f, 1f, 1f, visible = true)
        } else batch.hide(ring)
        val life = snapshot.life
        val warrens = life?.warrens.orEmpty()
        val dens = life?.packs?.map { it.den }.orEmpty()
        if (homes != warrens to dens) {
            homes = warrens to dens
            stage.define(homesMesh, warrensAndDens(terrain, warrens, dens))
        }
    }
}

/** The animals: one entity tree per creature from the species' glTF model, posed by the shared motion and clips. */
private class StageCreatures(
    private val stage: WorldStage,
    private val ids: StageIds,
    private val terrain: TerrainMap,
    textures: MutableMap<String, Int>,
    wildlife: Boolean,
) {
    private val models = mapOf(
        Species.Rabbit to (DioramaAsset.Bunny to Coat.Rabbit),
        Species.Deer to (DioramaAsset.Deer to Coat.None),
        Species.Fox to (DioramaAsset.Fox to Coat.Fox),
        Species.Wolf to (DioramaAsset.Wolf to Coat.Wolf),
    ).mapValues { (_, source) ->
        val (asset, coat) = source
        // Kool paints the coats only in worlds with wildlife, where the fox model is loaded too.
        StageModel(stage, ids, loadBundledModel(asset.path), asset.path.substringBeforeLast('/'), if (wildlife) coat else Coat.None, textures).also { model ->
            for (clip in CreatureClips) check(model.duration(clip) > 0f) { "The ${asset.file} model is missing animation $clip" }
        }
    }
    private val pacing = CreaturePacing()
    private val active = mutableMapOf<Long, Body>()
    private val unused = mutableMapOf<Species, MutableList<Body>>()

    private class Body(val species: Species, val rig: StageRig, val motion: CreatureMotion)

    fun shownAt(id: Long) = active[id]?.motion?.shown()

    fun show(world: WorldRenderProjection, reducedMotion: Boolean, dt: Float, nanos: Long, batch: TransformBatch) {
        pacing.frame(world, reducedMotion, nanos, dt)?.let { continuous -> retarget(world, continuous, reducedMotion, batch) }
        val rate = pacing.rate
        for (body in active.values) {
            body.motion.animate(dt, rate, reducedMotion)
            place(body, batch)
        }
    }

    private fun retarget(world: WorldRenderProjection, continuous: Boolean, reducedMotion: Boolean, batch: TransformBatch) {
        val village = villageGround(world)
        val arrivals = world.life?.arrivals.orEmpty()
        val speciesById = world.creatures.associate { it.id to it.species }
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val (id, body) = iterator.next()
            if (speciesById[id] == body.species) continue
            batch.hide(body.rig.root)
            unused.getOrPut(body.species, ::mutableListOf).add(body)
            iterator.remove()
        }
        for (pose in world.creatures) {
            val previous = active[pose.id]
            val body = active.getOrPut(pose.id) {
                unused[pose.species]?.removeLastOrNull() ?: models.getValue(pose.species).let { model ->
                    Body(pose.species, model.instantiate(-1), CreatureMotion(pose.species, terrain, model::duration))
                }
            }
            body.motion.retarget(world.tick, pose, continuous && previous != null, reducedMotion, villageScale(pose, village),
                dropIn = previous == null && pose.id in arrivals && !reducedMotion)
        }
    }

    private fun place(body: Body, batch: TransformBatch) {
        val m = body.motion
        val rotation = quaternionProduct(yawQuaternion(m.yaw), pitchQuaternion(m.lean))
        batch.put(body.rig.root, m.x, m.y, m.z, rotation, m.scale, m.scale, m.scale, visible = true)
        body.rig.pose(batch, body.rig.model.pose(m.clip, m.clipPhase))
    }
}

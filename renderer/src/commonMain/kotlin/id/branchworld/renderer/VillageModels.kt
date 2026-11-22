package id.branchworld.renderer

import de.fabmax.kool.Assets
import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.gltf.GltfFile
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.loadGltfFile
import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.InstanceLayouts
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.releaseWith
import id.branchworld.game.ecology.StructureKind
import id.branchworld.game.ecology.VillageTask
import id.branchworld.game.simulation.GroundPoint
import id.branchworld.game.simulation.SettlementPose
import id.branchworld.game.simulation.VillagerPose
import id.branchworld.game.terrain.TerrainMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * Presents the authority-projected settlements. Buildings, crops and stores are instanced per asset, so a growing
 * village adds no draw calls. Villagers are pooled skinned models whose daily route is derived from the tick.
 */
internal class VillageModels private constructor(
    private val scene: Scene,
    private val props: Map<VillageProp, Model>,
    private val bodies: List<GltfFile>,
    private val atlas: Texture2d,
) {
    private val textures = mutableSetOf<Texture2d>()
    private val bodyTextures = mutableSetOf<Texture2d>()
    private val figures = mutableMapOf<Long, Figure>()
    private val idle = List(bodies.size) { mutableListOf<Figure>() }
    private val matrix = MutableMat4f()
    private var previous: List<SettlementPose>? = null
    private var previousReducedMotion = false
    /** Hidden models never upload their textures, so only what is on screen gates the first complete frame. */
    val ready: Boolean get() {
        val atlasNeeded = props.any { (prop, model) -> prop.textured && model.isVisible }
        val bodiesNeeded = figures.values.any { it.model.isVisible }
        return (!atlasNeeded || atlas.isLoaded) && (!bodiesNeeded || bodyTextures.all { it.isLoaded })
    }

    init {
        atlas.releaseWith(scene)
        for (model in props.values) adopt(model)
    }

    fun show(sites: List<SettlementPose>, tick: Long, dayPhase: Float, terrain: TerrainMap, reducedMotion: Boolean, seconds: Float) {
        if (sites != previous || reducedMotion != previousReducedMotion) {
            previous = sites
            previousReducedMotion = reducedMotion
            placeProps(sites, terrain)
        }
        placeVillagers(sites, tick, dayPhase, terrain, reducedMotion, seconds)
    }

    private fun placeProps(sites: List<SettlementPose>, terrain: TerrainMap) {
        val placed = VillageProp.entries.associateWith { mutableListOf<MutableMat4f>() }
        fun put(prop: VillageProp, x: Float, z: Float, yaw: Float = 0f, scale: Float = prop.scale, lift: Float = 0f) {
            placed.getValue(prop).add(MutableMat4f().translate(x, terrain.heightAt(x, z) + lift, z)
                .rotate(yaw.deg, Vec3f.Y_AXIS).scale(scale))
        }
        for (site in sites) {
            val village = site.village
            if (village == null) {
                // Rules v5–v8 histories keep their original presentation.
                for (home in site.homes) cellPosition(home.x, home.z, home.elevationTwice).let {
                    put(VillageProp.LegacyHome, it.x, it.z)
                }
                cellPosition(site.field.x, site.field.z, site.field.elevationTwice).let { put(VillageProp.Windmill, it.x, it.z) }
                continue
            }
            val center = cellPosition(village.center.x, village.center.z, village.center.elevationTwice)
            if (site.id == 1) put(VillageProp.Windmill, center.x, center.z, yaw = 30f, scale = 0.34f)
            for (structure in village.structures) {
                val at = cellPosition(structure.ground.x, structure.ground.z, structure.ground.elevationTwice)
                // Homes face the village centre, snapped to the hexagonal kit's 60 degree sides.
                val facing = round(degrees(atan2(center.x - at.x, center.z - at.z)) / 60f) * 60f
                when (structure.kind) {
                    StructureKind.Home -> {
                        // A burned home stands as a charred ruin until the rebuilding has raised its frame again.
                        if (structure.burned && structure.stage < 2) {
                            put(VillageProp.Ruin, at.x, at.z, facing)
                            if (structure.stage > 0) put(VillageProp.Scaffolding, at.x, at.z, facing)
                            continue
                        }
                        // A house keeps its colour when a family leaves; empty homes simply go dark.
                        val household = structure.id
                        val building = structure.stage < 4 && structure.level == 1
                        if (!building || structure.stage >= 2) {
                            val enlarged = structure.level >= 2 && structure.stage == 4
                            put(homeProp(household, enlarged), at.x, at.z, facing)
                        }
                        if (structure.stage < 4) {
                            put(VillageProp.Scaffolding, at.x, at.z, facing)
                            put(VillageProp.Lumber, at.x + 0.16f, at.z + 0.11f, facing + 90f)
                        }
                    }
                    StructureKind.Well -> {
                        if (structure.stage >= 4) put(VillageProp.Well, at.x, at.z, facing)
                        else {
                            put(VillageProp.Scaffolding, at.x, at.z, facing, scale = 0.11f)
                            put(VillageProp.Stones, at.x + 0.06f, at.z - 0.05f)
                        }
                    }
                    StructureKind.Field, StructureKind.Clearing -> {
                        if (structure.kind == StructureKind.Clearing && structure.stage < 4) {
                            for (i in 0 until 3) put(VillageProp.Stump, at.x + (i - 1) * 0.055f, at.z + (i % 2) * 0.05f - 0.02f, i * 70f)
                            put(VillageProp.Logs, at.x + 0.07f, at.z + 0.06f, 20f)
                            continue
                        }
                        for (fx in 0..1) for (fz in 0..1) {
                            put(VillageProp.Furrows, at.x + (fx - 0.5f) * 0.17f, at.z + (fz - 0.5f) * 0.17f, facing)
                        }
                        // Standing crops follow the last real harvest; a parched field shows bare furrows.
                        val clumps = when (village.harvest) { 0 -> 0; 1 -> 3; 2 -> 6; else -> 9 }
                        for (i in 0 until clumps) {
                            val dx = (i % 3 - 1) * 0.095f
                            val dz = (i / 3 - 1) * 0.095f
                            put(VillageProp.Wheat, at.x + dx, at.z + dz, i * 47f)
                        }
                        put(VillageProp.Fence, at.x - 0.20f, at.z, facing + 90f)
                        put(VillageProp.Fence, at.x + 0.20f, at.z, facing + 90f)
                        if (structure.kind == StructureKind.Clearing) put(VillageProp.Stump, at.x + 0.12f, at.z - 0.09f, 40f)
                    }
                }
            }
            // Stores beside the centre show the village's real stock levels.
            val stores = listOf(VillageProp.Barrel to village.food, VillageProp.Sack to village.food,
                VillageProp.Lumber to village.wood, VillageProp.Crate to village.stone)
            for ((index, store) in stores.withIndex()) {
                val (prop, tier) = store
                for (item in 0 until tier) {
                    val angle = 2.2f + index * 0.62f + item * 0.19f
                    val radius = 0.24f + item * 0.035f
                    put(prop, center.x + cos(angle) * radius, center.z + sin(angle) * radius, angle * 57.3f)
                }
            }
        }
        for ((prop, model) in props) {
            val matrices = placed.getValue(prop)
            model.isVisible = matrices.isNotEmpty()
            for (mesh in model.meshes.values) {
                mesh.addInstances<InstanceLayouts.ModelMat>(matrices.size, clear = true) { buffer ->
                    for (each in matrices) buffer.put { set(it.modelMat, each) }
                }
            }
        }
    }

    private fun homeProp(household: Int, enlarged: Boolean) = when (household % 4) {
        0 -> if (enlarged) VillageProp.HomeBlueLarge else VillageProp.HomeBlue
        1 -> if (enlarged) VillageProp.HomeRedLarge else VillageProp.HomeRed
        2 -> if (enlarged) VillageProp.HomeYellowLarge else VillageProp.HomeYellow
        else -> if (enlarged) VillageProp.HomeGreenLarge else VillageProp.LegacyHome
    }

    private fun placeVillagers(
        sites: List<SettlementPose>, tick: Long, phase: Float, terrain: TerrainMap, reducedMotion: Boolean, seconds: Float,
    ) {
        val poses = sites.flatMap { site -> site.village?.villagers.orEmpty().map { it to site } }
        val legacy = sites.mapNotNull { site -> site.resident?.let { site to it } }
        val wanted = poses.map { it.first.id }.toSet() + legacy.map { -it.first.id.toLong() }
        val stale = figures.keys.filter { it !in wanted }
        for (id in stale) {
            val figure = checkNotNull(figures.remove(id))
            figure.model.isVisible = false
            idle[figure.body].add(figure)
        }
        for ((pose, site) in poses) {
            val figure = figures.getOrPut(pose.id) { idle[pose.body].removeLastOrNull() ?: makeFigure(pose.body) }
            val routine = routine(pose, site, phase, tick, reducedMotion)
            figure.model.isVisible = routine != null
            if (routine != null) figure.place(routine, terrain, seconds, reducedMotion, pose.id)
        }
        for ((site, resident) in legacy) {
            val figure = figures.getOrPut(-site.id.toLong()) { idle[0].removeLastOrNull() ?: makeFigure(0) }
            val bank = site.waterBank?.takeIf { site.haulingWater && !reducedMotion }
            val trip = if (bank == null) 0f else 1f - abs((tick % 60) / 30f - 1f)
            figure.model.isVisible = true
            figure.place(Routine(resident, bank ?: resident, trip, if (bank == null) "idle" else "carry", null, tick % 60 >= 30),
                terrain, seconds, reducedMotion, site.id.toLong())
        }
    }

    /** Morning walk out, a day's work, the walk home at dusk, a rest by the door, then sleep. */
    private fun routine(pose: VillagerPose, site: SettlementPose, phase: Float, tick: Long, reducedMotion: Boolean): Routine? {
        val work = workClip(pose.task)
        if (pose.task == VillageTask.HaulingWater && phase < DuskEnd) {
            if (reducedMotion) return Routine(pose.work, pose.work, 1f, "idle", pose.home)
            val cycle = (tick + pose.id * 7) % 80
            return Routine(pose.home, pose.work, 1f - abs(cycle / 40f - 1f), "carry", null, returning = cycle >= 40)
        }
        val stagger = (pose.id % 5) * 0.006f
        return when {
            phase < MorningStart + stagger -> null
            phase < WorkStart + stagger -> Routine(pose.home, pose.work,
                if (reducedMotion) 1f else (phase - MorningStart - stagger) / (WorkStart - MorningStart), "walk", null)
            phase < WorkEnd - stagger -> Routine(pose.work, pose.work, 1f, work, site.village?.center)
            phase < HomeArrive - stagger -> Routine(pose.work, pose.home,
                if (reducedMotion) 1f else (phase - WorkEnd + stagger) / (HomeArrive - WorkEnd), "walk", null)
            phase < DuskEnd -> Routine(pose.home, pose.home, 1f, if (pose.id % 3L == 0L) "wave" else "sit", site.village?.center)
            else -> null
        }
    }

    private fun workClip(task: VillageTask) = when (task) {
        VillageTask.Farming -> "dig"
        VillageTask.Gathering -> "gather"
        VillageTask.Woodcutting -> "chop"
        VillageTask.Quarrying -> "pick"
        VillageTask.Building, VillageTask.Settling -> "hammer"
        VillageTask.HaulingWater -> "carry"
        VillageTask.Idle -> "idle"
    }

    private fun makeFigure(body: Int): Figure {
        val model = bodies[body].makeModel(GltfLoadConfig(assetLoader = Assets.defaultLoader, pbrBlock = {
            metallic(0f)
            roughness(0.9f)
        }))
        bodyTextures.addAll(model.textures.values)
        adopt(model)
        return Figure(body, model)
    }

    private fun adopt(model: Model) {
        for (texture in model.textures.values) {
            if (textures.add(texture)) texture.releaseWith(scene)
        }
        model.textures.clear()
        scene.addNode(model)
    }

    private class Routine(
        val from: GroundPoint, val to: GroundPoint, val progress: Float, val clip: String, val faceToward: GroundPoint?,
        val returning: Boolean = false,
    )

    private inner class Figure(val body: Int, val model: Model) {
        private val clips = VillagerClips.associateWith { name -> model.animations.indexOfFirst { it.name == name } }
        private var enabled = -1

        fun place(routine: Routine, terrain: TerrainMap, seconds: Float, reducedMotion: Boolean, id: Long) {
            val jitterAngle = id * 2.399f
            val jitter = 0.07f
            val a = cellPosition(routine.from.x, routine.from.z, routine.from.elevationTwice)
            val b = cellPosition(routine.to.x, routine.to.z, routine.to.elevationTwice)
            val t = routine.progress.coerceIn(0f, 1f)
            val x = a.x + (b.x - a.x) * t + cos(jitterAngle) * jitter
            val z = a.z + (b.z - a.z) * t + sin(jitterAngle) * jitter
            val yaw = when {
                routine.from != routine.to -> degrees(atan2(b.x - a.x, b.z - a.z)) + if (routine.returning) 180f else 0f
                routine.faceToward != null -> {
                    val f = cellPosition(routine.faceToward.x, routine.faceToward.z, routine.faceToward.elevationTwice)
                    degrees(atan2(f.x - x, f.z - z))
                }
                else -> jitterAngle * 57.3f
            }
            val clip = clips[routine.clip]?.takeIf { it >= 0 } ?: clips.getValue("idle")
            if (clip != enabled) {
                enabled = clip
                model.enableAnimation(clip)
            }
            val animation = model.animations[clip]
            animation.progress = if (reducedMotion) 0f else ((seconds + (id % 17) * 0.37f) % animation.duration)
            model.applyAnimation(0f)
            model.transform.setIdentity().translate(x, terrain.heightAt(x, z), z).rotate(yaw.deg, Vec3f.Y_AXIS).scale(VillagerScale)
        }
    }

    companion object {
        private const val VillagerScale = 0.095f
        private const val MorningStart = 0.02f
        private const val WorkStart = 0.07f
        private const val WorkEnd = 0.40f
        private const val HomeArrive = 0.45f
        private const val DuskEnd = 0.60f

        suspend fun load(scene: Scene): VillageModels {
            // Every KayKit building shares one atlas; load it once instead of once per model file.
            val atlas = Texture2d(TexFormat.RGBA, MipMapping.Full, SamplerSettings(), "hexagons_medieval") {
                Assets.defaultLoader.loadImage2d("diorama/hexagons_medieval.png").getOrThrow()
            }
            val props = VillageProp.entries.associateWith { prop ->
                Assets.loadGltfFile("diorama/${prop.file}").getOrThrow().makeModel(GltfLoadConfig(
                    assetLoader = Assets.defaultLoader,
                    applyTransforms = true,
                    loadAnimations = false,
                    instanceLayout = InstanceLayouts.ModelMat,
                    pbrBlock = { primitive ->
                        if (primitive.materialRef?.pbrMetallicRoughness?.baseColorTexture != null) color {
                            colorSources.clear()
                            textureColor(atlas)
                        }
                        metallic(0f)
                        roughness(0.92f)
                    },
                )).also { model ->
                    model.isVisible = false
                    for (mesh in model.meshes.values) mesh.isFrustumChecked = false
                }
            }
            val bodies = VillagerBodies.map { Assets.loadGltfFile("diorama/$it").getOrThrow() }
            return VillageModels(scene, props, bodies, atlas)
        }
    }
}

internal enum class VillageProp(val file: String, val scale: Float) {
    LegacyHome("building_home_A_green.gltf", 0.30f),
    HomeBlue("building_home_A_blue.gltf", 0.38f),
    HomeRed("building_home_A_red.gltf", 0.38f),
    HomeYellow("building_home_A_yellow.gltf", 0.38f),
    HomeBlueLarge("building_home_B_blue.gltf", 0.35f),
    HomeRedLarge("building_home_B_red.gltf", 0.35f),
    HomeYellowLarge("building_home_B_yellow.gltf", 0.35f),
    HomeGreenLarge("building_home_B_green.gltf", 0.35f),
    Windmill("building_windmill_green.gltf", 0.24f),
    Well("building_well_blue.gltf", 0.33f),
    Scaffolding("building_scaffolding.gltf", 0.16f),
    Lumber("resource_lumber.gltf", 0.30f),
    Barrel("barrel.gltf", 0.36f),
    Sack("sack.gltf", 0.62f),
    Crate("crate_A_small.gltf", 0.36f),
    Fence("fence_wood_straight.gltf", 0.22f),
    Furrows("crops_dirtDoubleRow.glb", 0.17f),
    Wheat("crops_wheatStageB.glb", 0.24f),
    Stump("stump_old.glb", 0.26f),
    Logs("log_stack.glb", 0.26f),
    Stones("rock_smallH.glb", 0.15f),
    /** Rules v11: what a fire leaves of a home. */
    Ruin("building_destroyed.gltf", 0.38f),
    ;

    /** KayKit .gltf models use the shared atlas; Kenney .glb models use material colours. */
    val textured: Boolean get() = file.endsWith(".gltf")
}

private val VillagerBodies = listOf("villager_ranger.glb", "villager_rogue.glb", "villager_barbarian.glb")
private val VillagerClips = listOf("idle", "walk", "carry", "chop", "dig", "hammer", "pick", "gather", "sit", "wave")

private fun degrees(radians: Float): Float = radians * 57.29578f

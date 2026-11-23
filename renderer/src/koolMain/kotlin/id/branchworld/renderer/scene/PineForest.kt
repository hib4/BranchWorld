package id.branchworld.renderer.scene

import de.fabmax.kool.Assets
import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.loadGltfFile
import de.fabmax.kool.scene.InstanceLayouts
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.Scene
import id.branchworld.renderer.geometry.PineBark
import id.branchworld.renderer.geometry.PineVariant
import id.branchworld.renderer.geometry.PineVariants
import id.branchworld.renderer.geometry.YoungPine
import id.branchworld.renderer.geometry.pinePlacements
import id.branchworld.renderer.geometry.underFoliage
import id.branchworld.simulation.ecology.fire.Succession
import id.branchworld.simulation.projection.SettlementPose
import id.branchworld.simulation.terrain.TerrainMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * The island's pines: three instanced models placed on the terrain's pine props. Trees on settlement plots are hidden
 * while the plots are in use (cosmetic); pines the fire took or the axe felled come down and return as young trees,
 * following the authoritative stand record (rules v11).
 */
internal class PineForest(private val scene: Scene, private val terrain: TerrainMap, private val isCurrent: () -> Boolean) {
    /** Each pine model's instances with their cells, so settlements can clear the trees on their plots. */
    private var stands = emptyList<Pair<Model, List<PineInstance>>>()
    private var shownStands: Map<Int, Succession>? = null
    private var clearedCells: Set<Long>? = null

    /** Loads and places every pine variant; returns the models so the session can light and wait for them. */
    suspend fun plant(): List<Model> = PineVariants.mapNotNull { variant -> place(variant) }

    fun show(sites: List<SettlementPose>, burned: Map<Int, Succession>) {
        if (stands.isEmpty()) return
        val cells = buildSet {
            for (site in sites) {
                val village = site.village ?: continue
                for (ground in village.structures.map { it.ground } + village.center) {
                    for (dz in -1..1) for (dx in -1..1) add(cellKey(ground.x + dx, ground.z + dz))
                }
            }
        }
        if (cells == clearedCells && burned == shownStands) return
        clearedCells = cells
        shownStands = burned
        for ((model, stand) in stands) {
            val kept = stand.filter { it.cell !in cells && burned[it.prop].let { stage -> stage == null || stage == Succession.Scrub || stage == Succession.Grove } }
            model.meshes.values.forEach { mesh ->
                mesh.addInstances<InstanceLayouts.ModelMat>(kept.size, clear = true) { buffer ->
                    for (pine in kept) {
                        val young = burned[pine.prop] == Succession.Scrub
                        val matrix = if (young) MutableMat4f(pine.matrix).scale(YoungPine) else pine.matrix
                        buffer.put { set(it.modelMat, matrix) }
                    }
                }
            }
        }
    }

    private suspend fun place(variant: PineVariant): Model? {
        val placements = terrain.pinePlacements().filter { it.variant == variant }
        val cells = placements.map { it.cell }
        val props = placements.map { it.prop }
        val matrices = placements.map { pine ->
            MutableMat4f().translate(pine.x, pine.y, pine.z).rotate(pine.yawDegrees.deg, Vec3f.Y_AXIS).scale(pine.scale)
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
                    0 -> PineBark
                    1 -> variant.foliage
                    else -> variant.underFoliage()
                }.kool()
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
            stands = stands + (model to cells.indices.map { PineInstance(props[it], cells[it], matrices[it]) })
            model.meshes.values.forEach { mesh ->
                mesh.isFrustumChecked = false
                mesh.addInstances<InstanceLayouts.ModelMat>(matrices.size) { buffer ->
                    for (matrix in matrices) buffer.put { set(it.modelMat, matrix) }
                }
            }
        }
    }

    private suspend fun attachWhileCurrent(model: Model) {
        if (!isCurrent() || !currentCoroutineContext().isActive) {
            model.release()
            throw CancellationException("The island scene was replaced while loading its models")
        }
        scene.addNode(model)
    }
}

private class PineInstance(val prop: Int, val cell: Long, val matrix: MutableMat4f)

private fun cellKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)

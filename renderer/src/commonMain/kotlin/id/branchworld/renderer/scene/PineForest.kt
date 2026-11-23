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
import de.fabmax.kool.util.Color
import id.branchworld.renderer.assets.DioramaAsset
import id.branchworld.simulation.ecology.fire.Succession
import id.branchworld.simulation.projection.SettlementPose
import id.branchworld.simulation.terrain.PropKind
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

/** Young pines regrowing on burned or felled ground stand at this fraction of their grown height. */
private const val YoungPine = 0.42f

private fun cellKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)

private data class PineVariant(val asset: DioramaAsset, val height: Float, val sourceHeight: Float, val foliage: Color)

private val PineVariants = listOf(
    PineVariant(DioramaAsset.Pine, 0.89f, 1.3664f, Color(0.070f, 0.206f, 0.114f)),
    PineVariant(DioramaAsset.RoundedPine, 0.72f, 1.1959f, Color(0.130f, 0.244f, 0.103f)),
    PineVariant(DioramaAsset.TallPine, 1.04f, 1.5296f, Color(0.058f, 0.158f, 0.108f)),
)

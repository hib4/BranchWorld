package id.branchworld.renderer

import de.fabmax.kool.Assets
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.loadGltfFile
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.releaseWith
import id.branchworld.game.simulation.SettlementPose

/** Bounded scene-owned models for the authority-projected lots and resident marker. */
internal class SettlementModels private constructor(private val scene: Scene, private val assets: List<ModelFactory>) {
    private val textures = mutableSetOf<Texture2d>()
    private var previous: List<SettlementPose>? = null
    val ready: Boolean get() = textures.all { it.isLoaded }

    fun show(sites: List<SettlementPose>) {
        if (previous == sites) return
        previous = sites
        for ((index, points) in listOf(
            sites.flatMap { it.homes },
            sites.map { it.field },
            sites.mapNotNull { it.resident },
        ).withIndex()) {
            val asset = assets[index]
            for ((positionIndex, point) in points.withIndex()) {
                if (positionIndex == asset.models.size) asset.models.add(makeModel(asset))
                val model = asset.models[positionIndex]
                model.isVisible = true
                model.transform.setIdentity()
                    .translate(cellPosition(point.x, point.z, point.elevationTwice))
                    .scale(asset.scale)
            }
            for (unused in points.size until asset.models.size) asset.models[unused].isVisible = false
        }
    }

    private fun makeModel(asset: ModelFactory): Model = asset.create().also { model ->
        for (texture in model.textures.values) {
            if (textures.add(texture)) texture.releaseWith(scene)
        }
        model.textures.clear()
        scene.addNode(model)
    }

    private class ModelFactory(val scale: Float, val create: () -> Model) {
        val models = mutableListOf<Model>()
    }

    companion object {
        suspend fun load(scene: Scene): SettlementModels {
            val sizes = listOf(
                DioramaAsset.SettlementHome to 0.26f,
                DioramaAsset.SettlementFarm to 0.24f,
                DioramaAsset.Settler to 0.15f,
            )
            val factories = sizes.map { (asset, size) ->
                val file = Assets.loadGltfFile(asset.path).getOrThrow()
                ModelFactory(size) {
                    file.makeModel(GltfLoadConfig(assetLoader = Assets.defaultLoader, loadAnimations = false))
                }
            }
            return SettlementModels(scene, factories)
        }
    }
}

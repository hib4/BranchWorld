package id.branchworld.renderer.geometry

import id.branchworld.renderer.assets.DioramaAsset
import id.branchworld.simulation.terrain.PropKind
import id.branchworld.simulation.terrain.TerrainMap

/** One of the three pine models, its drawn height, its file's own height and its foliage colour. */
internal data class PineVariant(val asset: DioramaAsset, val height: Float, val sourceHeight: Float, val foliage: Rgba)

internal val PineVariants = listOf(
    PineVariant(DioramaAsset.Pine, 0.89f, 1.3664f, Rgba(0.070f, 0.206f, 0.114f)),
    PineVariant(DioramaAsset.RoundedPine, 0.72f, 1.1959f, Rgba(0.130f, 0.244f, 0.103f)),
    PineVariant(DioramaAsset.TallPine, 1.04f, 1.5296f, Rgba(0.058f, 0.158f, 0.108f)),
)

/** The bark colour, and the colour of a pine's lower foliage layer: [PineVariant.foliage] toward olive. */
internal val PineBark = Rgba(0.28f, 0.161f, 0.084f)
internal fun PineVariant.underFoliage() = foliage.mix(Rgba(0.22f, 0.31f, 0.11f), 0.35f)

/** Young pines regrowing on burned or felled ground stand at this fraction of their grown height. */
internal const val YoungPine = 0.42f

/** Where one pine stands: on terrain prop [prop] at cell ([cellX], [cellZ]), turned [yawDegrees] and scaled. */
internal data class PinePlacement(
    val variant: PineVariant, val prop: Int, val cellX: Int, val cellZ: Int,
    val x: Float, val y: Float, val z: Float, val yawDegrees: Float, val scale: Float,
) {
    val cell: Long get() = (cellX.toLong() shl 32) or (cellZ.toLong() and 0xffffffffL)
}

/** Every pine on the island: one per pine prop, its variant, size and turn chosen from the prop's cell. */
internal fun TerrainMap.pinePlacements(): List<PinePlacement> = buildList {
    for (index in 0 until propCount) {
        val prop = prop(index)
        if (prop.kind != PropKind.Pine) continue
        val pattern = (prop.x * 37 xor prop.z * 53) and 15
        val variant = if (pattern < 7) PineVariants[0] else if (pattern < 12) PineVariants[1] else PineVariants[2]
        val cell = checkNotNull(cellAt(prop.x, prop.z))
        val center = cellPosition(prop.x, prop.z, cell.elevationTwice)
        val variation = 0.82f + ((prop.x * 17 + prop.z * 41) and 15) / 38f
        val scale = variant.height / variant.sourceHeight * variation * (prop.scalePercent / 100f)
        // The Kenney scene nodes put the base 0.05 units below their file origin.
        add(PinePlacement(variant, index, prop.x, prop.z, center.x, heightAt(center.x, center.z) + 0.05f * scale, center.z,
            prop.yawDegrees.toFloat(), scale))
    }
}

@file:OptIn(ExperimentalForeignApi::class)

package id.branchworld.renderer.stage

import id.branchworld.renderer.geometry.Point3
import id.branchworld.renderer.geometry.Rgba
import id.branchworld.renderer.lighting.CoastalLight
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/** The shading programs the Swift stage compiles from `WorldShaders.metal`. */
internal object StageShading {
    /** Terrain: vertex colours, sun and fill light, a faint highlight and the land haze. */
    const val Land = 0
    /** The sea: shallows-to-deep vertex colours, waves, foam, rain rings and horizon haze. */
    const val Sea = 1
    /** glTF models: texture or vertex colour times the tint, lit and hazed; the variant picks an animal coat. */
    const val Model = 2
    /** Baked pines: vertex colours whose alpha is how far up the crown a vertex is, so the tops sway. */
    const val Foliage = 3
    /** Small lit shapes (markers, plants, burrows), without haze. */
    const val Plain = 4
    /** Unlit vertex colours with alpha: soft shade under trees. */
    const val Translucent = 5
    /** Grass blades and reeds bending in the wind; vertex alpha is how far a point bends. */
    const val Wind = 6
    /** Rivers, lakes and marshes: current, glints and the estuary meeting the sea. */
    const val River = 7
    /** Fire scars with soft edges; texture coordinates carry each tile's edge mask. */
    const val Scar = 8
    /** Flames and embers; the variant is the wind's direction. */
    const val Flame = 9
    /** Smoke rising downwind; the variant is the wind's direction. */
    const val Smoke = 10
}

/** Coats painted over the shared Cube Pets texture, as the Kool renderer does. */
internal object Coat {
    const val None = 0
    const val Rabbit = 1
    const val Wolf = 2
    const val Fox = 3
}

/**
 * The eight RGBA texels every shader reads its lighting from, so one small texture update relights the whole island.
 * Layout: sun direction and strength; sun colour and fill strength; fill colour and watershed recharge; land mist and
 * its strength; fill
 * direction and meadow wind; water light (sun, twilight, rain) and canopy wind; sea haze and motion; meadow tint.
 */
internal fun lightingTexels(light: CoastalLight, reducedMotion: Boolean, recharge: Float): ByteArray {
    fun unit(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
    fun direction(d: Point3) = d.norm().let { listOf((it.x + 1f) / 2f, (it.y + 1f) / 2f, (it.z + 1f) / 2f) }
    val haze = light.haze
    val mist = Rgba(haze.r * haze.r, haze.g * haze.g, haze.b * haze.b, if (light.rain) 0.49f else 0.31f)
    val meadowWind = if (reducedMotion) 0f else if (light.storm || light.dryStorm) 0.077f else if (light.rain) 0.057f else 0.026f
    val canopyWind = if (reducedMotion) 0f else if (light.rain || light.dryStorm) 0.045f else 0.019f
    val tint = Rgba(0.56f, 0.63f, 0.70f).mix(Rgba(1.08f, 1.02f, 0.85f), light.sun)
    val values = buildList {
        addAll(direction(light.direction)); add(light.strength / 2f)
        add(light.lightColor.r); add(light.lightColor.g); add(light.lightColor.b); add(light.fill)
        add(light.fillColor.r); add(light.fillColor.g); add(light.fillColor.b); add(recharge)
        add(mist.r); add(mist.g); add(mist.b); add(mist.a)
        addAll(direction(FillDirection)); add(meadowWind * 10f)
        add(light.sun); add(light.twilight); add(if (light.rain) 1f else 0f); add(canopyWind * 10f)
        add(haze.r); add(haze.g); add(haze.b); add(if (reducedMotion) 0f else 1f)
        add(tint.r / 1.1f); add(tint.g / 1.1f); add(tint.b / 1.1f); add(1f)
    }
    return ByteArray(values.size) { unit(values[it]) }
}

/** The fill light's fixed direction in the Kool scene. */
private val FillDirection = Point3(0.6f, -0.72f, 1f)

/** The clear colour behind the world. */
internal val Backdrop = Rgba(0.18f, 0.41f, 0.47f)

internal fun FloatArray.data(): NSData = if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), (size * 4).toULong()) }
internal fun IntArray.data(): NSData = if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), (size * 4).toULong()) }
internal fun ByteArray.data(): NSData = if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { out -> out.usePinned { memcpy(it.addressOf(0), bytes, length) } }
}

/** Files the app bundles under `diorama/`, the same folder the other platforms load from their resources. */
internal object BundleAssets {
    /** Where `diorama/` lives: the app bundle's resources, or the source folder when tests run outside an app. */
    var root: String? = null

    fun read(path: String): ByteArray {
        val root = root ?: checkNotNull(NSBundle.mainBundle.resourcePath) { "No bundle resources" }
        val data = checkNotNull(NSData.dataWithContentsOfFile("$root/$path")) { "Missing bundled asset $path" }
        return data.toByteArray()
    }
}

/** One frame's entity transforms, sent to the stage in a single call. */
internal class TransformBatch {
    private var buffer = ByteArray(48 * 64)
    private var count = 0

    fun put(entity: Int, x: Float, y: Float, z: Float, q: FloatArray, sx: Float, sy: Float, sz: Float, visible: Boolean) {
        if ((count + 1) * 48 > buffer.size) buffer = buffer.copyOf(buffer.size * 2)
        var at = count * 48
        fun int(v: Int) { for (i in 0 until 4) buffer[at++] = (v shr (i * 8)).toByte() }
        fun float(v: Float) = int(v.toRawBits())
        int(entity)
        float(x); float(y); float(z)
        float(q[0]); float(q[1]); float(q[2]); float(q[3])
        float(sx); float(sy); float(sz)
        float(if (visible) 1f else 0f)
        count++
    }

    fun hide(entity: Int) = put(entity, 0f, 0f, 0f, Identity, 1f, 1f, 1f, visible = false)

    val isEmpty get() = count == 0

    /** The batch as bytes, emptied for the next frame. */
    fun drain(): NSData = buffer.copyOf(count * 48).data().also { count = 0 }

    companion object {
        val Identity = floatArrayOf(0f, 0f, 0f, 1f)
    }
}

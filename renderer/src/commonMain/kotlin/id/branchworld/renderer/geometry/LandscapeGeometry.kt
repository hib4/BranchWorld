package id.branchworld.renderer.geometry

import id.branchworld.simulation.terrain.Biome
import id.branchworld.simulation.terrain.PropKind
import id.branchworld.simulation.terrain.TerrainMap
import id.branchworld.simulation.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.simulation.terrain.TerrainMap.Companion.MinCell
import id.branchworld.simulation.terrain.WaterCell
import id.branchworld.simulation.terrain.WaterKind
import kotlin.math.cos
import kotlin.math.sin

// The island's small decorative forms, which use only the recorded geography and never affect creature paths. Shared
// by every renderer backend as plain arrays.

private fun MeshArrays.vertex(x: Float, y: Float, z: Float, normal: Point3, color: Rgba) = vertex(Point3(x, y, z), normal, color)

private val Up = Point3(0f, 1f, 0f)

/** Weathered stones on the rock props: six slanted faces round a leaning top. */
internal fun TerrainMap.weatheredStones(): SurfaceMesh {
    val slate = Rgba(0.40f, 0.44f, 0.37f)
    val sandstone = Rgba(0.58f, 0.54f, 0.43f)
    val moss = Rgba(0.31f, 0.40f, 0.24f)
    val out = MeshArrays(0)
    for (index in 0 until propCount) {
        val prop = prop(index)
        if (prop.kind != PropKind.Rock) continue
        val cell = cellAt(prop.x, prop.z) ?: continue
        val p = cellPosition(prop.x, prop.z, cell.elevationTwice)
        val scale = prop.scalePercent / 100f
        val stretch = 0.85f + (index % 6) * 0.11f
        val angle = prop.yawDegrees * 0.017453292f
        val count = 6
        val top = Point3(p.x + 0.074f * cos(angle), p.y + 0.14f * scale, p.z + 0.039f * sin(angle))
        val base = List(count) { step ->
            val bearing = angle + step * 1.0471976f
            val radius = (0.12f + ((step * 5 + index) % 7) * 0.014f) * scale
            val x = p.x + cos(bearing) * radius * stretch
            val z = p.z + sin(bearing) * radius
            Point3(x, heightAt(x, z) - 0.013f, z)
        }
        for (step in base.indices) {
            val a = base[step]
            val b = base[(step + 1) % count]
            val normal = ((top - a) cross (b - a)).norm()
            var color = slate.mix(sandstone, ((step * 13 + index * 11) % 17) / 22f)
            if ((step + index * 3) % 13 < 2) color = moss
            out.triangle(out.vertex(a, normal, color), out.vertex(top, normal, color), out.vertex(b, normal, color))
        }
    }
    return out.build()
}

/** Meadow flowers: three stems with round heads on a few meadow cells. */
internal fun TerrainMap.meadowFlowers(): SurfaceMesh = ShapeBuilder().apply {
    for (z in MinCell..MaxCell) for (x in MinCell..MaxCell) {
        val cell = cellAt(x, z) ?: continue
        val pattern = ((x * 107 xor z * 61) * 31) and 127
        if (cell.biome != Biome.Meadow || !cell.walkable || pattern > 10) continue
        val p = cellPosition(x, z, cell.elevationTwice)
        for (blade in 0..2) {
            val offset = blade * 0.038f - 0.038f
            val height = 0.045f + ((pattern + blade * 3) % 7) * 0.009f
            val root = Point3(p.x + offset, heightAt(p.x + offset, p.z + offset), p.z + offset)
            color = Rgba(0.32f, 0.49f, 0.24f)
            cylinder(0.006f, 0.006f, height, Point3(root.x, root.y + height / 2, root.z), 3)
            color = if (pattern % 3 == 0) Rgba(0.86f, 0.62f, 0.20f) else Rgba(0.83f, 0.83f, 0.60f)
            icoSphere(0.019f, Point3(root.x, root.y + height, root.z), 0)
        }
    }
}.build()

/** Soft translucent shade under pines and stones, darkest at the centre. */
internal fun TerrainMap.canopyShade(): SurfaceMesh {
    val out = MeshArrays(0)
    for (index in 0 until propCount) {
        val prop = prop(index)
        if (prop.kind != PropKind.Pine && prop.kind != PropKind.Rock) continue
        val scale = prop.scalePercent / 100f
        val pine = prop.kind == PropKind.Pine
        val x = (prop.x + 0.5f) * CellSize + (if (pine) 0.24f else 0.09f) * scale
        val z = (prop.z + 0.5f) * CellSize + (if (pine) 0.23f else 0.09f) * scale
        val radius = (if (pine) 0.34f else 0.23f) * scale
        val center = out.vertex(x, heightAt(x, z) + 0.013f, z, Up, Rgba(0.054f, 0.096f, 0.089f, if (pine) 0.39f else 0.27f))
        val edge = List(10) { step ->
            val angle = step * 0.62831855f
            val px = x + cos(angle) * radius
            val pz = z + sin(angle) * radius * 1.44f
            out.vertex(px, heightAt(px, pz) + 0.016f, pz, Up, Rgba(0.06f, 0.14f, 0.13f, 0f))
        }
        edge.indices.forEach { step -> out.triangle(center, edge[(step + 1) % edge.size], edge[step]) }
    }
    return out.build()
}

/** Fine grass blades bending in the wind; vertex alpha is how far each point bends (0 at the root). */
internal fun TerrainMap.meadowBlades(): SurfaceMesh {
    val out = MeshArrays(0)
    for (z in MinCell..MaxCell) for (x in MinCell..MaxCell) {
        val cell = cellAt(x, z) ?: continue
        if ((cell.biome != Biome.Meadow && cell.biome != Biome.PineForest) || !cell.walkable) continue
        val pattern = (x * 6971 xor z * 2887) and 255
        if (pattern > 57 || sin(x * 0.29f) * cos(z * 0.35f) < -0.38f) continue
        for (blade in 0..3) {
            val px = (x + 0.32f + blade * 0.13f) * CellSize
            val pz = (z + 0.44f + (blade * 3 % 4) * 0.12f) * CellSize
            val root = heightAt(px, pz) - 0.006f
            val h = 0.049f + ((pattern + blade * 11) % 9) * 0.010f
            val stem = Rgba(0.25f, 0.36f, 0.15f, 0f)
            val base = out.vertex(px, root, pz, Up, stem)
            val edge = out.vertex(px + 0.017f, root, pz + 0.012f, Up, stem)
            val tip = out.vertex(px + 0.024f, root + h, pz - 0.013f, Up,
                if (pattern < 11) Rgba(0.68f, 0.64f, 0.29f) else Rgba(0.41f, 0.57f, 0.24f))
            out.triangle(base, edge, tip)
        }
    }
    return out.build()
}

/**
 * Rivers, lakes and marshes as one surface over the watershed's cells (and a one-cell apron so it meets sloped banks).
 * Vertex colour packs what the water shader needs: rg the downstream direction, b the kind (stream 0, lake 0.45,
 * marsh 1) and a the bed height.
 */
internal fun TerrainMap.inlandWaterSurface(): SurfaceMesh {
    val watershed = checkNotNull(watershed)
    fun WaterKind.paint() = when (this) {
        WaterKind.Stream -> 0f
        WaterKind.Lake -> 0.45f
        WaterKind.Marsh -> 1f
    }
    fun cornerSample(x: Int, z: Int, fallback: WaterCell): FloatArray {
        var count = 0
        var level = 0f
        var flowX = 0f
        var flowZ = 0f
        var paint = 0f
        for (dz in -1..0) for (dx in -1..0) {
            val cx = x + dx
            val cz = z + dz
            val cell = watershed.waterAt(cx, cz) ?: continue
            level += cell.surfaceTwice
            flowX += cell.downstreamX - cx
            flowZ += cell.downstreamZ - cz
            paint += cell.kind.paint()
            count++
        }
        return if (count == 0) floatArrayOf(fallback.surfaceTwice.toFloat(), 0f, 0f, fallback.kind.paint())
        else floatArrayOf(level / count, flowX / count, flowZ / count, paint / count)
    }
    val out = MeshArrays(0)
    for (z in MinCell..MaxCell) for (x in MinCell..MaxCell) {
        var nearby = watershed.waterAt(x, z)
        if (nearby == null) {
            for (dz in -1..1) for (dx in -1..1) {
                if (nearby == null) nearby = watershed.waterAt(x + dx, z + dz)
            }
        }
        val water = nearby ?: continue
        fun point(cx: Int, cz: Int): Int {
            val bed = cornerElevation(cx, cz) * ElevationScale
            val sample = cornerSample(cx, cz, water)
            val level = sample[0] * ElevationScale / 2
            return out.vertex(cx * CellSize, level, cz * CellSize, Up,
                Rgba(0.5f + sample[1] * 0.5f, 0.5f + sample[2] * 0.5f, sample[3], (bed + 1f) * 0.4f))
        }
        val a = point(x, z)
        val b = point(x + 1, z)
        val c = point(x, z + 1)
        val d = point(x + 1, z + 1)
        out.triangle(a, c, d)
        out.triangle(a, d, b)
    }
    return out.build()
}

/** Rushes and reed seed heads framing the open water; [compact] scenes draw fewer. Vertex alpha bends them in the wind. */
internal fun TerrainMap.reedBanks(compact: Boolean): SurfaceMesh {
    val watershed = checkNotNull(watershed)
    val out = MeshArrays(0)
    for (z in MinCell..MaxCell) for (x in MinCell..MaxCell) {
        val water = watershed.waterAt(x, z) ?: continue
        if (water.kind == WaterKind.Stream && water.surfaceTwice > 22) continue
        val pattern = (x * 2099 xor z * 6553) and 255
        val threshold = if (water.kind == WaterKind.Marsh) 100 else 53
        if (pattern > if (compact) threshold * 3 / 4 else threshold) continue
        // Framing the open surface leaves the main channel legible at survey distance.
        if (watershed.waterAt(x - 1, z) != null && watershed.waterAt(x + 1, z) != null &&
            watershed.waterAt(x, z - 1) != null && watershed.waterAt(x, z + 1) != null) continue
        for (blade in 0..4) {
            val px = (x + 0.21f + blade * 0.13f) * CellSize
            val pz = (z + 0.37f + (blade * 3 % 5) * 0.13f) * CellSize
            val stem = (water.surfaceTwice - 3) * ElevationScale / 2
            val root = maxOf(stem, heightAt(px, pz))
            val height = 0.12f + ((pattern + blade * 7) % 15) * 0.012f
            val lean = (blade - 2) * 0.023f
            val foot = Rgba(0.32f, 0.43f, 0.17f, 0f)
            val base = out.vertex(px - 0.012f, stem, pz, Up, foot)
            val edge = out.vertex(px + 0.014f, stem, pz + 0.013f, Up, foot)
            val tip = out.vertex(px + lean, root + height, pz + 0.02f, Up,
                if (blade % 2 == 0) Rgba(0.64f, 0.65f, 0.27f, 0.7f) else Rgba(0.45f, 0.54f, 0.19f, 0.7f))
            out.triangle(base, edge, tip)
            if (blade % 2 == 0) {
                val head = Rgba(0.54f, 0.40f, 0.19f, 0.7f)
                val lower = out.vertex(px + lean * 0.82f - 0.017f, root + height * 0.67f, pz + 0.018f, Up, head)
                val upper = out.vertex(px + lean + 0.014f, root + height * 1.10f, pz + 0.017f, Up, head)
                out.triangle(lower, tip, upper)
            }
        }
    }
    return out.build()
}

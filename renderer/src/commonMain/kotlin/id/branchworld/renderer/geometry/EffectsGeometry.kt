package id.branchworld.renderer.geometry

import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.fire.Succession
import id.branchworld.simulation.projection.EchoKind
import id.branchworld.simulation.projection.FirePose
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.LivingEchoProjection
import id.branchworld.simulation.terrain.Biome
import id.branchworld.simulation.terrain.TerrainMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Fire, comparison echoes, rain and contact shadows as plain arrays, shared by every renderer backend. Everything is
// cosmetic and drawn from the read-only projection.

private val YAxis = Point3(0f, 1f, 0f)
private val XAxis = Point3(1f, 0f, 0f)
private val ZAxis = Point3(0f, 0f, 1f)

/** Fire works on 2×2-cell tiles. */
internal const val FireTileGrid = TerrainMap.Size / 2

private fun TerrainMap.corner(x: Int, z: Int, lift: Float) =
    Point3(x * CellSize, cornerElevation(x, z) * ElevationScale + lift, z * CellSize)

/**
 * Ash darkens burned ground; fresh grass, then scrub, colour it as it comes back; felled ground shows bare earth. The
 * normals carry each tile's edge mask (x: west 1 + east 2, y: north 1 + south 2) for the soft-edged scar shading.
 */
internal fun TerrainMap.fireScars(fire: FirePose): SurfaceMesh {
    val out = MeshArrays(0)
    val burning = fire.burning.map { it.tile }.toSet()
    val tiles = fire.scars.associateBy { it.tile }
    for (scar in fire.scars) {
        val tileX = scar.tile % FireTileGrid
        val tileZ = scar.tile / FireTileGrid
        val lit = scar.tile in burning
        fun border(dx: Int, dz: Int): Int {
            val x = tileX + dx
            val z = tileZ + dz
            if (x !in 0 until FireTileGrid || z !in 0 until FireTileGrid) return 1
            val next = tiles[z * FireTileGrid + x] ?: return 1
            return if (next.stage == scar.stage && next.felled == scar.felled && (next.tile in burning) == lit) 0 else 1
        }
        val edges = Point3((border(-1, 0) + 2 * border(1, 0)).toFloat(), (border(0, -1) + 2 * border(0, 1)).toFloat(), 0f)
        val tint = when {
            lit -> Rgba(0.98f, 0.36f, 0.07f, 0.90f)
            scar.felled && scar.stage == Succession.Ash -> Rgba(0.47f, 0.34f, 0.20f, 0.62f)
            scar.stage == Succession.Ash -> Rgba(0.115f, 0.095f, 0.083f, 0.90f)
            scar.stage == Succession.Meadow -> Rgba(0.56f, 0.67f, 0.26f, 0.70f)
            else -> Rgba(0.32f, 0.46f, 0.26f, 0.46f)
        }
        for (dz in 0..1) for (dx in 0..1) {
            val x = scar.ground.x + dx
            val z = scar.ground.z + dz
            val cell = cellAt(x, z) ?: continue
            if (cell.biome == Biome.Water) continue
            val ia = out.vertex(corner(x, z, 0.007f), edges, tint)
            val ib = out.vertex(corner(x + 1, z, 0.007f), edges, tint)
            val ic = out.vertex(corner(x, z + 1, 0.007f), edges, tint)
            val id = out.vertex(corner(x + 1, z + 1, 0.007f), edges, tint)
            out.triangle(ia, ic, id)
            out.triangle(ia, id, ib)
        }
    }
    return out.build()
}

/**
 * Flame tongues as crossed triangles, and embers. Vertex colour drives the flame shading: r the flicker phase, g 0 at
 * the base and 1 at the tip, b the fire's intensity, a 1 for flames and 0 for embers that rise and fade.
 */
internal fun TerrainMap.fireFront(fire: FirePose, compact: Boolean): SurfaceMesh {
    val out = MeshArrays(0)
    for (tile in fire.burning) {
        val tongues = if (compact) 4 + tile.intensity else 6 + tile.intensity * 2
        repeat(tongues) { index ->
            val seed = tile.tile * 31 + index * 17
            val fx = tile.ground.x + 0.2f + ((seed * 13) % 17) / 10.5f
            val fz = tile.ground.z + 0.2f + ((seed * 7) % 19) / 11.5f
            val wx = fx * CellSize
            val wz = fz * CellSize
            val base = heightAt(wx, wz).coerceAtLeast(0f)
            val height = 0.17f + tile.intensity * 0.055f + (seed % 5) * 0.02f
            val width = 0.045f + (seed % 3) * 0.012f
            val phase = (seed % 97) / 97f
            for (turn in 0..1) {
                val angle = turn * (PI / 2).toFloat() + phase
                val ox = cos(angle) * width
                val oz = sin(angle) * width
                val root = Rgba(phase, 0f, tile.intensity / 4f, 1f)
                val left = out.vertex(Point3(wx - ox, base, wz - oz), YAxis, root)
                val right = out.vertex(Point3(wx + ox, base, wz + oz), YAxis, root)
                val tip = out.vertex(Point3(wx, base + height, wz), YAxis, Rgba(phase, 1f, tile.intensity / 4f, 1f))
                out.triangle(left, right, tip)
            }
        }
        val centerX = (tile.ground.x + 1f) * CellSize
        val centerZ = (tile.ground.z + 1f) * CellSize
        repeat(if (compact) 3 else 5) { index ->
            val seed = tile.tile * 19 + index * 23
            val x = centerX + ((seed % 15) - 7) * 0.012f
            val z = centerZ + ((seed % 13) - 6) * 0.016f
            val base = heightAt(x, z).coerceAtLeast(0f) + 0.18f
            val ember = Rgba((seed % 97) / 97f, 0f, tile.intensity / 4f, 0f)
            for (axis in 0..1) {
                val dx = if (axis == 0) 0.016f else 0f
                val dz = if (axis == 0) 0f else 0.016f
                val a = out.vertex(Point3(x - dx, base, z - dz), YAxis, ember)
                val b = out.vertex(Point3(x + dx, base, z + dz), YAxis, ember)
                val c = out.vertex(Point3(x, base + 0.026f, z), YAxis, ember)
                out.triangle(a, b, c)
            }
        }
    }
    return out.build()
}

/** Stumps where pines fell; burned trunks lie beside theirs, felled ones were hauled away for timber. */
internal fun TerrainMap.fallenPines(props: Set<Int>, felledTiles: Set<Int>): SurfaceMesh {
    val out = MeshArrays(0)
    for (index in props.sorted()) {
        val prop = prop(index)
        val cell = cellAt(prop.x, prop.z) ?: continue
        val center = cellPosition(prop.x, prop.z, cell.elevationTwice)
        val y = heightAt(center.x, center.z)
        val tileX = (prop.x - TerrainMap.MinCell) / 2
        val tileZ = (prop.z - TerrainMap.MinCell) / 2
        val felled = tileZ * (TerrainMap.Size / 2) + tileX in felledTiles
        val bark = if (felled) Rgba(0.36f, 0.24f, 0.14f) else Rgba(0.10f, 0.08f, 0.07f)
        val top = if (felled) Rgba(0.78f, 0.62f, 0.40f) else Rgba(0.16f, 0.11f, 0.08f)
        val radius = 0.020f + (prop.scalePercent % 7) * 0.0015f
        val height = if (felled) 0.030f else 0.055f
        val sides = 6
        val ring = List(sides) { side ->
            val angle = (side * 2 * PI / sides).toFloat() + prop.yawDegrees / 57.3f
            cos(angle) * radius to sin(angle) * radius
        }
        for (side in 0 until sides) {
            val (ax, az) = ring[side]
            val (bx, bz) = ring[(side + 1) % sides]
            val normal = Point3((ax + bx) / 2, 0f, (az + bz) / 2).norm()
            val a = out.vertex(Point3(center.x + ax, y - 0.01f, center.z + az), normal, bark)
            val b = out.vertex(Point3(center.x + bx, y - 0.01f, center.z + bz), normal, bark)
            val c = out.vertex(Point3(center.x + bx * 0.86f, y + height, center.z + bz * 0.86f), normal, bark)
            val d = out.vertex(Point3(center.x + ax * 0.86f, y + height, center.z + az * 0.86f), normal, bark)
            out.triangle(a, b, c)
            out.triangle(a, c, d)
            val hub = out.vertex(Point3(center.x, y + height, center.z), YAxis, top)
            val e = out.vertex(Point3(center.x + ax * 0.86f, y + height, center.z + az * 0.86f), YAxis, top)
            val f = out.vertex(Point3(center.x + bx * 0.86f, y + height, center.z + bz * 0.86f), YAxis, top)
            out.triangle(hub, f, e)
        }
        if (!felled) {
            val angle = prop.yawDegrees / 57.3f
            val dx = cos(angle) * 0.13f
            val dz = sin(angle) * 0.13f
            val px = -sin(angle) * 0.011f
            val pz = cos(angle) * 0.011f
            val a = out.vertex(Point3(center.x + px, y + 0.012f, center.z + pz), YAxis, bark)
            val b = out.vertex(Point3(center.x - px, y + 0.012f, center.z - pz), YAxis, bark)
            val c = out.vertex(Point3(center.x + dx - px, heightAt(center.x + dx, center.z + dz) + 0.010f, center.z + dz - pz), YAxis, bark)
            val d = out.vertex(Point3(center.x + dx + px, heightAt(center.x + dx, center.z + dz) + 0.010f, center.z + dz + pz), YAxis, bark)
            out.triangle(a, b, c)
            out.triangle(a, c, d)
        }
    }
    return out.build()
}

/** A forked bolt from the cloud base down to [point]. */
internal fun TerrainMap.lightningBolt(point: GroundPoint): SurfaceMesh {
    val out = MeshArrays(0)
    val p = cellPosition(point.x, point.z, point.elevationTwice)
    val ground = heightAt(p.x, p.z).coerceAtLeast(0f)
    var x = p.x + 0.18f
    var z = p.z - 0.10f
    var y = ground + 1.45f
    val steps = 7
    val glow = Rgba(1f, 0.97f, 0.84f, 0.95f)
    for (step in 1..steps) {
        val nx = p.x + if (step == steps) 0f else ((step * 37 + point.x * 11).mod(9) - 4) * 0.035f
        val nz = p.z + if (step == steps) 0f else ((step * 23 + point.z * 7).mod(9) - 4) * 0.028f
        val ny = ground + 1.45f * (1f - step / steps.toFloat())
        val w = 0.016f * (1.2f - step / steps.toFloat())
        val a = out.vertex(Point3(x - w, y, z), ZAxis, glow)
        val b = out.vertex(Point3(x + w, y, z), ZAxis, glow)
        val c = out.vertex(Point3(nx + w, ny, nz), ZAxis, glow)
        val d = out.vertex(Point3(nx - w, ny, nz), ZAxis, glow)
        out.triangle(a, b, c)
        out.triangle(a, c, d)
        val e = out.vertex(Point3(x, y, z - w), XAxis, glow)
        val f = out.vertex(Point3(x, y, z + w), XAxis, glow)
        val g = out.vertex(Point3(nx, ny, nz + w), XAxis, glow)
        val h = out.vertex(Point3(nx, ny, nz - w), XAxis, glow)
        out.triangle(e, f, g)
        out.triangle(e, g, h)
        x = nx; z = nz; y = ny
    }
    return out.build()
}

/** Smoke puffs over each burning tile; vertex colour r is the puff's phase and g its density (for the rising shading). */
internal fun TerrainMap.smokePuffs(fire: FirePose, compact: Boolean): SurfaceMesh = ShapeBuilder().apply {
    for ((order, tile) in fire.burning.withIndex()) {
        if (compact && order % 2 == 1) continue
        repeat(if (compact) 3 else 4) { index ->
            val seed = tile.tile * 19 + index * 23
            val wx = (tile.ground.x + 1f) * CellSize
            val wz = (tile.ground.z + 1f) * CellSize
            val base = heightAt(wx, wz).coerceAtLeast(0f) + 0.30f
            color = Rgba((seed % 89) / 89f, 0.6f + tile.intensity / 10f, 0f, 1f)
            icoSphere(0.11f + (seed % 4) * 0.03f, Point3(wx + (index % 2) * 0.05f, base, wz), 1)
        }
    }
}.build()

/** Heavy, rainless cloud gathering over the storm's path. */
internal fun stormClouds(points: List<GroundPoint>, compact: Boolean): SurfaceMesh = ShapeBuilder().apply {
    val first = points.first().let { cellPosition(it.x, it.z, it.elevationTwice) }
    val last = points.last().let { cellPosition(it.x, it.z, it.elevationTwice) }
    val crest = maxOf(first.y, last.y, 0f) + 1.34f
    val count = if (points.size == 1) 7 else if (compact) 12 else 16
    for (index in 0 until count) {
        val blend = index.toFloat() / (count - 1)
        val roll = sin(blend * PI).toFloat()
        val x = first.x + (last.x - first.x) * blend + (blend - 0.5f) * 0.72f
        val z = first.z + (last.z - first.z) * blend + sin(index * 2.42f) * 0.19f
        val base = crest + sin(index * 1.91f) * 0.07f
        color = Rgba(0.24f, 0.27f, 0.32f)
        icoSphere(0.22f + roll * 0.07f, Point3(x, base, z + 0.11f), 1)
        if (index % 2 == 0) {
            color = Rgba(0.34f, 0.36f, 0.41f)
            icoSphere(0.25f + roll * 0.12f + (index % 5) * 0.012f, Point3(x - 0.07f, base + 0.15f, z - 0.08f), 1)
        }
    }
}.build()

/** The rain veil: thin streaks in four stacked layers, so moving it down by one layer loops cleanly. */
internal fun rainVeil(compact: Boolean): SurfaceMesh {
    val out = MeshArrays(0)
    val streak = Rgba(0.71f, 0.83f, 0.95f, 0.22f)
    repeat(if (compact) 136 else 196) { index ->
        val x = ((index * 83) % 521) / 43f - 6f
        val z = ((index * 197) % 491) / 41f - 6f
        val height = ((index * 13) % 37) / 37f * 1.7f
        for (layer in 0..3) {
            val y = height + layer * 1.7f
            val a = out.vertex(Point3(x, y, z), ZAxis, streak)
            val b = out.vertex(Point3(x + 0.014f, y, z), ZAxis, streak)
            val c = out.vertex(Point3(x + 0.047f, y + 0.19f, z + 0.018f), ZAxis, streak)
            out.triangle(a, b, c)
        }
    }
    return out.build()
}

/** How far the rain veil has fallen at [seconds]: one layer loops every 1.7 units. */
internal fun rainDrop(seconds: Float, storm: Boolean) = -(seconds * if (storm) 5.6f else 3.6f) % 1.7f

/** A soft shadow under each animal, tilted to the ground it stands on. */
internal fun TerrainMap.contactShadows(animals: List<Triple<GroundPoint, Species, Boolean>>, wildlife: Boolean, wildlifeScale: Float): SurfaceMesh {
    val out = MeshArrays(0)
    for ((ground, species, juvenile) in animals) {
        val radius = when (species) {
            Species.Rabbit -> 0.16f
            Species.Deer -> 0.21f
            Species.Fox -> 0.19f
            Species.Wolf -> 0.23f
        } * (if (wildlife) wildlifeScale else 1f) * if (juvenile) 0.73f else 1f
        val c = cellPosition(ground.x, ground.z, ground.elevationTwice)
        val x = c.x + 0.036f
        val z = c.z + 0.036f
        val slopeX = (heightAt(x + radius, z) - heightAt(x - radius, z)) / (2f * radius)
        val slopeZ = (heightAt(x, z + radius) - heightAt(x, z - radius)) / (2f * radius)
        val y = heightAt(x, z) + 0.022f
        fun at(dx: Float, dz: Float) = Point3(x + dx * radius, y + (slopeX * dx + slopeZ * dz) * radius, z + dz * radius)
        val center = out.vertex(at(0.12f, 0.05f), YAxis, Rgba(0.053f, 0.085f, 0.075f, 0.38f))
        val ring = List(12) { i ->
            val angle = i * 0.523599f
            out.vertex(at(cos(angle), sin(angle)), YAxis, Rgba(0.053f, 0.085f, 0.075f, 0f))
        }
        ring.indices.forEach { i -> out.triangle(center, ring[(i + 1) % ring.size], ring[i]) }
    }
    return out.build()
}

/**
 * The comparison echo: rings, ribbons and pale ghost pines and houses where the two histories shown side by side
 * differ, tinted by what differs (water, fire, woods, homes).
 */
internal fun TerrainMap.livingEcho(echo: LivingEchoProjection): SurfaceMesh {
    val out = MeshArrays(0)
    var color = Rgba(1f, 1f, 1f)
    fun vertex(p: Point3) = out.vertex(p, YAxis, color)
    fun ribbon(from: Point3, to: Point3, width: Float) {
        val a = vertex(Point3(from.x - width, from.y, from.z - width))
        val b = vertex(Point3(from.x + width, from.y, from.z + width))
        val c = vertex(Point3(to.x - width, to.y, to.z - width))
        val d = vertex(Point3(to.x + width, to.y, to.z + width))
        out.triangle(a, b, c)
        out.triangle(b, d, c)
        // Crossing ribbons remain legible as the player orbits.
        val e = vertex(Point3(from.x - width, from.y, from.z + width))
        val f = vertex(Point3(from.x + width, from.y, from.z - width))
        val g = vertex(Point3(to.x - width, to.y, to.z + width))
        val h = vertex(Point3(to.x + width, to.y, to.z - width))
        out.triangle(e, f, g)
        out.triangle(f, h, g)
    }
    for (site in echo.sites) {
        val point = site.ground
        val cell = cellPosition(point.x, point.z, point.elevationTwice)
        val center = if (site.kind == EchoKind.Burned) Point3(cell.x + CellSize / 2, cell.y, cell.z + CellSize / 2) else cell
        val alternate = site.other > site.here
        val aqua = site.kind == EchoKind.Bank || site.kind == EchoKind.WetGround
        val tint = when {
            aqua && alternate -> Rgba(0.59f, 0.96f, 1f, 0.92f)
            aqua -> Rgba(0.32f, 0.82f, 0.86f, 0.84f)
            site.kind == EchoKind.Burned -> Rgba(1f, 0.63f, 0.43f, 0.91f)
            site.kind == EchoKind.Stand -> Rgba(0.76f, 0.92f, 0.66f, 0.93f)
            site.kind == EchoKind.Grove || site.kind == EchoKind.Clearing -> Rgba(0.80f, 0.95f, 0.63f, 0.93f)
            site.kind == EchoKind.Household -> Rgba(1f, 0.90f, 0.70f, 0.92f)
            else -> Rgba(1f, 0.82f, 0.41f, 0.92f)
        }
        fun Rgba.withAlpha(alpha: Float) = copy(a = alpha)
        fun ground(dx: Float, dz: Float): Point3 {
            val x = center.x + dx
            val z = center.z + dz
            return Point3(x, heightAt(x, z).coerceAtLeast(0f) + 0.032f, z)
        }
        fun contour(radius: Float, sides: Int, dashed: Boolean, width: Float, angleOffset: Double = 0.0) {
            val edge = List(sides) { index ->
                val angle = index * (PI * 2 / sides) + angleOffset
                val x = cos(angle).toFloat()
                val z = sin(angle).toFloat()
                vertex(ground(x * radius, z * radius)) to vertex(ground(x * (radius - width), z * (radius - width)))
            }
            for (index in edge.indices) {
                if (dashed && index % 3 == 2) continue
                val (a, b) = edge[index]
                val (c, d) = edge[(index + 1) % sides]
                out.triangle(a, c, b)
                out.triangle(c, d, b)
            }
        }
        fun ghostPine(height: Float, crown: Float) {
            val base = ground(0f, 0f)
            fun bough(low: Float, high: Float, breadth: Float) {
                val tip = Point3(base.x, base.y + height * high, base.z)
                val rim = List(8) { index ->
                    val angle = index * PI / 4
                    Point3(base.x + cos(angle).toFloat() * crown * breadth, base.y + height * low, base.z + sin(angle).toFloat() * crown * breadth)
                }
                for (index in rim.indices) {
                    val edge = rim[index]
                    val next = rim[(index + 1) % rim.size]
                    color = tint.withAlpha(if (index in 1..4) 0.37f else 0.24f)
                    out.triangle(vertex(tip), vertex(edge), vertex(next))
                    color = tint.withAlpha(0.72f)
                    ribbon(edge, next, 0.008f)
                }
            }
            bough(0.35f, 0.81f, 1f)
            bough(0.63f, 1f, 0.68f)
            color = tint.withAlpha(0.83f)
            ribbon(base, Point3(base.x, base.y + height * 0.43f, base.z), 0.012f)
        }
        fun ghostHouse(half: Float, wall: Float, roof: Float) {
            val base = ground(0f, 0f)
            val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (x, z) -> Point3(base.x + x * half, base.y, base.z + z * half) }
            val tops = corners.map { Point3(it.x, it.y + wall, it.z) }
            for (index in corners.indices) {
                val next = (index + 1) % corners.size
                color = tint.withAlpha(if (index % 2 == 0) 0.30f else 0.20f)
                val a = vertex(corners[index])
                val b = vertex(corners[next])
                val c = vertex(tops[next])
                val d = vertex(tops[index])
                out.triangle(a, b, c)
                out.triangle(a, c, d)
                color = tint.withAlpha(0.78f)
                ribbon(corners[index], tops[index], 0.006f)
                ribbon(tops[index], tops[next], 0.006f)
            }
            val ridgeA = Point3(base.x - half, base.y + wall + roof, base.z)
            val ridgeB = Point3(base.x + half, base.y + wall + roof, base.z)
            color = tint.withAlpha(0.42f)
            for ((edgeA, edgeB) in listOf(tops[0] to tops[1], tops[3] to tops[2])) {
                out.triangle(vertex(edgeA), vertex(edgeB), vertex(ridgeB))
                out.triangle(vertex(edgeA), vertex(ridgeB), vertex(ridgeA))
            }
            color = tint.withAlpha(0.85f)
            ribbon(ridgeA, ridgeB, 0.007f)
        }
        color = tint
        when (site.kind) {
            EchoKind.Grove -> {
                contour(0.125f, 24, alternate, 0.018f)
                if (alternate) {
                    val rooted = site.other == 2
                    ghostPine(if (rooted) 0.63f else 0.32f, if (rooted) 0.19f else 0.10f)
                }
            }
            EchoKind.Bank -> {
                contour(0.27f, 30, alternate, 0.023f)
                color = tint.withAlpha(0.61f)
                contour(0.19f, 24, alternate, 0.014f)
            }
            EchoKind.Field -> {
                contour(0.27f, 4, false, 0.022f, PI / 4)
                for (row in -1..1) {
                    val offset = row * 0.086f
                    color = tint.withAlpha(if (alternate) 0.87f else 0.69f)
                    ribbon(ground(-0.13f, offset), ground(0.13f, offset), 0.009f)
                }
            }
            EchoKind.Homes -> {
                contour(0.21f, 4, alternate || site.other < 0, 0.024f, PI / 4)
                if (alternate && site.other >= 2) ghostHouse(0.10f, if (site.other == 3) 0.20f else 0.15f, 0.08f)
                if (site.other < 0) {
                    color = Rgba(1f, 0.62f, 0.45f, 0.90f)
                    ribbon(ground(-0.10f, -0.10f), ground(0.10f, 0.10f), 0.011f)
                    ribbon(ground(-0.10f, 0.10f), ground(0.10f, -0.10f), 0.011f)
                }
            }
            EchoKind.Well -> {
                contour(0.15f, 16, alternate, 0.02f)
                if (alternate && site.other >= 2) ghostHouse(0.07f, 0.10f, 0.06f)
            }
            EchoKind.Clearing -> {
                contour(0.22f, 6, alternate, 0.02f)
                if (!alternate) ghostPine(0.55f, 0.17f)
            }
            EchoKind.Household -> {
                contour(0.08f, 12, alternate, 0.014f)
                if (alternate) {
                    val base = ground(0.12f, 0.10f)
                    color = tint.withAlpha(0.9f)
                    ribbon(base, Point3(base.x, base.y + 0.17f, base.z), 0.01f)
                    color = Rgba(1f, 0.74f, 0.38f, 0.75f)
                    ribbon(Point3(base.x, base.y + 0.17f, base.z), Point3(base.x, base.y + 0.20f, base.z), 0.022f)
                }
            }
            EchoKind.WetGround -> {
                color = tint.withAlpha(if (alternate) 0.63f else 0.52f)
                contour(0.12f, 18, alternate, 0.015f)
            }
            EchoKind.Burned -> {
                color = tint.withAlpha(if (site.here == 0) 0.86f else 0.67f)
                contour(0.157f, 12, site.here == 0, 0.013f, PI / 12)
                if (site.here == 0) ribbon(ground(-0.06f, 0.06f), ground(0.06f, -0.06f), 0.008f)
            }
            EchoKind.Stand -> {
                contour(0.088f, 12, alternate, 0.012f)
                if (alternate && site.other >= 2) {
                    val mature = site.other == 3
                    ghostPine(if (mature) 0.55f else 0.24f, if (mature) 0.13f else 0.07f)
                }
            }
        }
    }
    return out.build()
}

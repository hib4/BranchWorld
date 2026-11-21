package id.branchworld.renderer

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.releaseDelayed
import id.branchworld.game.simulation.EchoKind
import id.branchworld.game.simulation.LivingEchoProjection
import id.branchworld.game.terrain.TerrainMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A single depth-tested layer follows the visible history without recreating the island. */
internal class LivingEcho(private val scene: Scene, private val terrain: TerrainMap) {
    private var drawn: LivingEchoProjection? = null
    private var mesh: Node? = null
    private var previous: Pair<LivingEchoProjection, Node>? = null

    fun show(echo: LivingEchoProjection?) {
        if (echo == null || echo.sites.isEmpty()) {
            mesh?.isVisible = false
            return
        }
        if (drawn != echo) {
            mesh?.isVisible = false
            val cached = previous
            previous = drawn?.let { current -> mesh?.let { current to it } }
            mesh = if (cached?.first == echo) cached.second else {
                cached?.second?.let {
                    scene.removeNode(it)
                    it.releaseDelayed(2)
                }
                scene.addEcho(echo)
            }
            drawn = echo
        }
        mesh?.isVisible = true
    }

    private fun Scene.addEcho(echo: LivingEchoProjection) = addColorMesh("Living Echo") {
        generate {
            fun ribbon(from: Vec3f, to: Vec3f, width: Float) {
                val a = vertex(Vec3f(from.x - width, from.y, from.z - width), Vec3f.Y_AXIS)
                val b = vertex(Vec3f(from.x + width, from.y, from.z + width), Vec3f.Y_AXIS)
                val c = vertex(Vec3f(to.x - width, to.y, to.z - width), Vec3f.Y_AXIS)
                val d = vertex(Vec3f(to.x + width, to.y, to.z + width), Vec3f.Y_AXIS)
                addTriIndices(a, b, c)
                addTriIndices(b, d, c)
                // Crossing ribbons remain legible as the player orbits.
                val e = vertex(Vec3f(from.x - width, from.y, from.z + width), Vec3f.Y_AXIS)
                val f = vertex(Vec3f(from.x + width, from.y, from.z - width), Vec3f.Y_AXIS)
                val g = vertex(Vec3f(to.x - width, to.y, to.z + width), Vec3f.Y_AXIS)
                val h = vertex(Vec3f(to.x + width, to.y, to.z - width), Vec3f.Y_AXIS)
                addTriIndices(e, f, g)
                addTriIndices(f, h, g)
            }
            for (site in echo.sites) {
                val point = site.ground
                val center = cellPosition(point.x, point.z, point.elevationTwice)
                val alternate = site.other > site.here
                val aqua = site.kind == EchoKind.Bank || site.kind == EchoKind.WetGround
                val tint = when {
                    aqua && alternate -> Color(0.59f, 0.96f, 1f, 0.92f)
                    aqua -> Color(0.32f, 0.82f, 0.86f, 0.84f)
                    site.kind == EchoKind.Grove || site.kind == EchoKind.Clearing -> Color(0.80f, 0.95f, 0.63f, 0.93f)
                    site.kind == EchoKind.Household -> Color(1f, 0.90f, 0.70f, 0.92f)
                    else -> Color(1f, 0.82f, 0.41f, 0.92f)
                }
                fun ground(dx: Float, dz: Float): Vec3f {
                    val x = center.x + dx
                    val z = center.z + dz
                    return Vec3f(x, terrain.heightAt(x, z).coerceAtLeast(0f) + 0.032f, z)
                }
                fun contour(radius: Float, sides: Int, dashed: Boolean, width: Float, angleOffset: Double = 0.0) {
                    val edge = List(sides) { index ->
                        val angle = index * (PI * 2 / sides) + angleOffset
                        val x = cos(angle).toFloat()
                        val z = sin(angle).toFloat()
                        vertex(ground(x * radius, z * radius), Vec3f.Y_AXIS) to
                            vertex(ground(x * (radius - width), z * (radius - width)), Vec3f.Y_AXIS)
                    }
                    for (index in edge.indices) {
                        if (dashed && index % 3 == 2) continue
                        val (a, b) = edge[index]
                        val (c, d) = edge[(index + 1) % sides]
                        addTriIndices(a, c, b)
                        addTriIndices(c, d, b)
                    }
                }
                fun ghostPine(height: Float, crown: Float) {
                    val base = ground(0f, 0f)
                    fun bough(low: Float, high: Float, breadth: Float) {
                        val tip = Vec3f(base.x, base.y + height * high, base.z)
                        val rim = List(8) { index ->
                            val angle = index * PI / 4
                            Vec3f(base.x + cos(angle).toFloat() * crown * breadth, base.y + height * low,
                                base.z + sin(angle).toFloat() * crown * breadth)
                        }
                        for (index in rim.indices) {
                            val edge = rim[index]
                            val next = rim[(index + 1) % rim.size]
                            color = tint.withAlpha(if (index in 1..4) 0.37f else 0.24f)
                            addTriIndices(vertex(tip, Vec3f.Y_AXIS), vertex(edge, Vec3f.Y_AXIS), vertex(next, Vec3f.Y_AXIS))
                            color = tint.withAlpha(0.72f)
                            ribbon(edge, next, 0.008f)
                        }
                    }
                    bough(0.35f, 0.81f, 1f)
                    bough(0.63f, 1f, 0.68f)
                    color = tint.withAlpha(0.83f)
                    ribbon(base, Vec3f(base.x, base.y + height * 0.43f, base.z), 0.012f)
                }
                /** A pale house: translucent walls, a gabled roof and bright edges, standing only in the other history. */
                fun ghostHouse(half: Float, wall: Float, roof: Float) {
                    val base = ground(0f, 0f)
                    val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (x, z) ->
                        Vec3f(base.x + x * half, base.y, base.z + z * half)
                    }
                    val tops = corners.map { Vec3f(it.x, it.y + wall, it.z) }
                    for (index in corners.indices) {
                        val next = (index + 1) % corners.size
                        color = tint.withAlpha(if (index % 2 == 0) 0.30f else 0.20f)
                        val a = vertex(corners[index], Vec3f.Y_AXIS)
                        val b = vertex(corners[next], Vec3f.Y_AXIS)
                        val c = vertex(tops[next], Vec3f.Y_AXIS)
                        val d = vertex(tops[index], Vec3f.Y_AXIS)
                        addTriIndices(a, b, c)
                        addTriIndices(a, c, d)
                        color = tint.withAlpha(0.78f)
                        ribbon(corners[index], tops[index], 0.006f)
                        ribbon(tops[index], tops[next], 0.006f)
                    }
                    val ridgeA = Vec3f(base.x - half, base.y + wall + roof, base.z)
                    val ridgeB = Vec3f(base.x + half, base.y + wall + roof, base.z)
                    color = tint.withAlpha(0.42f)
                    for ((edgeA, edgeB) in listOf(tops[0] to tops[1], tops[3] to tops[2])) {
                        addTriIndices(vertex(edgeA, Vec3f.Y_AXIS), vertex(edgeB, Vec3f.Y_AXIS), vertex(ridgeB, Vec3f.Y_AXIS))
                        addTriIndices(vertex(edgeA, Vec3f.Y_AXIS), vertex(ridgeB, Vec3f.Y_AXIS), vertex(ridgeA, Vec3f.Y_AXIS))
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
                        contour(0.21f, 4, alternate, 0.024f, PI / 4)
                        // Only the other history has this home (or its larger form): draw it as a pale house.
                        if (alternate && site.other >= 2) ghostHouse(0.10f, if (site.other == 3) 0.20f else 0.15f, 0.08f)
                    }
                    EchoKind.Well -> {
                        contour(0.15f, 16, alternate, 0.02f)
                        if (alternate && site.other >= 2) ghostHouse(0.07f, 0.10f, 0.06f)
                    }
                    EchoKind.Clearing -> {
                        contour(0.22f, 6, alternate, 0.02f)
                        // Woods that still stand in the other history.
                        if (!alternate) ghostPine(0.55f, 0.17f)
                    }
                    EchoKind.Household -> {
                        contour(0.08f, 12, alternate, 0.014f)
                        if (alternate) {
                            val base = ground(0.12f, 0.10f)
                            color = tint.withAlpha(0.9f)
                            ribbon(base, Vec3f(base.x, base.y + 0.17f, base.z), 0.01f)
                            color = Color(1f, 0.74f, 0.38f, 0.75f)
                            ribbon(Vec3f(base.x, base.y + 0.17f, base.z), Vec3f(base.x, base.y + 0.20f, base.z), 0.022f)
                        }
                    }
                    EchoKind.WetGround -> {
                        color = tint.withAlpha(if (alternate) 0.63f else 0.52f)
                        contour(0.12f, 18, alternate, 0.015f)
                    }
                }
            }
        }
        shader = translucentSurface()
    }
}

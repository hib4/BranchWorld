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
                    site.kind == EchoKind.Grove -> Color(0.80f, 0.95f, 0.63f, 0.93f)
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
                color = tint
                when (site.kind) {
                    EchoKind.Grove -> {
                        contour(0.125f, 24, alternate, 0.018f)
                        if (alternate) {
                            val base = ground(0f, 0f)
                            val rooted = site.other == 2
                            val height = if (rooted) 0.63f else 0.32f
                            val crown = if (rooted) 0.19f else 0.10f
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
                    EchoKind.Homes -> contour(0.24f, 4, false, 0.026f, PI / 4)
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

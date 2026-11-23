package id.branchworld.renderer.scene.life

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.releaseDelayed
import id.branchworld.renderer.scene.CellSize
import id.branchworld.renderer.scene.cellPosition
import id.branchworld.renderer.scene.heightAt
import id.branchworld.renderer.scene.translucentSurface
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.RangePose
import id.branchworld.simulation.projection.SettlementPose
import id.branchworld.simulation.terrain.TerrainMap
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Rules v10 presentation: the herd's worn trail between its seasonal ranges and the edges of the Keeper's refuges.
 * The trail is the authoritative route; where families have settled the pasture its end fades and turns rust.
 */
internal class HerdTrail(private val scene: Scene, private val terrain: TerrainMap, private val route: List<GroundPoint>) {
    private data class Look(val refuges: List<Int>, val settled: Boolean, val bound: Int)
    private var drawn: Look? = null
    private var mesh: Node? = null

    fun show(ranges: RangePose?, settlements: List<SettlementPose>) {
        if (ranges == null || route.size < 2) {
            mesh?.isVisible = false
            return
        }
        val pasture = route.last()
        val settled = settlements.any { site ->
            site.village?.structures.orEmpty().any { abs(it.ground.x - pasture.x) + abs(it.ground.z - pasture.z) <= 8 }
        }
        val look = Look(ranges.refuges, settled, ranges.herdBound)
        if (look != drawn) {
            mesh?.let {
                scene.removeNode(it)
                it.releaseDelayed(2)
            }
            mesh = scene.addTrail(look)
            drawn = look
        }
        mesh?.isVisible = true
    }

    private fun ground(x: Float, z: Float) = Vec3f(x, terrain.heightAt(x, z).coerceAtLeast(0f) + 0.026f, z)

    private fun center(point: GroundPoint) = cellPosition(point.x, point.z, point.elevationTwice).let { Vec3f(it.x, 0f, it.z) }

    private fun Scene.addTrail(look: Look) = addColorMesh("Herd trail") {
        generate {
            fun band(from: Vec3f, to: Vec3f, width: Float) {
                val dx = to.x - from.x
                val dz = to.z - from.z
                val length = hypot(dx, dz).coerceAtLeast(0.0001f)
                val nx = -dz / length * width
                val nz = dx / length * width
                val a = vertex(ground(from.x + nx, from.z + nz), Vec3f.Y_AXIS)
                val b = vertex(ground(from.x - nx, from.z - nz), Vec3f.Y_AXIS)
                val c = vertex(ground(to.x + nx, to.z + nz), Vec3f.Y_AXIS)
                val d = vertex(ground(to.x - nx, to.z - nz), Vec3f.Y_AXIS)
                addTriIndices(a, b, c)
                addTriIndices(b, d, c)
            }
            val worn = Color(0.78f, 0.62f, 0.38f, 0.72f)
            val faded = Color(0.66f, 0.38f, 0.26f, 0.42f)
            val points = route.map(::center)
            val settledFrom = if (look.settled) (points.size - 9).coerceAtLeast(0) else points.size
            for (index in 0 until points.lastIndex) {
                val from = points[index]
                val to = points[index + 1]
                val lost = index >= settledFrom
                if (lost && index % 2 == 1) continue
                color = if (lost) faded else worn
                // Draped in short steps so the trail follows banks and slopes.
                for (step in 0 until 3) {
                    val t0 = step / 3f
                    val t1 = (step + 1) / 3f
                    band(Vec3f(from.x + (to.x - from.x) * t0, 0f, from.z + (to.z - from.z) * t0),
                        Vec3f(from.x + (to.x - from.x) * t1, 0f, from.z + (to.z - from.z) * t1), 0.03f)
                }
                // Hoof-worn chevrons every few cells point the way the herd is going this season.
                if (index % 4 == 2 && !lost) {
                    val forward = look.bound != 2
                    val tip = if (forward) to else from
                    val tail = if (forward) from else to
                    val dx = (tip.x - tail.x) * 0.45f
                    val dz = (tip.z - tail.z) * 0.45f
                    val mid = Vec3f((from.x + to.x) / 2f, 0f, (from.z + to.z) / 2f)
                    color = Color(0.94f, 0.80f, 0.50f, 0.72f)
                    band(Vec3f(mid.x - dx - dz * 0.8f, 0f, mid.z - dz + dx * 0.8f), Vec3f(mid.x + dx, 0f, mid.z + dz), 0.012f)
                    band(Vec3f(mid.x - dx + dz * 0.8f, 0f, mid.z - dz - dx * 0.8f), Vec3f(mid.x + dx, 0f, mid.z + dz), 0.012f)
                }
            }
            // A refuge's edge: a dashed green-gold line where the region meets land, with marker posts at its corners.
            for (region in look.refuges) {
                val left = TerrainMap.MinCell + region % 4 * 16
                val top = TerrainMap.MinCell + region / 4 * 16
                val corners = listOf(left to top, left + 16 to top, left + 16 to top + 16, left to top + 16)
                for ((index, corner) in corners.withIndex()) {
                    val next = corners[(index + 1) % corners.size]
                    for (step in 0 until 16) {
                        if (step % 2 == 1) continue
                        val x0 = (corner.first + (next.first - corner.first) * step / 16f) * CellSize
                        val z0 = (corner.second + (next.second - corner.second) * step / 16f) * CellSize
                        val x1 = (corner.first + (next.first - corner.first) * (step + 1) / 16f) * CellSize
                        val z1 = (corner.second + (next.second - corner.second) * (step + 1) / 16f) * CellSize
                        if (terrain.heightAt(x0, z0) <= 0f && terrain.heightAt(x1, z1) <= 0f) continue
                        color = Color(0.80f, 0.94f, 0.58f, 0.80f)
                        band(Vec3f(x0, 0f, z0), Vec3f(x1, 0f, z1), 0.016f)
                    }
                    val post = ground(corner.first * CellSize, corner.second * CellSize)
                    if (post.y > 0.03f) {
                        color = Color(0.95f, 0.84f, 0.55f, 0.88f)
                        for (side in listOf(Vec3f(0.012f, 0f, 0f), Vec3f(0f, 0f, 0.012f))) {
                            val a = vertex(Vec3f(post.x - side.x, post.y, post.z - side.z), Vec3f.Y_AXIS)
                            val b = vertex(Vec3f(post.x + side.x, post.y, post.z + side.z), Vec3f.Y_AXIS)
                            val c = vertex(Vec3f(post.x - side.x, post.y + 0.16f, post.z - side.z), Vec3f.Y_AXIS)
                            val d = vertex(Vec3f(post.x + side.x, post.y + 0.16f, post.z + side.z), Vec3f.Y_AXIS)
                            addTriIndices(a, b, c)
                            addTriIndices(b, d, c)
                        }
                    }
                }
            }
        }
        shader = translucentSurface()
    }
}

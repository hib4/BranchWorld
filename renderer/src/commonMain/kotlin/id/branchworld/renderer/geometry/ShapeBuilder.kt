package id.branchworld.renderer.geometry

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds small coloured shapes into plain [SurfaceMesh] arrays: triangles, cylinders and low-poly spheres. Used by
 * renderer backends without a mesh builder of their own (iOS) for markers, plants and burrows.
 */
internal class ShapeBuilder {
    private val positions = ArrayList<Float>()
    private val normals = ArrayList<Float>()
    private val colors = ArrayList<Float>()
    private val indices = ArrayList<Int>()
    private var count = 0
    var color = Rgba(1f, 1f, 1f)

    fun vertex(p: Point3, n: Point3): Int {
        positions += p.x; positions += p.y; positions += p.z
        normals += n.x; normals += n.y; normals += n.z
        colors += color.r; colors += color.g; colors += color.b; colors += color.a
        return count++
    }

    fun triangle(a: Int, b: Int, c: Int) {
        indices += a; indices += b; indices += c
    }

    /** Both windings, for thin shapes seen from either side. */
    fun doubleSided(a: Int, b: Int, c: Int) {
        triangle(a, b, c)
        triangle(a, c, b)
    }

    /** A capped frustum standing on [origin]'s centre line, [origin] being its middle. */
    fun cylinder(bottomRadius: Float, topRadius: Float, height: Float, origin: Point3, steps: Int) {
        val bottom = origin.y - height / 2
        val top = origin.y + height / 2
        val slope = (bottomRadius - topRadius) / height
        val ring = List(steps + 1) { i ->
            val angle = (i * 2.0 * PI / steps).toFloat()
            val c = cos(angle)
            val s = sin(angle)
            val normal = Point3(c, slope, s).norm()
            vertex(Point3(origin.x + c * bottomRadius, bottom, origin.z + s * bottomRadius), normal) to
                vertex(Point3(origin.x + c * topRadius, top, origin.z + s * topRadius), normal)
        }
        for (i in 0 until steps) {
            val (b0, t0) = ring[i]
            val (b1, t1) = ring[i + 1]
            triangle(b0, t0, b1)
            triangle(b1, t0, t1)
        }
        for ((y, radius, up) in listOf(Triple(top, topRadius, 1f), Triple(bottom, bottomRadius, -1f))) {
            if (radius <= 0f) continue
            val normal = Point3(0f, up, 0f)
            val centre = vertex(Point3(origin.x, y, origin.z), normal)
            val rim = List(steps) { i ->
                val angle = (i * 2.0 * PI / steps).toFloat()
                vertex(Point3(origin.x + cos(angle) * radius, y, origin.z + sin(angle) * radius), normal)
            }
            for (i in 0 until steps) {
                if (up > 0f) triangle(centre, rim[(i + 1) % steps], rim[i]) else triangle(centre, rim[i], rim[(i + 1) % steps])
            }
        }
    }

    /** An icosphere subdivided [steps] times, flat shaded. */
    fun icoSphere(radius: Float, center: Point3, steps: Int) {
        var faces = icosahedron()
        repeat(steps) {
            faces = faces.flatMap { (a, b, c) ->
                val ab = (a + b).norm()
                val bc = (b + c).norm()
                val ca = (c + a).norm()
                listOf(Triple(a, ab, ca), Triple(b, bc, ab), Triple(c, ca, bc), Triple(ab, bc, ca))
            }
        }
        for ((a, b, c) in faces) {
            val normal = ((b - a) cross (c - a)).norm()
            triangle(vertex(center + a * radius, normal), vertex(center + b * radius, normal), vertex(center + c * radius, normal))
        }
    }

    /** An upright wall round a circle, visible from both sides. */
    fun band(radius: Float, bottom: Float, top: Float, segments: Int = 28) {
        val points = List(segments) { index ->
            val angle = (index * 2.0 * PI / segments).toFloat()
            val normal = Point3(cos(angle), 0f, sin(angle))
            vertex(Point3(cos(angle) * radius, bottom, sin(angle) * radius), normal) to
                vertex(Point3(cos(angle) * radius, top, sin(angle) * radius), normal)
        }
        for (index in points.indices) {
            val (low, high) = points[index]
            val (nextLow, nextHigh) = points[(index + 1) % segments]
            doubleSided(low, nextLow, high)
            doubleSided(nextLow, nextHigh, high)
        }
    }

    /** A flat annulus at height [y], visible from both sides. */
    fun ring(outer: Float, inner: Float, y: Float, segments: Int = 28) {
        val up = Point3(0f, 1f, 0f)
        val points = List(segments) { index ->
            val angle = (index * 2.0 * PI / segments).toFloat()
            vertex(Point3(cos(angle) * outer, y, sin(angle) * outer), up) to vertex(Point3(cos(angle) * inner, y, sin(angle) * inner), up)
        }
        for (index in points.indices) {
            val (o, i) = points[index]
            val (no, ni) = points[(index + 1) % segments]
            doubleSided(o, no, i)
            doubleSided(no, ni, i)
        }
    }

    val isEmpty get() = indices.isEmpty()

    fun build() = SurfaceMesh(positions.toFloatArray(), normals.toFloatArray(), colors.toFloatArray(), indices.toIntArray())

    private companion object {
        fun icosahedron(): List<Triple<Point3, Point3, Point3>> {
            val t = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
            val v = listOf(
                Point3(-1f, t, 0f), Point3(1f, t, 0f), Point3(-1f, -t, 0f), Point3(1f, -t, 0f),
                Point3(0f, -1f, t), Point3(0f, 1f, t), Point3(0f, -1f, -t), Point3(0f, 1f, -t),
                Point3(t, 0f, -1f), Point3(t, 0f, 1f), Point3(-t, 0f, -1f), Point3(-t, 0f, 1f),
            ).map { it.norm() }
            val f = listOf(
                0 to 11 to 5, 0 to 5 to 1, 0 to 1 to 7, 0 to 7 to 10, 0 to 10 to 11, 1 to 5 to 9, 5 to 11 to 4, 11 to 10 to 2,
                10 to 7 to 6, 7 to 1 to 8, 3 to 9 to 4, 3 to 4 to 2, 3 to 2 to 6, 3 to 6 to 8, 3 to 8 to 9, 4 to 9 to 5,
                2 to 4 to 11, 6 to 2 to 10, 8 to 6 to 7, 9 to 8 to 1,
            )
            return f.map { (ab, c) -> Triple(v[ab.first], v[ab.second], v[c]) }
        }
    }
}

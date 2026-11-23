package id.branchworld.renderer.geometry

import id.branchworld.simulation.ecology.ForageKind
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.terrain.TerrainMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The living sandbox's small shapes, as plain arrays for backends without a mesh builder. They follow the Kool
// renderer's forms (scene/life) so every platform shows the same pegs, rings, plants and burrows.

/** A painted board-game peg: a band round the cell, a stake and a round knob that reads from survey height. */
internal fun placementPeg(paint: Rgba, rim: Rgba): SurfaceMesh = ShapeBuilder().apply {
    color = paint
    band(0.17f, 0f, 0.05f)
    color = rim
    band(0.13f, 0.01f, 0.04f)
    color = paint
    for (turn in 0..1) {
        val dx = if (turn == 0) 0.02f else 0f
        val dz = if (turn == 0) 0f else 0.02f
        val side = Point3(1f, 0f, 0f)
        val a = vertex(Point3(-dx, 0f, -dz), side)
        val b = vertex(Point3(dx, 0f, dz), side)
        val c = vertex(Point3(dx * 0.5f, 0.30f, dz * 0.5f), side)
        val d = vertex(Point3(-dx * 0.5f, 0.30f, -dz * 0.5f), side)
        doubleSided(a, b, c)
        doubleSided(a, c, d)
    }
    color = rim
    icoSphere(0.05f, Point3(0f, 0.30f, 0f), 1)
    color = paint
    icoSphere(0.075f, Point3(0f, 0.36f, 0f), 2)
}.build()

/** The ring under a picked creature, with four notches so it visibly turns. */
internal fun selectionRing(): SurfaceMesh = ShapeBuilder().apply {
    val outer = 0.19f
    val inner = 0.155f
    color = Rgba(1f, 0.95f, 0.78f, 0.96f)
    band(outer, 0f, 0.035f)
    ring(outer, inner, 0.035f)
    color = Rgba(0.48f, 0.34f, 0.22f, 0.96f)
    val up = Point3(0f, 1f, 0f)
    for (notch in 0 until 4) {
        val angle = notch * PI.toFloat() / 2f
        val a = vertex(Point3(cos(angle) * inner, 0.038f, sin(angle) * inner), up)
        val b = vertex(Point3(cos(angle + 0.18f) * outer, 0.038f, sin(angle + 0.18f) * outer), up)
        val c = vertex(Point3(cos(angle - 0.18f) * outer, 0.038f, sin(angle - 0.18f) * outer), up)
        doubleSided(a, b, c)
    }
}.build()

/** One tuft of meadow grass or a berry bush, a unit tall; instances scale it by the patch's biomass. */
internal fun forageTuft(kind: ForageKind, wildlife: Boolean): SurfaceMesh = ShapeBuilder().apply {
    val grass = kind == ForageKind.Meadow
    color = if (grass) Rgba(0.69f, 0.78f, 0.22f) else Rgba(0.51f, 0.67f, 0.25f)
    val first = if (grass) 0.17f else 0.11f
    cylinder(if (grass) 0.030f else 0.05f, 0.01f, first, Point3(0f, first / 2, 0f), 4)
    if (!grass && wildlife) {
        for (stem in 0..2) {
            val angle = stem * 2.4f
            val x = cos(angle) * 0.056f
            val z = sin(angle) * 0.056f
            val y = 0.070f + stem * 0.028f
            color = if (stem == 1) Rgba(0.33f, 0.48f, 0.24f) else Rgba(0.23f, 0.43f, 0.24f)
            icoSphere(0.045f, Point3(x, y, z), 0)
            color = Rgba(0.56f, 0.20f, 0.25f)
            icoSphere(0.014f, Point3(x, y + 0.038f, z), 0)
        }
    }
    color = if (grass) Rgba(0.42f, 0.60f, 0.16f) else Rgba(0.32f, 0.55f, 0.17f)
    val second = if (grass) 0.11f else 0.17f
    cylinder(0.024f, 0.008f, second, Point3(0.039f, second / 2, -0.021f), 4)
}.build()

/** A tree the player planted: a trunk and two cones of foliage. */
internal fun sapling(): SurfaceMesh = ShapeBuilder().apply {
    color = Rgba(0.45f, 0.28f, 0.13f)
    cylinder(0.023f, 0.023f, 0.20f, Point3(0f, 0.10f, 0f), 5)
    color = Rgba(0.25f, 0.62f, 0.30f)
    cylinder(0.110f, 0.009f, 0.23f, Point3(0f, 0.24f, 0f), 5)
    color = Rgba(0.36f, 0.73f, 0.36f)
    cylinder(0.076f, 0.004f, 0.20f, Point3(0f, 0.33f, 0f), 5)
}.build()

/** Low earth mounds over each warren and a ring of stones at each pack's den, in world space. */
internal fun warrensAndDens(terrain: TerrainMap, warrens: List<GroundPoint>, dens: List<GroundPoint>): SurfaceMesh = ShapeBuilder().apply {
    for (warren in warrens) {
        val p = cellPosition(warren.x, warren.z, warren.elevationTwice)
        val y = terrain.heightAt(p.x, p.z)
        color = Rgba(0.47f, 0.34f, 0.21f)
        icoSphere(0.055f, Point3(p.x, y - 0.025f, p.z), 1)
        color = Rgba(0.21f, 0.15f, 0.10f)
        icoSphere(0.022f, Point3(p.x + 0.035f, y + 0.005f, p.z + 0.012f), 1)
    }
    for (den in dens) {
        val p = cellPosition(den.x, den.z, den.elevationTwice)
        for (stone in 0 until 7) {
            val angle = stone * 2f * PI.toFloat() / 7f
            val x = p.x + cos(angle) * 0.13f
            val z = p.z + sin(angle) * 0.13f
            color = if (stone % 2 == 0) Rgba(0.55f, 0.55f, 0.52f) else Rgba(0.46f, 0.46f, 0.44f)
            icoSphere(0.028f + (stone % 3) * 0.006f, Point3(x, terrain.heightAt(x, z), z), 1)
        }
    }
}.build()

/** Copies of [shape] placed by [placements] (each a matrix), merged into one mesh for a single draw. */
internal fun instanced(shape: SurfaceMesh, placements: List<Mat4>): SurfaceMesh {
    val n = shape.vertexCount
    val positions = FloatArray(n * 3 * placements.size)
    val normals = FloatArray(n * 3 * placements.size)
    val colors = FloatArray(n * 4 * placements.size)
    val indices = IntArray(shape.indices.size * placements.size)
    placements.forEachIndexed { copy, matrix ->
        for (v in 0 until n) {
            val p = matrix.point(Point3(shape.positions[v * 3], shape.positions[v * 3 + 1], shape.positions[v * 3 + 2]))
            val d = matrix.direction(Point3(shape.normals[v * 3], shape.normals[v * 3 + 1], shape.normals[v * 3 + 2]))
            val at = (copy * n + v) * 3
            positions[at] = p.x; positions[at + 1] = p.y; positions[at + 2] = p.z
            normals[at] = d.x; normals[at + 1] = d.y; normals[at + 2] = d.z
        }
        shape.colors.copyInto(colors, copy * n * 4)
        for (i in shape.indices.indices) indices[copy * shape.indices.size + i] = shape.indices[i] + copy * n
    }
    return SurfaceMesh(positions, normals, colors, indices)
}

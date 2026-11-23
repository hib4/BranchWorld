package id.branchworld.renderer.scene.life

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.releaseDelayed
import id.branchworld.renderer.scene.cellPosition
import id.branchworld.renderer.geometry.heightAt
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.LifePose
import id.branchworld.simulation.projection.PlaceTool
import id.branchworld.simulation.projection.PlacementPreview
import id.branchworld.simulation.terrain.TerrainMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rules v12 presentation: where the player's tool would act, the creature they picked out, and the warrens and dens
 * the animals made. Everything here is cosmetic and drawn from the read-only projection.
 */
internal class LifeDetails(private val scene: Scene, private val terrain: TerrainMap) {
    private val valid = scene.addMarker("Placement ghost", Color(0.30f, 0.62f, 0.22f, 1f), Color(0.96f, 0.93f, 0.80f, 1f))
    private val invalid = scene.addMarker("Placement refused", Color(0.74f, 0.20f, 0.13f, 1f), Color(0.98f, 0.86f, 0.80f, 1f))
    private val selection = scene.addRing("Picked creature", Color(1f, 0.95f, 0.78f, 0.96f), outer = 0.19f, inner = 0.155f)
    private var homes: Node? = null
    private var shownHomes: Pair<List<GroundPoint>, List<GroundPoint>>? = null

    fun show(
        placement: PlacementPreview?,
        selected: Vec3f?,
        life: LifePose?,
        seconds: Float,
        reducedMotion: Boolean,
    ) {
        val bob = if (reducedMotion) 0f else sin(seconds * 4.2f) * 0.012f
        valid.isVisible = placement?.valid == true
        invalid.isVisible = placement != null && !placement.valid
        if (placement != null) {
            val p = cellPosition(placement.ground.x, placement.ground.z, placement.ground.elevationTwice)
            val y = terrain.heightAt(p.x, p.z).coerceAtLeast(0f)
            val marker = if (placement.valid) valid else invalid
            // Plants mark a smaller patch than animals.
            val size = if (placement.tool == PlaceTool.Tree || placement.tool == PlaceTool.Meadow) 0.8f else 1f
            marker.transform.setIdentity().translate(p.x, y - 0.01f + bob, p.z).scale(size)
        }
        selection.isVisible = selected != null
        if (selected != null) {
            selection.transform.setIdentity().translate(selected.x, selected.y + 0.025f, selected.z)
                .rotate(de.fabmax.kool.math.AngleF((seconds * 0.6f) % 6.2831855f), Vec3f.Y_AXIS)
        }
        val warrens = life?.warrens.orEmpty()
        val dens = life?.packs?.map { it.den }.orEmpty()
        if (shownHomes != warrens to dens) {
            homes?.let {
                scene.removeNode(it)
                it.releaseDelayed(2)
            }
            homes = if (warrens.isEmpty() && dens.isEmpty()) null else scene.addHomes(warrens, dens)
            shownHomes = warrens to dens
        }
    }

    /** A painted board-game peg: a raised band round the cell, a stake and a round knob that reads from survey height. */
    private fun Scene.addMarker(name: String, paint: Color, rim: Color): Node = addColorMesh(name) {
        generate {
            band(this, paint, 0.17f, 0f, 0.05f)
            band(this, rim, 0.13f, 0.01f, 0.04f)
            color = paint
            for (turn in 0..1) {
                val dx = if (turn == 0) 0.02f else 0f
                val dz = if (turn == 0) 0f else 0.02f
                val a = vertex(Vec3f(-dx, 0f, -dz), Vec3f.X_AXIS)
                val b = vertex(Vec3f(dx, 0f, dz), Vec3f.X_AXIS)
                val c = vertex(Vec3f(dx * 0.5f, 0.30f, dz * 0.5f), Vec3f.X_AXIS)
                val d = vertex(Vec3f(-dx * 0.5f, 0.30f, -dz * 0.5f), Vec3f.X_AXIS)
                addTriIndices(a, b, c)
                addTriIndices(a, c, d)
                addTriIndices(b, a, c)
                addTriIndices(c, a, d)
            }
            color = rim
            icoSphere { radius = 0.05f; steps = 1; center.set(0f, 0.30f, 0f) }
            color = paint
            icoSphere { radius = 0.075f; steps = 2; center.set(0f, 0.36f, 0f) }
        }
        shader = KslBlinnPhongShader {
            color { vertexColor() }
            specularStrength(0.12f)
        }
        // Moved every frame by its transform; small enough that culling it buys nothing.
        isFrustumChecked = false
        isVisible = false
    }

    private fun Scene.addRing(name: String, paint: Color, outer: Float, inner: Float): Node = addColorMesh(name) {
        generate {
            band(this, paint, outer, 0f, 0.035f)
            ring(this, paint, outer, inner, 0.035f)
            // Four notches so the ring visibly turns.
            color = Color(0.48f, 0.34f, 0.22f, 0.96f)
            for (notch in 0 until 4) {
                val angle = notch * PI.toFloat() / 2f
                val a = vertex(Vec3f(cos(angle) * inner, 0.003f, sin(angle) * inner), Vec3f.Y_AXIS)
                val b = vertex(Vec3f(cos(angle + 0.18f) * outer, 0.003f, sin(angle + 0.18f) * outer), Vec3f.Y_AXIS)
                val c = vertex(Vec3f(cos(angle - 0.18f) * outer, 0.003f, sin(angle - 0.18f) * outer), Vec3f.Y_AXIS)
                addTriIndices(a, b, c)
                addTriIndices(a, c, b)
            }
        }
        shader = KslBlinnPhongShader {
            color { vertexColor() }
            specularStrength(0.05f)
        }
        isFrustumChecked = false
        isVisible = false
    }

    /** Low earth mounds over each warren and a ring of stones at each pack's den. */
    private fun Scene.addHomes(warrens: List<GroundPoint>, dens: List<GroundPoint>) = addColorMesh("Warrens and dens") {
        generate {
            for (warren in warrens) {
                val p = cellPosition(warren.x, warren.z, warren.elevationTwice)
                val y = terrain.heightAt(p.x, p.z)
                color = Color(0.47f, 0.34f, 0.21f, 1f)
                icoSphere { radius = 0.055f; steps = 1; center.set(p.x, y - 0.025f, p.z) }
                color = Color(0.21f, 0.15f, 0.10f, 1f)
                icoSphere { radius = 0.022f; steps = 1; center.set(p.x + 0.035f, y + 0.005f, p.z + 0.012f) }
            }
            for (den in dens) {
                val p = cellPosition(den.x, den.z, den.elevationTwice)
                for (stone in 0 until 7) {
                    val angle = stone * 2f * PI.toFloat() / 7f
                    val x = p.x + cos(angle) * 0.13f
                    val z = p.z + sin(angle) * 0.13f
                    color = if (stone % 2 == 0) Color(0.55f, 0.55f, 0.52f, 1f) else Color(0.46f, 0.46f, 0.44f, 1f)
                    icoSphere { radius = 0.028f + (stone % 3) * 0.006f; steps = 1; center.set(x, terrain.heightAt(x, z), z) }
                }
            }
        }
        shader = KslBlinnPhongShader {
            color { vertexColor() }
            specularStrength(0.04f)
        }
    }
}

private fun ring(builder: de.fabmax.kool.scene.geometry.MeshBuilder<*>, paint: Color, outer: Float, inner: Float, y: Float) = with(builder) {
    color = paint
    val segments = 28
    val points = List(segments) { index ->
        val angle = (index * 2.0 * PI / segments).toFloat()
        vertex(Vec3f(cos(angle) * outer, y, sin(angle) * outer), Vec3f.Y_AXIS) to
            vertex(Vec3f(cos(angle) * inner, y, sin(angle) * inner), Vec3f.Y_AXIS)
    }
    for (index in points.indices) {
        val (o, i) = points[index]
        val (no, ni) = points[(index + 1) % segments]
        addTriIndices(o, no, i)
        addTriIndices(no, ni, i)
        addTriIndices(o, i, no)
        addTriIndices(no, i, ni)
    }
}

/** An upright wall round a circle, so a marker stays visible on uneven ground. */
private fun band(builder: de.fabmax.kool.scene.geometry.MeshBuilder<*>, paint: Color, radius: Float, bottom: Float, top: Float) = with(builder) {
    color = paint
    val segments = 28
    val points = List(segments) { index ->
        val angle = (index * 2.0 * PI / segments).toFloat()
        val normal = Vec3f(cos(angle), 0f, sin(angle))
        vertex(Vec3f(cos(angle) * radius, bottom, sin(angle) * radius), normal) to
            vertex(Vec3f(cos(angle) * radius, top, sin(angle) * radius), normal)
    }
    for (index in points.indices) {
        val (low, high) = points[index]
        val (nextLow, nextHigh) = points[(index + 1) % segments]
        addTriIndices(low, nextLow, high)
        addTriIndices(nextLow, nextHigh, high)
        addTriIndices(low, high, nextLow)
        addTriIndices(nextLow, high, nextHigh)
    }
}

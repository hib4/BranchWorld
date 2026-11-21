package id.branchworld.renderer

import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.scene.InstanceLayouts
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.MeshInstanceList
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import id.branchworld.game.ecology.Species
import id.branchworld.game.simulation.GroundPoint
import id.branchworld.game.simulation.SettlementPose
import id.branchworld.game.simulation.WorldRenderProjection
import id.branchworld.game.terrain.TerrainMap
import kotlin.math.cos
import kotlin.math.sin

/** Reuses the same draw batches for all animals and homes in the displayed history. */
internal class LocalDetails(scene: Scene, private val terrain: TerrainMap) {
    private val transform = MutableMat4f()
    private val shadow = scene.detailMesh("Ground contact") {
        color = Color(0.053f, 0.085f, 0.075f, 0.38f)
        val center = vertex(Vec3f(0.12f, 0f, 0.05f), Vec3f.Y_AXIS)
        color = Color(0.053f, 0.085f, 0.075f, 0f)
        val ring = List(12) { i ->
            val angle = i * 0.523599f
            vertex(Vec3f(cos(angle), 0f, sin(angle)), Vec3f.Y_AXIS)
        }
        ring.indices.forEach { i -> addTriIndices(center, ring[(i + 1) % ring.size], ring[i]) }
    }
    private val smoke = scene.detailMesh("Chimney smoke") {
        color = Color(0.77f, 0.78f, 0.73f, 0.105f)
        icoSphere { radius = 1f; steps = 0 }
    }
    private val lamplight = scene.detailMesh("Warm windows") {
        color = Color(1f, 0.61f, 0.24f, 0.27f)
        val center = vertex(Vec3f.ZERO, Vec3f.Y_AXIS)
        color = Color(1f, 0.46f, 0.17f, 0f)
        val ring = List(12) { i ->
            val angle = i * 0.523599f
            vertex(Vec3f(cos(angle), 0f, sin(angle)), Vec3f.Y_AXIS)
        }
        ring.indices.forEach { i -> addTriIndices(center, ring[(i + 1) % ring.size], ring[i]) }
    }
    private data class Contact(val ground: GroundPoint, val species: Species, val juvenile: Boolean)
    private var previousCreatures: List<Contact>? = null
    private var previousSites: List<SettlementPose>? = null
    private var previousProjection: WorldRenderProjection? = null
    private var homes = emptyList<GroundPoint>()
    private var smokeStep = -1

    fun show(world: WorldRenderProjection, light: CoastalLight, seconds: Float, reducedMotion: Boolean) {
        if (world !== previousProjection) {
            previousProjection = world
            val creatures = world.creatures.map { Contact(it.ground, it.species, it.juvenile) }
            val sites = world.settlements.orEmpty()
            val sitesChanged = sites != previousSites
            if (sitesChanged || creatures != previousCreatures) {
                previousCreatures = creatures
                previousSites = sites
                homes = sites.flatMap { it.homes }
                val fields = sites.map { it.field }
                shadow.populate(creatures.size + homes.size + fields.size) { instance ->
                    for ((ground, species, juvenile) in creatures) {
                        val radius = when (species) {
                            Species.Rabbit -> 0.16f
                            Species.Deer -> 0.21f
                            Species.Fox -> 0.19f
                        }
                        val bodyScale = if (world.wildlife != null) WildlifeBodyScale else 1f
                        instance(onGround(ground, radius * bodyScale * if (juvenile) 0.73f else 1f))
                    }
                    for (ground in homes) instance(onGround(ground, 0.23f))
                    for (ground in fields) instance(onGround(ground, 0.18f))
                }
            }
            if (sitesChanged) {
                smokeStep = -1
                lamplight.populate(homes.size) { instance ->
                    for (home in homes) instance(onGround(home, 0.21f, bias = -0.085f))
                }
            }
        }
        lamplight.isVisible = homes.isNotEmpty() && light.sun < 0.40f
        smoke.isVisible = homes.isNotEmpty() && !reducedMotion && !light.rain
        if (!smoke.isVisible) return
        val step = (seconds * 24f).toInt()
        if (step == smokeStep) return
        smokeStep = step
        smoke.populate(homes.size * 3) { instance ->
            for ((index, home) in homes.withIndex()) {
                val base = cellPosition(home.x, home.z, home.elevationTwice)
                repeat(3) { puff ->
                    val phase = (step / 95f + index * 0.14f + puff / 3f) % 1f
                    val size = (0.025f + phase * 0.040f) * (sin(phase * 3.141593f) * 0.65f + 0.2f)
                    instance(transform.setIdentity()
                        .translate(base.x + phase * 0.20f, base.y + 0.245f + phase * 0.31f, base.z - 0.091f + phase * 0.09f)
                        .scale(Vec3f(size, size * 0.85f, size)))
                }
            }
        }
    }

    private fun onGround(point: GroundPoint, radius: Float, bias: Float = 0.036f): MutableMat4f {
        val center = cellPosition(point.x, point.z, point.elevationTwice)
        val x = center.x + bias
        val z = center.z + bias
        val slopeX = (terrain.heightAt(x + radius, z) - terrain.heightAt(x - radius, z)) / (2f * radius)
        val slopeZ = (terrain.heightAt(x, z + radius) - terrain.heightAt(x, z - radius)) / (2f * radius)
        return transform.set(
            radius, 0f, 0f, x,
            slopeX * radius, 1f, slopeZ * radius, terrain.heightAt(x, z) + 0.022f,
            0f, 0f, radius, z,
            0f, 0f, 0f, 1f,
        )
    }

    private fun Mesh<*>.populate(count: Int, fill: ((MutableMat4f) -> Unit) -> Unit) {
        isVisible = count > 0
        if (count == 0) return
        addInstances<InstanceLayouts.ModelMat>(count, clear = true) { storage ->
            fill { matrix -> storage.put { set(it.modelMat, matrix) } }
        }
    }
}

private fun Scene.detailMesh(name: String, shape: de.fabmax.kool.scene.geometry.MeshBuilder<de.fabmax.kool.scene.VertexLayouts.PositionNormalColor>.() -> Unit) =
    addColorMesh(name, instances = MeshInstanceList(InstanceLayouts.ModelMat)) {
        isVisible = false
        isFrustumChecked = false
        generate(generator = shape)
        shader = KslUnlitShader {
            vertices { instancedModelMatrix() }
            color { vertexColor() }
            pipeline {
                isWriteDepth = false
                cullMethod = CullMethod.NO_CULLING
            }
        }
    }

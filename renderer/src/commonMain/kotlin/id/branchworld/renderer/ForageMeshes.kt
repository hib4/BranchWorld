package id.branchworld.renderer

import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.scene.InstanceLayouts
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.MeshInstanceList
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import id.branchworld.game.ecology.ForageKind
import id.branchworld.game.simulation.ForagePose
import id.branchworld.game.simulation.GroundPoint
import id.branchworld.game.simulation.WorldRenderProjection
import id.branchworld.game.terrain.TerrainMap
import kotlin.math.cos
import kotlin.math.sin

internal class ForageMeshes(
    scene: Scene,
    terrain: TerrainMap,
    springs: List<GroundPoint>,
    wildlife: Boolean,
    private val shaping: Boolean,
) {
    private val meshes = ForageKind.entries.associateWith { kind ->
        scene.addColorMesh("$kind forage", instances = MeshInstanceList(InstanceLayouts.ModelMat)) {
            generate {
                val grass = kind == ForageKind.Meadow
                color = if (grass) Color(0.69f, 0.78f, 0.22f) else Color(0.51f, 0.67f, 0.25f)
                cylinder {
                    bottomRadius = if (grass) 0.030f else 0.05f
                    topRadius = 0.01f
                    height = if (grass) 0.17f else 0.11f
                    origin.y = height / 2
                    steps = 4
                }
                if (!grass && wildlife) {
                    for (stem in 0..2) {
                        val angle = stem * 2.4f
                        val x = cos(angle) * 0.056f
                        val z = sin(angle) * 0.056f
                        val y = 0.070f + stem * 0.028f
                        color = if (stem == 1) Color(0.33f, 0.48f, 0.24f) else Color(0.23f, 0.43f, 0.24f)
                        icoSphere { radius = 0.045f; center.set(x, y, z); steps = 0 }
                        color = Color(0.56f, 0.20f, 0.25f)
                        icoSphere { radius = 0.014f; center.set(x, y + 0.038f, z); steps = 0 }
                    }
                }
                color = if (grass) Color(0.42f, 0.60f, 0.16f) else Color(0.32f, 0.55f, 0.17f)
                cylinder {
                    bottomRadius = 0.024f
                    topRadius = 0.008f
                    height = if (grass) 0.11f else 0.17f
                    origin.set(0.039f, height / 2, -0.021f)
                    steps = 4
                }
            }
            shader = KslBlinnPhongShader {
                vertices { instancedModelMatrix() }
                color { vertexColor() }
            }
        }
    }
    private val saplings = scene.addColorMesh("Planted saplings", instances = MeshInstanceList(InstanceLayouts.ModelMat)) {
        generate {
            color = Color(0.45f, 0.28f, 0.13f)
            cylinder {
                radius = 0.023f
                height = 0.20f
                origin.y = 0.10f
                steps = 5
            }
            color = Color(0.25f, 0.62f, 0.30f)
            cylinder {
                bottomRadius = 0.110f
                topRadius = 0.009f
                height = 0.23f
                origin.y = 0.24f
                steps = 5
            }
            color = Color(0.36f, 0.73f, 0.36f)
            cylinder {
                bottomRadius = 0.076f
                height = 0.20f
                topRadius = 0.004f
                origin.y = 0.33f
                steps = 5
            }
        }
        shader = KslBlinnPhongShader {
            vertices { instancedModelMatrix() }
            color { vertexColor() }
        }
    }
    private var previous: List<ForagePose>? = null
    private var previousTick: Long? = null
    private var previousBranch: Int? = null
    private var sprouting = emptySet<Int>()
    private var plantedAt = 0f
    private val previousGroups = mutableMapOf<Mesh<*>, List<ForagePose>>()

    init {
        if (terrain.watershed == null) scene.addColorMesh("Freshwater springs") {
            generate {
                for (spring in springs) {
                    val point = cellPosition(spring.x, spring.z, spring.elevationTwice)
                    val count = 11
                    fun edge(index: Int, size: Float): Vec3f {
                        val angle = index * 0.57119864f
                        val radius = size * (0.84f + ((spring.x * 83 + index * 7) and 7) / 24f)
                        val x = point.x + cos(angle) * radius * 1.2f
                        val z = point.z + sin(angle) * radius * 0.86f
                        return Vec3f(x, terrain.heightAt(x, z) + 0.017f, z)
                    }
                    color = Color(0.13f, 0.37f, 0.35f)
                    val pool = vertex(Vec3f(point.x, point.y + 0.020f, point.z), Vec3f.Y_AXIS)
                    color = Color(0.29f, 0.56f, 0.48f)
                    val wet = List(count) { vertex(edge(it, 0.108f), Vec3f.Y_AXIS) }
                    color = Color(0.35f, 0.38f, 0.26f)
                    val stones = List(count) { vertex(edge(it, 0.157f), Vec3f.Y_AXIS) }
                    repeat(count) { i ->
                        val next = (i + 1) % count
                        addTriIndices(pool, wet[next], wet[i])
                        addTriIndices(wet[i], wet[next], stones[next])
                        addTriIndices(wet[i], stones[next], stones[i])
                    }
                }
            }
            shader = KslBlinnPhongShader { color { vertexColor() }; specularStrength(0.13f) }
        }
    }

    fun show(world: WorldRenderProjection, seconds: Float, reducedMotion: Boolean) {
        val patches = world.forage
        val update = patches != previous
        val movedHistory = previousBranch != world.branchId || world.tick < (previousTick ?: 0L)
        if (movedHistory || reducedMotion) sprouting = emptySet()
        if (update) {
            val prior = previous
            if (shaping && !reducedMotion && !movedHistory && world.interpolateCreatures && prior != null) {
                val found = patches.filter { patch -> patch.planted && prior.none { it.id == patch.id } }
                if (found.isNotEmpty()) {
                    sprouting = found.mapTo(mutableSetOf(), ForagePose::id)
                    plantedAt = seconds
                }
            }
            for ((kind, mesh) in meshes) {
                mesh.show(patches.filter { it.kind == kind && !it.planted && it.biomassTier > 0 })
            }
        }
        if (update || sprouting.isNotEmpty() || previousGrowth < 1f) {
            val progress = if (sprouting.isEmpty()) 1f else ((seconds - plantedAt) / 0.35f).coerceIn(0f, 1f)
            saplings.show(patches.filter { it.planted }, progress)
            previousGrowth = progress
            if (progress == 1f) sprouting = emptySet()
        }
        previous = patches
        previousTick = world.tick
        previousBranch = world.branchId
    }

    private var previousGrowth = 1f

    private fun Mesh<*>.show(visible: List<ForagePose>, growth: Float = 1f) {
        if (previousGroups.put(this, visible) == visible && (this != saplings || growth == 1f && previousGrowth == 1f)) return
        isVisible = visible.isNotEmpty()
        if (visible.isEmpty()) return
        val transform = MutableMat4f()
        addInstances<InstanceLayouts.ModelMat>(visible.size, clear = true) { buffer ->
            for (patch in visible) {
                val location = patch.ground
                val point = cellPosition(location.x, location.z, location.elevationTwice)
                val height = when {
                    !patch.planted -> patch.biomassTier / 4f
                    shaping -> 0.22f + patch.biomassTier * 0.25f
                    else -> 0.6f + patch.biomassTier * 0.13f
                }
                val rise = if (patch.id in sprouting) 1f - 0.70f * (1f - growth) * (1f - growth) else 1f
                transform.setIdentity().translate(point).scale(Vec3f(1f, height * rise, 1f))
                buffer.put { set(it.modelMat, transform) }
            }
        }
    }
}

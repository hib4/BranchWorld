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

internal class ForageMeshes(scene: Scene, springs: List<GroundPoint>) {
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

    init {
        scene.addColorMesh("Freshwater springs") {
            generate {
                color = Color(0.19f, 0.64f, 0.89f)
                for (spring in springs) {
                    val point = cellPosition(spring.x, spring.z, spring.elevationTwice)
                    cylinder {
                        radius = 0.081f
                        height = 0.036f
                        steps = 12
                        origin.set(point.x, point.y + 0.018f, point.z)
                    }
                }
            }
            shader = KslBlinnPhongShader { color { vertexColor() } }
        }
    }

    fun show(patches: List<ForagePose>) {
        if (patches == previous) return
        previous = patches
        for ((kind, mesh) in meshes) {
            mesh.show(patches.filter { it.kind == kind && !it.planted && it.biomassTier > 0 })
        }
        saplings.show(patches.filter { it.planted })
    }

    private fun Mesh<*>.show(visible: List<ForagePose>) {
        isVisible = visible.isNotEmpty()
        if (visible.isEmpty()) return
        val transform = MutableMat4f()
        addInstances<InstanceLayouts.ModelMat>(visible.size, clear = true) { buffer ->
            for (patch in visible) {
                val location = patch.ground
                val point = cellPosition(location.x, location.z, location.elevationTwice)
                val height = if (patch.planted) 0.6f + patch.biomassTier * 0.13f else patch.biomassTier / 4f
                transform.setIdentity().translate(point).scale(Vec3f(1f, height, 1f))
                buffer.put { set(it.modelMat, transform) }
            }
        }
    }
}

package id.branchworld.renderer.scene

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import id.branchworld.renderer.geometry.canopyShade
import id.branchworld.renderer.geometry.heightAt
import id.branchworld.renderer.geometry.meadowBlades
import id.branchworld.renderer.geometry.weatheredStones
import id.branchworld.simulation.terrain.Biome
import id.branchworld.simulation.terrain.TerrainMap
import id.branchworld.simulation.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.simulation.terrain.TerrainMap.Companion.MinCell

/** Small decorative forms use only the recorded geography and never affect creature paths. */
internal fun Scene.addLandscapeDetails(terrain: TerrainMap, wind: MeadowWind): Mesh<*> {
    addColorMesh("Weathered stone and meadow flowers") {
        generate {
            addSurface(terrain.weatheredStones())
            for (z in MinCell..MaxCell) {
                for (x in MinCell..MaxCell) {
                    val cell = terrain.cellAt(x, z) ?: continue
                    val pattern = ((x * 107 xor z * 61) * 31) and 127
                    if (cell.biome != Biome.Meadow || !cell.walkable || pattern > 10) continue
                    val p = cellPosition(x, z, cell.elevationTwice)
                    for (blade in 0..2) {
                        val offset = blade * 0.038f - 0.038f
                        val height = 0.045f + ((pattern + blade * 3) % 7) * 0.009f
                        val root = Vec3f(p.x + offset, terrain.heightAt(p.x + offset, p.z + offset), p.z + offset)
                        color = Color(0.32f, 0.49f, 0.24f)
                        cylinder {
                            radius = 0.006f
                            this.height = height
                            origin.set(root.x, root.y + height / 2, root.z)
                            steps = 3
                        }
                        color = if (pattern % 3 == 0) Color(0.86f, 0.62f, 0.20f) else Color(0.83f, 0.83f, 0.60f)
                        icoSphere {
                            radius = 0.019f
                            center.set(root.x, root.y + height, root.z)
                            steps = 0
                        }
                    }
                }
            }
        }
        shader = KslBlinnPhongShader { color { vertexColor() }; specularStrength(0.02f) }
    }
    addColorMesh("Canopy shade") {
        generate { addSurface(terrain.canopyShade()) }
        shader = translucentSurface()
    }
    return addColorMesh("Wind in the meadow") {
        generate { addSurface(terrain.meadowBlades()) }
        shader = wind
    }
}

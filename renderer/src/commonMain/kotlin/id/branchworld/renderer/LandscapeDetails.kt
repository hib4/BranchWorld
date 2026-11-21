package id.branchworld.renderer

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.PropKind
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.game.terrain.TerrainMap.Companion.MinCell
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Small decorative forms use only the recorded geography and never affect creature paths. */
internal fun Scene.addLandscapeDetails(terrain: TerrainMap) {
    val slate = Color(0.40f, 0.44f, 0.37f)
    val sandstone = Color(0.58f, 0.54f, 0.43f)
    val moss = Color(0.31f, 0.40f, 0.24f)
    addColorMesh("Weathered stone and meadow flowers") {
        generate {
            for (index in 0 until terrain.propCount) {
                val prop = terrain.prop(index)
                if (prop.kind != PropKind.Rock) continue
                val cell = terrain.cellAt(prop.x, prop.z) ?: continue
                val p = cellPosition(prop.x, prop.z, cell.elevationTwice)
                val scale = prop.scalePercent / 100f
                val stretch = 0.85f + (index % 6) * 0.11f
                val angle = prop.yawDegrees * 0.017453292f
                val count = 6
                val top = Vec3f(p.x + 0.074f * cos(angle), p.y + 0.14f * scale, p.z + 0.039f * sin(angle))
                val base = List(count) { step ->
                    val bearing = angle + step * 1.0471976f
                    val radius = (0.12f + ((step * 5 + index) % 7) * 0.014f) * scale
                    val x = p.x + cos(bearing) * radius * stretch
                    val z = p.z + sin(bearing) * radius
                    Vec3f(x, terrain.heightAt(x, z) - 0.013f, z)
                }
                for (step in base.indices) {
                    val a = base[step]
                    val b = base[(step + 1) % count]
                    val normal = (top - a).cross(b - a, MutableVec3f()).norm()
                    color = slate.mix(sandstone, ((step * 13 + index * 11) % 17) / 22f)
                    if ((step + index * 3) % 13 < 2) color = moss
                    addTriIndices(vertex(a, normal), vertex(top, normal), vertex(b, normal))
                }
            }
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
        generate {
            for (index in 0 until terrain.propCount) {
                val prop = terrain.prop(index)
                if (prop.kind != PropKind.Pine) continue
                val scale = prop.scalePercent / 100f
                val x = (prop.x + 0.5f) * CellSize + 0.12f * scale
                val z = (prop.z + 0.5f) * CellSize + 0.14f * scale
                val radius = 0.28f * scale
                color = Color(0.06f, 0.14f, 0.13f, 0.27f)
                val center = vertex(Vec3f(x, terrain.heightAt(x, z) + 0.013f, z), Vec3f.Y_AXIS)
                color = Color(0.06f, 0.14f, 0.13f, 0f)
                val edge = List(10) { step ->
                    val angle = step * 0.62831855f
                    val px = x + cos(angle) * radius
                    val pz = z + sin(angle) * radius * 1.24f
                    vertex(Vec3f(px, terrain.heightAt(px, pz) + 0.016f, pz), Vec3f.Y_AXIS)
                }
                edge.indices.forEach { step -> addTriIndices(center, edge[(step + 1) % edge.size], edge[step]) }
            }
        }
        shader = translucentSurface()
    }
}

private fun TerrainMap.heightAt(x: Float, z: Float): Float {
    val cellX = floor(x / CellSize).toInt().coerceIn(MinCell, MaxCell)
    val cellZ = floor(z / CellSize).toInt().coerceIn(MinCell, MaxCell)
    val fx = (x / CellSize - cellX).coerceIn(0f, 1f)
    val fz = (z / CellSize - cellZ).coerceIn(0f, 1f)
    val a = cornerElevation(cellX, cellZ)
    val d = cornerElevation(cellX + 1, cellZ + 1)
    val diagonal = if (fx <= fz) {
        val c = cornerElevation(cellX, cellZ + 1)
        a + (c - a) * fz + (d - c) * fx
    } else {
        val b = cornerElevation(cellX + 1, cellZ)
        a + (b - a) * fx + (d - b) * fz
    }
    return diagonal * ElevationScale
}

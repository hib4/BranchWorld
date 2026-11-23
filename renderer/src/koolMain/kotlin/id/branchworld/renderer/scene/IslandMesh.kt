package id.branchworld.renderer.scene

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.util.Color
import id.branchworld.renderer.geometry.CellSize
import id.branchworld.renderer.geometry.ChunkSize
import id.branchworld.renderer.geometry.ElevationScale
import id.branchworld.renderer.geometry.Point3
import id.branchworld.renderer.geometry.SeaLevel
import id.branchworld.renderer.geometry.SurfaceMesh
import id.branchworld.renderer.geometry.seaSurface
import id.branchworld.renderer.geometry.terrainChunks
import id.branchworld.simulation.terrain.Biome
import id.branchworld.simulation.terrain.TerrainMap
import id.branchworld.simulation.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.simulation.terrain.TerrainMap.Companion.MinCell
import kotlin.math.max
import id.branchworld.renderer.geometry.cellPosition as GeometryCell
import id.branchworld.renderer.geometry.facets as geometryFacets

/** [cellPosition] as an engine vector, for scene code. */
internal fun cellPosition(x: Int, z: Int, elevationTwice: Int): Vec3f =
    GeometryCell(x, z, elevationTwice).let { Vec3f(it.x, it.y, it.z) }

internal data class TerrainFacet(val a: Vec3f, val b: Vec3f, val c: Vec3f, val biome: Biome) {
    fun normal() = (b - a).cross(c - a, MutableVec3f()).norm()
}

/** The shared terrain triangles as engine vectors, for overlays drawn over the ground. */
internal fun TerrainMap.facets(chunkX: Int, chunkZ: Int): List<TerrainFacet> =
    geometryFacets(chunkX, chunkZ).map { TerrainFacet(it.a.vec(), it.b.vec(), it.c.vec(), it.biome) }

private fun Point3.vec() = Vec3f(x, y, z)

/** The painted land and the sea, built from the shared geometry every renderer backend draws. */
internal fun Scene.addIsland(terrain: TerrainMap, water: CoastalWater, groundShader: KslBlinnPhongShader) {
    for (chunk in terrain.terrainChunks()) {
        addColorMesh("Terrain ${chunk.chunkX},${chunk.chunkZ}") {
            generate { addSurface(chunk.mesh) }
            shader = groundShader
        }
    }
    addColorMesh("Coastal water") {
        generate { addSurface(terrain.seaSurface()) }
        shader = water
    }
}

/** Appends shared mesh arrays to a Kool mesh being generated. */
internal fun MeshBuilder<*>.addSurface(mesh: SurfaceMesh) {
    val p = mesh.positions
    val n = mesh.normals
    val c = mesh.colors
    val added = IntArray(mesh.vertexCount) { i ->
        color = Color(c[i * 4], c[i * 4 + 1], c[i * 4 + 2], c[i * 4 + 3])
        vertex(Vec3f(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]), Vec3f(n[i * 3], n[i * 3 + 1], n[i * 3 + 2]))
    }
    val indices = mesh.indices
    for (i in indices.indices step 3) addTriIndices(added[indices[i]], added[indices[i + 1]], added[indices[i + 2]])
}

internal fun Scene.addRegionHighlight(terrain: TerrainMap, region: Int, rain: Boolean = false, subtle: Boolean = false) =
    addColorMesh("Intervention preview") {
        val x0 = MinCell + region % 4 * ChunkSize
        val z0 = MinCell + region / 4 * ChunkSize
        generate {
            if (rain) {
                for (z in maxOf(MinCell, z0 - 2)..minOf(MaxCell, z0 + 17)) {
                    for (x in maxOf(MinCell, x0 - 2)..minOf(MaxCell, x0 + 17)) {
                        val cell = checkNotNull(terrain.cellAt(x, z))
                        if (cell.biome == Biome.Water) continue
                        val edge = x == x0 - 2 || x == x0 + 17 || z == z0 - 2 || z == z0 + 17
                        color = Color(0.48f, 0.87f, 0.88f, if (edge) 0.28f else 0.11f)
                        fun point(cx: Int, cz: Int) = vertex(
                            Vec3f(cx * CellSize, max(terrain.cornerElevation(cx, cz) * ElevationScale, SeaLevel) + 0.016f,
                                cz * CellSize), Vec3f.Y_AXIS,
                        )
                        val a = point(x, z)
                        val b = point(x + 1, z)
                        val c = point(x, z + 1)
                        val d = point(x + 1, z + 1)
                        addTriIndices(a, c, d)
                        addTriIndices(a, d, b)
                    }
                }
            } else {
                terrain.facets(x0, z0).forEach { face ->
                    val centerX = (face.a.x + face.b.x + face.c.x) / 3f
                    val centerZ = (face.a.z + face.b.z + face.c.z) / 3f
                    val edge = centerX < (x0 + 1) * CellSize || centerX > (x0 + 15) * CellSize ||
                        centerZ < (z0 + 1) * CellSize || centerZ > (z0 + 15) * CellSize
                    color = if (subtle) Color(0.95f, 0.83f, 0.44f, if (edge) 0.27f else 0.085f)
                        else Color(1f, 0.80f, 0.29f, if (edge) 0.58f else 0.15f)
                    val normal = face.normal()
                    fun raised(p: Vec3f) = Vec3f(p.x, max(p.y, SeaLevel) + 0.016f, p.z)
                    addTriIndices(vertex(raised(face.a), normal), vertex(raised(face.b), normal), vertex(raised(face.c), normal))
                }
            }
        }
        shader = translucentSurface()
    }

internal fun translucentSurface() = KslUnlitShader {
    color { vertexColor() }
    pipeline { isWriteDepth = false; cullMethod = CullMethod.NO_CULLING }
}

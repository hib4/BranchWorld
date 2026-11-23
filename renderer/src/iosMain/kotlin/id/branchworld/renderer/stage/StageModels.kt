package id.branchworld.renderer.stage

import id.branchworld.renderer.WorldStage
import id.branchworld.renderer.assets.gltf.ClipSampler
import id.branchworld.renderer.assets.gltf.GltfModel
import id.branchworld.renderer.assets.gltf.GltfReader
import id.branchworld.renderer.assets.gltf.NodeTransform
import id.branchworld.renderer.geometry.Mat4
import id.branchworld.renderer.geometry.Point3
import id.branchworld.renderer.geometry.Rgba
import id.branchworld.renderer.geometry.SurfaceMesh

/** Hands out stage ids for meshes, materials, textures and entities. Ids are never reused within a scene. */
internal class StageIds {
    private var next = 1
    fun take() = next++
    fun reset() { next = 1 }
}

internal fun WorldStage.define(mesh: Int, surface: SurfaceMesh, texCoords: FloatArray = FloatArray(0)) =
    defineMesh(mesh, surface.positions.data(), surface.normals.data(), surface.colors.data(), texCoords.data(), surface.indices.data())

/** Reads a model the app bundles under `diorama/`. */
internal fun loadBundledModel(path: String): GltfModel {
    val bytes = BundleAssets.read(path)
    return if (path.endsWith(".glb")) GltfReader.readGlb(bytes)
        else GltfReader.readGltf(bytes.decodeToString()) { uri -> BundleAssets.read("${path.substringBeforeLast('/')}/$uri") }
}

/**
 * A glTF model uploaded once to the stage: a mesh per primitive and a material per glTF material, ready to be
 * instantiated as an entity tree that mirrors the model's nodes.
 */
internal class StageModel(
    private val stage: WorldStage,
    private val ids: StageIds,
    val model: GltfModel,
    folder: String,
    coat: Int,
    textures: MutableMap<String, Int>,
) {
    private val materials: List<Int> = model.materials.map { material ->
        val texture = material.baseColorImage?.let { model.images[it] }?.let { image ->
            val uri = image.uri
            if (uri != null) textures.getOrPut("$folder/$uri") {
                ids.take().also { stage.defineTexture(it, BundleAssets.read("$folder/$uri").data()) }
            } else ids.take().also { stage.defineTexture(it, checkNotNull(image.bytes).data()) }
        } ?: -1
        val c = material.baseColor
        ids.take().also { stage.defineMaterial(it, StageShading.Model, texture, c[0], c[1], c[2], c[3], coat) }
    }
    private val fallbackMaterial by lazy {
        ids.take().also { stage.defineMaterial(it, StageShading.Model, -1, 0.8f, 0.8f, 0.8f, 1f, coat) }
    }
    private val meshes: List<List<Pair<Int, Int>>> = model.meshes.map { mesh ->
        mesh.primitives.map { primitive ->
            val id = ids.take()
            val n = primitive.vertexCount
            // RealityKit textures made from images keep glTF's top-left origin, so uvs pass through unchanged.
            val uv = primitive.texCoords ?: FloatArray(0)
            stage.define(id, SurfaceMesh(primitive.positions, primitive.normals ?: FloatArray(n * 3), FloatArray(0), primitive.indices), uv)
            id to (primitive.material?.let { materials[it] } ?: fallbackMaterial)
        }
    }
    private val sampler = ClipSampler(model)

    /** Builds the model's entity tree under [parent]; the returned rig poses its nodes. */
    fun instantiate(parent: Int): StageRig {
        val root = ids.take()
        stage.addEntity(root, parent, -1, -1)
        val nodes = IntArray(model.nodes.size)
        fun add(node: Int, under: Int) {
            val entity = ids.take()
            nodes[node] = entity
            stage.addEntity(entity, under, -1, -1)
            model.nodes[node].mesh?.let { mesh ->
                for ((meshId, material) in meshes[mesh]) stage.addEntity(ids.take(), entity, meshId, material)
            }
            for (child in model.nodes[node].children) add(child, entity)
        }
        for (node in model.roots) add(node, root)
        return StageRig(root, nodes, this)
    }

    fun pose(clip: String, seconds: Float): List<NodeTransform> =
        sampler.pose(checkNotNull(model.clip(clip)) { "Missing clip $clip" }, seconds)

    fun duration(clip: String) = model.clip(clip)?.duration ?: 1f
}

/** One instance of a [StageModel]: its root entity and the entity standing in for each glTF node. */
internal class StageRig(val root: Int, private val nodes: IntArray, val model: StageModel) {
    fun pose(batch: TransformBatch, transforms: List<NodeTransform>) {
        transforms.forEachIndexed { node, t ->
            batch.put(nodes[node], t.tx, t.ty, t.tz, floatArrayOf(t.rx, t.ry, t.rz, t.rw), t.sx, t.sy, t.sz, visible = true)
        }
    }
}

/**
 * A static glTF model flattened into one coloured mesh per placement list: node transforms baked in, each primitive
 * painted with [paint], and vertex alpha carrying how far up the crown it sits so the stage's foliage shading sways it.
 */
internal fun bakeModel(model: GltfModel, placements: List<Mat4>, paint: (material: Int?) -> Rgba, crown: (localY: Float) -> Float): SurfaceMesh {
    val world = Array(model.nodes.size) { Mat4() }
    fun walk(node: Int, parent: Mat4) {
        val t = model.nodes[node].transform
        world[node] = parent * Mat4.trs(t.tx, t.ty, t.tz, t.rx, t.ry, t.rz, t.rw, t.sx, t.sy, t.sz)
        for (child in model.nodes[node].children) walk(child, world[node])
    }
    for (root in model.roots) walk(root, Mat4())
    val positions = ArrayList<Float>()
    val normals = ArrayList<Float>()
    val colors = ArrayList<Float>()
    val indices = ArrayList<Int>()
    var count = 0
    for (placement in placements) {
        for (root in model.roots) visit(root, model) { node ->
            val mesh = model.nodes[node].mesh ?: return@visit
            val nodeMatrix = world[node]
            for (primitive in model.meshes[mesh].primitives) {
                val color = paint(primitive.material)
                val base = count
                for (v in 0 until primitive.vertexCount) {
                    val local = nodeMatrix.point(Point3(primitive.positions[v * 3], primitive.positions[v * 3 + 1], primitive.positions[v * 3 + 2]))
                    val p = placement.point(local)
                    val n = primitive.normals?.let { placement.direction(nodeMatrix.direction(Point3(it[v * 3], it[v * 3 + 1], it[v * 3 + 2]))) }
                        ?: Point3(0f, 1f, 0f)
                    positions += p.x; positions += p.y; positions += p.z
                    normals += n.x; normals += n.y; normals += n.z
                    colors += color.r; colors += color.g; colors += color.b; colors += crown(local.y)
                    count++
                }
                for (index in primitive.indices) indices += base + index
            }
        }
    }
    return SurfaceMesh(positions.toFloatArray(), normals.toFloatArray(), colors.toFloatArray(), indices.toIntArray())
}

private fun visit(node: Int, model: GltfModel, each: (Int) -> Unit) {
    each(node)
    for (child in model.nodes[node].children) visit(child, model, each)
}

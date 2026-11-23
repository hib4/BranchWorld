package id.branchworld.renderer.assets.gltf

/**
 * A glTF 2.0 model read into plain arrays, for renderer backends without a glTF importer of their own (iOS). Covers
 * what BranchWorld's models use: triangle meshes, base-colour materials and textures, node hierarchies, skins and
 * translation / rotation / scale animation clips.
 */
internal class GltfModel(
    val nodes: List<GltfNode>,
    /** The default scene's root nodes. */
    val roots: List<Int>,
    val meshes: List<GltfMesh>,
    val materials: List<GltfMaterial>,
    val images: List<GltfImage>,
    val skins: List<GltfSkin>,
    val clips: List<GltfClip>,
) {
    fun clip(name: String): GltfClip? = clips.firstOrNull { it.name == name }

    /** Every node's parent, or -1 for a root. */
    val parents: IntArray by lazy {
        IntArray(nodes.size) { -1 }.also { parents -> nodes.forEachIndexed { index, node -> node.children.forEach { parents[it] = index } } }
    }
}

internal class GltfNode(
    val name: String?,
    val children: IntArray,
    val mesh: Int?,
    val skin: Int?,
    /** The rest pose. */
    val transform: NodeTransform,
)

/** A node's local translation, rotation quaternion (x, y, z, w) and scale. */
internal data class NodeTransform(
    val tx: Float = 0f, val ty: Float = 0f, val tz: Float = 0f,
    val rx: Float = 0f, val ry: Float = 0f, val rz: Float = 0f, val rw: Float = 1f,
    val sx: Float = 1f, val sy: Float = 1f, val sz: Float = 1f,
)

internal class GltfMesh(val name: String?, val primitives: List<GltfPrimitive>)

/** One triangle list: xyz [positions] and [normals], uv [texCoords], and optional four-joint skinning per vertex. */
internal class GltfPrimitive(
    val positions: FloatArray,
    val normals: FloatArray?,
    val texCoords: FloatArray?,
    val joints: IntArray?,
    val weights: FloatArray?,
    val indices: IntArray,
    val material: Int?,
) {
    val vertexCount get() = positions.size / 3
}

internal class GltfMaterial(
    val name: String?,
    /** Linear rgba. */
    val baseColor: FloatArray,
    /** Index into [GltfModel.images], when the base colour is textured. */
    val baseColorImage: Int?,
    val metallic: Float,
    val roughness: Float,
    val unlit: Boolean,
)

/** A texture image: a file next to the model ([uri]) or bytes embedded in a GLB ([bytes]). */
internal class GltfImage(val uri: String?, val bytes: ByteArray?, val mimeType: String?)

internal class GltfSkin(val joints: IntArray, /** Column-major 4×4 per joint. */ val inverseBindMatrices: FloatArray?)

internal enum class ChannelPath { Translation, Rotation, Scale }

internal enum class Interpolation { Linear, Step }

/** One animated property of one node: keyframe [times] and their [values] (3 or 4 floats per key). */
internal class GltfChannel(
    val node: Int,
    val path: ChannelPath,
    val interpolation: Interpolation,
    val times: FloatArray,
    val values: FloatArray,
) {
    val width get() = if (path == ChannelPath.Rotation) 4 else 3
}

internal class GltfClip(val name: String, val channels: List<GltfChannel>) {
    /** The last keyframe time over every channel, as engines measure a clip's length. */
    val duration: Float = channels.maxOfOrNull { it.times.lastOrNull() ?: 0f } ?: 0f
}

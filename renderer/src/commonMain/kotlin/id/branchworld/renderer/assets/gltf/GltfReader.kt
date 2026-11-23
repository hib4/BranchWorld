package id.branchworld.renderer.assets.gltf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reads glTF 2.0 into a [GltfModel]: binary `.glb` files, or `.gltf` JSON whose buffers [resolve] loads by uri. */
internal object GltfReader {
    private const val GlbMagic = 0x46546C67
    private const val JsonChunk = 0x4E4F534A
    private const val BinChunk = 0x004E4942

    fun readGlb(bytes: ByteArray): GltfModel {
        require(bytes.size >= 20 && bytes.int(0) == GlbMagic) { "Not a GLB file" }
        require(bytes.int(4) == 2) { "Unsupported glTF version ${bytes.int(4)}" }
        var offset = 12
        var json: String? = null
        var binary: ByteArray? = null
        while (offset + 8 <= bytes.size) {
            val length = bytes.int(offset)
            val type = bytes.int(offset + 4)
            val start = offset + 8
            when (type) {
                JsonChunk -> json = bytes.decodeToString(start, start + length)
                BinChunk -> binary = bytes.copyOfRange(start, start + length)
            }
            offset = start + length
        }
        return read(checkNotNull(json) { "GLB without a JSON chunk" }) { uri ->
            if (uri == null) checkNotNull(binary) { "GLB without a binary chunk" } else error("Unexpected external buffer $uri in a GLB")
        }
    }

    fun readGltf(json: String, resolve: (uri: String) -> ByteArray): GltfModel =
        read(json) { uri -> resolve(checkNotNull(uri) { "A .gltf buffer needs a uri" }) }

    private fun read(text: String, buffer: (uri: String?) -> ByteArray): GltfModel {
        val root = Json.parseToJsonElement(text).jsonObject
        val buffers = root.list("buffers").map { buffer(it.string("uri")) }
        val views = root.list("bufferViews").map { view ->
            BufferView(buffers[view.int("buffer") ?: 0], view.int("byteOffset") ?: 0, view.int("byteLength")!!, view.int("byteStride"))
        }
        val accessors = root.list("accessors").map { accessor ->
            Accessor(
                view = accessor.int("bufferView")?.let { views[it] },
                offset = accessor.int("byteOffset") ?: 0,
                componentType = accessor.int("componentType")!!,
                count = accessor.int("count")!!,
                width = when (val type = accessor.string("type")) {
                    "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16
                    else -> error("Unsupported accessor type $type")
                },
                normalized = accessor["normalized"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
        val images = root.list("images").map { image ->
            val bytes = image.int("bufferView")?.let { views[it].bytes() }
            GltfImage(image.string("uri"), bytes, image.string("mimeType"))
        }
        val textures = root.list("textures").map { it.int("source") }
        val materials = root.list("materials").map { material ->
            val pbr = material["pbrMetallicRoughness"]?.jsonObject
            GltfMaterial(
                name = material.string("name"),
                baseColor = pbr?.get("baseColorFactor")?.jsonArray?.map { it.jsonPrimitive.float }?.toFloatArray() ?: floatArrayOf(1f, 1f, 1f, 1f),
                baseColorImage = pbr?.get("baseColorTexture")?.jsonObject?.int("index")?.let { textures[it] },
                metallic = pbr?.float("metallicFactor") ?: 1f,
                roughness = pbr?.float("roughnessFactor") ?: 1f,
                unlit = material["extensions"]?.jsonObject?.containsKey("KHR_materials_unlit") == true,
            )
        }
        val meshes = root.list("meshes").map { mesh ->
            GltfMesh(mesh.string("name"), mesh.list("primitives").map { primitive ->
                val mode = primitive.int("mode") ?: 4
                require(mode == 4) { "Only triangle lists are supported (mode $mode)" }
                val attributes = primitive["attributes"]!!.jsonObject
                fun attribute(name: String) = attributes[name]?.jsonPrimitive?.int?.let { accessors[it] }
                val positions = attribute("POSITION")!!.floats()
                GltfPrimitive(
                    positions = positions,
                    normals = attribute("NORMAL")?.floats(),
                    texCoords = attribute("TEXCOORD_0")?.floats(),
                    joints = attribute("JOINTS_0")?.ints(),
                    weights = attribute("WEIGHTS_0")?.floats(),
                    indices = primitive.int("indices")?.let { accessors[it].ints() } ?: IntArray(positions.size / 3) { it },
                    material = primitive.int("material"),
                )
            })
        }
        val nodes = root.list("nodes").map { node ->
            require(node["matrix"] == null) { "Node matrices are not supported" }
            fun vector(name: String) = node[name]?.jsonArray?.map { it.jsonPrimitive.float }
            val t = vector("translation") ?: listOf(0f, 0f, 0f)
            val r = vector("rotation") ?: listOf(0f, 0f, 0f, 1f)
            val s = vector("scale") ?: listOf(1f, 1f, 1f)
            GltfNode(
                name = node.string("name"),
                children = node["children"]?.jsonArray?.map { it.jsonPrimitive.int }?.toIntArray() ?: IntArray(0),
                mesh = node.int("mesh"),
                skin = node.int("skin"),
                transform = NodeTransform(t[0], t[1], t[2], r[0], r[1], r[2], r[3], s[0], s[1], s[2]),
            )
        }
        val skins = root.list("skins").map { skin ->
            GltfSkin(skin["joints"]!!.jsonArray.map { it.jsonPrimitive.int }.toIntArray(),
                skin.int("inverseBindMatrices")?.let { accessors[it].floats() })
        }
        val clips = root.list("animations").mapIndexed { index, animation ->
            val samplers = animation.list("samplers")
            GltfClip(animation.string("name") ?: "animation $index", animation.list("channels").mapNotNull { channel ->
                val target = channel["target"]!!.jsonObject
                val node = target.int("node") ?: return@mapNotNull null
                val path = when (target.string("path")) {
                    "translation" -> ChannelPath.Translation
                    "rotation" -> ChannelPath.Rotation
                    "scale" -> ChannelPath.Scale
                    else -> return@mapNotNull null
                }
                val sampler = samplers[channel.int("sampler")!!]
                val interpolation = when (val mode = sampler.string("interpolation") ?: "LINEAR") {
                    "LINEAR" -> Interpolation.Linear
                    "STEP" -> Interpolation.Step
                    else -> error("Unsupported interpolation $mode")
                }
                GltfChannel(node, path, interpolation, accessors[sampler.int("input")!!].floats(), accessors[sampler.int("output")!!].floats())
            })
        }
        val scene = root.int("scene") ?: 0
        val roots = root.list("scenes").getOrNull(scene)?.get("nodes")?.jsonArray?.map { it.jsonPrimitive.int }
            ?: nodes.indices.filter { candidate -> nodes.none { candidate in it.children } }
        return GltfModel(nodes, roots, meshes, materials, images, skins, clips)
    }

    private class BufferView(val buffer: ByteArray, val offset: Int, val length: Int, val stride: Int?) {
        fun bytes() = buffer.copyOfRange(offset, offset + length)
    }

    private class Accessor(val view: BufferView?, val offset: Int, val componentType: Int, val count: Int, val width: Int, val normalized: Boolean) {
        private val componentSize = when (componentType) {
            5120, 5121 -> 1
            5122, 5123 -> 2
            5125, 5126 -> 4
            else -> error("Unsupported component type $componentType")
        }

        private inline fun read(each: (bytes: ByteArray, at: Int) -> Unit) {
            val view = view ?: return
            val stride = view.stride ?: (componentSize * width)
            val base = view.offset + offset
            for (element in 0 until count) {
                for (component in 0 until width) each(view.buffer, base + element * stride + component * componentSize)
            }
        }

        fun floats(): FloatArray {
            val out = FloatArray(count * width)
            var i = 0
            read { bytes, at ->
                out[i++] = when (componentType) {
                    5126 -> Float.fromBits(bytes.int(at))
                    5121 -> (bytes[at].toInt() and 0xff).let { if (normalized) it / 255f else it.toFloat() }
                    5123 -> bytes.ushort(at).let { if (normalized) it / 65535f else it.toFloat() }
                    5120 -> bytes[at].toInt().let { if (normalized) maxOf(it / 127f, -1f) else it.toFloat() }
                    5122 -> bytes.ushort(at).toShort().toInt().let { if (normalized) maxOf(it / 32767f, -1f) else it.toFloat() }
                    else -> bytes.int(at).toFloat()
                }
            }
            return out
        }

        fun ints(): IntArray {
            val out = IntArray(count * width)
            var i = 0
            read { bytes, at ->
                out[i++] = when (componentType) {
                    5121 -> bytes[at].toInt() and 0xff
                    5123 -> bytes.ushort(at)
                    5125 -> bytes.int(at)
                    else -> error("Integer data cannot use component type $componentType")
                }
            }
            return out
        }
    }
}

private fun ByteArray.int(at: Int) =
    (this[at].toInt() and 0xff) or ((this[at + 1].toInt() and 0xff) shl 8) or
        ((this[at + 2].toInt() and 0xff) shl 16) or ((this[at + 3].toInt() and 0xff) shl 24)

private fun ByteArray.ushort(at: Int) = (this[at].toInt() and 0xff) or ((this[at + 1].toInt() and 0xff) shl 8)

private fun JsonObject.list(name: String): List<JsonObject> = (this[name] as? JsonArray)?.map { it.jsonObject } ?: emptyList()
private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.int
private fun JsonObject.float(name: String): Float? = this[name]?.jsonPrimitive?.float
private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.content

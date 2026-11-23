package id.branchworld.renderer.assets.gltf

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The glTF reader and sampler on a GLB built in the test, so every target (iOS too) checks the same bytes. */
class GltfReaderTest {
    private fun glb(): ByteArray {
        val bin = Bytes()
        // 0: three positions, 36 bytes
        listOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f).forEach(bin::float)
        // 36: three normals
        repeat(3) { listOf(0f, 1f, 0f).forEach(bin::float) }
        // 72: indices as unsigned shorts, padded to 4 bytes
        listOf(0, 2, 1).forEach(bin::short); bin.short(0)
        // 80: key times
        listOf(0f, 1f).forEach(bin::float)
        // 88: rotations: none, then a half turn about Y
        listOf(0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f).forEach(bin::float)
        val json = """
            {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],
             "nodes":[{"name":"root","children":[1]},{"name":"body","mesh":0,"translation":[0,0.5,0]}],
             "meshes":[{"name":"tri","primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2,"material":0}]}],
             "materials":[{"name":"paint","pbrMetallicRoughness":{"baseColorFactor":[0.5,0.25,1,1],"metallicFactor":0,"roughnessFactor":0.8}}],
             "buffers":[{"byteLength":${bin.size}}],
             "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":72},{"buffer":0,"byteOffset":72,"byteLength":8},
                            {"buffer":0,"byteOffset":80,"byteLength":40}],
             "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                          {"bufferView":0,"byteOffset":36,"componentType":5126,"count":3,"type":"VEC3"},
                          {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"},
                          {"bufferView":2,"componentType":5126,"count":2,"type":"SCALAR"},
                          {"bufferView":2,"byteOffset":8,"componentType":5126,"count":2,"type":"VEC4"}],
             "animations":[{"name":"turn","samplers":[{"input":3,"output":4,"interpolation":"LINEAR"}],
                            "channels":[{"sampler":0,"target":{"node":1,"path":"rotation"}}]}]}
        """.trimIndent()
        val jsonBytes = json.encodeToByteArray().let { it + ByteArray((4 - it.size % 4) % 4) { 0x20 } }
        val out = Bytes()
        out.int(0x46546C67); out.int(2); out.int(12 + 8 + jsonBytes.size + 8 + bin.size)
        out.int(jsonBytes.size); out.int(0x4E4F534A); out.bytes(jsonBytes)
        out.int(bin.size); out.int(0x004E4942); out.bytes(bin.toByteArray())
        return out.toByteArray()
    }

    @Test
    fun readsMeshesMaterialsNodesAndClips() {
        val model = GltfReader.readGlb(glb())
        assertEquals(listOf(0), model.roots)
        assertEquals(listOf(-1, 0), model.parents.toList())
        val primitive = model.meshes.single().primitives.single()
        assertContentEquals(intArrayOf(0, 2, 1), primitive.indices)
        assertContentEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f), primitive.positions)
        assertEquals(1f, primitive.normals!![1])
        val material = model.materials.single()
        assertContentEquals(floatArrayOf(0.5f, 0.25f, 1f, 1f), material.baseColor)
        assertEquals(0.8f, material.roughness)
        assertEquals(0.5f, model.nodes[1].transform.ty)
        assertEquals(1f, model.clip("turn")!!.duration)
    }

    @Test
    fun samplesRotationsAlongTheShortestArcAndClampsPastTheEnds() {
        val model = GltfReader.readGlb(glb())
        val sampler = ClipSampler(model)
        val clip = model.clip("turn")!!
        val half = sampler.pose(clip, 0.5f)[1]
        // Halfway through a half turn about Y is a quarter turn: sin(45°) on both y and w.
        val quarter = sqrt(0.5f)
        assertEquals(quarter, half.ry, 1e-5f)
        assertEquals(quarter, half.rw, 1e-5f)
        assertEquals(0.5f, half.ty, "Untouched properties keep the rest pose")
        assertEquals(1f, sampler.pose(clip, 3f)[1].ry, 1e-6f)
        assertEquals(1f, sampler.pose(clip, -1f)[1].rw, 1e-6f)
    }

    @Test
    fun rejectsWhatIsNotAGlb() {
        assertFailsWith<IllegalArgumentException> { GltfReader.readGlb(ByteArray(24)) }
    }

    private class Bytes {
        private val data = ArrayList<Byte>()
        val size get() = data.size
        fun int(v: Int) = repeat(4) { data += (v shr (it * 8)).toByte() }
        fun short(v: Int) = repeat(2) { data += (v shr (it * 8)).toByte() }
        fun float(v: Float) = int(v.toRawBits())
        fun bytes(b: ByteArray) = b.forEach { data += it }
        fun toByteArray() = data.toByteArray()
    }
}

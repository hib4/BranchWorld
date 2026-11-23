package id.branchworld.renderer.assets.gltf

import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.KoolSystem
import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.gltf.GltfFile
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.util.Uint8BufferImpl
import id.branchworld.renderer.assets.CreatureClips
import id.branchworld.renderer.assets.DioramaAsset
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The shared glTF reader against every model the game ships, and its clip sampler against Kool's own animation. */
class GltfReaderAssetsTest {
    private val folder = File("src/commonMain/resources/diorama")

    private fun read(file: File): GltfModel = if (file.extension == "glb") GltfReader.readGlb(file.readBytes())
        else GltfReader.readGltf(file.readText()) { uri -> File(file.parentFile, uri).readBytes() }

    @Test
    fun everyShippedModelReadsIntoWellFormedTriangles() {
        val files = folder.listFiles { f -> f.extension == "glb" || f.extension == "gltf" }!!.sortedBy { it.name }
        assertTrue(files.size >= 25)
        for (file in files) {
            val model = read(file)
            assertTrue(model.roots.isNotEmpty(), file.name)
            assertTrue(model.meshes.isNotEmpty(), file.name)
            for (primitive in model.meshes.flatMap { it.primitives }) {
                assertEquals(0, primitive.indices.size % 3, file.name)
                assertTrue(primitive.indices.all { it in 0 until primitive.vertexCount }, file.name)
                assertEquals(primitive.vertexCount * 3, primitive.normals?.size, file.name)
                primitive.texCoords?.let { assertEquals(primitive.vertexCount * 2, it.size, file.name) }
                primitive.material?.let { assertTrue(it in model.materials.indices, file.name) }
            }
            for (image in model.images) {
                if (image.uri != null) assertTrue(File(file.parentFile, image.uri).isFile, "${file.name} ${image.uri}")
                else assertTrue(image.bytes!!.isNotEmpty(), file.name)
            }
            for (skin in model.skins) {
                assertEquals(skin.joints.size * 16, skin.inverseBindMatrices?.size, file.name)
                val primitive = model.meshes.flatMap { it.primitives }.first { it.joints != null }
                assertTrue(primitive.joints!!.all { it in skin.joints.indices }, file.name)
            }
        }
    }

    @Test
    fun animalsCarryEveryClipTheMotionPlaysAndTheirColourMap() {
        for (asset in listOf(DioramaAsset.Bunny, DioramaAsset.Deer, DioramaAsset.Fox, DioramaAsset.Wolf)) {
            val model = read(File(folder, asset.file))
            for (clip in CreatureClips) assertTrue(assertNotNull(model.clip(clip), "$asset $clip").duration > 0f, "$asset $clip")
            assertEquals("Textures/colormap.png", model.images.single().uri)
            assertEquals(0, model.materials.single().baseColorImage)
        }
    }

    @Test
    fun samplingAtAKeyframeReturnsThatKeyframe() {
        val model = read(File(folder, DioramaAsset.Fox.file))
        val values = FloatArray(4)
        for (clip in model.clips) for (channel in clip.channels) for (key in channel.times.indices) {
            ClipSampler.sample(channel, channel.times[key], values)
            for (i in 0 until channel.width) assertEquals(channel.values[key * channel.width + i], values[i], 1e-5f)
        }
    }

    @Test
    fun clipPosesMatchKoolsAnimationOfTheSameModel() {
        if (!KoolSystem.isInitialized) KoolSystem.initialize(KoolConfigJvm())
        for (asset in listOf(DioramaAsset.Bunny, DioramaAsset.Wolf)) {
            val bytes = File(folder, asset.file).readBytes()
            val ours = GltfReader.readGlb(bytes)
            val sampler = ClipSampler(ours)
            val kool = runBlocking { GltfFile(Uint8BufferImpl(ByteBuffer.wrap(bytes)), asset.path).getOrThrow() }
                .makeModel(GltfLoadConfig(loadAnimations = true, applyTransforms = false, mergeMeshesByMaterial = false))
            for (clipName in CreatureClips) {
                val clip = ours.clip(clipName)!!
                val index = kool.animations.indexOfFirst { it.name == clipName && it.duration > 0f }
                assertEquals(clip.duration, kool.animations[index].duration, 1e-5f)
                kool.enableAnimation(index)
                for (step in 0..8) {
                    val time = clip.duration * step / 8f * 0.999f
                    kool.animations[index].progress = time
                    kool.applyAnimation(0f)
                    val pose = sampler.pose(clip, time)
                    var compared = 0
                    ours.nodes.forEachIndexed { node, gltfNode ->
                        val name = gltfNode.name ?: return@forEachIndexed
                        val koolNode = kool.nodes[name] ?: return@forEachIndexed
                        compared++
                        val expected = koolNode.transform.matrixF
                        val actual = pose[node].matrix()
                        for (row in 0 until 4) for (col in 0 until 4) {
                            val (e, a) = expected[row, col] to actual[row, col]
                            assertTrue(abs(e - a) < 2e-4f, "$asset $clipName t=$time node $name [$row,$col]: $e vs $a")
                        }
                    }
                    // The body, four legs and the animated root must all have been checked.
                    assertTrue(compared >= 6, "$asset compared only $compared nodes")
                }
            }
        }
    }

    private fun NodeTransform.matrix() = MutableMat4f()
        .translate(tx, ty, tz)
        .rotate(QuatF(rx, ry, rz, rw))
        .scale(Vec3f(sx, sy, sz))
}

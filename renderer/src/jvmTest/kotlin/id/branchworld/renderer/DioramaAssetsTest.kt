package id.branchworld.renderer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DioramaAssetsTest {
    @Test
    fun completeGlbSetAndRelativeResourcesAreBundled() {
        for (asset in DioramaAsset.entries) {
            val bytes = resource(asset.path)
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0x46546C67, header.int, asset.path)
            assertEquals(2, header.int, asset.path)
            assertEquals(bytes.size, header.int, asset.path)
            val jsonSize = header.int
            assertEquals(0x4E4F534A, header.int, asset.path)
            val metadata = bytes.decodeToString(startIndex = header.position(), endIndex = header.position() + jsonSize)
            for (reference in uri.findAll(metadata)) {
                val path = "${asset.path.substringBeforeLast('/')}/${reference.groupValues[1]}"
                assertTrue(resource(path).isNotEmpty(), path)
            }
            if (asset == DioramaAsset.Bunny) {
                assertTrue(Regex(""""name"\s*:\s*"$BunnyAnimation"""").containsMatchIn(metadata))
                assertTrue(uri.findAll(metadata).any { it.groupValues[1] == "Textures/colormap.png" })
            }
        }
    }

    private fun resource(path: String): ByteArray =
        assertNotNull(javaClass.classLoader.getResourceAsStream(path), path).use { it.readBytes() }

    private companion object {
        val uri = Regex(""""uri"\s*:\s*"([^"]+)"""")
    }
}

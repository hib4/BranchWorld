package id.branchworld.game

import id.branchworld.game.save.SaveFileTooLarge
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopWorldSaveStoreTest {
    @Test
    fun concurrentFirstSaveRequiresOneWriterToConfirmReplacement() {
        val directory = createTempDirectory("branchworld-save-")
        try {
            runBlocking {
                val first = DesktopWorldSaveStore(directory)
                val second = DesktopWorldSaveStore(directory)
                assertNull(first.read(16))
                val legacy = directory.resolve("island-v1.json")
                Files.write(legacy, byteArrayOf(8))
                assertContentEquals(byteArrayOf(8), second.read(16))
                val a = async { first.write(byteArrayOf(1), replace = false) }
                val b = async { second.write(byteArrayOf(2), replace = false) }
                val outcomes = listOf(a.await(), b.await())
                assertEquals(1, outcomes.count { it })
                assertContentEquals(byteArrayOf(if (outcomes[0]) 1 else 2), second.read(16))
                // A temporary file left by an interrupted save cannot replace the committed slot.
                Files.write(directory.resolve(".island-interrupted.tmp"), byteArrayOf(9))
                assertTrue(first.write(byteArrayOf(3, 4), replace = true))
                assertContentEquals(byteArrayOf(3, 4), DesktopWorldSaveStore(directory).read(16))
                assertContentEquals(byteArrayOf(8), Files.readAllBytes(legacy))
                assertFailsWith<SaveFileTooLarge> { second.read(1) }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}

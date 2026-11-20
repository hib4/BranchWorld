package id.branchworld.game

import androidx.test.platform.app.InstrumentationRegistry
import id.branchworld.game.save.SaveFileTooLarge
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidWorldSaveStoreTest {
    @Test
    fun unfinishedWritePreservesCommittedSlot() {
        val root = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val directory = File(root, "world-save-test-${System.nanoTime()}").apply { check(mkdir()) }
        try {
            runBlocking {
                val store = AndroidWorldSaveStore(directory)
                val original = byteArrayOf(1, 2, 3)
                assertNull(store.read(16))
                val legacy = File(directory, "island-v1.json").apply { writeBytes(byteArrayOf(8)) }
                assertArrayEquals(byteArrayOf(8), store.read(16))
                assertTrue(store.write(original, replace = false))
                assertFalse(store.write(byteArrayOf(5), replace = false))
                File(directory, "island-v3.json.new").writeBytes(byteArrayOf(7))
                val reopened = AndroidWorldSaveStore(directory)
                assertArrayEquals(original, reopened.read(16))
                assertTrue(reopened.write(byteArrayOf(5), replace = true))
                assertArrayEquals(byteArrayOf(5), store.read(16))
                assertArrayEquals(byteArrayOf(8), legacy.readBytes())
            }
            assertThrows(SaveFileTooLarge::class.java) {
                runBlocking { AndroidWorldSaveStore(directory).read(0) }
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.branchworld.ios.storage

import id.branchworld.game.platform.storage.SaveFileTooLarge
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** iOS file storage under the shared save layout, in a scratch folder. */
class IosFileStorageTest {
    private val folder = NSTemporaryDirectory() + "branchworld-test-" + NSUUID().UUIDString
    private val storage = IosFileStorage(folder)

    @AfterTest
    fun clean() {
        NSFileManager.defaultManager.removeItemAtPath(folder, null)
    }

    @Test
    fun writesReadsAndRefusesToOverwriteWhenCreating() = runTest {
        assertNull(storage.read("worlds/a.json", 100))
        assertTrue(storage.write("worlds/a.json", byteArrayOf(1, 2, 3), replace = false))
        assertContentEquals(byteArrayOf(1, 2, 3), storage.read("worlds/a.json", 100))
        assertFalse(storage.write("worlds/a.json", byteArrayOf(9), replace = false))
        assertContentEquals(byteArrayOf(1, 2, 3), storage.read("worlds/a.json", 100))
        assertTrue(storage.write("worlds/a.json", byteArrayOf(4), replace = true))
        assertContentEquals(byteArrayOf(4), storage.read("worlds/a.json", 100))
    }

    @Test
    fun refusesFilesAboveTheLimit() = runTest {
        storage.write("island-v3.json", ByteArray(64), replace = true)
        assertFailsWith<SaveFileTooLarge> { storage.read("island-v3.json", 10) }
        assertEquals(64, storage.read("island-v3.json", 64)?.size)
    }

    @Test
    fun savesAWorldThroughTheSharedLayout() = runTest {
        val store = IosWorldSaveStore(folder)
        assertTrue(store.createFile("world-1.json", "{}".encodeToByteArray()))
        assertEquals("{}", store.readFile("world-1.json", 100)?.decodeToString())
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath("$folder/worlds/world-1.json"))
    }
}

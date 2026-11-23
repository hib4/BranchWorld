package id.branchworld.game.platform

import id.branchworld.game.platform.storage.BlobStorage
import id.branchworld.game.platform.storage.KeyedWorldSaveStore
import id.branchworld.game.platform.storage.SaveFileTooLarge
import id.branchworld.game.runSimulationTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyedWorldSaveStoreTest {
    private class MemoryStorage : BlobStorage {
        val files = linkedMapOf<String, ByteArray>()

        override suspend fun read(key: String, maxBytes: Int): ByteArray? = files[key]

        override suspend fun write(key: String, bytes: ByteArray, replace: Boolean): Boolean {
            if (!replace && key in files) return false
            files[key] = bytes.copyOf()
            return true
        }
    }

    @Test
    fun namedFilesLiveInTheWorldsFolderAndCreateNeverReplaces() = runSimulationTest {
        val storage = MemoryStorage()
        val store = KeyedWorldSaveStore(storage)
        assertTrue(store.createFile("world-1.json", byteArrayOf(1)))
        assertFalse(store.createFile("world-1.json", byteArrayOf(2)))
        assertContentEquals(byteArrayOf(1), store.readFile("world-1.json", 16))
        assertTrue(store.writeFile("world-1.json", byteArrayOf(3)))
        assertContentEquals(byteArrayOf(3), storage.files["worlds/world-1.json"])
        assertNull(store.readFile("world-2.json", 16))
        assertEquals(listOf("worlds/world-1.json"), storage.files.keys.toList())
    }

    @Test
    fun theOriginalSlotFallsBackToEarlierVersionsAndIsSizeChecked() = runSimulationTest {
        val storage = MemoryStorage()
        val store = KeyedWorldSaveStore(storage)
        assertNull(store.read(16))
        storage.files["island-v1.json"] = byteArrayOf(1)
        assertContentEquals(byteArrayOf(1), store.read(16))
        storage.files["island-v2.json"] = byteArrayOf(2)
        assertContentEquals(byteArrayOf(2), store.read(16))
        assertTrue(store.write(byteArrayOf(3, 3), replace = false))
        assertFalse(store.write(byteArrayOf(4), replace = false))
        assertContentEquals(byteArrayOf(3, 3), store.read(16))
        assertFailsWith<SaveFileTooLarge> { store.read(1) }
    }

    @Test
    fun namesOutsideTheSafeAlphabetAreRefused() = runSimulationTest {
        val store = KeyedWorldSaveStore(MemoryStorage())
        for (name in listOf("../profile.json", "World.json", "world.txt", "worlds/world-1.json")) {
            assertFailsWith<IllegalArgumentException> { store.readFile(name, 16) }
            assertFailsWith<IllegalArgumentException> { store.createFile(name, byteArrayOf(1)) }
        }
    }
}

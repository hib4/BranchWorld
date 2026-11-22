package id.branchworld.game

import android.util.AtomicFile
import id.branchworld.game.save.SaveFileName
import id.branchworld.game.save.SaveFileTooLarge
import id.branchworld.game.save.WorldSaveStore
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class AndroidWorldSaveStore(directory: File) : WorldSaveStore {
    private val slot = AtomicFile(File(directory, "island-v3.json"))
    private val legacy = listOf(2, 1).map { AtomicFile(File(directory, "island-v$it.json")) }

    override suspend fun read(maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        access.withLock {
            read(slot, maxBytes) ?: legacy.firstNotNullOfOrNull { read(it, maxBytes) }
        }
    }

    override suspend fun write(bytes: ByteArray, replace: Boolean): Boolean = withContext(Dispatchers.IO) {
        access.withLock { commit(bytes, replace) }
    }

    private fun commit(bytes: ByteArray, replace: Boolean): Boolean {
        if (!replace && slot.existsOnDisk()) return false
        val stream = slot.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            slot.finishWrite(stream)
        } catch (error: Exception) {
            slot.failWrite(stream)
            throw error
        }
        // AtomicFile logs rename failures. Verify the resulting commit before reporting success.
        check(slot.openRead().use { it.channel.size() == bytes.size.toLong() && it.readBytes().contentEquals(bytes) })
        return true
    }

    private fun read(file: AtomicFile, maxBytes: Int): ByteArray? = try {
        file.openRead().use { input ->
            if (input.channel.size() > maxBytes) throw SaveFileTooLarge()
            input.readBytes()
        }
    } catch (error: FileNotFoundException) {
        if (file.existsOnDisk()) throw error
        null
    }

    private val worlds = File(directory, "worlds")

    override suspend fun readFile(name: String, maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        require(SaveFileName.matches(name))
        access.withLock { read(AtomicFile(File(worlds, name)), maxBytes) }
    }

    override suspend fun writeFile(name: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        require(SaveFileName.matches(name))
        access.withLock {
            worlds.mkdirs()
            val file = AtomicFile(File(worlds, name))
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                stream.fd.sync()
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
            check(file.openRead().use { it.channel.size() == bytes.size.toLong() && it.readBytes().contentEquals(bytes) })
        }
        true
    }

    private fun AtomicFile.existsOnDisk() =
        baseFile.exists() || File(baseFile.parentFile, "${baseFile.name}.bak").exists()

    companion object {
        private val access = Mutex()
    }
}

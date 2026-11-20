package id.branchworld.game

import android.util.AtomicFile
import id.branchworld.game.save.SaveFileTooLarge
import id.branchworld.game.save.WorldSaveStore
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class AndroidWorldSaveStore(directory: File) : WorldSaveStore {
    private val slot = File(directory, "island-v1.json")
    private val atomic = AtomicFile(slot)

    override suspend fun read(maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        access.withLock {
            try {
                atomic.openRead().use { input ->
                    if (input.channel.size() > maxBytes) throw SaveFileTooLarge()
                    input.readBytes()
                }
            } catch (error: FileNotFoundException) {
                if (exists()) throw error
                null
            }
        }
    }

    override suspend fun write(bytes: ByteArray, replace: Boolean): Boolean = withContext(Dispatchers.IO) {
        access.withLock { commit(bytes, replace) }
    }

    private fun commit(bytes: ByteArray, replace: Boolean): Boolean {
        if (!replace && exists()) return false
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
        // AtomicFile logs rename failures. Verify the resulting commit before reporting success.
        check(atomic.openRead().use { it.channel.size() == bytes.size.toLong() && it.readBytes().contentEquals(bytes) })
        return true
    }

    private fun exists() = slot.exists() || File(slot.parentFile, "${slot.name}.bak").exists()

    companion object {
        private val access = Mutex()
    }
}

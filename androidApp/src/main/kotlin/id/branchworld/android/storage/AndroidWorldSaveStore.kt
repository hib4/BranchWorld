package id.branchworld.android.storage

import android.util.AtomicFile
import id.branchworld.game.platform.storage.BlobStorage
import id.branchworld.game.platform.storage.KeyedWorldSaveStore
import id.branchworld.game.platform.storage.SaveFileTooLarge
import id.branchworld.game.platform.storage.WorldSaveStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/** World saves in the app's private files directory. */
internal class AndroidWorldSaveStore(directory: File) : WorldSaveStore by KeyedWorldSaveStore(AndroidFileStorage(directory))

/**
 * Whole files under [directory], written through [AtomicFile] with an fsync, and read back before a write reports
 * success. One lock serializes every read and write in the process.
 */
internal class AndroidFileStorage(private val directory: File) : BlobStorage {
    override suspend fun read(key: String, maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        access.withLock { read(file(key), maxBytes) }
    }

    override suspend fun write(key: String, bytes: ByteArray, replace: Boolean): Boolean = withContext(Dispatchers.IO) {
        access.withLock {
            val file = file(key)
            file.baseFile.parentFile?.mkdirs()
            if (!replace && file.existsOnDisk()) return@withContext false
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                stream.fd.sync()
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
            // AtomicFile logs rename failures. Verify the resulting commit before reporting success.
            check(file.openRead().use { it.channel.size() == bytes.size.toLong() && it.readBytes().contentEquals(bytes) })
            true
        }
    }

    private fun file(key: String) = AtomicFile(File(directory, key))

    private fun read(file: AtomicFile, maxBytes: Int): ByteArray? = try {
        file.openRead().use { input ->
            if (input.channel.size() > maxBytes) throw SaveFileTooLarge()
            input.readBytes()
        }
    } catch (error: FileNotFoundException) {
        if (file.existsOnDisk()) throw error
        null
    }

    private fun AtomicFile.existsOnDisk() =
        baseFile.exists() || File(baseFile.parentFile, "${baseFile.name}.bak").exists()

    private companion object {
        val access = Mutex()
    }
}

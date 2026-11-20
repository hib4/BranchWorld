package id.branchworld.game

import id.branchworld.game.save.SaveFileTooLarge
import id.branchworld.game.save.WorldSaveStore
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class DesktopWorldSaveStore(private val directory: Path = applicationDataDirectory()) : WorldSaveStore {
    private val slot = directory.resolve("island-v1.json")

    override suspend fun read(maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            Files.newInputStream(slot).use { input ->
                input.readNBytes(maxBytes + 1).also { if (it.size > maxBytes) throw SaveFileTooLarge() }
            }
        } catch (_: NoSuchFileException) {
            null
        }
    }

    override suspend fun write(bytes: ByteArray, replace: Boolean): Boolean = withContext(Dispatchers.IO) {
        writer.withLock {
            Files.createDirectories(directory)
            // A second application process must honor the same overwrite check.
            FileChannel.open(directory.resolve(".island.lock"), CREATE, WRITE).use { channel ->
                channel.lock().use { commit(bytes, replace) }
            }
        }
    }

    private fun commit(bytes: ByteArray, replace: Boolean): Boolean {
        if (!replace && Files.exists(slot)) return false
        val temporary = Files.createTempFile(directory, ".island-", ".tmp")
        try {
            temporary.toFile().outputStream().use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            Files.move(temporary, slot, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
        return true
    }

    companion object {
        private val writer = Mutex()
    }
}

private fun applicationDataDirectory(): Path {
    val home = Path.of(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    val parent = when {
        os.startsWith("mac") -> home.resolve("Library/Application Support")
        os.startsWith("windows") -> System.getenv("LOCALAPPDATA")?.let(Path::of) ?: home.resolve("AppData/Local")
        else -> System.getenv("XDG_DATA_HOME")?.let(Path::of)?.takeIf(Path::isAbsolute) ?: home.resolve(".local/share")
    }
    return parent.resolve("BranchWorld")
}

package id.branchworld.desktop.storage

import id.branchworld.game.platform.storage.BlobStorage
import id.branchworld.game.platform.storage.KeyedWorldSaveStore
import id.branchworld.game.platform.storage.SaveFileTooLarge
import id.branchworld.game.platform.storage.WorldSaveStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

/** World saves in the user's application data folder. Verification runs may redirect it with -Dbranchworld.dataDir. */
internal class DesktopWorldSaveStore(directory: Path = applicationDataDirectory()) :
    WorldSaveStore by KeyedWorldSaveStore(DesktopFileStorage(directory))

/**
 * Whole files under [directory]. Each write goes to a synced temporary file that atomically replaces the target, under
 * a file lock so a second BranchWorld process honors the same create-only check.
 */
internal class DesktopFileStorage(private val directory: Path) : BlobStorage {
    override suspend fun read(key: String, maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            Files.newInputStream(directory.resolve(key)).use { input ->
                input.readNBytes(maxBytes + 1).also { if (it.size > maxBytes) throw SaveFileTooLarge() }
            }
        } catch (_: NoSuchFileException) {
            null
        }
    }

    override suspend fun write(key: String, bytes: ByteArray, replace: Boolean): Boolean = withContext(Dispatchers.IO) {
        val target = directory.resolve(key)
        writer.withLock {
            Files.createDirectories(target.parent)
            FileChannel.open(directory.resolve(".island.lock"), CREATE, WRITE).use { channel ->
                channel.lock().use {
                    if (!replace && Files.exists(target, NOFOLLOW_LINKS)) return@withContext false
                    commit(target, bytes)
                }
            }
        }
        true
    }

    private fun commit(target: Path, bytes: ByteArray) {
        val temporary = Files.createTempFile(target.parent, ".save-", ".tmp")
        try {
            temporary.toFile().outputStream().use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private companion object {
        val writer = Mutex()
    }
}

private fun applicationDataDirectory(): Path {
    System.getProperty("branchworld.dataDir")?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }
    val home = Path.of(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    val parent = when {
        os.startsWith("mac") -> home.resolve("Library/Application Support")
        os.startsWith("windows") -> System.getenv("LOCALAPPDATA")?.let(Path::of) ?: home.resolve("AppData/Local")
        else -> System.getenv("XDG_DATA_HOME")?.let(Path::of)?.takeIf(Path::isAbsolute) ?: home.resolve(".local/share")
    }
    return parent.resolve("BranchWorld")
}

package id.branchworld.game

import id.branchworld.game.save.SaveFileName
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
    // Verification runs may point the game at a scratch folder with -Dbranchworld.dataDir.
    private val slot = directory.resolve("island-v3.json")
    private val legacy = listOf(2, 1).map { directory.resolve("island-v$it.json") }

    override suspend fun read(maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        read(slot, maxBytes) ?: legacy.firstNotNullOfOrNull { read(it, maxBytes) }
    }

    private fun read(file: Path, maxBytes: Int): ByteArray? = try {
        Files.newInputStream(file).use { input ->
            input.readNBytes(maxBytes + 1).also { if (it.size > maxBytes) throw SaveFileTooLarge() }
        }
    } catch (_: NoSuchFileException) {
        null
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

    private val worlds = directory.resolve("worlds")

    override suspend fun readFile(name: String, maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        require(SaveFileName.matches(name))
        read(worlds.resolve(name), maxBytes)
    }

    override suspend fun writeFile(name: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        require(SaveFileName.matches(name))
        writer.withLock {
            Files.createDirectories(worlds)
            FileChannel.open(directory.resolve(".island.lock"), CREATE, WRITE).use { channel ->
                channel.lock().use { replace(worlds.resolve(name), bytes) }
            }
        }
        true
    }

    private fun replace(target: Path, bytes: ByteArray) {
        val temporary = Files.createTempFile(target.parent, ".world-", ".tmp")
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

    companion object {
        private val writer = Mutex()
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

package id.branchworld.desktop.photo

import id.branchworld.game.platform.photo.PhotoMeta
import id.branchworld.game.platform.photo.PhotoResult
import id.branchworld.game.platform.photo.PhotoSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.UUID

/** Photos go to ~/Pictures/BranchWorld, or ~/BranchWorld/Photos when Pictures cannot be written. */
internal object DesktopPhotos : PhotoSaver {
    override suspend fun save(png: ByteArray, photo: PhotoMeta): PhotoResult = withContext(Dispatchers.IO) {
        PhotoResult.Saved(saveDesktopPhoto(png, photo).parent.photoLocation())
    }
}

internal fun saveDesktopPhoto(
    png: ByteArray,
    photo: PhotoMeta,
    pictures: Path = photoHome.resolve("Pictures/BranchWorld"),
    fallback: Path = photoHome.resolve("BranchWorld/Photos"),
): Path {
    val filename = photo.fileName(UUID.randomUUID().toString())
    return try {
        writePng(pictures, filename, png)
    } catch (_: IOException) {
        writePng(fallback, filename, png)
    }
}

internal fun Path.photoLocation(): String =
    if (startsWith(photoHome)) "~/${photoHome.relativize(this)}" else toString()

private val photoHome: Path get() = Path.of(System.getProperty("user.home"))

private fun writePng(directory: Path, filename: String, png: ByteArray): Path {
    Files.createDirectories(directory)
    val temporary = Files.createTempFile(directory, ".branchworld-photo-", ".tmp")
    val target = directory.resolve(filename)
    try {
        temporary.toFile().outputStream().use { stream ->
            stream.write(png)
            stream.fd.sync()
        }
        Files.move(temporary, target, ATOMIC_MOVE)
        return target
    } finally {
        Files.deleteIfExists(temporary)
    }
}

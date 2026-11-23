@file:OptIn(ExperimentalForeignApi::class)

package id.branchworld.ios.storage

import id.branchworld.game.platform.storage.BlobStorage
import id.branchworld.game.platform.storage.KeyedWorldSaveStore
import id.branchworld.game.platform.storage.SaveFileTooLarge
import id.branchworld.game.platform.storage.WorldSaveStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/** World saves in the app's Application Support folder, in the same layout every platform uses. */
internal class IosWorldSaveStore(directory: String = applicationSupport()) : WorldSaveStore by KeyedWorldSaveStore(IosFileStorage(directory))

/**
 * Whole files under [directory]. Writes go through Foundation's atomic write (a temporary file renamed over the
 * target), one at a time, so a save is either complete or untouched.
 */
internal class IosFileStorage(private val directory: String) : BlobStorage {
    private val files = NSFileManager.defaultManager

    override suspend fun read(key: String, maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        val path = "$directory/$key"
        if (!files.fileExistsAtPath(path)) return@withContext null
        val size = (files.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
        if (size > maxBytes) throw SaveFileTooLarge()
        NSData.dataWithContentsOfFile(path)?.toByteArray()
    }

    override suspend fun write(key: String, bytes: ByteArray, replace: Boolean): Boolean = withContext(Dispatchers.IO) {
        val path = "$directory/$key"
        writer.withLock {
            files.createDirectoryAtPath(path.substringBeforeLast('/'), withIntermediateDirectories = true, attributes = null, error = null)
            if (!replace && files.fileExistsAtPath(path)) return@withContext false
            check(bytes.toNSData().writeToFile(path, atomically = true)) { "Could not write $key" }
        }
        true
    }

    private companion object {
        val writer = Mutex()
    }
}

private fun applicationSupport(): String {
    val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
    return "$base/BranchWorld"
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { out -> out.usePinned { memcpy(it.addressOf(0), bytes, length) } }
}

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }

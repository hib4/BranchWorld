package id.branchworld.game.platform.storage

/**
 * The save layout every platform shares, over any [BlobStorage]:
 * - `island-v3.json` is the original single slot, with `island-v2.json` and `island-v1.json` read as fallbacks;
 * - `worlds/<name>` holds each named file (island histories and the profile), with names checked against
 *   [SaveFileName].
 */
class KeyedWorldSaveStore(private val storage: BlobStorage) : WorldSaveStore {
    override suspend fun read(maxBytes: Int): ByteArray? =
        (listOf(SlotKey) + LegacySlotKeys).firstNotNullOfOrNull { storage.read(it, maxBytes)?.within(maxBytes) }

    override suspend fun write(bytes: ByteArray, replace: Boolean): Boolean = storage.write(SlotKey, bytes, replace)

    override suspend fun readFile(name: String, maxBytes: Int): ByteArray? =
        storage.read(fileKey(name), maxBytes)?.within(maxBytes)

    override suspend fun writeFile(name: String, bytes: ByteArray): Boolean =
        storage.write(fileKey(name), bytes, replace = true)

    override suspend fun createFile(name: String, bytes: ByteArray): Boolean =
        storage.write(fileKey(name), bytes, replace = false)

    private fun fileKey(name: String): String {
        require(SaveFileName.matches(name)) { "Not a save file name: $name" }
        return WorldsFolder + name
    }

    private fun ByteArray.within(maxBytes: Int) = also { if (size > maxBytes) throw SaveFileTooLarge() }

    companion object {
        const val SlotKey = "island-v3.json"
        val LegacySlotKeys = listOf("island-v2.json", "island-v1.json")
        const val WorldsFolder = "worlds/"
    }
}

package id.branchworld.game.platform.storage

/**
 * The one thing each platform's storage must do: keep whole files under relative keys such as `island-v3.json` or
 * `worlds/world-1.json`. File naming, validation and the save layout live in [KeyedWorldSaveStore].
 */
interface BlobStorage {
    /** The bytes stored under [key], or null when there are none. Throws [SaveFileTooLarge] above [maxBytes]. */
    suspend fun read(key: String, maxBytes: Int): ByteArray?

    /**
     * Stores [bytes] under [key] so that the file is either whole or untouched. When [replace] is false and [key]
     * already exists, nothing is written and the result is false.
     */
    suspend fun write(key: String, bytes: ByteArray, replace: Boolean): Boolean
}

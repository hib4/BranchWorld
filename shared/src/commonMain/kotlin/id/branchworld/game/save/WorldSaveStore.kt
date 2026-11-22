package id.branchworld.game.save

/**
 * Platform storage. [read] and [write] address the original single save slot, which is now only read once to import
 * it into the atlas. Named files hold each island's own history and the player's profile; adapters commit them
 * atomically and never touch the original slot.
 */
interface WorldSaveStore {
    suspend fun read(maxBytes: Int): ByteArray?

    /** Returns false when the slot exists and [replace] is false. */
    suspend fun write(bytes: ByteArray, replace: Boolean): Boolean

    /** A named file, or null when it does not exist. Names match [SaveFileName]. */
    suspend fun readFile(name: String, maxBytes: Int): ByteArray? = null

    /** Atomically replaces a named file. Adapters without named files ignore the request. */
    suspend fun writeFile(name: String, bytes: ByteArray): Boolean = false
}

/** Lower-case letters, digits and dashes with a .json extension: safe on every platform's file system. */
val SaveFileName = Regex("[a-z0-9][a-z0-9-]{0,62}\\.json")

class SaveFileTooLarge : Exception()

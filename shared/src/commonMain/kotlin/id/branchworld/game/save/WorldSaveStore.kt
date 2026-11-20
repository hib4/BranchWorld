package id.branchworld.game.save

/** Adapters perform bounded reads and commit writes atomically in a single local slot. */
interface WorldSaveStore {
    suspend fun read(maxBytes: Int): ByteArray?

    /** Returns false when the slot exists and [replace] is false. */
    suspend fun write(bytes: ByteArray, replace: Boolean): Boolean
}

class SaveFileTooLarge : Exception()

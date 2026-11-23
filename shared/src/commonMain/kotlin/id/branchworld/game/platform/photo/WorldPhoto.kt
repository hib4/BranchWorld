package id.branchworld.game.platform.photo

/** Identifies the world and displayed moment for platform photo filenames. */
data class PhotoMeta(val seed: Long, val tick: Long) {
    /** `branchworld-<seed>-<tick>[-<unique>].png`; a unique suffix keeps repeated photos of one moment apart. */
    fun fileName(unique: String? = null): String =
        "branchworld-$seed-$tick" + (unique?.let { "-$it" } ?: "") + ".png"
}

/** Result of saving the renderer's current frame through the platform photo adapter. */
sealed interface PhotoResult {
    data class Saved(val location: String, val share: (() -> Boolean)? = null) : PhotoResult
    data object ViewNotReady : PhotoResult
    data object CouldNotSave : PhotoResult
}

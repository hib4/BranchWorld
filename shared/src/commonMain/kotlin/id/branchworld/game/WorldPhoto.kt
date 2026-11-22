package id.branchworld.game

/** Identifies the world and displayed moment for platform photo filenames. */
data class PhotoMeta(val seed: Long, val tick: Long)

/** Result of saving the renderer's current frame through the platform photo adapter. */
sealed interface PhotoResult {
    data class Saved(val location: String, val share: (() -> Boolean)? = null) : PhotoResult
    data object ViewNotReady : PhotoResult
    data object CouldNotSave : PhotoResult
}

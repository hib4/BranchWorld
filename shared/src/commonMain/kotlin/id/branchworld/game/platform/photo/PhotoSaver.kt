package id.branchworld.game.platform.photo

/** Keeps a captured frame where the player finds their pictures: an album, a folder, or the browser's downloads. */
fun interface PhotoSaver {
    suspend fun save(png: ByteArray, photo: PhotoMeta): PhotoResult
}

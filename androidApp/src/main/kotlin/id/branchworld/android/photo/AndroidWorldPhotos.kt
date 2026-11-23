package id.branchworld.android.photo

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.AtomicFile
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import id.branchworld.game.R
import id.branchworld.game.platform.photo.PhotoMeta
import id.branchworld.game.platform.photo.PhotoResult
import id.branchworld.game.platform.photo.PhotoSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Shares photos kept in app storage on Android 9 and earlier. */
class PhotoFileProvider : FileProvider()

/** Photos go to the shared Pictures/BranchWorld album, or to app storage with a share sheet before Android 10. */
internal class AndroidWorldPhotos(private val activity: Activity) : PhotoSaver {
    override suspend fun save(png: ByteArray, photo: PhotoMeta): PhotoResult = withContext(Dispatchers.IO) {
        val filename = photo.fileName(UUID.randomUUID().toString())
        if (Build.VERSION.SDK_INT >= 29) {
            publish(png, filename)
            PhotoResult.Saved(Album)
        } else {
            val path = saveInAppPictures(png, filename)
            PhotoResult.Saved(activity.getString(R.string.photo_app_pictures)) { share(path) }
        }
    }

    @RequiresApi(29)
    private fun publish(png: ByteArray, filename: String) {
        val resolver = activity.contentResolver
        val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val entry = checkNotNull(resolver.insert(images, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, MimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Album)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        var published = false
        try {
            checkNotNull(resolver.openOutputStream(entry, "w")).use { it.write(png) }
            val committed = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            check(resolver.update(entry, committed, null, null) == 1)
            published = true
        } finally {
            if (!published) runCatching { resolver.delete(entry, null, null) }
        }
    }

    private fun saveInAppPictures(png: ByteArray, filename: String): File {
        val pictures = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(activity.filesDir, Environment.DIRECTORY_PICTURES)
        val album = File(pictures, "BranchWorld")
        check(album.mkdirs() || album.isDirectory)
        val path = File(album, filename)
        val file = AtomicFile(path)
        val stream = file.startWrite()
        try {
            stream.write(png)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        check(path.isFile && path.length() == png.size.toLong())
        return path
    }

    private fun share(path: File): Boolean = runCatching {
        val photo = FileProvider.getUriForFile(activity, "${activity.packageName}.photos", path)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MimeType
            putExtra(Intent.EXTRA_STREAM, photo)
            clipData = ClipData.newUri(activity.contentResolver, path.name, photo)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, null))
    }.isSuccess

    private companion object {
        const val Album = "Pictures/BranchWorld"
        const val MimeType = "image/png"
    }
}

package id.branchworld.web.photo

import id.branchworld.game.platform.photo.PhotoMeta
import id.branchworld.game.platform.photo.PhotoResult
import id.branchworld.game.platform.photo.PhotoSaver
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

/** Browsers keep photos in the player's downloads; there is no silent access to a pictures folder. */
internal object BrowserDownloads : PhotoSaver {
    override suspend fun save(png: ByteArray, photo: PhotoMeta): PhotoResult =
        if (download(png, photo.fileName())) PhotoResult.Saved("your downloads") else PhotoResult.CouldNotSave

    private fun download(png: ByteArray, fileName: String): Boolean = try {
        val bytes = png.unsafeCast<Int8Array>()
        val url = URL.createObjectURL(Blob(arrayOf(bytes), BlobPropertyBag(type = "image/png")))
        val link = document.createElement("a") as HTMLAnchorElement
        link.href = url
        link.download = fileName
        link.style.display = "none"
        document.body?.appendChild(link)
        link.click()
        link.remove()
        window.setTimeout({ URL.revokeObjectURL(url) }, 10_000)
        true
    } catch (error: Throwable) {
        console.error("BranchWorld could not save the photo", error)
        false
    }
}

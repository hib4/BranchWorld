@file:OptIn(ExperimentalForeignApi::class)

package id.branchworld.ios.photo

import id.branchworld.game.platform.photo.PhotoMeta
import id.branchworld.game.platform.photo.PhotoResult
import id.branchworld.game.platform.photo.PhotoSaver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSUUID
import platform.Foundation.dataWithBytes
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResourceCreationOptions
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import kotlin.coroutines.resume

/**
 * Photos go to the player's photo library. The game asks only to add photos, never to read the library, and iOS asks
 * the player once. After saving, the share sheet can send the same picture on.
 */
internal object IosPhotos : PhotoSaver {
    override suspend fun save(png: ByteArray, photo: PhotoMeta): PhotoResult {
        if (!allowed()) return PhotoResult.NotAllowed
        val data = png.toNSData()
        val saved = suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                val options = PHAssetResourceCreationOptions().apply { originalFilename = photo.fileName(NSUUID().UUIDString) }
                PHAssetCreationRequest.creationRequestForAsset().addResourceWithType(PHAssetResourceTypePhoto, data, options)
            }) { success, _ -> continuation.resume(success) }
        }
        // The share action runs from the interface, on the main thread.
        return if (saved) PhotoResult.Saved(Library) { share(data) } else PhotoResult.CouldNotSave
    }

    private suspend fun allowed(): Boolean = suspendCancellableCoroutine { continuation ->
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
            continuation.resume(status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited)
        }
    }

    /** Opens the share sheet over the game with the saved picture; false when there is nothing to present it from. */
    private fun share(png: NSData): Boolean {
        val image = UIImage.imageWithData(png) ?: return false
        val presenter = topController() ?: return false
        val sheet = UIActivityViewController(activityItems = listOf(image), applicationActivities = null)
        // iPad shows the sheet as a popover, which needs an anchor.
        sheet.popoverPresentationController?.sourceView = presenter.view
        presenter.presentViewController(sheet, animated = true, completion = null)
        return true
    }

    private fun topController(): UIViewController? {
        val window = UIApplication.sharedApplication.connectedScenes.firstNotNullOfOrNull { scene ->
            (scene as? UIWindowScene)?.windows?.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
        }
        var top = window?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }

    /** Where the photo went, as the player knows it. */
    private const val Library = "your photo library"
}

private fun ByteArray.toNSData(): NSData = usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }

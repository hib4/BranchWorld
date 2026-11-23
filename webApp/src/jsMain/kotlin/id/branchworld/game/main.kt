package id.branchworld.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.ComposeViewport
import id.branchworld.renderer.WebDiorama
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport("branchworld-ui") {
        val renderer = remember { WebDiorama() }
        val sounds = remember { WebWorldSounds() }
        val saveStore = remember { WebWorldSaveStore() }
        var foreground by remember { mutableStateOf(!pageHidden()) }
        DisposableEffect(Unit) {
            val visibility: (Event) -> Unit = { foreground = !pageHidden() }
            document.addEventListener("visibilitychange", visibility)
            onDispose { document.removeEventListener("visibilitychange", visibility) }
        }
        SideEffect { renderer.foreground(foreground) }
        LaunchedEffect(Unit) { hideLoadingNotice() }
        // Skiko clears the Compose canvas to white before every frame. Punch it back to transparent first,
        // so the world that Kool draws on the canvas beneath shows through everywhere the interface does not.
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, blendMode = BlendMode.Clear) }) {
        App(
            viewport = { modifier, projection -> renderer.Viewport(modifier, projection) },
            viewportHud = { renderer.Hud() },
            viewportCamera = renderer::present,
            foreground = foreground,
            saveStore = saveStore,
            playSound = sounds::play,
            setAmbience = sounds::setAmbience,
            savePhoto = { moment ->
                val png = renderer.capturePng()
                if (png == null) PhotoResult.ViewNotReady
                else if (downloadPhoto(png, moment)) PhotoResult.Saved("your downloads")
                else PhotoResult.CouldNotSave
            },
            viewportUnderneath = true,
        )
        }
    }
}

private fun pageHidden(): Boolean = document.asDynamic().hidden == true

private fun hideLoadingNotice() {
    val notice = document.getElementById("branchworld-loading") as? HTMLElement ?: return
    notice.style.opacity = "0"
    window.setTimeout({ notice.remove() }, 450)
}

/** Browsers keep photos in the player's downloads; there is no silent access to a pictures folder. */
private fun downloadPhoto(png: ByteArray, photo: PhotoMeta): Boolean = try {
    val bytes = png.unsafeCast<Int8Array>()
    val url = URL.createObjectURL(Blob(arrayOf(bytes), BlobPropertyBag(type = "image/png")))
    val link = document.createElement("a") as HTMLAnchorElement
    link.href = url
    link.download = "branchworld-${photo.seed}-${photo.tick}.png"
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

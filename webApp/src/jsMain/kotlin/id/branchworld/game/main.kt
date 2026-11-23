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
import id.branchworld.game.platform.AppServices
import id.branchworld.game.platform.HostShell
import id.branchworld.game.ui.app.App
import id.branchworld.renderer.WebDiorama
import id.branchworld.web.photo.BrowserDownloads
import id.branchworld.web.sound.WebWorldSounds
import id.branchworld.web.storage.WebWorldSaveStore
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport("branchworld-ui") {
        val renderer = remember { WebDiorama() }
        val services = remember {
            AppServices(
                viewport = renderer,
                saveStore = WebWorldSaveStore(),
                sounds = WebWorldSounds(),
                photos = BrowserDownloads,
                shell = HostShell(viewportUnderneath = true),
            )
        }
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
            App(services, foreground = foreground)
        }
    }
}

private fun pageHidden(): Boolean = document.asDynamic().hidden == true

private fun hideLoadingNotice() {
    val notice = document.getElementById("branchworld-loading") as? HTMLElement ?: return
    notice.style.opacity = "0"
    window.setTimeout({ notice.remove() }, 450)
}

package id.branchworld.game

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import id.branchworld.renderer.DesktopDiorama
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun main() {
    application {
        val renderer = remember { DesktopDiorama() }
        val sounds = remember { DesktopWorldSounds() }
        DisposableEffect(sounds) { onDispose(sounds::close) }
        val saveStore = remember { DesktopWorldSaveStore() }
        val state = rememberWindowState(width = 1120.dp, height = 650.dp)
        val foreground = !state.isMinimized
        SideEffect { renderer.foreground(foreground) }
        Window(
            state = state,
            onCloseRequest = { renderer.close(::exitApplication) },
            title = "BranchWorld",
        ) {
            App(
                viewport = { modifier, projection -> renderer.Viewport(modifier, projection) },
                viewportHud = { renderer.Hud() },
                viewportCamera = renderer::present,
                foreground = foreground,
                playInUnfocusedWindow = true,
                saveStore = saveStore,
                playSound = sounds::play,
                setAmbience = sounds::setAmbience,
                savePhoto = { moment ->
                    val png = renderer.capturePng()
                    if (png == null) PhotoResult.ViewNotReady else withContext(Dispatchers.IO) {
                        val path = saveDesktopPhoto(png, moment)
                        PhotoResult.Saved(path.parent.photoLocation())
                    }
                },
            )
        }
    }
}

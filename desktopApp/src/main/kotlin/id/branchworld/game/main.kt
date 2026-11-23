package id.branchworld.game

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import id.branchworld.desktop.photo.DesktopPhotos
import id.branchworld.desktop.sound.DesktopWorldSounds
import id.branchworld.desktop.storage.DesktopWorldSaveStore
import id.branchworld.game.platform.AppServices
import id.branchworld.game.platform.HostShell
import id.branchworld.game.ui.app.App
import id.branchworld.renderer.DesktopDiorama

fun main() {
    application {
        val renderer = remember { DesktopDiorama() }
        val sounds = remember { DesktopWorldSounds() }
        DisposableEffect(sounds) { onDispose(sounds::close) }
        val services = remember {
            AppServices(
                viewport = renderer,
                saveStore = DesktopWorldSaveStore(),
                sounds = sounds,
                photos = DesktopPhotos,
                shell = HostShell(playInUnfocusedWindow = true),
            )
        }
        val state = rememberWindowState(width = 1120.dp, height = 650.dp)
        val foreground = !state.isMinimized
        SideEffect { renderer.foreground(foreground) }
        Window(
            state = state,
            onCloseRequest = { renderer.close(::exitApplication) },
            title = "BranchWorld",
        ) {
            App(services, foreground = foreground)
        }
    }
}

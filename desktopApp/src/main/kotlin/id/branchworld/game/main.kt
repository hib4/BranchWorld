package id.branchworld.game

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import id.branchworld.renderer.DesktopDiorama

fun main() {
    application {
        val renderer = remember { DesktopDiorama() }
        val state = rememberWindowState()
        val foreground = !state.isMinimized
        SideEffect { renderer.foreground(foreground) }
        Window(
            state = state,
            onPreviewKeyEvent = renderer::cameraKey,
            onCloseRequest = { renderer.close(::exitApplication) },
            title = "BranchWorld",
        ) {
            App(
                viewport = { modifier, active -> renderer.Viewport(modifier, active) },
                viewportHud = { renderer.Hud() },
            )
        }
    }
}

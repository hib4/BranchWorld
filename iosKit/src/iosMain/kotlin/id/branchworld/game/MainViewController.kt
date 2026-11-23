package id.branchworld.game

import androidx.compose.ui.window.ComposeUIViewController
import id.branchworld.game.platform.AppServices
import id.branchworld.game.ui.app.App
import id.branchworld.ios.photo.IosPhotos
import id.branchworld.ios.sound.IosWorldSounds
import id.branchworld.ios.storage.IosWorldSaveStore
import id.branchworld.renderer.IosDiorama
import id.branchworld.renderer.WorldStage
import platform.UIKit.UIViewController

/** The iOS app's interface: the RealityKit [stage] Swift passes in, saves, sound and photos. */
fun MainViewController(stage: WorldStage): UIViewController {
    val services = AppServices(
        viewport = IosDiorama(stage),
        saveStore = IosWorldSaveStore(),
        sounds = IosWorldSounds(),
        photos = IosPhotos,
    )
    return ComposeUIViewController { App(services) }
}

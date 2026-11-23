package id.branchworld.game.platform

import androidx.compose.runtime.Composable
import id.branchworld.game.platform.photo.PhotoSaver
import id.branchworld.game.platform.sound.WorldSoundPlayer
import id.branchworld.game.platform.storage.WorldSaveStore
import id.branchworld.game.platform.viewport.WorldViewport

/**
 * Everything a host platform lends the game. Each part is optional: without a viewport the title shows a drawn island,
 * without storage worlds are not kept, and without sound or photos those controls stay hidden.
 */
class AppServices(
    val viewport: WorldViewport? = null,
    val saveStore: WorldSaveStore? = null,
    val sounds: WorldSoundPlayer? = null,
    val photos: PhotoSaver? = null,
    val shell: HostShell = HostShell(),
)

/** How the host window behaves around the game. */
class HostShell(
    /** Routes the system back gesture (Android) to the game while [enabled]. */
    val backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    /** Desktop keeps the world running while another window has focus. */
    val playInUnfocusedWindow: Boolean = false,
    /** The browser draws the 3D view beneath the interface, so the shell stays see-through. */
    val viewportUnderneath: Boolean = false,
)

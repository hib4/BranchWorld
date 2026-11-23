package id.branchworld.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import id.branchworld.android.photo.AndroidWorldPhotos
import id.branchworld.android.sound.AndroidWorldSounds
import id.branchworld.android.storage.AndroidWorldSaveStore
import id.branchworld.game.platform.AppServices
import id.branchworld.game.platform.HostShell
import id.branchworld.game.ui.app.App
import id.branchworld.renderer.AndroidDiorama

class MainActivity : ComponentActivity() {
    private val renderer by lazy { AndroidDiorama(applicationContext) }
    private val sounds by lazy { AndroidWorldSounds(applicationContext) }
    private val saveStore by lazy { AndroidWorldSaveStore(filesDir) }
    private val photos by lazy { AndroidWorldPhotos(this) }
    private val services by lazy {
        AppServices(
            viewport = renderer,
            saveStore = saveStore,
            sounds = sounds,
            photos = photos,
            shell = HostShell(backHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) }),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        setContent {
            App(services)
        }
    }

    override fun onResume() {
        super.onResume()
        sounds.resume()
        renderer.resume()
    }

    override fun onPause() {
        sounds.pause()
        renderer.pause()
        super.onPause()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) renderer.close()
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}

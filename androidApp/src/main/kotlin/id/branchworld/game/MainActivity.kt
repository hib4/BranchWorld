package id.branchworld.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import id.branchworld.renderer.AndroidDiorama

class MainActivity : ComponentActivity() {
    private val renderer by lazy { AndroidDiorama(applicationContext) }
    private val sounds by lazy { AndroidWorldSounds(applicationContext) }
    private val saveStore by lazy { AndroidWorldSaveStore(filesDir) }
    private val photos by lazy { AndroidWorldPhotos(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        setContent {
            App(
                viewport = { modifier, projection -> renderer.Viewport(modifier, projection) },
                viewportHud = { renderer.Hud() },
                viewportCamera = renderer::present,
                registerBackHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) },
                saveStore = saveStore,
                playSound = sounds::play,
                setAmbience = sounds::setAmbience,
                savePhoto = { moment ->
                    val png = renderer.capturePng()
                    if (png == null) PhotoResult.ViewNotReady else photos.save(png, moment)
                },
            )
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

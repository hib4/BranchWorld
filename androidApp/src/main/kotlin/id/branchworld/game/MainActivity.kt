package id.branchworld.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import id.branchworld.renderer.AndroidDiorama

class MainActivity : ComponentActivity() {
    private val renderer by lazy { AndroidDiorama(applicationContext) }
    private val saveStore by lazy { AndroidWorldSaveStore(filesDir) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(
                viewport = { modifier, projection -> renderer.Viewport(modifier, projection) },
                viewportHud = { renderer.Hud() },
                registerBackHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) },
                saveStore = saveStore,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        renderer.resume()
    }

    override fun onPause() {
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

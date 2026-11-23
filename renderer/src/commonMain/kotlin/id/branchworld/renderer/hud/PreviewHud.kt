package id.branchworld.renderer.hud

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.branchworld.game.ui.components.WorldCameraReset
import id.branchworld.renderer.camera.PreviewController
import id.branchworld.renderer.camera.ViewportStatus

@Composable
internal fun PreviewHud(controller: PreviewController) {
    val status by controller.status.collectAsState()
    val opening by controller.presentation.collectAsState()
    if (opening.opening && status == ViewportStatus.Ready) return
    val motion = rememberCoroutineScope()
    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
        status.message?.let { message ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Text(message, Modifier.padding(6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (!opening.opening) WorldCameraReset { controller.focus(motion) }
    }
}

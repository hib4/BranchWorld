package id.branchworld.game.ui.world

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.save_corrupt
import branchworld.shared.generated.resources.save_incompatible
import branchworld.shared.generated.resources.save_loaded
import branchworld.shared.generated.resources.save_unreadable
import branchworld.shared.generated.resources.save_unwritable
import branchworld.shared.generated.resources.shape_loaded_history
import id.branchworld.game.data.SaveStatus
import org.jetbrains.compose.resources.stringResource

/** Why the open world could not be read or kept, announced politely to screen readers. */
@Composable
internal fun SaveMessage(status: SaveStatus?, showTicks: Boolean = true) {
    val message = when (status) {
        SaveStatus.Corrupt -> stringResource(Res.string.save_corrupt)
        SaveStatus.Incompatible -> stringResource(Res.string.save_incompatible)
        SaveStatus.Unreadable -> stringResource(Res.string.save_unreadable)
        SaveStatus.Unwritable -> stringResource(Res.string.save_unwritable)
        is SaveStatus.Loaded -> if (showTicks) stringResource(Res.string.save_loaded, status.tick.toString()) else stringResource(Res.string.shape_loaded_history)
        null -> return
    }
    Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

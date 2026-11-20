package id.branchworld.game

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.load_world
import branchworld.shared.generated.resources.save_busy
import branchworld.shared.generated.resources.save_cancel
import branchworld.shared.generated.resources.save_corrupt
import branchworld.shared.generated.resources.save_incompatible
import branchworld.shared.generated.resources.save_loaded
import branchworld.shared.generated.resources.save_missing
import branchworld.shared.generated.resources.save_replace
import branchworld.shared.generated.resources.save_replace_message
import branchworld.shared.generated.resources.save_saved
import branchworld.shared.generated.resources.save_unreadable
import branchworld.shared.generated.resources.save_unwritable
import branchworld.shared.generated.resources.save_world
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LoadControls(status: SaveStatus?, onLoad: () -> Unit) {
    OutlinedButton(onClick = onLoad, enabled = status != SaveStatus.Busy) {
        Text(stringResource(Res.string.load_world))
    }
    SaveMessage(status)
}

@Composable
internal fun SaveControls(status: SaveStatus?, onSave: (Boolean) -> Unit, onDismiss: () -> Unit) {
    OutlinedButton(onClick = { onSave(false) }, enabled = status != SaveStatus.Busy) {
        Text(stringResource(Res.string.save_world))
    }
    SaveMessage(status)
    if (status == SaveStatus.ConfirmReplace) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.save_replace)) },
            text = { Text(stringResource(Res.string.save_replace_message)) },
            confirmButton = {
                TextButton(onClick = { onSave(true) }) { Text(stringResource(Res.string.save_replace)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.save_cancel)) }
            },
        )
    }
}

@Composable
private fun SaveMessage(status: SaveStatus?) {
    val message = when (status) {
        SaveStatus.Busy -> stringResource(Res.string.save_busy)
        SaveStatus.Missing -> stringResource(Res.string.save_missing)
        SaveStatus.Corrupt -> stringResource(Res.string.save_corrupt)
        SaveStatus.Incompatible -> stringResource(Res.string.save_incompatible)
        SaveStatus.Unreadable -> stringResource(Res.string.save_unreadable)
        SaveStatus.Unwritable -> stringResource(Res.string.save_unwritable)
        is SaveStatus.Saved -> stringResource(Res.string.save_saved, status.tick.toString())
        is SaveStatus.Loaded -> stringResource(Res.string.save_loaded, status.tick.toString())
        else -> return
    }
    Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

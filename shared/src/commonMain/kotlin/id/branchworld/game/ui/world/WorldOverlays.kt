package id.branchworld.game.ui.world

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.*
import branchworld.shared.generated.resources.Res
import id.branchworld.game.platform.photo.PhotoResult
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.BoardSheet
import id.branchworld.game.ui.components.ScrollColumn
import id.branchworld.game.ui.theme.GameIcons
import id.branchworld.game.ui.theme.LocalUi
import id.branchworld.game.ui.theme.Wood
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** The pause menu: every way out of the moment, with the autosave state in plain words. */
@Composable
internal fun PauseMenu(
    paint: Color,
    saved: String,
    marked: Boolean,
    diagnostics: Boolean,
    onResume: () -> Unit,
    onTimeline: () -> Unit,
    onMark: () -> Unit,
    onPhoto: () -> Unit,
    onSettings: () -> Unit,
    onAtlas: () -> Unit,
    onTitle: () -> Unit,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    /** Where the worlds list lives: "My worlds" in a living world. */
    atlasLabel: String = stringResource(Res.string.menu_atlas),
) {
    BoxWithConstraints(modifier) {
        // A short, wide window gets one band of four columns so every action is visible without scrolling.
        val band = maxWidth >= 620.dp && maxHeight < 440.dp
        val columns = if (band) 4 else 2
        val entries = buildList {
            add(Triple(stringResource(Res.string.pause_timeline), onTimeline, true))
            add(Triple(stringResource(if (marked) Res.string.pause_marked else Res.string.pause_mark), onMark, !marked))
            add(Triple(stringResource(Res.string.pause_photo), onPhoto, true))
            add(Triple(stringResource(Res.string.menu_settings), onSettings, true))
            add(Triple(atlasLabel, onAtlas, true))
            add(Triple(stringResource(Res.string.pause_title_screen), onTitle, true))
            if (diagnostics) add(Triple(stringResource(Res.string.diagnostics_title), onDiagnostics, true))
        }
        val resume = stringResource(Res.string.pause_resume)
        BoardSheet(stringResource(Res.string.pause_title), Modifier.widthIn(max = if (band) 820.dp else 428.dp)
            .fillMaxWidth(), onClose = onResume) {
            Text(saved, style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
            ScrollColumn(spacing = 8.dp) {
                if (!band) Block(resume, onResume, Modifier.fillMaxWidth(), paint = paint, icon = GameIcons.Play, size = BlockSize.Small)
                val cells = if (band) listOf(Triple(resume, onResume, true)) + entries else entries
                for (row in cells.chunked(columns)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, action, enabled) in row) {
                            val primary = band && label == resume && action == onResume
                            Block(label, action, Modifier.weight(1f), enabled = enabled, paint = if (primary) paint else Wood.BeechLight,
                                icon = if (primary) GameIcons.Play else null, size = BlockSize.Small)
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Capture controls sit over the live view. The platform adapter saves the rendered scene. */
@Composable
internal fun PhotoControls(
    onLeave: () -> Unit,
    onSave: (() -> Unit)?,
    saving: Boolean,
    result: PhotoResult?,
    modifier: Modifier = Modifier,
) {
    var hint by remember { mutableStateOf(true) }
    var shareFailed by remember(result) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(3_500)
        hint = false
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
        Block(stringResource(Res.string.photo_leave), onLeave, icon = GameIcons.Close, size = BlockSize.Small)
        if (onSave != null) {
            Block(stringResource(if (saving) Res.string.photo_saving else Res.string.photo_keep), onSave,
                icon = GameIcons.Camera, enabled = !saving, size = BlockSize.Small)
        }
        val status = when (result) {
            is PhotoResult.Saved -> stringResource(Res.string.photo_saved, result.location)
            PhotoResult.ViewNotReady -> stringResource(Res.string.photo_view_not_ready)
            PhotoResult.CouldNotSave -> stringResource(Res.string.photo_failed)
            null -> null
        }
        if (status != null) {
            Board(Modifier.widthIn(max = 290.dp).semantics { liveRegion = LiveRegionMode.Polite }, padding = 10.dp) {
                Text(status, style = MaterialTheme.typography.bodySmall)
                if (result is PhotoResult.Saved && result.share != null) {
                    Block(stringResource(Res.string.photo_share), { shareFailed = !result.share.invoke() }, size = BlockSize.Compact)
                    if (shareFailed) Text(stringResource(Res.string.photo_share_failed), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val showHint = hint && !saving && result == null
        if (LocalUi.current.reducedMotion) {
            if (showHint) PhotoHint()
        } else AnimatedVisibility(showHint, enter = fadeIn(), exit = fadeOut()) { PhotoHint() }
    }
}

@Composable
private fun PhotoHint() {
    Board(Modifier.widthIn(max = 250.dp), padding = 10.dp) {
        Text(stringResource(Res.string.photo_hint), style = MaterialTheme.typography.bodySmall)
    }
}

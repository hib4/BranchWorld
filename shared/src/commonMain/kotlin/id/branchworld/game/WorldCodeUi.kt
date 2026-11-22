package id.branchworld.game

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.code_checksum
import branchworld.shared.generated.resources.code_copied
import branchworld.shared.generated.resources.code_copy
import branchworld.shared.generated.resources.code_different
import branchworld.shared.generated.resources.code_enter
import branchworld.shared.generated.resources.code_exporting
import branchworld.shared.generated.resources.code_field
import branchworld.shared.generated.resources.code_importing
import branchworld.shared.generated.resources.code_invalid
import branchworld.shared.generated.resources.code_newer
import branchworld.shared.generated.resources.code_occupied
import branchworld.shared.generated.resources.code_open
import branchworld.shared.generated.resources.code_share
import branchworld.shared.generated.resources.code_share_detail
import branchworld.shared.generated.resources.code_too_large
import branchworld.shared.generated.resources.code_unsupported
import branchworld.shared.generated.resources.code_unwritable
import id.branchworld.game.timeline.TimelineCheckpoint
import id.branchworld.game.timeline.WorldCode
import id.branchworld.game.timeline.WorldCodeError
import id.branchworld.game.timeline.WorldCodeProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WorldCodeShareControl(island: IslandLoad.Ready) {
    var checkpoint by remember(island) { mutableStateOf<TimelineCheckpoint?>(null) }
    Block(stringResource(Res.string.code_share), { checkpoint = island.checkpoint() }, size = BlockSize.Small)
    checkpoint?.let { WorldCodeShareDialog(it) { checkpoint = null } }
}

@Suppress("DEPRECATION")
@Composable
private fun WorldCodeShareDialog(checkpoint: TimelineCheckpoint, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<WorldCodeProblem?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(checkpoint) {
        try {
            code = withContext(Dispatchers.Default) { WorldCode.encode(checkpoint) }
        } catch (error: WorldCodeError) {
            problem = error.problem
        }
    }
    CodeDialog(stringResource(Res.string.code_share), onClose) {
        val failure = problem
        val ready = code
        when {
            failure != null -> CodeStatus(failure.label())
            ready == null -> CodeStatus(stringResource(Res.string.code_exporting))
            else -> {
                Text(stringResource(Res.string.code_share_detail), style = MaterialTheme.typography.bodyMedium)
                SelectionContainer {
                    Text(ready, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                }
                Block(stringResource(Res.string.code_copy), {
                    clipboard.setText(AnnotatedString(ready))
                    copied = true
                }, size = BlockSize.Small, paint = Wood.Woad)
                if (copied) CodeStatus(stringResource(Res.string.code_copied))
            }
        }
    }
}

@Composable
internal fun WorldCodeImportControl(onImport: (suspend (String) -> WorldCodeImport)?, onOpen: (WorldCodeImport.Open) -> Unit) {
    if (onImport == null) return
    var open by rememberSaveable { mutableStateOf(false) }
    Block(stringResource(Res.string.code_enter), { open = true }, size = BlockSize.Small)
    if (open) {
        WorldCodeImportDialog(onImport, onOpen = {
            open = false
            onOpen(it)
        }, onClose = { open = false })
    }
}

@Composable
private fun WorldCodeImportDialog(
    onImport: suspend (String) -> WorldCodeImport,
    onOpen: (WorldCodeImport.Open) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<WorldCodeImport?>(null) }
    val tooLong = code.length > WorldCode.MaxLength
    val problem = issue
    CodeDialog(stringResource(Res.string.code_enter), onClose) {
        OutlinedTextField(code, { value ->
            code = value.take(WorldCode.MaxLength + 1)
            issue = null
        }, modifier = Modifier.fillMaxWidth(), enabled = !loading, maxLines = 4,
            label = { Text(stringResource(Res.string.code_field)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Wood.Walnut, unfocusedTextColor = Wood.Walnut,
                focusedBorderColor = Wood.Woad, unfocusedBorderColor = Wood.WalnutSoft, cursorColor = Wood.Woad,
            ),
        )
        Block(stringResource(Res.string.code_open), {
            if (!loading) {
                loading = true
                issue = null
                scope.launch {
                    try {
                        when (val result = onImport(code)) {
                            is WorldCodeImport.Open -> onOpen(result)
                            else -> issue = result
                        }
                    } finally {
                        loading = false
                    }
                }
            }
        }, enabled = code.isNotBlank() && !tooLong && !loading, paint = Wood.Woad, size = BlockSize.Small)
        when {
            loading -> CodeStatus(stringResource(Res.string.code_importing))
            tooLong -> CodeStatus(WorldCodeProblem.TooLarge.label())
            problem is WorldCodeImport.BadCode -> CodeStatus(problem.problem.label())
            problem == WorldCodeImport.Occupied -> CodeStatus(stringResource(Res.string.code_occupied))
            problem == WorldCodeImport.Unwritable -> CodeStatus(stringResource(Res.string.code_unwritable))
        }
    }
}

@Composable
private fun CodeDialog(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        BoardSheet(title, Modifier.widthIn(max = 440.dp).fillMaxWidth(), onClose = onClose) {
            ScrollColumn(spacing = 10.dp) { content() }
        }
    }
}

@Composable
private fun CodeStatus(text: String) {
    Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
}

@Composable
private fun WorldCodeProblem.label() = stringResource(when (this) {
    WorldCodeProblem.Invalid -> Res.string.code_invalid
    WorldCodeProblem.Checksum -> Res.string.code_checksum
    WorldCodeProblem.Newer -> Res.string.code_newer
    WorldCodeProblem.Different -> Res.string.code_different
    WorldCodeProblem.TooLarge -> Res.string.code_too_large
    WorldCodeProblem.Unsupported -> Res.string.code_unsupported
})

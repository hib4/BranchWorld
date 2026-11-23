package id.branchworld.game.ui.world.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.*
import branchworld.shared.generated.resources.Res
import id.branchworld.game.data.Bookmark
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.components.PegButton
import id.branchworld.game.ui.theme.GameIcons
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.world.dayLabel
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MarkedMoments(bookmarks: List<Bookmark>, island: IslandLoad.Ready, onVisit: (Bookmark) -> Unit, onForget: (Bookmark) -> Unit) {
    val view by island.timelineView.collectAsStateWithLifecycle()
    Text(stringResource(Res.string.timeline_marks), style = MaterialTheme.typography.titleMedium)
    for (mark in bookmarks.sortedWith(compareBy<Bookmark> { it.branchId }.thenBy { it.tick })) {
        val branch = view.branches.firstOrNull { it.id == mark.branchId } ?: continue
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(branch.name, style = MaterialTheme.typography.titleSmall)
                Text(dayLabel(mark.tick, null), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
            }
            Block(stringResource(Res.string.timeline_mark_go), { onVisit(mark) }, size = BlockSize.Small, enabled = !view.seeking)
            PegButton(GameIcons.Close, stringResource(Res.string.timeline_mark_forget), { onForget(mark) })
        }
    }
}

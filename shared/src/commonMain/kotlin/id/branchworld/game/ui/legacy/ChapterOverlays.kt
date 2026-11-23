package id.branchworld.game.ui.legacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.*
import branchworld.shared.generated.resources.Res
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.BoardSheet
import id.branchworld.game.ui.components.Peg
import id.branchworld.game.ui.components.ScrollColumn
import id.branchworld.game.ui.theme.GameIcons
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.theme.chapterPaint
import id.branchworld.game.ui.world.dayLabel
import id.branchworld.simulation.ecology.EcologyState
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.range.RangeEventKind
import id.branchworld.simulation.ecology.settlement.Hardship
import id.branchworld.simulation.timeline.ChapterOutcome
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The Last Crossing's current concern, read from the herd's real journey and the village's request. */
internal fun crossingConcern(state: EcologyState): StringResource? {
    val habitat = state.habitat ?: return null
    val herd = habitat.herd
    val warning = state.shaping?.warningAt
    val settled = habitat.log.lastOrNull { it.kind == RangeEventKind.PastureSettled }
    return when {
        warning != null && state.tick - warning <= 120 -> Res.string.crossing_warning
        herd.bound == 1 && herd.arrivedAt == null -> Res.string.crossing_going_west
        herd.bound == 1 && settled != null && settled.tick >= herd.departedAt -> Res.string.crossing_settled
        herd.bound == 1 -> Res.string.crossing_on_pasture
        herd.bound == 2 && herd.arrivedAt == null && herd.departedAt > 0 -> Res.string.crossing_returning
        else -> Res.string.crossing_at_lake
    }
}

/** A short first-time explanation that appears once, at the moment it matters. */
@Composable
internal fun KeeperGuide(text: StringResource, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Board(modifier.widthIn(max = 340.dp).semantics { liveRegion = LiveRegionMode.Polite }, padding = 14.dp) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        Block(stringResource(Res.string.guide_ok), onDismiss, paint = Wood.BeechLight, size = BlockSize.Small)
    }
}

/** The chapter's closing: every history as it stands, so the Keeper chooses which ending to remember. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChapterClosing(
    story: Story,
    outcomes: List<ChapterOutcome>,
    activeBranch: Int,
    onChoose: (ChapterOutcome) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoardSheet(stringResource(Res.string.close_title, stringResource(story.title)), modifier.fillMaxWidth(), onClose = onCancel) {
        ScrollColumn(spacing = 10.dp) {
            Text(stringResource(Res.string.close_intro), style = MaterialTheme.typography.bodyMedium, color = Wood.WalnutSoft)
            for (outcome in outcomes.sortedByDescending { it.branch.id == activeBranch }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(outcome.branch.name, style = MaterialTheme.typography.titleLarge)
                        if (outcome.branch.id == activeBranch) Peg(stringResource(Res.string.close_current))
                    }
                    Text(dayLabel(outcome.tick, null), style = MaterialTheme.typography.labelMedium, color = Wood.WalnutSoft)
                    for (line in outcomeLines(story.chapter, outcome)) Text(line, style = MaterialTheme.typography.bodyMedium)
                    Block(stringResource(Res.string.close_choose), { onChoose(outcome) }, Modifier.padding(top = 4.dp),
                        paint = Wood.BeechLight,
                        icon = GameIcons.Seal, size = BlockSize.Small)
                }
            }
        }
    }
}

@Composable
internal fun outcomeLines(chapter: Chapter, outcome: ChapterOutcome): List<String> = buildList {
    // The same facts in the same order for every history, with explicit zeros, so histories compare row by row.
    add(stringResource(Res.string.outcome_people, outcome.people, outcome.homes))
    add(stringResource(if (outcome.settlements > 1) Res.string.outcome_two_places else Res.string.outcome_one_place))
    val departures = outcome.departed.toList().filter { it.second > 0 }.sortedBy { it.first.ordinal }
    if (departures.isEmpty()) add(stringResource(Res.string.outcome_nobody_left)) else {
        val causes = departures.map { (cause, count) ->
            stringResource(when (cause) {
                Hardship.Thirst -> Res.string.outcome_cause_thirst
                Hardship.Hunger -> Res.string.outcome_cause_hunger
                Hardship.Crowding -> Res.string.outcome_cause_crowding
                Hardship.Fire -> Res.string.outcome_cause_fire
            }, count)
        }
        add(stringResource(Res.string.outcome_left, causes.joinToString(", ")))
    }
    if (chapter == Chapter.EmberSeason) {
        add(stringResource(Res.string.outcome_ember_damage, outcome.homesBurned, outcome.pinesBurned))
        add(stringResource(Res.string.outcome_ember_ground, outcome.burnedGround, outcome.pinesFelled))
        add(stringResource(Res.string.outcome_ember_recovering, outcome.regrowingGround))
        outcome.emberPlan?.let { add(stringResource(Res.string.outcome_ember_plan, stringResource(it.title()))) }
    } else if (chapter == Chapter.LastCrossing) {
        add(if (outcome.calves > 0) stringResource(Res.string.outcome_calves, outcome.calves) else stringResource(Res.string.outcome_no_calves))
        add(stringResource(if (outcome.refuges > 0) Res.string.outcome_refuge else Res.string.outcome_no_refuge))
    } else {
        add(stringResource(if (outcome.well) Res.string.outcome_well else Res.string.outcome_no_well))
        add(if (outcome.groves > 0) stringResource(Res.string.outcome_groves, outcome.groves) else stringResource(Res.string.outcome_no_groves))
    }
    add(stringResource(Res.string.outcome_wildlife, outcome.deer, outcome.rabbits, outcome.foxes))
    if (chapter != Chapter.EmberSeason) {
        add(outcome.growthPlan?.let { stringResource(Res.string.outcome_plan, stringResource(it.title())) }
            ?: stringResource(Res.string.outcome_no_plan))
        add(stringResource(when {
            outcome.growthPlan == null && outcome.waterPlan == null -> Res.string.outcome_no_answer
            outcome.keeperChose -> Res.string.outcome_keeper
            else -> Res.string.outcome_village
        }))
    }
}

/** After closing: what opened, and where to go next. */
@Composable
internal fun ChapterClosed(story: Story, branchName: String, onNext: (() -> Unit)?, onStay: () -> Unit, modifier: Modifier = Modifier) {
    Board(modifier.widthIn(max = 420.dp), padding = 20.dp) {
        Text(stringResource(Res.string.close_done), Modifier.semantics { heading() }, style = MaterialTheme.typography.displaySmall)
        Text(stringResource(Res.string.close_remembered, branchName), style = MaterialTheme.typography.bodyLarge)
        story.next?.let { Text(stringResource(Res.string.close_unlocked, stringResource(it.title)), color = Wood.Walnut,
            style = MaterialTheme.typography.titleMedium) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val next = story.next
            if (next != null && onNext != null) Block(stringResource(Res.string.close_begin_next, stringResource(next.title)), onNext,
                paint = chapterPaint(next.chapter), icon = GameIcons.Play)
            Block(stringResource(Res.string.close_keep), onStay, paint = if (onNext == null) chapterPaint(story.chapter) else Wood.BeechLight)
        }
    }
}

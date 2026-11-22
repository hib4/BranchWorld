package id.branchworld.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.*
import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.Hardship
import id.branchworld.game.ecology.RangeEventKind
import id.branchworld.game.timeline.ChapterOutcome
import kotlinx.coroutines.delay
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
            add(Triple(stringResource(Res.string.menu_atlas), onAtlas, true))
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

/** A short first-time explanation that appears once, at the moment it matters. */
@Composable
internal fun KeeperGuide(text: StringResource, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Board(modifier.widthIn(max = 340.dp).semantics { liveRegion = LiveRegionMode.Polite }, padding = 14.dp) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        Block(stringResource(Res.string.guide_ok), onDismiss, paint = Wood.BeechLight, size = BlockSize.Small)
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

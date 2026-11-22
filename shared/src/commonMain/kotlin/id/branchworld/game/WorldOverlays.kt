package id.branchworld.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    GlassPanel(modifier.widthIn(max = 380.dp), padding = 18.dp) {
        Text(stringResource(Res.string.pause_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.displaySmall)
        Text(saved, style = MaterialTheme.typography.bodySmall, color = Atlas.Mist)
        OrnamentRule()
        Column(Modifier.verticalScroll(rememberScrollState())) {
            MenuItem(stringResource(Res.string.pause_resume), onResume, icon = GameIcons.Play, large = false)
            MenuItem(stringResource(Res.string.pause_timeline), onTimeline, icon = GameIcons.Branches, large = false)
            MenuItem(stringResource(if (marked) Res.string.pause_marked else Res.string.pause_mark), onMark, icon = GameIcons.Bookmark,
                large = false, enabled = !marked)
            MenuItem(stringResource(Res.string.pause_photo), onPhoto, icon = GameIcons.Camera, large = false)
            MenuItem(stringResource(Res.string.menu_settings), onSettings, icon = GameIcons.Gear, large = false)
            MenuItem(stringResource(Res.string.menu_atlas), onAtlas, icon = GameIcons.Compass, large = false)
            if (diagnostics) MenuItem(stringResource(Res.string.diagnostics_title), onDiagnostics, icon = GameIcons.Eye, large = false)
            MenuItem(stringResource(Res.string.pause_title_screen), onTitle, icon = GameIcons.Back, large = false)
        }
    }
}

/** A short first-time explanation that appears once, at the moment it matters. */
@Composable
internal fun KeeperGuide(text: StringResource, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier.widthIn(max = 340.dp).semantics { liveRegion = LiveRegionMode.Polite }, padding = 14.dp) {
        SectionLabel(stringResource(Res.string.guide_title))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        StoryButton(stringResource(Res.string.guide_ok), onDismiss, primary = false, onDark = true)
    }
}

/** Photo mode keeps one quiet way back and a hint that fades away. */
@Composable
internal fun PhotoControls(onLeave: () -> Unit, modifier: Modifier = Modifier) {
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3_500)
        hint = false
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StoryButton(stringResource(Res.string.photo_leave), onLeave, primary = false, icon = GameIcons.Close, onDark = true)
        AnimatedVisibility(hint, enter = fadeIn(), exit = fadeOut()) {
            InfoChip(stringResource(Res.string.photo_hint), background = Atlas.InkGlass)
        }
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
    VellumSheet(stringResource(Res.string.close_title, stringResource(story.title)), modifier,
        kicker = stringResource(Res.string.chapter_numeral, story.numeral), onClose = onCancel) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.close_intro), style = MaterialTheme.typography.bodyMedium, color = Atlas.InkSoft)
            for (outcome in outcomes.sortedByDescending { it.branch.id == activeBranch }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(outcome.branch.name, style = MaterialTheme.typography.titleLarge)
                    Text(dayLabel(outcome.tick, null), style = MaterialTheme.typography.labelMedium, color = Atlas.Ember)
                    for (line in outcomeLines(story.chapter, outcome)) Text("◆  $line", style = MaterialTheme.typography.bodyMedium)
                    StoryButton(stringResource(Res.string.close_choose), { onChoose(outcome) }, Modifier.padding(top = 4.dp),
                        icon = GameIcons.Seal)
                }
                OrnamentRule(Atlas.Ember.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
internal fun outcomeLines(chapter: Chapter, outcome: ChapterOutcome): List<String> = buildList {
    add(stringResource(Res.string.outcome_people, outcome.people, outcome.homes))
    if (outcome.settlements > 1) add(stringResource(Res.string.outcome_two_places))
    if (outcome.departed.isEmpty()) add(stringResource(Res.string.outcome_nobody_left))
    for ((cause, count) in outcome.departed.toList().sortedBy { it.first.ordinal }) add(stringResource(when (cause) {
        Hardship.Thirst -> Res.string.outcome_left_thirst
        Hardship.Hunger -> Res.string.outcome_left_hunger
        Hardship.Crowding -> Res.string.outcome_left_crowding
    }, count))
    if (outcome.well) add(stringResource(Res.string.outcome_well))
    if (chapter == Chapter.FadingGrove && outcome.groves > 0) add(stringResource(Res.string.outcome_groves, outcome.groves))
    add(stringResource(Res.string.outcome_wildlife, outcome.deer, outcome.rabbits, outcome.foxes))
    if (chapter == Chapter.LastCrossing) {
        add(stringResource(Res.string.outcome_calves, outcome.calves))
        if (outcome.refuges > 0) add(stringResource(Res.string.outcome_refuge))
    }
    outcome.growthPlan?.let { add(stringResource(Res.string.outcome_plan, stringResource(it.title()))) }
    if (outcome.growthPlan != null || outcome.waterPlan != null) {
        add(stringResource(if (outcome.keeperChose) Res.string.outcome_keeper else Res.string.outcome_village))
    }
}

/** After closing: what opened, and where to go next. */
@Composable
internal fun ChapterClosed(story: Story, branchName: String, onNext: (() -> Unit)?, onStay: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier.widthIn(max = 420.dp), padding = 20.dp) {
        SectionLabel(stringResource(Res.string.chapter_numeral, story.numeral))
        Text(stringResource(Res.string.close_done), Modifier.semantics { heading() }, style = MaterialTheme.typography.displaySmall)
        Text(stringResource(Res.string.close_remembered, branchName), style = MaterialTheme.typography.bodyLarge)
        story.next?.let { Text(stringResource(Res.string.close_unlocked, stringResource(it.title)), color = Atlas.Gold,
            style = MaterialTheme.typography.titleMedium) }
        OrnamentRule()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val next = story.next
            if (next != null && onNext != null) StoryButton(stringResource(Res.string.close_begin_next, stringResource(next.title)), onNext,
                icon = GameIcons.Play)
            StoryButton(stringResource(Res.string.close_keep), onStay, primary = next == null || onNext == null, onDark = true)
        }
    }
}

@Composable
internal fun Kicker(key: IslandSessionKey): String {
    val story = Story.of(key)
    return if (story != null) stringResource(Res.string.chapter_numeral, story.numeral) else stringResource(sessionKind(key))
}

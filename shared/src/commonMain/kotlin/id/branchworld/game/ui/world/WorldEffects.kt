package id.branchworld.game.ui.world

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import id.branchworld.game.domain.HandResult
import id.branchworld.game.domain.MusicPolicy
import id.branchworld.game.domain.playMusic
import id.branchworld.game.domain.session.ComparisonView
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.domain.session.InterventionFeedback
import id.branchworld.game.domain.useHand
import id.branchworld.game.domain.worldAmbience
import id.branchworld.game.platform.sound.WorldAmbience
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.game.platform.sound.WorldSoundPlayer
import id.branchworld.game.ui.world.hud.HandMessages
import id.branchworld.simulation.core.ShapingRulesVersion
import id.branchworld.simulation.ecology.Intervention
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldRenderProjection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Sound and attention for an open world. */
internal class WorldSoundState(
    val sounds: WorldSoundPlayer?,
    /** The player has world sounds on. */
    val enabled: Boolean,
    /** The window is in front and the app is running, so the world may be heard. */
    val active: Boolean,
    /** The player has music on. */
    val music: Boolean = false,
) {
    fun play(sound: WorldSound) {
        if (enabled) sounds?.play(sound)
    }
}

/**
 * What an open world does on its own while the interface shows it: run its clock, answer taps of the player's hand,
 * sound its weather and fire, and let notes and camera focus lapse.
 */
@Composable
internal fun WorldEffects(
    session: IslandLoad.Ready?,
    ui: WorldScreenState,
    isWorld: Boolean,
    pose: WorldRenderProjection?,
    comparison: ComparisonView?,
    interventions: InterventionFeedback?,
    sound: WorldSoundState,
    lifecycle: Lifecycle,
    foreground: Boolean,
    playbackState: Lifecycle.State,
    /** Whether this world is still the one on screen; read each tick by the running clock. */
    stillShowing: () -> Boolean,
    messages: HandMessages,
    onComparisonClosed: () -> Unit,
) {
    val living = session?.key?.living == true
    val clock = session?.clock
    val planting = interventions?.applied?.firstOrNull { it.kind == Intervention.Reforest && it.amount > 0 }
    LaunchedEffect(session) { ui.plantingNotice = null }
    LaunchedEffect(session, planting) {
        if (session == null || planting == null) {
            ui.plantingNotice = null
            ui.announcedPlanting = null
            return@LaunchedEffect
        }
        val terrain = session.key.terrain
        val event = "${terrain.worldgenVersion}/${terrain.seed}/${session.timelineView.value.branchId}/${planting.tick}/${planting.region}"
        if (ui.announcedPlanting == event) return@LaunchedEffect
        ui.announcedPlanting = event
        ui.plantingNotice = planting.amount
        sound.play(WorldSound.Grove)
        delay(5_000)
        ui.plantingNotice = null
    }
    val shower = pose?.shaping?.shower
    val liveRain = interventions?.applied?.firstOrNull { it.kind == Intervention.Rain && it.amount > 0 }
    LaunchedEffect(session, liveRain) {
        if (comparison == null && liveRain != null) sound.play(WorldSound.RainChime)
    }
    // A story or sandbox begun from the menus starts its clock once the island is ready.
    LaunchedEffect(session, ui.autoStart, isWorld) {
        if (!ui.autoStart || !isWorld || clock == null) return@LaunchedEffect
        ui.autoStart = false
        if (clock.state.tick == 0L && !clock.playback.value.playing) {
            if (session.chapter == Chapter.FadingGrove && session.key.rules >= ShapingRulesVersion) clock.playTo(240) else clock.togglePlaying()
        }
    }
    val fire = pose?.fire
    val thunderAt = pose?.takeIf { fire?.lightning != null }?.let { it.tick - checkNotNull(fire).lightningAge }
    LaunchedEffect(session, pose?.branchId, thunderAt) {
        // A still image after seeking shows the bolt without replaying its sound.
        if (sound.active && isWorld && comparison == null && pose?.interpolateCreatures == true && thunderAt != null) {
            sound.play(WorldSound.Thunder)
        }
    }
    sound.sounds?.let { sounds ->
        val sink by rememberUpdatedState(sounds)
        val mix = worldAmbience(sound.enabled, sound.active, pose?.climate, calledRain = shower != null,
            dryStorm = !fire?.storm.isNullOrEmpty(), burning = !fire?.burning.isNullOrEmpty())
        LaunchedEffect(mix) { sink.setAmbience(mix) }
        DisposableEffect(Unit) { onDispose { sink.setAmbience(WorldAmbience.Silent) } }
        // Music follows the same weather as the soundscape, even when the soundscape itself is switched off.
        val weather = worldAmbience(enabled = true, active = true, pose?.climate, calledRain = shower != null,
            dryStorm = !fire?.storm.isNullOrEmpty(), burning = !fire?.burning.isNullOrEmpty())
        val mood = MusicPolicy.mood(isWorld, weather)
        val moods = remember { MutableStateFlow(mood) }
        SideEffect { moods.value = mood }
        LaunchedEffect(sounds, sound.music && sound.active, isWorld) {
            if (sound.music && sound.active) playMusic(sounds, isWorld, moods)
        }
    }
    LaunchedEffect(clock, lifecycle, isWorld, foreground, playbackState) {
        if (clock != null && isWorld && foreground) {
            lifecycle.repeatOnLifecycle(playbackState) {
                clock.run(isActive = { stillShowing() && lifecycle.currentState.isAtLeast(playbackState) })
            }
        }
    }
    val tool = ui.tool
    LaunchedEffect(session, living, tool, isWorld) {
        val island = session ?: return@LaunchedEffect
        if (!living || !isWorld) return@LaunchedEffect
        island.world.pointer.taps.collect { pick ->
            if (ui.overlay != null || comparison != null) return@collect
            when (val result = island.useHand(tool, pick)) {
                is HandResult.Inspect -> {
                    ui.selectedId = result.creatureId
                    result.creatureId?.let { ui.requestFocus(WorldCamera.Focus.Creature(it)) }
                }
                is HandResult.Done -> sound.play(result.sound)
                is HandResult.NeedsNewHistory -> ui.pastPick = result.pick
                is HandResult.Refused -> ui.note(messages.of(result.check, tool))
                HandResult.Busy -> ui.note(messages.busy)
                HandResult.NothingToLift -> ui.note(messages.erase)
                HandResult.Ignored -> Unit
            }
        }
    }
    LaunchedEffect(session, pose?.branchId?.takeIf { comparison == null }, comparison != null) {
        ui.focus = null
        if (comparison == null) onComparisonClosed()
    }
    val creatureFocus = ui.focus as? WorldCamera.Focus.Creature
    if (creatureFocus != null && pose?.creatures?.none { it.id == creatureFocus.id } == true) {
        LaunchedEffect(creatureFocus) { ui.focus = null }
    }
    val noteText = ui.handNote
    LaunchedEffect(noteText, ui.handNoteAt) {
        if (noteText != null) {
            delay(2_600)
            ui.clearNote()
        }
    }
}

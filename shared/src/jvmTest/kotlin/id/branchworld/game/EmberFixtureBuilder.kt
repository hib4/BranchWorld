package id.branchworld.game

import id.branchworld.game.data.AtlasEntry
import id.branchworld.game.data.ChapterRecord
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.data.PlayerProfileCodec
import id.branchworld.game.ui.legacy.Story
import id.branchworld.simulation.core.FireRulesVersion
import id.branchworld.simulation.core.canonicalHash
import id.branchworld.simulation.ecology.Intervention
import id.branchworld.simulation.ecology.canonicalHash
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.fire.tileCell
import id.branchworld.simulation.ecology.region
import id.branchworld.simulation.ecology.settlement.PlanOption
import id.branchworld.simulation.legacy.canonicalHash
import id.branchworld.simulation.save.TimelineSaveCodec
import id.branchworld.simulation.save.WorldCode
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.timeline.Timeline
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

/**
 * Builds real Ember Season histories for capture runs: from the dry storm, the village fells its woods, the Keeper
 * cuts a firebreak, the grove is kept and left to burn, and rain falls on the ridge. Each line of
 * build/verification/living-worlds-09/fixture.txt is `<folder> <history 1-5> <tick>`: the history shown and the tick.
 */
class EmberFixtureBuilder {
    @Test
    fun buildFixtures(): Unit = runBlocking {
        val root = File(System.getProperty("user.dir")).parentFile
        val request = File(root, "build/verification/living-worlds-09/fixture.txt").takeIf { it.exists() } ?: return@runBlocking
        val timeline = Timeline.start(IslandGenerator.generate(Story.EmberSeason.key.terrain), FireRulesVersion, Chapter.EmberSeason)
        while (timeline.state.shaping?.warningAt == null) check(timeline.advance())
        val warning = timeline.state.tick
        val strike = tileCell(timeline.state.fire!!.strikes.first().tile).region()
        val village = timeline.state.settlements!!.sites.first().center.region()
        fun Timeline.advanceTo(tick: Long) { while (state.tick < tick) check(advance()) }
        val horizon = 4_800L
        for ((name, act) in listOf<Pair<String, Timeline.() -> Unit>>(
            "The village fells" to { },
            "A firebreak" to { check(endorse(PlanOption.KeepGrove)); check(intervene(Intervention.Firebreak, village)) },
            "Let it burn" to { check(endorse(PlanOption.KeepGrove)) },
            "Rain on the ridge" to { check(endorse(PlanOption.KeepGrove)); check(intervene(Intervention.Rain, strike)) },
        )) {
            timeline.show(checkNotNull(timeline.seek(1, warning)).replay())
            check(timeline.fork(name))
            timeline.act()
            timeline.advanceTo(horizon)
        }
        timeline.show(checkNotNull(timeline.seek(1, warning)).replay())
        timeline.advanceTo(horizon)
        val records = listOf(
            ChapterRecord(Chapter.FadingGrove.code, Story.FadingGrove.key.slot, 2, "Rain at Hollowmere", 1000, 9, 0, 6, 3),
            ChapterRecord(Chapter.LastCrossing.code, Story.LastCrossing.key.slot, 2, "A refuge for the herd", 7200, 8, 1, 7, 4),
        )
        for (line in request.readLines().filter { it.isNotBlank() }) {
            val (folderName, history, tickText) = line.trim().split(" ")
            val folder = File(folderName).also { File(it, "worlds").mkdirs() }
            timeline.show(checkNotNull(timeline.seek(history.toInt(), tickText.toLong())).replay())
            val checkpoint = timeline.checkpoint()
            File(folder, "worlds/story-ember-season.json").writeBytes(TimelineSaveCodec.encode(checkpoint))
            val active = checkpoint.branches.first { it.id == timeline.branchId }
            val code = WorldCode.encode(checkpoint)
            File(folder, "world-code.txt").writeText("$code\n")
            val entry = AtlasEntry(Story.EmberSeason.key.slot, Story.EmberSeason.key.terrain.seed, Story.EmberSeason.key.terrain.worldgenVersion,
                FireRulesVersion, Chapter.EmberSeason.code, active.head.tick, checkpoint.branches.size, active.name, 3)
            val profile = PlayerProfile(chapters = records, atlas = listOf(entry), legacyImported = true)
            File(folder, "worlds/profile.json").writeBytes(PlayerProfileCodec.encode(profile))
            println("EMBER_FIXTURE $folderName history=$history tick=${timeline.state.tick} headHash=${active.head.canonicalHash()} viewHash=${timeline.state.canonicalHash()} burning=${timeline.state.fire!!.burning.size} scars=${timeline.state.fire!!.scars.size}")
        }
    }
}

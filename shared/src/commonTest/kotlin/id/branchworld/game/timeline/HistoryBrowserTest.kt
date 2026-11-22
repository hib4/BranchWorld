package id.branchworld.game.timeline

import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.EmberSeed
import id.branchworld.game.ecology.FireRulesVersion
import id.branchworld.game.ecology.PlanOption
import id.branchworld.game.ecology.WorldChronicle
import id.branchworld.game.ecology.WorldChronicleLimit
import id.branchworld.game.ecology.WorldEventKind
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryBrowserTest {
    @Test
    fun retainedSnapshotsRestoreEvictedCausesAndReferencesStillIdentifyTheMoment() {
        val terrain = IslandGenerator.generate(TerrainKey(EmberSeed, WatershedWorldgenVersion))
        val opening = Timeline.start(terrain, FireRulesVersion, Chapter.EmberSeason).state
        val site = GridCell(0, 0)
        val (storm, stormRef) = WorldChronicle().record(10, WorldEventKind.DryStorm, site)
        val (started, fireRef) = storm.record(20, WorldEventKind.FireStarted, site, causes = listOf(stormRef))
        val later = (0 until WorldChronicleLimit).fold(started) { history, index ->
            history.record(21L + index, WorldEventKind.MeadowReturned, site).first
        }
        val final = later.record(150, WorldEventKind.HomeBurned, site, causes = listOf(fireRef)).first
        val home = final.events.last()
        val head = opening.copy(tick = 150, chronicle = final)
        val saved = opening.copy(tick = 20, chronicle = started)
        assertEquals(null, final.find(fireRef.id))

        val fromHead = HistoryBrowser(listOf(HistorySegment(1, null, 0, 150, listOf(head), emptyList())))
        assertEquals(listOf(fireRef), fromHead.causesOf(home).map { it.ref })
        assertEquals(1, fromHead.causesOf(home).single().branchId)

        val fromSnapshot = HistoryBrowser(listOf(HistorySegment(1, null, 0, 150, listOf(saved, head), emptyList())))
        assertEquals(listOf(fireRef, stormRef), fromSnapshot.causesOf(home).map { it.ref })
        assertEquals(1, fromSnapshot.entries.filterIsInstance<HistoryEntry.World>().count { it.event.ref == stormRef })
    }

    @Test
    fun causesVisitTheirOwningBranchAndRowsExcludeSiblingEvents() = runBlocking {
        val terrain = IslandGenerator.generate(TerrainKey(EmberSeed, WatershedWorldgenVersion))
        val timeline = Timeline.start(terrain, FireRulesVersion, Chapter.EmberSeason)
        while (timeline.state.shaping?.warningAt == null) timeline.advance()
        val warning = timeline.state.tick
        assertTrue(timeline.fork("Keep the pines"))
        assertTrue(timeline.endorse(PlanOption.KeepGrove))
        while (timeline.state.tick < 900) timeline.advance()
        val history = timeline.history(2)
        val home = history.entries.filterIsInstance<HistoryEntry.World>().single { it.event.kind == WorldEventKind.HomeBurned }
        val chain = history.causesOf(home.event)
        assertEquals(listOf(WorldEventKind.FireStarted, WorldEventKind.Lightning, WorldEventKind.DryStorm), chain.map { it.ref.kind })
        assertEquals(listOf(2, 2, 1), chain.map { it.branchId })
        assertEquals(1 to warning, timeline.historyLocation(2, warning))
        assertEquals(1, history.entries.filterIsInstance<HistoryEntry.World>().count { it.event.kind == WorldEventKind.DryStorm })
        assertTrue(timeline.history(1).entries.none { it is HistoryEntry.World && it.event.kind == WorldEventKind.HomeBurned })
    }
}

package id.branchworld.game

import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.RangeRulesVersion
import id.branchworld.game.ecology.ShapingRulesVersion
import id.branchworld.game.ecology.Species
import id.branchworld.game.save.AtlasEntry
import id.branchworld.game.save.Bookmark
import id.branchworld.game.save.ChapterRecord
import id.branchworld.game.save.PlayerProfile
import id.branchworld.game.save.PlayerProfileCodec
import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.save.WorldSaveStore
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion
import id.branchworld.game.timeline.Timeline
import id.branchworld.game.timeline.WorldCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtlasTest {
    private class MemoryStore(var legacy: ByteArray? = null) : WorldSaveStore {
        val files = linkedMapOf<String, ByteArray>()
        var legacyWrites = 0
        override suspend fun read(maxBytes: Int) = legacy
        override suspend fun write(bytes: ByteArray, replace: Boolean): Boolean {
            legacyWrites++
            return false
        }
        override suspend fun readFile(name: String, maxBytes: Int) = files[name]
        override suspend fun writeFile(name: String, bytes: ByteArray): Boolean {
            files[name] = bytes
            return true
        }
        override suspend fun createFile(name: String, bytes: ByteArray): Boolean =
            if (name in files) false else writeFile(name, bytes)
    }

    @Test
    fun everyIslandHasItsOwnSlot() {
        assertEquals("story-fading-grove.json", Story.FadingGrove.key.slot)
        assertEquals("story-last-crossing.json", Story.LastCrossing.key.slot)
        assertEquals("sandbox-271828-w2-r11.json", IslandSessionKey.sandbox(271828).slot)
        assertEquals("sandbox-n0-w2-r11.json", IslandSessionKey.sandbox(-1).slot)
        // Islands shaped in the rules-v10 sandbox keep their original slots and open under their own rules.
        assertEquals("sandbox-271828-w2.json", IslandSessionKey(TerrainKey(271828, WatershedWorldgenVersion), RangeRulesVersion).slot)
        assertEquals("story-ember-season.json", Story.EmberSeason.key.slot)
        assertEquals(Story.EmberSeason, Story.LastCrossing.next)
        assertEquals("island-104729-w2-r8.json", IslandSessionKey(IslandGallery[0], ShapingRulesVersion).slot)
        assertEquals(Chapter.FadingGrove, Story.FadingGrove.key.story)
        assertNull(IslandSessionKey.sandbox(FadingGroveSeedForTest).story)
        assertTrue(IslandSessionKey.sandbox(1).sandbox)
        val names = listOf(Story.FadingGrove.key, Story.LastCrossing.key, IslandSessionKey.sandbox(Long.MIN_VALUE)).map { it.slot }
        assertTrue(names.all { id.branchworld.game.save.SaveFileName.matches(it) })
    }

    @Test
    fun theJourneyOpensChapterByChapter() {
        val fresh = PlayerProfile()
        assertTrue(Story.FadingGrove.unlocked(fresh))
        assertFalse(Story.LastCrossing.unlocked(fresh))
        val closed = fresh.record(ChapterRecord(Chapter.FadingGrove.code, Story.FadingGrove.key.slot, 2, "Rain", 1000, 7, 0, 4, 3))
        assertTrue(Story.LastCrossing.unlocked(closed))
        assertEquals(Story.LastCrossing, Story.FadingGrove.next)
    }

    @Test
    fun theProfileRoundTripsAndForgetsNothingSilently() {
        val entry = AtlasEntry(Story.LastCrossing.key.slot, 271828, WatershedWorldgenVersion, RangeRulesVersion, Chapter.LastCrossing.code,
            7200, 3, "A refuge for the herd")
        val mark = Bookmark(entry.slot, 2, 960, "A refuge for the herd")
        val profile = PlayerProfile().visit(entry).visit(entry.copy(tick = 7300)).mark(mark).mark(mark).see("look").see("look")
            .copy(settings = PlayerProfile().settings.copy(textScale = 130, reducedMotion = true))
        assertEquals(1, profile.atlas.size)
        assertEquals(7300, profile.lastPlayed?.tick)
        assertEquals(listOf(mark), profile.bookmarks)
        assertEquals(listOf("look"), profile.hints)
        assertEquals(profile, PlayerProfileCodec.decode(PlayerProfileCodec.encode(profile)))
        assertEquals(Story.LastCrossing.key, profile.lastPlayed?.key())
        assertTrue(profile.unmark(mark).bookmarks.isEmpty())
        assertNull(PlayerProfileCodec.decode("{not json".encodeToByteArray()))
        assertNull(PlayerProfileCodec.decode("""{"version":99}""".encodeToByteArray()))
    }

    @Test
    fun theOriginalSaveIsImportedOnceAndNeverRewritten() = runSimulationTest {
        val terrain = IslandGenerator.generate(Story.FadingGrove.key.terrain)
        val timeline = Timeline.start(terrain)
        while (timeline.state.tick < 300) assertTrue(timeline.advance())
        val original = TimelineSaveCodec.encode(timeline.checkpoint())
        val store = MemoryStore(original.copyOf())
        val session = IslandSession()

        val entry = assertNotNull(session.importLegacy(store))
        assertEquals(Story.FadingGrove.key.slot, entry.slot)
        assertEquals(300, entry.tick)
        assertContentEquals(original, store.files.getValue(entry.slot))
        assertContentEquals(original, store.legacy)
        assertEquals(0, store.legacyWrites)

        // A second import leaves the island's own, newer history alone.
        store.files[entry.slot] = "newer".encodeToByteArray()
        assertNotNull(session.importLegacy(store))
        assertContentEquals("newer".encodeToByteArray(), store.files.getValue(entry.slot))
    }

    @Test
    fun anIslandReopensFromItsSlotAndAutosavesBack() = runSimulationTest {
        val store = MemoryStore()
        val key = IslandSessionKey(TerrainKey(271828, WatershedWorldgenVersion), RangeRulesVersion, Chapter.LastCrossing)
        val first = IslandSession()
        first.open(key, store)
        val island = assertIs(first.state.value)
        assertEquals(key, island.key)
        assertEquals(Chapter.LastCrossing, island.chapter)
        island.clock.playTo(120)
        val clock = launch { island.clock.run(isActive = { true }, wait = {}) }
        while (island.clock.state.tick < 120) yield()
        clock.cancel()
        val saved = assertNotNull(first.autosave(store))
        assertEquals(120, saved.tick)
        assertEquals(Chapter.LastCrossing.code, saved.chapter)

        val second = IslandSession()
        second.open(key, store)
        val reopened = assertIs(second.state.value)
        assertEquals(island.clock.state, reopened.clock.state)

        // A slot that cannot be read is reported and never overwritten by autosave.
        store.files[key.slot] = "damaged".encodeToByteArray()
        val third = IslandSession()
        third.open(key, store)
        assertEquals(SaveStatus.Corrupt, third.saveStatus.value)
        third.acknowledgeRestore(0)
        assertNull(third.autosave(store))
        assertContentEquals("damaged".encodeToByteArray(), store.files.getValue(key.slot))
    }

    @Test
    fun importingAgainReopensTheSharedCopyWithoutReplacingIt() = runSimulationTest {
        val story = Story.EmberSeason
        val timeline = Timeline.start(IslandGenerator.generate(story.key.terrain), story.key.rules, story.chapter)
        val code = WorldCode.encode(timeline.checkpoint())
        val original = TimelineSaveCodec.encode(timeline.checkpoint())
        val store = MemoryStore(original).apply { files[story.key.slot] = original }
        val session = IslandSession()
        val imported = kotlin.test.assertIs<WorldCodeImport.Open>(session.importCode(code, store)).entry
        val importedKey = imported.key()
        assertTrue(importedKey.isShared)
        assertEquals(importedKey, ShellState().selectIsland(importedKey).openWorld().island)
        session.open(importedKey, store)
        val world = assertIs(session.state.value)
        assertEquals(story.chapter, world.chapter)
        assertEquals(importedKey, world.key)
        world.clock.playTo(120)
        val clock = launch { world.clock.run(isActive = { true }, wait = {}) }
        while (world.clock.state.tick < 120) yield()
        clock.cancel()
        assertEquals(120, assertNotNull(session.autosave(store)).tick)

        val second = IslandSession()
        val reopened = kotlin.test.assertIs<WorldCodeImport.Open>(second.importCode(code, store)).entry
        assertEquals(120, reopened.tick)
        second.open(reopened.key(), store)
        assertEquals(world.clock.state, assertIs(second.state.value).clock.state)
        assertContentEquals(original, store.files.getValue(story.key.slot))
        assertContentEquals(original, store.legacy)
    }

    @Test
    fun chaptersCloseOnlyAfterTheirCrisisAndReportRealOutcomes() = runSimulationTest {
        val grove = Timeline.start(IslandGenerator.generate(Story.FadingGrove.key.terrain))
        while (grove.state.tick < 300) assertTrue(grove.advance())
        assertTrue(grove.fork("Rain at Hollowmere"))
        assertFalse(grove.canCloseChapter)
        assertTrue(grove.intervene(Intervention.Rain, grove.recommendedRainRegion()))
        while (grove.state.tick < 899) assertTrue(grove.advance())
        assertFalse(grove.canCloseChapter)
        assertTrue(grove.advance())
        assertTrue(grove.canCloseChapter)
        val outcomes = grove.outcomes()
        assertEquals(listOf("Main", "Rain at Hollowmere"), outcomes.map { it.branch.name })
        val rain = outcomes.single { it.branch.id == grove.branchId }
        assertEquals(grove.state.settlements!!.sites.sumOf { it.residents }, rain.people)
        assertEquals(grove.state.creatures.count { it.species == Species.Deer }, rain.deer)
        assertEquals(900, rain.tick)

        val crossing = Timeline.start(IslandGenerator.generate(Story.LastCrossing.key.terrain), RangeRulesVersion, Chapter.LastCrossing)
        while (crossing.state.habitat!!.herd.crossings < 3) {
            assertTrue(crossing.advance())
            if (crossing.state.habitat!!.herd.crossings < 3) assertFalse(crossing.canCloseChapter)
        }
        assertTrue(crossing.state.tick > 2400)
        assertTrue(crossing.canCloseChapter)
    }

    private fun assertIs(load: IslandLoad): IslandLoad.Ready = load as? IslandLoad.Ready ?: error("Expected a ready island, got $load")

    private companion object {
        const val FadingGroveSeedForTest = 104729L
    }
}

package id.branchworld.game

import androidx.compose.runtime.saveable.listSaver
import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.CivilizationRulesVersion
import id.branchworld.game.ecology.CrossingSeed
import id.branchworld.game.ecology.FadingGroveSeed
import id.branchworld.game.ecology.EmberSeed
import id.branchworld.game.ecology.FireRulesVersion
import id.branchworld.game.ecology.LifeRulesVersion
import id.branchworld.game.ecology.WorldStart
import id.branchworld.game.ecology.RangeRulesVersion
import id.branchworld.game.ecology.ShapingRulesVersion
import id.branchworld.game.ecology.VillageRulesVersion
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion

internal val SharedWorldSlot = Regex("shared-[0-9a-f]{8}\\.json")
/** A living world's own save slot, numbered from the profile. */
internal val LivingWorldSlot = Regex("world-[0-9]{1,9}\\.json")

internal enum class ShellRoute(val savedId: String) {
    Home("home"),
    World("world"),
}

/** The menus reachable from the title screen. The live island stays behind every one of them. */
internal enum class HomeScreen(val savedId: String) {
    Title("title"), Stories("stories"), Atlas("atlas"), Sandbox("sandbox"), Settings("settings"),
    /** Living worlds: create one, or reopen one the player made. */
    NewWorld("new"), Worlds("worlds"),
}

internal val IslandSeeds = listOf(FadingGroveSeed, CrossingSeed, EmberSeed)
internal val IslandGallery = IslandSeeds.map { TerrainKey(it, WatershedWorldgenVersion) }

internal data class IslandSessionKey(
    val terrain: TerrainKey,
    val rules: Int = if (terrain.worldgenVersion == WatershedWorldgenVersion) VillageRulesVersion else CivilizationRulesVersion,
    /** The crafted story of a rules-v10 history. The Fading Grove is implied by its seed under rules v8–v9. */
    val chapter: Chapter? = null,
    val sharedSlot: String? = null,
    /** A living world's own slot. */
    val worldSlot: String? = null,
    /** How a new living world begins. */
    val start: WorldStart? = null,
) {
    /** Rules v12: a freeform living world rather than a story or an earlier sandbox. */
    val living: Boolean get() = rules >= LifeRulesVersion

    val story: Chapter? get() = chapter ?: Chapter.FadingGrove.takeIf {
        rules in ShapingRulesVersion..VillageRulesVersion && terrain == IslandGallery.first()
    }

    val sandbox: Boolean get() = rules >= RangeRulesVersion && chapter == null
    val isShared: Boolean get() = sharedSlot != null

    /** The island's own save slot in the World Atlas. */
    val slot: String get() {
        sharedSlot?.let { return it }
        worldSlot?.let { return it }
        val seed = if (terrain.seed < 0) "n${-(terrain.seed + 1)}" else terrain.seed.toString()
        return when {
            living -> "world-$seed.json"
            chapter == Chapter.LastCrossing -> "story-last-crossing.json"
            chapter == Chapter.EmberSeason -> "story-ember-season.json"
            story == Chapter.FadingGrove && rules == VillageRulesVersion -> "story-fading-grove.json"
            // Rules-v11 sandboxes have their own slot, so islands shaped under earlier rules keep theirs.
            sandbox && rules >= FireRulesVersion -> "sandbox-$seed-w${terrain.worldgenVersion}-r$rules.json"
            sandbox -> "sandbox-$seed-w${terrain.worldgenVersion}.json"
            else -> "island-$seed-w${terrain.worldgenVersion}-r$rules.json"
        }
    }

    companion object {
        fun sandbox(seed: Long) = IslandSessionKey(TerrainKey(seed, WatershedWorldgenVersion), FireRulesVersion)

        fun living(seed: Long, slot: String, start: WorldStart) =
            IslandSessionKey(TerrainKey(seed, WatershedWorldgenVersion), LifeRulesVersion, worldSlot = slot, start = start)
    }
}

internal data class ShellState(
    val route: ShellRoute = ShellRoute.Home,
    val diagnosticsVisible: Boolean = false,
    val terrain: TerrainKey = IslandGallery.first(),
    val rules: Int = IslandSessionKey(terrain).rules,
    val chapter: Chapter? = null,
    val screen: HomeScreen = HomeScreen.Title,
    val sharedSlot: String? = null,
    val worldSlot: String? = null,
    val start: WorldStart? = null,
) {
    val seed: Long get() = terrain.seed
    val island: IslandSessionKey get() = IslandSessionKey(terrain, rules, chapter, sharedSlot, worldSlot, start)
    fun openWorld(): ShellState = copy(route = ShellRoute.World, diagnosticsVisible = false, screen = HomeScreen.Title)

    fun selectIsland(key: TerrainKey): ShellState = selectIsland(IslandSessionKey(key))

    fun selectIsland(key: IslandSessionKey): ShellState =
        if (route == ShellRoute.Home) copy(terrain = key.terrain, rules = key.rules, chapter = key.chapter, sharedSlot = key.sharedSlot,
            worldSlot = key.worldSlot, start = key.start) else this

    fun show(next: HomeScreen): ShellState = if (route == ShellRoute.Home) copy(screen = next) else this

    fun toggleDiagnostics(): ShellState =
        if (route == ShellRoute.World) copy(diagnosticsVisible = !diagnosticsVisible) else this

    fun back(): ShellState = copy(route = ShellRoute.Home, diagnosticsVisible = false, screen = HomeScreen.Title)

    companion object {
        val saver = listSaver<ShellState, Any>(
            save = {
                listOf(it.route.savedId, it.diagnosticsVisible, it.seed, it.terrain.worldgenVersion, it.rules,
                    it.chapter?.name ?: "", it.screen.savedId, it.sharedSlot.orEmpty(), it.worldSlot.orEmpty(), it.start?.name.orEmpty())
            },
            restore = { values ->
                val route = ShellRoute.entries.find { it.savedId == values.getOrNull(0) }
                val diagnosticsVisible = values.getOrNull(1) as? Boolean
                if (route == null || diagnosticsVisible == null) {
                    null
                } else {
                    val seed = values.getOrNull(2) as? Long
                    val key = seed?.let { TerrainKey(it, values.getOrNull(3) as? Int ?: 1) } ?: IslandGallery.first()
                    ShellState(route, diagnosticsVisible && route == ShellRoute.World, key,
                        values.getOrNull(4) as? Int ?: IslandSessionKey(key).rules,
                        Chapter.entries.find { it.name == values.getOrNull(5) },
                        HomeScreen.entries.find { it.savedId == values.getOrNull(6) } ?: HomeScreen.Title,
                        (values.getOrNull(7) as? String)?.takeIf(SharedWorldSlot::matches),
                        (values.getOrNull(8) as? String)?.takeIf(LivingWorldSlot::matches),
                        WorldStart.entries.find { it.name == values.getOrNull(9) })
                }
            },
        )
    }
}

package id.branchworld.game

import androidx.compose.runtime.saveable.listSaver
import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.CivilizationRulesVersion
import id.branchworld.game.ecology.CrossingSeed
import id.branchworld.game.ecology.FadingGroveSeed
import id.branchworld.game.ecology.RangeRulesVersion
import id.branchworld.game.ecology.ShapingRulesVersion
import id.branchworld.game.ecology.VillageRulesVersion
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion

internal enum class ShellRoute(val savedId: String) {
    Home("home"),
    World("world"),
}

/** The menus reachable from the title screen. The live island stays behind every one of them. */
internal enum class HomeScreen(val savedId: String) {
    Title("title"), Stories("stories"), Atlas("atlas"), Sandbox("sandbox"), Settings("settings"),
}

internal val IslandSeeds = listOf(FadingGroveSeed, CrossingSeed, 424242L)
internal val IslandGallery = IslandSeeds.map { TerrainKey(it, WatershedWorldgenVersion) }

internal data class IslandSessionKey(
    val terrain: TerrainKey,
    val rules: Int = if (terrain.worldgenVersion == WatershedWorldgenVersion) VillageRulesVersion else CivilizationRulesVersion,
    /** The crafted story of a rules-v10 history. The Fading Grove is implied by its seed under rules v8–v9. */
    val chapter: Chapter? = null,
) {
    val story: Chapter? get() = chapter ?: Chapter.FadingGrove.takeIf {
        rules in ShapingRulesVersion..VillageRulesVersion && terrain == IslandGallery.first()
    }

    val sandbox: Boolean get() = rules >= RangeRulesVersion && chapter == null

    /** The island's own save slot in the World Atlas. */
    val slot: String get() {
        val seed = if (terrain.seed < 0) "n${-(terrain.seed + 1)}" else terrain.seed.toString()
        return when {
            chapter == Chapter.LastCrossing -> "story-last-crossing.json"
            story == Chapter.FadingGrove && rules == VillageRulesVersion -> "story-fading-grove.json"
            sandbox -> "sandbox-$seed-w${terrain.worldgenVersion}.json"
            else -> "island-$seed-w${terrain.worldgenVersion}-r$rules.json"
        }
    }

    companion object {
        fun sandbox(seed: Long) = IslandSessionKey(TerrainKey(seed, WatershedWorldgenVersion), RangeRulesVersion)
    }
}

internal data class ShellState(
    val route: ShellRoute = ShellRoute.Home,
    val diagnosticsVisible: Boolean = false,
    val terrain: TerrainKey = IslandGallery.first(),
    val rules: Int = IslandSessionKey(terrain).rules,
    val chapter: Chapter? = null,
    val screen: HomeScreen = HomeScreen.Title,
) {
    val seed: Long get() = terrain.seed
    val island: IslandSessionKey get() = IslandSessionKey(terrain, rules, chapter)
    fun openWorld(): ShellState = copy(route = ShellRoute.World, diagnosticsVisible = false, screen = HomeScreen.Title)

    fun selectIsland(key: TerrainKey): ShellState = selectIsland(IslandSessionKey(key))

    fun selectIsland(key: IslandSessionKey): ShellState =
        if (route == ShellRoute.Home) copy(terrain = key.terrain, rules = key.rules, chapter = key.chapter) else this

    fun show(next: HomeScreen): ShellState = if (route == ShellRoute.Home) copy(screen = next) else this

    fun toggleDiagnostics(): ShellState =
        if (route == ShellRoute.World) copy(diagnosticsVisible = !diagnosticsVisible) else this

    fun back(): ShellState = copy(route = ShellRoute.Home, diagnosticsVisible = false, screen = HomeScreen.Title)

    companion object {
        val saver = listSaver<ShellState, Any>(
            save = {
                listOf(it.route.savedId, it.diagnosticsVisible, it.seed, it.terrain.worldgenVersion, it.rules,
                    it.chapter?.name ?: "", it.screen.savedId)
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
                        HomeScreen.entries.find { it.savedId == values.getOrNull(6) } ?: HomeScreen.Title)
                }
            },
        )
    }
}

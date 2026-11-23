package id.branchworld.game.ui.app.navigation

import androidx.compose.runtime.saveable.listSaver
import id.branchworld.game.domain.IslandGallery
import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.game.domain.LivingWorldSlot
import id.branchworld.game.domain.SharedWorldSlot
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.life.WorldStart
import id.branchworld.simulation.terrain.TerrainKey

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

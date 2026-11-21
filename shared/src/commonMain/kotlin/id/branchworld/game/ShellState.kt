package id.branchworld.game

import androidx.compose.runtime.saveable.listSaver
import id.branchworld.game.ecology.CivilizationRulesVersion
import id.branchworld.game.ecology.FadingGroveSeed
import id.branchworld.game.ecology.VillageRulesVersion
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion

internal enum class ShellRoute(val savedId: String) {
    Home("home"),
    World("world"),
}

internal val IslandSeeds = listOf(FadingGroveSeed, 271828L, 424242L)
internal val IslandGallery = IslandSeeds.map { TerrainKey(it, WatershedWorldgenVersion) }

internal data class IslandSessionKey(
    val terrain: TerrainKey,
    val rules: Int = if (terrain.worldgenVersion == WatershedWorldgenVersion) VillageRulesVersion else CivilizationRulesVersion,
)

internal data class ShellState(
    val route: ShellRoute = ShellRoute.Home,
    val diagnosticsVisible: Boolean = false,
    val terrain: TerrainKey = IslandGallery.first(),
    val rules: Int = IslandSessionKey(terrain).rules,
) {
    val seed: Long get() = terrain.seed
    val island: IslandSessionKey get() = IslandSessionKey(terrain, rules)
    fun openWorld(): ShellState = copy(route = ShellRoute.World, diagnosticsVisible = false)

    fun selectIsland(key: TerrainKey): ShellState =
        if (route == ShellRoute.Home && key in IslandGallery) copy(terrain = key, rules = IslandSessionKey(key).rules) else this

    fun toggleDiagnostics(): ShellState =
        if (route == ShellRoute.World) copy(diagnosticsVisible = !diagnosticsVisible) else this

    fun back(): ShellState = copy(route = ShellRoute.Home, diagnosticsVisible = false)

    companion object {
        val saver = listSaver<ShellState, Any>(
            save = { listOf(it.route.savedId, it.diagnosticsVisible, it.seed, it.terrain.worldgenVersion, it.rules) },
            restore = { values ->
                val route = ShellRoute.entries.find { it.savedId == values.getOrNull(0) }
                val diagnosticsVisible = values.getOrNull(1) as? Boolean
                if (route == null || diagnosticsVisible == null) {
                    null
                } else {
                    val seed = values.getOrNull(2) as? Long
                    val key = seed?.let { TerrainKey(it, values.getOrNull(3) as? Int ?: 1) } ?: IslandGallery.first()
                    ShellState(route, diagnosticsVisible && route == ShellRoute.World, key,
                        values.getOrNull(4) as? Int ?: IslandSessionKey(key).rules)
                }
            },
        )
    }
}

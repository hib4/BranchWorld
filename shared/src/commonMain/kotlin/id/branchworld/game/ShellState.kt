package id.branchworld.game

import androidx.compose.runtime.saveable.listSaver

internal enum class ShellRoute(val savedId: String) {
    Home("home"),
    World("world"),
}

internal val IslandSeeds = listOf(104729L, 271828L, 424242L)

internal data class ShellState(
    val route: ShellRoute = ShellRoute.Home,
    val diagnosticsVisible: Boolean = false,
    val seed: Long = IslandSeeds.first(),
) {
    fun openWorld(): ShellState = copy(route = ShellRoute.World, diagnosticsVisible = false)

    fun selectSeed(seed: Long): ShellState =
        if (route == ShellRoute.Home && seed in IslandSeeds) copy(seed = seed) else this

    fun toggleDiagnostics(): ShellState =
        if (route == ShellRoute.World) copy(diagnosticsVisible = !diagnosticsVisible) else this

    fun back(): ShellState = copy(route = ShellRoute.Home, diagnosticsVisible = false)

    companion object {
        val saver = listSaver<ShellState, Any>(
            save = { listOf(it.route.savedId, it.diagnosticsVisible, it.seed) },
            restore = { values ->
                val route = ShellRoute.entries.find { it.savedId == values.getOrNull(0) }
                val diagnosticsVisible = values.getOrNull(1) as? Boolean
                if (route == null || diagnosticsVisible == null) {
                    null
                } else {
                    val seed = (values.getOrNull(2) as? Long)?.takeIf(IslandSeeds::contains) ?: IslandSeeds.first()
                    ShellState(route, diagnosticsVisible && route == ShellRoute.World, seed)
                }
            },
        )
    }
}

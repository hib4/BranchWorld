package id.branchworld.game

import androidx.compose.runtime.saveable.listSaver

internal enum class ShellRoute(val savedId: String) {
    Home("home"),
    World("world"),
}

internal data class ShellState(
    val route: ShellRoute = ShellRoute.Home,
    val diagnosticsVisible: Boolean = false,
) {
    fun openWorld(): ShellState = ShellState(route = ShellRoute.World)

    fun toggleDiagnostics(): ShellState =
        if (route == ShellRoute.World) copy(diagnosticsVisible = !diagnosticsVisible) else this

    fun back(): ShellState = ShellState()

    companion object {
        val saver = listSaver<ShellState, Any>(
            save = { listOf(it.route.savedId, it.diagnosticsVisible) },
            restore = { values ->
                val route = ShellRoute.entries.find { it.savedId == values.getOrNull(0) }
                val diagnosticsVisible = values.getOrNull(1) as? Boolean
                if (route == null || diagnosticsVisible == null) {
                    null
                } else {
                    ShellState(route, diagnosticsVisible && route == ShellRoute.World)
                }
            },
        )
    }
}

package id.branchworld.game

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShellStateTest {
    @Test
    fun startsAtHomeWithDiagnosticsHidden() {
        val state = ShellState()

        assertEquals(ShellRoute.Home, state.route)
        assertFalse(state.diagnosticsVisible)
    }

    @Test
    fun openingTheWorldStartsWithDiagnosticsHidden() {
        val state = ShellState().openWorld()

        assertEquals(ShellRoute.World, state.route)
        assertFalse(state.diagnosticsVisible)
    }

    @Test
    fun diagnosticsToggleOnlyInTheWorld() {
        val home = ShellState()
        val world = home.openWorld()

        assertEquals(home, home.toggleDiagnostics())
        assertTrue(world.toggleDiagnostics().diagnosticsVisible)
        assertEquals(world, world.toggleDiagnostics().toggleDiagnostics())
    }

    @Test
    fun returningHomeClearsDiagnostics() {
        val home = ShellState()
        val diagnostics = home.openWorld().toggleDiagnostics()

        assertEquals(home, diagnostics.back())
        assertFalse(diagnostics.back().openWorld().diagnosticsVisible)
    }

    @Test
    fun saverRestoresTheActiveRouteAndOverlay() {
        val state = ShellState().openWorld().toggleDiagnostics()
        val saver = ShellState.saver
        val saved = assertNotNull(with(saver) { SaverScope { true }.save(state) })

        assertEquals(state, saver.restore(saved))
    }
}

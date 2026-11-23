package id.branchworld.game.ui.app.navigation

import androidx.compose.runtime.saveable.SaverScope
import id.branchworld.game.domain.IslandGallery
import id.branchworld.game.ui.legacy.Story
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
        val state = ShellState().selectIsland(IslandGallery[1]).openWorld().toggleDiagnostics()
        val saver = ShellState.saver
        val saved = assertNotNull(with(saver) { SaverScope { true }.save(state) })

        assertEquals(state, saver.restore(saved))
        assertEquals(271828L, state.back().seed)
        assertEquals(ShellState().openWorld(), saver.restore(listOf("world", false)))
        val legacy = assertNotNull(saver.restore(listOf("world", false, 104729L)))
        assertEquals(1, legacy.terrain.worldgenVersion)
    }

    @Test
    fun saverKeepsTheChosenStoryAndMenu() {
        val state = ShellState().selectIsland(Story.LastCrossing.key).show(HomeScreen.Atlas)
        val saved = assertNotNull(with(ShellState.saver) { SaverScope { true }.save(state) })
        assertEquals(state, ShellState.saver.restore(saved))
        assertEquals(Story.LastCrossing.key, state.island)
        assertEquals(HomeScreen.Title, state.openWorld().back().screen)
    }
}

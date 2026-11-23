package id.branchworld.renderer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.branchworld.game.platform.AppServices
import id.branchworld.game.platform.storage.WorldSaveStore
import id.branchworld.game.platform.viewport.worldViewport
import id.branchworld.game.ui.app.App
import id.branchworld.simulation.projection.WorldRenderSource
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopEmberUiTest {
    /** With the first two chapters closed, a player begins Ember Season, meets the dry storm and cuts a firebreak. */
    @Test
    fun theKeeperCutsAFirebreakBeforeTheStormBreaks() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var viewport: WorldRenderSource? = null
        val store = ProfileStore()
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                App(AppServices(viewport = worldViewport { _, world -> SideEffect { viewport = world } }, saveStore = store))
            }
        }
        developerMode()
        tap("Stories")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Ember Season").fetchSemanticsNodes().isNotEmpty() }
        // Chapter III is the last card on the shelf.
        onAllNodesWithText("Begin").fetchSemanticsNodes().size.let { count -> onAllNodesWithText("Begin")[count - 1].performScrollToAndClick() }
        waitUntil(timeoutMillis = 10_000) { viewport?.poses?.value?.fire != null }
        val world = checkNotNull(viewport)
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Play").fetchSemanticsNodes().isNotEmpty() }
        tap("Play")
        // The storm gathers once the woods have cured, and Sedgewick asks the Keeper what to do.
        waitUntil(timeoutMillis = 40_000) { onAllNodesWithText("A dry storm gathers over Sedgewick").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(world.poses.value.fire!!.storm.size == 2)
        onNodeWithText("Endorse · Keep the old woods").performScrollToAndClick()
        tap("Shape")
        tap("Cut a firebreak")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("stretches", substring = true).fetchSemanticsNodes().isNotEmpty() }
        tap("Cut the firebreak")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Cancel", substring = true).fetchSemanticsNodes().isNotEmpty() }
        tap("Close")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Play").fetchSemanticsNodes().isNotEmpty() }
        tap("Play")
        waitUntil(timeoutMillis = 20_000) { world.poses.value.fire!!.scars.any { it.felled } }
        assertTrue(world.poses.value.fire!!.stands.isNotEmpty() || world.poses.value.fire!!.scars.count { it.felled } >= 3)
    }

    /** On a phone the Shape sheet keeps the chosen power while the story plays on. */
    @Test
    fun phoneShapeSheetKeepsTheFirebreakChoice() = runDesktopComposeUiTest(width = 411, height = 914) {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var viewport: WorldRenderSource? = null
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                App(AppServices(viewport = worldViewport { _, world -> SideEffect { viewport = world } }, saveStore = ProfileStore()))
            }
        }
        developerMode()
        tap("Stories")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Ember Season").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Begin").fetchSemanticsNodes().size.let { count -> onAllNodesWithText("Begin")[count - 1].performScrollToAndClick() }
        waitUntil(timeoutMillis = 10_000) { viewport?.poses?.value?.fire != null }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Play").fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText("Play").fetchSemanticsNodes().isNotEmpty() }
        tap("Play")
        waitUntil(timeoutMillis = 40_000) { onAllNodesWithText("Endorse · Keep the old woods").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Endorse · Keep the old woods").performScrollToAndClick()
        tap("Shape")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Cut a firebreak").fetchSemanticsNodes().isNotEmpty() }
        tap("Cut a firebreak")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("stretches", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // The sheet holds the clock while the Keeper chooses, and the choice stands.
        waitForIdle()
        onAllNodesWithText("stretches", substring = true).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        onNodeWithText("Cut the firebreak").assertExists()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToAndClick() {
        runCatching { performScrollTo() }
        performClick()
    }

    /** A profile in which The Fading Grove and The Last Crossing are closed, so Ember Season is open. */
    private class ProfileStore : WorldSaveStore {
        private val files = mutableMapOf("profile.json" to Profile.encodeToByteArray())
        override suspend fun read(maxBytes: Int): ByteArray? = null
        override suspend fun write(bytes: ByteArray, replace: Boolean): Boolean = false
        override suspend fun readFile(name: String, maxBytes: Int): ByteArray? = files[name]
        override suspend fun writeFile(name: String, bytes: ByteArray): Boolean {
            files[name] = bytes
            return true
        }
        override suspend fun createFile(name: String, bytes: ByteArray) = name !in files && writeFile(name, bytes)
    }

    private companion object {
        const val Profile = """{"version":1,"chapters":[""" +
            """{"chapter":1,"slot":"story-fading-grove.json","branchId":2,"branchName":"Rain","tick":1000,"people":9,"departed":0,"deer":6,"homes":3},""" +
            """{"chapter":2,"slot":"story-last-crossing.json","branchId":2,"branchName":"Refuge","tick":7200,"people":8,"departed":1,"deer":7,"homes":4}""" +
            """],"legacyImported":true}"""
    }
}

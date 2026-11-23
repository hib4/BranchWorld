package id.branchworld.renderer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.branchworld.game.App
import id.branchworld.game.save.WorldSaveStore
import id.branchworld.game.simulation.WorldRenderSource
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DesktopWorldCodeUiTest {
    @Test
    fun atlasImportAndTimelineShareReplayTheSameCode() = runDesktopComposeUiTest(width = 900, height = 750) {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var viewport: WorldRenderSource? = null
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                App(saveStore = MemoryStore, viewport = { _, world -> SideEffect { viewport = world } })
            }
        }
        waitUntil(timeoutMillis = 10_000) { viewport != null }
        tap("My worlds")
        tap("Enter a world code")
        onNode(hasSetTextAction()).performTextReplacement(Fixture)
        tap("Open shared world")
        waitUntil(timeoutMillis = 12_000) {
            viewport?.poses?.value?.let { it.tick == 525L && it.branchId == 2 } == true
        }
        tap("Timeline")
        tap("Share this world")
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(Fixture).fetchSemanticsNodes().isNotEmpty() }
    }

    private object MemoryStore : WorldSaveStore {
        private val files = mutableMapOf<String, ByteArray>()
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
        const val Fixture = "BW1-0410P-0Z4WG-SG600-0H020-00C80-GA020-800C0-03004-000G2-00300-18T15-R8FH3-GFC1J-AM30"
    }
}

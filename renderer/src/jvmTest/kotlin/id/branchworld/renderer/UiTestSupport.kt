package id.branchworld.renderer

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/** Taps a control by its visible label, or by its spoken label when a compact layout shows only its icon. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.tap(label: String) {
    val byText = onAllNodesWithText(label)
    val node = if (byText.fetchSemanticsNodes().isNotEmpty()) byText[0] else onAllNodesWithContentDescription(label)[0]
    runCatching { node.performScrollTo() }
    node.performClick()
}

/** From the title screen: Stories, then Begin on the first chapter, as a new player would. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.beginFadingGrove() {
    waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Stories").fetchSemanticsNodes().isNotEmpty() }
    tap("Stories")
    tap("Begin")
}

/** From the world: the pause menu's way back to the title screen. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.returnToTitle() {
    tap("Menu")
    tap("Return to title")
}

package id.branchworld.game.ui.app

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How much room the window gives the interface, and the arrangement that follows from it. */
internal data class ShellLayout(
    val maxWidth: Dp,
    val height: Dp,
    /** Desktop and tablets: boards beside the world rather than over it. */
    val wide: Boolean,
    /** The title introduction sits at the left rather than the bottom. */
    val sideIntroduction: Boolean,
    /** Phones in portrait: the dock tightens its padding. */
    val tightDock: Boolean,
    /** Phones in landscape: little height, so cards move to the side. */
    val shortLandscape: Boolean,
) {
    /** Panels open at the side of the world instead of rising from the bottom. */
    val sidePanel get() = wide || shortLandscape
    val compact get() = maxWidth < 600.dp

    companion object {
        fun of(scope: BoxWithConstraintsScope) = with(scope) {
            val wide = maxWidth >= 760.dp && maxHeight >= 420.dp
            ShellLayout(
                maxWidth = maxWidth,
                height = maxHeight,
                wide = wide,
                sideIntroduction = maxWidth >= 760.dp && maxWidth > maxHeight * 1.3f,
                tightDock = maxWidth < 460.dp,
                shortLandscape = !wide && maxWidth >= 640.dp && maxWidth > maxHeight * 1.45f,
            )
        }
    }
}

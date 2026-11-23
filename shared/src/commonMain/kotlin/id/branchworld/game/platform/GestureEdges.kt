package id.branchworld.game.platform

import androidx.compose.ui.Modifier

/** Keeps a horizontal drag (the timeline scrubber) from being taken by the system's edge-swipe Back gesture. */
internal expect fun Modifier.keepFromSystemGestures(): Modifier

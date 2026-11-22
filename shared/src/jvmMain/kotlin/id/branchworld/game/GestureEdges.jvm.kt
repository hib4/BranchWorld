package id.branchworld.game

import androidx.compose.ui.Modifier

/** No system edge gestures compete with drags on this platform. */
internal actual fun Modifier.keepFromSystemGestures(): Modifier = this

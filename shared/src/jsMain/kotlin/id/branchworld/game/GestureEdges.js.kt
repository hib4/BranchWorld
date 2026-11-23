package id.branchworld.game

import androidx.compose.ui.Modifier

/** Browsers own their edge gestures outside the page; nothing competes with drags inside the canvas. */
internal actual fun Modifier.keepFromSystemGestures(): Modifier = this

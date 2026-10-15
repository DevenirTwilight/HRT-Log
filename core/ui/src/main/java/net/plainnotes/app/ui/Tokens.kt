package net.plainnotes.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** UI-02 A3: spacing steps used across the app (4/8/12/16/24dp). Used in files changed since UI-02; others keep literals. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}

/** UI-02 A3: semantic corner radii (large cards, nested lists, small badges). */
object Corners {
    val card = RoundedCornerShape(24.dp)
    val list = RoundedCornerShape(16.dp)
    val badge = RoundedCornerShape(10.dp)
}

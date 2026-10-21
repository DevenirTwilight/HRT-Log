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

/**
 * UI-02 A3 / UI-06 D: semantic corner radii. [tag] (10dp) is a rounded rectangle for filter tags and small marks, not a pill;
 * [pill] is fully rounded, for pill buttons and status labels.
 */
object Corners {
    val card = RoundedCornerShape(24.dp)
    val list = RoundedCornerShape(16.dp)
    val tag = RoundedCornerShape(10.dp)
    val pill = RoundedCornerShape(percent = 50)
    // UI-07 B: the Expressive shape steps.
    val xs = RoundedCornerShape(Radii.xs)
    val s = RoundedCornerShape(Radii.s)
    val m = RoundedCornerShape(Radii.m)
    val l = RoundedCornerShape(Radii.l)
    val xl = RoundedCornerShape(Radii.xl)
    val xxl = RoundedCornerShape(Radii.xxl)
    val full = RoundedCornerShape(percent = 50)
}

/** UI-07 B: the same steps as radii, for shapes that animate between them. [full] is larger than any control, so it rounds fully. */
object Radii {
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 28.dp
    val xxl = 32.dp
    val full = 1000.dp
}

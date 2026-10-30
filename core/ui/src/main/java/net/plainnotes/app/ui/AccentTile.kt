package net.plainnotes.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Which of the four colours an icon tile uses; [NEUTRAL] is the quiet grey for entries that have no accent. */
enum class AccentKind { TEAL, BLUE, PURPLE, ROSE, NEUTRAL }

/** The tone for [kind] from the current theme; neutral uses the scheme's own container and variant text colours. */
@Composable fun accentTone(kind: AccentKind): AccentTone {
    val a = NotesTheme.accent; val c = MaterialTheme.colorScheme
    return when (kind) {
        AccentKind.TEAL -> a.teal; AccentKind.BLUE -> a.blue; AccentKind.PURPLE -> a.purple; AccentKind.ROSE -> a.rose
        AccentKind.NEUTRAL -> AccentTone(c.onSurfaceVariant, c.surfaceContainerHigh, c.onSurface, c.surfaceContainer)
    }
}

/**
 * UI-13: a small icon tile — a faint background with the icon in the accent colour. It is decorative (the label next to it carries the
 * meaning), so the icon has no content description. Never used for buttons, selected items or large areas.
 */
@Composable fun AccentIconTile(icon: ImageVector, kind: AccentKind, modifier: Modifier = Modifier, size: Dp = 38.dp, corner: Dp = 13.dp, iconSize: Dp = 22.dp) {
    val tone = accentTone(kind)
    Box(modifier.size(size).clip(RoundedCornerShape(corner)).background(tone.soft), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(iconSize), tint = tone.main)
    }
}

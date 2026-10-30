package net.plainnotes.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * UI-13 (REQUIREMENTS, 2026-10-30): the app's main colour stays teal; blue, purple and rose are small accents only (icon tiles,
 * small tags, date tiles, timeline dots). They are never used for buttons, big cards, selected items, curves or chart lines, and
 * they never carry meaning alone: every accent element sits next to an icon or text.
 *
 * [main] is the icon or text colour, [container] the tile fill, [onContainer] text on the tile, [soft] a faint tag background.
 */
@Immutable data class AccentTone(val main: Color, val container: Color, val onContainer: Color, val soft: Color)

@Immutable data class AccentColors(
    val blue: AccentTone,
    val purple: AccentTone,
    val rose: AccentTone,
    /** The main teal in the same four-slot shape, so a screen can pick any of the four for an icon tile without special cases. */
    val teal: AccentTone,
    val contrast: Contrast = Contrast.STANDARD,
)

private val LightBlue = AccentTone(Color(0xFF2C4A78), Color(0xFFD6E3FF), Color(0xFF0F2547), Color(0xFFEAF1FC))
private val LightPurple = AccentTone(Color(0xFF5A3E78), Color(0xFFE9DDF5), Color(0xFF2A1740), Color(0xFFF4EEF9))
private val LightRose = AccentTone(Color(0xFF8A3B5E), Color(0xFFF8DCE8), Color(0xFF3B1226), Color(0xFFFCEFF4))
private val DarkBlue = AccentTone(Color(0xFFAFC6F2), Color(0xFF25406B), Color(0xFFD6E3FF), Color(0xFF17233A))
private val DarkPurple = AccentTone(Color(0xFFD3BDEB), Color(0xFF483262), Color(0xFFE9DDF5), Color(0xFF261E33))
private val DarkRose = AccentTone(Color(0xFFF0B2CC), Color(0xFF6A2B49), Color(0xFFF8DCE8), Color(0xFF331C28))

/**
 * The tokens for one theme. Medium contrast moves the icon/text colour half way towards black (light) or white (dark); high contrast
 * draws icons and text in the scheme's onSurface, so nothing depends on the hue. With dynamic colour the fixed hues are not forced:
 * blue follows the dynamic secondary family, purple and rose the tertiary family (they need not be told apart, because everything
 * they mark also has an icon or text).
 */
fun accentColors(scheme: ColorScheme, dark: Boolean, contrast: Contrast = Contrast.STANDARD, dynamic: Boolean = false): AccentColors {
    val extreme = if (dark) Color.White else Color.Black
    fun AccentTone.raised() = when (contrast) {
        Contrast.STANDARD -> this
        Contrast.MEDIUM -> copy(main = lerp(main, extreme, 0.5f), onContainer = lerp(onContainer, extreme, 0.5f))
        Contrast.HIGH -> copy(main = scheme.onSurface, onContainer = scheme.onSurface)
    }
    fun family(container: Color, onContainer: Color, main: Color) =
        AccentTone(main, container, onContainer, lerp(scheme.surface, container, 0.5f))
    val blue = if (dynamic) family(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary) else if (dark) DarkBlue else LightBlue
    val purple = if (dynamic) family(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary) else if (dark) DarkPurple else LightPurple
    val rose = if (dynamic) family(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary) else if (dark) DarkRose else LightRose
    return AccentColors(blue.raised(), purple.raised(), rose.raised(),
        family(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary).raised(), contrast)
}

/** Outside [NotesTheme] (previews, tests wrapping a bare MaterialTheme) the light standard tokens apply. */
val LocalAccentColors = staticCompositionLocalOf { accentColors(notesColorScheme(false), false) }

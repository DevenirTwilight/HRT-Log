package net.plainnotes.app.ui

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** UI-13: the accent tokens match the owner's table, reach WCAG AA everywhere, and nothing else changed colour. */
class AccentColorsTest {
    private fun ratio(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
    private fun tone(main: Long, container: Long, on: Long, soft: Long) = AccentTone(Color(main), Color(container), Color(on), Color(soft))

    @Test fun valuesMatchTheOwnersTable() {
        val light = accentColors(notesColorScheme(false), false)
        assertEquals(tone(0xFF2C4A78, 0xFFD6E3FF, 0xFF0F2547, 0xFFEAF1FC), light.blue)
        assertEquals(tone(0xFF5A3E78, 0xFFE9DDF5, 0xFF2A1740, 0xFFF4EEF9), light.purple)
        assertEquals(tone(0xFF8A3B5E, 0xFFF8DCE8, 0xFF3B1226, 0xFFFCEFF4), light.rose)
        val dark = accentColors(notesColorScheme(true), true)
        assertEquals(tone(0xFFAFC6F2, 0xFF25406B, 0xFFD6E3FF, 0xFF17233A), dark.blue)
        assertEquals(tone(0xFFD3BDEB, 0xFF483262, 0xFFE9DDF5, 0xFF261E33), dark.purple)
        assertEquals(tone(0xFFF0B2CC, 0xFF6A2B49, 0xFFF8DCE8, 0xFF331C28), dark.rose)
    }

    @Test fun everyAccentReachesAAInLightDarkAndEveryContrastLevel() {
        val failures = mutableListOf<String>()
        for (dark in listOf(false, true)) for (contrast in Contrast.entries) {
            val scheme = notesColorScheme(dark, contrast); val a = accentColors(scheme, dark, contrast)
            for ((name, t) in listOf("blue" to a.blue, "purple" to a.purple, "rose" to a.rose, "teal" to a.teal)) {
                val where = "${if (dark) "dark" else "light"}/$contrast/$name"
                fun need(what: String, fg: Color, bg: Color, min: Float) { val r = ratio(fg, bg); if (r < min) failures += "$where $what ${"%.2f".format(r)} < $min" }
                need("text on its tile", t.onContainer, t.container, 4.5f)
                need("icon on the faint tag background", t.main, t.soft, 3f)
                need("accent text on the page background", t.main, scheme.background, 4.5f)
                need("accent text on a card", t.main, scheme.surfaceContainerLow, 4.5f)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun highContrastDrawsIconsAndTextInOnSurfaceSoNothingDependsOnHue() {
        for (dark in listOf(false, true)) {
            val scheme = notesColorScheme(dark, Contrast.HIGH); val a = accentColors(scheme, dark, Contrast.HIGH)
            for (t in listOf(a.blue, a.purple, a.rose)) { assertEquals(scheme.onSurface, t.main); assertEquals(scheme.onSurface, t.onContainer) }
        }
    }

    @Test fun dynamicColourFollowsTheDynamicSecondaryAndTertiaryFamilies() {
        val s = lightColorScheme(secondary = Color(0xFF123456), secondaryContainer = Color(0xFFABCDEF), onSecondaryContainer = Color(0xFF001122),
            tertiary = Color(0xFF654321), tertiaryContainer = Color(0xFFFEDCBA), onTertiaryContainer = Color(0xFF221100))
        val a = accentColors(s, false, Contrast.STANDARD, dynamic = true)
        assertEquals(s.secondary, a.blue.main); assertEquals(s.secondaryContainer, a.blue.container); assertEquals(s.onSecondaryContainer, a.blue.onContainer)
        for (t in listOf(a.purple, a.rose)) { assertEquals(s.tertiary, t.main); assertEquals(s.tertiaryContainer, t.container); assertEquals(s.onTertiaryContainer, t.onContainer) }
    }

    @Test fun statusTokensAreUnchanged() {
        val light = semanticColors(notesColorScheme(false), false); val dark = semanticColors(notesColorScheme(true), true)
        fun check(name: String, t: SemanticTone, container: Long?, on: Long, accent: Long) {
            if (container == null) assertEquals("$name container", Color.Transparent, t.container) else assertEquals("$name container", Color(container), t.container)
            assertEquals("$name on", Color(on), t.onContainer); assertEquals("$name accent", Color(accent), t.accent)
        }
        check("light attention", light.attention, 0xFFFFDBCE, 0xFF5C2410, 0xFF8E4D35)
        check("light success", light.success, 0xFFB8EDE6, 0xFF00201D, 0xFF006A63)
        check("light missed", light.missed, 0xFFEDE4DF, 0xFF4A3F3A, 0xFF7A6A63)
        check("light unconfirmed", light.unconfirmed, null, 0xFF3F4947, 0xFF6F7977)
        check("light skipped", light.skipped, 0xFFDAE5E2, 0xFF3F4947, 0xFF6F7977)
        check("dark attention", dark.attention, 0xFF71361F, 0xFFFFDBCE, 0xFFFFB599)
        check("dark success", dark.success, 0xFF00504A, 0xFF9EF2E8, 0xFF82D5CC)
        check("dark missed", dark.missed, 0xFF3B322E, 0xFFE9DED9, 0xFFA39089)
        check("dark unconfirmed", dark.unconfirmed, null, 0xFFBEC9C6, 0xFF889391)
        check("dark skipped", dark.skipped, 0xFF3F4947, 0xFFDDE4E2, 0xFF889391)
        // The main colour and error colours are the scheme's own and keep their pre-UI-13 values.
        val l = notesColorScheme(false); val d = notesColorScheme(true)
        assertEquals(Color(0xFF006A63), l.primary); assertEquals(Color(0xFF9EF2E8), l.primaryContainer); assertEquals(Color(0xFFCCE8E4), l.secondaryContainer)
        assertEquals(Color(0xFF8E4D35), l.tertiary); assertEquals(Color(0xFFFFDBCE), l.tertiaryContainer)
        assertEquals(Color(0xFFBA1A1A), l.error); assertEquals(Color(0xFFFFDAD6), l.errorContainer)
        assertEquals(Color(0xFF82D5CC), d.primary); assertEquals(Color(0xFF00504A), d.primaryContainer); assertEquals(Color(0xFF324B48), d.secondaryContainer)
        assertEquals(Color(0xFFFFB599), d.tertiary); assertEquals(Color(0xFF71361F), d.tertiaryContainer)
        assertEquals(Color(0xFFFFB4AB), d.error); assertEquals(Color(0xFF93000A), d.errorContainer)
    }

    @Test fun neutralBaseIsTheOwnersWarmWhiteAndHasNoGreenCast() {
        val l = notesColorScheme(false); val d = notesColorScheme(true)
        assertEquals(Color(0xFFFBF9F7), l.background); assertEquals(Color(0xFF141211), d.background)
        assertEquals(Color(0xFFF5F2EF), l.surfaceContainerLow); assertEquals(Color(0xFF1C1A18), d.surfaceContainerLow)
        assertEquals(Color(0xFFEFEBE8), l.surfaceContainer); assertEquals(Color(0xFF211F1D), d.surfaceContainer)
        assertEquals(Color(0xFFE9E4E0), l.surfaceContainerHigh); assertEquals(Color(0xFF2B2826), d.surfaceContainerHigh)
        assertEquals(Color(0xFF1D1B1A), l.onSurface); assertEquals(Color(0xFFE8E2DE), d.onSurface)
        assertEquals(Color(0xFF4C4642), l.onSurfaceVariant); assertEquals(Color(0xFFCFC6C1), d.onSurfaceVariant)
        assertEquals(Color(0xFFD8D1CC), l.outlineVariant); assertEquals(Color(0xFF4C4642), d.outlineVariant)
        assertEquals(Color(0xFF7B7470), l.outline); assertEquals(Color(0xFF968D88), d.outline)
        // Every neutral role is warm (red >= green >= blue) and only slightly tinted, so none can read as green or blue.
        val failures = mutableListOf<String>()
        for ((theme, s) in listOf("light" to l, "dark" to d)) for ((name, c) in listOf(
            "background" to s.background, "onBackground" to s.onBackground, "surface" to s.surface, "onSurface" to s.onSurface,
            "surfaceVariant" to s.surfaceVariant, "onSurfaceVariant" to s.onSurfaceVariant, "outline" to s.outline, "outlineVariant" to s.outlineVariant,
            "surfaceContainerLow" to s.surfaceContainerLow, "surfaceContainer" to s.surfaceContainer, "surfaceContainerHigh" to s.surfaceContainerHigh,
            "surfaceContainerHighest" to s.surfaceContainerHighest, "surfaceDim" to s.surfaceDim, "surfaceBright" to s.surfaceBright,
            "inverseSurface" to s.inverseSurface, "inverseOnSurface" to s.inverseOnSurface)) {
            val r = (c.red * 255).toInt(); val g = (c.green * 255).toInt(); val b = (c.blue * 255).toInt()
            if (!(r >= g && g >= b && r - b <= 0x0F)) failures += "$theme $name is not a warm neutral: $r,$g,$b"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        // Lighter-to-darker order of the container ladder is kept (light: darker as it goes up; dark: lighter).
        assertTrue(l.surfaceContainerLow.luminance() > l.surfaceContainer.luminance() && l.surfaceContainer.luminance() > l.surfaceContainerHigh.luminance() &&
            l.surfaceContainerHigh.luminance() > l.surfaceContainerHighest.luminance())
        assertTrue(d.surfaceContainerLow.luminance() < d.surfaceContainer.luminance() && d.surfaceContainer.luminance() < d.surfaceContainerHigh.luminance() &&
            d.surfaceContainerHigh.luminance() < d.surfaceContainerHighest.luminance())
    }
}

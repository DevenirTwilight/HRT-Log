package net.plainnotes.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Original teal/coral palette; tones follow the Material 3 tonal ladder. UI-13 (REQUIREMENTS, 2026-10-30): primary/secondary/tertiary/error
 * and their containers are unchanged; the neutral background, surface, outline and inverse families are a warm neutral (no green cast).
 */
private val Light = lightColorScheme(
    primary = Color(0xFF006A63), onPrimary = Color.White, primaryContainer = Color(0xFF9EF2E8), onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A6360), onSecondary = Color.White, secondaryContainer = Color(0xFFCCE8E4), onSecondaryContainer = Color(0xFF051F1D),
    tertiary = Color(0xFF8E4D35), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFDBCE), onTertiaryContainer = Color(0xFF370E00),
    error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF9F7), onBackground = Color(0xFF1D1B1A), surface = Color(0xFFFBF9F7), onSurface = Color(0xFF1D1B1A),
    surfaceVariant = Color(0xFFE2DCD8), onSurfaceVariant = Color(0xFF4C4642), outline = Color(0xFF7B7470), outlineVariant = Color(0xFFD8D1CC),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F2EF), surfaceContainer = Color(0xFFEFEBE8),
    surfaceContainerHigh = Color(0xFFE9E4E0), surfaceContainerHighest = Color(0xFFE3DDD9), surfaceDim = Color(0xFFDDD8D4), surfaceBright = Color(0xFFFBF9F7),
    inverseSurface = Color(0xFF32302E), inverseOnSurface = Color(0xFFF5F0ED), inversePrimary = Color(0xFF82D5CC),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF82D5CC), onPrimary = Color(0xFF003733), primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFF9EF2E8),
    secondary = Color(0xFFB0CCC8), onSecondary = Color(0xFF1C3532), secondaryContainer = Color(0xFF324B48), onSecondaryContainer = Color(0xFFCCE8E4),
    tertiary = Color(0xFFFFB599), onTertiary = Color(0xFF55200B), tertiaryContainer = Color(0xFF71361F), onTertiaryContainer = Color(0xFFFFDBCE),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141211), onBackground = Color(0xFFE8E2DE), surface = Color(0xFF141211), onSurface = Color(0xFFE8E2DE),
    surfaceVariant = Color(0xFF4C4642), onSurfaceVariant = Color(0xFFCFC6C1), outline = Color(0xFF968D88), outlineVariant = Color(0xFF4C4642),
    surfaceContainerLowest = Color(0xFF0F0D0C), surfaceContainerLow = Color(0xFF1C1A18), surfaceContainer = Color(0xFF211F1D),
    surfaceContainerHigh = Color(0xFF2B2826), surfaceContainerHighest = Color(0xFF353230), surfaceDim = Color(0xFF141211), surfaceBright = Color(0xFF3A3735),
    inverseSurface = Color(0xFFE8E2DE), inverseOnSurface = Color(0xFF32302E), inversePrimary = Color(0xFF006A63),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Contrast { STANDARD, MEDIUM, HIGH }

/** Raises contrast of secondary text and outlines (medium) and of containers too (high). */
private fun ColorScheme.withContrast(c: Contrast, dark: Boolean): ColorScheme = when (c) {
    Contrast.STANDARD -> this
    Contrast.MEDIUM -> copy(onSurfaceVariant = onSurface.copy(alpha = 0.92f), outline = onSurfaceVariant, outlineVariant = outline)
    Contrast.HIGH -> copy(onSurfaceVariant = onSurface, outline = onSurface, outlineVariant = onSurfaceVariant,
        surfaceContainerLow = if (dark) Color.Black else Color.White, surfaceContainerLowest = if (dark) Color.Black else Color.White,
        background = if (dark) Color.Black else Color.White, surface = if (dark) Color.Black else Color.White)
}

/** The app's own (non-dynamic) scheme at a contrast level; what [NotesTheme] uses without dynamic colour. */
fun notesColorScheme(dark: Boolean, contrast: Contrast = Contrast.STANDARD): ColorScheme = (if (dark) Dark else Light).withContrast(contrast, dark)

@Composable fun NotesTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, contrast: Contrast = Contrast.STANDARD, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val scheme: ColorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) Dark else Light
    val dynamic = dynamicColor && Build.VERSION.SDK_INT >= 31
    val colors = scheme.withContrast(contrast, dark)
    // UI-06: semantic status tokens next to the Material scheme, same contrast level and dynamic rule. UI-13: accent tokens likewise.
    // UI-07 D: the system "Remove animations" setting, read once per theme composition.
    val reduce = ReduceMotion.fromSystem(LocalContext.current)
    androidx.compose.runtime.CompositionLocalProvider(LocalSemanticColors provides semanticColors(colors, dark, contrast, dynamic), LocalAccentColors provides accentColors(colors, dark, contrast, dynamic), LocalReduceMotion provides reduce) {
        MaterialTheme(colorScheme = colors, shapes = Shapes(small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)), content = content)
    }
}

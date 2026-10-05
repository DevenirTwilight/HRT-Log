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

/** Original teal/coral palette; tones follow the Material 3 tonal ladder. */
private val Light = lightColorScheme(
    primary = Color(0xFF006A63), onPrimary = Color.White, primaryContainer = Color(0xFF9EF2E8), onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A6360), onSecondary = Color.White, secondaryContainer = Color(0xFFCCE8E4), onSecondaryContainer = Color(0xFF051F1D),
    tertiary = Color(0xFF8E4D35), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFDBCE), onTertiaryContainer = Color(0xFF370E00),
    error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4FBF9), onBackground = Color(0xFF161D1C), surface = Color(0xFFF4FBF9), onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E2), onSurfaceVariant = Color(0xFF3F4947), outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEEF5F3), surfaceContainer = Color(0xFFE9EFED),
    surfaceContainerHigh = Color(0xFFE3EAE8), surfaceContainerHighest = Color(0xFFDDE4E2), surfaceDim = Color(0xFFD5DBD9), surfaceBright = Color(0xFFF4FBF9),
    inverseSurface = Color(0xFF2B3231), inverseOnSurface = Color(0xFFECF2F0), inversePrimary = Color(0xFF82D5CC),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF82D5CC), onPrimary = Color(0xFF003733), primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFF9EF2E8),
    secondary = Color(0xFFB0CCC8), onSecondary = Color(0xFF1C3532), secondaryContainer = Color(0xFF324B48), onSecondaryContainer = Color(0xFFCCE8E4),
    tertiary = Color(0xFFFFB599), onTertiary = Color(0xFF55200B), tertiaryContainer = Color(0xFF71361F), onTertiaryContainer = Color(0xFFFFDBCE),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514), onBackground = Color(0xFFDDE4E2), surface = Color(0xFF0E1514), onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6), outline = Color(0xFF889391), outlineVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color(0xFF090F0F), surfaceContainerLow = Color(0xFF161D1C), surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF252B2A), surfaceContainerHighest = Color(0xFF2F3635), surfaceDim = Color(0xFF0E1514), surfaceBright = Color(0xFF343B3A),
    inverseSurface = Color(0xFFDDE4E2), inverseOnSurface = Color(0xFF2B3231), inversePrimary = Color(0xFF006A63),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable fun NotesTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val scheme: ColorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) Dark else Light
    MaterialTheme(colorScheme = scheme, shapes = Shapes(small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)), content = content)
}

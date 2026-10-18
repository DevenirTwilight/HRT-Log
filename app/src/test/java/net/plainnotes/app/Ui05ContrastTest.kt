package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.ui.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI-05 G: every pill and coloured icon background UI-05 added reaches WCAG AA (text 4.5:1, icons 3:1) in light and
 * dark and at standard, medium and high contrast. Event type colours are checked in Ui05TimelineTest.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class Ui05ContrastTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private data class Pair4(val name: String, val fg: Color, val bg: Color, val min: Float)
    private fun ratio(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test fun newPillsAndIconBackgroundsReachAA() {
        val pairs = mutableListOf<Pair4>()
        ui.setContent { Column {
            listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode -> Contrast.entries.forEach { contrast ->
                NotesTheme(mode, false, contrast) {
                    val c = MaterialTheme.colorScheme; val t = "$mode/$contrast"
                    val todayPill = c.onPrimary.copy(alpha = 0.16f).compositeOver(c.primary)
                    pairs += Pair4("$t today progress pill text", c.onPrimary, todayPill, 4.5f)
                    pairs += Pair4("$t today record status icon", c.onPrimary, todayPill, 3f)
                    pairs += Pair4("$t today countdown", c.onPrimary, c.primary, 4.5f)
                    pairs += Pair4("$t home strip text", c.onTertiaryContainer, c.tertiaryContainer, 4.5f)
                    pairs += Pair4("$t home strip add button", c.onTertiary, c.tertiary, 4.5f)
                    pairs += Pair4("$t drawer stock badge", c.onTertiaryContainer, c.tertiaryContainer, 4.5f)
                    pairs += Pair4("$t current period pill", c.onPrimary, c.primary, 4.5f)
                    pairs += Pair4("$t other period pill", c.onSecondaryContainer, c.secondaryContainer, 4.5f)
                    pairs += Pair4("$t stock days", c.onSurfaceVariant, c.surfaceContainerLow, 4.5f)
                    pairs += Pair4("$t medical date tile month", c.primary, c.surfaceContainerLowest, 4.5f)
                    pairs += Pair4("$t medical date tile day", c.onSurface, c.surfaceContainerLowest, 4.5f)
                    pairs += Pair4("$t medical row icon", c.primary, c.surfaceContainerLowest, 3f)
                    pairs += Pair4("$t medical visit icon", c.onSecondaryContainer, c.secondaryContainer, 3f)
                    pairs += Pair4("$t inset row secondary text", c.onSurfaceVariant, c.surfaceContainerLowest, 4.5f)
                }
            } }
        } }
        ui.waitForIdle()
        val failures = pairs.filter { ratio(it.fg, it.bg) < it.min }.map { "${it.name}: ${"%.2f".format(ratio(it.fg, it.bg))} < ${it.min}" }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}

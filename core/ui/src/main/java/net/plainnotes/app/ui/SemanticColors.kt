package net.plainnotes.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import net.plainnotes.app.domain.DoseStatus
import net.plainnotes.app.domain.SlotState

/**
 * UI-06 (REQUIREMENTS §115): semantic colour tokens. Every status colour in the app comes from here; red ([danger]) is only
 * for real errors and irreversible deletes. [container] is the fill, [onContainer] text on it, [accent] borders and symbols.
 */
@Immutable data class SemanticTone(val container: Color, val onContainer: Color, val accent: Color)

@Immutable data class SemanticColors(
    /** Something to do now: take now, save, the main button. */
    val action: SemanticTone,
    /** Needs a look but is not an error: past time, low stock, expiring soon, no opened package. */
    val attention: SemanticTone,
    /** Taken, on time, done. */
    val success: SemanticTone,
    /** Confirmed not taken: a neutral warm grey, outlined with [SemanticTone.accent]. */
    val missed: SemanticTone,
    /** A past time with no entry: transparent fill, dashed [SemanticTone.accent] outline. */
    val unconfirmed: SemanticTone,
    /** Skipped on purpose: neutral. */
    val skipped: SemanticTone,
    /** Only real errors and irreversible deletes. */
    val danger: SemanticTone,
    /** The contrast level these tokens were derived for (UI-07: high contrast draws icon outlines and glyphs in onSurface). */
    val contrast: Contrast = Contrast.STANDARD,
    /** The scheme's onSurface, for those high-contrast outlines and glyphs. */
    val onSurface: Color = Color.Unspecified,
)

private val LightFixed = mapOf(
    "attention" to SemanticTone(Color(0xFFFFDBCE), Color(0xFF5C2410), Color(0xFF8E4D35)),
    "success" to SemanticTone(Color(0xFFB8EDE6), Color(0xFF00201D), Color(0xFF006A63)),
    "missed" to SemanticTone(Color(0xFFEDE4DF), Color(0xFF4A3F3A), Color(0xFF7A6A63)),
    "unconfirmed" to SemanticTone(Color.Transparent, Color(0xFF3F4947), Color(0xFF6F7977)),
    "skipped" to SemanticTone(Color(0xFFDAE5E2), Color(0xFF3F4947), Color(0xFF6F7977)),
)
private val DarkFixed = mapOf(
    "attention" to SemanticTone(Color(0xFF71361F), Color(0xFFFFDBCE), Color(0xFFFFB599)),
    "success" to SemanticTone(Color(0xFF00504A), Color(0xFF9EF2E8), Color(0xFF82D5CC)),
    "missed" to SemanticTone(Color(0xFF3B322E), Color(0xFFE9DED9), Color(0xFFA39089)),
    "unconfirmed" to SemanticTone(Color.Transparent, Color(0xFFBEC9C6), Color(0xFF889391)),
    "skipped" to SemanticTone(Color(0xFF3F4947), Color(0xFFDDE4E2), Color(0xFF889391)),
)

/**
 * The tokens for one theme. Medium contrast moves text and symbols half way towards black (light) or white (dark); high
 * contrast uses that extreme for text and the scheme's onSurface for borders and symbols, as `withContrast` does for the scheme.
 * With dynamic colour, action and success follow the dynamic primary family and danger the dynamic error; the other tokens stay
 * fixed so their meaning never changes with the wallpaper.
 */
fun semanticColors(scheme: ColorScheme, dark: Boolean, contrast: Contrast = Contrast.STANDARD, dynamic: Boolean = false): SemanticColors {
    val fixed = if (dark) DarkFixed else LightFixed
    val extreme = if (dark) Color.White else Color.Black
    fun SemanticTone.raised() = when (contrast) {
        Contrast.STANDARD -> this
        Contrast.MEDIUM -> copy(onContainer = lerp(onContainer, extreme, 0.5f), accent = lerp(accent, onContainer, 0.4f))
        Contrast.HIGH -> copy(onContainer = extreme, accent = scheme.onSurface)
    }
    val success = if (dynamic) SemanticTone(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary) else fixed.getValue("success")
    return SemanticColors(
        action = SemanticTone(scheme.primary, scheme.onPrimary, scheme.primary),
        attention = fixed.getValue("attention").raised(),
        success = success.raised(),
        missed = fixed.getValue("missed").raised(),
        unconfirmed = fixed.getValue("unconfirmed").raised(),
        skipped = fixed.getValue("skipped").raised(),
        danger = SemanticTone(scheme.errorContainer, scheme.onErrorContainer, scheme.error),
        contrast = contrast, onSurface = scheme.onSurface,
    )
}

/** Outside [NotesTheme] (previews, tests wrapping a bare MaterialTheme) the light standard tokens apply. */
val LocalSemanticColors = staticCompositionLocalOf { semanticColors(notesColorScheme(false), false) }

/** `NotesTheme.semantic.missed` and so on, inside [NotesTheme]. */
object NotesTheme {
    val semantic: SemanticColors @Composable @ReadOnlyComposable get() = LocalSemanticColors.current
    /** UI-13: blue, purple and rose accents for small elements (icon tiles, tags, date tiles, timeline dots). */
    val accent: AccentColors @Composable @ReadOnlyComposable get() = LocalAccentColors.current
    /** UI-07 C: heroNumber, statNumber, sectionTitle, cardTitle. */
    val typeExtra: TypeExtra @Composable @ReadOnlyComposable get() = currentTypeExtra()
}

// --- One status mapping for the whole app ---

/** Every dose state the app draws: from a slot, a record, a recorded status or a calendar day. */
enum class DoseState { ON_TIME, LATE, PARTIAL, MISSED, UNCONFIRMED, SKIPPED, OVERDUE, PENDING, SOON }
enum class StatusTone { ACTION, ATTENTION, SUCCESS, MISSED, UNCONFIRMED, SKIPPED, DANGER, NONE }
/** The symbol shown with the colour, so a state never depends on colour alone. TEXT means the label itself carries it. */
enum class StatusSymbol { CHECK, HALF, CROSS, QUESTION, DASH, TEXT, NONE }
/** Text keys; the app turns them into its translated strings. */
enum class StatusText { TAKEN, PARTIAL, MISSED, UNCONFIRMED, SKIPPED, PAST_TIME, PENDING, SOON }

/**
 * @property outlined draw an [SemanticTone.accent] border (missed); @property dashed draw it dashed (unconfirmed);
 * @property lateNote add the words "late" without changing the colour.
 */
@Immutable data class StatusStyle(val state: DoseState, val tone: StatusTone, val symbol: StatusSymbol, val text: StatusText,
                                  val outlined: Boolean = false, val dashed: Boolean = false, val lateNote: Boolean = false)

object StatusStyles {
    /** The single table (UI-06 §B). */
    fun of(state: DoseState): StatusStyle = when (state) {
        DoseState.ON_TIME -> StatusStyle(state, StatusTone.SUCCESS, StatusSymbol.CHECK, StatusText.TAKEN)
        DoseState.LATE -> StatusStyle(state, StatusTone.SUCCESS, StatusSymbol.CHECK, StatusText.TAKEN, lateNote = true)
        DoseState.PARTIAL -> StatusStyle(state, StatusTone.MISSED, StatusSymbol.HALF, StatusText.PARTIAL)
        DoseState.MISSED -> StatusStyle(state, StatusTone.MISSED, StatusSymbol.CROSS, StatusText.MISSED, outlined = true)
        DoseState.UNCONFIRMED -> StatusStyle(state, StatusTone.UNCONFIRMED, StatusSymbol.QUESTION, StatusText.UNCONFIRMED, dashed = true)
        DoseState.SKIPPED -> StatusStyle(state, StatusTone.SKIPPED, StatusSymbol.DASH, StatusText.SKIPPED)
        DoseState.OVERDUE -> StatusStyle(state, StatusTone.ATTENTION, StatusSymbol.TEXT, StatusText.PAST_TIME)
        DoseState.PENDING -> StatusStyle(state, StatusTone.NONE, StatusSymbol.NONE, StatusText.PENDING)
        DoseState.SOON -> StatusStyle(state, StatusTone.NONE, StatusSymbol.NONE, StatusText.SOON)
    }
    fun of(state: SlotState): StatusStyle = of(DoseState.valueOf(state.name))
    fun of(status: DoseStatus): StatusStyle = of(DoseState.valueOf(status.name))
    /** A stored record: status ON_TIME/LATE/MISSED/SKIPPED; a MISSED record nobody confirmed (AUTO_MISSED) is UNCONFIRMED. */
    fun ofRecord(status: String, unconfirmed: Boolean): StatusStyle =
        of(if (status == "MISSED" && unconfirmed) DoseState.UNCONFIRMED else DoseState.valueOf(status))
}

/** The token for a tone; null when the state is not coloured (pending, soon). */
fun SemanticColors.of(tone: StatusTone): SemanticTone? = when (tone) {
    StatusTone.ACTION -> action; StatusTone.ATTENTION -> attention; StatusTone.SUCCESS -> success; StatusTone.MISSED -> missed
    StatusTone.UNCONFIRMED -> unconfirmed; StatusTone.SKIPPED -> skipped; StatusTone.DANGER -> danger; StatusTone.NONE -> null
}

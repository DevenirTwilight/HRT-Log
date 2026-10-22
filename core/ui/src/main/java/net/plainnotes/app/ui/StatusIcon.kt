package net.plainnotes.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/** UI-07 A (REQUIREMENTS §133): the status icon. The kinds a dose or a calendar day can show. */
enum class StatusIconKind { TAKEN, PARTIAL, MISSED, SKIPPED, UNCONFIRMED, TODAY }
/** UI-07b: what is drawn. HALF is a thin ring with its right half filled; RING_DOT is today's ring with a centre dot. */
enum class StatusGlyph { CHECK, HALF, CROSS, DASH, QUESTION, RING_DOT }

/** The four sizes the app uses. Line widths scale with the diameter. */
object StatusIconSizes { val XL = 40.dp; val L = 32.dp; val M = 22.dp; val S = 18.dp; val all = listOf(XL, L, M, S) }

/** UI-07b (REQUIREMENTS §136): line style. One [glyph] in one [color]; no filled disc and no outer ring (today aside). */
@Immutable data class StatusIconSpec(val glyph: StatusGlyph, val color: Color)

/** The status table's state as an icon kind; pending, soon and past time have none. */
fun statusIconKind(state: DoseState): StatusIconKind? = when (state) {
    DoseState.ON_TIME, DoseState.LATE -> StatusIconKind.TAKEN
    DoseState.PARTIAL -> StatusIconKind.PARTIAL
    DoseState.MISSED -> StatusIconKind.MISSED
    DoseState.SKIPPED -> StatusIconKind.SKIPPED
    DoseState.UNCONFIRMED -> StatusIconKind.UNCONFIRMED
    DoseState.OVERDUE, DoseState.PENDING, DoseState.SOON -> null
}

/** The token whose colour a kind's line takes; today has none (it uses the caller's ring colour). */
fun statusIconTone(kind: StatusIconKind, sem: SemanticColors): SemanticTone? = when (kind) {
    StatusIconKind.TAKEN, StatusIconKind.PARTIAL -> sem.success
    StatusIconKind.MISSED -> sem.missed
    StatusIconKind.SKIPPED -> sem.skipped
    StatusIconKind.UNCONFIRMED -> sem.unconfirmed
    StatusIconKind.TODAY -> null
}

/**
 * The spec for [kind] in [sem]. The line sits straight on its background, so its colour follows that background:
 * - [tint] when the caller gives one: on a primary background (today card, today's pill) that is onPrimary;
 * - otherwise high contrast draws in onSurface;
 * - [onOwnContainer]: on the state's own tinted day cell or chip, the token's onContainer (dark skipped accent on the
 *   skipped container is only 2.94:1);
 * - otherwise the token's accent.
 * [ring] is the colour of today's ring and dot.
 */
fun statusIconSpec(kind: StatusIconKind, sem: SemanticColors, ring: Color = Color.White, onOwnContainer: Boolean = false, tint: Color? = null): StatusIconSpec {
    val glyph = when (kind) {
        StatusIconKind.TAKEN -> StatusGlyph.CHECK; StatusIconKind.PARTIAL -> StatusGlyph.HALF; StatusIconKind.MISSED -> StatusGlyph.CROSS
        StatusIconKind.SKIPPED -> StatusGlyph.DASH; StatusIconKind.UNCONFIRMED -> StatusGlyph.QUESTION; StatusIconKind.TODAY -> StatusGlyph.RING_DOT
    }
    val tone = statusIconTone(kind, sem) ?: return StatusIconSpec(glyph, ring)
    val high = sem.contrast == Contrast.HIGH && sem.onSurface != Color.Unspecified
    return StatusIconSpec(glyph, when {
        tint != null -> tint
        high -> sem.onSurface
        onOwnContainer -> tone.onContainer
        else -> tone.accent
    })
}

/** Line width: 8 % of the diameter (3.2 dp at 40 dp), never under 1.5 dp. Thicker than the filled-circle icon had: no fill helps. */
fun statusGlyphWidth(size: Dp): Dp = maxOf(size * 0.08f, 1.5.dp)
/** Today's ring: 1.5 dp at 40 dp, scaled, never under 1 dp (unchanged from UI-07). */
fun statusOutlineWidth(size: Dp): Dp = maxOf(size * (1.5f / 40f), 1.dp)
/** The glyphs fill about this share of the diameter, round caps included; the partial ring's outer diameter too. */
const val STATUS_GLYPH_SPAN = 0.6f

/** Tests read the drawn kind and colour from here; neither is spoken (the row or day cell says the state in words). */
val StatusIconKindKey = SemanticsPropertyKey<StatusIconKind>("StatusIconKind")
var SemanticsPropertyReceiver.statusIconKind by StatusIconKindKey
val StatusIconColorKey = SemanticsPropertyKey<Color>("StatusIconColor")
var SemanticsPropertyReceiver.statusIconColor by StatusIconColorKey

/**
 * UI-07b: a status as a coloured line glyph with round caps. Decorative: no content description, so nothing is read twice;
 * the row or day cell it sits in says the state in words. See [statusIconSpec] for [onOwnContainer] and [tint].
 */
@Composable fun StatusIcon(kind: StatusIconKind, size: Dp = StatusIconSizes.M, modifier: Modifier = Modifier, ring: Color = Color.White,
                           onOwnContainer: Boolean = false, tint: Color? = null) {
    val spec = statusIconSpec(kind, NotesTheme.semantic, ring, onOwnContainer, tint)
    Canvas(modifier.size(size).semantics { statusIconKind = kind; statusIconColor = spec.color }) { drawStatusIcon(spec, size) }
}

/** The drawing itself, shared by [StatusIcon] and anything that paints the icon into its own canvas. */
fun DrawScope.drawStatusIcon(spec: StatusIconSpec, size: Dp) {
    val d = this.size.minDimension; val c = center; val r = d / 2f
    val w = statusGlyphWidth(size).toPx()
    val line = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val col = spec.color
    // Stroke centres span 0.52 d so that with the round caps (w / 2 each end, 0.04 d at 8 %) the glyph spans about 0.6 d.
    fun p(x: Float, y: Float) = Offset(c.x - r + x * d, c.y - r + y * d)
    when (spec.glyph) {
        StatusGlyph.CHECK -> drawPath(Path().apply { p(0.24f, 0.52f).let { moveTo(it.x, it.y) }; p(0.42f, 0.70f).let { lineTo(it.x, it.y) }; p(0.76f, 0.34f).let { lineTo(it.x, it.y) } }, col, style = line)
        StatusGlyph.CROSS -> { drawLine(col, p(0.27f, 0.27f), p(0.73f, 0.73f), w, StrokeCap.Round); drawLine(col, p(0.73f, 0.27f), p(0.27f, 0.73f), w, StrokeCap.Round) }
        StatusGlyph.DASH -> drawLine(col, p(0.24f, 0.5f), p(0.76f, 0.5f), w, StrokeCap.Round)
        StatusGlyph.QUESTION -> {
            drawPath(Path().apply {
                p(0.34f, 0.38f).let { moveTo(it.x, it.y) }
                p(0.34f, 0.20f).let { a -> p(0.66f, 0.20f).let { b -> p(0.66f, 0.38f).let { e -> cubicTo(a.x, a.y, b.x, b.y, e.x, e.y) } } }
                p(0.66f, 0.49f).let { a -> p(0.50f, 0.50f).let { b -> p(0.50f, 0.60f).let { e -> cubicTo(a.x, a.y, b.x, b.y, e.x, e.y) } } }
            }, col, style = line)
            drawCircle(col, w * 0.75f, p(0.5f, 0.74f))
        }
        StatusGlyph.HALF -> {
            // A thin ring (outer diameter 0.6 d) and its right half filled as a pie up to the ring's centre line.
            val ringW = w * 0.6f; val rr = d * STATUS_GLYPH_SPAN / 2f - ringW / 2f
            drawCircle(col, rr, c, style = Stroke(ringW))
            drawArc(col, -90f, 180f, useCenter = true, topLeft = Offset(c.x - rr, c.y - rr), size = Size(2 * rr, 2 * rr))
        }
        StatusGlyph.RING_DOT -> {
            val o = statusOutlineWidth(size).toPx()
            drawCircle(col, r - o / 2f, c, style = Stroke(o))
            drawCircle(col, d * 0.14f, c)
        }
    }
}

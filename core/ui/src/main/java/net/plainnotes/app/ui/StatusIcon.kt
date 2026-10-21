package net.plainnotes.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/** UI-07 A (REQUIREMENTS §116): the filled-circle status icon. The kinds a dose or a calendar day can show. */
enum class StatusIconKind { TAKEN, PARTIAL, MISSED, SKIPPED, UNCONFIRMED, TODAY }
enum class StatusGlyph { CHECK, CROSS, DASH, QUESTION, DOT, NONE }

/** The four sizes the app uses. Line widths scale with the diameter. */
object StatusIconSizes { val XL = 40.dp; val L = 32.dp; val M = 22.dp; val S = 18.dp; val all = listOf(XL, L, M, S) }

/**
 * What one icon draws, all from the UI-06 tokens: a [fill] circle, optionally its right [half] in another colour, an
 * outline [stroke] ([dashed] for unconfirmed, [thinStroke] for the partial and skipped circles so their edge stays visible
 * on the page), and a [glyph] in [glyphColor].
 */
@Immutable data class StatusIconSpec(val fill: Color, val half: Color?, val stroke: Color?, val dashed: Boolean, val thinStroke: Boolean,
                                     val glyph: StatusGlyph, val glyphColor: Color)

/** The status table's state as an icon kind; pending, soon and past time have none. */
fun statusIconKind(state: DoseState): StatusIconKind? = when (state) {
    DoseState.ON_TIME, DoseState.LATE -> StatusIconKind.TAKEN
    DoseState.PARTIAL -> StatusIconKind.PARTIAL
    DoseState.MISSED -> StatusIconKind.MISSED
    DoseState.SKIPPED -> StatusIconKind.SKIPPED
    DoseState.UNCONFIRMED -> StatusIconKind.UNCONFIRMED
    DoseState.OVERDUE, DoseState.PENDING, DoseState.SOON -> null
}

private val DarkGlyph = Color(0xFF0E1514)

/**
 * The spec for [kind] in [sem]. High contrast draws outlines and glyphs in onSurface. The taken check is white on the
 * success accent; where that accent is light (dark theme) it turns dark so the check keeps 3:1. [ring] is the colour of
 * today's ring and dot (white or onPrimary on a primary cell).
 */
fun statusIconSpec(kind: StatusIconKind, sem: SemanticColors, ring: Color = Color.White): StatusIconSpec {
    val high = sem.contrast == Contrast.HIGH && sem.onSurface != Color.Unspecified
    fun line(c: Color) = if (high) sem.onSurface else c
    return when (kind) {
        StatusIconKind.TAKEN -> sem.success.accent.let { a ->
            StatusIconSpec(a, null, null, false, false, StatusGlyph.CHECK, if (a.luminance() > 0.35f) DarkGlyph else Color.White) }
        StatusIconKind.PARTIAL -> StatusIconSpec(sem.missed.container, sem.success.accent, line(sem.missed.accent), false, true, StatusGlyph.NONE, Color.Transparent)
        StatusIconKind.MISSED -> StatusIconSpec(sem.missed.container, null, line(sem.missed.accent), false, false, StatusGlyph.CROSS, line(sem.missed.onContainer))
        StatusIconKind.SKIPPED -> StatusIconSpec(sem.skipped.container, null, line(sem.skipped.accent), false, true, StatusGlyph.DASH, line(sem.skipped.onContainer))
        StatusIconKind.UNCONFIRMED -> StatusIconSpec(Color.Transparent, null, line(sem.unconfirmed.accent), true, false, StatusGlyph.QUESTION, line(sem.unconfirmed.onContainer))
        StatusIconKind.TODAY -> StatusIconSpec(Color.Transparent, null, ring, false, false, StatusGlyph.DOT, ring)
    }
}

/** Glyph line: 6.5 % of the diameter (2.6 dp at 40 dp), never under 1.25 dp so 18 dp stays legible. */
fun statusGlyphWidth(size: Dp): Dp = maxOf(size * 0.065f, 1.25.dp)
/** Outline: 1.5 dp at 40 dp, scaled, never under 1 dp; the thin outline is two thirds of it, never under 0.75 dp. */
fun statusOutlineWidth(size: Dp, thin: Boolean = false): Dp = if (thin) maxOf(size * (1f / 40f), 0.75.dp) else maxOf(size * (1.5f / 40f), 1.dp)

/** Tests read the drawn kind from here; it is not spoken (the row or day cell says the state in words). */
val StatusIconKindKey = SemanticsPropertyKey<StatusIconKind>("StatusIconKind")
var SemanticsPropertyReceiver.statusIconKind by StatusIconKindKey

/**
 * UI-07 A: a status as a filled circle with a round-capped line glyph. Decorative: no content description, so nothing is
 * read twice; the row or day cell it sits in says the state in words.
 */
@Composable fun StatusIcon(kind: StatusIconKind, size: Dp = StatusIconSizes.M, modifier: Modifier = Modifier, ring: Color = Color.White) {
    val spec = statusIconSpec(kind, NotesTheme.semantic, ring)
    Canvas(modifier.size(size).semantics { statusIconKind = kind }) { drawStatusIcon(spec, size) }
}

/** The drawing itself, shared by [StatusIcon] and anything that paints the icon into its own canvas. */
fun DrawScope.drawStatusIcon(spec: StatusIconSpec, size: Dp) {
    val d = this.size.minDimension; val c = center
    val outline = if (spec.stroke != null) statusOutlineWidth(size, spec.thinStroke).toPx() else 0f
    val r = d / 2f
    if (spec.fill.alpha > 0f) drawCircle(spec.fill, r, c)
    spec.half?.let { drawArc(it, -90f, 180f, useCenter = true, topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r)) }
    spec.stroke?.let { s ->
        val dash = maxOf(d * 3f / 40f, 1.5.dp.toPx())
        drawCircle(s, r - outline / 2f, c, style = Stroke(outline, pathEffect = if (spec.dashed) PathEffect.dashPathEffect(floatArrayOf(dash, dash)) else null))
    }
    val w = statusGlyphWidth(size).toPx()
    val line = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(c.x - r + x * d, c.y - r + y * d)
    when (spec.glyph) {
        StatusGlyph.CHECK -> drawPath(Path().apply { p(0.29f, 0.52f).let { moveTo(it.x, it.y) }; p(0.44f, 0.66f).let { lineTo(it.x, it.y) }; p(0.71f, 0.37f).let { lineTo(it.x, it.y) } }, spec.glyphColor, style = line)
        StatusGlyph.CROSS -> { drawLine(spec.glyphColor, p(0.35f, 0.35f), p(0.65f, 0.65f), w, StrokeCap.Round); drawLine(spec.glyphColor, p(0.65f, 0.35f), p(0.35f, 0.65f), w, StrokeCap.Round) }
        StatusGlyph.DASH -> drawLine(spec.glyphColor, p(0.32f, 0.5f), p(0.68f, 0.5f), w, StrokeCap.Round)
        StatusGlyph.QUESTION -> {
            drawPath(Path().apply {
                p(0.38f, 0.38f).let { moveTo(it.x, it.y) }
                p(0.38f, 0.25f).let { a -> p(0.62f, 0.25f).let { b -> p(0.62f, 0.38f).let { e -> cubicTo(a.x, a.y, b.x, b.y, e.x, e.y) } } }
                p(0.62f, 0.47f).let { a -> p(0.50f, 0.49f).let { b -> p(0.50f, 0.58f).let { e -> cubicTo(a.x, a.y, b.x, b.y, e.x, e.y) } } }
            }, spec.glyphColor, style = line)
            drawCircle(spec.glyphColor, w * 0.75f, p(0.5f, 0.71f))
        }
        StatusGlyph.DOT -> drawCircle(spec.glyphColor, d * 0.14f, c)
        StatusGlyph.NONE -> {}
    }
}

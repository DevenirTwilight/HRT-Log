package net.plainnotes.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R

/**
 * UI-06 (REQUIREMENTS §115): the app side of the one status table in core/ui [StatusStyles]: words, symbol, colours and outline
 * for a [StatusStyle]. Screens draw states only through these, never with colorScheme.error.
 */
@Composable fun statusText(style: StatusStyle): String = stringResource(statusTextRes(style))

fun statusTextRes(style: StatusStyle): Int = when (style.text) {
    StatusText.TAKEN -> if (style.lateNote) R.string.status_late else R.string.status_on_time
    StatusText.PARTIAL -> R.string.legend_partial
    StatusText.MISSED -> R.string.status_missed
    StatusText.UNCONFIRMED -> R.string.status_unconfirmed
    StatusText.SKIPPED -> R.string.status_skipped
    StatusText.PAST_TIME -> R.string.status_past_time
    StatusText.PENDING -> R.string.status_pending
    StatusText.SOON -> R.string.status_soon
}

/** The symbol as an icon; HALF is drawn by the calendar's own mark, TEXT and NONE have none. */
fun statusIcon(symbol: StatusSymbol): ImageVector? = when (symbol) {
    StatusSymbol.CHECK -> Icons.Rounded.Check
    StatusSymbol.CROSS -> Icons.Outlined.Close
    StatusSymbol.QUESTION -> Icons.AutoMirrored.Outlined.HelpOutline
    StatusSymbol.DASH -> Icons.Outlined.Remove
    StatusSymbol.HALF, StatusSymbol.TEXT, StatusSymbol.NONE -> null
}

@Composable fun statusTone(style: StatusStyle): SemanticTone? = NotesTheme.semantic.of(style.tone)

/** Solid accent outline for missed, dashed for unconfirmed, nothing otherwise. */
fun Modifier.statusOutline(style: StatusStyle, tone: SemanticTone?, shape: Shape, width: Dp = 1.5.dp): Modifier = when {
    tone == null -> this
    style.dashed -> drawBehind {
        val w = width.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val radius = (outline as? androidx.compose.ui.graphics.Outline.Rounded)?.roundRect?.topLeftCornerRadius ?: CornerRadius.Zero
        drawRoundRect(tone.accent, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), cornerRadius = radius,
            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
    }
    style.outlined -> border(width, tone.accent, shape)
    else -> this
}

/** A status label: fully rounded, the state's colours, its symbol and its words. */
@Composable fun StatusChip(style: StatusStyle, text: String = statusText(style), modifier: Modifier = Modifier) {
    val tone = statusTone(style); val c = MaterialTheme.colorScheme
    val container = tone?.container ?: c.surfaceContainerHighest
    val content = tone?.onContainer ?: c.onSurfaceVariant
    Surface(color = container, contentColor = content, shape = Corners.pill,
        modifier = modifier.statusOutline(style, tone, Corners.pill).testTag("status:${style.state}")) {
        Row(Modifier.padding(start = if (statusIconKind(style.state) != null) 4.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            // UI-07 A: the filled-circle status icon (decorative; the chip's words say the state).
            statusIconKind(style.state)?.let { StatusIcon(it, StatusIconSizes.S); Spacer(Modifier.width(6.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

/** Colours a calendar day by its state; planned days keep the neutral surface. */
@Composable fun dayTone(kind: DayKind): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (kind) {
        DayKind.PLANNED -> c.surfaceContainerHighest to c.onSurface
        DayKind.NONE -> Color.Transparent to c.onSurface
        else -> statusTone(dayStyle(kind))!!.let { it.container to it.onContainer }
    }
}

/** A calendar day's state through the same table: taken, partly done, missed, unconfirmed; planned and empty days are not coloured. */
fun dayStyle(kind: DayKind): StatusStyle = StatusStyles.of(when (kind) {
    DayKind.TAKEN -> DoseState.ON_TIME; DayKind.PARTIAL -> DoseState.PARTIAL; DayKind.MISSED -> DoseState.MISSED
    DayKind.UNCONFIRMED -> DoseState.UNCONFIRMED; DayKind.PLANNED, DayKind.NONE -> DoseState.PENDING
})

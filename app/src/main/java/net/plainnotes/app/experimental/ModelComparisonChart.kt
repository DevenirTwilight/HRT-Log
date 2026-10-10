package net.plainnotes.app.experimental

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.plainnotes.app.ui.ChartViewport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ComparisonSeries(val values: DoubleArray, val color: Color, val dashed: Boolean = false)

/** Data for [ModelComparisonChart]; x is hours since the epoch. */
class ComparisonChartData(
    val x: DoubleArray,
    val series: List<ComparisonSeries>,
    val band: Pair<DoubleArray, DoubleArray>? = null,
    val bandColor: Color = Color.Gray,
    val longTail: BooleanArray? = null,
    /** (hour, mg) markers along the time axis. */
    val doses: List<Pair<Double, Double>> = emptyList(),
    /** Observed study points (hour, value, half-width) drawn as dots with error bars. */
    val points: List<Triple<Double, Double, Double>> = emptyList(),
    val nowHour: Double? = null,
    /** Non-null: label the axis as hours after this origin; null: calendar date/time. */
    val axisOrigin: Double? = null,
    val description: String = "",
)

private fun niceStep(span: Double, target: Int): Double {
    val raw = span / target
    val mag = 10.0.pow(floor(log10(raw)))
    val f = raw / mag
    return mag * when { f < 1.5 -> 1.0; f < 3 -> 2.0; f < 7 -> 5.0; else -> 10.0 }
}

/**
 * Multi-series comparison chart with the official chart's interaction model: pinch to zoom the time axis, drag to pan,
 * tap to select an instant (the caller computes exact kernel values for it). Geometry reuses [ChartViewport].
 */
@Composable fun ModelComparisonChart(data: ComparisonChartData, selectedHour: Double?, onSelect: (Double) -> Unit, valueLabel: (Double) -> String,
                                     modifier: Modifier = Modifier.fillMaxWidth().height(240.dp), tag: String = "xpk-chart") {
    val select by rememberUpdatedState(onSelect)
    val c = MaterialTheme.colorScheme
    val grid = c.outlineVariant; val textColor = c.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val minX = data.x.first(); val maxX = data.x.last()
    var start by remember(minX, maxX) { mutableDoubleStateOf(minX) }
    var end by remember(minX, maxX) { mutableDoubleStateOf(maxX) }
    val density = LocalDensity.current
    val leftPad = with(density) { 48.dp.toPx() }; val bottomPad = with(density) { 30.dp.toPx() }; val topPad = with(density) { 10.dp.toPx() }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val h24 = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
    val hourFmt = remember(locale, h24) { DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, if (h24) "Hm" else "hm"), locale) }
    val dayFmt = remember(locale) { DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "Md"), locale) }
    val minSpan = min(1.0, maxX - minX)
    Canvas(modifier.testTag(tag).semantics { contentDescription = data.description }
        .pointerInput(minX, maxX) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val w = size.width - leftPad; if (w <= 0) return@detectTransformGestures
                val span = end - start
                val anchor = start + span * ((centroid.x - leftPad) / w).coerceIn(0f, 1f)
                val newSpan = (span / zoom).coerceIn(minSpan, maxX - minX)
                var s = anchor - (anchor - start) * newSpan / span - pan.x / w * newSpan
                s = s.coerceIn(minX, maxX - newSpan); start = s; end = s + newSpan
            }
        }
        .pointerInput(minX, maxX) { detectTapGestures { o -> val w = size.width - leftPad; if (o.x >= leftPad && w > 0) select(start + (end - start) * ((o.x - leftPad) / w)) } }) {
        val w = size.width - leftPad; val h = size.height - bottomPad - topPad
        if (w <= 0 || h <= 0 || end <= start) return@Canvas
        val visible = data.series.flatMap { s -> ChartViewport.samples(data.x, s.values, start, end).map { it.second } } +
            (data.band?.let { ChartViewport.samples(data.x, it.second, start, end).map { p -> p.second } } ?: emptyList()) +
            data.points.filter { it.first in start..end }.map { it.second + it.third }
        val peak = visible.filter { it.isFinite() }.maxOrNull() ?: 0.0
        val step = niceStep(if (peak > 0) peak * 1.1 else 1.0, 4)
        val top = ceil((if (peak > 0) peak * 1.1 else 1.0) / step) * step
        fun px(x: Double) = leftPad + ((x - start) / (end - start) * w).toFloat()
        fun py(y: Double) = topPad + (h - (y / top * h)).toFloat()
        val labelStyle = TextStyle(color = textColor, fontSize = 10.sp)
        var v = 0.0
        while (v <= top + 1e-9) {
            val yy = py(v)
            drawLine(grid, Offset(leftPad, yy), Offset(size.width, yy), 1f, pathEffect = if (v == 0.0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val t = measurer.measure(valueLabel(v), labelStyle)
            drawText(t, topLeft = Offset(leftPad - t.size.width - 6f, yy - t.size.height / 2f))
            v += step
        }
        drawTimeAxis(data.axisOrigin, start, end, ::px, size.height - bottomPad, grid, labelStyle, measurer, hourFmt, dayFmt)
        val canvas = drawContext.canvas
        canvas.save(); canvas.clipRect(leftPad, topPad, size.width, topPad + h)
        data.longTail?.let { tail ->
            var i = 0
            while (i < data.x.size) {
                if (!tail[i]) { i++; continue }
                val from = i; while (i < data.x.size && tail[i]) i++
                val l = px(max(start, data.x[from])); val r = px(min(end, data.x[i - 1]))
                if (r > l) drawRect(c.onSurface.copy(alpha = 0.07f), Offset(l, topPad), Size(r - l, h))
            }
        }
        data.band?.let { (lo, hi) ->
            val upper = ChartViewport.samples(data.x, hi, start, end); val lower = ChartViewport.samples(data.x, lo, start, end)
            if (upper.isNotEmpty()) drawPath(Path().apply {
                moveTo(px(upper[0].first), py(upper[0].second)); upper.forEach { lineTo(px(it.first), py(it.second)) }
                lower.reversed().forEach { lineTo(px(it.first), py(it.second)) }; close()
            }, data.bandColor.copy(alpha = 0.22f))
        }
        data.series.forEach { s ->
            val pts = ChartViewport.samples(data.x, s.values, start, end)
            if (pts.size < 2) return@forEach
            val path = Path().apply { moveTo(px(pts[0].first), py(pts[0].second)); pts.drop(1).forEach { lineTo(px(it.first), py(it.second)) } }
            drawPath(path, s.color, style = Stroke(width = 2.5.dp.toPx(), pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null))
            // Peak in the visible range, marked and labelled.
            val best = pts.maxBy { it.second }
            drawCircle(s.color, 4.dp.toPx(), Offset(px(best.first), py(best.second)))
            val label = measurer.measure(valueLabel(best.second), labelStyle.copy(color = s.color))
            drawText(label, topLeft = Offset((px(best.first) + 6f).coerceAtMost(size.width - label.size.width.toFloat()), (py(best.second) - label.size.height - 2f).coerceAtLeast(topPad)))
        }
        data.points.forEach { (t, y, half) ->
            if (t in start..end) {
                drawLine(textColor, Offset(px(t), py(y - half)), Offset(px(t), py(y + half)), 2f)
                drawCircle(Color.White, 5.dp.toPx(), Offset(px(t), py(y))); drawCircle(c.tertiary, 3.5.dp.toPx(), Offset(px(t), py(y)))
            }
        }
        data.nowHour?.takeIf { it in start..end }?.let { drawLine(c.error.copy(alpha = 0.7f), Offset(px(it), topPad), Offset(px(it), topPad + h), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))) }
        selectedHour?.takeIf { it in start..end }?.let { drawLine(textColor, Offset(px(it), topPad), Offset(px(it), topPad + h), 1.5f) }
        canvas.restore()
        // Dose markers below the plot, so they never hide the curves.
        data.doses.filter { it.first in start..end }.forEach { (t, _) ->
            val x = px(t); val y0 = topPad + h + 2f
            drawPath(Path().apply { moveTo(x, y0); lineTo(x - 5f, y0 + 8f); lineTo(x + 5f, y0 + 8f); close() }, c.primary)
        }
    }
}

private fun DrawScope.drawTimeAxis(origin: Double?, start: Double, end: Double, px: (Double) -> Float, y: Float, grid: Color, style: TextStyle,
                                   measurer: androidx.compose.ui.text.TextMeasurer, hourFmt: DateTimeFormatter, dayFmt: DateTimeFormatter) {
    val span = end - start
    if (origin != null) {
        val stepH = when { span <= 2 -> 0.25; span <= 4.5 -> 0.5; span <= 9 -> 1.0; span <= 25 -> 4.0; else -> 8.0 }
        var t = ceil((start - origin) / stepH) * stepH
        while (origin + t <= end + 1e-9) {
            val x = px(origin + t)
            drawLine(grid.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, y), 1f)
            val label = if (stepH < 1) "+%.2gh".format(t) else "+%dh".format(Math.round(t))
            val m = measurer.measure(label, style); drawText(m, topLeft = Offset(x - m.size.width / 2f, y + 12f))
            t += stepH
        }
        return
    }
    val stepH = when { span <= 36 -> 6.0; span <= 96 -> 12.0; else -> 24.0 }
    val zone = ZoneId.systemDefault()
    val fmt = if (stepH < 24) hourFmt else dayFmt
    val offsetH = zone.rules.getOffset(Instant.ofEpochMilli((start * 3_600_000).toLong())).totalSeconds / 3600.0
    var t = ceil((start + offsetH) / stepH) * stepH - offsetH
    while (t <= end) {
        val x = px(t)
        drawLine(grid.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, y), 1f)
        val m = measurer.measure(Instant.ofEpochMilli((t * 3_600_000).toLong()).atZone(zone).format(fmt), style)
        drawText(m, topLeft = Offset(x - m.size.width / 2f, y + 12f))
        t += stepH
    }
}

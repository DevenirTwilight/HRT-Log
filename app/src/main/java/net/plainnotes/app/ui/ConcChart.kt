package net.plainnotes.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Data for the time-series chart; x is hours since epoch. Values before [splitX] draw solid, after dashed. */
class ChartData(
    val x: DoubleArray, val y: DoubleArray,
    val band68: Pair<DoubleArray, DoubleArray>? = null, val band95: Pair<DoubleArray, DoubleArray>? = null,
    val splitX: Double? = null,
    val points: List<Pair<Double, Double>> = emptyList(),
    /** Optional horizontal reference range (user-entered), drawn as a light band. */
    val range: Pair<Double, Double>? = null,
    val unit: String = "",
    val includeBandsInScale:Boolean = false,
    val breaks:DoubleArray = doubleArrayOf(),
    /** Optional query-local model evaluation. Invoked on Default, outside Canvas, with cancellation. */
    val readAt:((Double,()->Unit)->Double?)? = null,
    /** UI-12: hours of actual doses, drawn as short vertical ticks along the bottom edge (callers pass only past doses). */
    val marks: DoubleArray = doubleArrayOf(),
    /** UI-12: day-based axis (date in bold at local midnight, noon label at 12:00) when the visible span is 1.5 to 10 days. */
    val axisDays: Boolean = false,
    val noonLabel: String = "12:00",
    /** UI-12: width of the recorded line in dp; the forecast stays 2 dp (or thinner when this is thinner). Null keeps 2.5 dp. */
    val lineWidthDp: Float? = null,
    /** UI-12: how the unit is spoken by a screen reader (e.g. "picograms per millilitre"); null reads the unit text. */
    val unitSpoken: String? = null,
)

private fun niceStep(span: Double, target: Int): Double {
    val raw = span / target
    val mag = 10.0.pow(floor(log10(raw)))
    val f = raw / mag
    return mag * when { f < 1.5 -> 1.0; f < 3 -> 2.0; f < 7 -> 5.0; else -> 10.0 }
}

/**
 * Zoomable chart (pinch to zoom the time axis, drag to pan, tap to read a value).
 * Kept dependency-free so the series styling (solid record / dashed forecast, CI bands, lab dots) stays exact.
 */
/** UI-13: the dose ticks on the chart (and its legend) are a light teal; at high contrast they are the solid main teal. */
internal fun doseMarkColor(c: androidx.compose.material3.ColorScheme, accent: AccentColors): androidx.compose.ui.graphics.Color =
    if (accent.contrast == Contrast.HIGH) c.primary else c.primary.copy(alpha = 0.55f)

@Composable fun ConcChart(data: ChartData, initialStart: Double, initialEnd: Double, modifier: Modifier = Modifier, valueLabel: (Double) -> String) {
    val c = MaterialTheme.colorScheme
    val line = c.primary; val band = c.primary; val grid = c.outlineVariant; val textColor = c.onSurfaceVariant
    // UI-13 (colours only): lab results are the purple accent, the dose ticks a light teal; every position, value and rule is unchanged.
    val labColor = NotesTheme.accent.purple.main; val nowColor = c.outline; val rangeColor = c.secondary; val markColor = doseMarkColor(c, NotesTheme.accent)
    val measurer = rememberTextMeasurer()
    val minX = data.x.firstOrNull() ?: initialStart; val maxX = data.x.lastOrNull() ?: initialEnd
    var start by remember(initialStart,initialEnd,minX,maxX) { mutableDoubleStateOf(max(minX, initialStart)) }
    var end by remember(initialStart,initialEnd,minX,maxX) { mutableDoubleStateOf(min(maxX, initialEnd)) }
    var tapX by remember(data) { mutableStateOf<Double?>(null) }
    var tapValue by remember(data) { mutableStateOf<Pair<Double,Double>?>(null) }
    LaunchedEffect(data,tapX) {
        tapValue=null
        tapX?.let { tx ->
            val value=if(data.readAt!=null)withContext(Dispatchers.Default){data.readAt.invoke(tx){ensureActive()}} else null
            value?.let{tapValue=tx to it}
        }
    }
    fun valueAtTap(tx:Double):Double? = if(data.readAt==null)net.plainnotes.app.pk.Pk.interpolate(data.x,data.y,tx) else tapValue?.takeIf{it.first==tx}?.second
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var cardSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val h24 = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
    val hourFmt = remember(locale, h24) { DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, if (h24) "Hm" else "hm"), locale) }
    val dayFmt = remember(locale) { DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "Md"), locale) }
    val leftPad = with(density) { 44.dp.toPx() }; val bottomPad = with(density) { 22.dp.toPx() }; val topPad = with(density) { 8.dp.toPx() }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { canvasSize = it }
            .pointerInput(data) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val w = size.width - leftPad; if (w <= 0) return@detectTransformGestures
                    val span = end - start
                    val anchor = start + span * ((centroid.x - leftPad) / w).coerceIn(0f, 1f)
                    var newSpan = (span / zoom).coerceIn(6.0, max(6.0, maxX - minX))
                    var s = anchor - (anchor - start) * newSpan / span - pan.x / w * newSpan
                    s = s.coerceIn(minX, max(minX, maxX - newSpan)); start = s; end = s + newSpan; tapX = null
                }
            }
            .pointerInput(data) { detectTapGestures { o -> val w = size.width - leftPad
                // UI-12: tapping next to the current point again closes the readout; elsewhere moves it.
                val previous = tapX?.let { leftPad + ((it - start) / (end - start) * w).toFloat() }
                tapX = if (o.x < leftPad || (previous != null && kotlin.math.abs(o.x - previous) < 12.dp.toPx())) null else start + (end - start) * ((o.x - leftPad) / w) } }) {
            val w = size.width - leftPad; val h = size.height - bottomPad - topPad
            if (w <= 0 || h <= 0 || data.x.isEmpty()) return@Canvas
            if(end<=start)return@Canvas
            val yMax = ChartViewport.top(data,start,end)
            val step = niceStep(yMax, 4)
            val top = ceil(yMax / step) * step
            fun px(x: Double) = leftPad + ((x - start) / (end - start) * w).toFloat()
            fun py(y: Double) = topPad + (h - (y / top * h)).toFloat()
            val labelStyle = TextStyle(color = textColor, fontSize = 10.sp)
            // horizontal grid + y labels
            var v = 0.0
            while (v <= top + 1e-9) {
                val yy = py(v)
                drawLine(grid, Offset(leftPad, yy), Offset(size.width, yy), strokeWidth = 1f, pathEffect = if (v == 0.0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val t = measurer.measure(valueLabel(v), labelStyle)
                drawText(t, topLeft = Offset(leftPad - t.size.width - 6f, yy - t.size.height / 2f))
                v += step
            }
            data.range?.let { (lo, hi) -> drawRect(rangeColor.copy(alpha = 0.10f), Offset(leftPad, py(hi)), androidx.compose.ui.geometry.Size(w, py(lo) - py(hi))) }
            drawTimeAxis(measurer, labelStyle, start, end, ::px, size.height - bottomPad, grid, hourFmt, dayFmt, data.axisDays, data.noonLabel)
            clipRectSafe(leftPad, topPad, size.width, topPad+h) {
                fun bandPath(b: Pair<DoubleArray, DoubleArray>, indices:IntRange): Path = Path().apply {
                    var first = true
                    for (i in indices) { if (data.x[i] < start - 48 || data.x[i] > end + 48) continue; val p = Offset(px(data.x[i]), py(b.second[i])); if (first) { moveTo(p.x, p.y); first = false } else lineTo(p.x, p.y) }
                    for (i in indices.reversed()) { if (data.x[i] < start - 48 || data.x[i] > end + 48) continue; lineTo(px(data.x[i]), py(b.first[i])) }
                    close()
                }
                ChartViewport.segments(data.x,data.breaks).forEach { range -> data.band95?.let { drawPath(bandPath(it,range), band.copy(alpha = 0.10f)) } }
                ChartViewport.segments(data.x,data.breaks).forEach { range -> data.band68?.let { drawPath(bandPath(it,range), band.copy(alpha = 0.16f)) } }
                val solid = Path(); val dashed = Path(); var sStarted = false; var dStarted = false
                for (segment in ChartViewport.segmentSamples(data.x,data.y,start,end,data.splitX,data.breaks)) {
                  sStarted=false;dStarted=false
                  for ((x,y) in segment) {
                    val p = Offset(px(x), py(y))
                    if(data.splitX==null || x<=data.splitX){if(!sStarted){solid.moveTo(p.x,p.y);sStarted=true}else solid.lineTo(p.x,p.y)}
                    if(data.splitX!=null && x>=data.splitX){if(!dStarted){dashed.moveTo(p.x,p.y);dStarted=true}else dashed.lineTo(p.x,p.y)}
                }
                }
                val lineW = data.lineWidthDp ?: 2.5f
                drawPath(solid, line, style = Stroke(width = lineW.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(dashed, line.copy(alpha = 0.75f), style = Stroke(width = minOf(2f, lineW).dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                data.splitX?.takeIf { it in start..end }?.let { drawLine(nowColor.copy(alpha = 0.7f), Offset(px(it), topPad), Offset(px(it), topPad + h), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))) }
                data.marks.forEach { x -> if (x in start..end) drawLine(markColor, Offset(px(x), topPad + h), Offset(px(x), topPad + h - 10.dp.toPx()), 1.5.dp.toPx(), cap = StrokeCap.Butt) }
                data.points.forEach { (x, y) -> if (x in start..end) { drawCircle(Color.White, 6.dp.toPx(), Offset(px(x), py(y))); drawCircle(labColor, 4.5.dp.toPx(), Offset(px(x), py(y))) } }
                tapX?.let { tx ->
                    valueAtTap(tx)?.let { ty ->
                        drawLine(textColor.copy(alpha = 0.5f), Offset(px(tx), topPad), Offset(px(tx), topPad + h), 1f)
                        drawCircle(line, 5.dp.toPx(), Offset(px(tx), py(ty)))
                    }
                }
            }
        }
        tapX?.let { tx -> valueAtTap(tx)?.let { ty ->
            // UI-12: a floating readout beside the point; on the right half it opens to the left, otherwise to the right.
            val yMaxOut = ChartViewport.top(data, start, end); val stepOut = niceStep(yMaxOut, 4); val top = ceil(yMaxOut / stepOut) * stepOut
            val plotW = canvasSize.width - leftPad; val plotH = canvasSize.height - bottomPad - topPad
            val pointX = leftPad + ((tx - start) / (end - start) * plotW).toFloat(); val pointY = topPad + (plotH - (ty / top * plotH)).toFloat()
            val gap = with(density) { 10.dp.toPx() }
            val x = if (pointX > canvasSize.width / 2f) pointX - cardSize.width - gap else pointX + gap
            val y = (pointY - cardSize.height - gap / 2).coerceIn(with(density) { 2.dp.toPx() }, maxOf(0f, (canvasSize.height - cardSize.height).toFloat()))
            val time = Instant.ofEpochMilli((tx * 3_600_000).toLong())
            val whenText = formatDateTime(time)
            val number = valueLabel(ty)
            val spoken = androidx.compose.ui.res.stringResource(net.plainnotes.app.R.string.ui12_readout_spoken, whenText, number, data.unitSpoken ?: data.unit)
            Surface(color = c.inverseSurface, contentColor = c.inverseOnSurface, shape = MaterialTheme.shapes.small,
                modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }.onSizeChanged { cardSize = it }
                    .testTag("chart-readout").semantics(mergeDescendants = true) { contentDescription = spoken; liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(whenText, style = MaterialTheme.typography.labelMedium)
                    Text("$number ${data.unit}", style = MaterialTheme.typography.titleSmall)
                }
            }
        } }
    }
}

private inline fun DrawScope.clipRectSafe(l: Float, t: Float, r: Float, b: Float, block: DrawScope.() -> Unit) =
    drawContext.canvas.let { canvas -> canvas.save(); canvas.clipRect(l, t, r, b); block(); canvas.restore() }

/**
 * Which of the axis labels can be drawn without touching one another. A smaller [rank] wins a contested spot (the bold date beats the
 * half-day mark); among equal ranks the earlier label wins. At normal text size nothing overlaps and every label is kept; at 2x text on a
 * narrow screen the half-day marks give way first instead of printing over the dates.
 */
internal fun axisLabelsToDraw(centers: FloatArray, widths: FloatArray, rank: IntArray, gap: Float): BooleanArray {
    require(centers.size == widths.size && centers.size == rank.size)
    val keep = BooleanArray(centers.size)
    val taken = ArrayList<Pair<Float, Float>>()
    for (i in centers.indices.sortedWith(compareBy({ rank[it] }, { it }))) {
        val left = centers[i] - widths[i] / 2f - gap / 2f
        val right = centers[i] + widths[i] / 2f + gap / 2f
        if (left.isNaN() || right.isNaN()) continue
        if (taken.none { left < it.second && right > it.first }) { keep[i] = true; taken += left to right }
    }
    return keep
}

/** Keeps a label of [width] fully inside a canvas of [total] width; a label wider than the canvas is centred. */
internal fun clampAxisLabelCenter(center: Float, width: Float, total: Float): Float =
    if (!center.isFinite() || !width.isFinite() || !total.isFinite()) center else if (width >= total) total / 2f else center.coerceIn(width / 2f, total - width / 2f)

private fun DrawScope.drawTimeAxis(measurer: TextMeasurer, style: TextStyle, start: Double, end: Double, px: (Double) -> Float, y: Float, grid: Color,
                                   hourFmt: DateTimeFormatter, dayFmt: DateTimeFormatter, days: Boolean = false, noonLabel: String = "12:00") {
    val span = end - start
    val stepH = when { span <= 36 -> 6.0; span <= 96 -> 12.0; span <= 24 * 10 -> 24.0; span <= 24 * 35 -> 24.0 * 7; span <= 24 * 120 -> 24.0 * 14; else -> 24.0 * 30 }
    val dayLabels = days && stepH in 12.0..24.0
    val zone = ZoneId.systemDefault()
    val fmt = if (stepH < 24) hourFmt else dayFmt
    val offsetH = zone.rules.getOffset(Instant.ofEpochMilli((start * 3_600_000).toLong())).totalSeconds / 3600.0
    var t = ceil((start + offsetH) / stepH) * stepH - offsetH
    val ticks = ArrayList<Triple<Float, androidx.compose.ui.text.TextLayoutResult, Boolean>>()
    while (t <= end) {
        val x = px(t)
        drawLine(grid.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, y), 1f)
        val at = Instant.ofEpochMilli((t * 3_600_000).toLong()).atZone(zone)
        // UI-12: with day labels the date is bold at local midnight and the half-day tick says "12時"/"12 h" instead of a clock time.
        val label = if (dayLabels) (if (at.hour == 12) noonLabel else at.format(dayFmt)) else at.format(fmt)
        val half = dayLabels && at.hour == 12
        val m = measurer.measure(label, if (dayLabels && !half) style.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else style)
        ticks += Triple(x, m, half)
        t += stepH
    }
    // A label near the edge of the canvas is moved inside it (never cut off); the plan then works on the positions actually drawn.
    val centers = FloatArray(ticks.size) { clampAxisLabelCenter(ticks[it].first, ticks[it].second.size.width.toFloat(), size.width) }
    val keep = axisLabelsToDraw(centers, FloatArray(ticks.size) { ticks[it].second.size.width.toFloat() },
        IntArray(ticks.size) { if (ticks[it].third) 1 else 0 }, 6.dp.toPx())
    ticks.forEachIndexed { i, (_, m, _) -> if (keep[i]) drawText(m, topLeft = Offset(centers[i] - m.size.width / 2f, y + 4f)) }
}

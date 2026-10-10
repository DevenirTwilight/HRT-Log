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
@Composable fun ConcChart(data: ChartData, initialStart: Double, initialEnd: Double, modifier: Modifier = Modifier, valueLabel: (Double) -> String) {
    val c = MaterialTheme.colorScheme
    val line = c.primary; val band = c.primary; val grid = c.outlineVariant; val textColor = c.onSurfaceVariant
    val labColor = c.tertiary; val nowColor = c.error; val rangeColor = c.secondary
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
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val h24 = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
    val hourFmt = remember(locale, h24) { DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, if (h24) "Hm" else "hm"), locale) }
    val dayFmt = remember(locale) { DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "Md"), locale) }
    val leftPad = with(density) { 44.dp.toPx() }; val bottomPad = with(density) { 22.dp.toPx() }; val topPad = with(density) { 8.dp.toPx() }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()
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
            .pointerInput(data) { detectTapGestures { o -> val w = size.width - leftPad; tapX = if (o.x < leftPad) null else start + (end - start) * ((o.x - leftPad) / w) } }) {
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
            drawTimeAxis(measurer, labelStyle, start, end, ::px, size.height - bottomPad, grid, hourFmt, dayFmt)
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
                drawPath(solid, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(dashed, line.copy(alpha = 0.75f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                data.splitX?.takeIf { it in start..end }?.let { drawLine(nowColor.copy(alpha = 0.7f), Offset(px(it), topPad), Offset(px(it), topPad + h), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))) }
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
            Surface(color = c.inverseSurface, contentColor = c.inverseOnSurface, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(start = 52.dp, top = 4.dp)) {
                val time = Instant.ofEpochMilli((tx * 3_600_000).toLong())
                Text("${formatDateTime(time)}  ·  ${valueLabel(ty)} ${data.unit}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
        } }
    }
}

private inline fun DrawScope.clipRectSafe(l: Float, t: Float, r: Float, b: Float, block: DrawScope.() -> Unit) =
    drawContext.canvas.let { canvas -> canvas.save(); canvas.clipRect(l, t, r, b); block(); canvas.restore() }

private fun DrawScope.drawTimeAxis(measurer: TextMeasurer, style: TextStyle, start: Double, end: Double, px: (Double) -> Float, y: Float, grid: Color,
                                   hourFmt: DateTimeFormatter, dayFmt: DateTimeFormatter) {
    val span = end - start
    val stepH = when { span <= 36 -> 6.0; span <= 96 -> 12.0; span <= 24 * 10 -> 24.0; span <= 24 * 35 -> 24.0 * 7; span <= 24 * 120 -> 24.0 * 14; else -> 24.0 * 30 }
    val zone = ZoneId.systemDefault()
    val fmt = if (stepH < 24) hourFmt else dayFmt
    val offsetH = zone.rules.getOffset(Instant.ofEpochMilli((start * 3_600_000).toLong())).totalSeconds / 3600.0
    var t = ceil((start + offsetH) / stepH) * stepH - offsetH
    while (t <= end) {
        val x = px(t)
        drawLine(grid.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, y), 1f)
        val label = Instant.ofEpochMilli((t * 3_600_000).toLong()).atZone(zone).format(fmt)
        val m = measurer.measure(label, style)
        drawText(m, topLeft = Offset(x - m.size.width / 2f, y + 4f))
        t += stepH
    }
}

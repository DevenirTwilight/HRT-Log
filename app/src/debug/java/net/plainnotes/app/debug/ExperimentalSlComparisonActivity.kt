package net.plainnotes.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.DoseExtras
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route
import net.plainnotes.app.pk.experimental.ResearchShapeComparison
import net.plainnotes.app.pk.experimental.ResearchShapeComparisonV01

/**
 * DEBUG APK ONLY: opt-in, entirely in-memory scenario sandbox, NO access to personal records.
 * Launch: adb shell am start -n net.plainnotes.app/.debug.ExperimentalSlComparisonActivity
 * Not registered in the release manifest and not connected to the ordinary concentration UI.
 */
class ExperimentalSlComparisonActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ResearchSandbox() } }
    }
}

@Composable
private fun ResearchSandbox() {
    var schedule by remember { mutableStateOf("0:0.5, 6:0.5, 12:0.5, 18:0.5") }
    var asOfText by remember { mutableStateOf("48") }
    var baselineIndex by remember { mutableIntStateOf(2) }
    var comparison by remember { mutableStateOf<ResearchShapeComparison?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val baselines = ResearchShapeComparisonV01.assumedPriceBaselines
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("HRT Log · 实验药代对比 / PK Research Sandbox", style = MaterialTheme.typography.titleLarge)
        Text(
            "调试版专用；默认演示数据为虚构给药事件。仅显示两条各自归一化的相对曲线，" +
                "不是 pg/mL、个人血药浓度或临床置信区间。不会读取、保存或发送你的 HRT 记录。",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium
        )
        Text("手动输入（相对小时:mg，用逗号分隔；负时间表示更早的假设给药）")
        OutlinedTextField(
            value = schedule, onValueChange = { schedule = it; comparison = null },
            modifier = Modifier.fillMaxWidth(), singleLine = false, minLines = 2, maxLines = 4,
            label = { Text("Hypothetical events · hour:mg") }
        )
        OutlinedTextField(
            value = asOfText, onValueChange = { asOfText = it; comparison = null },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("观察时刻（相对小时） · As-of hour") }
        )
        Text("Price 图线的假设背景值（不是你的 E2 背景值）", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = baselineIndex.toFloat(),
            onValueChange = { baselineIndex = it.toInt().coerceIn(0, 4); comparison = null },
            valueRange = 0f..4f, steps = 3,
        )
        Text("b=${baselines[baselineIndex].toInt()} pg/mL（仅研究情景）")
        Button(onClick = {
            if (busy) return@Button
            busy = true
            error = false
            val frozenSchedule = schedule
            val frozenAsOf = asOfText
            val frozenBaseline = baselines[baselineIndex]
            scope.launch {
                val result = withContext(Dispatchers.Default) {
                    runCatching {
                        val asOf = frozenAsOf.trim().toDouble()
                        require(asOf.isFinite())
                        val entries = frozenSchedule.split(',').map { s ->
                            val pair = s.trim().split(':')
                            require(pair.size == 2)
                            val at = pair[0].trim().toDouble()
                            val dose = pair[1].trim().toDouble()
                            require(at.isFinite() && dose.isFinite() && dose > 0.0)
                            DoseEvent(
                                "demo", Route.SUBLINGUAL, at, dose, Ester.E2, 70.0,
                                DoseExtras(sublingualTier = 2.0),
                            )
                        }
                        require(entries.isNotEmpty() && entries.size <= 128)
                        ResearchShapeComparisonV01.compare(entries, asOf, frozenBaseline)
                    }.getOrNull()
                }
                comparison = result
                error = result == null
                busy = false
            }
        }, enabled = !busy) { Text(if (busy) "计算中..." else "计算虚构情景 · Render") }
        if (error) Text("输入无效或观察窗内没有可计算的给药事件。", color = MaterialTheme.colorScheme.error)
        comparison?.let { c ->
            Text("候选：${c.candidateId} · 本基线分组 ${c.matchingCandidateCount} 套 · 使用 ${c.consideredRecordedDoses} 条手动事件",
                style = MaterialTheme.typography.bodySmall)
            ResearchOverlayChart(c)
            Text("● 旧版 SL 曲线 / Legacy relative", color = primary)
            Text("● 新版 M2 曲线 / Experimental relative", color = secondary)
            Text("两者各自以 1 mg 后 1 小时响应归一化。M2 未区分含服时间、吞咽比例或生物利用度；请勿据此调整给药。",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ResearchOverlayChart(c: ResearchShapeComparison) {
    val legacyColor = MaterialTheme.colorScheme.primary
    val experimentalColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maximum = maxOf(
        1e-6,
        c.legacyRelative.maxOrNull() ?: 0.0,
        c.experimentalRelative.maxOrNull() ?: 0.0,
    ) * 1.1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("共同归一化纵轴 / shared relative scale · 0 — ${"%.2f".format(maximum)}")
        Canvas(Modifier.fillMaxWidth().height(260.dp)) {
            val left = 20.dp.toPx()
            val top = 8.dp.toPx()
            val width = size.width - left - 6.dp.toPx()
            val height = size.height - top - 12.dp.toPx()
            if (width <= 0f || height <= 0f || c.timeHours.size < 2) return@Canvas
            val t0 = c.timeHours.first()
            val total = c.timeHours.last() - t0
            for (i in 0..4) {
                val y = top + height * i / 4
                drawLine(gridColor, Offset(left,y), Offset(left+width,y))
            }
            fun shape(values: DoubleArray): Path = Path().apply {
                values.indices.forEach { i ->
                    val x = left + (((c.timeHours[i] - t0) / total) * width).toFloat()
                    val y = top + (height * (1.0 - values[i] / maximum)).toFloat()
                    if (i == 0) moveTo(x,y) else lineTo(x,y)
                }
            }
            drawPath(shape(c.legacyRelative), legacyColor, style = Stroke(2.dp.toPx()))
            drawPath(shape(c.experimentalRelative), experimentalColor, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("48h 之前 / before", style = MaterialTheme.typography.labelSmall)
            Text("观察时刻 / as-of", style = MaterialTheme.typography.labelSmall)
        }
    }
}

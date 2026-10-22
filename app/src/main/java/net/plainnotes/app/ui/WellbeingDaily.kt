package net.plainnotes.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.CheckinItemEntity
import net.plainnotes.app.data.CheckinScoreEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * UI-08 (REQUIREMENTS §138): the wellbeing page's daily tab. No scores, no judgement, no advice (wellbeing-design.md §0):
 * nothing here adds values up, compares them with a norm, colours a level as good or bad, or counts streaks.
 */

/** Tests read these; neither is spoken. */
val SunAngleKey = SemanticsPropertyKey<Float>("WellbeingSunAngle")
var SemanticsPropertyReceiver.sunAngle by SunAngleKey
val LevelSelectedColorKey = SemanticsPropertyKey<Color>("WellbeingLevelColor")
var SemanticsPropertyReceiver.levelColor by LevelSelectedColorKey

/** A: "Daily / Symptoms / Stage review" as a pill segmented control: a light track, the chosen one a white pill; tab semantics. */
@Composable fun WellbeingTabs(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.surfaceContainerHigh, shape = Corners.full, modifier = modifier.fillMaxWidth().testTag("wb-tabs")) {
        Row(Modifier.padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(R.string.wb_daily, R.string.wb_symptoms, R.string.wb_reviews).forEachIndexed { i, label ->
                val on = selected == i
                Surface(color = if (on) c.surfaceContainerLowest else Color.Transparent, contentColor = if (on) c.onSurface else c.onSurfaceVariant,
                    shape = Corners.full, shadowElevation = if (on) 1.dp else 0.dp,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).selectable(on, role = Role.Tab) { onSelect(i) }.testTag("wb-tab:$i")) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** "‹ Today · Thu 22 Oct ›": the day the daily and symptoms tabs share; the date opens the picker; next is off on today. */
@Composable fun WellbeingDateRow(day: LocalDate, onDay: (LocalDate) -> Unit, onPick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    Row(modifier.fillMaxWidth().testTag("wb-date-row"), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onDay(day.minusDays(1)) }, modifier = Modifier.size(48.dp).testTag("wb-prev-day"), colors = IconButtonDefaults.iconButtonColors(contentColor = color)) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_day)) }
        Text((if (day == today) stringResource(R.string.today) + " · " else "") + formatShortDate(day), style = MaterialTheme.typography.titleSmall, color = color,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onPick)
                .wrapContentHeight(Alignment.CenterVertically).testTag("wb-date"), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onDay(day.plusDays(1)) }, enabled = day < today, modifier = Modifier.size(48.dp).testTag("wb-next-day"),
            colors = IconButtonDefaults.iconButtonColors(contentColor = color, disabledContentColor = color.copy(alpha = 0.38f))) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_day)) }
    }
}

/** The card's title by how many enabled items have a value that day (0, some, all). */
internal fun dayCardTitle(noted: Int, total: Int, isToday: Boolean): Int = when {
    noted == 0 || total == 0 -> if (isToday) R.string.wb_card_title_none_today else R.string.wb_card_title_none_other
    noted < total -> R.string.wb_card_title_some
    else -> R.string.wb_card_title_all
}
/** The sun turns about 22° per noted item; nothing else depends on the count. */
const val SUN_STEP_DEGREES = 22f
/** The sun's line colour: a little lighter than the card (darker on a dark card), decoration only. */
internal fun sunColor(card: Color): Color = if (card.luminance() > 0.4f) lerp(card, Color.White, 0.45f) else lerp(card, Color.Black, 0.25f)

/** B: the daily card: date row, title by count, the no-judgement subtitle, "x of y noted" and a turning line sun. */
@Composable fun WellbeingDayCard(day: LocalDate, onDay: (LocalDate) -> Unit, onPick: () -> Unit, noted: Int, total: Int) {
    val tone = NotesTheme.semantic.success; val reduce = LocalReduceMotion.current
    val target = noted * SUN_STEP_DEGREES
    val angle = remember { Animatable(target) }
    LaunchedEffect(target, reduce) { if (reduce) angle.snapTo(target) else angle.animateTo(target, spring(dampingRatio = 0.6f, stiffness = 120f)) }
    val sun = sunColor(tone.container)
    Surface(color = tone.container, contentColor = tone.onContainer, shape = Corners.xxl, modifier = Modifier.fillMaxWidth().testTag("wb-day-card")) {
        Box(Modifier.clipToBounds()) {
            // Decoration: a big line sun in the top-right corner, partly cut off by the card's edge; not spoken.
            Canvas(Modifier.align(Alignment.TopEnd).offset(x = 36.dp, y = (-36).dp).size(150.dp).semantics { sunAngle = angle.value }.testTag("wb-sun")) {
                drawLineSun(sun, angle.value)
            }
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WellbeingDateRow(day, onDay, onPick, tone.onContainer)
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(dayCardTitle(noted, total, day == LocalDate.now())), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() }.testTag("wb-card-title"))
                    Text(stringResource(R.string.wb_card_subtitle), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("wb-card-subtitle"))
                    Surface(color = tone.onContainer.copy(alpha = 0.10f), contentColor = tone.onContainer, shape = Corners.full) {
                        Text(stringResource(R.string.wb_card_count, noted, total), style = MaterialTheme.typography.labelLarge.tnum(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).testTag("wb-card-count"))
                    }
                }
            }
        }
    }
}

/** A circle and eight round-capped rays, turned by [degrees]. */
fun DrawScope.drawLineSun(color: Color, degrees: Float) {
    val r = size.minDimension / 2f; val w = r * 0.07f
    rotate(degrees) {
        drawCircle(color, r * 0.42f, center, style = Stroke(w))
        repeat(8) { i ->
            val a = Math.toRadians(i * 45.0)
            val d = Offset(Math.cos(a).toFloat(), Math.sin(a).toFloat())
            drawLine(color, center + d * (r * 0.62f), center + d * (r * 0.92f), w, StrokeCap.Round)
        }
    }
}

/** The item's line icon: sun, bolt, moon, a person; a plain dot for earlier and custom items. */
private fun itemIcon(key: String?) = when (key) {
    "DAY_MOOD" -> Icons.Outlined.WbSunny; "DAY_ENERGY" -> Icons.Outlined.Bolt; "DAY_SLEEP" -> Icons.Outlined.Bedtime
    "DAY_BODY" -> Icons.Outlined.AccessibilityNew; else -> Icons.Outlined.Circle
}

/** C: one item: icon, name, "Not noted yet" / "Noted 4 of 5", "Clear", then the five buttons and the two end words. */
@Composable fun CheckinItemRow(item: CheckinItemEntity, value: Int?, index: Int, count: Int, onValue: (Int?) -> Unit) {
    val c = MaterialTheme.colorScheme; val label = checkinLabel(item)
    val daily = item.builtin_key?.startsWith("DAY_") == true
    val ends = when (item.builtin_key) {
        "DAY_MOOD" -> R.string.wb_low_mood to R.string.wb_high_mood; "DAY_ENERGY" -> R.string.wb_low_energy to R.string.wb_high_energy
        "DAY_SLEEP" -> R.string.wb_low_sleep to R.string.wb_high_sleep; else -> R.string.wb_low_body to R.string.wb_high_body
    }
    GroupedItem(index, count, modifier = Modifier.testTag("wb-item:${item.id}")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(c.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(itemIcon(item.builtin_key), null, Modifier.size(if (daily) 22.dp else 12.dp), tint = c.onSurfaceVariant) }
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(if (value == null) stringResource(R.string.wb_item_unrecorded) else stringResource(R.string.wb_item_value, value),
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.testTag("wb-item-status:${item.id}"))
                }
                if (value != null) TextButton(onClick = { onValue(null) }, modifier = Modifier.heightIn(min = 48.dp).testTag("wb-item-clear:${item.id}")) {
                    Text(stringResource(R.string.wb_clear)) }
            }
            FiveLevelButtons(value, label, Modifier.testTag("wb-levels:${item.id}")) { onValue(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (daily) stringResource(ends.first) else "1", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                Text(if (daily) stringResource(ends.second) else "5", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
    }
}

/** The colours of one level button; every chosen level looks the same (no red low, no green high). */
@Composable internal fun levelColors(selected: Boolean): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme; val action = NotesTheme.semantic.action
    return if (selected) action.container to action.onContainer else c.surfaceContainerLow to c.onSurface
}

/**
 * C: 1–5 as five equal pills (≥ 48 dp) in a radio group. Untouched means not noted: nothing is written until a pill is
 * tapped; tapping the chosen pill changes nothing; clearing is the separate "Clear". Each pill reads "<item>, 4 of 5".
 */
@Composable fun FiveLevelButtons(value: Int?, label: String, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..5).forEach { n ->
            val on = value == n; val (bg, fg) = levelColors(on)
            val spoken = stringResource(R.string.wb_level_spoken, label, n)
            Surface(color = bg, contentColor = fg, shape = Corners.full, border = if (on) null else BorderStroke(1.dp, c.outline),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).graphicsLayer { val s = if (on) 1.06f else 1f; scaleX = s; scaleY = s }
                    .selectable(on, role = Role.RadioButton) { if (!on) onPick(n) }
                    .clearAndSetSemantics { contentDescription = spoken; levelColor = bg }
                    .testTag("wb-level:$label:$n")) {
                Box(contentAlignment = Alignment.Center) { Text("$n", style = MaterialTheme.typography.titleMedium.tnum(), fontWeight = if (on) FontWeight.Bold else FontWeight.Medium) }
            }
        }
    }
}

/** The stage review's satisfaction (and anything else on 1–5): status, the five pills, the end words, "Clear". */
@Composable fun FiveLevelInput(value: Int?, low: String, high: String, label: String, onValue: (Int?) -> Unit) {
    Text(if (value == null) stringResource(R.string.wb_item_unrecorded) else stringResource(R.string.wb_item_value, value), style = MaterialTheme.typography.labelSmall)
    FiveLevelButtons(value, label) { onValue(it) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(low, style = MaterialTheme.typography.bodySmall); Text(high, style = MaterialTheme.typography.bodySmall) }
    if (value != null) TextButton(onClick = { onValue(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.wb_clear)) }
}

/** F: what the trend line draws: lines between values on consecutive days, a dot for a value with no neighbour. */
data class TrendShape(val lines: List<Pair<LocalDate, LocalDate>>, val dots: List<LocalDate>)
fun trendShape(points: Map<LocalDate, Int>): TrendShape {
    val days = points.keys.sorted()
    val lines = days.zipWithNext().filter { (a, b) -> ChronoUnit.DAYS.between(a, b) == 1L }
    val joined = lines.flatMap { listOf(it.first, it.second) }.toSet()
    return TrendShape(lines, days.filter { it !in joined })
}

/** Draws [points] (1–5) from [from] to [to]: a dot per value, lines only between consecutive days; no mean, band or reference. */
fun DrawScope.drawWellbeingTrend(points: Map<LocalDate, Int>, from: LocalDate, to: LocalDate, color: Color, width: Float) {
    val span = ChronoUnit.DAYS.between(from, to).coerceAtLeast(1).toFloat()
    val pad = width * 2
    fun at(d: LocalDate) = Offset(pad + (size.width - 2 * pad) * ChronoUnit.DAYS.between(from, d) / span,
        pad + (size.height - 2 * pad) * (5 - points.getValue(d)) / 4f)
    val shape = trendShape(points.filterKeys { it in from..to })
    shape.lines.forEach { (a, b) -> drawLine(color, at(a), at(b), width, StrokeCap.Round) }
    // Every value is a small dot in the line's own colour (owner, 2026-10-22: no big or contrasting dots); a lone day, with no
    // segment beside it, gets a slightly larger dot so it stays visible.
    val lone = shape.dots.toSet()
    points.keys.filter { it in from..to }.sorted().forEach { drawCircle(color, width * if (it in lone) 1.2f else 0.9f, at(it)) }
}

/** F: one item's thin line: "5" and "1" on the left, the line beside; read as "<item>, n days noted in the last 30 days". */
@Composable fun WellbeingTrendLine(label: String, points: Map<LocalDate, Int>, from: LocalDate, to: LocalDate, days: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val spoken = if (days == 0) stringResource(R.string.wb_trend_spoken_all, label, points.size) else stringResource(R.string.wb_trend_spoken, label, days, points.size)
    Row(modifier.fillMaxWidth().height(64.dp).clearAndSetSemantics { contentDescription = spoken }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("5", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant); Text("1", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant) }
        Canvas(Modifier.weight(1f).fillMaxHeight()) { drawWellbeingTrend(points, from, to, c.primary, 2.dp.toPx()) }
    }
}


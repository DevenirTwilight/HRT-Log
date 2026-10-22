package net.plainnotes.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.LabPanelEntity
import net.plainnotes.app.timeline.EventKind
import net.plainnotes.app.timeline.EventSource
import net.plainnotes.app.timeline.PeriodEvent

/**
 * LAB-04 (REQUIREMENTS §137): one lab draw as one timeline row. Display only: the projection, which period an event belongs
 * to and every saved row stay as they are; nothing is written and no panel is created.
 */
internal data class TimelineLabDraw(val key: String, val draw: LabDraw, val events: List<PeriodEvent>) {
    val place: String? get() = (draw.panel?.laboratory ?: draw.labs.first().laboratory)?.trim()?.takeIf { it.isNotEmpty() }
    /** Results outside their report's range, by the labs page's own [labFlag]. */
    val outOfRange: Int get() = draw.labs.count { labFlag(it) != null }
}

/** What a timeline container (upcoming, a period, unknown) lists: an event as before, or a merged draw. */
internal sealed interface TimelineRow {
    data class One(val event: PeriodEvent) : TimelineRow
    data class Draw(val draw: TimelineLabDraw) : TimelineRow
}

internal object TimelineLabDraws {
    /**
     * The draws to merge, by lab result id. Grouping is [labDraws], the labs page's own function, over every lab event on the
     * timeline: a panel is one draw; results without a panel follow the LAB-01 rule (same sampled time and laboratory text).
     * A draw of two or more results merges only when all its events sit in one container (same period, all upcoming or all
     * not); a draw split across containers (inconsistent data) stays one row per result.
     */
    fun mergeable(events: List<PeriodEvent>, upcoming: List<PeriodEvent>, panels: List<LabPanelEntity>): Map<Long, TimelineLabDraw> {
        val labEvents = (events.map { it to false } + upcoming.map { it to true }).mapNotNull { (e, up) ->
            (e.source as? EventSource.Lab)?.let { s -> s.value.id to Triple(e, up, e.displayPeriodKey) } }.toMap()
        val out = mutableMapOf<Long, TimelineLabDraw>()
        labDraws(labEvents.values.map { (it.first.source as EventSource.Lab).value }, panels).filter { it.labs.size > 1 }.forEach { d ->
            val members = d.labs.map { labEvents.getValue(it.id) }
            if (members.map { it.second to it.third }.distinct().size != 1) return@forEach
            val key = d.panel?.let { "lab-draw:panel:${it.id}" } ?: "lab-draw:group:${d.labs.minOf { it.id }}"
            val draw = TimelineLabDraw(key, d, members.map { it.first })
            d.labs.forEach { out[it.id] = draw }
        }
        return out
    }

    /** One container's events with each mergeable draw folded into one row where its first result was. */
    fun fold(container: List<PeriodEvent>, byLab: Map<Long, TimelineLabDraw>): List<TimelineRow> {
        val placed = mutableSetOf<String>()
        return container.mapNotNull { e ->
            val draw = (e.source as? EventSource.Lab)?.let { byLab[it.value.id] }
            when {
                draw == null -> TimelineRow.One(e)
                placed.add(draw.key) -> TimelineRow.Draw(draw)
                else -> null
            }
        }
    }
}

/**
 * The merged row, in the timeline's event row style: the lab type icon, "Lab test · n results", "date · laboratory", the
 * result names on one line, "n out of range" (attention, never red) and a chevron. TalkBack reads it as one sentence.
 */
@Composable internal fun TimelineLabDrawRow(item: TimelineLabDraw, onOpen: () -> Unit) {
    val n = item.draw.labs.size; val out = item.outOfRange
    val names = item.draw.labs.map { analyteLabel(it.analyte_code) }.joinToString(stringResource(R.string.lab_names_join))
    val date = formatDate(item.events.first().date)
    val spoken = (listOfNotNull(stringResource(R.string.timeline_lab_draw_spoken_title, n), date, item.place, names) +
        listOfNotNull(out.takeIf { it > 0 }?.let { stringResource(R.string.timeline_lab_draw_spoken_out, it) })).joinToString(stringResource(R.string.calendar_a11y_join))
    // The tag goes before clearAndSetSemantics, which would clear it otherwise.
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("timeline:${item.key}").clickable(role = Role.Button, onClick = onOpen)
        .clearAndSetSemantics { contentDescription = spoken; role = Role.Button; onClick { onOpen(); true } }
        .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EventTypeBadge(EventKind.LAB)
        Column(Modifier.weight(1f)) {
            // The "n out of range" label sits at the end of the title line and wraps under it when both do not fit (2× font, 320 dp).
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.timeline_lab_draw_title, n), style = MaterialTheme.typography.titleSmall)
                if (out > 0) {
                    val tone = NotesTheme.semantic.attention
                    Surface(color = tone.container, contentColor = tone.onContainer, shape = Corners.pill) {
                        Text(stringResource(R.string.timeline_lab_draw_out_of_range, out), style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Text(listOfNotNull(date, item.place).joinToString(" · "), style = MaterialTheme.typography.bodySmall.tnum(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(names, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
    }
}

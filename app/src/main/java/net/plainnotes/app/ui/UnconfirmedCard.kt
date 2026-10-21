package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.data.unconfirmed
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** UI-06 C: the home card covers the last 48 hours; older unconfirmed doses stay on the intake history page. */
val UNCONFIRMED_WINDOW: Duration = Duration.ofHours(48)

/** Unconfirmed doses (missed, nobody confirmed) whose planned time is within [UNCONFIRMED_WINDOW] before [now]; newest first. */
fun recentUnconfirmed(records: List<RecordEntity>, now: Instant): List<RecordEntity> {
    val from = now.minus(UNCONFIRMED_WINDOW).toEpochMilli(); val to = now.toEpochMilli()
    return records.filter { it.deleted_at_utc == null && it.unconfirmed && (it.scheduled_utc ?: Long.MIN_VALUE) in from..to }
        .sortedWith(compareByDescending<RecordEntity> { it.scheduled_utc }.thenByDescending { it.id })
}

/**
 * UI-06 C: "No entry yet" under the today card. A dashed unconfirmed outline and a ?, at most two doses, the rest behind
 * "n more" (the intake history, filtered to them). "I took it" opens the existing backfill dialog; "I didn't take it" is the
 * existing confirm-missed. Calm wording only. Not shown when nothing is unconfirmed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun UnconfirmedCard(rows: List<RecordEntity>, today: LocalDate, zone: ZoneId, medOf: (RecordEntity) -> MedicationEntity?,
                                onTook: (RecordEntity) -> Unit, onNotTaken: (RecordEntity) -> Unit, onMore: (List<RecordEntity>) -> Unit) {
    if (rows.isEmpty()) return
    val style = StatusStyles.of(DoseState.UNCONFIRMED); val tone = NotesTheme.semantic.unconfirmed; val c = MaterialTheme.colorScheme
    val simple = LocalSimpleMode.current
    Surface(color = c.surfaceContainerLowest, shape = Corners.card,
        modifier = Modifier.fillMaxWidth().statusOutline(style, tone, Corners.card).testTag("unconfirmed-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.take(2).forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = c.outlineVariant)
                val at = Instant.ofEpochMilli(r.scheduled_utc!!).atZone(zone); val time = formatTime(at.toInstant())
                val title = when (at.toLocalDate()) {
                    today -> stringResource(R.string.confirm_title_today, time)
                    today.minusDays(1) -> stringResource(R.string.confirm_title_yesterday, time)
                    else -> stringResource(R.string.confirm_title_date, formatShortDate(at.toLocalDate()) + " " + time)
                }
                val m = medOf(r)
                Column(Modifier.testTag("unconfirmed-row:${r.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, tint = tone.accent, modifier = Modifier.size(24.dp))
                        Text(title, style = MaterialTheme.typography.titleMedium, color = c.onSurface, modifier = Modifier.semantics { heading() }.testTag("unconfirmed-title:${r.id}"))
                    }
                    val what = listOfNotNull(if (simple) null else m?.name, (r.planned_dose ?: r.actual_dose)?.let { formatDose(it, m?.unit) }).joinToString(" · ")
                    Text(if (what.isEmpty()) stringResource(R.string.confirm_body) else stringResource(R.string.confirm_body_with, what),
                        style = MaterialTheme.typography.bodyMedium, color = tone.onContainer, modifier = Modifier.testTag("unconfirmed-body:${r.id}"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ onTook(r) }, Modifier.heightIn(min = 48.dp).testTag("unconfirmed-took:${r.id}")) { Text(stringResource(R.string.confirm_took)) }
                        OutlinedButton({ onNotTaken(r) }, Modifier.heightIn(min = 48.dp).testTag("unconfirmed-not-taken:${r.id}")) { Text(stringResource(R.string.confirm_not_taken)) }
                    }
                }
            }
            if (rows.size > 2) TextButton({ onMore(rows) }, Modifier.heightIn(min = 48.dp).testTag("unconfirmed-more")) {
                Text(stringResource(R.string.confirm_more, rows.size - 2) + " → " + stringResource(R.string.nav_history))
            }
        }
    }
}

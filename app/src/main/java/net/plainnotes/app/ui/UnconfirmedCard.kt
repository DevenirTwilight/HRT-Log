package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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

/** UI-07 D: how a row of the card was answered here; the row then collapses into one line with this result. */
enum class ConfirmAnswer { TOOK, NOT_TAKEN }

/**
 * UI-06 C: "No entry yet" under the today card. A dashed unconfirmed outline and a ?, at most two doses, the rest behind
 * "n more" (the intake history, filtered to them). "I took it" opens the existing backfill dialog; "I didn't take it" is the
 * existing confirm-missed. Calm wording only. Not shown when nothing is unconfirmed.
 *
 * UI-07: 28 dp corners, press-morph buttons, the ? status icon, and [answered] rows — ones answered here that are no
 * longer unconfirmed — shrink into a one-line result ("Logged" / "Noted: not taken"). A row whose dialog was cancelled
 * stays open, because it is still unconfirmed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun UnconfirmedCard(rows: List<RecordEntity>, today: LocalDate, zone: ZoneId, medOf: (RecordEntity) -> MedicationEntity?,
                                onTook: (RecordEntity) -> Unit, onNotTaken: (RecordEntity) -> Unit, onMore: (List<RecordEntity>) -> Unit,
                                answered: List<Pair<RecordEntity, ConfirmAnswer>> = emptyList()) {
    if (rows.isEmpty() && answered.isEmpty()) return
    val style = StatusStyles.of(DoseState.UNCONFIRMED); val tone = NotesTheme.semantic.unconfirmed; val c = MaterialTheme.colorScheme
    val simple = LocalSimpleMode.current; val reduce = LocalReduceMotion.current; val type = NotesTheme.typeExtra
    val enter = if (reduce) EnterTransition.None else expandVertically(motionSpec(false)) + fadeIn(motionSpec(false))
    Surface(color = c.surfaceContainerLowest, shape = Corners.xl,
        modifier = Modifier.fillMaxWidth().statusOutline(style, tone, Corners.xl).testTag("unconfirmed-card")) {
        Column(Modifier.padding(20.dp).animateContentSize(motionSpec(reduce)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            @Composable fun titleOf(r: RecordEntity): String {
                val at = Instant.ofEpochMilli(r.scheduled_utc!!).atZone(zone); val time = formatTime(at.toInstant())
                return when (at.toLocalDate()) {
                    today -> stringResource(R.string.confirm_title_today, time)
                    today.minusDays(1) -> stringResource(R.string.confirm_title_yesterday, time)
                    else -> stringResource(R.string.confirm_title_date, formatShortDate(at.toLocalDate()) + " " + time)
                }
            }
            @Composable fun whenOf(r: RecordEntity): String {
                val at = Instant.ofEpochMilli(r.scheduled_utc!!).atZone(zone); val time = formatTime(at.toInstant())
                return when (at.toLocalDate()) { today -> stringResource(R.string.today); today.minusDays(1) -> stringResource(R.string.yesterday); else -> formatShortDate(at.toLocalDate()) } + " " + time
            }
            // Answered here: one line each, the result in words beside the icon of what it became.
            answered.forEach { (r, a) ->
                key("answered-${r.id}") {
                    val shown = remember { MutableTransitionState(reduce) }.apply { targetState = true }
                    AnimatedVisibility(shown, enter = enter) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("unconfirmed-result:${r.id}"), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatusIcon(if (a == ConfirmAnswer.TOOK) StatusIconKind.TAKEN else StatusIconKind.MISSED, StatusIconSizes.M)
                            Text(whenOf(r) + " · " + stringResource(if (a == ConfirmAnswer.TOOK) R.string.today_logged else R.string.confirm_result_not_taken),
                                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                        }
                    }
                }
            }
            rows.take(2).forEachIndexed { i, r ->
                key(r.id) {
                if (i > 0 || answered.isNotEmpty()) HorizontalDivider(color = c.outlineVariant)
                val m = medOf(r)
                Column(Modifier.testTag("unconfirmed-row:${r.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusIcon(StatusIconKind.UNCONFIRMED, StatusIconSizes.L, Modifier.testTag("unconfirmed-icon:${r.id}"))
                        Text(titleOf(r), style = type.cardTitle, color = c.onSurface, modifier = Modifier.semantics { heading() }.testTag("unconfirmed-title:${r.id}"))
                    }
                    val what = listOfNotNull(if (simple) null else m?.name, (r.planned_dose ?: r.actual_dose)?.let { formatDose(it, m?.unit) }).joinToString(" · ")
                    Text(if (what.isEmpty()) stringResource(R.string.confirm_body) else stringResource(R.string.confirm_body_with, what),
                        style = MaterialTheme.typography.bodyMedium, color = tone.onContainer, modifier = Modifier.testTag("unconfirmed-body:${r.id}"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // UI-07 B: both answers press-morph from fully round to 16 dp.
                        val tookPress = remember { MutableInteractionSource() }; val tookMorph = rememberPressMorph(tookPress, rest = 24.dp)
                        Button({ onTook(r) }, Modifier.heightIn(min = 48.dp).pressMorph(tookMorph).testTag("unconfirmed-took:${r.id}"), shape = tookMorph.shape,
                            interactionSource = tookPress) { Text(stringResource(R.string.confirm_took)) }
                        val notPress = remember { MutableInteractionSource() }; val notMorph = rememberPressMorph(notPress, rest = 24.dp)
                        OutlinedButton({ onNotTaken(r) }, Modifier.heightIn(min = 48.dp).pressMorph(notMorph).testTag("unconfirmed-not-taken:${r.id}"), shape = notMorph.shape,
                            interactionSource = notPress) { Text(stringResource(R.string.confirm_not_taken)) }
                    }
                }
                }
            }
            if (rows.size > 2) TextButton({ onMore(rows) }, Modifier.heightIn(min = 48.dp).testTag("unconfirmed-more")) {
                Text(stringResource(R.string.confirm_more, rows.size - 2) + " → " + stringResource(R.string.nav_history))
            }
        }
    }
}

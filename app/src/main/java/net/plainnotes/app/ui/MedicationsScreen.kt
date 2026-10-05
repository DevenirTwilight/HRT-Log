package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesState
import net.plainnotes.app.R
import net.plainnotes.app.data.MedicationEntity

@Composable fun MedicationsScreen(state: NotesState, onAdd: () -> Unit, onEdit: (MedicationEntity) -> Unit, onArchive: (MedicationEntity) -> Unit, contentPadding: PaddingValues) {
    val list = state.medications.sortedWith(compareByDescending<MedicationEntity> { it.active }.thenBy { it.sort_order }.thenBy { it.id })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (list.isEmpty()) item {
            EmptyState(Icons.Outlined.Medication, stringResource(R.string.no_medications), stringResource(R.string.empty_body)) {
                Button(onClick = onAdd) { Text(stringResource(R.string.add_medication)) }
            }
        }
        items(list, key = { it.id }) { m -> MedicationCard(m, state, { onEdit(m) }, { onArchive(m) }) }
    }
}

@Composable private fun MedicationCard(m: MedicationEntity, state: NotesState, onEdit: () -> Unit, onArchive: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val profile = state.profiles[m.id]
    ElevatedCard(onClick = onEdit, modifier = Modifier.fillMaxWidth().alpha(if (m.active) 1f else 0.6f),
        colors = CardDefaults.elevatedCardColors(containerColor = c.surfaceContainerLow), elevation = CardDefaults.elevatedCardElevation(0.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = if (m.molecule == "E2") c.primaryContainer else c.tertiaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Medication, null, tint = if (m.molecule == "E2") c.onPrimaryContainer else c.onTertiaryContainer)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium)
                val detail = listOfNotNull(choiceLabel(profile?.ester?.takeIf { it != "E2" } ?: m.molecule), m.route?.let { choiceLabel(it) })
                Text(detail.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                Text(stringResource(R.string.per_intake, formatDose(m.dose_per_intake, m.unit)) + " · " + scheduleText(state.schedules[m.id]),
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (!m.active) StatusPill(stringResource(R.string.archived), c.surfaceContainerHighest, c.onSurfaceVariant)
                    else if (!m.notifications_on) StatusPill(stringResource(R.string.reminders_off), c.surfaceContainerHighest, c.onSurfaceVariant, Icons.Outlined.NotificationsOff)
                    if (m.molecule == "E2" && net.plainnotes.app.conc.ConcentrationCalculator.missingFor(m, profile).isEmpty())
                        StatusPill(stringResource(R.string.pk_ready), c.secondaryContainer, c.onSecondaryContainer, Icons.AutoMirrored.Outlined.ShowChart)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                    if (m.active) DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { menu = false; onArchive() })
                }
            }
        }
    }
}

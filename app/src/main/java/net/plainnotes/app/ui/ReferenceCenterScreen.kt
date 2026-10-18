package net.plainnotes.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.MedicationEntity

/**
 * Navigation-only future module. A tap never starts a file picker, writes a database row,
 * opens an unreviewed official URL, or exposes an unfinished production feature.
 */
@Composable
fun FutureModuleCard(@StringRes title: Int, @StringRes explanation: Int, tag: String) {
    var open by remember { mutableStateOf(false) }
    OutlinedCard(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ui_skeleton_coming_soon),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ui_skeleton_unavailable))
                    Text(stringResource(explanation))
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.testTag("skeleton-dialog-close")) {
                    Text(stringResource(R.string.ok))
                }
            },
            modifier = Modifier.testTag("skeleton-unavailable-dialog")
        )
    }
}

@Composable
private fun CenterLinkCard(@StringRes title: Int, @StringRes summary: Int, tag: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * UI skeleton around already-shipped manual medication references. Not a licensed ANSM catalogue:
 * no official dataset or summary is bundled, and unavailable cards never claim verified facts.
 */
@Composable
fun ReferenceCenterScreen(
    medications: List<MedicationEntity>,
    onMedications: () -> Unit,
    onReference: (MedicationEntity) -> Unit,
    onStock: () -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("reference-center"),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item("introduction") {
            Text(stringResource(R.string.ui_skeleton_ref_intro), style = MaterialTheme.typography.bodyMedium)
        }
        item("ready-heading") {
            Text(stringResource(R.string.ui_skeleton_ready), style = MaterialTheme.typography.titleMedium)
        }
        item("medication-plans") {
            CenterLinkCard(R.string.medications, R.string.ui_skeleton_manage_existing,
                "reference-center-medications", onMedications)
        }
        item("existing-stock") {
            CenterLinkCard(R.string.stock, R.string.ui_skeleton_existing_stock,
                "reference-center-stock", onStock)
        }
        if (medications.isEmpty()) {
            item("empty") {
                Text(stringResource(R.string.ui_skeleton_no_medications),
                    modifier = Modifier.testTag("reference-center-empty"))
            }
        } else {
            item("my-medications") {
                Text(stringResource(R.string.ui_skeleton_saved_medications),
                    style = MaterialTheme.typography.titleMedium)
            }
            items(medications, key = { "reference:${it.id}" }) { med ->
                OutlinedCard(onClick = { onReference(med) },
                    modifier = Modifier.fillMaxWidth().testTag("reference-center-item:${med.id}")) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(med.name, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.ui_skeleton_user_reference),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item("future-heading") {
            Text(stringResource(R.string.ui_skeleton_future), style = MaterialTheme.typography.titleMedium)
        }
        item("official") {
            FutureModuleCard(R.string.ui_skeleton_official_catalogue,
                R.string.ui_skeleton_official_desc, "reference-center-official")
        }
        item("documents") {
            FutureModuleCard(R.string.ui_skeleton_rcp_notice,
                R.string.ui_skeleton_rcp_desc, "reference-center-documents")
        }
        item("safety") {
            FutureModuleCard(R.string.ui_skeleton_safety,
                R.string.ui_skeleton_safety_desc, "reference-center-safety")
        }
        item("supply") {
            FutureModuleCard(R.string.ui_skeleton_supply,
                R.string.ui_skeleton_supply_desc, "reference-center-supply")
        }
    }
}

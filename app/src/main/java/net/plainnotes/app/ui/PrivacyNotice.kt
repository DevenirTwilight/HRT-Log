package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.plainnotes.app.R

/** Sections of the offline privacy notice, in reading order. Every statement is mapped to code in
 * docs/privacy-evidence.md; keep both in sync. */
internal val PRIVACY_NOTICE_SECTIONS = listOf(
    R.string.privacy_notice_purpose_title to R.string.privacy_notice_purpose_body,
    R.string.privacy_notice_device_title to R.string.privacy_notice_device_body,
    R.string.privacy_notice_storage_title to R.string.privacy_notice_storage_body,
    R.string.privacy_notice_export_title to R.string.privacy_notice_export_body,
    R.string.privacy_notice_backup_title to R.string.privacy_notice_backup_body,
    R.string.privacy_notice_import_title to R.string.privacy_notice_import_body,
    R.string.privacy_notice_notify_title to R.string.privacy_notice_notify_body,
    R.string.privacy_notice_disguise_title to R.string.privacy_notice_disguise_body,
    R.string.privacy_notice_delete_title to R.string.privacy_notice_delete_body,
    R.string.privacy_notice_changes_title to R.string.privacy_notice_changes_body,
)

/** Full privacy notice, bundled in the app and readable offline. Opened only from About inside the unlocked app;
 * the disguise shells never show it. */
@Composable fun PrivacyNoticeDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("privacy-notice")) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.privacy_notice_title), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("privacy-notice-close")) {
                        Text(stringResource(R.string.privacy_notice_close))
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.privacy_notice_intro), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PRIVACY_NOTICE_SECTIONS.forEachIndexed { index, (title, body) ->
                        Column(Modifier.testTag("privacy-section-$index"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

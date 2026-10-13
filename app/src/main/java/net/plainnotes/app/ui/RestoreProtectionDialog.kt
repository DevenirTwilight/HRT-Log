package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.privacy.RestoreProtection

@Composable fun RestoreProtectionDialog(notice:RestoreProtection.Notice?,onCancel:()->Unit,onBackup:()->Unit,onContinue:()->Unit) {
    AlertDialog(onDismissRequest=onCancel,title={Text(stringResource(R.string.restore_protection_title))},
        text={Column(Modifier.testTag("restore-protection").verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(stringResource(R.string.backup_restore_warning))
            Text(stringResource(R.string.restore_protection_note),style=MaterialTheme.typography.bodySmall)
            notice?.let {Text(stringResource(when(it){
                RestoreProtection.Notice.CANCELLED->R.string.restore_backup_cancelled
                RestoreProtection.Notice.FAILED->R.string.restore_backup_failed
                RestoreProtection.Notice.SAVED->R.string.restore_backup_saved
                RestoreProtection.Notice.SAME_TARGET->R.string.restore_backup_same_target
            }),Modifier.testTag("restore-backup-result"),style=MaterialTheme.typography.bodySmall)}
            Button(onClick=onBackup,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.restore_backup_current))}
            OutlinedButton(onClick=onContinue,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.restore_continue))}
            TextButton(onClick=onCancel,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.cancel))}
        }},confirmButton={})
}

package net.plainnotes.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R

/** Ordinary text semantics: the full notice is available to accessibility services. */
@Composable fun ExportPrivacyNotice() {
    Text(stringResource(R.string.export_privacy_notice),Modifier.testTag("export-privacy-notice"),
        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

/** CSV previously opened SAF directly; the other exports reuse their existing options dialog. */
@Composable fun PlainExportDialog(title:String,onDismiss:()->Unit,onExport:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},
        text={Column(Modifier.verticalScroll(rememberScrollState())){ExportPrivacyNotice()}},
        confirmButton={
            val actions=listOf(stringResource(R.string.export_action),stringResource(R.string.cancel))
            AdaptiveActions(actions,contentInset=48.dp){i,mod->
                if(i==0)Button(onClick=onExport,modifier=mod){Text(actions[i])}
                else TextButton(onClick=onDismiss,modifier=mod){Text(actions[i])}
            }
        })
}

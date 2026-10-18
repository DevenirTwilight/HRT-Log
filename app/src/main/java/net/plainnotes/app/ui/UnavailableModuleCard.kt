package net.plainnotes.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R

/** Presentation only: this component has no business callback, repository, URI or importer. */
@Composable internal fun UnavailableModuleCard(@StringRes title:Int,@StringRes reason:Int,tag:String) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(onClick={open=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag(tag)) {
        Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Info,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title),style=MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.center_not_open),style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    if(open)AlertDialog(onDismissRequest={open=false},modifier=Modifier.testTag("center-unavailable-dialog"),
        title={Text(stringResource(title))},
        text={Column(Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.center_not_open));Text(stringResource(reason))
        }},confirmButton={TextButton(onClick={open=false},modifier=Modifier.heightIn(min=48.dp).testTag("center-unavailable-close")) {Text(stringResource(R.string.ok))}})
}

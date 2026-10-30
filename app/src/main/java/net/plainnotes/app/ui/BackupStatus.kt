package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.plainnotes.app.R
import net.plainnotes.app.privacy.BackupHistory
import java.time.ZoneId

/** Only composed by the unlocked main data page; no receiver, notification, or background schedule. */
@Composable fun BackupStatus(history:BackupHistory) {
    val state by history.state.collectAsStateWithLifecycle(context=kotlinx.coroutines.Dispatchers.Main.immediate)
    val owner=LocalLifecycleOwner.current
    var revision by remember{mutableIntStateOf(0)}
    DisposableEffect(owner,history) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME){history.refresh();revision++}}
        owner.lifecycle.addObserver(observer)
        onDispose{owner.lifecycle.removeObserver(observer)}
    }
    val now=remember(revision,state){history.now()}
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val time=state.lastExport?.let{instant->val z=instant.atZone(ZoneId.systemDefault());formatDate(z.toLocalDate())+" "+formatTime(z.toLocalTime())}
            ?: stringResource(R.string.backup_history_never)
        Text(stringResource(R.string.backup_history_last,time),Modifier.testTag("backup-history-last"),style=MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.backup_history_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(state.reminderEnabled,role=Role.Switch){history.configure(it,state.days)},verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end=12.dp)) {
                Text(stringResource(R.string.backup_reminder_title),style=MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.backup_reminder_note),style=MaterialTheme.typography.bodySmall)
            }
            Switch(state.reminderEnabled,onCheckedChange=null)
        }
        if(state.reminderEnabled) {
            AdaptiveChoice(BackupHistory.intervals.map{stringResource(R.string.backup_reminder_days,it)},BackupHistory.intervals.indexOf(state.days),{history.configure(true,BackupHistory.intervals[it])})
            if(history.due(now))Text(stringResource(R.string.backup_reminder_due),Modifier.testTag("backup-reminder-due"),style=MaterialTheme.typography.bodySmall)
        }
    }
}

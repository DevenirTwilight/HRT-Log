package net.plainnotes.app.disguise.privatenotes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PrivateNoteEditor(state:PrivateNotesModel.State,onDraft:(String,String)->Unit,onCancel:()->Unit,onSave:()->Unit) {
    BackHandler{onCancel()}
    Scaffold(topBar={TopAppBar(title={Text(stringResource(if(state.id==null)R.string.private_new else R.string.private_edit))},
        navigationIcon={IconButton(onClick=onCancel){Icon(Icons.Outlined.Close,stringResource(R.string.private_cancel))}},
        actions={TextButton(enabled=!state.saving && (state.title.isNotBlank() || state.body.isNotBlank()),onClick=onSave){Text(stringResource(R.string.private_save))}})}){pad->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize().imePadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(state.error)Text(stringResource(R.string.private_error),color=MaterialTheme.colorScheme.error)
            OutlinedTextField(state.title,{onDraft(it,state.body)},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.private_title))},singleLine=true,enabled=!state.saving)
            OutlinedTextField(state.body,{onDraft(state.title,it)},Modifier.fillMaxWidth().weight(1f),label={Text(stringResource(R.string.private_body))},enabled=!state.saving)
        }
    }
}

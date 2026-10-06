package net.plainnotes.app.disguise.privatenotes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PrivateNotesScreen(state:PrivateNotesModel.State,onClose:()->Unit,onEdit:(PrivateNote?)->Unit,onDraft:(String,String)->Unit,
    onCancel:()->Unit,onSave:()->Unit,onDelete:(String)->Unit,onRetry:()->Unit) {
    if(state.editing){PrivateNoteEditor(state,onDraft,onCancel,onSave);return}
    var deleting by remember{mutableStateOf<PrivateNote?>(null)}
    Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.private_notes))},navigationIcon={IconButton(onClick=onClose){Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.private_close))}})},
        floatingActionButton={if(!state.loading && !state.error)FloatingActionButton(onClick={onEdit(null)}){Icon(Icons.Outlined.Add,stringResource(R.string.private_new))}}){pad->
        when {
            state.loading->Box(Modifier.padding(pad).fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            state.error->Column(Modifier.padding(pad).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(stringResource(R.string.private_error));OutlinedButton(onClick=onRetry){Text(stringResource(R.string.private_retry))}
            }
            state.notes.isEmpty()->Column(Modifier.padding(pad).fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                Icon(Icons.Outlined.Lock,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp));Text(stringResource(R.string.private_empty),style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp));Text(stringResource(R.string.private_empty_help),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else->LazyColumn(Modifier.padding(pad).fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=16.dp,bottom=96.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                items(state.notes,key={it.id}){note->
                    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.fillMaxWidth()){
                        Row(Modifier.clickable(enabled=!state.saving){onEdit(note)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                Text(note.title.ifBlank{note.body.lineSequence().firstOrNull{it.isNotBlank()} ?: stringResource(R.string.private_untitled)},style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                                if(note.body.isNotBlank())Text(note.body,style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(note.updatedAt)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(enabled=!state.saving,onClick={deleting=note}){Icon(Icons.Outlined.Delete,stringResource(R.string.private_delete))}
                        }
                    }
                }
            }
        }
    }
    deleting?.let{note->AlertDialog(onDismissRequest={deleting=null},title={Text(stringResource(R.string.private_delete))},text={Text(stringResource(R.string.private_delete_confirm))},
        confirmButton={TextButton(onClick={deleting=null;onDelete(note.id)}){Text(stringResource(R.string.private_delete))}},
        dismissButton={TextButton(onClick={deleting=null}){Text(stringResource(R.string.private_cancel))}})}
}

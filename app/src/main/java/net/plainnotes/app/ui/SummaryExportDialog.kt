package net.plainnotes.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.NotesState
import net.plainnotes.app.R
import net.plainnotes.app.security.launchPicker
import java.time.LocalDate

@Composable fun SummaryExportDialog(model:NotesViewModel,state:NotesState,onDismiss:()->Unit) {
    val context=LocalContext.current;val labels=checkinLabels();val schedules=state.medications.associate{it.id to scheduleText(state.schedules[it.id])}
    var from by rememberSaveable{mutableStateOf(LocalDate.now().minusDays(29).toString())};var to by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var pick by remember{mutableIntStateOf(0)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->if(uri!=null)model.exportSummary(uri,LocalDate.parse(from),LocalDate.parse(to),context,labels,schedules);onDismiss()}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.wb_summary))},text={Column{
        TextButton(onClick={pick=1}){Text(stringResource(R.string.batch_from)+": "+from)}
        TextButton(onClick={pick=2}){Text(stringResource(R.string.batch_to)+": "+to)}
        Text(stringResource(R.string.wb_summary_footer))
    }},confirmButton={
        val actions=listOf(stringResource(R.string.export_pdf),stringResource(R.string.cancel))
        AdaptiveActions(actions,contentInset=48.dp){i,mod->
            if(i==0)Button(enabled=from<=to,onClick={launcher.launchPicker("notes-summary-${LocalDate.now()}.pdf")},modifier=mod){Text(actions[i])}
            else TextButton(onClick=onDismiss,modifier=mod){Text(actions[i])}
        }
    })
    if(pick!=0)DatePickerModal(LocalDate.parse(if(pick==1)from else to),{pick=0}){if(it<=LocalDate.now()){if(pick==1)from=it.toString()else to=it.toString()};pick=0}
}

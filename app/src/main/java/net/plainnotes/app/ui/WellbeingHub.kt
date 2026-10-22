package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesState
import net.plainnotes.app.R
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.data.StageReviewEntity
import java.time.LocalDate

/**
 * UI-08 (§138): the wellbeing hub. Pill tabs; the daily and symptoms tabs share one day (saved across recreation); "Choose
 * items" is in the top bar now (AppShell), not at the bottom of the page.
 */
@Composable fun WellbeingHub(state:NotesState,extra:NotesViewModel.ExtraState,model:NotesViewModel,region:String?,padding:PaddingValues)=
    WellbeingHubContent(state,extra,region,padding,model::setScore,model::setNote,model::setSymptom,model::deleteReview,model::saveReview)

/** The hub without the view model (same callbacks), so tests can drive the tabs and the shared day. */
@Composable fun WellbeingHubContent(state:NotesState,extra:NotesViewModel.ExtraState,region:String?,padding:PaddingValues,
    onScore:(LocalDate,Long,Int?)->Unit,onNote:(LocalDate,String)->Unit,onSymptom:(LocalDate,String,Boolean,String?)->Unit,
    onDeleteReview:(Long)->Unit,onSaveReview:(StageReviewEntity)->Unit) {
    var tab by rememberSaveable{mutableIntStateOf(0)};var date by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var pick by remember{mutableStateOf(false)}
    var editingId by rememberSaveable{mutableStateOf<Long?>(null)};var newReview by rememberSaveable{mutableStateOf(false)}
    val editing=extra.reviews.firstOrNull{it.id==editingId}
    fun setDate(d:LocalDate){if(d<=LocalDate.now())date=d.toString()}
    Column(Modifier.fillMaxSize().padding(padding)) {
        WellbeingTabs(tab,{tab=it},Modifier.padding(start=16.dp,end=16.dp,top=8.dp))
        if(tab==0)WellbeingScreen(extra.items,extra.scores,extra.notes,onScore,onNote,PaddingValues(),date){date=it}
        else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(tab==1){WellbeingDateRow(LocalDate.parse(date),::setDate,{pick=true});SymptomsScreen(LocalDate.parse(date),state.medications,state.profiles,extra.symptoms,region,onSymptom)}
            else ReviewList(extra.reviews,{editingId=it?.id;newReview=it==null},onDeleteReview)
        }
    }
    if(pick)DatePickerModal(LocalDate.parse(date),{pick=false}){setDate(it);pick=false}
    if(newReview||editing!=null) StageReviewDialog(editing,extra.reviews,extra.symptoms,extra.effects,{editingId=null;newReview=false}){onSaveReview(it);editingId=null;newReview=false}
}

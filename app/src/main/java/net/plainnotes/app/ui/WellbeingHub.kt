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

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WellbeingHub(state:NotesState,extra:NotesViewModel.ExtraState,model:NotesViewModel,region:String?,onManage:()->Unit,padding:PaddingValues) {
    var tab by rememberSaveable{mutableIntStateOf(0)};var date by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var pick by remember{mutableStateOf(false)}
    var editingId by rememberSaveable{mutableStateOf<Long?>(null)};var newReview by rememberSaveable{mutableStateOf(false)}
    val editing=extra.reviews.firstOrNull{it.id==editingId}
    Column(Modifier.fillMaxSize().padding(padding)) {
        PrimaryTabRow(tab){listOf(R.string.wb_daily,R.string.wb_symptoms,R.string.wb_reviews).forEachIndexed{i,label->Tab(tab==i,{tab=i},text={Text(stringResource(label))})}}
        if(tab==0)WellbeingScreen(extra.items,extra.scores,extra.notes,model::setScore,model::setNote,onManage,PaddingValues())
        else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(tab==1){TextButton(onClick={pick=true}){Text(date)};SymptomsScreen(LocalDate.parse(date),state.medications,state.profiles,extra.symptoms,region,model::setSymptom)}
            else ReviewList(extra.reviews,{editingId=it?.id;newReview=it==null},model::deleteReview)
            TextButton(onClick=onManage){Text(stringResource(R.string.wb_manage))}
        }
    }
    if(pick)DatePickerModal(LocalDate.parse(date),{pick=false}){if(it<=LocalDate.now())date=it.toString();pick=false}
    if(newReview||editing!=null) StageReviewDialog(editing,extra.reviews,extra.symptoms,extra.effects,{editingId=null;newReview=false}){model.saveReview(it);editingId=null;newReview=false}
}

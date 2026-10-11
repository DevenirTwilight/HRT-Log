package net.plainnotes.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import net.plainnotes.app.NotesState
import net.plainnotes.app.ui.*
import net.plainnotes.app.conc.ConcentrationCalculator
import androidx.compose.runtime.*
import net.plainnotes.app.data.*
import net.plainnotes.app.experimental.UnifiedConcentrationScreen
import java.time.Instant

/** Debug-only consolidated host. Entirely synthetic; never opens a database or accepts input health records. */
class UnifiedConcentrationTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val now=Instant.parse("2026-03-10T12:00:00Z")
        val med=MedicationEntity(1,"Synthetic SL E2","E2","SUBLINGUAL","MG",1.0,30.0,null,15,120,false,null,true,true,0)
        val profile=ProfileEntity(1,"E2","sublingual",sl_tier=2)
        val records=listOf(30L,20L,3L).mapIndexed {i,h -> RecordEntity((i+1).toLong(),1,
            taken_utc=now.toEpochMilli()-h*3600000,taken_zone="UTC",actual_dose=1.0,status="ON_TIME",origin="APP",revision=1,
            config_snapshot=MedicationSnapshot.encode(med,profile)) }
        val labs=listOf(LabValueEntity(1,"E2",150.0,"pg/mL",now.minusSeconds(2*3600).toEpochMilli(),"UTC"))
        val state=NotesState(medications=listOf(med),profiles=mapOf(1L to profile),loading=false)
        val result=ConcentrationCalculator.compute(state.medications,state.profiles,records,emptyList(),labs,70.0,now,calibrate=false)
        setContent { MaterialTheme {
            var pmol by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            UnifiedConcentrationScreen(records,emptyMap(),labs,PaddingValues(),{pad,preview,selector ->
                ConcentrationScreen(state,result,false,70.0,ConcSettings(pmol,false,net.plainnotes.app.pk.CalibrationMode.RETROSPECTIVE),
                    {pmol=it.pmol},{},{},{},pad,records,state.profiles,preview=preview,modelSelector=selector)
            },{now})
        } }
    }
}

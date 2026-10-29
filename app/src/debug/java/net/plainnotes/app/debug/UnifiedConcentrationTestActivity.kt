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
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import net.plainnotes.app.data.*
import net.plainnotes.app.experimental.UnifiedConcentrationScreen
import java.time.Instant

/** Debug-only consolidated host. Entirely synthetic; never opens a database or accepts input health records. */
class UnifiedConcentrationTestActivity : ComponentActivity() {
    companion object { const val REAL_WINDOW_RECOMPOSER = "synthetic-real-window-recomposer" }
    var instrumentationRecomposer: Recomposer? = null
        private set
    var previewDelayMillis by mutableLongStateOf(0L)
    var previewFails by mutableStateOf(false)
    var syntheticHistoryRevision by mutableIntStateOf(0)
    var syntheticSublingualEnabled by mutableStateOf(true)
    var syntheticExtraRoute by mutableStateOf<String?>(null)
    var syntheticUnknownContext by mutableStateOf(false)
    var syntheticWrongPreview by mutableStateOf(false)
    var syntheticLabPgMl by mutableDoubleStateOf(150.0)
    /** D-1 tests: shifts the clock the screen reads, and the preview the original screen was last given (test oracle). */
    @Volatile var clockShiftMillis = 0L
    var refreshMillis = 60_000L
    @Volatile var lastPreview: net.plainnotes.app.experimental.ConcentrationPreview? = null
    /** Visual audit only: render with the app's own theme (light/dark) instead of the plain MaterialTheme. */
    var auditDark by mutableStateOf<Boolean?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val now=Instant.parse("2026-03-10T12:00:00Z")
        val med=MedicationEntity(1,"Synthetic SL E2","E2","SUBLINGUAL","MG",1.0,30.0,null,15,120,false,null,true,true,0)
        val profile=ProfileEntity(1,"E2","sublingual",sl_tier=2)
        val baseRecords=listOf(30L,20L,3L).mapIndexed {i,h -> RecordEntity((i+1).toLong(),1,
            taken_utc=now.toEpochMilli()-h*3600000,taken_zone="UTC",actual_dose=1.0,status="ON_TIME",origin="APP",revision=1,
            config_snapshot=MedicationSnapshot.encode(med,profile)) }
        // Opt-in instrumentation uses the same lifecycle/main-thread recomposer as a real window.
        val parent = if (intent.getBooleanExtra(REAL_WINDOW_RECOMPOSER, false))
            window.decorView.createLifecycleAwareWindowRecomposer(lifecycle = lifecycle).also { instrumentationRecomposer = it }
        else null
        setContent(parent = parent) { AuditTheme(auditDark) {
            val extraMed=med.copy(id=2,name="Synthetic other route",molecule=if(syntheticExtraRoute=="SPI")"SPI" else "E2",
                route=when(syntheticExtraRoute){"SPI"->"ORAL";"EV"->"SUBLINGUAL";else->syntheticExtraRoute},unit=if(syntheticExtraRoute=="PATCH")"PATCH" else "MG")
            val extraProfile=ProfileEntity(2,if(syntheticExtraRoute=="EV")"EV" else "E2",
                when(syntheticExtraRoute){"GEL"->"gel";"PATCH"->"patchApply";"EV"->"sublingual";else->"oral"},
                sl_tier=2,gel_product_id=1,patch_release_ug_day=50.0)
            val records=remember(syntheticHistoryRevision,syntheticSublingualEnabled,syntheticExtraRoute,syntheticUnknownContext) {
                (if(syntheticSublingualEnabled)baseRecords.map{it.copy(id=it.id+syntheticHistoryRevision*100)} else emptyList()) +
                    if(syntheticExtraRoute!=null || syntheticUnknownContext)listOf(RecordEntity(99,2,
                        taken_utc=now.minusSeconds(3600).toEpochMilli(),taken_zone="UTC",actual_dose=1.0,status="ON_TIME",origin="APP",revision=1,
                        config_snapshot=if(syntheticUnknownContext)"{}" else MedicationSnapshot.encode(extraMed,extraProfile))) else emptyList()
            }
            val state=NotesState(medications=(if(syntheticSublingualEnabled)listOf(med) else emptyList())+if(syntheticExtraRoute!=null)listOf(extraMed) else emptyList(),
                profiles=mapOf(1L to profile,2L to extraProfile),loading=false)
            var pmol by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            var calibrate by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            val labs=remember(syntheticLabPgMl) { listOf(LabValueEntity(1,"E2",syntheticLabPgMl,"pg/mL",now.minusSeconds(2*3600).toEpochMilli(),"UTC")) }
            val result=remember(records,labs,calibrate) { ConcentrationCalculator.compute(state.medications,state.profiles,records,emptyList(),labs,70.0,now,calibrate=calibrate) }
            // UI-12: the same second compatibility run the app performs for the non-sublingual half of the total.
            val nonSublingual=remember(records,labs) {
                if(result.models[net.plainnotes.app.pk.Curve.E2]?.any{it.key=="E2_SL"}!=true) null else {
                    val x=E2Total.nonSublingualInputs(state.medications,state.profiles,records,emptyList(),emptyMap())
                    if(x.records.isEmpty()) null else ConcentrationCalculator.compute(x.medications,x.profiles,x.records,x.planned,emptyList(),70.0,now,calibrate=false)
                }
            }
            UnifiedConcentrationScreen(records,emptyMap(),labs,PaddingValues(),{pad,preview,status,basis ->
                SideEffect { lastPreview=preview }
                ConcentrationScreen(state,result,false,70.0,ConcSettings(pmol,calibrate,net.plainnotes.app.pk.CalibrationMode.RETROSPECTIVE),
                    {pmol=it.pmol;calibrate=it.calibrate},{},{},{},pad,records,state.profiles,preview=preview,referenceNotice=status,basisNotice=basis,
                    e2NonSublingual=nonSublingual,doseTimes=records.mapNotNull{it.taken_utc}.map{java.time.Instant.ofEpochMilli(it)},labs=labs)
            },{now.plusMillis(clockShiftMillis)}, refreshMillis = refreshMillis, previewComputer = { audit,model,hour ->
                kotlinx.coroutines.delay(previewDelayMillis)
                check(!previewFails)
                if(syntheticWrongPreview) net.plainnotes.app.experimental.ConcentrationPreview.compute(audit,net.plainnotes.app.pk.experimental.ResearchModel.M1,hour)
                else net.plainnotes.app.experimental.ConcentrationPreview.compute(audit,model,hour)
            })
        } }
    }
}

@Composable private fun AuditTheme(dark: Boolean?, content: @Composable () -> Unit) {
    if (dark == null) MaterialTheme { content() } else NotesTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { androidx.compose.material3.Surface { content() } }
}

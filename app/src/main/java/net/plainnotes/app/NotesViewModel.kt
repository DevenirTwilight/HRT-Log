package net.plainnotes.app
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.reminder.ReminderCoordinator
import java.time.*
import javax.inject.Inject
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.pk.CalibrationMode

/** Current schedule of one medication, for display only. */
data class ScheduleSummary(val kind:RuleKind,val interval:Int,val weekdays:Set<DayOfWeek>,val times:List<LocalTime>)
data class NotesState(val medications:List<MedicationEntity> = emptyList(),val slots:List<TimelineEntry> = emptyList(),val appointments:List<AppointmentEntity> = emptyList(),val error:Int?=null,val loading:Boolean=true,
                      val schedules:Map<Long,ScheduleSummary> = emptyMap(),val profiles:Map<Long,ProfileEntity> = emptyMap(),val calendarStart:LocalDate=LocalDate.now())
data class EditMedication(val medication:MedicationEntity?,val profile:ProfileEntity?,val rule:RuleEntity?,val times:List<TimeEntity>)
@HiltViewModel class NotesViewModel @Inject constructor(private val repo:NotesRepository,private val reminders:ReminderCoordinator,
    @dagger.hilt.android.qualifiers.ApplicationContext private val app:android.content.Context):ViewModel() {
    val notificationSlot=MutableStateFlow<Slot?>(null)
    fun notification(id:String)=viewModelScope.launch {
        runCatching { val mapping=repo.transaction{it.mappings().firstOrNull{m->m.opaque_id==id}} ?: return@runCatching
            notificationSlot.value=repo.calendar(displayFrom=LocalDate.now().minusDays(1)).firstOrNull{mapping.identity=="${it.slot.key}:SOON"||mapping.identity=="${it.slot.key}:DUE"||mapping.identity=="${it.slot.key}:LATE"}?.slot }
    }
    private val mutable=MutableStateFlow(NotesState());val state=mutable.asStateFlow()
    var editor=MutableStateFlow<EditMedication?>(null);private set
    var override=MutableStateFlow<SlotOverride?>(null);private set
    private var calendarStart=LocalDate.now()
    fun calendarFrom(value:LocalDate?){calendarStart=value ?: LocalDate.now();refresh()}
    init { refresh();viewModelScope.launch{while(isActive){delay(30000);refresh()}} }
    fun refresh()=viewModelScope.launch { try {
        val meds=repo.medications();val slots=repo.calendar(displayFrom=calendarStart);val appts=repo.appointments()
        val schedules=repo.rules().filter{it.effective_until_utc==null}.associate { r ->
            r.medication_id to ScheduleSummary(RuleKind.valueOf(r.kind),r.interval,DayOfWeek.entries.filter{r.weekday_mask and (1 shl (it.value-1))!=0}.toSet(),
                repo.transaction{it.times(r.id)}.map{LocalTime.parse(it.local_time)}.sorted())
        }
        val profiles=meds.mapNotNull{m->repo.profile(m.id)?.let{m.id to it}}.toMap()
        mutable.value=NotesState(meds,slots,appts,null,false,schedules,profiles,calendarStart)
    }catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.data_error,loading=false)} }
    fun clearError(){mutable.value=mutable.value.copy(error=null)}
    private fun change(block:suspend()->Unit)=viewModelScope.launch{try{reminders.mutate(block);refresh()}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)}}
    fun edit(m:MedicationEntity?)=viewModelScope.launch {try {
        val r=m?.let{repo.rules().lastOrNull{r->r.medication_id==it.id&&r.effective_until_utc==null}}
        val t=if(r==null)emptyList()else repo.transaction{it.times(r.id)}
        editor.value=EditMedication(m,m?.let{repo.profile(it.id)},r,t)
    }catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)} }
    fun closeEditor(){editor.value=null}
    fun save(d:net.plainnotes.app.ui.MedicationDraft)=change {repo.saveMedication(d.medication,d.ester,d.kind,d.interval,d.times,d.weekdays,pk=d.pk);editor.value=null;loadConcentration()}
    fun editById(id:Long){state.value.medications.firstOrNull{it.id==id}?.let{edit(it)}}

    // Concentration (PK) page
    data class ConcState(val loading:Boolean=false,val result:ConcentrationResult?=null,val weight:Double?=null,val labs:List<LabValueEntity> = emptyList(),val doseTimes:List<Instant> = emptyList())
    val conc=MutableStateFlow(ConcState())
    private var concSettings=Pair(true,CalibrationMode.RETROSPECTIVE)
    fun concentrationSettings(calibrate:Boolean,mode:CalibrationMode){if(concSettings!=calibrate to mode){concSettings=calibrate to mode;loadConcentration()}}
    fun loadConcentration()=viewModelScope.launch { try {
        conc.value=conc.value.copy(loading=true)
        val now=Instant.now()
        val meds=repo.medications();val profiles=meds.mapNotNull{m->repo.profile(m.id)?.let{m.id to it}}.toMap()
        val records=repo.records();val labs=repo.labs();val weight=repo.weight()
        val planned=repo.planned(now.minusSeconds(3600),now.plusSeconds(ConcentrationCalculator.FORECAST_DAYS*86400))
        val result=withContext(Dispatchers.Default){ConcentrationCalculator.compute(meds,profiles,records,planned,labs,weight,now,concSettings.first,concSettings.second)}
        val e2=meds.filter{it.molecule=="E2"}.map{it.id}.toSet()
        val doseTimes=records.filter{it.medication_id in e2 && it.status in listOf("ON_TIME","LATE") && it.taken_utc!=null}.map{Instant.ofEpochMilli(it.taken_utc!!)}.sorted()
        conc.value=ConcState(false,result,weight,labs,doseTimes)
    }catch(_:Exception){conc.value=conc.value.copy(loading=false);mutable.value=mutable.value.copy(error=R.string.data_error)} }
    // History, stock and well-being
    data class ExtraState(val records:List<RecordEntity> = emptyList(),val containers:List<ContainerEntity> = emptyList(),val items:List<CheckinItemEntity> = emptyList(),
                          val scores:List<CheckinScoreEntity> = emptyList(),val notes:List<DayNoteEntity> = emptyList())
    val extra=MutableStateFlow(ExtraState())
    private fun guarded(block:suspend()->Unit)=viewModelScope.launch{try{block()}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)}}
    fun loadExtra()=guarded {
        val today=LocalDate.now()
        extra.value=ExtraState(repo.records(),repo.containers(),repo.checkinItems(),repo.scores(today.minusYears(5),today),repo.notes(today.minusYears(5),today))
    }
    private fun mutateExtra(block:suspend()->Unit)=guarded{block();loadExtra();refresh()}
    fun editRecord(id:Long,t:Instant,d:Double)=guarded{reminders.mutate{repo.editRecord(id,t,d)};loadExtra();refresh();loadConcentration()}
    fun deleteRecord(id:Long)=guarded{reminders.mutate{repo.deleteRecord(id)};loadExtra();refresh();loadConcentration()}
    fun addContainers(med:Long,capacity:Double,count:Int,open:Boolean)=mutateExtra{repo.addContainers(med,capacity,count,open)}
    fun replaceContainer(med:Long,capacity:Double)=mutateExtra{repo.replaceContainer(med,capacity)}
    fun setRemaining(container:Long,remaining:Double)=mutateExtra{repo.setRemaining(container,remaining)}
    fun setScore(date:LocalDate,item:Long,value:Int?)=mutateExtra{repo.setScore(date,item,value)}
    fun setNote(date:LocalDate,text:String)=mutateExtra{repo.setNote(date,text)}
    fun saveCheckinItem(v:CheckinItemEntity)=mutateExtra{repo.saveCheckinItem(v)}
    // Data: Trans Memo import, encrypted backup, exports, wipe
    sealed interface DataJob { object Idle:DataJob; object Working:DataJob; class Done(val message:Int,val arg:String?=null):DataJob; class Failed(val message:Int):DataJob
        class ImportReady(val export:net.plainnotes.app.importer.TmExport,val preview:net.plainnotes.app.importer.TransMemo.Preview):DataJob
        class Imported(val summary:ImportSummary):DataJob }
    val dataJob=MutableStateFlow<DataJob>(DataJob.Idle)
    fun clearDataJob(){dataJob.value=DataJob.Idle}
    private fun dataOp(block:suspend()->DataJob)=viewModelScope.launch{dataJob.value=DataJob.Working;dataJob.value=try{block()}catch(e:BackupCodec.WrongPassword){DataJob.Failed(R.string.backup_wrong_password)}
        catch(e:BackupCodec.BadFile){DataJob.Failed(R.string.backup_bad_file)}catch(e:net.plainnotes.app.importer.InvalidExport){DataJob.Failed(R.string.import_invalid)}catch(_:Exception){DataJob.Failed(R.string.operation_error)}}
    private fun tempImport()=java.io.File(app.noBackupFilesDir,"import").apply{mkdirs()}.resolve("transmemo.db")
    fun openTransMemo(uri:android.net.Uri)=dataOp {
        withContext(Dispatchers.IO) {
            val f=tempImport();app.contentResolver.openInputStream(uri)!!.use{i->f.outputStream().use{i.copyTo(it)}}
            val export=AndroidSqlSource.open(f).use{net.plainnotes.app.importer.TransMemo.read(it)}
            DataJob.ImportReady(export,net.plainnotes.app.importer.TransMemo.preview(export,ZoneId.systemDefault()))
        }
    }
    fun runImport(export:net.plainnotes.app.importer.TmExport,choices:net.plainnotes.app.importer.TransMemo.Choices,overwrite:Boolean)=dataOp {
        val plan=withContext(Dispatchers.Default){net.plainnotes.app.importer.TransMemo.plan(export,ZoneId.systemDefault(),choices)}
        val summary=reminders.mutate{repo.importTransMemo(plan,overwrite)}
        withContext(Dispatchers.IO){tempImport().delete()}
        refresh();loadExtra();loadConcentration();DataJob.Imported(summary)
    }
    fun cancelImport()=viewModelScope.launch{withContext(Dispatchers.IO){tempImport().delete()};dataJob.value=DataJob.Idle}
    fun exportBackup(uri:android.net.Uri,password:CharArray)=dataOp {
        val bytes=repo.exportBackup(password);password.fill(' ')
        withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri,"wt")!!.use{it.write(bytes)}};DataJob.Done(R.string.backup_saved)
    }
    fun restoreBackup(uri:android.net.Uri,password:CharArray)=dataOp {
        val bytes=withContext(Dispatchers.IO){app.contentResolver.openInputStream(uri)!!.use{it.readBytes()}}
        reminders.mutate{repo.restoreBackup(bytes,password)};password.fill(' ')
        refresh();loadExtra();loadConcentration();DataJob.Done(R.string.backup_restored)
    }
    private suspend fun exportData(labels:(CheckinItemEntity)->String,schedules:Map<Long,String>):net.plainnotes.app.export.ExportData {
        val meds=repo.medications();val today=LocalDate.now()
        return net.plainnotes.app.export.ExportData(meds,meds.mapNotNull{m->repo.profile(m.id)?.let{m.id to it}}.toMap(),repo.records(),repo.labs(),repo.checkinItems(),
            repo.scores(today.minusYears(50),today),repo.notes(today.minusYears(50),today),schedules,labels)
    }
    fun exportCsv(uri:android.net.Uri,labels:(CheckinItemEntity)->String,schedules:Map<Long,String>)=dataOp {
        val d=exportData(labels,schedules);withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri,"wt")!!.use{net.plainnotes.app.export.CsvExport.write(d,it)}};DataJob.Done(R.string.export_saved)
    }
    fun exportPdf(uri:android.net.Uri,days:Int,includeChart:Boolean,context:android.content.Context,labels:(CheckinItemEntity)->String,schedules:Map<Long,String>)=dataOp {
        val d=exportData(labels,schedules);val c=if(includeChart)conc.value.result else null
        withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri,"wt")!!.use{net.plainnotes.app.export.PdfReport.write(context,d,days,c,it)}};DataJob.Done(R.string.export_saved)
    }
    /** Deletes everything: database, key, reminders cache and preferences. The caller restarts the UI. */
    fun wipeAll(onDone:()->Unit)=viewModelScope.launch {
        withContext(Dispatchers.IO){ reminders.mutate{repo.destroyAll()}
            app.getSharedPreferences("prefs",android.content.Context.MODE_PRIVATE).edit().clear().commit()
            java.io.File(app.createDeviceProtectedStorageContext().filesDir,"reminders.cache").delete()
            app.getSystemService(android.app.NotificationManager::class.java).cancelAll()
            runCatching{reminders.sync()} }
        onDone()
    }
    fun setWeight(kg:Double)=viewModelScope.launch{try{repo.setWeight(kg);loadConcentration()}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)}}
    fun saveLab(v:LabValueEntity)=viewModelScope.launch{try{repo.saveLab(v);loadConcentration()}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)}}
    fun deleteLab(v:LabValueEntity)=viewModelScope.launch{try{repo.deleteLab(v.id);loadConcentration()}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)}}
    fun delete(id:Long)=change{repo.removeMedication(id)}
    fun complete(s:Slot,t:Instant,d:Double,site:String?=null)=change{repo.complete(s,t,d,site)}.also{it.invokeOnCompletion{loadConcentration();loadExtra()}}
    fun manual(id:Long,t:Instant,d:Double,site:String?=null)=change{repo.unscheduled(id,t,d,site)}.also{it.invokeOnCompletion{loadConcentration();loadExtra()}}
    fun loadOverride(key:String)=viewModelScope.launch{override.value=repo.currentOverride(key)}
    fun changeOverride(s:Slot,o:SlotOverride)=change{repo.override(s,o);override.value=null}
    fun appointment(v:AppointmentEntity)=change{repo.appointment(v)}
    fun testReminder()=viewModelScope.launch{try{reminders.testReminder()}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)}}
    fun sync()=viewModelScope.launch{runCatching{reminders.sync()};refresh()}
}

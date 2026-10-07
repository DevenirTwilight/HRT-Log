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
data class ScheduleSummary(val kind:RuleKind,val interval:Int,val weekdays:Set<DayOfWeek>,val times:List<LocalTime>,val dose:Double?=null,val timeDoses:List<Double?> = emptyList())
data class NotesState(val medications:List<MedicationEntity> = emptyList(),val slots:List<TimelineEntry> = emptyList(),val appointments:List<AppointmentEntity> = emptyList(),val error:Int?=null,val loading:Boolean=true,
                      val schedules:Map<Long,ScheduleSummary> = emptyMap(),val profiles:Map<Long,ProfileEntity> = emptyMap(),val calendarStart:LocalDate=LocalDate.now(),val ruleSnapshots:Map<Long,String> = emptyMap())
data class EditMedication(val medication:MedicationEntity?,val profile:ProfileEntity?,val rule:RuleEntity?,val times:List<TimeEntity>)
@HiltViewModel class NotesViewModel @Inject constructor(repository:NotesRepository,private val reminders:ReminderCoordinator,
    @dagger.hilt.android.qualifiers.ApplicationContext private val app:android.content.Context):ViewModel() {
    // One activity owns one data space; an old activity must never follow a shell switch.
    private val repo=repository.pinnedTo(Space.PRIMARY)
    private var refreshJob:Job?=null
    private var concJob:Job?=null
    private var readFailureShown=false
    private suspend fun <T> mutate(block:suspend()->T):T = reminders.mutate(block)
    private fun readFailure(cause:Exception) { if(!readFailureShown){readFailureShown=true;mutable.value=mutable.value.copy(error=if(cause is KeyRecoveryRequired || cause is DataLockedException) R.string.data_error else R.string.operation_error,loading=false)} }
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
    init { refresh();viewModelScope.launch{while(isActive){delay(30000);if(!readFailureShown)refresh()}} }
    /** All pages read the same committed snapshot; a newer refresh cancels older work. */
    fun refresh():Job {
        refreshJob?.cancel();concJob?.cancel()
        return viewModelScope.launch { try {
            val today=LocalDate.now();val now=Instant.now();val start=calendarStart
            val snapshot=repo.transaction { dao ->
                val slots=repo.calendar(now,displayFrom=start)
                val meds=dao.medications()
                val schedules=dao.rules().filter{it.effective_until_utc==null}.associate { r ->
                    val times=dao.times(r.id).sortedBy{it.local_time}
                    r.medication_id to ScheduleSummary(RuleKind.valueOf(r.kind),r.interval,DayOfWeek.entries.filter{r.weekday_mask and (1 shl (it.value-1))!=0}.toSet(),
                        times.map{LocalTime.parse(it.local_time)},r.dose_snapshot,times.map{it.dose_override})
                }
                val profiles=meds.mapNotNull{m->dao.profile(m.id)?.let{m.id to it}}.toMap()
                val upcoming=(repo.planned(now,now.plus(Duration.ofDays(366)),now)+slots.filter{it.slot.at<now}).distinctBy{it.slot.key}.filter{it.state in net.plainnotes.app.ui.OPEN_STATES}
                NotesState(meds,slots,dao.appointments(),mutable.value.error,false,schedules,profiles,start,dao.rules().associate{it.id to it.config_snapshot}) to
                    ExtraState(dao.records(),dao.containers(),repo.checkinItems(),dao.scores("0001-01-01",today.toString()),dao.notes("0001-01-01",today.toString()),upcoming,dao.stageReviews(),dao.symptomChecks("0001-01-01",today.toString()),dao.reviewEffects())
            }
            ensureActive()
            mutable.value=snapshot.first.copy(error=mutable.value.error);extra.value=snapshot.second;readFailureShown=false
            loadConcentration()
        }catch(e:CancellationException){throw e}catch(e:Exception){readFailure(e)} }.also{refreshJob=it}
    }
    fun clearError(){mutable.value=mutable.value.copy(error=null)}
    private fun change(block:suspend()->Unit)=guarded{mutate(block);refresh().join()}
    fun edit(m:MedicationEntity?)=viewModelScope.launch {try {
        val r=m?.let{repo.rules().lastOrNull{r->r.medication_id==it.id&&r.effective_until_utc==null}}
        val t=if(r==null)emptyList()else repo.transaction{it.times(r.id)}
        editor.value=EditMedication(m,m?.let{repo.profile(it.id)},r,t)
    }catch(e:CancellationException){throw e}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)} }
    fun closeEditor(){editor.value=null}
    fun save(d:net.plainnotes.app.ui.MedicationDraft)=change {repo.saveMedication(d.medication,d.ester,d.kind,d.interval,d.times,d.weekdays,pk=d.pk,resizeContainers=d.resizeContainers);editor.value=null}
    fun editById(id:Long){state.value.medications.firstOrNull{it.id==id}?.let{edit(it)}}

    // Concentration (PK) page
    data class ConcState(val loading:Boolean=false,val result:ConcentrationResult?=null,val weight:Double?=null,val labs:List<LabValueEntity> = emptyList(),val doseTimes:List<Instant> = emptyList())
    val conc=MutableStateFlow(ConcState())
    private var concSettings=Pair(true,CalibrationMode.RETROSPECTIVE)
    fun concentrationSettings(calibrate:Boolean,mode:CalibrationMode){if(concSettings!=calibrate to mode){concSettings=calibrate to mode;loadConcentration()}}
    fun loadConcentration():Job { concJob?.cancel(); return viewModelScope.launch { try {
        conc.value=conc.value.copy(loading=true)
        val now=Instant.now()
        data class Inputs(val meds:List<MedicationEntity>,val profiles:Map<Long,ProfileEntity>,val records:List<RecordEntity>,val labs:List<LabValueEntity>,val weight:Double?,val planned:List<TimelineEntry>,val snapshots:Map<Long,String>)
        val inputs=repo.transaction { dao ->
            val meds=dao.medications()
            Inputs(meds,meds.mapNotNull{m->dao.profile(m.id)?.let{m.id to it}}.toMap(),dao.records(),dao.labs(),dao.pkSettings()?.current_weight_kg,
                repo.planned(now.minusSeconds(3600),now.plusSeconds(ConcentrationCalculator.FORECAST_DAYS*86400),now),dao.rules().associate{it.id to it.config_snapshot})
        }
        val (meds,profiles,records,labs,weight,planned,snapshots)=inputs
        val settings=concSettings
        val result=withContext(Dispatchers.Default){ConcentrationCalculator.compute(meds,profiles,records,planned,labs,weight,now,settings.first,settings.second,snapshots)}
        val doseTimes=records.filter{MedicationSnapshot.decode(it.config_snapshot,it.medication_id)?.molecule=="E2" && it.deleted_at_utc==null && it.status in listOf("ON_TIME","LATE") && it.taken_utc!=null}.map{Instant.ofEpochMilli(it.taken_utc!!)}.sorted()
        ensureActive();conc.value=ConcState(false,result,weight,labs,doseTimes)
    }catch(e:CancellationException){throw e}catch(e:Exception){conc.value=conc.value.copy(loading=false);readFailure(e)} }.also{concJob=it} }
    // History, stock and well-being
    data class ExtraState(val records:List<RecordEntity> = emptyList(),val containers:List<ContainerEntity> = emptyList(),val items:List<CheckinItemEntity> = emptyList(),
                          val scores:List<CheckinScoreEntity> = emptyList(),val notes:List<DayNoteEntity> = emptyList(),
                          /** Open doses for the next year (calendar colours and the stock forecast). */ val upcoming:List<TimelineEntry> = emptyList(),
                          val reviews:List<StageReviewEntity> = emptyList(),val symptoms:List<SymptomCheckEntity> = emptyList(),val effects:List<ReviewEffectEntity> = emptyList())
    val extra=MutableStateFlow(ExtraState())
    private fun guarded(block:suspend()->Unit)=viewModelScope.launch{try{block()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)}}
    fun loadExtra():Job=refresh()
    private fun mutateExtra(block:suspend()->Unit)=change(block)
    data class ImportedLink(val record:RecordEntity,val candidates:List<TimelineEntry>)
    val importedLink=MutableStateFlow<ImportedLink?>(null)
    private var importedLinkJob:Job?=null
    fun prepareImportedLink(record:RecordEntity) {
        importedLinkJob?.cancel()
        importedLinkJob=guarded{val candidates=repo.importedCandidates(record.id);importedLink.value=ImportedLink(record,candidates)}
    }
    fun closeImportedLink(){importedLinkJob?.cancel();importedLink.value=null}
    fun linkImported(id:Long,key:String)=change{repo.linkImported(id,key);importedLink.value=null}
    fun editRecord(id:Long,t:Instant,d:Double)=change{repo.editRecord(id,t,d)}
    fun deleteRecord(id:Long)=change{repo.deleteRecord(id)}
    fun addContainers(med:Long,capacity:Double,count:Int,open:Boolean,source:String?=null,batch:String?=null)=mutateExtra{repo.addContainers(med,capacity,count,open,source,batch)}
    fun replaceContainer(med:Long,capacity:Double)=mutateExtra{repo.replaceContainer(med,capacity)}
    fun setRemaining(container:Long,remaining:Double)=mutateExtra{repo.setRemaining(container,remaining)}
    fun setScore(date:LocalDate,item:Long,value:Int?)=mutateExtra{repo.setScore(date,item,value)}
    fun setNote(date:LocalDate,text:String)=mutateExtra{repo.setNote(date,text)}
    fun saveCheckinItem(v:CheckinItemEntity)=mutateExtra{repo.saveCheckinItem(v)}
    fun saveReview(v:StageReviewEntity)=mutateExtra{repo.saveStageReview(v)}
    fun deleteReview(id:Long)=mutateExtra{repo.deleteStageReview(id)}
    fun confirmMissed(id:Long)=change{repo.confirmMissed(id)}
    fun setSymptom(date:LocalDate,group:String,checked:Boolean,note:String?=null)=mutateExtra{repo.setSymptomCheck(date,group,checked,note) { meds, profiles ->
        net.plainnotes.app.symptoms.SymptomCatalog.load().snapshot(group,meds,profiles)
    }}
    fun setReviewEffect(id:String,enabled:Boolean)=mutateExtra{repo.setReviewEffect(id,enabled)}
    fun setContainerInfo(id:Long,source:String?,batch:String?)=mutateExtra{repo.setContainerInfo(id,source,batch)}
    fun reorderItems(ids:List<Long>)=mutateExtra{repo.reorderCheckinItems(ids)}
    // Data: Trans Memo import, encrypted backup, exports, wipe
    sealed interface DataJob { object Idle:DataJob; object Working:DataJob; class Done(val message:Int,val arg:String?=null):DataJob; class Failed(val message:Int):DataJob
        class ImportReady(val export:net.plainnotes.app.importer.TmExport,val preview:net.plainnotes.app.importer.TransMemo.Preview):DataJob
        class Imported(val summary:ImportSummary):DataJob
        class HtReady(val export:net.plainnotes.app.importer.HrtTracker.Export,val preview:net.plainnotes.app.importer.HrtTracker.Preview):DataJob
        class HtImported(val summary:net.plainnotes.app.data.HtImportSummary):DataJob }
    val dataJob=MutableStateFlow<DataJob>(DataJob.Idle)
    fun clearDataJob(){dataJob.value=DataJob.Idle}
    private fun dataOp(block:suspend()->DataJob)=viewModelScope.launch{dataJob.value=DataJob.Working;dataJob.value=try{block()}catch(e:BackupCodec.WrongPassword){DataJob.Failed(R.string.backup_wrong_password)}
        catch(e:BackupCodec.TooLarge){DataJob.Failed(R.string.backup_too_large)}
        catch(e:BackupCodec.NewerBackup){DataJob.Failed(R.string.backup_newer_version)}
        catch(e:BackupCodec.BadFile){DataJob.Failed(R.string.backup_bad_file)}catch(e:net.plainnotes.app.importer.InvalidExport){DataJob.Failed(R.string.import_invalid)}catch(e:net.plainnotes.app.importer.HrtTracker.InvalidExport){DataJob.Failed(R.string.ht_invalid)}catch(e:CancellationException){throw e}catch(_:Exception){DataJob.Failed(R.string.operation_error)}}
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
        val summary=mutate{repo.importTransMemo(plan,overwrite)}
        withContext(Dispatchers.IO){tempImport().delete()}
        refresh().join();DataJob.Imported(summary)
    }
    fun openHrtTracker(uri:android.net.Uri)=dataOp {
        val limit=32*1024*1024
        val text=withContext(Dispatchers.IO){app.contentResolver.openInputStream(uri)!!.use{ i ->
            val out=java.io.ByteArrayOutputStream();val buf=ByteArray(64*1024)
            while(true){val n=i.read(buf);if(n<0)break;out.write(buf,0,n);if(out.size()>limit)throw net.plainnotes.app.importer.HrtTracker.InvalidExport("too large")}
            out.toString("UTF-8") }}
        val export=withContext(Dispatchers.Default){net.plainnotes.app.importer.HrtTracker.read(text)}
        DataJob.HtReady(export,net.plainnotes.app.importer.HrtTracker.preview(export))
    }
    fun runHtImport(export:net.plainnotes.app.importer.HrtTracker.Export,duplicates:net.plainnotes.app.importer.HrtTracker.Duplicates?,
                    targets:Map<net.plainnotes.app.importer.HrtTracker.Group,Long?>,names:Map<net.plainnotes.app.importer.HrtTracker.Group,String>,weight:Boolean)=dataOp {
        val plan=withContext(Dispatchers.Default){net.plainnotes.app.importer.HrtTracker.plan(export,duplicates)}
        val summary=mutate{repo.importHrtTracker(plan,targets,names,if(weight)export.weightKg else null)}
        refresh().join();DataJob.HtImported(summary)
    }
    fun cancelImport()=viewModelScope.launch{withContext(Dispatchers.IO){tempImport().delete()};dataJob.value=DataJob.Idle}
    fun exportBackup(uri:android.net.Uri,password:CharArray)=dataOp {
        val bytes=try{repo.exportBackup(password)}finally{password.fill(' ')}
        withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri,"wt")!!.use{it.write(bytes)}};DataJob.Done(R.string.backup_saved)
    }
    /** Backup used as a gate (disguise mode): true only once the encrypted file is fully written. */
    suspend fun backupTo(uri:android.net.Uri,password:CharArray):Boolean=withContext(Dispatchers.IO){
        runCatching{ val bytes=repo.exportBackup(password); app.contentResolver.openOutputStream(uri,"wt")!!.use{it.write(bytes)} }.isSuccess.also{password.fill(' ')}
    }
    fun restoreBackup(uri:android.net.Uri,password:CharArray)=dataOp {
        try {
            val bytes=withContext(Dispatchers.IO){app.contentResolver.openInputStream(uri)!!.use{BackupLimits.read(it)}}
            mutate{repo.restoreBackup(bytes,password)}
        }finally{password.fill(' ')}
        refresh().join();DataJob.Done(R.string.backup_restored)
    }
    private suspend fun exportData(labels:(CheckinItemEntity)->String,schedules:Map<Long,String>):net.plainnotes.app.export.ExportData {
        val meds=repo.medications();val today=LocalDate.now()
        return net.plainnotes.app.export.ExportData(meds,meds.mapNotNull{m->repo.profile(m.id)?.let{m.id to it}}.toMap(),repo.records(),repo.labs(),repo.checkinItems(),
            repo.scores(LocalDate.of(1,1,1),today),repo.notes(LocalDate.of(1,1,1),today),schedules,labels,repo.containers(),repo.symptomChecks(LocalDate.of(1,1,1),today),repo.stageReviews())
    }
    fun exportCsv(uri:android.net.Uri,labels:(CheckinItemEntity)->String,schedules:Map<Long,String>)=dataOp {
        val d=repo.transaction{exportData(labels,schedules)};withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri,"wt")!!.use{net.plainnotes.app.export.CsvExport.write(d,it)}};DataJob.Done(R.string.export_saved)
    }
    fun exportSummary(uri:android.net.Uri,from:LocalDate,to:LocalDate,context:android.content.Context,labels:(CheckinItemEntity)->String,schedules:Map<Long,String>)=dataOp {
        val d=repo.transaction{exportData(labels,schedules)}
        withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri,"wt")!!.use{net.plainnotes.app.export.PdfReport.write(context,d,1,null,it,from to to)}}
        DataJob.Done(R.string.export_saved)
    }
    fun exportPdf(uri:android.net.Uri,days:Int,includeChart:Boolean,context:android.content.Context,labels:(CheckinItemEntity)->String,schedules:Map<Long,String>)=dataOp {
        val d=repo.transaction{exportData(labels,schedules)};val c=if(includeChart)conc.value.result else null
        withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri,"wt")!!.use{net.plainnotes.app.export.PdfReport.write(context,d,days,c,it)}};DataJob.Done(R.string.export_saved)
    }
    /** Deletes everything: database, key, reminders cache and preferences. The caller restarts the UI. */
    /** Legacy empty HRT database is retained across upgrade and erased only after explicit confirmation. */
    suspend fun destroyLegacyPrivateData()=withContext(Dispatchers.IO){repo.destroy(Space.DECOY)}
    fun wipeAll(onDone:()->Unit)=viewModelScope.launch {
        withContext(Dispatchers.IO){ mutate{repo.destroyAll()}
            net.plainnotes.app.disguise.Disguise.disable(app); repo.destroy(net.plainnotes.app.data.Space.DECOY)
            app.getSharedPreferences("prefs",android.content.Context.MODE_PRIVATE).edit().clear().commit()
            app.getSharedPreferences("shell_notes",android.content.Context.MODE_PRIVATE).edit().clear().commit()
            net.plainnotes.app.reminder.NotificationPrefs(app).clear(); net.plainnotes.app.security.AppLock(app).disable()
            java.io.File(app.createDeviceProtectedStorageContext().filesDir,"reminders.cache").delete()
            app.getSystemService(android.app.NotificationManager::class.java).cancelAll()
            runCatching{reminders.sync()} }
        onDone()
    }
    fun setWeight(kg:Double)=change{repo.setWeight(kg)}
    fun saveLab(v:LabValueEntity)=change{repo.saveLab(v)}
    fun deleteLab(v:LabValueEntity)=change{repo.deleteLab(v.id)}
    fun delete(id:Long)=change{repo.removeMedication(id)}
    fun complete(s:Slot,t:Instant,d:Double,site:String?=null)=change{repo.complete(s,t,d,site)}
    fun backfill(id:Long,from:LocalDate,to:LocalDate,times:List<java.time.LocalTime>,d:Double,onDone:(Int)->Unit)=viewModelScope.launch {
        try { val n=mutate{repo.backfill(id,from,to,times,d)}; refresh().join(); onDone(n) }
        catch(e:CancellationException){throw e}catch(_:Exception){ mutable.value=mutable.value.copy(error=R.string.operation_error) }
    }
    fun manual(id:Long,t:Instant,d:Double,site:String?=null)=change{repo.unscheduled(id,t,d,site)}
    fun loadOverride(key:String)=viewModelScope.launch{override.value=repo.currentOverride(key)}
    fun changeOverride(s:Slot,o:SlotOverride)=change{repo.override(s,o);override.value=null}
    fun appointment(v:AppointmentEntity)=change{repo.appointment(v)}
    fun testReminder()=viewModelScope.launch{try{reminders.testReminder()}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)}}
    fun sync()=viewModelScope.launch{runCatching{reminders.sync()};refresh().join()}
}

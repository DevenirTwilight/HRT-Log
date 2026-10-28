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
/** [recognized]: schedule recognised from imported history, offered on the import review sheet (REQUIREMENTS §37a). */
data class EditMedication(val medication:MedicationEntity?,val profile:ProfileEntity?,val rule:RuleEntity?,val times:List<TimeEntity>,
                          val recognized:net.plainnotes.app.timeline.RecognizedSchedule?=null)
@HiltViewModel class NotesViewModel @Inject constructor(repository:NotesRepository,private val reminders:ReminderCoordinator,
    @dagger.hilt.android.qualifiers.ApplicationContext private val app:android.content.Context,
    private val measuredSaves:MeasuredSaveCoordinator=MeasuredSaveCoordinator(repository),
    private val savedState:androidx.lifecycle.SavedStateHandle=androidx.lifecycle.SavedStateHandle()):ViewModel() {
    // One activity owns one data space; an old activity must never follow a shell switch.
    private val bootstrapRepository=repository.pinnedTo(Space.PRIMARY)
    private val runtime=net.plainnotes.app.runtime.RuntimeLibrarySession(bootstrapRepository,savedState)
    private val repo get()=runtime.currentRepository()
    internal val libraryRuntime get()=runtime
    internal val libraryUiState get()=savedState
    fun pendingLibraryOperation():String?=savedState.get<String>(net.plainnotes.app.runtime.RuntimeLibrarySession.RESTORE_OPERATION)
        ?.takeIf {runCatching {java.util.UUID.fromString(it).toString()==it}.getOrDefault(false)}
    fun sensitivePresentationHidden()=runtime.hideSensitivePresentation()
    val librarySession=runtime.state
    private var referenceController:net.plainnotes.app.reference.MedicationReferenceController?=null
    val medicationReferences get()=requireNotNull(referenceController)
    private val referenceChangeCounter=MutableStateFlow(0)
    /** Mirrors the reference controller's saved-change counter; usable before the runtime has created the controller. */
    val referenceChanges=referenceChangeCounter.asStateFlow()
    private var runtimeVisible=true
    private var runtimePause:Job?=null
    fun runtimeStopped(graceMillis:Long?) {
        runtimeVisible=false;refreshJob?.cancel();concJob?.cancel();runtimePause?.cancel()
        if(graceMillis!=null)runtimePause=viewModelScope.launch {if(graceMillis>0)delay(graceMillis);runtime.pause()}
    }
    fun runtimeResumed() {runtimeVisible=true;runtimePause?.cancel();viewModelScope.launch {try {runtime.resume();refresh()}catch(e:CancellationException){throw e}catch(e:Exception){runtimeFailure(e)}}}
    private fun runtimeFailure(e:Exception) {
        if(e is LibraryScopeMismatch || e is KeyRecoveryRequired || e is DataLockedException) {
            if(librarySession.value !is net.plainnotes.app.runtime.RuntimeLibrarySession.State.Unavailable)runtime.invalidate(if(e is LibraryScopeMismatch)R.string.library_scope_changed else R.string.data_error);editor.value=null;override.value=null;importedLink.value=null;notificationSlot.value=null
            referenceController?.close();mutable.value=NotesState(error=R.string.library_scope_changed,loading=false);extra.value=ExtraState()
        }
    }
    override fun onCleared() {runtime.close();super.onCleared()}
    @OptIn(ExperimentalCoroutinesApi::class)
    val measuredWriteProgress=measuredSaves.latestTask.flatMapLatest { task ->
        task?.progress ?: flowOf(null)
    }.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    fun checkMeasuredSave():Boolean=measuredSaves.latestTask.value?.let{measuredSaves.reconcile(it)} ?: false
    private var refreshJob:Job?=null
    private var refreshRevision=0L
    private var concJob:Job?=null
    private var readFailureShown=false
    private suspend fun <T> mutate(block:suspend()->T):T {
        runtime.repository()
        return reminders.mutate((librarySession.value as? net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready)?.scope ?: throw LibraryScopeMismatch(),permission=runtime::checkRequest,operation=block)
    }
    private fun readFailure(cause:Exception) {runtimeFailure(cause); if(!readFailureShown){readFailureShown=true;mutable.value=mutable.value.copy(error=if(cause is KeyRecoveryRequired || cause is DataLockedException) R.string.data_error else if(cause is LibraryScopeMismatch)R.string.library_scope_changed else if(cause is LibraryRestorePending)R.string.library_restore_pending else R.string.operation_error,loading=false)} }
    val notificationSlot=MutableStateFlow<Slot?>(null)
    fun libraryRequestRejected() {mutable.value=mutable.value.copy(error=R.string.library_scope_unknown)}
    fun notification(id:String,expected:LibraryRequestScope?=null)=viewModelScope.launch {
        runCatching {runtime.repository()
            if(expected==null || (librarySession.value as? net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready)?.scope!=expected) {
                mutable.value=mutable.value.copy(error=R.string.library_scope_unknown);return@runCatching
            }
            val mapping=repo.transaction{it.mappings().firstOrNull{m->m.opaque_id==id}} ?: return@runCatching
            val slot=repo.calendar(displayFrom=LocalDate.now().minusDays(1)).firstOrNull{mapping.identity=="${it.slot.key}:SOON"||mapping.identity=="${it.slot.key}:DUE"||mapping.identity=="${it.slot.key}:LATE"}?.slot
            runtime.verify();runtime.checkRequest();notificationSlot.value=slot
        }.onFailure {e->if(e is CancellationException)throw e;if(e is Exception)readFailure(e)}
    }
    private val mutable=MutableStateFlow(NotesState());val state=mutable.asStateFlow()
    var editor=MutableStateFlow<EditMedication?>(null);private set
    var override=MutableStateFlow<SlotOverride?>(null);private set
    private var calendarStart=LocalDate.now()
    fun calendarFrom(value:LocalDate?){calendarStart=value ?: LocalDate.now();refresh()}
    init {
        viewModelScope.launch {
            try {
                runtime.initialize {referenceController=net.plainnotes.app.reference.MedicationReferenceController(it,viewModelScope).also {controller->viewModelScope.launch {controller.changes.collect {referenceChangeCounter.value=it}}}}
                refresh()
                bootstrapRepository.generationChanges().collect {runtime.verify();if(runtimeVisible && readFailureShown)refresh()}
            } catch(e:CancellationException){throw e}catch(e:Exception){readFailure(e)}
        }
        viewModelScope.launch { measuredSaves.latestTask.filterNotNull().collectLatest { task ->
            task.progress.first{it.status!=net.plainnotes.app.domain.write.WriteStatus.PENDING}
            if(task.progress.value.status==net.plainnotes.app.domain.write.WriteStatus.SUCCESS)refresh().join()
        }}
        viewModelScope.launch{while(isActive){delay(30000);if(runtimeVisible && !readFailureShown)refresh()}}
    }
    /** All pages read the same committed snapshot; a newer refresh cancels older work. */
    fun refresh():Job {
        refreshJob?.cancel();concJob?.cancel()
        return viewModelScope.launch { try {
            runtime.repository()
            val today=LocalDate.now();val now=Instant.now();val start=calendarStart
            val snapshot=try { repo.transaction { dao ->
                val slots=repo.calendar(now,displayFrom=start)
                val meds=dao.medications()
                val schedules=dao.rules().filter{it.effective_until_utc==null}.associate { r ->
                    val times=dao.times(r.id).sortedBy{it.local_time}
                    r.medication_id to ScheduleSummary(RuleKind.valueOf(r.kind),r.interval,DayOfWeek.entries.filter{r.weekday_mask and (1 shl (it.value-1))!=0}.toSet(),
                        times.map{LocalTime.parse(it.local_time)},r.dose_snapshot,times.map{it.dose_override})
                }
                val conversion=net.plainnotes.app.timeline.TimelineV2Migration.plan(
                    ExtraState(records=dao.records(),regimens=dao.regimens(),historyPeriods=dao.historyPeriods()),now,
                    dao.rules().associate{it.id to it.config_snapshot})
                conversion?.let{repo.migrateTimeline(it.replace,it.rows,now)}
                val profiles=meds.mapNotNull{m->dao.profile(m.id)?.let{m.id to it}}.toMap()
                val upcoming=(repo.planned(now,now.plus(Duration.ofDays(366)),now)+slots.filter{it.slot.at<now}).distinctBy{it.slot.key}.filter{it.state in net.plainnotes.app.ui.OPEN_STATES}
                val stockForecasts=repo.stockForecasts(now)
                NotesState(meds,slots,dao.appointments(),mutable.value.error,false,schedules,profiles,start,dao.rules().associate{it.id to it.config_snapshot}) to
                    ExtraState(dao.records(),dao.containers(),repo.checkinItems(),dao.scores("0001-01-01",today.toString()),dao.notes("0001-01-01",today.toString()),upcoming,dao.stageReviews(),dao.symptomChecks("0001-01-01",today.toString()),dao.reviewEffects(),dao.regimens(),dao.regimenLinks(),dao.milestones(),dao.labs(),dao.labContexts(),dao.visitQuestions(),dao.visitPacks(),dao.historyPeriods(),dao.annotations(),dao.trash().filter{it.state==Trash.TRASHED},dao.deletedRecords(),stockForecasts,dao.containersWithLedger().toSet(),dao.voidedContainers(),dao.containersWithConsumption().toSet(),referenceEpoch=dao.referencePreviewEpoch(),labPanels=dao.labPanels())
            }}catch(_:MeasuredSaveBusyException) {
                displaySnapshot(repo.readNotesSnapshot(now,ZoneId.systemDefault(),start,today),start)
            }
            ensureActive();runtime.verify();runtime.checkRequest()
            mutable.value=snapshot.first.copy(error=mutable.value.error);extra.value=snapshot.second;refreshRevision++;readFailureShown=false
            loadConcentration()
        }catch(e:CancellationException){throw e}catch(e:Exception){readFailure(e)} }.also{refreshJob=it}
    }
    /** Same display DTOs; the read path has no maintenance or arbitrary DAO write capability. */
    private fun displaySnapshot(s:NotesReadSnapshot,start:LocalDate):Pair<NotesState,ExtraState> {
        val schedules=s.rules.filter{it.effective_until_utc==null}.associate { r ->
            val times=s.times.getValue(r.id)
            r.medication_id to ScheduleSummary(RuleKind.valueOf(r.kind),r.interval,
                DayOfWeek.entries.filter{r.weekday_mask and (1 shl (it.value-1))!=0}.toSet(),
                times.map{LocalTime.parse(it.local_time)},r.dose_snapshot,times.map{it.dose_override})
        }
        return NotesState(s.medications,s.slots,s.appointments,mutable.value.error,false,schedules,s.profiles,start,s.rules.associate{it.id to it.config_snapshot}) to
            ExtraState(s.records,s.containers,s.items,s.scores,s.notes,s.upcoming,s.reviews,s.symptoms,s.effects,s.regimens,s.regimenLinks,
                s.milestones,s.labs,s.labContexts,s.visitQuestions,s.visitPacks,s.historyPeriods,s.annotations,s.trash,s.trashedRecords,
                s.stockForecasts,s.containersWithLedger,s.voidedContainers,s.containersWithConsumption,s.referenceEpoch,s.labPanels)
    }
    suspend fun recordedReferenceHistory(epoch:String,record:Long)=repo.recordedReferenceHistory(epoch,record)
    fun clearError(){mutable.value=mutable.value.copy(error=null)}
    private fun change(block:suspend()->Unit)=guarded{mutate(block);refresh().join()}
    fun edit(m:MedicationEntity?)=viewModelScope.launch {try {runtime.repository()
        val r=m?.let{repo.rules().lastOrNull{r->r.medication_id==it.id&&r.effective_until_utc==null}}
        val t=if(r==null)emptyList()else repo.transaction{it.times(r.id)}
        val recognized=if(m?.needs_review!=null && r==null)net.plainnotes.app.timeline.ScheduleRecognition.of(extra.value.records.filter{it.medication_id==m.id},java.time.ZoneId.systemDefault()) else null
        val profile=m?.let{repo.profile(it.id)}
        runtime.verify();runtime.checkRequest()
        editor.value=EditMedication(m,profile,r,t,recognized)
    }catch(e:CancellationException){throw e}catch(e:Exception){readFailure(e)} }
    fun closeEditor(){editor.value=null}
    fun save(d:net.plainnotes.app.ui.MedicationDraft)=change {repo.saveMedication(d.medication,d.ester,d.kind,d.interval,d.times,d.weekdays,pk=d.pk,resizeContainers=d.resizeContainers,timeDoses=d.timeDoses);editor.value=null}
    fun confirmHistoricalContext(m:MedicationEntity,p:ProfileEntity?,from:LocalDate,to:LocalDate)=change{repo.confirmHistoricalContext(m,p,from,to)}
    fun editById(id:Long){state.value.medications.firstOrNull{it.id==id}?.let{edit(it)}}

    // Concentration (PK) page
    data class ConcState(val loading:Boolean=false,val result:ConcentrationResult?=null,val weight:Double?=null,val labs:List<LabValueEntity> = emptyList(),val doseTimes:List<Instant> = emptyList())
    val conc=MutableStateFlow(ConcState())
    private var concSettings=Pair(true,CalibrationMode.RETROSPECTIVE)
    fun concentrationSettings(calibrate:Boolean,mode:CalibrationMode){if(concSettings!=calibrate to mode){concSettings=calibrate to mode;loadConcentration()}}
    fun loadConcentration():Job { concJob?.cancel(); return viewModelScope.launch { try {runtime.repository()
        conc.value=conc.value.copy(loading=true)
        val now=Instant.now()
        data class Inputs(val meds:List<MedicationEntity>,val profiles:Map<Long,ProfileEntity>,val records:List<RecordEntity>,val labs:List<LabValueEntity>,val weight:Double?,val planned:List<TimelineEntry>,val snapshots:Map<Long,String>)
        val inputs=try { repo.transaction { dao ->
            val meds=dao.medications()
            Inputs(meds,meds.mapNotNull{m->dao.profile(m.id)?.let{m.id to it}}.toMap(),dao.records(),dao.labs(),dao.pkSettings()?.current_weight_kg,
                repo.planned(now.minusSeconds(3600),now.plusSeconds(ConcentrationCalculator.FORECAST_DAYS*86400),now),dao.rules().associate{it.id to it.config_snapshot})
        }}catch(_:MeasuredSaveBusyException) {
            val read=repo.readCalculationInputs(now,now.minusSeconds(3600),now.plusSeconds(ConcentrationCalculator.FORECAST_DAYS*86400),ZoneId.systemDefault())
            Inputs(read.medications,read.profiles,read.records,read.labs,read.weight,read.planned,read.snapshots)
        }
        val (meds,profiles,records,labs,weight,planned,snapshots)=inputs
        val settings=concSettings
        val result=withContext(Dispatchers.Default){ConcentrationCalculator.compute(meds,profiles,records,planned,labs,weight,now,settings.first,settings.second,snapshots,net.plainnotes.app.conc.HistoryRead(true,ConcentrationCalculator.hours(now)),checkCancelled={ensureActive()})}
        val doseTimes=records.filter{MedicationSnapshot.decode(it.config_snapshot,it.medication_id)?.molecule=="E2" && it.deleted_at_utc==null && it.status in listOf("ON_TIME","LATE") && it.taken_utc!=null}.map{Instant.ofEpochMilli(it.taken_utc!!)}.sorted()
        ensureActive();runtime.verify();runtime.checkRequest();conc.value=ConcState(false,result,weight,labs,doseTimes)
    }catch(e:CancellationException){throw e}catch(e:Exception){conc.value=conc.value.copy(loading=false);readFailure(e)} }.also{concJob=it} }
    // History, stock and well-being
    data class ExtraState(val records:List<RecordEntity> = emptyList(),val containers:List<ContainerEntity> = emptyList(),val items:List<CheckinItemEntity> = emptyList(),
                          val scores:List<CheckinScoreEntity> = emptyList(),val notes:List<DayNoteEntity> = emptyList(),
                          /** Open doses for the next year (calendar colours and dose lists). */ val upcoming:List<TimelineEntry> = emptyList(),
                          val reviews:List<StageReviewEntity> = emptyList(),val symptoms:List<SymptomCheckEntity> = emptyList(),val effects:List<ReviewEffectEntity> = emptyList(),
                          val regimens:List<RegimenVersionEntity> = emptyList(),val regimenLinks:List<RegimenRuleLinkEntity> = emptyList(),
                          val milestones:List<MilestoneEntity> = emptyList(),val labs:List<LabValueEntity> = emptyList(),val labContexts:List<LabContextEntity> = emptyList(),
                          val visitQuestions:List<VisitQuestionEntity> = emptyList(),val visitPacks:List<VisitPackEntity> = emptyList(),
                          val historyPeriods:List<HistoryPeriodEntity> = emptyList(),val annotations:List<RecordAnnotationEntity> = emptyList(),
                          /** §39 recycle bin (only items that can still be restored). */ val trash:List<TrashItemEntity> = emptyList(),
                          val trashedRecords:List<RecordEntity> = emptyList(),
                          /** B4: the one stock forecast per active medication (stock page, calendar, same rule as reminders). */
                          val stockForecasts:Map<Long,StockForecast> = emptyMap(),
                          /** Packages with ledger history; only the others can be undone as added by mistake. */
                          val containersWithLedger:Set<Long> = emptySet(),
                          val voidedContainers:List<ContainerEntity> = emptyList(),
                          val containersWithConsumption:Set<Long> = emptySet(),val referenceEpoch:String?=null,
                          /** LAB-02 lab panels (one blood draw each). */ val labPanels:List<LabPanelEntity> = emptyList())
    val extra=MutableStateFlow(ExtraState())
    private fun guarded(block:suspend()->Unit)=viewModelScope.launch{try{runtime.repository();block()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){runtimeFailure(e);mutable.value=mutable.value.copy(error=when(e){is LibraryScopeMismatch->R.string.library_scope_changed;is LibraryRequestRevoked->R.string.library_request_locked;is LibraryRestorePending->R.string.library_restore_pending;is MedicalVisitConflict->R.string.medical_stale;is PeriodRestoreConflict->R.string.period_restore_overlap;is PackageRestoreConflict->R.string.pkg_restore_conflict;is PackageConsumptionConflict->R.string.pkg_undo_locked;is SkipUndoStale->R.string.history_skip_stale;is SkipUndoUnavailable->R.string.history_skip_unavailable;is SkipUndoUnitConflict->R.string.history_skip_unit_changed;else->R.string.operation_error})}}
    fun loadExtra():Job=refresh()
    private fun mutateExtra(block:suspend()->Unit)=change(block)
    data class ImportedLink(val record:RecordEntity,val candidates:List<TimelineEntry>)
    val importedLink=MutableStateFlow<ImportedLink?>(null)
    private var importedLinkJob:Job?=null
    fun prepareImportedLink(record:RecordEntity) {
        importedLinkJob?.cancel()
        importedLinkJob=guarded{val candidates=repo.importedCandidates(record.id);runtime.verify();runtime.checkRequest();importedLink.value=ImportedLink(record,candidates)}
    }
    fun closeImportedLink(){importedLinkJob?.cancel();importedLink.value=null}
    fun linkImported(id:Long,key:String)=change{repo.linkImported(id,key);importedLink.value=null}
    fun editRecord(id:Long,t:Instant,d:Double)=change{repo.editRecord(id,t,d)}
    fun deleteRecord(id:Long)=change{repo.deleteRecord(id)}
    private val undoingSkips=mutableSetOf<Long>()
    fun undoSkipped(record:RecordEntity)=guarded {
        if(!undoingSkips.add(record.id))return@guarded
        try {
            // Reject a known stale selection before invalidating the reminder cache. The transaction rechecks it.
            if(record !in extra.value.records)throw SkipUndoStale()
            mutate{repo.undoSkipped(record)}
            refresh().join()
        } finally {undoingSkips.remove(record.id)}
    }
    fun addContainers(med:Long,capacity:Double,count:Int,open:Boolean,source:String?=null,batch:String?=null)=mutateExtra{repo.addContainers(med,capacity,count,open,source,batch)}
    fun replaceContainer(med:Long,capacity:Double)=mutateExtra{repo.replaceContainer(med,capacity)}
    fun setRemaining(container:Long,remaining:Double)=mutateExtra{repo.setRemaining(container,remaining)}
    fun correctStock(container:Long,remaining:Double)=mutateExtra{repo.correctStock(container,remaining)}
    fun removeMistakenContainer(container:Long)=mutateExtra{repo.removeMistakenContainer(container)}
    fun restoreVoidedContainer(container:Long)=mutateExtra{repo.restoreVoidedContainer(container)}
    fun setScore(date:LocalDate,item:Long,value:Int?)=mutateExtra{repo.setScore(date,item,value)}
    fun setNote(date:LocalDate,text:String)=mutateExtra{repo.setNote(date,text)}
    fun saveCheckinItem(v:CheckinItemEntity)=mutateExtra{repo.saveCheckinItem(v)}
    fun saveReview(v:StageReviewEntity)=mutateExtra{repo.saveStageReview(v)}
    private val milestoneSaving=net.plainnotes.app.timeline.MilestoneSaving(viewModelScope,{value,committed->
        mutate {
            val saved=repo.saveMilestone(value)
            committed(saved) // transaction has returned: subsequent reminder errors cannot undo it
            extra.update{it.copy(milestones=(it.milestones.filterNot{row->row.id==saved.id}+saved).sortedWith(compareBy({row->row.date},{row->row.id})))}
        }
    },{
        val before=refreshRevision
        refresh().join()
        refreshRevision>before
    })
    val milestoneSave=milestoneSaving.state
    fun saveMilestone(value:MilestoneEntity)=milestoneSaving.save(value)
    fun clearMilestoneSave()=milestoneSaving.clear()
    fun deleteMilestone(id:Long)=mutateExtra{repo.deleteMilestone(id)}
    fun deleteReview(id:Long)=mutateExtra{repo.deleteStageReview(id)}
    fun confirmMissed(id:Long)=change{repo.confirmMissed(id)}
    fun setSymptom(date:LocalDate,group:String,checked:Boolean,note:String?=null)=mutateExtra{repo.setSymptomCheck(date,group,checked,note) { meds, profiles ->
        net.plainnotes.app.symptoms.SymptomCatalog.load().snapshot(group,meds,profiles)
    }}
    fun setReviewEffect(id:String,enabled:Boolean)=mutateExtra{repo.setReviewEffect(id,enabled)}
    fun setContainerInfo(id:Long,source:String?,batch:String?)=mutateExtra{repo.setContainerInfo(id,source,batch)}
    fun reorderItems(ids:List<Long>)=mutateExtra{repo.reorderCheckinItems(ids)}
    // Data: Trans Memo import, encrypted backup, exports, wipe
    sealed interface DataJob { object Idle:DataJob; object Working:DataJob;
        object ImportDetecting:DataJob
        class ImportParsing(val source:net.plainnotes.app.importer.ImportFormatDetector.Source):DataJob
        object ImportCancelled:DataJob; class Done(val message:Int,val arg:String?=null):DataJob; class Failed(val message:Int):DataJob
        class ImportReady(val export:net.plainnotes.app.importer.TmExport,val preview:net.plainnotes.app.importer.TransMemo.Preview):DataJob
        class Imported(val summary:ImportSummary):DataJob
        class HtReady(val export:net.plainnotes.app.importer.HrtTracker.Export,val preview:net.plainnotes.app.importer.HrtTracker.Preview):DataJob
        class HtImported(val summary:net.plainnotes.app.data.HtImportSummary):DataJob
        class MonaReady(val export:net.plainnotes.app.importer.Mona.Export,val preview:MonaImportPreview,val scope:LibraryRequestScope):DataJob
        class MonaImported(val summary:MonaImportSummary,val plan:net.plainnotes.app.importer.MonaMigration.Plan):DataJob
        class ChrysalideReady(val export:net.plainnotes.app.importer.Chrysalide.Export,val preview:ChrysalideImportPreview,val scope:LibraryRequestScope):DataJob
        class ChrysalideImported(val summary:ChrysalideImportSummary,val preview:ChrysalideImportPreview):DataJob
        object FeatherlineWorking:DataJob
        class FeatherlinePassword(val archive:net.plainnotes.app.importer.Featherline.Archive,val scope:LibraryRequestScope,val error:Int?=null):DataJob
        class FeatherlineReady(val preview:FeatherlineImportPreview,val scope:LibraryRequestScope):DataJob
        class FeatherlineImported(val summary:FeatherlineImportSummary,val preview:FeatherlineImportPreview):DataJob }
    val dataJob=MutableStateFlow<DataJob>(DataJob.Idle)
    private var importReadJob:Job?=null
    private var featherlineRegistration:AutoCloseable?=null
    private fun releaseFeatherline(){featherlineRegistration?.close();featherlineRegistration=null}
    fun clearDataJob(){importReadJob?.cancel();importReadJob=null;releaseFeatherline();dataJob.value=DataJob.Idle}
    // Only an actual Repository return may acknowledge a replacing restore. A later scheduling
    // error or deliberate epoch change cannot turn that historical COMMIT into a failed save.
    private class CommittedLibraryChange(val result:DataJob)
    private fun acknowledgeCommittedLibrary(receipt:CommittedLibraryChange) {
        runtimeFailure(LibraryScopeMismatch());runtime.invalidate(R.string.library_change_committed);dataJob.value=receipt.result
    }
    private fun dataOp(committed:()->CommittedLibraryChange?={null},block:suspend()->DataJob)=viewModelScope.launch{dataJob.value=DataJob.Working;val result=try{runtime.repository();block().also {if(committed()==null){runtime.verify();runtime.checkRequest()}}}catch(e:LibraryScopeMismatch){runtimeFailure(e);DataJob.Failed(R.string.library_scope_changed)}catch(e:LibraryRequestRevoked){DataJob.Failed(R.string.library_request_locked)}catch(e:net.plainnotes.app.importer.ImportFormatDetector.Unknown){DataJob.Failed(R.string.import_format_unknown)}
        catch(e:net.plainnotes.app.importer.ImportFormatDetector.Damaged){DataJob.Failed(R.string.import_file_damaged)}
        catch(e:net.plainnotes.app.importer.ImportFormatDetector.TooLarge){DataJob.Failed(R.string.import_file_too_large)}
        catch(e:net.plainnotes.app.importer.ImportFormatDetector.Unsupported){DataJob.Failed(when(e.source) {
            net.plainnotes.app.importer.ImportFormatDetector.Source.FEATHERLINE->R.string.import_featherline_unsupported
            net.plainnotes.app.importer.ImportFormatDetector.Source.HRT_LOG->R.string.import_use_native_restore
            else->R.string.import_version_unsupported
        })}
        catch(e:BackupCodec.WrongPassword){DataJob.Failed(R.string.backup_wrong_password)}
        catch(e:BackupCodec.TooLarge){DataJob.Failed(R.string.backup_too_large)}
        catch(e:BackupCodec.NewerBackup){DataJob.Failed(R.string.backup_newer_version)}
        catch(e:BackupCodec.BadFile){DataJob.Failed(R.string.backup_bad_file)}catch(e:net.plainnotes.app.importer.InvalidExport){DataJob.Failed(R.string.import_invalid)}catch(e:net.plainnotes.app.importer.HrtTracker.InvalidExport){DataJob.Failed(R.string.ht_invalid)}catch(e:CancellationException){committed()?.let(::acknowledgeCommittedLibrary);throw e}catch(_:Exception){DataJob.Failed(R.string.operation_error)}
        val receipt=committed()
        if(receipt!=null)acknowledgeCommittedLibrary(receipt)else{ensureActive();dataJob.value=result}
    }
    private fun importRead(block:suspend()->DataJob):Job {
        val previous=importReadJob
        previous?.cancel()
        return dataOp { previous?.join(); block() }.also{importReadJob=it}
    }
    /** One SAF result, frozen screen identity, one provider open and source-specific preview. */
    fun openImport(uri:android.net.Uri,expected:LibraryRequestScope?=(librarySession.value as? net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready)?.scope)=importRead {
        val current=(librarySession.value as? net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready)?.scope
        if(expected==null || current!=expected)throw LibraryScopeMismatch()
        dataJob.value=DataJob.ImportDetecting
        val parsed=net.plainnotes.app.imports.AutomaticImportReader.read(app.noBackupFilesDir,
            {app.contentResolver.openInputStream(uri)},runtime::checkRequest) {source->dataJob.compareAndSet(DataJob.ImportDetecting,DataJob.ImportParsing(source))}
        withContext(Dispatchers.Default) {when(parsed) {
            is net.plainnotes.app.importer.ImportFormatDetector.Parsed.EncryptedFeatherline->{
                releaseFeatherline();featherlineRegistration=runtime.protect(parsed.archive)
                DataJob.FeatherlinePassword(parsed.archive,expected)
            }
            is net.plainnotes.app.importer.ImportFormatDetector.Parsed.Mona->DataJob.MonaReady(parsed.export,repo.previewMona(parsed.export),expected)
            is net.plainnotes.app.importer.ImportFormatDetector.Parsed.Chrysalide->DataJob.ChrysalideReady(parsed.export,repo.previewChrysalide(parsed.export),expected)
            is net.plainnotes.app.importer.ImportFormatDetector.Parsed.Tracker->DataJob.HtReady(parsed.export,net.plainnotes.app.importer.HrtTracker.preview(parsed.export))
            is net.plainnotes.app.importer.ImportFormatDetector.Parsed.Memo->DataJob.ImportReady(parsed.export,net.plainnotes.app.importer.TransMemo.preview(parsed.export,ZoneId.systemDefault()))
        }}
    }
    fun cancelAutomaticImport() {
        if(dataJob.value==DataJob.FeatherlineWorking)importReadJob?.cancel()
        else{clearDataJob();dataJob.value=DataJob.ImportCancelled}
    }
    fun openTransMemo(uri:android.net.Uri)=importRead {
        val export=net.plainnotes.app.privacy.TransMemoImportCopies.read(app.noBackupFilesDir,
            {app.contentResolver.openInputStream(uri)}) { file ->
            AndroidSqlSource.open(file).use{net.plainnotes.app.importer.TransMemo.read(it)}
        }
        withContext(Dispatchers.Default) {
            DataJob.ImportReady(export,net.plainnotes.app.importer.TransMemo.preview(export,ZoneId.systemDefault()))
        }
    }
    fun runImport(export:net.plainnotes.app.importer.TmExport,choices:net.plainnotes.app.importer.TransMemo.Choices,overwrite:Boolean):Job {
        var committed:CommittedLibraryChange?=null
        return dataOp(committed={committed}) {
        val plan=withContext(Dispatchers.Default){net.plainnotes.app.importer.TransMemo.plan(export,ZoneId.systemDefault(),choices)}
        val summary=mutate{repo.importTransMemo(plan,overwrite).also {if(overwrite)committed=CommittedLibraryChange(DataJob.Imported(it))}}
        if(!overwrite)refresh().join();DataJob.Imported(summary)
        }
    }
    fun openHrtTracker(uri:android.net.Uri)=importRead {
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
    /** A preview is single-use in this owner. The Repository rescans under its transaction. */
    fun runMonaImport(ready:DataJob.MonaReady):Job? {
        if(!dataJob.compareAndSet(ready,DataJob.Working))return null
        val receipt=MonaCommitReceipt()
        return viewModelScope.launch {
            try {
                runtime.repository();runtime.verify();runtime.checkRequest()
                if((librarySession.value as? net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready)?.scope!=ready.scope)throw LibraryScopeMismatch()
                mutate {repo.importMona(ready.export,emptyMap(),emptyMap(),receipt)}
                refresh().join()
            } catch(e:CancellationException) {
                if(receipt.committed==null) {dataJob.value=DataJob.ImportCancelled;throw e}
            } catch(e:LibraryScopeMismatch) {
                runtimeFailure(e)
                if(receipt.committed==null)dataJob.value=DataJob.Failed(R.string.library_scope_changed)
            } catch(_:LibraryRequestRevoked) {
                if(receipt.committed==null)dataJob.value=DataJob.Failed(R.string.library_request_locked)
            } catch(_:Exception) {
                if(receipt.committed==null)dataJob.value=DataJob.Failed(R.string.operation_error)
            } finally {
                receipt.committed?.let {dataJob.value=DataJob.MonaImported(it,ready.preview.plan)}
            }
        }
    }
    fun runChrysalideImport(ready:DataJob.ChrysalideReady,targets:Map<Long,Long>):Job? {
        if(!dataJob.compareAndSet(ready,DataJob.Working))return null
        val receipt=ChrysalideCommitReceipt()
        return viewModelScope.launch {
            try {
                runtime.repository();runtime.verify();runtime.checkRequest()
                if((librarySession.value as? net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready)?.scope!=ready.scope)throw LibraryScopeMismatch()
                mutate {
                    repo.importChrysalide(ready.export,targets,receipt).also {
                        // The receipt is acknowledged only after the repository transaction COMMIT.
                        // Publish that fact before ReminderCoordinator's follow-up rebuild or UI refresh.
                        receipt.committed?.let { committed -> dataJob.value=DataJob.ChrysalideImported(committed,ready.preview) }
                    }
                }
                refresh().join()
            } catch(e:CancellationException) { if(receipt.committed==null){dataJob.value=DataJob.ImportCancelled;throw e} }
            catch(e:LibraryScopeMismatch) {runtimeFailure(e);if(receipt.committed==null)dataJob.value=DataJob.Failed(R.string.library_scope_changed)}
            catch(_:LibraryRequestRevoked) {if(receipt.committed==null)dataJob.value=DataJob.Failed(R.string.library_request_locked)}
            catch(_:Exception) {if(receipt.committed==null)dataJob.value=DataJob.Failed(R.string.operation_error)}
            finally {receipt.committed?.let{dataJob.value=DataJob.ChrysalideImported(it,ready.preview)}}
        }
    }
    fun decryptFeatherline(ready:DataJob.FeatherlinePassword,password:CharArray):Job? {
        if(!dataJob.compareAndSet(ready,DataJob.FeatherlineWorking)){password.fill('\u0000');return null}
        return viewModelScope.launch {
            var plain:ByteArray?=null
            try {
                runtime.repository();runtime.verify();runtime.checkRequest()
                if((librarySession.value as? net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready)?.scope!=ready.scope)throw LibraryScopeMismatch()
                val parent=coroutineContext[Job]
                val export=withContext(Dispatchers.IO) {
                    plain=ready.archive.authenticate(password){parent?.ensureActive();runtime.checkRequest()}
                    try {
                        val text=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(requireNotNull(plain))).toString()
                        net.plainnotes.app.importer.Featherline.read(text)
                    }finally{plain?.fill(0);plain=null}
                }
                runtime.verify();runtime.checkRequest()
                val preview=repo.previewFeatherline(export)
                releaseFeatherline();dataJob.value=DataJob.FeatherlineReady(preview,ready.scope)
            }catch(_:FeatherlineContainer.Authentication){dataJob.value=DataJob.FeatherlinePassword(ready.archive,ready.scope,R.string.featherline_auth_failed)}
            catch(_:net.plainnotes.app.importer.ImportFormatDetector.TooLarge){releaseFeatherline();dataJob.value=DataJob.Failed(R.string.import_file_too_large)}
            catch(_:FeatherlineContainer.TooLarge){releaseFeatherline();dataJob.value=DataJob.Failed(R.string.import_file_too_large)}
            catch(_:net.plainnotes.app.importer.Featherline.Unsupported){releaseFeatherline();dataJob.value=DataJob.Failed(R.string.import_version_unsupported)}
            catch(e:CancellationException){releaseFeatherline();dataJob.value=DataJob.ImportCancelled;throw e}
            catch(e:LibraryScopeMismatch){releaseFeatherline();runtimeFailure(e);dataJob.value=DataJob.Failed(R.string.library_scope_changed)}
            catch(_:LibraryRequestRevoked){releaseFeatherline();dataJob.value=DataJob.Failed(R.string.library_request_locked)}
            catch(_:Exception){releaseFeatherline();dataJob.value=DataJob.Failed(R.string.import_file_damaged)}
            finally{plain?.fill(0);password.fill('\u0000')}
        }.also{importReadJob=it;it.invokeOnCompletion{password.fill('\u0000')}}
    }
    fun runFeatherlineImport(ready:DataJob.FeatherlineReady):Job? {
        if(!dataJob.compareAndSet(ready,DataJob.FeatherlineWorking))return null
        val receipt=FeatherlineCommitReceipt()
        return viewModelScope.launch {
            try {
                runtime.repository();runtime.verify();runtime.checkRequest()
                if((librarySession.value as? net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready)?.scope!=ready.scope)throw LibraryScopeMismatch()
                mutate{repo.importFeatherline(ready.preview.export,receipt)};refresh().join()
            }catch(e:CancellationException){if(receipt.committed==null){dataJob.value=DataJob.ImportCancelled;throw e}}
            catch(e:LibraryScopeMismatch){runtimeFailure(e);if(receipt.committed==null)dataJob.value=DataJob.Failed(R.string.library_scope_changed)}
            catch(_:LibraryRequestRevoked){if(receipt.committed==null)dataJob.value=DataJob.Failed(R.string.library_request_locked)}
            catch(_:Exception){if(receipt.committed==null)dataJob.value=DataJob.Failed(R.string.operation_error)}
            finally{receipt.committed?.let{dataJob.value=DataJob.FeatherlineImported(it,ready.preview)}}
        }.also{importReadJob=it}
    }
    fun cancelImport(){clearDataJob()}
    val backupHistory=net.plainnotes.app.privacy.BackupHistory(app.getSharedPreferences("prefs",android.content.Context.MODE_PRIVATE))
    private val backupWriter=net.plainnotes.app.privacy.BackupWriter(backupHistory)
    fun exportBackup(uri:android.net.Uri,password:CharArray)=dataOp {
        backupWriter.write(password,repo::exportBackup){app.contentResolver.openOutputStream(uri,"wt")}
        DataJob.Done(R.string.backup_saved)
    }.also{job->job.invokeOnCompletion{password.fill('\u0000')}}
    /** Main encrypted export used as a gate; cancellation never becomes a success. */
    suspend fun backupTo(uri:android.net.Uri,password:CharArray):Boolean=try {
        runtime.repository()
        backupWriter.write(password,repo::exportBackup){app.contentResolver.openOutputStream(uri,"wt")};true
    }catch(e:CancellationException){throw e}catch(_:Exception){false}finally{password.fill('\u0000')}
    fun restoreBackup(uri:android.net.Uri,password:CharArray):Job {
        var committed:CommittedLibraryChange?=null
        return dataOp(committed={committed}) {
        try {
            val bytes=withContext(Dispatchers.IO){app.contentResolver.openInputStream(uri)!!.use{BackupLimits.read(it)}}
            mutate{repo.restoreBackup(bytes,password);committed=CommittedLibraryChange(DataJob.Done(R.string.backup_restored))}
        }finally{password.fill('\u0000')}
        requireNotNull(committed).result
        }.also{job->job.invokeOnCompletion{password.fill('\u0000')}}
    }
    private suspend fun exportData(labels:(CheckinItemEntity)->String,schedules:Map<Long,String>):net.plainnotes.app.export.ExportData {
        val meds=repo.medications();val today=LocalDate.now()
        return net.plainnotes.app.export.ExportData(meds,meds.mapNotNull{m->repo.profile(m.id)?.let{m.id to it}}.toMap(),repo.records(),repo.labs(),repo.checkinItems(),
            repo.scores(LocalDate.of(1,1,1),today),repo.notes(LocalDate.of(1,1,1),today),schedules,labels,repo.containers(),repo.symptomChecks(LocalDate.of(1,1,1),today),repo.stageReviews(),repo.labContexts(),
            repo.transaction{it.regimens()},repo.transaction{it.milestones()},repo.appointments())
    }
    fun exportCsv(uri:android.net.Uri,labels:(CheckinItemEntity)->String,schedules:Map<Long,String>)=dataOp {
        val d=repo.transaction{exportData(labels,schedules)};withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri,"wt")!!.use{net.plainnotes.app.export.CsvExport.write(d,it)}};DataJob.Done(R.string.export_saved)
    }
    /** Record labels by confirmed or saved period (REQUIREMENTS §35a); same projection as History. */
    private fun recordLabels():Map<Long,Set<net.plainnotes.app.domain.RecordLabel>> = runCatching{
        val e=extra.value;val s=mutable.value
        net.plainnotes.app.timeline.HistoryLabels.build(e.records,net.plainnotes.app.timeline.PeriodTimelineProjection.build(e,s.appointments,ruleSnapshots=s.ruleSnapshots),e.annotations,java.time.ZoneId.systemDefault())
    }.getOrDefault(emptyMap())
    fun exportSummary(uri:android.net.Uri,from:LocalDate,to:LocalDate,context:android.content.Context,labels:(CheckinItemEntity)->String,schedules:Map<Long,String>)=dataOp {
        val d=repo.transaction{exportData(labels,schedules)}
        withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri,"wt")!!.use{net.plainnotes.app.export.PdfReport.write(context,d,1,null,it,from to to,labels=recordLabels())}}
        DataJob.Done(R.string.export_saved)
    }
    fun exportPdf(uri:android.net.Uri,days:Int,includeChart:Boolean,context:android.content.Context,labels:(CheckinItemEntity)->String,schedules:Map<Long,String>)=dataOp {
        val d=repo.transaction{exportData(labels,schedules)};val c=if(includeChart)conc.value.result else null
        withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri,"wt")!!.use{net.plainnotes.app.export.PdfReport.write(context,d,days,c,it,labels=recordLabels())}};DataJob.Done(R.string.export_saved)
    }
    /** Legacy empty HRT database is retained across upgrade and erased only after explicit confirmation. */
    suspend fun destroyLegacyPrivateData()=withContext(Dispatchers.IO){repo.destroy(Space.DECOY)}
    /** Deletes everything: database, key, reminders cache, stock-alert markers and preferences. The caller restarts the UI.
     * Any store that cannot be cleared reports [DataJob.Failed] instead of success. */
    fun wipeAll(onDone:()->Unit)=viewModelScope.launch {
        dataJob.value=DataJob.Working
        try {
        runtime.repository();runtime.verify();runtime.checkRequest()
        withContext(Dispatchers.IO){
            // Stock-alert markers go first, so an interrupted wipe never leaves them behind a fresh database (ids restart).
            check(net.plainnotes.app.reminder.StockAlerts.clear(app)){"stock alerts not cleared"}
            mutate{repo.destroyAll()}
            net.plainnotes.app.attachments.AttachmentReconciliationStore.clearAll(
                app.getSharedPreferences("attachment-reconciliation-v1",android.content.Context.MODE_PRIVATE))
            net.plainnotes.app.disguise.Disguise.disable(app); repo.destroy(net.plainnotes.app.data.Space.DECOY)
            app.getSharedPreferences("prefs",android.content.Context.MODE_PRIVATE).edit().clear().commit()
            backupHistory.clear()
            app.getSharedPreferences("shell_notes",android.content.Context.MODE_PRIVATE).edit().clear().commit()
            net.plainnotes.app.reminder.NotificationPrefs(app).clear(); net.plainnotes.app.reminder.ReminderTestLog(app).clear(); net.plainnotes.app.security.AppLock(app).disable()
            java.io.File(app.createDeviceProtectedStorageContext().filesDir,"reminders.cache").delete()
            app.getSystemService(android.app.NotificationManager::class.java).cancelAll()
            runCatching{reminders.sync()}
            // Cleared again after the last reminder pass; never report success with markers left over.
            check(net.plainnotes.app.reminder.StockAlerts.clear(app) && net.plainnotes.app.reminder.StockAlerts.isClear(app)){"stock alerts not cleared"} }
        dataJob.value=DataJob.Idle;onDone()
        }catch(e:CancellationException){throw e}catch(_:Exception){dataJob.value=DataJob.Failed(R.string.operation_error)}
    }
    fun setWeight(kg:Double)=change{repo.setWeight(kg)}
    // D-2 (REQUIREMENTS §67 item 5): a new estimate never stores the retired Legacy sublingual E2 value.
    private suspend fun newEstimate(dao:net.plainnotes.app.data.NotesDao,lab:LabValueEntity)=
        net.plainnotes.app.conc.SublingualEstimatePolicy.apply(net.plainnotes.app.conc.LabEstimate.capture(dao,lab))
    fun saveLab(v:LabValueEntity,includeEstimate:Boolean=false)=change{repo.saveLab(v,recapture=includeEstimate,estimate={dao,lab->if(includeEstimate)newEstimate(dao,lab) else null})}
    /** LAB-01 batch entry: [onDone] says whether all were saved (then the form closes) or none (the form keeps its content). */
    fun saveLabs(values:List<LabValueEntity>,includeEstimate:Boolean,onDone:(Boolean)->Unit)=saveLabDraw(values,includeEstimate) {onDone(it!=null)}
    /** The callback acknowledges the exact committed UUID, even when subsequent refresh/reminders fail. */
    fun saveLabDraw(values:List<LabValueEntity>,includeEstimate:Boolean,onDone:(LabPanelEntity?)->Unit)=viewModelScope.launch {
        var committed:LabPanelEntity?=null
        try {
            mutate {repo.saveLabs(values,recapture=includeEstimate,estimate={dao,lab->if(includeEstimate)newEstimate(dao,lab) else null}).also {committed=it}}
            refresh().join();onDone(committed)
        }catch(e:CancellationException) {if(committed!=null)onDone(committed);throw e}
        catch(_:Exception) {if(committed==null)mutable.value=mutable.value.copy(error=R.string.operation_error);onDone(committed)}
    }
    // LAB-02 lab panels (docs/design/lab-panel-adr.md)
    fun saveLabPanel(panel:LabPanelEntity,time:java.time.LocalDateTime,laboratory:String,note:String)=change{
        val sample=net.plainnotes.app.ui.labSampleForEdit(panel.sampled_utc,panel.sampled_zone,time)
        repo.saveLabPanel(panel.id,sample.utc,sample.zone,laboratory,note)
    }
    fun addToLabPanel(id:Long,values:List<LabValueEntity>,includeEstimate:Boolean)=change{repo.addToLabPanel(id,values,recapture=includeEstimate,estimate={dao,lab->if(includeEstimate)newEstimate(dao,lab) else null})}
    fun removeFromLabPanel(v:LabValueEntity)=change{repo.removeFromLabPanel(v.id)}
    fun deleteLabPanel(id:Long)=change{repo.deleteLabPanel(id)}
    fun groupAsLabPanel(labs:List<LabValueEntity>,onDone:(Long)->Unit={})=viewModelScope.launch {
        var committed:Long?=null
        try {mutate {repo.groupAsLabPanel(labs.map {it.id}).also {committed=it}};refresh().join();committed?.let(onDone)}
        catch(e:CancellationException) {committed?.let(onDone);throw e}
        catch(_:Exception) {if(committed!=null)onDone(requireNotNull(committed)) else mutable.value=mutable.value.copy(error=R.string.operation_error)}
    }
    fun rebuildLabContext(v:LabValueEntity,includeEstimate:Boolean=false)=change{repo.rebuildLabContext(v.id,estimate={dao,lab->if(includeEstimate)newEstimate(dao,lab) else null})}
    fun deleteLab(v:LabValueEntity)=change{repo.deleteLab(v.id)}
    fun delete(id:Long)=change{repo.removeMedication(id)}
    fun complete(s:Slot,t:Instant,d:Double,site:String?=null)=change{repo.complete(s,t,d,site)}
    fun backfill(id:Long,from:LocalDate,to:LocalDate,times:List<java.time.LocalTime>,d:Double,onDone:(Int)->Unit)=viewModelScope.launch {
        try { val n=mutate{repo.backfill(id,from,to,times,d)}; refresh().join(); onDone(n) }
        catch(e:CancellationException){throw e}catch(_:Exception){ mutable.value=mutable.value.copy(error=R.string.operation_error) }
    }
    fun manual(id:Long,t:Instant,d:Double,site:String?=null,extra:Boolean=false)=change{repo.unscheduled(id,t,d,site,extra)}
    fun setExtra(recordId:Long,extra:Boolean)=change{repo.setExtra(recordId,extra)}
    /** REQUIREMENTS §37b timeline edits; past periods only, never reminders. */
    fun editTimeline(replace:List<String>,rows:List<TimelineEditRow>)=change{repo.editTimeline(replace,rows)}
    // §39 recycle bin
    fun deletePeriod(replace:List<String>,rows:List<TimelineEditRow>)=change{repo.deletePeriod(replace,rows)}
    fun restoreTrash(id:Long)=change{repo.restoreTrash(id)}
    fun purgeTrash(item:TrashItemEntity)=change{repo.purgeTrash(item.id,systemUnderneath(item))}
    fun emptyTrash()=change{extra.value.trash.forEach{repo.purgeTrash(it.id,systemUnderneath(it))}}
    /** Whether plans the system made lie under a period deletion; then purging hides it for good instead of removing it. */
    private fun systemUnderneath(item:TrashItemEntity):Boolean {
        if(item.kind!=Trash.PERIOD)return true
        val e=extra.value;val rows=e.historyPeriods.filter{it.group_key==item.ref}
        val without=e.copy(historyPeriods=e.historyPeriods.filter{it.group_key!=item.ref})
        val p=net.plainnotes.app.timeline.PeriodTimelineProjection.build(without,mutable.value.appointments,ruleSnapshots=mutable.value.ruleSnapshots).projection
        return rows.any{r->val z=java.time.ZoneId.of(r.zone);val from=LocalDate.parse(r.from_date).atStartOfDay(z).toInstant();val until=r.until_date?.let{LocalDate.parse(it).atStartOfDay(z).toInstant()} ?: Instant.MAX
            p.raw.any{it.span.medicationId==r.medication_id && it.kind!=net.plainnotes.app.domain.SpanKind.USER && it.kind!=net.plainnotes.app.domain.SpanKind.FILL &&
                it.span.from<until && (it.span.until ?: Instant.MAX)>from}}
    }
    fun loadOverride(key:String)=guarded{val value=repo.currentOverride(key);runtime.verify();runtime.checkRequest();override.value=value}
    fun changeOverride(s:Slot,o:SlotOverride)=change{repo.override(s,o);override.value=null}
    private val medicalSaving=net.plainnotes.app.medical.MedicalVisitSaving(viewModelScope,{draft,committed->
        mutate {
            val saved=repo.pinnedTo(draft.space).saveHistoricalVisit(draft.value,draft.expected)
            committed(saved)
            mutable.update{it.copy(appointments=(it.appointments.filterNot{row->row.id==saved.id}+saved).sortedBy{row->row.at_utc})}
        }
    },{val before=refreshRevision;refresh().join();refreshRevision>before})
    val medicalVisitSave=medicalSaving.state
    fun saveMedicalVisit(value:AppointmentEntity,expected:AppointmentEntity?=null)=medicalSaving.save(net.plainnotes.app.medical.MedicalVisitDraft(value,expected,repo.space))
    fun clearMedicalVisitSave()=medicalSaving.clear()
    fun retractMedicalVisit(value:AppointmentEntity)=change{repo.retractHistoricalVisit(value)}
    fun deleteMedicalVisit(value:AppointmentEntity)=change{repo.deleteHistoricalVisit(value)}
    fun appointment(v:AppointmentEntity)=change{repo.appointment(v)}
    fun deleteAppointment(id:Long)=change{repo.deleteAppointment(id)}
    fun setVisitCompleted(id:Long,completed:Boolean)=change{repo.setVisitCompleted(id,completed)}
    fun saveVisitQuestion(v:VisitQuestionEntity)=change{repo.saveVisitQuestion(v)}
    fun deleteVisitQuestion(id:Long)=change{repo.deleteVisitQuestion(id)}
    fun moveVisitQuestion(id:Long,up:Boolean)=change{repo.moveVisitQuestion(id,up)}
    /** Reads one transaction, writes the PDF, and only then keeps the immutable record of what was exported. */
    fun exportVisitPack(uri:android.net.Uri,appointmentId:Long,from:LocalDate,to:LocalDate,sections:Set<VisitSection>,context:android.content.Context,
                        labels:(CheckinItemEntity)->String,schedules:Map<Long,String>,regimenLabels:Map<Long,String>,unknownName:String)=dataOp {
        require(from<=to && to<=LocalDate.now() && sections.isNotEmpty())
        val zone=java.time.ZoneId.systemDefault()
        val (d,spec)=repo.transaction{dao->val d=exportData(labels,schedules);val a=requireNotNull(dao.appointmentById(appointmentId))
            d to net.plainnotes.app.visit.VisitPackSpec(a,from,to,sections,dao.visitQuestions().filter{it.appointment_id==appointmentId},regimenLabels,unknownName)}
        val recordLabels=recordLabels()
        val facts=net.plainnotes.app.visit.VisitFacts.build(d,spec.appointment,from,to,zone,recordLabels)
        val language=context.resources.configuration.locales[0].toLanguageTag()
        val digest=net.plainnotes.app.visit.VisitDigest.compute(d,spec,facts,language,zone)
        withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri,"wt")!!.use{net.plainnotes.app.export.PdfReport.write(context,d,1,null,it,visit=spec,facts=facts,digest=digest,labels=recordLabels)}}
        mutate{repo.recordVisitPack(VisitPackEntity(appointment_id=appointmentId,generated_utc=Instant.now().toEpochMilli(),zone=zone.id,range_from=from.toString(),range_to=to.toString(),
            sections=VisitSection.encode(sections),language=language,template_version=net.plainnotes.app.visit.VISIT_TEMPLATE_VERSION,input_digest=digest,facts_json=facts.json().toString()))}
        refresh().join();DataJob.Done(R.string.export_saved)
    }
    /** Stages of the last test reminder (scheduled, received, notification outcome) and the immediate test, for the settings page. */
    val reminderTest=MutableStateFlow(net.plainnotes.app.reminder.ReminderTestStatus())
    fun refreshReminderTest(){reminderTest.value=reminders.testStatus()}
    fun testReminder()=viewModelScope.launch{try{reminderTest.value=reminders.testReminder()}catch(e:Exception){mutable.value=mutable.value.copy(error=when(e){is MedicalVisitConflict->R.string.medical_stale;is PeriodRestoreConflict->R.string.period_restore_overlap;is PackageRestoreConflict->R.string.pkg_restore_conflict;is PackageConsumptionConflict->R.string.pkg_undo_locked;is SkipUndoStale->R.string.history_skip_stale;is SkipUndoUnavailable->R.string.history_skip_unavailable;is SkipUndoUnitConflict->R.string.history_skip_unit_changed;else->R.string.operation_error})}}
    fun sync()=viewModelScope.launch{runCatching{reminders.sync()};refreshReminderTest();refresh().join()}
}

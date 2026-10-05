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

data class NotesState(val medications:List<MedicationEntity> = emptyList(),val slots:List<TimelineEntry> = emptyList(),val appointments:List<AppointmentEntity> = emptyList(),val error:Int?=null,val loading:Boolean=true)
data class EditMedication(val medication:MedicationEntity?,val profile:ProfileEntity?,val rule:RuleEntity?,val times:List<TimeEntity>)
@HiltViewModel class NotesViewModel @Inject constructor(private val repo:NotesRepository,private val reminders:ReminderCoordinator):ViewModel() {
    val notificationSlot=MutableStateFlow<Slot?>(null)
    fun notification(id:String)=viewModelScope.launch {
        runCatching { val mapping=repo.transaction{it.mappings().firstOrNull{m->m.opaque_id==id}} ?: return@runCatching
            notificationSlot.value=repo.calendar(displayFrom=LocalDate.now().minusDays(1)).firstOrNull{mapping.identity=="${it.slot.key}:SOON"||mapping.identity=="${it.slot.key}:DUE"||mapping.identity=="${it.slot.key}:LATE"}?.slot }
    }
    private val mutable=MutableStateFlow(NotesState());val state=mutable.asStateFlow()
    var editor=MutableStateFlow<EditMedication?>(null);private set
    var override=MutableStateFlow<SlotOverride?>(null);private set
    private var calendarStart=LocalDate.now()
    fun calendarFrom(value:String){try{calendarStart=LocalDate.parse(value);refresh()}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.invalid)}}
    init { refresh();viewModelScope.launch{while(isActive){delay(30000);refresh()}} }
    fun refresh()=viewModelScope.launch { try {
        val meds=repo.medications();val slots=repo.calendar(displayFrom=calendarStart);val appts=repo.appointments()
        mutable.value=NotesState(meds,slots,appts,null,false)
    }catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.data_error,loading=false)} }
    fun clearError(){mutable.value=mutable.value.copy(error=null)}
    private fun change(block:suspend()->Unit)=viewModelScope.launch{try{reminders.mutate(block);refresh()}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)}}
    fun edit(m:MedicationEntity?)=viewModelScope.launch {try {
        val r=m?.let{repo.rules().lastOrNull{r->r.medication_id==it.id&&r.effective_until_utc==null}}
        val t=if(r==null)emptyList()else repo.transaction{it.times(r.id)}
        editor.value=EditMedication(m,m?.let{repo.profile(it.id)},r,t)
    }catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)} }
    fun closeEditor(){editor.value=null}
    fun save(m:MedicationEntity,ester:String?,kind:RuleKind,n:Int,times:List<LocalTime>,days:Set<DayOfWeek>)=change {repo.saveMedication(m,ester,kind,n,times,days);editor.value=null}
    fun delete(id:Long)=change{repo.removeMedication(id)}
    fun complete(s:Slot,t:Instant,d:Double)=change{repo.complete(s,t,d)}
    fun manual(id:Long,t:Instant,d:Double)=change{repo.unscheduled(id,t,d)}
    fun loadOverride(key:String)=viewModelScope.launch{override.value=repo.currentOverride(key)}
    fun changeOverride(s:Slot,o:SlotOverride)=change{repo.override(s,o);override.value=null}
    fun appointment(v:AppointmentEntity)=change{repo.appointment(v)}
    fun testReminder()=viewModelScope.launch{try{reminders.testReminder()}catch(_:Exception){mutable.value=mutable.value.copy(error=R.string.operation_error)}}
    fun sync()=viewModelScope.launch{runCatching{reminders.sync()};refresh()}
}

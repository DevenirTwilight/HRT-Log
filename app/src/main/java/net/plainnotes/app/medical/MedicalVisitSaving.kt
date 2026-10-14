package net.plainnotes.app.medical

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.plainnotes.app.data.*
import java.util.concurrent.atomic.AtomicBoolean

data class MedicalVisitDraft(val value:AppointmentEntity,val expected:AppointmentEntity?=null,val space:Space=Space.PRIMARY)
data class MedicalVisitSaveState(val saving:Boolean=false,val saved:AppointmentEntity?=null,val failed:Boolean=false,val stale:Boolean=false,val refreshFailed:Boolean=false)

/** A committed visit stays acknowledged even when reminders or refresh fail, so retry cannot insert it twice. */
class MedicalVisitSaving(private val scope:CoroutineScope,
    private val write:suspend (MedicalVisitDraft,(AppointmentEntity)->Unit)->Unit,
    private val refresh:suspend ()->Boolean) {
    private val busy=AtomicBoolean(false)
    private val mutable=MutableStateFlow(MedicalVisitSaveState())
    val state=mutable.asStateFlow()
    fun clear(){if(!busy.get())mutable.value=MedicalVisitSaveState()}
    fun save(draft:MedicalVisitDraft) {
        if(mutable.value.saved!=null || !busy.compareAndSet(false,true))return
        mutable.value=MedicalVisitSaveState(saving=true)
        scope.launch {
            var committed:AppointmentEntity?=null
            try {
                write(draft){committed=it}
                checkNotNull(committed)
                val refreshed=refresh()
                mutable.value=MedicalVisitSaveState(saved=committed,refreshFailed=!refreshed)
            } catch(e:CancellationException) {
                mutable.value=MedicalVisitSaveState(saved=committed,refreshFailed=committed!=null)
                throw e
            } catch(e:Exception) {
                mutable.value=MedicalVisitSaveState(saved=committed,failed=committed==null,stale=e is MedicalVisitConflict,refreshFailed=committed!=null)
            } finally {busy.set(false)}
        }
    }
}

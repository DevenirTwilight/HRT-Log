package net.plainnotes.app.timeline

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.plainnotes.app.data.MilestoneEntity
import java.util.concurrent.atomic.AtomicBoolean

data class MilestoneSaveState(val saving:Boolean=false,val saved:MilestoneEntity?=null,val failed:Boolean=false,val refreshFailed:Boolean=false)

/** Commit acknowledgement is independent of reminder rescheduling and UI refresh. */
class MilestoneSaving(private val scope:CoroutineScope,
    private val write:suspend (MilestoneEntity,(MilestoneEntity)->Unit)->Unit,
    private val refresh:suspend ()->Boolean) {
    private val busy=AtomicBoolean(false)
    private val mutable=MutableStateFlow(MilestoneSaveState())
    val state=mutable.asStateFlow()
    fun clear(){if(!busy.get())mutable.value=MilestoneSaveState()}
    fun save(value:MilestoneEntity) {
        if(!busy.compareAndSet(false,true))return
        mutable.value=MilestoneSaveState(saving=true)
        scope.launch {
            var committed:MilestoneEntity?=null
            try {
                write(value){committed=it}
                checkNotNull(committed)
                val refreshed=refresh()
                mutable.value=MilestoneSaveState(saved=committed,refreshFailed=!refreshed)
            } catch(e:CancellationException) {
                mutable.value=MilestoneSaveState(saved=committed,refreshFailed=committed!=null)
                throw e
            } catch(_:Exception) {
                mutable.value=MilestoneSaveState(saved=committed,failed=committed==null,refreshFailed=committed!=null)
            } finally {busy.set(false)}
        }
    }
}

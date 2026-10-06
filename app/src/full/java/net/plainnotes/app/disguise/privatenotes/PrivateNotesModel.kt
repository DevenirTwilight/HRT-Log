package net.plainnotes.app.disguise.privatenotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory view state survives rotation, but is never serialized into Activity saved state or HRT storage. */
class PrivateNotesModel(private val store:PrivateStore):ViewModel() {
    data class State(val notes:List<PrivateNote> = emptyList(),val loading:Boolean=true,val error:Boolean=false,
        val editing:Boolean=false,val id:String?=null,val title:String="",val body:String="",val saving:Boolean=false)
    private val mutable=MutableStateFlow(State());val state=mutable.asStateFlow()
    private val writes=Mutex()
    private var operation:Job?=null
    init { reload() }
    fun reload() {
        operation?.cancel()
        operation=viewModelScope.launch {
            mutable.value=mutable.value.copy(loading=true,error=false)
            try { val notes=withContext(Dispatchers.IO){store.list()};ensureActive();mutable.value=mutable.value.copy(notes=notes,loading=false) }
            catch(e:CancellationException){throw e} catch(_:Exception){mutable.value=mutable.value.copy(loading=false,error=true)}
        }
    }
    fun edit(note:PrivateNote?){mutable.value=mutable.value.copy(editing=true,id=note?.id,title=note?.title ?: "",body=note?.body ?: "",error=false)}
    fun draft(title:String,body:String){mutable.value=mutable.value.copy(title=title,body=body)}
    fun cancel(){mutable.value=mutable.value.copy(editing=false,id=null,title="",body="",error=false)}
    fun save() {
        if(mutable.value.saving)return
        val draft=mutable.value
        operation=viewModelScope.launch {
            mutable.value=mutable.value.copy(saving=true,error=false)
            try {
                val notes=writes.withLock{withContext(Dispatchers.IO){store.save(draft.id,draft.title,draft.body);store.list()}}
                ensureActive();mutable.value=State(notes=notes,loading=false)
            } catch(e:CancellationException){throw e} catch(_:Exception){mutable.value=mutable.value.copy(saving=false,error=true)}
        }
    }
    fun delete(id:String) {
        if(mutable.value.saving)return
        operation=viewModelScope.launch {
            mutable.value=mutable.value.copy(saving=true,error=false)
            try {
                val notes=writes.withLock{withContext(Dispatchers.IO){store.delete(id);store.list()}}
                ensureActive();mutable.value=mutable.value.copy(notes=notes,saving=false)
            } catch(e:CancellationException){throw e} catch(_:Exception){mutable.value=mutable.value.copy(saving=false,error=true)}
        }
    }
    fun clearMemory(){operation?.cancel();mutable.value=State()}
    override fun onCleared(){clearMemory()}
}

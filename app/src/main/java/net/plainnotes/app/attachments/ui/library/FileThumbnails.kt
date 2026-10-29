package net.plainnotes.app.attachments.ui.library

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.plainnotes.app.attachments.AttachmentViewer
import net.plainnotes.app.attachments.ui.MedicalAttachmentGateway
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.FileKind
import java.util.UUID

/**
 * MED-05: the first page (or a reduced image) of each file as a small picture for the file library.
 *
 * Plaintext pixels live only here, in memory: a bounded number of small bitmaps, never written to disk, a backup or an export.
 * Each one is made through the same authenticated, revocable reader as the full viewer, one file at a time; a damaged file only
 * marks itself unavailable. Lock, disguise, a data switch, leaving the page and [close] all drop every picture at once.
 */
internal class FileThumbnails(private val scope:CoroutineScope,private val io:CoroutineDispatcher,
    private val open:suspend (UUID)->MedicalAttachmentGateway.Preview,
    private val maxEntries:Int=MAX_ENTRIES,
    /** Bitmaps are recycled where they are drawn, so a frame can never draw a bitmap that was just recycled. */
    private val recycle:(List<Bitmap>)->Unit=::recycleOnMain):AutoCloseable {
    enum class Size(val edge:Int) { ROW(192), PAGE(768) }
    enum class Reason { PDF_NEEDS_ANDROID_9, NO_PREVIEW_FOR_TYPE, FAILED }
    data class Key(val document:UUID,val size:Size)
    sealed interface Thumb {
        class Ready(val bitmap:Bitmap):Thumb
        data class Unavailable(val reason:Reason):Thumb
    }
    private val mutable=MutableStateFlow<Map<Key,Thumb>>(emptyMap())
    val state:StateFlow<Map<Key,Thumb>> =mutable.asStateFlow()
    private val lock=Any()
    private val queue=ArrayDeque<Pair<Key,FileKind>>()
    private val known=HashSet<Key>()
    private var generation=0L
    private var pump:Job?=null
    private var closed=false
    /** Idempotent. The newest request is served first, so what the user is looking at is made before what scrolled past. */
    fun request(document:UUID,kind:FileKind,size:Size) {
        val key=Key(document,size)
        synchronized(lock) {
            if(closed || key in known)return
            if(kind==FileKind.OTHER) {known+=key;put(key,Thumb.Unavailable(Reason.NO_PREVIEW_FOR_TYPE));return}
            known+=key;queue.addFirst(key to kind)
            if(pump?.isActive!=true)pump=scope.launch {drain()}
        }
    }
    private suspend fun drain() {
        while(true) {
            val (key,kind,gen)=synchronized(lock) {
                if(closed || queue.isEmpty()) {pump=null;return}
                val next=queue.removeFirst();Triple(next.first,next.second,generation)
            }
            val result=try {make(key,kind)}catch(e:CancellationException) {throw e}
                catch(e:Throwable) {Thumb.Unavailable(if(e is AttachmentViewer.PdfPreviewUnsupported)Reason.PDF_NEEDS_ANDROID_9 else Reason.FAILED)}
            val rejected=synchronized(lock) {
                if(closed || gen!=generation)result else {put(key,result);null}
            }
            (rejected as? Thumb.Ready)?.let {recycle(listOf(it.bitmap))}
        }
    }
    private suspend fun make(key:Key,kind:FileKind):Thumb=withContext(io) {
        val preview=open(key.document)
        try {
            val page=if(kind==FileKind.PDF)preview.pdfPage(0,key.size.edge) else preview.image(key.size.edge)
            // The reader keeps (and later recycles) its own bitmap; this picture is an independent copy that this class owns.
            val copy=try {preview.publish {page.copy(Bitmap.Config.ARGB_8888,false)}}finally {runCatching {preview.release(page)}}
            Thumb.Ready(requireNotNull(copy))
        }finally {runCatching {preview.close()}}
    }
    /** Callers hold [lock]. */
    private fun put(key:Key,value:Thumb) {
        val next=LinkedHashMap(mutable.value)
        next.remove(key)?.let {(it as? Thumb.Ready)?.let {old->recycle(listOf(old.bitmap))}}
        next[key]=value
        val evicted=ArrayList<Bitmap>()
        while(next.size>maxEntries) {
            val oldest=next.entries.first();next.remove(oldest.key);known-=oldest.key
            (oldest.value as? Thumb.Ready)?.let {evicted+=it.bitmap}
        }
        mutable.value=next
        if(evicted.isNotEmpty())recycle(evicted)
    }
    /** Lock, disguise, a data switch or leaving the page: every picture goes, and anything still being made is discarded. */
    fun clear() {
        val dropped=synchronized(lock) {
            generation++;queue.clear();known.clear()
            val all=mutable.value.values.mapNotNull {(it as? Thumb.Ready)?.bitmap}
            mutable.value=emptyMap();all
        }
        if(dropped.isNotEmpty())recycle(dropped)
    }
    override fun close() {
        synchronized(lock) {closed=true}
        clear();pump?.cancel()
    }
    companion object {
        const val MAX_ENTRIES=48
        private fun recycleOnMain(bitmaps:List<Bitmap>) {
            if(Looper.myLooper()==Looper.getMainLooper())bitmaps.forEach {it.recycle()}
            else Handler(Looper.getMainLooper()).post {bitmaps.forEach {it.recycle()}}
        }
    }
}

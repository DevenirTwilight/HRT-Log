package net.plainnotes.app.attachments.ui.library

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.plainnotes.app.attachments.AttachmentViewer
import net.plainnotes.app.attachments.ui.MedicalAttachmentGateway
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.FileKind
import net.plainnotes.app.attachments.ui.library.FileThumbnails.Reason
import net.plainnotes.app.attachments.ui.library.FileThumbnails.Size
import net.plainnotes.app.attachments.ui.library.FileThumbnails.Thumb
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** MED-05: first-page pictures are independent in-memory copies, one file at a time, bounded, isolated per file and dropped on revocation. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class FileThumbnailsTest {
    private class Reader(val failure:Throwable?=null):MedicalAttachmentGateway.Preview {
        val made=mutableListOf<Bitmap>();val released=mutableListOf<Bitmap>();var closed=false;var edge=0;var onMake:(()->Unit)?=null
        private fun make(e:Int):Bitmap {onMake?.invoke();failure?.let {throw it};edge=e;return Bitmap.createBitmap(16,24,Bitmap.Config.ARGB_8888).also {made+=it}}
        override fun pdfFirstPage()=make(0)
        override fun image()=make(0)
        override fun pdfPage(index:Int,maxEdge:Int)=make(maxEdge)
        override fun image(maxEdge:Int)=make(maxEdge)
        override fun release(bitmap:Bitmap) {released+=bitmap;bitmap.recycle()}
        override fun close() {closed=true}
    }
    private val recycled=mutableListOf<Bitmap>()
    private fun thumbnails(max:Int=48,readers:MutableList<Pair<UUID,Reader>>,make:(UUID)->Reader)=
        FileThumbnails(CoroutineScope(Dispatchers.Unconfined),Dispatchers.Unconfined,{id->make(id).also {readers+=id to it}},max) {list->
            list.forEach {it.recycle()};recycled+=list
        }
    private fun ready(t:FileThumbnails,id:UUID,size:Size=Size.ROW)=t.state.value[FileThumbnails.Key(id,size)]

    @Test fun aPdfBecomesAnIndependentCopyAndTheReadersOwnBitmapIsReleased() {
        val readers=mutableListOf<Pair<UUID,Reader>>();val t=thumbnails(readers=readers) {Reader()}
        val id=UUID.randomUUID();t.request(id,FileKind.PDF,Size.ROW)
        val thumb=ready(t,id) as Thumb.Ready;val reader=readers.single().second
        assertEquals(Size.ROW.edge,reader.edge);assertTrue(reader.closed)
        assertEquals(1,reader.released.size);assertNotSame(reader.released.single(),thumb.bitmap)
        assertFalse(thumb.bitmap.isRecycled);assertTrue("the reader's bitmap is gone, ours is not",reader.released.single().isRecycled)
        t.close()
    }
    @Test fun aRequestIsServedOnceAndALargeSizeIsASeparatePicture() {
        val readers=mutableListOf<Pair<UUID,Reader>>();val t=thumbnails(readers=readers) {Reader()}
        val id=UUID.randomUUID()
        t.request(id,FileKind.IMAGE,Size.ROW);t.request(id,FileKind.IMAGE,Size.ROW);t.request(id,FileKind.IMAGE,Size.PAGE)
        assertEquals(2,readers.size);assertEquals(Size.PAGE.edge,readers.last().second.edge)
        assertNotNull(ready(t,id,Size.ROW));assertNotNull(ready(t,id,Size.PAGE))
        t.close()
    }
    @Test fun anOtherFileOpensNothingAndSaysWhy() {
        val readers=mutableListOf<Pair<UUID,Reader>>();val t=thumbnails(readers=readers) {Reader()}
        val id=UUID.randomUUID();t.request(id,FileKind.OTHER,Size.ROW)
        assertTrue(readers.isEmpty());assertEquals(Thumb.Unavailable(Reason.NO_PREVIEW_FOR_TYPE),ready(t,id))
        t.close()
    }
    @Test fun aBadFileOnlyMarksItselfAndAnOldAndroidPdfSaysSo() {
        val readers=mutableListOf<Pair<UUID,Reader>>()
        val bad=UUID.randomUUID();val old=UUID.randomUUID();val good=UUID.randomUUID()
        val t=thumbnails(readers=readers) {id->when(id) {bad->Reader(IllegalStateException("damaged"));old->Reader(AttachmentViewer.PdfPreviewUnsupported());else->Reader()}}
        t.request(bad,FileKind.PDF,Size.ROW);t.request(old,FileKind.PDF,Size.ROW);t.request(good,FileKind.PDF,Size.ROW)
        assertEquals(Thumb.Unavailable(Reason.FAILED),ready(t,bad));assertEquals(Thumb.Unavailable(Reason.PDF_NEEDS_ANDROID_9),ready(t,old))
        assertTrue(ready(t,good) is Thumb.Ready);assertTrue(readers.all {it.second.closed})
        t.request(bad,FileKind.PDF,Size.ROW);assertEquals("a failed file is not retried in a loop",3,readers.size)
        t.close()
    }
    @Test fun theOldestPicturesAreEvictedAndRecycledPastTheLimit() {
        val readers=mutableListOf<Pair<UUID,Reader>>();val t=thumbnails(max=2,readers=readers) {Reader()}
        val ids=List(3) {UUID.randomUUID()}
        ids.forEach {t.request(it,FileKind.PDF,Size.ROW)}
        assertEquals(2,t.state.value.size);assertNull(ready(t,ids[0]));assertNotNull(ready(t,ids[2]))
        assertEquals(1,recycled.size)
        t.request(ids[0],FileKind.PDF,Size.ROW);assertNotNull("an evicted picture can be made again",ready(t,ids[0]))
        t.close()
    }
    @Test fun clearDropsEveryPictureAtOnceAndDiscardsOneStillBeingMade() {
        val readers=mutableListOf<Pair<UUID,Reader>>()
        lateinit var t:FileThumbnails
        val first=UUID.randomUUID();val second=UUID.randomUUID()
        t=thumbnails(readers=readers) {id->Reader().also {r->if(id==second)r.onMake={t.clear()}}}
        t.request(first,FileKind.PDF,Size.ROW)
        val shown=(ready(t,first) as Thumb.Ready).bitmap
        t.request(second,FileKind.PDF,Size.ROW) // locked while this one is being made
        assertTrue(t.state.value.isEmpty());assertTrue(shown.isRecycled)
        assertTrue("the picture made after the lock is recycled, never shown",readers.last().second.released.isNotEmpty())
        t.request(first,FileKind.PDF,Size.ROW);assertNotNull("after access returns pictures are made again",ready(t,first))
        t.close()
    }
    @Test fun aClosedCacheServesNothing() {
        val readers=mutableListOf<Pair<UUID,Reader>>();val t=thumbnails(readers=readers) {Reader()}
        val id=UUID.randomUUID();t.request(id,FileKind.PDF,Size.ROW);val bitmap=(ready(t,id) as Thumb.Ready).bitmap
        t.close();assertTrue(bitmap.isRecycled);assertTrue(t.state.value.isEmpty())
        t.request(UUID.randomUUID(),FileKind.PDF,Size.ROW);assertEquals(1,readers.size)
    }
}

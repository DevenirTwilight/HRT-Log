package net.plainnotes.app.attachments.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.attachments.AttachmentFlowController
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.*
import net.plainnotes.app.data.MedicalAttachmentOperations
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.UUID

/** MED-05: the viewer's page changes against a scripted multi-page reader — bounds, bounded memory, failure and revocation. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class MedicalViewerPagesTest {
    private val target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,7)
    private class Pages(val count:Int):MedicalAttachmentGateway.Preview {
        val made=mutableListOf<Pair<Int,Bitmap>>();val released=mutableListOf<Bitmap>();var closed=false
        var failIndex:Int?=null;var whileMaking:((Int)->Unit)?=null
        override fun pdfFirstPage()=pdfPage(0,1600)
        override fun image()=error("not an image")
        override fun release(bitmap:Bitmap) {released+=bitmap;bitmap.recycle()}
        override fun pageCount()=count
        override fun pdfPage(index:Int,maxEdge:Int):Bitmap {
            whileMaking?.invoke(index)
            if(index==failIndex)error("synthetic page failure")
            return Bitmap.createBitmap(8,8+index,Bitmap.Config.ARGB_8888).also {made+=index to it}
        }
        override fun close() {closed=true;made.forEach {it.second.recycle()}}
    }
    private class Gateway(override val target:MedicalAttachmentOperations.Target,val pages:Pages):MedicalAttachmentGateway {
        override var scopeEnded=false
        var revoke:(()->Unit)?=null
        override suspend fun documents()=listOf(MedicalAttachmentOperations.Document(UUID.randomUUID(),1,"Synthetic scan","application/pdf","ACTIVE","ACTIVE"))
        override suspend fun pickerIntent()=Intent(Intent.ACTION_OPEN_DOCUMENT)
        override suspend fun preview(document:UUID):MedicalAttachmentGateway.Preview=pages
        override suspend fun newImport(title:String):MedicalAttachmentGateway.Import=error("not used")
        override suspend fun reconcile(operation:UUID,document:UUID)=AttachmentFlowController.State.ReconcileRequired
        override fun onRevoked(listener:()->Unit):AutoCloseable {revoke=listener;return AutoCloseable {}}
    }
    private fun opened(pages:Pages):Pair<MedicalAttachmentsController,Gateway> {
        val gateway=Gateway(target,pages)
        val controller=MedicalAttachmentsController(gateway,CoroutineScope(Dispatchers.Unconfined),Dispatchers.Unconfined)
        controller.refresh()
        controller.open((controller.state.value.files as Files.Ready).items.single())
        return controller to gateway
    }
    private fun showing(c:MedicalAttachmentsController)=c.state.value.viewer as Viewer.Showing

    @Test fun aPdfOpensOnItsFirstPageWithItsPageCount()=runBlocking {
        val (c,_)=opened(Pages(3))
        val s=showing(c);assertTrue(s.pdf);assertEquals(0,s.pageIndex);assertEquals(3,s.pageCount);assertFalse(s.busy);assertFalse(s.pageFailed)
        c.close()
    }
    @Test fun anotherPageReplacesTheShownOneAndTheOldBitmapIsReleasedAMomentLater()=runBlocking {
        val pages=Pages(3);val (c,_)=opened(pages)
        val first=showing(c).page
        c.showPage(1)
        val s=showing(c);assertEquals(1,s.pageIndex);assertFalse(s.busy);assertNotSame(first,s.page);assertFalse(s.page.isRecycled)
        assertFalse("the page that was on screen is not recycled in the same step",first.isRecycled)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertTrue(first.isRecycled);assertEquals(listOf(first),pages.released)
        c.showPage(2);assertEquals(2,showing(c).pageIndex)
        c.close()
    }
    @Test fun outOfRangeSamePageAndBusyRequestsAreIgnored()=runBlocking {
        val pages=Pages(3);val (c,_)=opened(pages)
        c.showPage(-1);c.showPage(3);c.showPage(0)
        assertEquals(1,pages.made.size);assertEquals(0,showing(c).pageIndex)
        pages.whileMaking={index->if(index==1)c.showPage(2)} // a request arriving while page 1 is being made
        c.showPage(1)
        assertEquals(1,showing(c).pageIndex);assertEquals(listOf(0,1),pages.made.map {it.first})
        c.close()
    }
    @Test fun aPageThatCannotBeMadeKeepsTheShownPageAndSaysSo()=runBlocking {
        val pages=Pages(3).apply {failIndex=1};val (c,_)=opened(pages)
        val first=showing(c).page
        c.showPage(1)
        val s=showing(c);assertSame(first,s.page);assertEquals(0,s.pageIndex);assertTrue(s.pageFailed);assertFalse(s.busy);assertFalse(first.isRecycled)
        pages.failIndex=null;c.showPage(1)
        assertEquals(1,showing(c).pageIndex);assertFalse(showing(c).pageFailed)
        c.close()
    }
    @Test fun lockingWhileAPageIsBeingMadeDiscardsItAndShowsNothing()=runBlocking {
        val pages=Pages(3);val (c,g)=opened(pages)
        pages.whileMaking={index->if(index==1)requireNotNull(g.revoke)()}
        c.showPage(1)
        assertTrue(c.state.value.revoked);assertEquals(Viewer.Closed,c.state.value.viewer)
        assertTrue(pages.made.all {it.second.isRecycled});assertTrue(pages.closed)
        c.close()
    }
    @Test fun closingTheViewerWhileAPageIsBeingMadeDiscardsIt()=runBlocking {
        val pages=Pages(3);val (c,_)=opened(pages)
        pages.whileMaking={index->if(index==1)c.closeViewer()}
        c.showPage(1)
        assertEquals(Viewer.Closed,c.state.value.viewer);assertTrue(pages.closed);assertTrue(pages.made.all {it.second.isRecycled})
        c.close()
    }
    @Test fun anImageHasNoPagesToTurn()=runBlocking {
        val pages=Pages(1);val (c,_)=opened(pages)
        c.showPage(1);assertEquals(0,showing(c).pageIndex);assertEquals(1,showing(c).pageCount);assertEquals(1,pages.made.size)
        c.close()
    }
}

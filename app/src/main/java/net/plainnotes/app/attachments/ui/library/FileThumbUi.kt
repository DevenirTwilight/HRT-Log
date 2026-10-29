package net.plainnotes.app.attachments.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.FileTile
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.FileKind
import net.plainnotes.app.attachments.ui.library.FileThumbnails.Reason
import net.plainnotes.app.attachments.ui.library.FileThumbnails.Thumb

/**
 * Draws a bitmap that someone else owns and may recycle. The check happens in the draw pass on the main thread, where owners
 * recycle too, so a picture removed by a lock or an eviction can never be drawn after it is gone.
 * [crop] fills the box from the top (a page's heading stays visible); otherwise the whole bitmap is fitted inside.
 */
@Composable internal fun GuardedBitmap(bitmap:Bitmap,modifier:Modifier=Modifier,crop:Boolean=true) {
    val image=remember(bitmap) {bitmap.asImageBitmap()}
    Canvas(modifier.clipToBounds()) {
        if(bitmap.isRecycled || bitmap.width<=0 || bitmap.height<=0)return@Canvas
        val scale=if(crop)maxOf(size.width/bitmap.width,size.height/bitmap.height) else minOf(size.width/bitmap.width,size.height/bitmap.height)
        val width=(bitmap.width*scale).toInt().coerceAtLeast(1);val height=(bitmap.height*scale).toInt().coerceAtLeast(1)
        val left=((size.width-width)/2f).toInt();val top=if(crop)0 else ((size.height-height)/2f).toInt()
        clipRect {drawImage(image,dstOffset=IntOffset(left,top),dstSize=IntSize(width,height))}
    }
}

internal fun Reason.messageRes()=when(this) {
    Reason.PDF_NEEDS_ANDROID_9->R.string.viewer_pdf_unsupported
    Reason.NO_PREVIEW_FOR_TYPE->R.string.viewer_no_preview
    Reason.FAILED->R.string.viewer_failed
}

/** A file-library row's picture: the first page when it is ready, otherwise the type tile (never an empty frame). Decorative: the row names the file. */
@Composable internal fun RowThumb(kind:FileKind,thumb:Thumb?,modifier:Modifier=Modifier) {
    val ready=(thumb as? Thumb.Ready)?.bitmap?.takeUnless {it.isRecycled}
    Box(modifier.size(width=56.dp,height=72.dp).clearAndSetSemantics {},contentAlignment=Alignment.Center) {
        if(ready!=null) Surface(Modifier.fillMaxSize(),shape=MaterialTheme.shapes.small,color=Color.White,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            GuardedBitmap(ready,Modifier.fillMaxSize().testTag("library-thumb"))
        } else FileTile(kind,true)
    }
}

/** The big picture on the file page: the first page, or the type tile with the honest reason, or progress while it is being made. */
@Composable internal fun PagePreview(kind:FileKind,thumb:Thumb?,available:Boolean,modifier:Modifier=Modifier) {
    val ready=(thumb as? Thumb.Ready)?.bitmap?.takeUnless {it.isRecycled}
    Box(modifier.fillMaxSize().padding(16.dp),contentAlignment=Alignment.Center) {
        when {
            ready!=null->Surface(Modifier.fillMaxHeight().aspectRatio(ready.width.toFloat()/ready.height.coerceAtLeast(1)),shape=MaterialTheme.shapes.small,color=Color.White,shadowElevation=2.dp) {
                GuardedBitmap(ready,Modifier.fillMaxSize().testTag("file-detail-thumb"),crop=false)
            }
            thumb is Thumb.Unavailable->Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                FileTile(kind,available)
                Text(stringResource(thumb.reason.messageRes()),Modifier.testTag("file-detail-thumb-reason"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            available && kind!=FileKind.OTHER->CircularProgressIndicator(Modifier.size(32.dp).testTag("file-detail-thumb-loading"))
            else->FileTile(kind,available)
        }
    }
}

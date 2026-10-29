package net.plainnotes.app.attachments.ui

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import net.plainnotes.app.attachments.ui.library.GuardedBitmap
import kotlin.math.abs
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.FileKind
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Files
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Import
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Item
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Reason
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Viewer
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.UnlinkStatus
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.State as AttachmentsState
import net.plainnotes.app.ui.Corners
import net.plainnotes.app.ui.GroupedItem
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.StatusPill

/**
 * UI-10: the related files of one explicit record. A heading with the file count, a status banner when something is
 * happening, then the files as a connected list with one clear "Add file" action, or a single empty state that carries the
 * same action. Stateless; [MedicalAttachmentsController] owns the work. Encryption details stay out of the UI.
 */
@Composable internal fun MedicalAttachmentsSection(state:AttachmentsState,targetLabel:String,onAdd:()->Unit,onConfirm:(String)->Unit,onCancelImport:()->Unit,
    onCheckStatus:()->Unit,onDismissMessage:()->Unit,onRetry:()->Unit,onOpen:(Item)->Unit,modifier:Modifier=Modifier,
    onUnlink:((Item)->Unit)?=null,onDismissUnlinkMessage:()->Unit={},
    /** Offered only when the file library can link an already stored file (not available yet); null draws nothing. */
    onChooseExisting:(()->Unit)?=null,
    /** What the files are linked to when that is narrower than the page suggests (one result of a multi-result blood test). */
    scopeNote:String?=null,
    /** The file library lists files itself: show only the import / unlink status here, never an empty "No files yet" card after a save. */
    statusOnly:Boolean=false) {
    var pendingUnlink by remember {mutableStateOf<Item?>(null)}
    Column(modifier.fillMaxWidth().testTag("attachments"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        val ready=(state.files as? Files.Ready)?.takeIf {!state.revoked && !state.ended}
        if(!statusOnly)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.attach_section),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,
                modifier=Modifier.weight(1f).semantics {heading()})
            if(ready!=null && ready.items.isNotEmpty()) {
                val tone=MaterialTheme.colorScheme
                Box(Modifier.testTag("attachments-count")) {
                    StatusPill(pluralStringResource(R.plurals.attach_count,ready.items.size,ready.items.size),tone.secondaryContainer,tone.onSecondaryContainer)
                }
            }
        }
        if(scopeNote!=null && !state.revoked && !state.ended)Text(scopeNote,Modifier.testTag("attachments-scope"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            state.ended->Banner(stringResource(R.string.attach_data_changed),"attachments-ended")
            state.revoked->Banner(stringResource(R.string.attach_hidden),"attachments-hidden")
            else-> {
                ImportStatus(state.import,onCheckStatus,onDismissMessage)
                when(state.unlinkStatus) {
                    UnlinkStatus.WORKING->Banner(stringResource(R.string.attach_unlink_working),"attachments-unlink-status")
                    UnlinkStatus.REMOVED->Message(stringResource(R.string.attach_unlink_done),Modifier.testTag("attachments-unlink-status"),onDismissUnlinkMessage)
                    UnlinkStatus.REMOVED_LIST_STALE->Message(stringResource(R.string.attach_unlink_list_stale),Modifier.testTag("attachments-unlink-status"),onDismissUnlinkMessage)
                    UnlinkStatus.UNKNOWN->Banner(stringResource(R.string.attach_unlink_unknown),"attachments-unlink-status")
                    UnlinkStatus.IDLE->{}
                }
                val saving=state.import==Import.Saving || state.import==Import.Checking || state.import==Import.Unknown ||
                    state.unlinkStatus in setOf(UnlinkStatus.WORKING,UnlinkStatus.UNKNOWN)
                val canAdd=!saving && state.import !is Import.Confirming
                if(!statusOnly)when(val files=state.files) {
                    Files.Loading->LinearProgressIndicator(Modifier.fillMaxWidth().testTag("attachments-loading"))
                    Files.Failed->Surface(Modifier.fillMaxWidth(),shape=Corners.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.attach_list_failed),color=MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onRetry,Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.attach_retry))}
                        }
                    }
                    is Files.Ready->if(files.items.isEmpty())EmptyFiles(canAdd,onAdd,onChooseExisting)
                    else {
                        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {files.items.forEachIndexed {i,item->
                            AttachmentRow(item,i,files.items.size,onOpen,
                                onUnlink=if(onUnlink!=null && state.unlinkStatus==UnlinkStatus.IDLE && canAdd) ({pendingUnlink=item}) else null)
                        }}
                        AddButton(canAdd,onAdd,prominent=false)
                        ChooseExisting(canAdd,onChooseExisting)
                    }
                }
            }
        }
    }
    (state.import as? Import.Confirming)?.takeIf {!state.revoked}?.let {ConfirmDialog(it.title,targetLabel,onConfirm,onCancelImport)}
    pendingUnlink?.takeIf {!state.revoked && !state.ended && state.unlinkStatus==UnlinkStatus.IDLE}?.let {item->
        AlertDialog(onDismissRequest={pendingUnlink=null},
            title={Text(stringResource(R.string.attach_unlink_title))},
            text={Text(stringResource(R.string.attach_unlink_explain,item.title))},
            confirmButton={TextButton(onClick={pendingUnlink=null;onUnlink?.invoke(item)},modifier=Modifier.testTag("attachments-unlink-confirm")) {
                Text(stringResource(R.string.attach_unlink_action))
            }},
            dismissButton={TextButton(onClick={pendingUnlink=null}) {Text(stringResource(R.string.cancel))}})
    }
}

/** The one primary action of the section. Prominent (filled) in the empty state, tonal under a list. */
@Composable private fun AddButton(enabled:Boolean,onAdd:()->Unit,prominent:Boolean) {
    val modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("attachments-add")
    val content:@Composable RowScope.()->Unit={Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.attach_add))}
    if(prominent)Button(onAdd,modifier,enabled=enabled,content=content) else FilledTonalButton(onAdd,modifier,enabled=enabled,content=content)
}

@Composable private fun ChooseExisting(enabled:Boolean,onChoose:(()->Unit)?) {
    if(onChoose!=null)OutlinedButton(onChoose,Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("attachments-choose-existing"),enabled=enabled) {Text(stringResource(R.string.attach_choose_existing))}
}

@Composable private fun EmptyFiles(canAdd:Boolean,onAdd:()->Unit,onChooseExisting:(()->Unit)?=null) {
    Surface(Modifier.fillMaxWidth().testTag("attachments-empty"),shape=Corners.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Outlined.Description,null,Modifier.padding(14.dp).size(28.dp),tint=MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(stringResource(R.string.attach_empty_title),style=MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.attach_empty),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            AddButton(canAdd,onAdd,prominent=true)
            ChooseExisting(canAdd,onChooseExisting)
        }
    }
}

/** A calm status line with a tinted container; announced politely when it changes. */
@Composable private fun Banner(text:String,tag:String) {
    Surface(Modifier.fillMaxWidth().testTag(tag).semantics {liveRegion=LiveRegionMode.Polite},shape=Corners.list,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(text,Modifier.padding(horizontal=16.dp,vertical=12.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ImportStatus(import:Import,onCheckStatus:()->Unit,onDismiss:()->Unit) {
    val polite=Modifier.testTag("attachments-status").semantics {liveRegion=LiveRegionMode.Polite}
    when(import) {
        Import.Saving,Import.Checking->Surface(polite.fillMaxWidth(),shape=Corners.list,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if(import==Import.Saving)R.string.attach_saving else R.string.attach_checking))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        is Import.Saved->Message(stringResource(if(import.listStale)R.string.attach_saved_list_stale else R.string.attach_saved),polite,onDismiss)
        Import.NotSaved->Message(stringResource(R.string.attach_not_saved),polite,onDismiss,error=true)
        Import.Cancelled->Message(stringResource(R.string.attach_cancelled),polite,onDismiss)
        Import.Unknown->Surface(polite.fillMaxWidth(),shape=Corners.list,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.attach_unknown))
                OutlinedButton(onCheckStatus,Modifier.heightIn(min=48.dp).testTag("attachments-check")) {Text(stringResource(R.string.attach_check))}
            }
        }
        Import.Idle,is Import.Confirming->{}
    }
}

@Composable private fun Message(text:String,modifier:Modifier,onDismiss:()->Unit,error:Boolean=false) {
    val c=MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(),shape=Corners.list,color=if(error)c.errorContainer else c.secondaryContainer,contentColor=if(error)c.onErrorContainer else c.onSecondaryContainer) {
        Row(Modifier.padding(start=16.dp,top=4.dp,bottom=4.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(text,Modifier.weight(1f))
            TextButton(onDismiss,Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.attach_dismiss))}
        }
    }
}

/** A tinted file tile, title and type; unavailable files say so in grey. The row opens the file; the overflow removes the link. */
@Composable private fun AttachmentRow(item:Item,index:Int,count:Int,onOpen:(Item)->Unit,onUnlink:(()->Unit)?=null) {
    val kind=stringResource(when(item.kind) {FileKind.PDF->R.string.attach_kind_pdf;FileKind.IMAGE->R.string.attach_kind_image;FileKind.OTHER->R.string.attach_kind_other})
    val status=stringResource(if(item.available)R.string.attach_status_saved else R.string.attach_status_unavailable)
    val description=stringResource(R.string.attach_item_description,item.title,kind,status)
    var menu by remember {mutableStateOf(false)}
    GroupedItem(index,count) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Row(Modifier.weight(1f).then(if(item.available)Modifier.clickable(role=Role.Button) {onOpen(item)} else Modifier)) {
                Row(Modifier.fillMaxWidth().heightIn(min=72.dp).padding(start=16.dp,top=12.dp,bottom=12.dp,end=if(onUnlink!=null)8.dp else 16.dp)
                    .testTag("attachment:${item.document}").clearAndSetSemantics {contentDescription=description;if(item.available)role=Role.Button},
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    FileTile(item.kind,item.available)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(item.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(kind,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(!item.available)Text(status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.outline)
                    }
                }
            }
            if(onUnlink!=null) Box {
                IconButton({menu=true},Modifier.size(48.dp).testTag("attachment-more:${item.document}")) {
                    Icon(Icons.Outlined.MoreVert,stringResource(R.string.attach_more,item.title))
                }
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem({Text(stringResource(R.string.attach_unlink_action))},{menu=false;onUnlink()},Modifier.testTag("attachment-unlink:${item.document}"))
                }
            }
        }
    }
}

/** File-type tile; the short code is a visual mark only (the localized type is read by TalkBack). */
@Composable internal fun FileTile(kind:FileKind,available:Boolean) {
    val (container,content)=when {
        !available->MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.outline
        kind==FileKind.PDF->MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        kind==FileKind.IMAGE->NotesTheme.semantic.attention.container to NotesTheme.semantic.attention.onContainer
        else->MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(Modifier.size(48.dp),shape=MaterialTheme.shapes.medium,color=container,contentColor=content) {
        Box(Modifier.padding(bottom=6.dp),contentAlignment=Alignment.BottomCenter) {
            Text(when(kind) {FileKind.PDF->"PDF";FileKind.IMAGE->"IMG";FileKind.OTHER->"FILE"},style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.SemiBold)
        }
    }
}

@Composable private fun ConfirmDialog(defaultTitle:String,targetLabel:String,onConfirm:(String)->Unit,onCancel:()->Unit) {
    var title by remember(defaultTitle) {mutableStateOf(defaultTitle)}
    val valid=title.isNotBlank() && title.length<=256
    AlertDialog(onDismissRequest=onCancel,modifier=Modifier.testTag("attachments-confirm"),
        title={Text(stringResource(R.string.attach_confirm_title))},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.attach_confirm_target,targetLabel))
            OutlinedTextField(title,{title=it.take(256)},label={Text(stringResource(R.string.attach_confirm_name))},singleLine=true,
                modifier=Modifier.fillMaxWidth().testTag("attachments-name"))
        }},
        confirmButton={TextButton({onConfirm(title.trim())},enabled=valid,modifier=Modifier.testTag("attachments-confirm-attach")) {Text(stringResource(R.string.attach_confirm_action))}},
        dismissButton={TextButton(onCancel,modifier=Modifier.testTag("attachments-confirm-cancel")) {Text(stringResource(R.string.cancel))}})
}

/**
 * UI-10 file page (sample 07): back arrow, the preview on a white sheet inside a rounded area, a type chip and the title,
 * the record the file is linked to, and what the preview shows. Scrolls as one page. Shows only a page the controller
 * still owns; on revocation the controller has already replaced it with [Viewer.Closed], so no stale medical page stays on
 * screen. Nothing is claimed that was not read: no size, date, source or integrity line.
 */
@Composable internal fun MedicalAttachmentViewer(viewer:Viewer,onClose:()->Unit,modifier:Modifier=Modifier,targetLabel:String="",onPage:(Int)->Unit={}) {
    if(viewer==Viewer.Closed)return
    val showing=viewer as? Viewer.Showing
    var scale by remember(showing?.page) {mutableFloatStateOf(1f)}
    var offset by remember(showing?.page) {mutableStateOf(Offset.Zero)}
    val title=when(viewer) {is Viewer.Opening->viewer.title;is Viewer.Showing->viewer.title;is Viewer.Message->viewer.title;else->""}
    Surface(modifier.fillMaxSize().testTag("attachment-viewer"),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            IconButton(onClose,Modifier.padding(start=8.dp,top=8.dp).size(48.dp).testTag("attachment-viewer-close")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.viewer_close))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val height=(maxWidth*1.2f).coerceIn(240.dp,460.dp)
                    Surface(Modifier.fillMaxWidth().height(height),shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Box(Modifier.fillMaxSize().padding(20.dp),contentAlignment=Alignment.Center) {
                            when(viewer) {
                                is Viewer.Opening->Column(Modifier.semantics {liveRegion=LiveRegionMode.Polite},verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.viewer_opening));LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                                is Viewer.Message->Text(stringResource(when(viewer.reason) {Reason.PDF_NEEDS_ANDROID_9->R.string.viewer_pdf_unsupported;Reason.NO_PREVIEW_FOR_TYPE->R.string.viewer_no_preview;Reason.FAILED->R.string.viewer_failed}),
                                    Modifier.fillMaxWidth().testTag("attachment-viewer-message").semantics {liveRegion=LiveRegionMode.Polite})
                                is Viewer.Showing-> {
                                    val label=if(viewer.pdf && viewer.pageCount>1)stringResource(R.string.med05_page_label,viewer.title,viewer.pageIndex+1,viewer.pageCount)
                                        else stringResource(if(viewer.pdf)R.string.viewer_page else R.string.viewer_image,viewer.title)
                                    // Defensive: a recycled page is never drawn even if a frame races the revocation.
                                    if(!viewer.page.isRecycled)Surface(Modifier.aspectRatio(viewer.page.width.toFloat()/viewer.page.height.coerceAtLeast(1)),shape=MaterialTheme.shapes.extraSmall,
                                        color=androidx.compose.ui.graphics.Color.White,shadowElevation=2.dp) {
                                        ZoomablePage(viewer.page,label,scale,offset,{scale=it},{offset=it},{step->if(viewer.pdf && !viewer.busy)onPage(viewer.pageIndex+step)})
                                    }
                                    // The page number sits in the controls below once there is more than one page; the badge stays for a single page.
                                    if(viewer.pdf && viewer.pageCount==1)Surface(Modifier.align(Alignment.BottomEnd),shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.inverseSurface.copy(alpha=0.8f),
                                        contentColor=MaterialTheme.colorScheme.inverseOnSurface) {
                                        Text(stringResource(R.string.viewer_pdf_first_page),
                                            Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Viewer.Closed->{}
                            }
                        }
                    }
                }
                showing?.let {ViewerControls(it,scale,onPage,{scale=it;if(it<=1f)offset=Offset.Zero},{scale=1f;offset=Offset.Zero})}
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    (viewer as? Viewer.Showing)?.let {
                        val tone=MaterialTheme.colorScheme
                        StatusPill(stringResource(if(it.pdf)R.string.attach_kind_pdf else R.string.attach_kind_image),tone.secondaryContainer,tone.onSecondaryContainer)
                    }
                    Text(title,Modifier.semantics {heading()},style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,maxLines=3,overflow=TextOverflow.Ellipsis)
                }
                if(targetLabel.isNotBlank())LinkedRecord(targetLabel)
                (viewer as? Viewer.Showing)?.let {FileDetails(it.pdf,it.pageCount)}
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable private fun LinkedRecord(label:String) {
    Surface(Modifier.fillMaxWidth(),shape=Corners.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp).semantics(mergeDescendants=true) {}.testTag("attachment-viewer-linked"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Surface(Modifier.size(40.dp),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer) {
                Box(contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Link,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSecondaryContainer)}
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.viewer_linked_record),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label,style=MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable private fun FileDetails(pdf:Boolean,pageCount:Int=1) {
    val rows=listOf(stringResource(R.string.viewer_detail_type) to stringResource(if(pdf)R.string.attach_kind_pdf else R.string.attach_kind_image),
        stringResource(R.string.viewer_detail_preview) to (if(pdf && pageCount>1)stringResource(R.string.med05_preview_pages,pageCount) else stringResource(if(pdf)R.string.viewer_preview_first_page else R.string.viewer_preview_full)))
    Column(Modifier.testTag("attachment-viewer-details"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.viewer_details),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,modifier=Modifier.semantics {heading()})
        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
            rows.forEachIndexed {i,(name,value)->
                GroupedItem(i,rows.size) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp).semantics(mergeDescendants=true) {},verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text(name,Modifier.weight(0.4f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value,Modifier.weight(0.6f),style=MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** The page with pinch zoom, pan when zoomed, double tap to zoom and (at normal size) a sideways swipe for the next or previous page. */
@Composable private fun ZoomablePage(bitmap:Bitmap,label:String,scale:Float,offset:Offset,onScale:(Float)->Unit,onOffset:(Offset)->Unit,onSwipe:(Int)->Unit) {
    var size by remember {mutableStateOf(IntSize.Zero)}
    fun clamp(o:Offset,s:Float):Offset {
        val x=size.width*(s-1f)/2f;val y=size.height*(s-1f)/2f
        return Offset(o.x.coerceIn(-x,x),o.y.coerceIn(-y,y))
    }
    val currentScale by rememberUpdatedState(scale);val currentOffset by rememberUpdatedState(offset)
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged {size=it}
        .pointerInput(Unit) {detectTapGestures(onDoubleTap={if(currentScale>1.2f){onScale(1f);onOffset(Offset.Zero)}else onScale(DOUBLE_TAP_SCALE)})}
        .pointerInput(bitmap) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false)
                var swipe=0f;var multi=false
                do {
                    val event=awaitPointerEvent()
                    if(event.changes.count {it.pressed}>1)multi=true
                    val zoom=event.calculateZoom();val pan=event.calculatePan()
                    if(multi || currentScale>1.01f) {
                        val next=(currentScale*zoom).coerceIn(1f,MAX_SCALE)
                        onScale(next);onOffset(clamp(currentOffset+pan,next))
                        if(zoom!=1f || pan!=Offset.Zero)event.changes.forEach {if(it.positionChanged())it.consume()}
                    } else if(abs(pan.x)>abs(pan.y)) {
                        swipe+=pan.x;event.changes.forEach {if(it.positionChanged())it.consume()}
                    }
                } while(event.changes.any {it.pressed})
                if(!multi && currentScale<=1.01f && abs(swipe)>SWIPE_DISTANCE_DP*density)onSwipe(if(swipe<0)1 else -1)
            }
        }
        .semantics {contentDescription=label;role=Role.Image}.testTag("attachment-viewer-page")) {
        GuardedBitmap(bitmap,Modifier.fillMaxSize().graphicsLayer {scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y},crop=false)
    }
}

private const val MAX_SCALE=5f
private const val DOUBLE_TAP_SCALE=2.5f
private const val SWIPE_DISTANCE_DP=64f

/** Page buttons and zoom buttons: everything the gestures do can also be done with a tap (TalkBack, switch access). */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ViewerControls(viewer:Viewer.Showing,scale:Float,onPage:(Int)->Unit,onScale:(Float)->Unit,onReset:()->Unit) {
    Column(Modifier.testTag("attachment-viewer-controls"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp),itemVerticalAlignment=Alignment.CenterVertically) {
            if(viewer.pdf && viewer.pageCount>1) {
                IconButton({onPage(viewer.pageIndex-1)},Modifier.size(48.dp).testTag("attachment-viewer-prev"),enabled=!viewer.busy && viewer.pageIndex>0) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft,stringResource(R.string.med05_prev_page))
                }
                Text(stringResource(R.string.med05_page_of,viewer.pageIndex+1,viewer.pageCount),Modifier.testTag("attachment-viewer-page-number").semantics {liveRegion=LiveRegionMode.Polite},
                    style=MaterialTheme.typography.titleSmall)
                IconButton({onPage(viewer.pageIndex+1)},Modifier.size(48.dp).testTag("attachment-viewer-next"),enabled=!viewer.busy && viewer.pageIndex<viewer.pageCount-1) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,stringResource(R.string.med05_next_page))
                }
                Spacer(Modifier.width(12.dp))
            }
            val steps=listOf(1f,1.5f,2f,3f,5f)
            TextButton({onScale(steps.lastOrNull {it<scale-0.01f} ?: 1f)},Modifier.heightIn(min=48.dp).testTag("attachment-viewer-zoom-out"),enabled=scale>1.01f) {
                Text("−",style=MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(4.dp));Text(stringResource(R.string.med05_zoom_out))
            }
            TextButton({onScale(steps.firstOrNull {it>scale+0.01f} ?: MAX_SCALE)},Modifier.heightIn(min=48.dp).testTag("attachment-viewer-zoom-in"),enabled=scale<MAX_SCALE-0.01f) {
                Text("+",style=MaterialTheme.typography.titleMedium);Spacer(Modifier.width(4.dp));Text(stringResource(R.string.med05_zoom_in))
            }
            TextButton(onReset,Modifier.heightIn(min=48.dp).testTag("attachment-viewer-zoom-reset"),enabled=scale>1.01f) {Text(stringResource(R.string.med05_zoom_reset))}
        }
        if(viewer.pageFailed)Text(stringResource(R.string.med05_page_failed),Modifier.testTag("attachment-viewer-page-failed").semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.med05_zoom_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

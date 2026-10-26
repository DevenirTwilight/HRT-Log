package net.plainnotes.app.attachments.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.State as AttachmentsState
import net.plainnotes.app.ui.GroupedItem
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.StatusPill

/**
 * Attachments of one explicit record: list, add, confirm, status. Stateless; [MedicalAttachmentsController]
 * owns the work. Encryption details stay out of the UI. Not reachable from release navigation.
 */
@Composable internal fun MedicalAttachmentsSection(state:AttachmentsState,targetLabel:String,onAdd:()->Unit,onConfirm:(String)->Unit,onCancelImport:()->Unit,
    onCheckStatus:()->Unit,onDismissMessage:()->Unit,onRetry:()->Unit,onOpen:(Item)->Unit,modifier:Modifier=Modifier) {
    Column(modifier.fillMaxWidth().testTag("attachments"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.attach_section),style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics {heading()})
        when {
            state.ended->Notice(stringResource(R.string.attach_data_changed),"attachments-ended")
            state.revoked->Notice(stringResource(R.string.attach_hidden),"attachments-hidden")
            else-> {
                ImportStatus(state.import,onCheckStatus,onDismissMessage)
                when(val files=state.files) {
                    Files.Loading->LinearProgressIndicator(Modifier.fillMaxWidth().testTag("attachments-loading"))
                    Files.Failed->Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.attach_list_failed),color=MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onRetry,Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.attach_retry))}
                    }
                    is Files.Ready->if(files.items.isEmpty())
                        Text(stringResource(R.string.attach_empty),color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("attachments-empty"))
                    else Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {files.items.forEachIndexed {i,item->AttachmentRow(item,i,files.items.size,onOpen)}}
                }
                val saving=state.import==Import.Saving || state.import==Import.Checking
                Button(onAdd,Modifier.heightIn(min=48.dp).testTag("attachments-add"),enabled=!saving && state.import !is Import.Confirming) {
                    Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.attach_add))
                }
            }
        }
    }
    (state.import as? Import.Confirming)?.takeIf {!state.revoked}?.let {ConfirmDialog(it.title,targetLabel,onConfirm,onCancelImport)}
}

@Composable private fun Notice(text:String,tag:String) {
    Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag(tag).semantics {liveRegion=LiveRegionMode.Polite})
}

@Composable private fun ImportStatus(import:Import,onCheckStatus:()->Unit,onDismiss:()->Unit) {
    val polite=Modifier.testTag("attachments-status").semantics {liveRegion=LiveRegionMode.Polite}
    when(import) {
        Import.Saving,Import.Checking->Column(polite,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if(import==Import.Saving)R.string.attach_saving else R.string.attach_checking))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is Import.Saved->Message(stringResource(if(import.listStale)R.string.attach_saved_list_stale else R.string.attach_saved),polite,onDismiss)
        Import.NotSaved->Message(stringResource(R.string.attach_not_saved),polite,onDismiss)
        Import.Cancelled->Message(stringResource(R.string.attach_cancelled),polite,onDismiss)
        Import.Unknown->Column(polite,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.attach_unknown))
            OutlinedButton(onCheckStatus,Modifier.heightIn(min=48.dp).testTag("attachments-check")) {Text(stringResource(R.string.attach_check))}
        }
        Import.Idle,is Import.Confirming->{}
    }
}

@Composable private fun Message(text:String,modifier:Modifier,onDismiss:()->Unit) {
    Row(modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(text,Modifier.weight(1f))
        TextButton(onDismiss,Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.attach_dismiss))}
    }
}

/** Sample 06 (docs/design/claude-ui-2026-10-17): a tinted file tile, title, type line; unavailable files say so in grey. */
@Composable private fun AttachmentRow(item:Item,index:Int,count:Int,onOpen:(Item)->Unit) {
    val kind=stringResource(when(item.kind) {FileKind.PDF->R.string.attach_kind_pdf;FileKind.IMAGE->R.string.attach_kind_image;FileKind.OTHER->R.string.attach_kind_other})
    val status=stringResource(if(item.available)R.string.attach_status_saved else R.string.attach_status_unavailable)
    val description=stringResource(R.string.attach_item_description,item.title,kind,status)
    GroupedItem(index,count,onClick=if(item.available) ({onOpen(item)}) else null) {
        Row(Modifier.fillMaxWidth().heightIn(min=72.dp).padding(horizontal=16.dp,vertical=12.dp).testTag("attachment:${item.document}")
            .clearAndSetSemantics {contentDescription=description;if(item.available)role=Role.Button},
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            FileTile(item.kind,item.available)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(item.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(kind,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(!item.available)Text(status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** File-type tile; the short code is a visual mark only (the localized type is read by TalkBack). */
@Composable private fun FileTile(kind:FileKind,available:Boolean) {
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

/** Full-page preview after sample 07: back arrow, the page on a white sheet inside a rounded preview area, a type chip
 * and the title. Shows only a page the controller still owns; on revocation the controller has already replaced it
 * with [Viewer.Closed], so no stale medical page stays on screen. */
@Composable internal fun MedicalAttachmentViewer(viewer:Viewer,onClose:()->Unit,modifier:Modifier=Modifier) {
    if(viewer==Viewer.Closed)return
    val title=when(viewer) {is Viewer.Opening->viewer.title;is Viewer.Showing->viewer.title;is Viewer.Message->viewer.title;else->""}
    Surface(modifier.fillMaxSize().testTag("attachment-viewer"),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            IconButton(onClose,Modifier.size(48.dp).testTag("attachment-viewer-close")) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.viewer_close))}
            Surface(Modifier.fillMaxWidth().weight(1f),shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center) {
                    when(viewer) {
                        is Viewer.Opening->Column(Modifier.semantics {liveRegion=LiveRegionMode.Polite},verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.viewer_opening));LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        is Viewer.Message->Text(stringResource(when(viewer.reason) {Reason.PDF_NEEDS_ANDROID_9->R.string.viewer_pdf_unsupported;Reason.NO_PREVIEW_FOR_TYPE->R.string.viewer_no_preview;Reason.FAILED->R.string.viewer_failed}),
                            Modifier.fillMaxWidth().testTag("attachment-viewer-message").semantics {liveRegion=LiveRegionMode.Polite})
                        is Viewer.Showing-> {
                            val label=stringResource(if(viewer.pdf)R.string.viewer_page else R.string.viewer_image,viewer.title)
                            // Defensive: a recycled page is never drawn even if a frame races the revocation.
                            if(!viewer.page.isRecycled)Surface(Modifier.aspectRatio(viewer.page.width.toFloat()/viewer.page.height.coerceAtLeast(1)),shape=MaterialTheme.shapes.extraSmall,
                                color=androidx.compose.ui.graphics.Color.White,shadowElevation=2.dp) {
                                // Scales to the preview area; the page keeps its proportions.
                                Image(remember(viewer.page) {viewer.page.asImageBitmap()},label,Modifier.fillMaxSize().testTag("attachment-viewer-page"),contentScale=ContentScale.Fit)
                            }
                            if(viewer.pdf)Surface(Modifier.align(Alignment.BottomEnd),shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.inverseSurface.copy(alpha=0.8f),
                                contentColor=MaterialTheme.colorScheme.inverseOnSurface) {
                                Text(stringResource(R.string.viewer_pdf_first_page),Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium)
                            }
                        }
                        Viewer.Closed->{}
                    }
                }
            }
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                (viewer as? Viewer.Showing)?.let {
                    val tone=MaterialTheme.colorScheme
                    StatusPill(stringResource(if(it.pdf)R.string.attach_kind_pdf else R.string.attach_kind_image),tone.secondaryContainer,tone.onSecondaryContainer)
                }
                Text(title,Modifier.semantics {heading()},style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,maxLines=3,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

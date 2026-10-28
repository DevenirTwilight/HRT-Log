package net.plainnotes.app.attachments.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.FileTile
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.FileKind
import net.plainnotes.app.ui.Corners
import net.plainnotes.app.ui.GroupedItem
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.StatusPill

private fun FileCategory.label() = when (this) {
    FileCategory.LAB_REPORT -> R.string.lib_cat_lab; FileCategory.PRESCRIPTION -> R.string.lib_cat_rx; FileCategory.VISIT_NOTE -> R.string.lib_cat_note
    FileCategory.CERTIFICATE -> R.string.lib_cat_cert; FileCategory.IMAGING -> R.string.lib_cat_imaging; FileCategory.OTHER -> R.string.lib_cat_other
}
private fun FileKind.label() = when (this) { FileKind.PDF -> R.string.attach_kind_pdf; FileKind.IMAGE -> R.string.attach_kind_image; FileKind.OTHER -> R.string.attach_kind_other }

/**
 * Sample 06 as Compose: search, the add action, files still to sort, categories with counts, recent files and the full backup.
 * A control the backend cannot serve yet is drawn disabled with its reason; nothing is hidden and nothing pretends. Not
 * reachable from release navigation; it is exercised with synthetic data until the file library backend is accepted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun MedicalFileLibraryScreen(state: LibraryState, caps: LibraryCapabilities, onAdd: () -> Unit, onPrepare: () -> Unit, onOpen: (DocumentSummary) -> Unit,
    onUnsorted: () -> Unit, onCategory: (FileCategory) -> Unit, onShowAll: () -> Unit, onBackup: () -> Unit, onBin: () -> Unit, query: String = "", onQuery: (String) -> Unit = {},
    modifier: Modifier = Modifier, typeCounts:Map<FileKind,Int> = emptyMap(),typeFilter:FileKind?=null,onType:(FileKind?)->Unit={},busy:Boolean=false) {
    val c = MaterialTheme.colorScheme
    val narrow = LocalDensity.current.fontScale > 1.3f || androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
    LazyColumn(modifier.fillMaxSize().testTag("library"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("search") {
            TextField(query, onQuery, Modifier.fillMaxWidth().testTag("library-search"), enabled = caps.search, singleLine = true, shape = Corners.full,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(stringResource(if(caps.categories)R.string.lib_search_hint else R.string.med03_search_hint)) },
                supportingText = if (!caps.search) ({ Text(stringResource(R.string.lib_not_yet), Modifier.testTag("library-search-reason")) }) else null,
                colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
        }
        item("actions") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onAdd, Modifier.heightIn(min = 56.dp).testTag("library-add"), enabled = caps.standaloneImport && !busy) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lib_add))
                }
                FilledTonalButton(onPrepare, Modifier.heightIn(min = 56.dp).testTag("library-prepare")) { Text(stringResource(R.string.lib_prepare)) }
            }
            if (!caps.standaloneImport) Text(stringResource(R.string.lib_not_yet), Modifier.padding(top = 4.dp).testTag("library-add-reason"), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        if(typeCounts.isNotEmpty()) item("types") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.med03_types),style=MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(typeFilter==null,{onType(null)},{Text(stringResource(R.string.med03_all_types))},Modifier.heightIn(min=48.dp).testTag("library-type:ALL"))
                    FileKind.entries.filter {(typeCounts[it] ?: 0)>0}.forEach {kind->
                        FilterChip(typeFilter==kind,{onType(kind)},{Text(stringResource(kind.label())+" · "+typeCounts.getValue(kind))},Modifier.heightIn(min=48.dp).testTag("library-type:${kind.name}"))
                    }
                }
            }
        }
        if (state.unsorted() > 0) item("unsorted") {
            val tone = NotesTheme.semantic.attention
            Surface(onUnsorted, Modifier.fillMaxWidth().testTag("library-unsorted"), shape = Corners.card, color = tone.container, contentColor = tone.onContainer) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(Modifier.size(48.dp), shape = MaterialTheme.shapes.medium, color = tone.onContainer, contentColor = tone.container) {
                        Box(contentAlignment = Alignment.Center) { Text(state.unsorted().toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.lib_unsorted_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(pluralStringResource(R.plurals.lib_unsorted_body, state.unsorted(), state.unsorted()), style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                }
            }
        }
        if (caps.categories) item("categories") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lib_by_category), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                FileCategory.entries.chunked(if (narrow) 2 else 3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { cat ->
                            val n = state.categoryCounts[cat] ?: 0
                            Surface({ onCategory(cat) }, Modifier.weight(1f).heightIn(min = 88.dp).testTag("library-category:${cat.name}"), enabled = n > 0, shape = Corners.card, color = c.surfaceContainerLow) {
                                Column(Modifier.padding(16.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(n.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (n > 0) c.onSurface else c.outline)
                                    Text(stringResource(cat.label()), style = MaterialTheme.typography.bodyMedium, color = if (n > 0) c.onSurface else c.outline)
                                }
                            }
                        }
                        repeat((if (narrow) 2 else 3) - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item("recent-head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lib_recent), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TextButton(onShowAll, Modifier.heightIn(min = 48.dp).testTag("library-all")) { Text(stringResource(R.string.lib_all, state.total)) }
            }
        }
        if (state.documents.isEmpty()) item("empty") { Text(stringResource(R.string.lib_empty), Modifier.testTag("library-empty"), color = c.onSurfaceVariant) }
        else item("recent") {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                state.documents.forEachIndexed { i, d -> GroupedItem(i, state.documents.size, onClick = { onOpen(d) }, modifier = Modifier.testTag("library-doc:${d.document}")) { DocumentRow(d) } }
            }
        }
        item("backup") {
            Surface(onBackup, Modifier.fillMaxWidth().testTag("library-backup"), shape = Corners.card, color = c.surfaceContainerLow) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.VerifiedUser, null, tint = c.primary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.med_transfer_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.mh_backup_body), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                }
            }
        }
        item("footer") {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                TextButton(onUnsorted, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.lib_unlinked_files, state.unsorted())) }
                TextButton(onBin, Modifier.heightIn(min = 48.dp).testTag("library-bin"), enabled = caps.recycle) { Text(stringResource(R.string.lib_bin)) }
            }
        }
    }
}

private fun LibraryState.unsorted() = unlinked

@Composable private fun DocumentRow(d: DocumentSummary) {
    val c = MaterialTheme.colorScheme
    val kind = stringResource(d.kind.label())
    val link = when (val l = d.link) {
        LinkState.Unlinked -> stringResource(R.string.lib_not_linked)
        is LinkState.Linked -> pluralStringResource(R.plurals.lib_linked_n, l.count, l.count)
    }
    val line = listOfNotNull(d.category?.let { stringResource(it.label()) } ?: kind, d.addedLabel).joinToString(" · ")
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FileTile(d.kind, true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(d.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            Text(link, style = MaterialTheme.typography.bodySmall, color = if (d.link is LinkState.Linked) c.primary else c.outline)
        }
    }
}

/**
 * Sample 07 as Compose: the preview (supplied by the caller, always the existing capability-checked viewer), type and category,
 * a title, the linked records with their scope (whole report or one result) and only the details that really exist.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun MedicalFileDetailPage(detail: DocumentDetail, caps: LibraryCapabilities, preview: @Composable () -> Unit, onBack: () -> Unit, onViewFull: () -> Unit,
    onAddLink: () -> Unit, onUnlink: (DocumentLink) -> Unit, onOpenRecord: (RecordTarget) -> Unit, onRestore: () -> Unit, modifier: Modifier = Modifier,operationsEnabled:Boolean=true) {
    val c = MaterialTheme.colorScheme
    val s = detail.summary
    Scaffold(modifier.testTag("file-detail"), topBar = {
        TopAppBar(title = {}, navigationIcon = { IconButton(onBack, Modifier.size(48.dp).testTag("file-detail-back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.ui10_back)) } })
    }, containerColor = c.surface) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 420.dp), shape = MaterialTheme.shapes.extraLarge, color = c.surfaceContainerHigh) { preview() }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(stringResource(s.kind.label()), c.secondaryContainer, c.onSecondaryContainer)
                    s.category?.let { StatusPill(stringResource(it.label()), c.surfaceContainerHigh, c.onSurfaceVariant) }
                }
                Text(s.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            if (detail.inBin) Surface(Modifier.fillMaxWidth().testTag("file-detail-bin"), shape = Corners.card, color = c.errorContainer, contentColor = c.onErrorContainer) {
                Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lib_in_bin), Modifier.weight(1f))
                    TextButton(onRestore, Modifier.heightIn(min = 48.dp).testTag("file-detail-restore"), enabled = caps.recycle && operationsEnabled) { Text(stringResource(R.string.lib_restore)) }
                }
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (caps.bidirectionalLinks) Button(onAddLink, Modifier.heightIn(min = 56.dp).testTag("file-detail-add-link"),enabled=operationsEnabled) { Text(stringResource(R.string.lib_detail_add_link)) }
                OutlinedButton(onViewFull, Modifier.heightIn(min = 56.dp).testTag("file-detail-view")) { Text(stringResource(R.string.lib_view_full)) }
            }
            Surface(Modifier.fillMaxWidth().testTag("file-detail-links"), shape = Corners.card, color = c.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.lib_detail_linked), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    if (detail.linksKnown && detail.links.isEmpty()) Text(stringResource(R.string.lib_detail_no_links), Modifier.testTag("file-detail-no-links"), color = c.onSurfaceVariant)
                    detail.links.forEach { LinkRow(it, caps.bidirectionalLinks && operationsEnabled, { onOpenRecord(it.target) }, { onUnlink(it) }) }
                }
            }
            val facts = listOfNotNull(s.category?.let { R.string.lib_info_category to stringResource(it.label()) }, s.addedLabel?.let { R.string.lib_info_added to it },
                detail.sizeLabel?.let { R.string.lib_info_size to it }, R.string.viewer_detail_type to stringResource(s.kind.label()))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.viewer_details), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Column(Modifier.testTag("file-detail-facts"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    facts.forEachIndexed { i, (name, value) ->
                        GroupedItem(i, facts.size) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(stringResource(name), Modifier.weight(0.4f), color = c.onSurfaceVariant)
                                Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun LinkRow(link: DocumentLink, canRemove: Boolean, onOpen: () -> Unit, onUnlink: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val t = link.target
    val scope = when (t) { is RecordTarget.LabPanel -> stringResource(R.string.lib_scope_report); is RecordTarget.LabValue -> stringResource(R.string.lib_scope_item); is RecordTarget.Visit -> stringResource(R.string.mh_target_visit) }
    Surface(Modifier.fillMaxWidth(), shape = Corners.list, color = c.surfaceContainerLowest) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen).heightIn(min = 64.dp).padding(vertical = 8.dp).testTag("file-link:${t.label}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(40.dp), shape = androidx.compose.foundation.shape.CircleShape, color = c.secondaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(if (t is RecordTarget.Visit) Icons.AutoMirrored.Outlined.EventNote else Icons.Outlined.Science, null, Modifier.size(20.dp), tint = c.onSecondaryContainer) }
                }
                Column(Modifier.weight(1f)) {
                    Text(t.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(scope + (if (t is RecordTarget.LabPanel) " · " + pluralStringResource(R.plurals.mh_lab_count, t.valueCount, t.valueCount) else ""), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
            }
            if (canRemove) IconButton(onUnlink, Modifier.size(48.dp).testTag("file-unlink:${t.label}")) { Icon(Icons.Outlined.Close, stringResource(R.string.lib_unlink_cd, t.label)) }
            else Spacer(Modifier.width(12.dp))
        }
    }
}

/**
 * The file-first add flow, step two: the chosen file, an editable title, an optional category and one explicit link choice.
 * "Don't link yet" is a real choice (the file is kept without a record). A choice the backend cannot serve is disabled with a
 * reason. Linking to an existing record, a lab report or a new record always names the record it will be linked to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AddDocumentDetails(draft: ImportDraft, caps: LibraryCapabilities, onChange: (ImportDraft) -> Unit, onChoose: () -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit,
    modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val needsRecord = draft.choice == LinkChoice.EXISTING_VISIT || draft.choice == LinkChoice.EXISTING_RECORD
    val valid = draft.title.isNotBlank() && draft.title.length <= 256 && (!needsRecord || draft.chosen != null)
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("add-document"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.lib_import_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Surface(shape = Corners.list, color = c.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FileTile(draft.verifiedKind ?: FileKind.OTHER, true)
                Column { Text(stringResource(R.string.lib_import_file), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant); Text(draft.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        OutlinedTextField(draft.title, { onChange(draft.copy(title = it.take(256))) }, Modifier.fillMaxWidth().testTag("add-document-title"), label = { Text(stringResource(R.string.attach_confirm_name)) }, singleLine = true)
        if (caps.categories) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lib_import_category), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FileCategory.entries.forEach { cat -> FilterChip(draft.category == cat, { onChange(draft.copy(category = if (draft.category == cat) null else cat)) }, { Text(stringResource(cat.label())) },
                    Modifier.heightIn(min = 48.dp).testTag("add-document-category:${cat.name}")) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.selectableGroup()) {
            Text(stringResource(R.string.lib_import_link), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            LinkChoice.entries.forEach { choice ->
                val ok = caps.standaloneImport && (choice == LinkChoice.LATER || caps.bidirectionalLinks)
                val label = stringResource(when (choice) { LinkChoice.EXISTING_VISIT -> R.string.lib_link_visit; LinkChoice.EXISTING_RECORD -> R.string.lib_link_record
                    LinkChoice.NEW_RECORD -> R.string.lib_link_new; LinkChoice.LATER -> R.string.lib_link_later })
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(draft.choice == choice, enabled = ok, role = Role.RadioButton) { onChange(draft.copy(choice = choice, chosen = null)) }
                    .testTag("add-document-link:${choice.name}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(draft.choice == choice, null, enabled = ok)
                    Column {
                        Text(label, color = if (ok) c.onSurface else c.outline)
                        if (!ok) Text(stringResource(R.string.lib_import_needs), style = MaterialTheme.typography.bodySmall, color = c.outline)
                    }
                }
            }
            if (needsRecord) Row(Modifier.fillMaxWidth().padding(start = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(draft.chosen?.let { stringResource(R.string.lib_import_chosen, it.label) } ?: "", Modifier.weight(1f).testTag("add-document-chosen"))
                OutlinedButton(onChoose, Modifier.heightIn(min = 48.dp).testTag("add-document-choose")) { Text(stringResource(R.string.lib_import_choose)) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onConfirm, Modifier.heightIn(min = 56.dp).testTag("add-document-confirm"), enabled = valid && caps.standaloneImport) { Text(stringResource(R.string.attach_confirm_action)) }
            TextButton(onCancel, Modifier.heightIn(min = 56.dp).testTag("add-document-cancel")) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/**
 * The record-first flow's result. The record and the file are two separate facts: a saved record stays saved whatever happens
 * to the file, an unknown file outcome only offers a read-only check (never another write) and a known failure offers an
 * explicit new attempt.
 */
@Composable internal fun RecordFileOutcomeCard(recordLabel: String, outcome: RecordFileOutcome, onAddFile: () -> Unit, onLater: () -> Unit, onCheck: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val bad = outcome == RecordFileOutcome.NOT_SAVED
    Surface(modifier.fillMaxWidth().testTag("record-file-outcome"), shape = Corners.card, color = if (bad) c.errorContainer else c.secondaryContainer, contentColor = if (bad) c.onErrorContainer else c.onSecondaryContainer) {
        Column(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lib_record_saved, recordLabel), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(when (outcome) { RecordFileOutcome.NONE -> R.string.lib_record_offer; RecordFileOutcome.ADDED -> R.string.lib_record_file_added
                RecordFileOutcome.NOT_SAVED -> R.string.lib_record_file_failed; RecordFileOutcome.UNKNOWN -> R.string.lib_record_file_unknown }), Modifier.testTag("record-file-outcome-text"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (outcome) {
                    RecordFileOutcome.NONE -> { Button(onAddFile, Modifier.heightIn(min = 48.dp).testTag("record-file-add")) { Text(stringResource(R.string.attach_add)) }
                        TextButton(onLater, Modifier.heightIn(min = 48.dp).testTag("record-file-later")) { Text(stringResource(R.string.lib_record_later)) } }
                    RecordFileOutcome.NOT_SAVED -> { Button(onAddFile, Modifier.heightIn(min = 48.dp).testTag("record-file-add")) { Text(stringResource(R.string.lib_record_again)) }
                        TextButton(onLater, Modifier.heightIn(min = 48.dp).testTag("record-file-later")) { Text(stringResource(R.string.lib_record_later)) } }
                    RecordFileOutcome.UNKNOWN -> OutlinedButton(onCheck, Modifier.heightIn(min = 48.dp).testTag("record-file-check")) { Text(stringResource(R.string.attach_check)) }
                    RecordFileOutcome.ADDED -> TextButton(onLater, Modifier.heightIn(min = 48.dp).testTag("record-file-later")) { Text(stringResource(R.string.attach_dismiss)) }
                }
            }
        }
    }
}

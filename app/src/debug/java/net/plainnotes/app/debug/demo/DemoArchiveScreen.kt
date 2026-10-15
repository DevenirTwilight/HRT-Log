package net.plainnotes.app.debug.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.ThemeMode
import net.plainnotes.app.ui.formatDate

/** DEBUG BUILD ONLY launcher entry for the synthetic medical document library demo (MED-02B6). No real data, no
 * FLAG_SECURE (nothing private is shown, and screenshots are part of the evidence). */
class MedicalArchiveDemoActivity : ComponentActivity() {
    private val holder by viewModels<DemoHolder>()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent { NotesTheme(ThemeMode.SYSTEM) { Surface(Modifier.fillMaxSize()) { DemoArchiveApp(holder.repository) } } }
    }
}

/** Keeps the in-memory demo across rotation; it is gone when the demo task ends (never written to disk). */
class DemoHolder : androidx.lifecycle.ViewModel() { val repository = DemoRepository() }

@Composable fun DemoArchiveApp(repository: DemoRepository) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val documents by repository.documents.collectAsState()
    BackHandler(selected != null) { selected = null }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        DemoBanner()
        val doc = selected?.let { id -> documents.firstOrNull { it.uuid == id } }
        if (doc == null) DemoLibrary(documents, { repository.reset() }) { selected = it.uuid } else DemoDetail(doc, repository) { selected = null }
    }
}

/** Always visible: this is synthetic sample data, never the user's records. */
@Composable fun DemoBanner() {
    Text(stringResource(R.string.demo_banner), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 16.dp, vertical = 10.dp).testTag("demo-banner"))
}

@Composable fun categoryLabel(category: String?): String = stringResource(when (category) {
    null, DemoRepository.UNCATEGORISED -> R.string.demo_uncategorised
    "LAB_REPORT" -> R.string.demo_cat_lab_report; "IMAGING" -> R.string.demo_cat_imaging
    "PRESCRIPTION" -> R.string.demo_cat_prescription; "LETTER" -> R.string.demo_cat_letter
    else -> R.string.demo_cat_other
})

@Composable fun clinicalDateLabel(d: ClinicalDate): String = when (d.precision) {
    DatePrecision.DAY -> stringResource(R.string.demo_date_day, formatDate(java.time.LocalDate.of(d.year!!, d.month!!, d.day!!)))
    DatePrecision.MONTH -> stringResource(R.string.demo_date_month, d.text)
    DatePrecision.YEAR -> stringResource(R.string.demo_date_year, d.text)
    DatePrecision.UNKNOWN -> stringResource(R.string.demo_date_unknown)
}

@Composable fun statusLabel(s: DocumentStatus): String = stringResource(when (s) {
    DocumentStatus.METADATA_ONLY -> R.string.demo_status_metadata; DocumentStatus.TEST_FILE -> R.string.demo_status_test
    DocumentStatus.MISSING -> R.string.demo_status_missing; DocumentStatus.CORRUPT -> R.string.demo_status_corrupt
    DocumentStatus.QUARANTINED -> R.string.demo_status_quarantined
})

@Composable private fun DemoLibrary(documents: List<AttachmentSummary>, onReset: () -> Unit, onOpen: (AttachmentSummary) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(DemoTab.INBOX) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val labels = DemoFixtures.CATEGORIES.associateWith { categoryLabel(it) } + (null to categoryLabel(null))
    val visible = remember(documents, tab, query, category, labels) { DemoRepository.visible(documents, tab, query, category) { labels[it] ?: "" } }
    var confirmReset by remember { mutableStateOf(false) }
    Text(stringResource(R.string.demo_breadcrumb), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).semantics { heading() })
    if (confirmReset) AlertDialog({ confirmReset = false }, title = { Text(stringResource(R.string.demo_reset_title)) }, text = { Text(stringResource(R.string.demo_reset_body)) },
        confirmButton = { TextButton({ confirmReset = false; onReset() }, Modifier.testTag("demo-reset-confirm")) { Text(stringResource(R.string.demo_reset)) } },
        dismissButton = { TextButton({ confirmReset = false }) { Text(stringResource(R.string.demo_cancel)) } })
    ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp) {
        DemoTab.entries.forEach { t ->
            Tab(selected = tab == t, onClick = { tab = t; category = null }, modifier = Modifier.testTag("demo-tab-${t.name}"),
                text = { Text(stringResource(when (t) { DemoTab.INBOX -> R.string.demo_tab_inbox; DemoTab.ALL -> R.string.demo_tab_all
                    DemoTab.CATEGORIES -> R.string.demo_tab_categories; DemoTab.TRASH -> R.string.demo_tab_trash })) })
        }
    }
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("demo-search"),
        label = { Text(stringResource(R.string.demo_search)) }, singleLine = true)
    LazyColumn(Modifier.fillMaxSize().testTag("demo-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tab == DemoTab.CATEGORIES && category == null && query.isBlank()) {
            val counts = documents.filter { !it.trashed }.groupingBy { it.category ?: DemoRepository.UNCATEGORISED }.eachCount()
            items(DemoFixtures.CATEGORIES + DemoRepository.UNCATEGORISED, key = { "category:$it" }) { c ->
                ElevatedCard(Modifier.fillMaxWidth().testTag("demo-category:$c").clickable(role = Role.Button) { category = c }) {
                    Text(stringResource(R.string.demo_categories_count, categoryLabel(c), counts[c] ?: 0), Modifier.padding(16.dp))
                }
            }
        } else {
            if (category != null) item("category-header") {
                AssistChip(onClick = { category = null }, label = { Text(categoryLabel(category) + "  ✕") }, modifier = Modifier.testTag("demo-category-clear"))
            }
            if (visible.isEmpty()) item("empty") { Text(stringResource(R.string.demo_empty), Modifier.testTag("demo-empty")) }
            items(visible, key = { it.uuid }) { d -> DemoDocumentCard(d) { onOpen(d) } }
        }
        item("evidence") { DemoEvidenceCard() }
        item("reset") { TextButton({ confirmReset = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("demo-reset")) { Text(stringResource(R.string.demo_reset)) } }
    }
}

@Composable private fun DemoDocumentCard(d: AttachmentSummary, onOpen: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().testTag("demo-doc:${d.uuid}").clickable(role = Role.Button, onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d.title, style = MaterialTheme.typography.titleMedium)
            Text(categoryLabel(d.category) + " · " + if (d.links.isEmpty()) stringResource(R.string.demo_not_linked) else stringResource(R.string.demo_linked, d.links.size))
            Text(clinicalDateLabel(d.clinicalDate), style = MaterialTheme.typography.bodySmall)
            Text(statusLabel(d.status), style = MaterialTheme.typography.labelMedium,
                color = if (d.status.failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (d.inInbox) Text(stringResource(R.string.demo_inbox_state), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun DemoEvidenceCard() {
    OutlinedCard(Modifier.fillMaxWidth().testTag("demo-evidence")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.demo_evidence_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.demo_evidence_body), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun DemoDetail(d: AttachmentSummary, repository: DemoRepository, onBack: () -> Unit) {
    var dialog by rememberSaveable(d.uuid) { mutableStateOf<String?>(null) }
    when (dialog) {
        "edit" -> DemoEditDialog(d, repository) { dialog = null }
        "link" -> DemoLinkDialog(d, repository) { dialog = null }
        "correct" -> DemoCorrectionDialog(d, repository) { dialog = null }
        "delete1" -> AlertDialog({ dialog = null }, title = { Text(stringResource(R.string.demo_delete_title)) }, text = { Text(stringResource(R.string.demo_delete_body)) },
            confirmButton = { TextButton({ dialog = "delete2" }, Modifier.testTag("demo-delete-confirm1")) { Text(stringResource(R.string.demo_continue)) } },
            dismissButton = { TextButton({ dialog = null }, Modifier.testTag("demo-delete-cancel1")) { Text(stringResource(R.string.demo_cancel)) } })
        "delete2" -> AlertDialog({ dialog = null }, title = { Text(stringResource(R.string.demo_delete_title2)) }, text = { Text(stringResource(R.string.demo_delete_body2, d.title)) },
            confirmButton = { TextButton({ dialog = null; if (repository.deleteForever(d.uuid)) onBack() }, Modifier.testTag("demo-delete-confirm2"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.demo_delete_forever)) } },
            dismissButton = { TextButton({ dialog = null }, Modifier.testTag("demo-delete-cancel2")) { Text(stringResource(R.string.demo_cancel)) } })
    }
    LazyColumn(Modifier.fillMaxSize().testTag("demo-detail"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("back") { TextButton(onClick = onBack, modifier = Modifier.testTag("demo-back")) { Text("‹ " + stringResource(R.string.demo_back)) } }
        item("title") { Text(d.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }
        item("status") {
            val failure = d.status.failure != null
            Surface(color = if (failure) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().testTag("demo-status")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(statusLabel(d.status), style = MaterialTheme.typography.titleSmall)
                    if (d.status == DocumentStatus.TEST_FILE) Text(stringResource(R.string.demo_preview_test), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item("meta") { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(categoryLabel(d.category))
            Text(clinicalDateLabel(d.clinicalDate), modifier = Modifier.testTag("demo-clinical-date"))
            Text(stringResource(R.string.demo_imported, formatDate(d.importedOn)))
            Text(d.source.institution?.let { stringResource(R.string.demo_source, it) } ?: stringResource(R.string.demo_source_none))
            Text(stringResource(R.string.demo_source_hint), style = MaterialTheme.typography.bodySmall)
            d.note?.let { Text(stringResource(R.string.demo_note) + ": " + it) }
        } }
        item("actions") { DemoActions(d, repository) { dialog = it } }
        item("versions-title") { Text(stringResource(R.string.demo_versions), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
        items(d.versions.sortedByDescending { it.seq }, key = { it.uuid }) { v ->
            Column(Modifier.fillMaxWidth().testTag("demo-version:${v.uuid}")) {
                Text(stringResource(if (v.state == VersionState.CURRENT) R.string.demo_version_current else R.string.demo_version_retained, v.seq), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.demo_version_meta, formatDate(v.importedOn), v.sizeKiB) + " · " + statusLabel(v.status), style = MaterialTheme.typography.bodySmall)
                v.correctionNote?.let { Text(stringResource(R.string.demo_correction, it), style = MaterialTheme.typography.bodySmall) }
            }
        }
        item("links-title") { Text(stringResource(R.string.demo_links), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
        if (d.links.isEmpty()) item("links-none") { Text(stringResource(R.string.demo_links_none), Modifier.testTag("demo-links-none")) }
        items(d.links, key = { it }) { key -> val t = repository.target(key)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(targetLabel(t, key), Modifier.weight(1f).testTag("demo-link:$key"))
                if (!d.trashed) TextButton({ repository.unlink(d.uuid, key) }, Modifier.heightIn(min = 48.dp).testTag("demo-unlink:$key")) { Text(stringResource(R.string.demo_unlink)) }
            }
        }
    }
}

@Composable private fun targetLabel(t: LinkTarget?, key: String): String =
    (if (t?.kind == LinkKind.LAB) stringResource(R.string.demo_link_lab) else stringResource(R.string.demo_link_visit)) + " · " +
        (t?.label ?: key) + (t?.let { " · " + formatDate(it.date) } ?: "") + (if (t?.retired == true) " · " + stringResource(R.string.demo_link_retired) else "")

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DemoActions(d: AttachmentSummary, repository: DemoRepository, open: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("demo-actions")) {
        if (d.trashed) {
            Button({ repository.restore(d.uuid) }, Modifier.heightIn(min = 48.dp).testTag("demo-restore")) { Text(stringResource(R.string.demo_restore)) }
            OutlinedButton({ open("delete1") }, Modifier.heightIn(min = 48.dp).testTag("demo-delete"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.demo_delete_forever)) }
        } else {
            if (d.inInbox) Button({ repository.file(d.uuid) }, Modifier.heightIn(min = 48.dp).testTag("demo-file")) { Text(stringResource(R.string.demo_file)) }
            FilledTonalButton({ open("edit") }, Modifier.heightIn(min = 48.dp).testTag("demo-edit")) { Text(stringResource(R.string.demo_edit)) }
            FilledTonalButton({ open("link") }, Modifier.heightIn(min = 48.dp).testTag("demo-link-manage")) { Text(stringResource(R.string.demo_link_manage)) }
            OutlinedButton({ open("correct") }, Modifier.heightIn(min = 48.dp).testTag("demo-correct")) { Text(stringResource(R.string.demo_correct)) }
            TextButton({ repository.trash(d.uuid) }, Modifier.heightIn(min = 48.dp).testTag("demo-trash")) { Text(stringResource(R.string.demo_trash)) }
        }
    }
}

/** Live targets can be linked or unlinked; a retired dataset is listed but cannot be linked (same id, different record). */
@Composable private fun DemoLinkDialog(d: AttachmentSummary, repository: DemoRepository, onClose: () -> Unit) {
    val docs by repository.documents.collectAsState()
    val links = docs.firstOrNull { it.uuid == d.uuid }?.links.orEmpty()
    AlertDialog(onClose, title = { Text(stringResource(R.string.demo_link_manage)) }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp).testTag("demo-link-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("help") { Text(stringResource(R.string.demo_link_help), style = MaterialTheme.typography.bodySmall) }
            items(repository.targets, key = { it.key }) { t ->
                Column(Modifier.fillMaxWidth()) {
                    Text(targetLabel(t, t.key))
                    when {
                        t.retired -> Text(stringResource(R.string.demo_link_retired_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("demo-link-blocked:${t.key}"))
                        t.key in links -> TextButton({ repository.unlink(d.uuid, t.key) }, Modifier.heightIn(min = 48.dp).testTag("demo-link-toggle:${t.key}")) { Text(stringResource(R.string.demo_unlink)) }
                        else -> FilledTonalButton({ repository.link(d.uuid, t.key) }, Modifier.heightIn(min = 48.dp).testTag("demo-link-toggle:${t.key}")) { Text(stringResource(R.string.demo_link_add)) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClose, Modifier.testTag("demo-link-close")) { Text(stringResource(R.string.demo_done)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DemoEditDialog(d: AttachmentSummary, repository: DemoRepository, onClose: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(d.title) }
    var institution by rememberSaveable { mutableStateOf(d.source.institution.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(d.category) }
    var precision by rememberSaveable { mutableStateOf(d.clinicalDate.precision) }
    var year by rememberSaveable { mutableStateOf(d.clinicalDate.year?.toString().orEmpty()) }
    var month by rememberSaveable { mutableStateOf(d.clinicalDate.month?.toString().orEmpty()) }
    var day by rememberSaveable { mutableStateOf(d.clinicalDate.day?.toString().orEmpty()) }
    var note by rememberSaveable { mutableStateOf(d.note.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<DemoEditError?>(null) }
    AlertDialog({ onClose() }, title = { Text(stringResource(R.string.demo_edit)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("demo-edit-form"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(stringResource(editErrorText(it)), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("demo-edit-error")) }
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().testTag("demo-edit-title"), label = { Text(stringResource(R.string.demo_field_title)) })
            OutlinedTextField(institution, { institution = it }, Modifier.fillMaxWidth().testTag("demo-edit-institution"), label = { Text(stringResource(R.string.demo_field_institution)) })
            Text(stringResource(R.string.demo_field_category), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (DemoFixtures.CATEGORIES + listOf<String?>(null)).forEach { c ->
                    FilterChip(category == c, { category = c }, { Text(categoryLabel(c)) }, Modifier.testTag("demo-edit-cat:${c ?: DemoRepository.UNCATEGORISED}"))
                }
            }
            Text(stringResource(R.string.demo_field_precision), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePrecision.entries.forEach { p ->
                    FilterChip(precision == p, { precision = p }, { Text(stringResource(precisionLabel(p))) }, Modifier.testTag("demo-edit-prec:${p.name}"))
                }
            }
            if (precision != DatePrecision.UNKNOWN) OutlinedTextField(year, { year = it }, Modifier.fillMaxWidth().testTag("demo-edit-year"), label = { Text(stringResource(R.string.demo_field_year)) }, singleLine = true)
            if (precision == DatePrecision.MONTH || precision == DatePrecision.DAY) OutlinedTextField(month, { month = it }, Modifier.fillMaxWidth().testTag("demo-edit-month"), label = { Text(stringResource(R.string.demo_field_month)) }, singleLine = true)
            if (precision == DatePrecision.DAY) OutlinedTextField(day, { day = it }, Modifier.fillMaxWidth().testTag("demo-edit-day"), label = { Text(stringResource(R.string.demo_field_day)) }, singleLine = true)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth().testTag("demo-edit-note"), label = { Text(stringResource(R.string.demo_note)) }, minLines = 2)
        }
    }, confirmButton = { TextButton({ error = repository.edit(d.uuid, DemoEdit(title, institution, category, precision, year, month, day, note)); if (error == null) onClose() },
        Modifier.testTag("demo-edit-save")) { Text(stringResource(R.string.demo_save)) } },
        dismissButton = { TextButton(onClose, Modifier.testTag("demo-edit-cancel")) { Text(stringResource(R.string.demo_cancel)) } })
}

private fun editErrorText(e: DemoEditError) = when (e) {
    DemoEditError.TITLE_BLANK -> R.string.demo_err_title; DemoEditError.TITLE_TOO_LONG -> R.string.demo_err_title_long
    DemoEditError.YEAR -> R.string.demo_err_year; DemoEditError.MONTH -> R.string.demo_err_month; DemoEditError.DAY -> R.string.demo_err_day
    DemoEditError.FUTURE -> R.string.demo_err_future; DemoEditError.NOTE_TOO_LONG -> R.string.demo_err_note
}
private fun precisionLabel(p: DatePrecision) = when (p) {
    DatePrecision.DAY -> R.string.demo_prec_day; DatePrecision.MONTH -> R.string.demo_prec_month
    DatePrecision.YEAR -> R.string.demo_prec_year; DatePrecision.UNKNOWN -> R.string.demo_prec_unknown
}

@Composable private fun DemoCorrectionDialog(d: AttachmentSummary, repository: DemoRepository, onClose: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(onClose, title = { Text(stringResource(R.string.demo_correct)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.demo_correct_help, d.versions.maxOf { it.seq } + 1))
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth().testTag("demo-correct-note"), label = { Text(stringResource(R.string.demo_correct_note)) }, minLines = 2)
        }
    }, confirmButton = { TextButton({ if (repository.addCorrection(d.uuid, note)) onClose() }, Modifier.testTag("demo-correct-save"), enabled = note.isNotBlank()) {
        Text(stringResource(R.string.demo_save)) } },
        dismissButton = { TextButton(onClose, Modifier.testTag("demo-correct-cancel")) { Text(stringResource(R.string.demo_cancel)) } })
}

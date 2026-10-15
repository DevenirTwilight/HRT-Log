package net.plainnotes.app.debug.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    private val repository by lazy { DemoRepository() }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent { NotesTheme(ThemeMode.SYSTEM) { Surface(Modifier.fillMaxSize()) { DemoArchiveApp(repository) } } }
    }
}

@Composable fun DemoArchiveApp(repository: DemoRepository) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val documents by repository.documents.collectAsState()
    BackHandler(selected != null) { selected = null }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        DemoBanner()
        val doc = selected?.let { id -> documents.firstOrNull { it.uuid == id } }
        if (doc == null) DemoLibrary(documents) { selected = it.uuid } else DemoDetail(doc, repository) { selected = null }
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

@Composable private fun DemoLibrary(documents: List<AttachmentSummary>, onOpen: (AttachmentSummary) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(DemoTab.INBOX) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val labels = DemoFixtures.CATEGORIES.associateWith { categoryLabel(it) } + (null to categoryLabel(null))
    val visible = remember(documents, tab, query, category, labels) { DemoRepository.visible(documents, tab, query, category) { labels[it] ?: "" } }
    Text(stringResource(R.string.demo_breadcrumb), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).semantics { heading() })
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
            Text((if (t?.kind == LinkKind.LAB) stringResource(R.string.demo_link_lab) else stringResource(R.string.demo_link_visit)) + " · " +
                (t?.label ?: key) + (t?.let { " · " + formatDate(it.date) } ?: "") + (if (t?.retired == true) " · " + stringResource(R.string.demo_link_retired) else ""),
                Modifier.testTag("demo-link:$key"))
        }
    }
}

package net.plainnotes.app.debug.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/*
 * MED-02B6 / PHR-DEMO — DEBUG BUILD ONLY, SYNTHETIC DATA ONLY.
 * Presentation model for a future medical document library. Names follow the reviewed design (reports 39/55) so a
 * production backend can implement the same shapes later, but nothing here stores, encrypts or reads real files:
 * DemoRepository is an in-memory fixture, never the SQLCipher database, PNBAK1 or the attachment tables, and the
 * attachment feature flag stays off. The release build contains none of this (scripts/check_attachment_release_isolation.py).
 */

/** How precisely the user knows the clinical date. UNKNOWN is shown as unknown, never guessed from the import date. */
enum class DatePrecision { DAY, MONTH, YEAR, UNKNOWN }

data class ClinicalDate(val year: Int?, val month: Int?, val day: Int?, val precision: DatePrecision) {
    companion object {
        val UNKNOWN = ClinicalDate(null, null, null, DatePrecision.UNKNOWN)
        fun day(d: LocalDate) = ClinicalDate(d.year, d.monthValue, d.dayOfMonth, DatePrecision.DAY)
        fun month(y: Int, m: Int) = ClinicalDate(y, m, null, DatePrecision.MONTH)
        fun year(y: Int) = ClinicalDate(y, null, null, DatePrecision.YEAR)
    }
    /** Sortable/searchable text in the precision the user gave, e.g. 2026-09-10, 2026-09, 2025. Empty when unknown. */
    val text: String get() = when (precision) {
        DatePrecision.DAY -> "%04d-%02d-%02d".format(year, month, day)
        DatePrecision.MONTH -> "%04d-%02d".format(year, month)
        DatePrecision.YEAR -> "%04d".format(year)
        DatePrecision.UNKNOWN -> ""
    }
}

/** What the demo knows about the original bytes. Only TEST_FILE has (synthetic) content; the others never open. */
enum class DocumentStatus { METADATA_ONLY, TEST_FILE, MISSING, CORRUPT, QUARANTINED }

/** Why an original cannot be shown. Mirrors the production failure classes, without any real IO behind it. */
enum class StorageFailure { MISSING, CORRUPT, QUARANTINED }
val DocumentStatus.failure: StorageFailure? get() = when (this) {
    DocumentStatus.MISSING -> StorageFailure.MISSING; DocumentStatus.CORRUPT -> StorageFailure.CORRUPT
    DocumentStatus.QUARANTINED -> StorageFailure.QUARANTINED; else -> null
}

enum class VersionState { CURRENT, RETAINED }

/** One immutable version of an original. A correction adds a version; it never edits an existing one. */
data class VersionSummary(val uuid: String, val seq: Int, val state: VersionState, val importedOn: LocalDate, val sizeKiB: Int,
                          val status: DocumentStatus, val correctionNote: String?)

/** Where the user says the document came from. A user statement, not a verified signature or seal. */
data class SourceClaim(val institution: String?)

enum class LinkKind { VISIT, LAB }

/** An existing visit or lab value the user can link to. Identity is epoch + integer id: the same integer in another
 * epoch (a retired dataset) is a different record and is never linked automatically. */
data class LinkTarget(val kind: LinkKind, val epoch: String, val intId: Int, val label: String, val date: LocalDate, val retired: Boolean = false) {
    val key: String get() = "$kind:$epoch:$intId"
}

/** Inbox (not yet triaged), uncategorised (category == null) and unlinked (links empty) are three independent states. */
data class AttachmentSummary(val uuid: String, val title: String, val category: String?, val source: SourceClaim, val clinicalDate: ClinicalDate,
                             val note: String?, val versions: List<VersionSummary>, val links: List<String>, val inInbox: Boolean, val trashed: Boolean) {
    val current: VersionSummary get() = versions.first { it.state == VersionState.CURRENT }
    val status: DocumentStatus get() = current.status
    val importedOn: LocalDate get() = versions.minOf { it.importedOn }
}

enum class DemoTab { INBOX, ALL, CATEGORIES, TRASH }

/** Deterministic synthetic fixture: fixed UUIDs, dates and order so screenshots and tests are reproducible. */
object DemoFixtures {
    const val EPOCH_CURRENT = "epoch-2026"
    const val EPOCH_RETIRED = "epoch-2025-retired"
    val CATEGORIES = listOf("LAB_REPORT", "IMAGING", "PRESCRIPTION", "LETTER", "OTHER")

    val targets: List<LinkTarget> = listOf(
        LinkTarget(LinkKind.VISIT, EPOCH_CURRENT, 7, "Endocrinology follow-up (synthetic)", LocalDate.of(2026, 9, 12)),
        LinkTarget(LinkKind.LAB, EPOCH_CURRENT, 12, "Estradiol 412 pmol/L (synthetic)", LocalDate.of(2026, 9, 10)),
        LinkTarget(LinkKind.LAB, EPOCH_CURRENT, 13, "Testosterone 0.6 nmol/L (synthetic)", LocalDate.of(2026, 9, 10)),
        LinkTarget(LinkKind.VISIT, EPOCH_CURRENT, 8, "Primary care check-up (synthetic)", LocalDate.of(2026, 6, 3)),
        // Same integer id 12 as the current estradiol, but from a retired dataset: a different record, never auto-linked.
        LinkTarget(LinkKind.LAB, EPOCH_RETIRED, 12, "Estradiol 280 pmol/L (synthetic, retired dataset)", LocalDate.of(2025, 3, 2), retired = true),
    )

    private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)
    private fun v(doc: Int, seq: Int, state: VersionState, on: LocalDate, kib: Int, status: DocumentStatus = DocumentStatus.TEST_FILE, note: String? = null) =
        VersionSummary(uuid(doc * 100 + seq), seq, state, on, kib, status, note)

    fun documents(): List<AttachmentSummary> {
        val named = listOf(
            AttachmentSummary(uuid(1), "Blood test report — September (synthetic)", "LAB_REPORT", SourceClaim("Synthetic City Laboratory"),
                ClinicalDate.day(LocalDate.of(2026, 9, 10)), "Fictional sample used to demonstrate versions and links.",
                listOf(v(1, 1, VersionState.RETAINED, LocalDate.of(2026, 9, 11), 182, note = null),
                       v(1, 2, VersionState.CURRENT, LocalDate.of(2026, 9, 14), 185, note = "Lab re-issued the page with the reference range")),
                listOf("VISIT:$EPOCH_CURRENT:7", "LAB:$EPOCH_CURRENT:12", "LAB:$EPOCH_CURRENT:13"), inInbox = false, trashed = false),
            AttachmentSummary(uuid(2), "Photo of a clinic handout (synthetic)", null, SourceClaim(null), ClinicalDate.UNKNOWN, null,
                listOf(v(2, 1, VersionState.CURRENT, LocalDate.of(2026, 10, 2), 640)), emptyList(), inInbox = true, trashed = false),
            AttachmentSummary(uuid(3), "Prescription scan (synthetic)", "PRESCRIPTION", SourceClaim("Synthetic Health Centre"),
                ClinicalDate.year(2025), "Only the year is known.", listOf(v(3, 1, VersionState.CURRENT, LocalDate.of(2026, 1, 20), 310)),
                emptyList(), inInbox = true, trashed = false),
            AttachmentSummary(uuid(4), "Referral letter (synthetic)", "LETTER", SourceClaim("Synthetic Gender Clinic"),
                ClinicalDate.month(2026, 6), null, listOf(v(4, 1, VersionState.CURRENT, LocalDate.of(2026, 6, 20), 95)),
                listOf("VISIT:$EPOCH_CURRENT:8"), inInbox = false, trashed = true),
            AttachmentSummary(uuid(5), "Ultrasound summary — damaged copy (synthetic)", "IMAGING", SourceClaim("Synthetic Imaging"),
                ClinicalDate.day(LocalDate.of(2026, 5, 4)), null, listOf(v(5, 1, VersionState.CURRENT, LocalDate.of(2026, 5, 6), 2048, DocumentStatus.CORRUPT)),
                emptyList(), inInbox = false, trashed = false),
            AttachmentSummary(uuid(6), "Older lab printout — original missing (synthetic)", "LAB_REPORT", SourceClaim("Synthetic City Laboratory"),
                ClinicalDate.month(2025, 3), null, listOf(v(6, 1, VersionState.CURRENT, LocalDate.of(2025, 3, 10), 120, DocumentStatus.MISSING)),
                emptyList(), inInbox = false, trashed = false),
            AttachmentSummary(uuid(7), "Imported file held in quarantine (synthetic)", null, SourceClaim(null), ClinicalDate.UNKNOWN,
                "Kept apart: it did not pass the import checks.", listOf(v(7, 1, VersionState.CURRENT, LocalDate.of(2026, 10, 5), 77, DocumentStatus.QUARANTINED)),
                emptyList(), inInbox = true, trashed = false),
            AttachmentSummary(uuid(8), "Visit notes entered by hand (synthetic)", "OTHER", SourceClaim(null), ClinicalDate.day(LocalDate.of(2026, 9, 12)),
                "Metadata only: no original file was added.", listOf(v(8, 1, VersionState.CURRENT, LocalDate.of(2026, 9, 12), 0, DocumentStatus.METADATA_ONLY)),
                emptyList(), inInbox = false, trashed = false),
        )
        // Deterministic filler so the list has 20+ rows (paging / item reuse with stable keys).
        val filler = (9..24).map { n ->
            val cat = CATEGORIES[n % CATEGORIES.size]
            AttachmentSummary(uuid(n), "Sample document %02d (synthetic)".format(n), cat, SourceClaim("Synthetic Clinic ${('A' + n % 4)}"),
                ClinicalDate.day(LocalDate.of(2026, 1 + n % 9, 1 + n)), null, listOf(v(n, 1, VersionState.CURRENT, LocalDate.of(2026, 1 + n % 9, 2 + n), 50 + n)),
                emptyList(), inInbox = false, trashed = false)
        }
        return named + filler
    }
}

/** In-memory demo state only. A fresh instance is the fixture; nothing is persisted or shared with the real app data. */
class DemoRepository {
    private val state = MutableStateFlow(DemoFixtures.documents())
    val documents: StateFlow<List<AttachmentSummary>> = state
    val targets: List<LinkTarget> = DemoFixtures.targets
    fun target(key: String): LinkTarget? = targets.firstOrNull { it.key == key }
    fun document(uuid: String): AttachmentSummary? = state.value.firstOrNull { it.uuid == uuid }

    companion object {
        /** Tab and search filter. Search matches title, category, stated institution, clinical date text and import date. */
        fun visible(all: List<AttachmentSummary>, tab: DemoTab, query: String, category: String?, categoryLabel: (String?) -> String): List<AttachmentSummary> {
            val q = query.trim().lowercase()
            return all.filter { d ->
                when (tab) {
                    DemoTab.INBOX -> d.inInbox && !d.trashed
                    DemoTab.ALL -> !d.trashed
                    DemoTab.CATEGORIES -> !d.trashed && (category == null || d.category == category || (category == UNCATEGORISED && d.category == null))
                    DemoTab.TRASH -> d.trashed
                }
            }.filter { d ->
                q.isEmpty() || listOfNotNull(d.title, categoryLabel(d.category), d.source.institution, d.clinicalDate.text, d.importedOn.toString())
                    .any { it.lowercase().contains(q) }
            }.sortedWith(compareByDescending<AttachmentSummary> { it.importedOn }.thenBy { it.uuid })
        }
        const val UNCATEGORISED = "__uncategorised__"
    }
}

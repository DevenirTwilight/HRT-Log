package net.plainnotes.app.attachments.ui.library

import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.FileKind
import java.util.UUID

/**
 * UI-10 ⇄ MED-03 interface contract, as the types the file library screens consume. Nothing here stores, reads or decrypts a
 * file and nothing here is reachable from release navigation: the production hub offers only the explicit visit and
 * individual lab entrances. Every field that the backend cannot yet supply is nullable, and the UI draws a field only when it
 * is present (no date, institution or size is ever inferred from a file name or from the user's history).
 * See docs/medical-attachments/2026-10-27/ui10/INTERFACE_CONTRACT.md for who owns what and which operation needs what.
 */
internal enum class FileCategory { LAB_REPORT, PRESCRIPTION, VISIT_NOTE, CERTIFICATE, IMAGING, OTHER }

/** A record a file can be linked to. A lab panel is a different target from one of its values; they are never inferred from each other. */
internal sealed interface RecordTarget {
    val label: String
    data class Visit(val id: Long, override val label: String) : RecordTarget
    data class LabValue(val id: Long, override val label: String) : RecordTarget
    data class LabPanel(val id: Long, override val label: String, val valueCount: Int) : RecordTarget
}

internal sealed interface LinkState {
    data object Unlinked : LinkState
    data class Linked(val count: Int) : LinkState
}

internal data class DocumentSummary(val document: UUID, val title: String, val kind: FileKind, val link: LinkState,
    val category: FileCategory? = null, val addedLabel: String? = null,
    /** MED-04: what the user typed. [detailsKnown] says the backend supplies these, so an empty date is drawn as "date unknown" instead of being left out. */
    val dateLabel: String? = null, val institution: String? = null, val detailsKnown: Boolean = false)

internal data class DocumentLink(val target: RecordTarget)

internal data class DocumentDetail(val summary: DocumentSummary, val links: List<DocumentLink>, val sizeLabel: String? = null,
    /** True while the file is in the bin: it can be restored, nothing else. */
    val inBin: Boolean = false,val linksKnown:Boolean=true)

/** [toSort] is the number of files still without a category or a date (MED-04); null keeps the earlier meaning, files without a record. */
internal data class LibraryState(val documents: List<DocumentSummary>, val categoryCounts: Map<FileCategory, Int>, val unlinked: Int, val total: Int, val toSort: Int? = null)

/** What the backend can do today. Defaults are closed; only a verified production gateway advertises capabilities. A disabled control says why; it never pretends. */
internal data class LibraryCapabilities(val standaloneImport: Boolean = false, val search: Boolean = false, val categories: Boolean = false,
    val bidirectionalLinks: Boolean = false, val panelLinks: Boolean = false, val chooseExisting: Boolean = false, val recycle: Boolean = false)

internal enum class LinkChoice { EXISTING_VISIT, EXISTING_RECORD, NEW_RECORD, LATER }

/** The two-step add flow from the file side: the chosen file, an editable title, an optional category and one explicit link choice. */
internal data class ImportDraft(val fileName: String, val title: String, val category: FileCategory? = null, val choice: LinkChoice = LinkChoice.LATER,
    val chosen: RecordTarget? = null, val verifiedKind:FileKind?=null,
    val date: java.time.LocalDate? = null, val institution: String = "")

/** What happened to the file when a record was saved first. The two outcomes are reported separately and an unknown one is never retried. */
internal enum class RecordFileOutcome { NONE, ADDED, NOT_SAVED, UNKNOWN }

package net.plainnotes.app.reference

import net.plainnotes.app.data.UserReferenceProduct
import java.net.URI
import java.time.LocalDate

/** Read-only evidence, never a prescription, product binding, PK input or body-text cache. */
enum class DrugDocumentKind { RCP, NOTICE }
enum class DocumentEntryStatus { UNVERIFIED, VERIFIED, ARCHIVED, REMOVED }
enum class TextReuseStatus { UNKNOWN, ALLOWED }
data class DocumentEvidence(
    val kind:DrugDocumentKind,val cis:String,val cip:String?=null,val cipBelongsToCis:String?=null,
    val sourceNamespace:String,val sourceVersion:String,val sourceUrl:String,
    val retrievedAt:String?,val pageIdentityVerifiedAt:String?,val contentVerifiedAt:String?,
    val documentVersion:String?,val lastModifiedIfVerified:String?,val evidenceDigest:String?,
    val panelId:String?,val entryStatus:DocumentEntryStatus,val textReuse:TextReuseStatus=TextReuseStatus.UNKNOWN,
)
enum class DocumentBlock { PUBLICATION_OFF, NO_EVIDENCE, WRONG_IDENTITY, WRONG_VERSION, UNVERIFIED, ARCHIVED, REMOVED, INVALID_SOURCE }
sealed interface DocumentAvailability {
    data class Entry(val evidence:DocumentEvidence):DocumentAvailability
    data class Unavailable(val reason:DocumentBlock):DocumentAvailability
}

/** Publication is intentionally fixed OFF; no bundled official data, preference or remote toggle. */
internal object DrugDocuments {
    const val OFFICIAL_DISTRIBUTION_APPROVED=false
    fun forProduct(product:UserReferenceProduct,kind:DrugDocumentKind):DocumentAvailability =
        evaluate(product,kind,null,OFFICIAL_DISTRIBUTION_APPROVED)

    // Explicit gate argument is used by synthetic contract tests, never inferred from an HTTP status.
    internal fun evaluate(product:UserReferenceProduct,kind:DrugDocumentKind,evidence:DocumentEvidence?,publicationApproved:Boolean):DocumentAvailability {
        fun blocked(reason:DocumentBlock)=DocumentAvailability.Unavailable(reason)
        if(!publicationApproved)return blocked(DocumentBlock.PUBLICATION_OFF)
        val e=evidence ?: return blocked(DocumentBlock.NO_EVIDENCE)
        if(e.kind!=kind || e.cis!=product.cis || !Regex("[0-9]{8}").matches(e.cis) || e.cip!=product.cip ||
            (e.cip!=null && (!Regex("[0-9]{13}").matches(e.cip) || e.cipBelongsToCis!=e.cis)))return blocked(DocumentBlock.WRONG_IDENTITY)
        if(e.sourceVersion!=product.sourceVersion || e.sourceVersion.isBlank())return blocked(DocumentBlock.WRONG_VERSION)
        if(e.entryStatus==DocumentEntryStatus.ARCHIVED)return blocked(DocumentBlock.ARCHIVED)
        if(e.entryStatus==DocumentEntryStatus.REMOVED)return blocked(DocumentBlock.REMOVED)
        if(e.entryStatus!=DocumentEntryStatus.VERIFIED || e.pageIdentityVerifiedAt==null || e.retrievedAt==null ||
            e.evidenceDigest==null || !Regex("[0-9a-f]{64}").matches(e.evidenceDigest))return blocked(DocumentBlock.UNVERIFIED)
        val expectedPanel=if(kind==DrugDocumentKind.RCP)"tabpanel-rcp-panel" else "tabpanel-notice-panel"
        if(e.sourceNamespace!="BDPM_FR" || e.panelId!=expectedPanel || !PublicProductPage.valid(e.sourceUrl,e.cis))return blocked(DocumentBlock.INVALID_SOURCE)
        try {listOfNotNull(e.retrievedAt,e.pageIdentityVerifiedAt,e.contentVerifiedAt).forEach{LocalDate.parse(it)}}
        catch(_:RuntimeException){return blocked(DocumentBlock.INVALID_SOURCE)}
        // Entry presence never promotes content verification or grants text reuse.
        return DocumentAvailability.Entry(e)
    }
}

/** Validate precisely the public URI prepared by this app. Browser/site redirects remain outside app control. */
internal object PublicProductPage {
    const val HOST="base-donnees-publique.medicaments.gouv.fr"
    fun valid(value:String,cis:String?):Boolean {
        if(cis==null || !Regex("[0-9]{8}").matches(cis) || value.length>2048 || value.any{it.code<=32 || it.code>=127} || '%' in value)return false
        return runCatching {
            val uri=URI(value)
            uri.scheme=="https" && uri.rawAuthority==HOST && uri.host==HOST && uri.rawUserInfo==null && uri.port == -1 &&
                uri.rawQuery==null && uri.rawFragment==null && uri.rawPath=="/medicament/$cis/extrait" &&
                value=="https://$HOST/medicament/$cis/extrait"
        }.getOrDefault(false)
    }
}

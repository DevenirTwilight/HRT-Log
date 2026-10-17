package net.plainnotes.app

import net.plainnotes.app.data.UserReferenceProduct
import net.plainnotes.app.reference.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class DrugDocumentsTest {
    private val url="https://base-donnees-publique.medicaments.gouv.fr/medicament/00000001/extrait"
    private val product=UserReferenceProduct("SYNTHETIC source",cis="00000001",sourceUrl=url,sourceVersion="synthetic-v1",recordedOn="2026-10-17")
    private fun evidence(kind:DrugDocumentKind=DrugDocumentKind.RCP)=DocumentEvidence(kind,"00000001",sourceNamespace="BDPM_FR",sourceVersion="synthetic-v1",sourceUrl=url,
        retrievedAt="2026-10-17",pageIdentityVerifiedAt="2026-10-17",contentVerifiedAt=null,documentVersion=null,lastModifiedIfVerified=null,evidenceDigest="0".repeat(64),
        panelId=if(kind==DrugDocumentKind.RCP)"tabpanel-rcp-panel" else "tabpanel-notice-panel",entryStatus=DocumentEntryStatus.VERIFIED,
        fieldEvidence=DocumentFieldEvidence("SYNTHETIC page CIS field",if(kind==DrugDocumentKind.RCP)"tabpanel-rcp-panel" else "tabpanel-notice-panel","0".repeat(64),"2026-10-17"))
    @Test fun exactPublicProductUriDoesNotAcceptHealthParametersOrAuthorityTricks() {
        assertTrue(PublicProductPage.valid(url,"00000001"))
        val bad=listOf(url+"?dose=PRIVATE",url+"#rcp",url+"/",url.replace("https:","http:"),url.replace("https:","intent:"),
            url.replace(".fr/",".fr.evil.example/"),url.replace("https://","https://user@"),url.replace(".fr/",".fr:443/"),
            url.replace(".fr/",".fr:8443/"),url.replace("00000001","00000002"),url.replace("medicament","%6dedicament"),
            url.replace("medicament","%256dedicament"),url.replace("medicament/","medicament/../medicament/"),url.replace("medicaments","médicaments"),
            " "+url,url+"\n",url.replace("https://","https://<"),url.replace("https:","HTTPS:"))
        bad.forEach{assertFalse(it,PublicProductPage.valid(it,"00000001"))}
        assertFalse(PublicProductPage.valid(url,null));assertFalse(PublicProductPage.valid(url,"000000001"))
    }
    @Test fun distributionOffCannotBeOverriddenByVerifiedLookingEvidenceOrBodyDates() {
        assertFalse(DrugDocuments.OFFICIAL_DISTRIBUTION_APPROVED)
        DrugDocumentKind.entries.forEach{kind->
            assertEquals(DocumentBlock.PUBLICATION_OFF,(DrugDocuments.forProduct(product,kind) as DocumentAvailability.Unavailable).reason)
            assertEquals(DocumentBlock.PUBLICATION_OFF,(DrugDocuments.evaluate(product,kind,evidence(kind).copy(contentVerifiedAt="2026-10-17",textReuse=TextReuseStatus.ALLOWED),false) as DocumentAvailability.Unavailable).reason)
        }
    }
    @Test fun pagePresenceNeverPromotesBodyVerificationOrTextRights() {
        val state=DrugDocuments.evaluate(product,DrugDocumentKind.RCP,evidence(),true) as DocumentAvailability.Entry
        assertNull(state.evidence.contentVerifiedAt);assertNull(state.evidence.documentVersion);assertEquals(TextReuseStatus.UNKNOWN,state.evidence.textReuse)
        assertNull(evidence().lastModifiedIfVerified)
    }
    @Test fun identitiesKindsPanelsVersionsAndPackagingCannotBeMerged() {
        val e=evidence()
        listOf(e.copy(cis="00000002"),e.copy(kind=DrugDocumentKind.NOTICE),e.copy(panelId="tabpanel-notice-panel"),
            e.copy(sourceVersion="synthetic-v2"),e.copy(cip="0000000000001",cipBelongsToCis="00000002"),
            e.copy(sourceNamespace="USER_ENTERED"),e.copy(sourceUrl=url+"?private=1"),e.copy(pageIdentityVerifiedAt="2026-02-30"),
            e.copy(evidenceDigest="missing"),e.copy(fieldEvidence=null),e.copy(fieldEvidence=e.fieldEvidence!!.copy(entryLocator="wrong-document")),e.copy(pageIdentityVerifiedAt=null),e.copy(entryStatus=DocumentEntryStatus.UNVERIFIED),
            e.copy(entryStatus=DocumentEntryStatus.ARCHIVED),e.copy(entryStatus=DocumentEntryStatus.REMOVED)).forEach {
            assertTrue(it.toString(),DrugDocuments.evaluate(product,DrugDocumentKind.RCP,it,true) is DocumentAvailability.Unavailable)
        }
        val packed=product.copy(cip="0000000000001")
        assertTrue(DrugDocuments.evaluate(packed,DrugDocumentKind.RCP,e.copy(cip=packed.cip,cipBelongsToCis=packed.cis),true) is DocumentAvailability.Entry)
        assertTrue(DrugDocuments.evaluate(product,DrugDocumentKind.NOTICE,evidence(DrugDocumentKind.NOTICE),true) is DocumentAvailability.Entry)
        assertTrue(DrugDocuments.evaluate(product.copy(sourceVersion="new"),DrugDocumentKind.RCP,e,true) is DocumentAvailability.Unavailable)
        assertEquals(e,evidence()) // Looking up a new version did not mutate the frozen old source.
    }
    @Test fun lockForegroundPrivateAndGenerationAllParticipateInNavigationAdmission() {
        val state=DocumentNavigationState(true,true,true,false,7,7,false,false,false)
        assertTrue(state.allows(7))
        listOf(state.copy(initialized=false),state.copy(foreground=false),state.copy(contentVisible=false),state.copy(locked=true),
            state.copy(ownerGeneration=8),state.copy(currentGeneration=8),state.copy(privateTarget=true),state.copy(disguised=true)).forEach{assertFalse(it.toString(),it.allows(7))}
        assertFalse(state.allows(6));assertTrue(state.copy(disguised=true,primaryAuthorized=true).allows(7))
        assertFalse(state.copy(disguised=true,primaryAuthorized=true,currentGeneration=8).allows(7))
    }
    @Test fun newResourceFileHasAllFourLanguagesAndMatchingPlaceholders() {
        fun read(locale:String):Map<String,List<String>> {
            val xml=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$locale/drug_documents.xml"))
            val entries=xml.getElementsByTagName("string")
            return (0 until entries.length).associate {i->val node=entries.item(i);node.attributes.getNamedItem("name").nodeValue to Regex("%[0-9]+\\$[sd]").findAll(node.textContent).map{it.value}.toList()}
        }
        val base=read("values");assertEquals(11,base.size)
        listOf("values-fr","values-zh","values-b+zh+Hant").forEach{assertEquals(it,base,read(it))}
    }
}

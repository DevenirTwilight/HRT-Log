package net.plainnotes.app.data
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ReferencePayloadTest {
    private fun product()=UserReferenceProduct("SYNTHETIC nonofficial product",listOf("compound A","compound B"),sourceVersion="user-v1",recordedOn="2026-10-16")
    private fun rejects(block:()->Unit) {try {block();fail("Invalid source accepted")} catch(_:IllegalArgumentException){} catch(_:IllegalStateException){} catch(_:java.time.DateTimeException){} catch(_:org.json.JSONException){} }
    @Test fun canonicalOrderingAndHashAreIndependentOfObjectInsertionOrder() {
        val a=JSONObject().put("b","0.125").put("a",JSONObject().put("z",1).put("a","µg"))
        val b=JSONObject().put("a",JSONObject().put("a","µg").put("z",1)).put("b","0.125")
        assertEquals(ReferencePayload.canonical(a),ReferencePayload.canonical(b))
        assertEquals(ReferencePayload.sha(ReferencePayload.canonical(a)),ReferencePayload.sha(ReferencePayload.canonical(b)))
        assertNotEquals(ReferencePayload.sha("0.125"),ReferencePayload.sha("0.126"))
    }
    @Test fun userEnteredFactsNeverClaimOfficialValidationOrEquivalentIngredients() {
        val json=ReferencePayload.userProduct(product())
        assertEquals("USER_ENTERED",json.getString("namespace"));assertEquals("UNVERIFIED",json.getString("verification"))
        assertEquals(2,json.getJSONArray("ingredients").length())
        assertEquals(product(),ReferencePayload.readProduct(json))
        json.put("verification","VERIFIED_OFFICIAL_DOWNLOAD");rejects {ReferencePayload.validateProduct(json)}
    }
    @Test fun floatingPointAndExcessiveDepthOrTextCannotEnterCanonicalSnapshots() {
        rejects {ReferencePayload.canonical(JSONObject().put("quantity",0.1))}
        rejects {ReferencePayload.canonical(JSONObject().put("quantity",Double.NaN))}
        rejects {ReferencePayload.canonical(JSONObject().put("text","a".repeat(ReferencePayload.MAX_TEXT+1)))}
        var deep=JSONObject().put("value",1);repeat(17) {deep=JSONObject().put("child",deep)}
        rejects {ReferencePayload.canonical(deep)}
    }
    @Test fun nullAndStringsWithUnicodeOrQuotesRoundtripWithoutChangingIdentity() {
        val value=product().copy(name="SYNTHETIC œstrogène µg \"A\"\n片",form=null)
        val canonical=ReferencePayload.canonical(ReferencePayload.userProduct(value))
        assertEquals(value,ReferencePayload.readProduct(JSONObject(canonical)))
    }
    @Test fun cipCannotExistWithoutCisAndIdentifiersRemainStrings() {
        rejects {ReferencePayload.userProduct(product().copy(cip="0000000000001"))}
        val json=ReferencePayload.userProduct(product().copy(cis="00000001",cip="0000000000001"))
        assertEquals("00000001",json.getString("cis"));assertEquals("0000000000001",json.getString("cip"))
        rejects {ReferencePayload.userProduct(product().copy(cis="1"))}
        rejects {ReferencePayload.userProduct(product().copy(cis="00000001",cip="1"))}
    }
    @Test fun suppliedProductLinkMustMatchExactIdentityAndCannotCarryUserQueryOrForeignAuthority() {
        val base=product().copy(cis="00000001",sourceUrl="https://base-donnees-publique.medicaments.gouv.fr/medicament/00000001/extrait")
        assertEquals(base,ReferencePayload.readProduct(ReferencePayload.userProduct(base)))
        listOf("http://base-donnees-publique.medicaments.gouv.fr/medicament/00000001/extrait",
            "https://base-donnees-publique.medicaments.gouv.fr.example.org/medicament/00000001/extrait",
            "https://base-donnees-publique.medicaments.gouv.fr/medicament/00000002/extrait",
            base.sourceUrl+"?record=SYNTHETIC",base.sourceUrl+"#tracking",
            "https://name@base-donnees-publique.medicaments.gouv.fr/medicament/00000001/extrait").forEach {url->rejects {ReferencePayload.userProduct(base.copy(sourceUrl=url))}}
    }
    @Test fun missingVersionInvalidDateAndUnreviewedClinicalFieldsAreRejected() {
        rejects {ReferencePayload.userProduct(product().copy(sourceVersion=""))}
        rejects {ReferencePayload.userProduct(product().copy(recordedOn="2026-02-30"))}
        rejects {ReferencePayload.validateProduct(ReferencePayload.userProduct(product()).put("recommended_dose","SYNTHETIC forbidden field"))}
    }
}

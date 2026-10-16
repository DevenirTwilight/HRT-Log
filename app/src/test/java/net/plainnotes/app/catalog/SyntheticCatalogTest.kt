package net.plainnotes.app.catalog

import net.plainnotes.app.debug.catalog.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

internal fun fixtureText() = File("src/debug/assets/ansm02-synthetic.json").readText()
internal fun fixture() = SyntheticCatalog.decode(fixtureText())

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class SyntheticCatalogTest {
    private fun rejected(change: (JSONObject) -> Unit) {
        val json = JSONObject(fixtureText()); change(json)
        assertThrows(IllegalArgumentException::class.java) { SyntheticCatalog.decode(json.toString()) }
    }
    @Test fun identitiesAndParentRelationsAreUnique() {
        val c = fixture()
        assertEquals(8, c.products.size)
        assertEquals(8, c.products.map { it.cis }.distinct().size)
        assertEquals(2, c.products.count { it.name == c.products.first().name })
        assertEquals(2, c.products.first().presentations.size)
        c.products.forEach { p -> p.presentations.forEach { assertEquals(p.cis, it.cis) } }
        rejected { root -> root.getJSONArray("products").getJSONObject(1).put("cis", "SYNTH-CIS-1").put("presentations", org.json.JSONArray()) }
        rejected { root -> root.getJSONArray("products").getJSONObject(0).getJSONArray("presentations").getJSONObject(1).put("cip", "SYNTH-CIP-11") }
        rejected { root -> root.getJSONArray("products").getJSONObject(0).getJSONArray("presentations").getJSONObject(0).put("cis", "SYNTH-CIS-2") }
    }
    @Test fun realOrUnversionedDataCannotEnterMock() {
        rejected { it.put("kind", "OFFICIAL") }
        rejected { it.put("version", "fr-bdpm-2026-10-15.1") }
        rejected { it.getJSONArray("products").getJSONObject(0).put("cis", "65281285") }
        rejected { it.getJSONArray("products").getJSONObject(0).put("url", "https://example.invalid") }
    }
    @Test fun searchIgnoresCaseAndAccentsButKeepsSeparateProducts() {
        val c = fixture()
        assertEquals(setOf("SYNTH-CIS-1", "SYNTH-CIS-2", "SYNTH-CIS-3", "SYNTH-CIS-4", "SYNTH-CIS-7", "SYNTH-CIS-8"), c.search(CatalogFilter("  ESTRADIOL   ")).map { it.cis }.toSet())
        assertEquals(6, c.search(CatalogFilter("estradiol")).size)
        assertEquals(c.search(CatalogFilter("Progestérone")), c.search(CatalogFilter("PROGESTERONE")))
        assertEquals("oe", catalogSearchKey("Œ"))
        assertEquals("1 mg", c.products.first().strength)
        assertEquals("0,04 g/100 g", c.products[2].strength)
        assertTrue(c.search(CatalogFilter("Progynova")).isEmpty()) // a real brand is never presented as the fixture
    }
    @Test fun strengthFormAndRouteFiltersIntersect() {
        val c = fixture()
        assertEquals(listOf("SYNTH-CIS-1"), c.search(CatalogFilter("estradiol", "comprimé", "1 mg", "orale")).map { it.cis })
        assertEquals(listOf("SYNTH-CIS-8"), c.search(CatalogFilter(form = "capsule", strength = "1 mg")).map { it.cis })
        assertEquals(listOf("SYNTH-CIS-5"), c.search(CatalogFilter(route = "vaginale")).map { it.cis })
        assertTrue(c.search(CatalogFilter(form = "gel", route = "orale")).isEmpty())
    }
    @Test fun multipleIngredientsKeepFractionsAndUnknownsWithoutCalculatingDose() {
        val c = fixture(); val p = c.products[5]
        assertEquals(listOf("SA", "FT", "SA"), p.ingredients.map { it.role })
        assertEquals(listOf("1", "1", "2"), p.ingredients.map { it.group })
        assertEquals(listOf("1 mg", "0,8 mg", "5 mg"), p.ingredients.map { it.strength })
        assertNull(c.products[6].strength)
        assertTrue(c.products[4].presentations.isEmpty())
        assertTrue(c.products[6].presentations.single().label.contains("10 mL"))
        assertTrue(c.products[2].presentations.single().label.contains("40 pressions"))
        assertEquals(listOf("orale", "vaginale"), c.products[4].routes)
        assertEquals("25 µg/24 h", c.products[3].strength)
        assertEquals("2 mg", c.products[3].ingredients.single().strength)
        assertEquals("100 g de gel", c.products[2].ingredients.single().denominator)
    }
    @Test fun archivedAndUnknownStatesAreNotDroppedOrInvented() {
        val c = fixture()
        assertEquals(Registration.ARCHIVED, c.search(CatalogFilter("progestérone")).single().registration)
        assertEquals(Marketing.STOPPED_DECLARED, c.products[4].marketing)
        assertEquals(Registration.UNVERIFIED, c.products[5].registration)
        assertEquals(DocumentEntry.UNVERIFIED, c.products[5].rcp)
        assertEquals(DocumentEntry.MISSING, c.products[5].notice)
        rejected { it.getJSONArray("products").getJSONObject(0).put("registration", "AVAILABLE") }
    }
    @Test fun chooseIsExplicitIdempotentAndNeverChangesManualEntries() {
        val c = fixture(); val before = MockCatalogDraft("Non-France manual name", "0,5 tablette")
        val result = before.choose(c, "SYNTH-CIS-1", "SYNTH-CIP-11")
        assertEquals(before.name, result.name); assertEquals(before.doseText, result.doseText)
        assertNull(before.selection)
        assertEquals(result, result.choose(c, "SYNTH-CIS-1", "SYNTH-CIP-11"))
        assertEquals("synthetic-v1", result.selection!!.version)
        assertNull(result.copy(selection = null).selection)
        assertThrows(IllegalArgumentException::class.java) { before.choose(c, "SYNTH-CIS-2", "SYNTH-CIP-11") }
        val updated = c.copy(version = "synthetic-v2", products = c.products.map { it.copy(name = "Changed later") })
        assertEquals("synthetic-v1", result.selection!!.version)
        assertNotEquals(updated.products.first().name, result.selection!!.name)
    }
    @Test fun malformedMissingAndConflictingInputFailsClosed() {
        assertThrows(Exception::class.java) { SyntheticCatalog.decode("not JSON") }
        assertThrows(Exception::class.java) { SyntheticCatalog.decode("{}") }
        assertThrows(Exception::class.java) { SyntheticCatalog.decode(" ".repeat(128_001)) }
        rejected { it.getJSONArray("products").getJSONObject(0).put("name", " ") }
        rejected { it.getJSONArray("products").getJSONObject(0).getJSONArray("ingredients").getJSONObject(0).put("role", "ACTIVE_EQUIVALENT") }
        val json = JSONObject(fixtureText())
        json.getJSONArray("products").getJSONObject(0).remove("name")
        assertThrows(Exception::class.java) { SyntheticCatalog.decode(json.toString()) }
    }

    @Test fun emptyCatalogueIsAValidManualFallback() {
        val root = JSONObject(fixtureText()).put("products", org.json.JSONArray())
        assertTrue(SyntheticCatalog.decode(root.toString()).search(CatalogFilter()).isEmpty())
    }
}

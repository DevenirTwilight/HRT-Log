package net.plainnotes.app.ui11

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.*
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.reference.MedicationReferenceController
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * UI-11 (REQUIREMENTS §159): the product reference linking page, real Room and the real bundled BDPM records (the same two
 * PROVAMES products the existing flow contract uses); only the medication is synthetic. Four languages at 320 dp / 2× font
 * (dark) and 411 dp / 1× (light). Shots are written when UI11_SHOTS=<label> is set.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class Ui11LinkTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr").flatMap { l -> listOf(arrayOf<Any>(l, 320, 2f, true), arrayOf<Any>(l, 411, 1f, false)) }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: NotesDatabase; private lateinit var repo: NotesRepository; private lateinit var controller: MedicationReferenceController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val mode get() = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private fun idle() = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    @Before fun setup() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font)
        db = Room.inMemoryDatabaseBuilder(ui.activity, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo = NotesRepository(object : DatabaseAccess(ui.activity) { override fun get(space: Space) = db }); controller = MedicationReferenceController(repo, scope)
    }
    @After fun cleanup() { scope.cancel(); db.close() }

    private suspend fun medication() = repo.saveMedication(MedicationEntity(name = "SYNTHETIC linked medication", molecule = "OTHER", unit = "TABLET", dose_per_intake = 0.5,
        container_capacity = 28.0, soon_alert_minutes = 0, late_after_minutes = 60, site_rotation = false, notifications_on = false, active = true, sort_order = 0),
        "OTHER", net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS, 1, listOf(java.time.LocalTime.NOON), emptySet(), java.time.Instant.now().minusSeconds(86400))
    private fun count(table: String) = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }
    private fun shot(name: String) {
        val dir = System.getenv("UI11_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        ui.waitForIdle()
        val view = requireNotNull(org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull { it.isShowing }?.window).decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
        File("build/ui11-shots/$dir").apply { mkdirs() }.resolve("${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun scrollTo(tag: String) = ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag(tag))
    private fun click(tag: String) { scrollTo(tag); ui.onNodeWithTag(tag).performClick() }
    private fun search(text: String) {
        scrollTo("bdpm-search"); ui.onNodeWithTag("bdpm-search").performTextReplacement(text); click("bdpm-search-go")
        ui.waitUntil(30000) { idle(); ui.onAllNodesWithTag("bdpm-result:65421374").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun show(name: String? = "SYNTHETIC linked medication") {
        ui.setContent { NotesTheme(mode) { MedicationReferenceDialog(controller, { name }) {} } }
    }
    /** The same snapshot the real flow would save, written through the existing repository API (no dialogs). */
    private fun seedOfficial(id: Long) = runBlocking {
        val catalogue = BdpmCatalogue(ui.activity)
        val product = catalogue.search("PROVAMES").entries.first { it.cis == "65421374" }
        repo.associateInitialMedication(id, java.util.UUID.randomUUID().toString(), catalogue.snapshot(product, product.packages.first()), repo.referencePreviewEpoch())
    }
    private fun seedUserEntered(id: Long) = runBlocking {
        repo.associateInitialMedication(id, java.util.UUID.randomUUID().toString(),
            UserReferenceProduct("SYNTHETIC user product", listOf("SYNTHETIC ingredient"), "2 mg", "tablet", listOf("oral"), sourceVersion = "user-v1", recordedOn = "2026-10-20"), repo.referencePreviewEpoch())
    }
    /** Robolectric never idles a nested AlertDialog at 411 dp; dialog flows run at 320 dp only (as in the UI-10 tests). */
    private fun dialogsOnlyAtSmallWidth() = org.junit.Assume.assumeTrue(width == 320)
    private fun linkOfficialPackage() {
        search("PROVAMES"); click("bdpm-result:65421374"); click("bdpm-package:3400935479341")
        ui.waitUntil(30000) { idle(); ui.onAllNodesWithTag("reference-select").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("reference-select").performClick(); ui.onNodeWithTag("reference-confirm").performClick()
        ui.waitUntil(15000) { idle(); controller.state.value.let { it.saved || it.failed } }
        assertTrue(controller.state.value.saved)
    }

    @Test fun topBarHasTheBackArrowTheMedicationNameAndTheBoundaryCard() {
        val id = runBlocking { medication() }; runBlocking { controller.open(id)!!.join() }
        var closed = 0
        ui.setContent { NotesTheme(mode) { MedicationReferenceDialog(controller, { "SYNTHETIC linked medication" }) { closed++ } } }
        val back = Ui11CenterTest.BACK.getValue(locale)
        ui.onNodeWithTag("reference-close").assert(hasContentDescription(back)).assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        ui.onAllNodesWithText(s(R.string.cancel)).assertCountEquals(0)
        ui.onNodeWithText(s(R.string.ref2_page_title)).assertExists()
        ui.onNodeWithTag("reference-subtitle").assert(hasText("SYNTHETIC linked medication"))
        ui.onNodeWithTag("reference-boundary").assert(hasAnyDescendant(hasText(s(R.string.ref2_boundary_lead)))).assert(hasAnyDescendant(hasText(s(R.string.ref2_boundary))))
        ui.onAllNodesWithTag("reference-current").assertCountEquals(0)
        shot("link_none")
        ui.onNodeWithTag("reference-close").performClick()
        assertEquals(1, closed)
        assertEquals(1, count("medication")); assertEquals(0, count("reference_event"))
    }

    @Test fun segmentedTabsAreTabsAndSwitchBetweenTheCatalogueAndMyEntries() {
        val id = runBlocking { medication() }; runBlocking { controller.open(id)!!.join() }
        show()
        scrollTo("reference-official-tab")
        ui.onNodeWithTag("reference-official-tab").assert(hasText(s(R.string.ref2_tab_catalogue))).assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)).assertHeightIsAtLeast(48.dp)
        ui.onNodeWithTag("reference-saved-tab").assert(hasText(s(R.string.ref2_tab_saved))).assertIsNotSelected()
        ui.onNodeWithTag("bdpm-panel").assertExists()
        ui.onNodeWithTag("reference-saved-tab").performClick()
        ui.onNodeWithTag("reference-saved-tab").assertIsSelected(); ui.onNodeWithTag("reference-official-tab").assertIsNotSelected()
        ui.onAllNodesWithTag("bdpm-panel").assertCountEquals(0)
        for (tag in listOf("reference-search", "reference-form-filter", "reference-strength-filter", "reference-route-filter")) { scrollTo(tag); ui.onNodeWithTag(tag).assertExists() }
        scrollTo("reference-official-tab"); ui.onNodeWithTag("reference-official-tab").performClick(); ui.onNodeWithTag("bdpm-panel").assertExists()
    }

    @Test fun catalogueResultsExpandOneAtATimeAndPackagesAreRadioCards() {
        val id = runBlocking { medication() }; runBlocking { controller.open(id)!!.join() }
        show()
        search("PROVAMES")
        ui.onNodeWithTag("bdpm-status").assert(hasText(s(R.string.bdpm_status_line, "2", "2026-10-22")))
        for (cis in listOf("65281285", "65421374")) { scrollTo("bdpm-result:$cis"); ui.onNodeWithTag("bdpm-result:$cis").assert(hasText(s(R.string.ref2_tag_official))).assert(hasText("CIS $cis")) }
        ui.onAllNodesWithTag("bdpm-expanded:65281285").assertCountEquals(0); ui.onAllNodesWithTag("bdpm-expanded:65421374").assertCountEquals(0)
        click("bdpm-result:65281285"); scrollTo("bdpm-expanded:65281285"); ui.onNodeWithTag("bdpm-expanded:65281285").assertExists()
        click("bdpm-result:65421374")
        ui.onAllNodesWithTag("bdpm-expanded:65281285").assertCountEquals(0)
        scrollTo("bdpm-expanded:65421374"); ui.onNodeWithTag("bdpm-expanded:65421374").assertExists()
        // The primary button waits for a package; choosing one keeps the original preview -> confirm flow.
        scrollTo("bdpm-link"); ui.onNodeWithTag("bdpm-link").assertIsNotEnabled()
        scrollTo("bdpm-package:3400935479341")
        ui.onNodeWithTag("bdpm-package:3400935479341").assertIsNotSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton))
        shot("link_catalogue_expanded")
        scrollTo("bdpm-product-only"); ui.onNodeWithTag("bdpm-product-only").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        dialogsOnlyAtSmallWidth()
        scrollTo("bdpm-package:3400935479341")
        ui.onNodeWithTag("bdpm-package:3400935479341").performClick()
        ui.waitUntil(30000) { idle(); ui.onAllNodesWithTag("reference-select").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("reference-confirm-cancel").assertDoesNotExist()
        ui.onNodeWithText(s(R.string.cancel)).performClick()
        scrollTo("bdpm-package:3400935479341"); ui.onNodeWithTag("bdpm-package:3400935479341").assertIsSelected()
        scrollTo("bdpm-link"); ui.onNodeWithTag("bdpm-link").assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        ui.waitUntil(30000) { idle(); ui.onAllNodesWithTag("reference-select").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(s(R.string.cancel)).performClick()
        assertEquals("nothing was written by browsing or previewing", 0, count("reference_event"))
    }

    @Test fun officialLinkShowsTheCurrentLinkCard() {
        val id = runBlocking { medication() }; seedOfficial(id); runBlocking { controller.open(id)!!.join() }
        show()
        scrollTo("reference-current")
        ui.onNodeWithTag("reference-source-tag").assert(hasAnyDescendant(hasText(s(R.string.ref2_tag_official)))).assert(SemanticsMatcher.expectValue(DrugTagToneKey, "official"))
        ui.onNodeWithTag("reference-package-tag").assert(hasAnyDescendant(hasText(s(R.string.ref2_tag_package))))
        ui.onNodeWithTag("reference-current").assert(hasAnyDescendant(hasText("65421374"))).assert(hasAnyDescendant(hasText("3400935479341")))
            .assert(hasAnyDescendant(hasText(s(R.string.ref2_label_cis)))).assert(hasAnyDescendant(hasText(s(R.string.ref2_label_package))))
        ui.onNodeWithTag("reference-view").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        ui.onNodeWithTag("reference-unlink").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        shot("link_official")
        // The read-only details toggle with the button.
        scrollTo("reference-details"); ui.onNodeWithTag("reference-details").assertExists()
        scrollTo("reference-view"); ui.onNodeWithTag("reference-view").performClick(); ui.onAllNodesWithTag("reference-details").assertCountEquals(0)
        ui.onNodeWithTag("reference-view").performClick(); scrollTo("reference-details"); ui.onNodeWithTag("reference-details").assertExists()
        assertEquals(DrugLinkStatus.OFFICIAL, drugLinkStatus(runBlocking { repo.medicationReferenceState(id) }.reference))
        assertEquals(mapOf(id to DrugLinkStatus.OFFICIAL), runBlocking { controller.links(listOf(id)) }.mapValues { drugLinkStatus(it.value) })
    }

    @Test fun unlinkingStillAsksFirstAndNeverTouchesTheMedication() {
        dialogsOnlyAtSmallWidth()
        val id = runBlocking { medication() }; seedOfficial(id); runBlocking { controller.open(id)!!.join() }
        val before = runBlocking { repo.medications() }
        show()
        click("reference-unlink")
        ui.onNodeWithTag("reference-confirm").assertExists()
        ui.onNodeWithTag("reference-confirm-cancel").performClick()
        assertNotNull(runBlocking { repo.medicationReferenceState(id) }.reference!!.product)
        click("reference-unlink"); ui.onNodeWithTag("reference-confirm").performClick()
        ui.waitUntil(15000) { idle(); controller.state.value.let { it.saved || it.failed } }
        assertNull(runBlocking { repo.medicationReferenceState(id) }.reference!!.product)
        ui.onAllNodesWithTag("reference-current").assertCountEquals(0)
        assertEquals("a saved change bumps the counter the centre refreshes on", 1, controller.changes.value)
        assertEquals("link and unlink never touch the medication", before, runBlocking { repo.medications() })
    }

    @Test fun officialLinkThroughTheRealFlowStillReachesTheSameCard() {
        dialogsOnlyAtSmallWidth()
        val id = runBlocking { medication() }; runBlocking { controller.open(id)!!.join() }
        show(); linkOfficialPackage()
        scrollTo("reference-current")
        ui.onNodeWithTag("reference-source-tag").assert(hasAnyDescendant(hasText(s(R.string.ref2_tag_official))))
        assertEquals(DrugLinkStatus.OFFICIAL, drugLinkStatus(runBlocking { repo.medicationReferenceState(id) }.reference))
    }

    @Test fun userEnteredLinkIsTaggedUnverifiedAndStatusReadsAsUserEntered() {
        val id = runBlocking { medication() }; seedUserEntered(id); runBlocking { controller.open(id)!!.join() }
        show()
        scrollTo("reference-current")
        ui.onNodeWithTag("reference-source-tag").assert(hasAnyDescendant(hasText(s(R.string.ref2_tag_user)))).assert(SemanticsMatcher.expectValue(DrugTagToneKey, "user"))
        ui.onAllNodesWithTag("reference-package-tag").assertCountEquals(0)
        assertEquals(DrugLinkStatus.USER_ENTERED, drugLinkStatus(runBlocking { repo.medicationReferenceState(id) }.reference))
        // The saved library lists it with the same tag and its entry date.
        click("reference-saved-tab"); scrollTo("reference-search-go"); ui.onNodeWithTag("reference-search-go").performClick()
        ui.waitUntil(10000) { idle(); ui.onAllNodesWithTag("reference-loading").fetchSemanticsNodes().isEmpty() }
        val uuid = runBlocking { repo.savedReferenceProducts() }.entries.single().snapshotUuid
        scrollTo("reference-result:$uuid")
        val recorded = runBlocking { repo.savedReferenceProducts() }.entries.single().product.recordedOn
        ui.onNodeWithTag("reference-result:$uuid").assert(hasText(s(R.string.ref2_tag_user))).assert(hasText(s(R.string.ref2_entered_on, recorded)))
            .assertHasClickAction().assertHeightIsAtLeast(56.dp)
        shot("link_mine")
    }

    @Test fun manualEntryCreatesAUserEnteredLinkAndBumpsTheChangeCounter() {
        dialogsOnlyAtSmallWidth()
        val id = runBlocking { medication() }; runBlocking { controller.open(id)!!.join() }
        show()
        click("reference-manual")
        ui.onNodeWithTag("reference-name").performTextInput("SYNTHETIC user product"); ui.onNodeWithTag("reference-preview").performClick()
        ui.onNodeWithTag("reference-select").performClick(); ui.onNodeWithTag("reference-confirm").performClick()
        ui.waitUntil(15000) { idle(); controller.state.value.let { it.saved || it.failed } }
        assertTrue(controller.state.value.saved)
        scrollTo("reference-current")
        ui.onNodeWithTag("reference-source-tag").assert(hasAnyDescendant(hasText(s(R.string.ref2_tag_user))))
        assertEquals(1, controller.changes.value)
    }

    @Test fun manualEntryButtonHasItsUnverifiedCaption() {
        val id = runBlocking { medication() }; runBlocking { controller.open(id)!!.join() }
        show()
        scrollTo("reference-manual")
        ui.onNodeWithTag("reference-manual").assert(hasText(s(R.string.ref2_manual_button))).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        ui.onNodeWithText(s(R.string.ref2_manual_caption)).assertExists()
    }
}

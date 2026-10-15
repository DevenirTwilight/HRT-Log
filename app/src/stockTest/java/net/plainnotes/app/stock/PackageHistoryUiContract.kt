package net.plainnotes.app.stock

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import net.plainnotes.app.NotesState
import net.plainnotes.app.R
import net.plainnotes.app.data.ContainerEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.ui.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/** The same real Compose interaction contract runs on native Robolectric and API35, using synthetic rows only. */
@OptIn(ExperimentalTestApi::class)
abstract class PackageHistoryUiContract(private val language: String) {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val med = MedicationEntity(1, "Synthetic", "E2", "ORAL", "MG", 2.0, 30.0,
        site_rotation = false, notifications_on = true, active = true, sort_order = 0)
    private lateinit var state: LazyListState
    private lateinit var strings: Context
    private var originalConfiguration: Configuration? = null
    private fun box(id: Long, status: String = "EMPTY") = ContainerEntity(id, 1, 30.0, 0.0,
        if (status == "EMPTY" || status == "VOIDED") 30.0 else if (status == "SEALED") 0.0 else 2.0,
        if (status == "SEALED") null else "2026-01-01", status, source_note = "Synthetic package $id")
    private fun list() = ui.onNodeWithTag("packages-1")
    private fun index(key: Any) = list().fetchSemanticsNode().config[SemanticsProperties.IndexForKey](key)
    private fun composed() = ui.onAllNodes(SemanticsMatcher("a composed package row") {
        val tag = it.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
        tag.startsWith("package-row-") || tag.startsWith("voided-package-")
    }).fetchSemanticsNodes().size
    private fun scroll(key: Any) { list().performScrollToKey(key) }
    private fun toggle(group: String) { scroll("header-$group"); ui.onNodeWithTag("package-toggle-$group").performClick(); ui.waitForIdle() }
    private fun more(group: String) {
        scroll("more-$group")
        val before = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        ui.onNodeWithTag("package-more-$group").assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals("Adding a batch must retain the viewport", before, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset) }
    }
    @Suppress("DEPRECATION")
    private fun content(body: @Composable () -> Unit) {
        val configuration = Configuration(ui.activity.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(language))); fontScale = 2f; screenWidthDp = 320
        }
        // Dialog windows use their host View's Android resources, not only the parent LocalContext.
        originalConfiguration = Configuration(ui.activity.resources.configuration)
        ui.activity.resources.updateConfiguration(configuration, ui.activity.resources.displayMetrics)
        strings = ui.activity.createConfigurationContext(configuration)
        val density = ui.activity.resources.displayMetrics.widthPixels / 320f
        ui.setContent { CompositionLocalProvider(LocalContext provides strings, LocalConfiguration provides configuration,
            LocalDensity provides Density(density, 2f)) {
            NotesTheme(ThemeMode.LIGHT) { Surface(Modifier.fillMaxSize().testTag("pagination-viewport")) { body() } }
        } }
        assertEquals(320f, ui.onNodeWithTag("pagination-viewport").fetchSemanticsNode().size.width / density, 1f)
    }
    @After @Suppress("DEPRECATION") fun restoreHostConfiguration() {
        originalConfiguration?.let { ui.activity.resources.updateConfiguration(it, ui.activity.resources.displayMetrics) }
    }
    @Composable private fun Manage(boxes: List<ContainerEntity>, onDismiss: () -> Unit = {},
        onInfo: (ContainerEntity) -> Unit = {}, onUndo: (ContainerEntity) -> Unit = {}, onRestore: (ContainerEntity) -> Unit = {}) {
        state = rememberLazyListState()
        PackagesDialog(med, boxes, emptySet(), onDismiss, onInfo, voided = boxes.filter { it.state == "VOIDED" },
            onRestore = onRestore, listState = state, onUndo = onUndo)
    }
    private fun evidence(kind: String, total: Int, shown: Int, nodes: Int) {
        val report = JSONObject().put("kind", kind).put("locale", language).put("width_dp", 320).put("font_scale", 2)
            .put("total", total).put("shown", shown).put("composed_rows", nodes).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("measurement", "Attached package semantics nodes at an idle viewport; not CPU/FPS or Release performance")
        println("B4E_PACKAGE_HISTORY_JSON=$report")
        Log.i("B4EPackages", "B4E_PACKAGE_HISTORY_JSON=$report")
    }
    private fun assertKeys(total: Int, shown: Int) {
        val lookup = list().fetchSemanticsNode().config[SemanticsProperties.IndexForKey]
        for (id in 1L..total.toLong()) assertEquals("ID $id must appear once in deterministic descending order",
            if (total - id < shown) (total - id + 1).toInt() else -1, lookup(id))
    }

    @Test fun firstPagesAt1_20_21_100_500StartCollapsedAndAreCapped() {
        var count by mutableIntStateOf(1)
        content { key(count) { Manage((1L..count.toLong()).map { box(it) }) } }
        for (n in listOf(1, 20, 21, 100, 500)) {
            ui.runOnIdle { count = n }
            list().assert(hasScrollToIndexAction())
            assertEquals(2, state.layoutInfo.totalItemsCount); assertEquals(0, composed())
            toggle("EMPTY")
            val shown = minOf(n, 20)
            assertEquals(2 + shown + if (n > shown) 1 else 0, state.layoutInfo.totalItemsCount)
            assertKeys(n, shown)
            val nodes = composed(); assertTrue(nodes in 1..shown); if (n >= 20) assertTrue(nodes < 20)
            evidence("first-page", n, shown, nodes)
            if (n > 20) {
                more("EMPTY")
                val appended = minOf(n, 40)
                assertKeys(n, appended)
                assertEquals(2 + appended + if (n > appended) 1 else 0, state.layoutInfo.totalItemsCount)
                if (n == 21) assertEquals(-1, index("more-EMPTY"))
                if (n == 100) {
                    while (index("more-EMPTY") >= 0) more("EMPTY")
                    assertKeys(100, 100); assertEquals(102, state.layoutInfo.totalItemsCount)
                    evidence("completed-history", 100, 100, composed())
                }
            } else assertEquals(-1, index("more-EMPTY"))
        }
    }

    @Test fun all500SameDateRowsHaveStableOrderAndNeverComposeTheWholeHistory() {
        content { Manage((1L..500L).map { box(it) }.shuffled(Random(84))) }
        toggle("EMPTY")
        var maximum = 0
        for (shown in 20..500 step 20) {
            assertKeys(500, shown)
            val nodes = composed(); maximum = maxOf(maximum, nodes)
            assertTrue("500 rows must remain virtualized", nodes in 1..19)
            evidence("all-batches", 500, shown, nodes)
            if (shown < 500) more("EMPTY") else assertEquals(-1, index("more-EMPTY"))
        }
        assertEquals(502, state.layoutInfo.totalItemsCount)
        scroll(1L); ui.onNodeWithTag("package-row-1").assertExists()
        assertTrue(maximum < 20)
        evidence("completed-history", 500, 500, maximum)
    }

    @Test fun activeGroupsAlsoHaveIndependentTwentyRowBatches() {
        val boxes = (1L..100L).map { box(it, "IN_USE") } + (1001L..1021L).map { box(it, "SEALED") }
        content { Manage(boxes) }
        assertEquals(45, state.layoutInfo.totalItemsCount)
        assertEquals(-1, index(80L)); assertEquals(-1, index(1001L))
        more("SEALED"); assertTrue(index(1001L) >= 0); assertEquals(-1, index("more-SEALED"))
        assertEquals(-1, index(80L))
        more("IN_USE"); assertTrue(index(80L) >= 0)
    }

    @Test fun mixedGroupsAnd100VoidedRowsRegroupImmediatelyWithoutStaleActions() {
        var boxes by mutableStateOf(listOf(box(1, "IN_USE")) + (2L..4L).map { box(it, "SEALED") } +
            (1001L..1200L).map { box(it) } + (2001L..2020L).map { box(it, "DISCARDED") } +
            (3001L..3100L).map { box(it, "VOIDED") })
        var undone: Long? = null; var restored: Long? = null
        content { Manage(boxes, onUndo = { chosen -> undone = chosen.id; boxes = boxes.map { if (it.id == chosen.id) it.copy(state = "VOIDED") else it } },
            onRestore = { chosen -> restored = chosen.id; boxes = boxes.map { if (it.id == chosen.id) it.copy(state = "EMPTY") else it } }) }
        assertEquals(9, state.layoutInfo.totalItemsCount)
        assertEquals(-1, index(3100L)); assertEquals(-1, index(1200L))
        toggle("EMPTY"); toggle("DISCARDED")
        assertTrue(index(1200L) >= 0); assertTrue(index(2001L) >= 0); assertEquals(-1, index(1180L))
        scroll("advanced-voided"); ui.onNodeWithTag("view-voided").performClick()
        assertTrue(index(3100L) >= 0); assertEquals(-1, index(3080L))
        evidence("voided-first-page", 100, 20, composed())
        more("VOIDED"); assertTrue(index(3080L) >= 0); assertEquals(-1, index(3060L))
        assertEquals(-1, index(1180L))
        while (index("more-VOIDED") >= 0) more("VOIDED")
        assertTrue(index(3001L) >= 0); assertEquals(-1, index("more-VOIDED"))
        evidence("completed-voided", 100, 100, composed())
        more("EMPTY"); assertTrue(index(1180L) >= 0)
        scroll(1200L); ui.onNodeWithTag("undo-package-1200").performScrollTo().performClick()
        assertEquals(1200L, undone); assertEquals(-1, index(1200L))
        ui.onNodeWithTag("package-info-1200").assertDoesNotExist(); ui.onNodeWithTag("undo-package-1200").assertDoesNotExist()
        scroll("header-EMPTY"); ui.onNodeWithText(strings.getString(R.string.pkg_empty) + " (199)").assertExists()
        scroll(3100L); ui.onNodeWithTag("restore-package-3100").performScrollTo().performClick()
        assertEquals(3100L, restored); ui.onNodeWithTag("restore-package-3100").assertDoesNotExist()
        scroll("header-EMPTY"); ui.onNodeWithText(strings.getString(R.string.pkg_empty) + " (200)").assertExists()
        assertTrue(index(3100L) >= 0)
        scroll("advanced-voided"); ui.onNodeWithTag("view-voided").performClick()
        assertEquals(-1, index(3099L)); assertEquals(-1, index(1200L))
        assertTrue(index(1180L) >= 0); assertTrue(index(2001L) >= 0)
        ui.onNodeWithTag("view-voided").performClick(); ui.waitForIdle()
        assertTrue(index(3099L) >= 0); assertEquals(-1, index(3079L)); assertEquals(-1, index(1200L))
    }

    @Test fun detailsReturnRetainsPositionAndReopeningResetsHistory() {
        val boxes = (1L..100L).map { box(it) } + (1001L..1100L).map { box(it, "VOIDED") }
        var open by mutableStateOf(true); var selected by mutableStateOf<ContainerEntity?>(null)
        content {
            Button(onClick = { open = true }, modifier = Modifier.testTag("reopen-packages")) { Text("Synthetic open") }
            if (open) Manage(boxes, onDismiss = { open = false }, onInfo = { selected = it })
            selected?.let { ContainerInfoDialog(it, { selected = null }) { _, _ -> selected = null } }
        }
        toggle("EMPTY"); more("EMPTY"); scroll(70L)
        ui.onNodeWithTag("package-info-70").performScrollTo()
        val before = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        ui.onNodeWithTag("package-info-70").performClick(); assertEquals(70L, selected?.id)
        ui.onNodeWithText(strings.getString(R.string.cancel)).performClick()
        assertEquals(before, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
        assertTrue(index(61L) >= 0); assertEquals(-1, index(60L))
        ui.onNodeWithTag("package-info-70").assertIsDisplayed().assertHasClickAction()
        ui.onNodeWithText(strings.getString(R.string.close)).performClick()
        ui.onNodeWithTag("reopen-packages").performClick(); ui.waitForIdle()
        assertEquals(2, state.layoutInfo.totalItemsCount); assertEquals(0, composed())
        assertEquals(-1, index(70L)); assertEquals(-1, index(1100L))
    }

    @Test fun homeKeepsOneSummaryAndZeroValidPackagesRemainUntracked() {
        var boxes by mutableStateOf(listOf(box(501L, "IN_USE")) + (1L..500L).map { box(it) } +
            (1001L..1100L).map { box(it, "VOIDED") })
        content { StockScreen(NotesState(listOf(med), loading = false), boxes, emptyList(), {}, {}, {}, PaddingValues()) }
        // UI-01 D: management sits in the collapsed "Package records (n)" section, which counts the same 501 packages.
        ui.onNodeWithTag("stock-packages-1").performScrollTo().assertTextContains(strings.getString(R.string.stock_packages_section, 501)).performClick()
        ui.onAllNodesWithTag("stock-manage-1").assertCountEquals(1)
        ui.onAllNodesWithText("Synthetic", substring = false).assertCountEquals(1)
        ui.onNodeWithTag("stock-manage-1").performScrollTo().assertTextContains(strings.getString(R.string.stock_manage, 501))
        assertEquals(0, composed())
        ui.runOnIdle { boxes = boxes.filter { it.state == "VOIDED" } }
        ui.onNodeWithText(strings.getString(R.string.stock_not_tracked)).assertExists()
        ui.onNodeWithText(strings.getString(R.string.stock_no_active)).assertDoesNotExist()
        ui.onNodeWithTag("stock-manage-1").performScrollTo().assertTextContains(strings.getString(R.string.stock_manage_plain))
    }
}

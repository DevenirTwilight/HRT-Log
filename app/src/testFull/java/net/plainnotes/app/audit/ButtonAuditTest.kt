package net.plainnotes.app.audit

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.NotesState
import net.plainnotes.app.EditMedication
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.ScheduleSummary
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.*
import net.plainnotes.app.disguise.*
import net.plainnotes.app.disguise.privatenotes.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.json.JSONObject
import org.junit.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.*

/**
 * Audit only (run with BUTTON_AUDIT=1): renders every screen and dialog with synthetic data in four languages at
 * font scales 1.0/1.3/2.0, saves screenshots and records rows of labelled buttons whose sizes differ, controls that
 * overflow their window, and button text that is cut off. It never fails on findings.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class ButtonAuditTest(private val locale: String, private val scale: Float, private val width:Int,private val theme:String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}@{1}/{2}dp/{3}")
        fun params():List<Array<Any>> {
            val widths=(System.getenv("BUTTON_AUDIT_WIDTHS") ?: "411").split(',').map{it.toInt()}
            val themes=(System.getenv("BUTTON_AUDIT_THEMES") ?: "LIGHT").split(',').also{require(it.all{t->t in listOf("LIGHT","DARK","HIGH")})}
            val locales=(System.getenv("BUTTON_AUDIT_LOCALES") ?: "en,zh-rCN,zh-rTW,fr-rFR").split(',')
            val scales=(System.getenv("BUTTON_AUDIT_SCALES") ?: "1,1.3,2").split(',').map{it.toFloat()}
            return locales.flatMap{l->scales.flatMap{s->widths.flatMap{w->themes.map{t->arrayOf<Any>(l,s,w,t)}}}}
        }
    }
    private val setup = TestRule { base, _ -> object : org.junit.runners.model.Statement() {
        override fun evaluate() {
            Assume.assumeTrue(System.getenv("BUTTON_AUDIT") == "1")
            RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h1800dp-xxhdpi"); RuntimeEnvironment.setFontScale(scale); base.evaluate()
        }
    } }
    private val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(setup).around(rule)

    private val zone = ZoneId.systemDefault(); private val now = Instant.now(); private val today = LocalDate.now()
    private val tag get() = "${locale}_${scale}_${width}_${theme}"
    private val out get() = File("build/button-audit/$tag").apply { mkdirs() }
    private val report = mutableListOf<JSONObject>()

    private fun med(id: Long, name: String, molecule: String, route: String?, unit: String, dose: Double) =
        MedicationEntity(id, name, molecule, route, unit, dose, 30.0, null, 15, 120, false, null, true, true, id.toInt())
    private val meds = listOf(med(1, "Estradiol valérate (synthétique)", "E2", "ORAL", "MG", 2.0), med(2, "Acétate de cyprotérone", "CPA", "ORAL", "MG", 12.5), med(3, "Gel synthétique", "E2", "GEL", "MG", 1.5))
    private val profiles = mapOf(1L to ProfileEntity(1, "EV", "oral"), 2L to ProfileEntity(2, "CPA", "oral"), 3L to ProfileEntity(3, "E2", "gel", null, 1, "ARM", 750.0))
    private fun slot(med: Long, at: Instant, dose: Double, state: SlotState) = TimelineEntry(Slot("wall:$med@$at", med, med, at, at, zone, dose, 15, 120, at.minusSeconds(86400 * 30)), state)
    private val appt = AppointmentEntity(1, "ENDO", now.plusSeconds(2 * 86400).toEpochMilli(), zone.id, "Hôpital synthétique", "Dr Exemple", null, 60)
    private fun state(): NotesState {
        val d = today.atStartOfDay(zone).toInstant()
        val slots = listOf(slot(1, d.plusSeconds(8 * 3600), 2.0, SlotState.ON_TIME), slot(3, now.minusSeconds(3 * 3600), 1.5, SlotState.OVERDUE),
            slot(1, now.plusSeconds(600), 2.0, SlotState.SOON), slot(2, now.minusSeconds(26 * 3600), 12.5, SlotState.UNCONFIRMED), slot(1, d.plusSeconds(86400 + 8 * 3600), 2.0, SlotState.PENDING))
        return NotesState(meds, slots, listOf(appt), null, false,
            mapOf(1L to ScheduleSummary(RuleKind.EVERY_N_DAYS, 1, emptySet(), listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))), 2L to ScheduleSummary(RuleKind.EVERY_N_DAYS, 1, emptySet(), listOf(LocalTime.of(8, 0))),
                3L to ScheduleSummary(RuleKind.WEEKLY, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), listOf(LocalTime.of(9, 0)))), profiles, today)
    }
    private val snapshot get() = MedicationSnapshot.encode(meds[0], profiles[1L])
    private fun records(): List<RecordEntity> = (0 until 12).map { i ->
        val sched = now.minusSeconds((i + 1) * 12L * 3600)
        val status = when (i) { 2 -> "LATE"; 5 -> "MISSED"; 8 -> "SKIPPED"; else -> "ON_TIME" }
        val taken = status in listOf("ON_TIME", "LATE")
        RecordEntity(i + 1L, if (i % 3 == 0) 2 else 1, if (i == 4) null else 1, if (i == 4) null else "wall:1@$i", if (i == 4) null else sched.toEpochMilli(), if (i == 4) null else zone.id, 2.0, 120,
            if (taken) sched.plusSeconds(600).toEpochMilli() else null, if (taken) zone.id else null, if (taken) 2.0 else null, null, status,
            origin = if (i == 4) "IMPORT_HT" else if (i == 5) "AUTO_MISSED" else "APP", revision = 1, config_snapshot = if (i == 4) "{\"source\":\"hrttracker\"}" else snapshot)
    }
    private val containers = listOf(ContainerEntity(1, 1, 30.0, 0.0, 22.0, today.minusDays(10).toString(), "IN_USE", "Pharmacie synthétique", "LOT-001"),
        ContainerEntity(2, 1, 30.0, 0.0, 0.0, null, "SEALED"), ContainerEntity(3, 3, 80.0, 0.0, 74.0, today.minusDays(27).toString(), "IN_USE"))
    private val labs = listOf(LabValueEntity(1, "E2", 160.0, "pg/mL", now.minusSeconds(40 * 86400).toEpochMilli(), zone.id, 100.0, 300.0, "pg/mL"),
        LabValueEntity(2, "E2", 142.0, "pg/mL", now.minusSeconds(86400).toEpochMilli(), zone.id, 100.0, 300.0, "pg/mL", "Labo synthétique"), LabValueEntity(3, "T", 25.0, "ng/dL", now.minusSeconds(86400).toEpochMilli(), zone.id))
    private val items get() = CHECKIN_DEFAULTS.mapIndexed { i, k -> CheckinItemEntity(i + 1L, k, null, i < 6, i) }
    private fun extra(): NotesViewModel.ExtraState {
        val r = RuleEntity(id = 1, medication_id = 1, kind = "EVERY_N_DAYS", interval = 1, weekday_mask = 0, anchor_local = today.minusDays(30).toString(), anchor_zone = zone.id,
            effective_from_utc = now.minusSeconds(30 * 86400).toEpochMilli(), effective_zone = zone.id, missed_tracking_from_utc = now.minusSeconds(30 * 86400).toEpochMilli(),
            dose_snapshot = 2.0, soon_snapshot = 15, late_snapshot = 120, config_snapshot = snapshot)
        val def = RegimenDefinition.from(r, listOf(TimeEntity(rule_id = 1, local_time = "20:00:00")))
        return NotesViewModel.ExtraState(records(), containers, items, (0 until 10).map { CheckinScoreEntity(today.minusDays(it.toLong()).toString(), 1, 1 + it % 5) },
            listOf(DayNoteEntity(today.toString(), "Note synthétique")), emptyList(), listOf(StageReviewEntity(1, today.minusDays(3).toString(), tolerance_note = "Synthétique")),
            listOf(SymptomCheckEntity(today.toString(), "JAUNDICE", "Synthétique")), emptyList(),
            listOf(RegimenVersionEntity(1, 1, r.effective_from_utc, zone = zone.id, definition_json = def.json(), clinical_signature = def.signature(), origin = "APP")), emptyList(),
            listOf(MilestoneEntity(1, today.toString(), title = "Jalon synthétique")), labs, emptyList(),
            listOf(VisitQuestionEntity(1, 1, 0, "Question synthétique un peu longue pour vérifier le retour à la ligne", "ASKED", "Réponse")), emptyList())
    }
    private fun conc() = ConcentrationCalculator.compute(meds.take(2), profiles, (0 until 30).map { i ->
        RecordEntity(i + 1L, 1, taken_utc = now.minusSeconds((30 - i) * 12L * 3600).toEpochMilli(), taken_zone = zone.id, actual_dose = 2.0, status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = snapshot)
    }, (1..10).map { slot(1, now.plusSeconds(it * 12L * 3600), 2.0, SlotState.PENDING) }, labs.take(2), 62.0, now)

    private class Case(val name: String, val then: (() -> Unit)? = null, val content: @Composable () -> Unit)
    private var current by mutableStateOf<Case?>(null)

    private fun model(): NotesViewModel {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(c, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        val repo = NotesRepository(object : DatabaseAccess(c) { override fun get(space: Space) = db })
        runBlocking {
            val id = repo.saveMedication(meds[0].copy(id = 0), "EV", RuleKind.EVERY_N_DAYS, 1, listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), emptySet(), now.minusSeconds(5 * 86400), pk = ProfileEntity(0, "EV", "oral"))
            repo.addContainers(id, 30.0, 1, true)
            val a = repo.saveAppointment(appt.copy(id = 0)); repo.setVisitCompleted(a, false)
            repo.saveAppointment(appt.copy(id = 0, at_utc = now.minusSeconds(40 * 86400).toEpochMilli())).let { repo.setVisitCompleted(it, true) }
            repo.saveVisitQuestion(VisitQuestionEntity(appointment_id = a, sort_order = 0, text = "Question synthétique un peu longue pour vérifier le retour à la ligne"))
        }
        return NotesViewModel(repo, ReminderCoordinator(c, repo), c)
    }

    private fun cases(m: NotesViewModel): List<Case> {
        val s = state(); val x = extra(); val c = act
        fun click(res: Int) = { val n = rule.onAllNodesWithText(c.getString(res), substring = false).onFirst(); runCatching { n.performScrollTo() }; n.performClick(); Unit }
        val padding = PaddingValues()
        return listOf(
            Case("settings") { SettingsScreen(Appearance(ThemeMode.SYSTEM, false), {}, false, {}, {}, {}, padding) {
                RegionSection("FR") {}; PrivacySection(1, true) {}; DisguiseSection({}) { _, _ -> true }; DataSection(m, mapOf(1L to "08:00 · 20:00")) } },
            Case("calendar_day") { CalendarScreen(s, today, {}, {}, {}, {}, padding, extra = x, initialView = CalView.DAY) },
            Case("calendar_month") { CalendarScreen(s, today, {}, {}, {}, {}, padding, extra = x) },
            Case("calendar_week") { CalendarScreen(s, today, {}, {}, {}, {}, padding, extra = x, initialView = CalView.WEEK) },
            Case("history") { HistoryScreen(s, records(), {}, {}, padding) },
            Case("stock") { StockScreen(s, containers, records(), {}, { _, _ -> }, {}, padding) },
            Case("medications") { MedicationsScreen(s, {}, {}, {}, padding) },
            Case("wellbeing_daily") { WellbeingHub(s, x, m, "FR", {}, padding) },
            Case("wellbeing_symptoms", click(R.string.wb_symptoms)) { WellbeingHub(s, x, m, "FR", {}, padding) },
            Case("wellbeing_reviews", click(R.string.wb_reviews)) { WellbeingHub(s, x, m, "FR", {}, padding) },
            Case("concentration") { val r = remember { conc() }; ConcentrationScreen(s, r, false, 62.0, ConcSettings(false, true, net.plainnotes.app.pk.CalibrationMode.RETROSPECTIVE), {}, {}, {}, {}, padding, records(), profiles) },
            Case("labs") { LabsScreen(labs, listOf(now.minusSeconds(86400 + 5 * 3600)), {}, {}, padding) },
            Case("timeline") { LongitudinalScreen(s, x, {}, {}, {}, padding) },
            Case("visits_list") { val st by m.state.collectAsState(); val ex by m.extra.collectAsState(); VisitsScreen(st, ex, m, null, {}, {}, padding) },
            Case("visit_detail") { val st by m.state.collectAsState(); val ex by m.extra.collectAsState(); VisitsScreen(st, ex, m, st.appointments.firstOrNull { it.completed_utc == null }?.id, {}, {}, padding) },
            Case("visit_question_dialog", click(R.string.visit_question_add)) { val st by m.state.collectAsState(); val ex by m.extra.collectAsState(); VisitsScreen(st, ex, m, st.appointments.firstOrNull { it.completed_utc == null }?.id, {}, {}, padding) },
            Case("visit_delete_dialog", click(R.string.visit_delete)) { val st by m.state.collectAsState(); val ex by m.extra.collectAsState(); VisitsScreen(st, ex, m, st.appointments.firstOrNull { it.completed_utc == null }?.id, {}, {}, padding) },
            Case("visit_pack_dialog") { val st by m.state.collectAsState(); val ex by m.extra.collectAsState(); st.appointments.firstOrNull { it.completed_utc == null }?.let { VisitPackDialog(it, st, ex, {}) { _, _ -> } } },
            Case("pdf_options_dialog", click(R.string.export_pdf)) { Column(Modifier.verticalScroll(rememberScrollState())) { DataSection(m, mapOf(1L to "08:00")) } },
            Case("about") { AboutScreen(padding) },
            Case("lock") { LockScreen({ false }, { 0L }, {}) },
            Case("editor_screen") { MedicationEditor(EditMedication(meds[2], profiles[3L], null, emptyList()), {}, inDialog = false) {} },
            Case("editor_dialog") { MedicationEditor(EditMedication(null, null, null, emptyList()), {}) {} },
            Case("intake_dialog") { IntakeDialog(c.getString(R.string.complete), meds[2], 1.5, now, {}, "LEFT_ARM") { _, _, _ -> } },
            Case("override_dialog") { OverrideDialog(s.slots[4], meds[0], SlotOverride(s.slots[4].slot.key), {}) {} },
            Case("manual_intake_dialog") { ManualIntakeDialog(meds, { "LEFT_ARM" }, {}) { _, _, _, _, _ -> } },
            Case("appointment_dialog") { AppointmentDialog({}, appt) {} },
            Case("batch_add_dialog") { BatchAddDialog(meds, { listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)) }, {}) { _, _, _, _, _ -> } },
            Case("add_stock_dialog") { AddStockDialog(meds[0], {}) { _, _, _, _, _ -> } },
            Case("adjust_stock_dialog") { AdjustStockDialog(containers[0], meds[0], {}) {} },
            Case("container_info_dialog") { ContainerInfoDialog(containers[0], {}) { _, _ -> } },
            Case("lab_dialog") { LabDialog(labs[1], {}, {}) { _, _ -> } },
            Case("weight_dialog") { WeightDialog(62.0, {}) {} },
            Case("pk_disclaimer_dialog") { PkDisclaimerDialog({}, {}) },
            Case("stage_review_dialog") { StageReviewDialog(null, x.reviews, x.symptoms, emptyList(), {}) {} },
            Case("manage_items_dialog") { ManageCheckinItemsDialog(items, {}, {}) },
            Case("historical_context_dialog") { HistoricalContextDialog(meds[0], profiles[1L], records(), {}) { _, _, _, _ -> } },
            Case("imported_plan_dialog") { ImportedPlanDialog(NotesViewModel.ImportedLink(records()[4], listOf(s.slots[0], s.slots[2])), meds[0], {}) {} },
            Case("summary_export_dialog") { val st by m.state.collectAsState(); SummaryExportDialog(m, st) {} },
            Case("password_dialog") { PasswordDialog(c.getString(R.string.backup_export), c.getString(R.string.backup_password_note), true, {}) {} },
            Case("new_pin_dialog") { NewPinDialog({}) {} },
            Case("date_picker") { DatePickerModal(today, {}) {} },
            Case("time_picker") { TimePickerModal(LocalTime.of(8, 0), {}) {} },
            Case("disguise_setup_dialog", click(R.string.disguise_setup)) { Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { DisguiseSection({}) { _, _ -> true } } },
            Case("shell_calculator") { ShellTheme { CalculatorScreen {} } },
            Case("shell_notes") { ShellTheme { NotesShellScreen("Liste synthétique", {}) { _, _ -> } } },
            Case("private_notes") { PrivateNotesScreen(PrivateNotesModel.State(listOf(PrivateNote("1", "Note synthétique", "Texte", 1, 1)), loading = false), {}, {}, { _, _ -> }, {}, {}, {}, {}) },
            Case("private_note_editor") { PrivateNoteEditor(PrivateNotesModel.State(loading = false, editing = true, title = "Titre", body = "Texte"), { _, _ -> }, {}, {}) },
        )
    }

    /** Bounded: some dialogs relayout forever under Robolectric at large font scales, so the looper is never fully drained. */
    private fun idle() { val looper = shadowOf(android.os.Looper.getMainLooper()); repeat(4) { rule.mainClock.advanceTimeBy(500); var n = 0; while (!looper.isIdle && n++ < 500) looper.runOneTask() } }

    /** Cached: the rule's activity getter drains the looper, which never ends while a dialog relayouts. */
    private lateinit var act: ComponentActivity

    @Test fun audit() {
        act = rule.activity
        rule.mainClock.autoAdvance = false
        val m = model()
        // Let the view model read its synthetic database before screens that observe it.
        repeat(200) { if (m.state.value.appointments.size < 2 || m.extra.value.visitQuestions.isEmpty()) { val l = shadowOf(android.os.Looper.getMainLooper()); var n = 0; while (!l.isIdle && n++ < 500) l.runOneTask(); Thread.sleep(10) } }
        rule.setContent { NotesTheme(if(theme=="DARK")ThemeMode.DARK else ThemeMode.LIGHT,contrast=if(theme=="HIGH")Contrast.HIGH else Contrast.STANDARD) { Surface { current?.let { c -> key(c.name) { c.content() } } } } }
        val only = System.getenv("BUTTON_AUDIT_CASES")?.split(',')?.toSet()
        val cases=cases(m)
        require(only==null || only.all{name->cases.any{it.name==name}}){"Unknown BUTTON_AUDIT_CASES: $only"}
        cases.filter { only == null || it.name in only }.forEach { case ->
            current = case; idle()
            runCatching { case.then?.invoke(); idle() }.onFailure { report += JSONObject().put("case", case.name).put("kind", "action_failed").put("detail", it.toString().take(200)) }
            runCatching { measure(case.name); shoot(case.name) }.onFailure { report += JSONObject().put("case", case.name).put("kind", "measure_failed").put("detail", it.toString().take(300)) }
            current = null; idle()
        }
        File(out, if (only == null) "report.jsonl" else "report-extra.jsonl").writeText(report.joinToString("\n") { it.put("locale", locale).put("scale", scale.toDouble()).put("width",width).put("theme",theme).toString() } + "\n")
    }

    private fun shoot(name: String) {
        fun save(view: android.view.View, file: String) {
            if (view.width <= 0 || view.height <= 0) return
            val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); b.eraseColor(android.graphics.Color.WHITE); view.draw(android.graphics.Canvas(b))
            try { File(out, file).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { b.recycle() }
        }
        save(act.window.decorView, "$name.png")
        ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let { save(it, "${name}__dialog.png") }
    }

    private fun label(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") ?: "").ifBlank { n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "" }
    private fun root(n: SemanticsNode): SemanticsNode { var r = n; while (r.parent != null) r = r.parent!!; return r }
    private fun scrollsHorizontally(n: SemanticsNode): Boolean { var p = n.parent; while (p != null) { if (p.config.contains(SemanticsProperties.HorizontalScrollAxisRange)) return true; p = p.parent }; return false }

    /** Compose roots of the activity and of every shown dialog, read without waiting for idle (dialog text fields never idle under Robolectric). */
    private fun roots(): List<androidx.compose.ui.platform.ViewRootForTest> {
        val views = mutableListOf<android.view.View>(act.window.decorView)
        ShadowDialog.getShownDialogs().filter { it.isShowing }.mapNotNullTo(views) { it.window?.decorView }
        val found = mutableListOf<androidx.compose.ui.platform.ViewRootForTest>()
        fun walk(v: android.view.View) { if (v is androidx.compose.ui.platform.ViewRootForTest) found += v; if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        views.forEach(::walk); return found
    }
    private fun all(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::all)
    private fun isControl(n: SemanticsNode) = n.config.contains(SemanticsActions.OnClick) || n.config.contains(SemanticsProperties.ToggleableState) || n.config.contains(SemanticsProperties.Selected)

    private fun measure(case: String) {
        val d = act.resources.displayMetrics.density
        fun dp(px: Float) = Math.round(px / d * 10) / 10.0
        val owners = roots().map { it.semanticsOwner }
        val interactive = owners.flatMap { all(it.rootSemanticsNode) }.filter(::isControl)
        // Lines and cut-off text per interactive element, from the unmerged text nodes inside it.
        val lines = mutableMapOf<Int, Int>()
        owners.flatMap { all(it.unmergedRootSemanticsNode) }.filter { it.config.contains(SemanticsProperties.Text) }.forEach { t ->
            val results = mutableListOf<TextLayoutResult>()
            t.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val r = results.firstOrNull() ?: return@forEach
            var p: SemanticsNode? = t; var owner: SemanticsNode? = null
            while (p != null) { if (isControl(p)) { owner = p; break }; p = p.parent }
            if (owner != null) lines[owner.id] = maxOf(lines[owner.id] ?: 0, r.lineCount)
            if (r.lineCount == 0) return@forEach
            val last = r.lineCount - 1
            val ellipsized = r.isLineEllipsized(last)
            // Characters after the last laid-out line are hidden (maxLines without ellipsis).
            val hidden = r.getLineEnd(last, visibleEnd = false) < r.layoutInput.text.length
            val widest = (0..last).maxOf { r.getLineRight(it) - r.getLineLeft(it) }
            val cutWidth = widest - r.size.width > d
            val cutHeight = r.getLineBottom(last) - r.size.height > 2 * d
            if (ellipsized || hidden || cutWidth || cutHeight) report += JSONObject().put("case", case).put("kind", "truncated").put("in_control", owner != null)
                .put("text", t.config[SemanticsProperties.Text].joinToString(" ").take(80)).put("ellipsized", ellipsized).put("hidden_chars", hidden)
                .put("cut_width_dp", dp(widest - r.size.width)).put("cut_height_dp", dp(r.getLineBottom(last) - r.size.height)).put("lines", r.lineCount)
        }
        // Overflow: unclipped bounds outside the window, unless the control sits in a horizontally scrolling row.
        interactive.forEach { n ->
            val rootW = root(n).size.width; val left = n.positionInRoot.x; val right = left + n.size.width
            if ((right > rootW + 1 || left < -1) && !scrollsHorizontally(n)) report += JSONObject().put("case", case).put("kind", "overflow").put("label", label(n).take(60))
                .put("right_dp", dp(right)).put("window_dp", dp(rootW.toFloat()))
        }
        report += JSONObject().put("case",case).put("kind","coverage").put("controls",interactive.size)
        interactive.filter{!scrollsHorizontally(it) && it.positionInRoot.y>=0 && it.positionInRoot.y<root(it).size.height}.forEach { n ->
            if(n.size.width<=0 || n.size.height<=0) report += JSONObject().put("case",case).put("kind","collapsed").put("label",label(n))
        }
        interactive.filter{it.config.getOrNull(SemanticsProperties.Role) in listOf(Role.Button,Role.RadioButton)}.groupBy{it.parent?.id}.values.forEach { group ->
            group.forEachIndexed { i,a -> group.drop(i+1).forEach { b ->
                val ar=a.boundsInRoot;val br=b.boundsInRoot
                if(minOf(ar.right,br.right)-maxOf(ar.left,br.left)>d && minOf(ar.bottom,br.bottom)-maxOf(ar.top,br.top)>d)
                    report += JSONObject().put("case",case).put("kind","overlap").put("labels",listOf(label(a),label(b)).joinToString(" | "))
            }}
        }
        // Rows: labelled controls sharing a parent whose vertical spans overlap.
        interactive.filter { label(it).isNotBlank() && it.size.height > 0 }.groupBy { it.parent?.id }.values.forEach { group ->
            val sorted = group.sortedBy { it.positionInRoot.y }
            val rows = mutableListOf<MutableList<SemanticsNode>>()
            sorted.forEach { n -> val top = n.positionInRoot.y; val bottom = top + n.size.height
                val row = rows.lastOrNull()?.takeIf { r -> r.any { o -> val ot = o.positionInRoot.y; val ob = ot + o.size.height; minOf(bottom, ob) - maxOf(top, ot) >= 0.5f * minOf(n.size.height, o.size.height) } }
                if (row != null) row += n else rows += mutableListOf(n) }
            rows.forEach { row ->
                val hs = row.map { it.size.height }; val ws = row.map { it.size.width }
                val roles = row.map { n -> if (n.config.contains(SemanticsActions.SetText)) "TextField" else n.config.getOrNull(SemanticsProperties.Role)?.toString() ?: "-" }
                val segmented = roles.all { it == "RadioButton" || it == "Tab" }
                // Every row is kept so rows of one group (e.g. Theme vs Contrast) can be compared offline.
                if (row.size >= 2 || segmented) report += JSONObject().put("case", case).put("kind", "row").put("parent", row.first().parent?.id ?: -1)
                    .put("labels", row.joinToString(" | ") { label(it).take(30) }).put("roles", roles.joinToString(","))
                    .put("heights_dp", hs.joinToString(",") { dp(it.toFloat()).toString() }).put("widths_dp", ws.joinToString(",") { dp(it.toFloat()).toString() })
                    .put("lines", row.joinToString(",") { (lines[it.id] ?: 0).toString() }).put("top_dp", dp(row.minOf { it.positionInRoot.y }))
                    .put("left_dp", row.joinToString(",") { dp(it.positionInRoot.x).toString() })
            }
        }
    }
}

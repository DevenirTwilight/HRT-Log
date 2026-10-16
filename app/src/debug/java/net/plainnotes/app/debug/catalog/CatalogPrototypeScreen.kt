package net.plainnotes.app.debug.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.R
import net.plainnotes.app.ui.*

/** Non-exported debug instrumentation host. No launcher, intent input, database or production navigation. */
class CatalogPrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NotesTheme { Surface(Modifier.fillMaxSize()) { CatalogPrototypeHost(onClose = { finish() }) } } }
    }
}

@Composable fun CatalogPrototypeHost(onClose: () -> Unit = {}) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val loaded by produceState<CatalogLoad>(CatalogLoad.Loading, attempt) {
        value = CatalogLoad.Loading
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("ansm02-synthetic.json").bufferedReader().use { CatalogLoad.Ready(SyntheticCatalog.decode(it.readText())) } }
                .getOrElse { CatalogLoad.Failed }
        }
    }
    CatalogPrototype(loaded, onRetry = { attempt++ }, onClose = onClose)
}

@Composable private fun CatalogText(id: Int, value: String, tag: String? = null) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.then(if (tag == null) Modifier else Modifier.testTag(tag))) {
        Text(stringResource(id), style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable private fun CatalogHeading(id: Int) = Text(stringResource(id), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

/** A separate in-memory editor preview: none of its actions can call the real MedicationEditor or repository. */
@Composable fun CatalogPrototype(load: CatalogLoad, onRetry: () -> Unit = {}, onClose: () -> Unit = {}) {
    var page by rememberSaveable { mutableStateOf("editor") }
    var query by rememberSaveable { mutableStateOf("") }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var strength by rememberSaveable { mutableStateOf<String?>(null) }
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var currentCis by rememberSaveable { mutableStateOf<String?>(null) }
    var currentCip by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var dose by rememberSaveable { mutableStateOf("") }
    var chosenCis by rememberSaveable { mutableStateOf<String?>(null) }
    var chosenCip by rememberSaveable { mutableStateOf<String?>(null) }
    var chosenVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var chosenName by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf(false) }
    val catalog = (load as? CatalogLoad.Ready)?.catalog
    val product = catalog?.products?.firstOrNull { it.cis == currentCis }
    val keyboard = LocalSoftwareKeyboardController.current
    fun back() {
        keyboard?.hide(); message = false
        page = when (page) { "detail" -> "catalog"; "confirm" -> "detail"; else -> "editor" }
    }
    BackHandler(page != "editor") { back() }
    Surface(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text(stringResource(R.string.cat_synthetic), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(Spacing.m).testTag("cat-banner"))
        TextButton(onClick = { if (page == "editor") onClose() else back() }, modifier = Modifier.heightIn(min = 48.dp).testTag("cat-back")) {
            Text(stringResource(if (page == "editor") R.string.cat_close else R.string.cat_back))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("cat-body"), contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            item { CatalogHeading(when (page) { "catalog" -> R.string.cat_title; "detail" -> R.string.cat_detail; "confirm" -> R.string.cat_confirm; "done" -> R.string.cat_preview; else -> R.string.cat_editor }) }
            if (page == "editor" || page == "done") {
                item { Text(stringResource(R.string.cat_mock_boundary)) }
                item { CatalogInput(name, { name = it }, R.string.cat_name, "cat-name") }
                item { CatalogInput(dose, { dose = it }, R.string.cat_user_dose, "cat-dose") }
                if (chosenCis != null) item {
                    CatalogText(R.string.cat_mock_selected, listOfNotNull(chosenName, chosenCis, chosenCip, chosenVersion).joinToString("\n"), "cat-selected")
                    TextButton({ chosenCis = null; chosenCip = null; chosenVersion = null; chosenName = null }, Modifier.heightIn(min = 48.dp).testTag("cat-unlink")) { Text(stringResource(R.string.cat_unlink)) }
                }
                item { Button({ keyboard?.hide(); page = "catalog" }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cat-open")) { Text(stringResource(R.string.cat_choose)) } }
                item { OutlinedButton({ keyboard?.hide(); page = "done" }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cat-manual")) { Text(stringResource(R.string.cat_manual)) } }
                if (page == "done") item { Text(stringResource(R.string.cat_not_saved), Modifier.heightIn(min = 48.dp).testTag("cat-not-saved")) }
            } else if (page == "catalog") {
                item { CatalogInput(query, { query = it }, R.string.cat_search, "cat-search", search = true) { keyboard?.hide() } }
                if (catalog != null) {
                    item { Text(stringResource(R.string.cat_local_only)) }
                    item { CatalogFilterControl(R.string.cat_form, form, catalog.products.map { it.form }, "form") { form = it } }
                    item { CatalogFilterControl(R.string.cat_strength, strength, catalog.products.mapNotNull { it.strength }, "strength") { strength = it } }
                    item { CatalogFilterControl(R.string.cat_route, route, catalog.products.flatMap { it.routes }, "route") { route = it } }
                    if (form != null || strength != null || route != null || query.isNotEmpty()) item {
                        TextButton({ form = null; strength = null; route = null; query = "" }, Modifier.heightIn(min = 48.dp).testTag("cat-clear")) { Text(stringResource(R.string.cat_clear)) }
                    }
                    val products = catalog.search(CatalogFilter(query, form, strength, route))
                    if (products.isEmpty()) item { Text(stringResource(R.string.cat_no_results), Modifier.heightIn(min = 48.dp).testTag("cat-no-results")) }
                    items(products, key = { it.cis }) { p ->
                        OutlinedCard(onClick = { keyboard?.hide(); currentCis = p.cis; currentCip = null; page = "detail" },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cat-product:${p.cis}"), shape = Corners.list) {
                            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                Text(p.ingredients.filter { it.role == "SA" }.joinToString(" · ") { it.name })
                                Text(listOf(p.strength ?: stringResource(R.string.cat_unknown), p.form, p.routes.joinToString(" · ")).joinToString("\n"))
                                Text(p.cis)
                                Text(stringResource(R.string.cat_registration) + "\n" + stringResource(registrationLabel(p.registration)))
                                Text(stringResource(R.string.cat_marketing) + "\n" + stringResource(marketingLabel(p.marketing)))
                                Text(catalog.version)
                                Text(stringResource(R.string.cat_supply_unknown))
                                Text(stringResource(R.string.cat_date, formatDate(catalog.verifiedOn)))
                            }
                        }
                    }
                    item { CatalogText(R.string.cat_source, catalog.source + "\n" + catalog.version) }
                } else item {
                    if (load == CatalogLoad.Loading) Text(stringResource(R.string.cat_loading), Modifier.heightIn(min = 48.dp).testTag("cat-loading"))
                    else {
                        Text(stringResource(R.string.cat_load_failed), Modifier.heightIn(min = 48.dp).testTag("cat-error"))
                        OutlinedButton(onRetry, Modifier.heightIn(min = 48.dp).testTag("cat-retry")) { Text(stringResource(R.string.cat_retry)) }
                    }
                }
                item { OutlinedButton({ back() }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cat-fallback")) { Text(stringResource(R.string.cat_manual)) } }
            } else if (product == null) {
                item { Text(stringResource(R.string.cat_load_failed)) }
                item { Button({ page = "editor" }, Modifier.heightIn(min = 48.dp).testTag("cat-fallback")) { Text(stringResource(R.string.cat_manual)) } }
            } else {
                item { Text(product.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }.testTag("cat-product-name")) }
                item { CatalogText(R.string.cat_cis, product.cis) }
                item { CatalogText(R.string.cat_strength, product.strength ?: stringResource(R.string.cat_unknown)) }
                item { CatalogText(R.string.cat_form, product.form) }
                item { CatalogText(R.string.cat_route, product.routes.joinToString("\n")) }
                item { CatalogHeading(R.string.cat_ingredients); Text(stringResource(R.string.cat_not_additive)) }
                items(product.ingredients) { i -> CatalogText(R.string.cat_ingredient, listOfNotNull(i.name, i.strength, i.denominator, "${i.role} · ${i.group}").joinToString("\n")) }
                item {
                    CatalogText(R.string.cat_registration, stringResource(registrationLabel(product.registration)))
                    CatalogText(R.string.cat_marketing, stringResource(marketingLabel(product.marketing)))
                    Text(stringResource(R.string.cat_supply_unknown))
                    Text(stringResource(R.string.cat_status_limit))
                }
                item { CatalogHeading(R.string.cat_packaging) }
                if (product.presentations.isEmpty()) item { Text(stringResource(R.string.cat_no_pack)) }
                items(product.presentations, key = { it.cip }) { p -> CatalogText(R.string.cat_cip, p.cip + "\n" + p.label) }
                item {
                    CatalogText(R.string.cat_source, catalog.source + "\n" + catalog.version)
                    Text(stringResource(R.string.cat_date, formatDate(catalog.verifiedOn)))
                    Text(stringResource(R.string.cat_unverified))
                }
                item { CatalogDocument(R.string.cat_rcp, product.rcp) { message = true } }
                item { CatalogDocument(R.string.cat_notice, product.notice) { message = true } }
                if (message) item { Text(stringResource(R.string.cat_no_real_link), Modifier.heightIn(min = 48.dp).testTag("cat-link-message")) }
                if (page == "detail") item { Button({ page = "confirm" }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cat-select")) { Text(stringResource(R.string.cat_select_mock)) } }
                else {
                    item { Text(stringResource(R.string.cat_confirm_boundary), Modifier.heightIn(min = 48.dp).testTag("cat-confirm-boundary")) }
                    item { CatalogFilterControl(R.string.cat_cip, currentCip, product.presentations.map { it.cip }, "cip") { currentCip = it } }
                    item { Button({
                        val result = MockCatalogDraft(name, dose).choose(catalog, product.cis, currentCip).selection!!
                        chosenCis = result.cis; chosenCip = result.cip; chosenVersion = result.version; chosenName = result.name
                        page = "editor"
                    }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cat-confirm")) { Text(stringResource(R.string.cat_confirm_mock)) } }
                    item { OutlinedButton({ back() }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cat-cancel")) { Text(stringResource(R.string.cat_cancel)) } }
                }
            }
        }
    }
    }
}

/** Keep labels outside the floating-label box so long translations remain readable at 2×. */
@Composable private fun CatalogInput(value: String, change: (String) -> Unit, labelId: Int, tag: String, search: Boolean = false, onSearch: () -> Unit = {}) {
    val label = stringResource(labelId)
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(value, change, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).semantics { contentDescription = label },
            singleLine = search, keyboardOptions = KeyboardOptions(imeAction = if (search) ImeAction.Search else ImeAction.Default),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }))
    }
}

@Composable private fun CatalogFilterControl(label: Int, value: String?, options: List<String>, tag: String, change: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton({ expanded = !expanded }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cat-filter-$tag")) {
            Text(stringResource(label) + ": " + (value ?: stringResource(R.string.cat_all)))
        }
        // Inline choices wrap naturally at 320dp/2×; no fixed-height popup or truncated product identifiers.
        if (expanded) {
            TextButton({ change(null); expanded = false }, Modifier.heightIn(min = 48.dp).testTag("cat-filter-$tag-all")) { Text(stringResource(R.string.cat_all)) }
            options.distinct().sorted().forEach { option ->
                TextButton({ change(option); expanded = false }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cat-filter-$tag:$option")) { Text(option) }
            }
        }
    }
}
@Composable private fun CatalogDocument(label: Int, entry: DocumentEntry, onClick: () -> Unit) {
    Column {
        CatalogText(label, stringResource(when (entry) { DocumentEntry.PANEL_OBSERVED -> R.string.cat_panel_only; DocumentEntry.MISSING -> R.string.cat_document_missing; DocumentEntry.UNVERIFIED -> R.string.cat_unknown }))
        Text(stringResource(R.string.cat_clinical_unknown))
        if (entry == DocumentEntry.PANEL_OBSERVED) OutlinedButton(onClick, Modifier.testTag(if (label == R.string.cat_rcp) "cat-rcp" else "cat-notice")) { Text(stringResource(label) + "\n" + stringResource(R.string.cat_mock_document)) }
    }
}
private fun registrationLabel(s: Registration) = when (s) {
    Registration.ACTIVE -> R.string.cat_auth_active; Registration.ARCHIVED -> R.string.cat_auth_archived
    Registration.REPEALED -> R.string.cat_auth_repealed; Registration.EXPIRED -> R.string.cat_auth_expired; Registration.UNVERIFIED -> R.string.cat_unknown
}
private fun marketingLabel(s: Marketing) = when (s) {
    Marketing.MARKETED_DECLARED -> R.string.cat_marketed; Marketing.STOPPED_DECLARED -> R.string.cat_stopped
    Marketing.NOT_MARKETED -> R.string.cat_not_marketed; Marketing.UNVERIFIED -> R.string.cat_unknown
}

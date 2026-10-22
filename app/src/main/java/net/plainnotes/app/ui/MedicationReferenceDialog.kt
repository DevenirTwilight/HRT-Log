package net.plainnotes.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.reference.MedicationReferenceController
import java.time.LocalDate

/** Explicit reference association; catalogue browsing never prefills medication or inventory fields. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MedicationReferenceDialog(controller:MedicationReferenceController,onDismiss:()->Unit) {
    val state by controller.state.collectAsState()
    val catalogueState=rememberBdpmCatalogueUiState()
    var query by remember {mutableStateOf("")};var form by remember {mutableStateOf("")};var route by remember {mutableStateOf("")}
    var strengthFilter by remember {mutableStateOf("")}
    var editing by remember {mutableStateOf(false)}
    var official by remember {mutableStateOf(true)}
    var preview by remember {mutableStateOf<UserReferenceProduct?>(null)}
    var confirm by remember {mutableStateOf(false)};var unlink by remember {mutableStateOf(false)}
    LaunchedEffect(state.saved){if(state.saved){confirm=false;unlink=false;preview=null;editing=false}}
    val keyboard=LocalSoftwareKeyboardController.current
    Dialog(onDismissRequest={if(controller.close())onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=true)) {
        Scaffold(modifier=Modifier.testTag("reference-window"),topBar={TopAppBar(title={Text(stringResource(R.string.ref2_title))},navigationIcon={
            IconButton(modifier=Modifier.testTag("reference-close"),enabled=!state.busy,onClick={if(controller.close())onDismiss()}){Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.cancel))}
        })}) {padding->
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("reference-screen"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text(stringResource(R.string.bdpm_available),style=MaterialTheme.typography.bodyMedium)}
                item {Text(stringResource(R.string.ref2_boundary))}
                if(state.target==null)item {Text(stringResource(R.string.ref2_save_first))}
                if(state.loading)item {LinearProgressIndicator(Modifier.fillMaxWidth().testTag("reference-loading"))}
                if(state.failed)item {
                    Text(stringResource(if(state.stale)R.string.ref2_stale else R.string.ref2_error),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("reference-error"))
                    TextButton(enabled=!state.busy,onClick={controller.open(state.target)}) {Text(stringResource(R.string.ref2_reload))}
                }
                state.current?.reference?.product?.let { product->
                    item {Text(stringResource(R.string.ref2_linked),style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{heading()})}
                    item {ReferenceProductDetails(product)}
                    item {TextButton(enabled=!state.busy && !state.loading,onClick={unlink=true;confirm=true},modifier=Modifier.testTag("reference-unlink")){Text(stringResource(R.string.ref2_unlink))}}
                }
                if(state.busy)item {Text(stringResource(R.string.ref2_saving),modifier=Modifier.testTag("reference-saving"))}
                item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={official=true},enabled=!state.busy,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("reference-official-tab").semantics{selected=official}){Text(stringResource(R.string.bdpm_title))}
                    TextButton(onClick={official=false},enabled=!state.busy,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("reference-saved-tab").semantics{selected=!official}){Text(stringResource(R.string.ref2_saved_library))}
                }}
                if(official) item(key="bdpm-catalogue") {BdpmCataloguePanel(catalogueState){preview=it}}
                if(!official) {
                item {Text(stringResource(R.string.ref2_saved_library),style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{heading()})}
                item {ReferenceField(query,{query=it.take(256)},R.string.ref2_search,"reference-search")}
                item {ReferenceField(form,{form=it.take(256)},R.string.ref2_filter_form,"reference-form-filter")}
                item {ReferenceField(strengthFilter,{strengthFilter=it.take(256)},R.string.ref2_filter_strength,"reference-strength-filter")}
                item {ReferenceField(route,{route=it.take(256)},R.string.ref2_filter_route,"reference-route-filter")}
                item {Button(enabled=!state.busy && !state.loading,onClick={keyboard?.hide();controller.search(query,form,route,strengthFilter)},modifier=Modifier.heightIn(min=48.dp).testTag("reference-search-go")){Text(stringResource(R.string.ref2_find))}}
                if(!state.loading && state.products.isEmpty())item {Text(stringResource(R.string.ref2_empty),modifier=Modifier.testTag("reference-empty"))}
                if(state.limited)item {Text(stringResource(R.string.ref2_limited))}
                state.products.forEach { entry->item(key=entry.snapshotUuid) {
                    OutlinedCard(onClick={preview=entry.product},modifier=Modifier.fillMaxWidth().testTag("reference-result:${entry.snapshotUuid}")) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(entry.product.name,style=MaterialTheme.typography.titleMedium)
                            Text(entry.product.ingredients.joinToString("\n"))
                            listOfNotNull(entry.product.strength,entry.product.form).forEach {Text(it)}
                            Text(entry.product.routes.joinToString("\n"))
                            Text(stringResource(R.string.ref2_identifiers,entry.product.cis ?: stringResource(R.string.ref2_unknown),entry.product.cip ?: stringResource(R.string.ref2_unknown)))
                            Text(stringResource(if(entry.product.bdpm==null)R.string.ref2_unverified else R.string.bdpm_source))
                            Text(stringResource(R.string.ref2_version_value,entry.product.sourceVersion))
                            Text(stringResource(R.string.ref2_date_value,entry.product.recordedOn))
                        }
                    }
                } }
                }
                item {OutlinedButton(enabled=!state.busy && !state.loading,onClick={editing=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("reference-manual")){Text(stringResource(R.string.ref2_manual))}}
            }
        }
    }
    if(editing)UserReferenceForm({editing=false}) {preview=it;editing=false}
    preview?.let { product->
        AlertDialog(onDismissRequest={if(!state.busy)preview=null},title={Text(stringResource(R.string.ref2_details))},
            text={Column(Modifier.heightIn(max=LocalConfiguration.current.screenHeightDp.dp*0.4f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                ReferenceProductDetails(product);Text(stringResource(R.string.ref2_boundary))
                if(state.target==null)Text(stringResource(R.string.ref2_save_first))
            }},confirmButton={TextButton(enabled=state.target!=null && state.current!=null && !state.busy && !state.loading && !state.stale,
                onClick={unlink=false;confirm=true},modifier=Modifier.testTag("reference-select")){Text(stringResource(R.string.ref2_select))}},
            dismissButton={TextButton(enabled=!state.busy,onClick={preview=null}){Text(stringResource(R.string.cancel))}})
    }
    if(confirm)AlertDialog(onDismissRequest={if(!state.busy)confirm=false},title={Text(stringResource(if(unlink)R.string.ref2_unlink else R.string.ref2_confirm))},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if(unlink)R.string.ref2_unlink_body else R.string.ref2_boundary))
            if(state.failed)Text(stringResource(if(state.stale)R.string.ref2_stale else R.string.ref2_error),color=MaterialTheme.colorScheme.error)
            if(state.busy)Text(stringResource(R.string.ref2_saving))
        }},confirmButton={TextButton(enabled=!state.busy && !state.stale,onClick={controller.confirm(if(unlink)null else preview)},modifier=Modifier.testTag("reference-confirm")){Text(stringResource(R.string.ref2_confirm))}},
        dismissButton={TextButton(modifier=Modifier.testTag("reference-confirm-cancel"),enabled=!state.busy,onClick={confirm=false;if(state.stale)controller.open(state.target)}){Text(stringResource(if(state.stale)R.string.ref2_reload else R.string.cancel))}})
}

@Composable private fun ReferenceField(value:String,onValue:(String)->Unit,label:Int,tag:String,multiline:Boolean=false) {
    OutlinedTextField(value,onValue,Modifier.fillMaxWidth().testTag(tag),label={Text(stringResource(label))},singleLine=!multiline,minLines=if(multiline)2 else 1)
}

@Composable fun ReferenceProductDetails(product:UserReferenceProduct) {
    val context=LocalContext.current
    var external by remember {mutableStateOf(false)};var failed by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth().testTag("reference-details")) {
        Text(product.name,style=MaterialTheme.typography.titleLarge)
        Text(stringResource(if(product.bdpm==null)R.string.ref2_unverified else R.string.bdpm_source))
        product.bdpm?.let{b->
            Text(stringResource(R.string.bdpm_scope));Text(stringResource(R.string.bdpm_snapshot_dates,b.sourceDates,product.recordedOn))
            b.packageDescription?.let{Text(it)};b.cip7?.let{Text("CIP7 $it")}
            Text(b.downloadUrl)
        }
        listOf(R.string.ref2_ingredients to product.ingredients.joinToString("\n"),R.string.ref2_strength to product.strength,
            R.string.ref2_form to product.form,(if(product.bdpm==null)R.string.ref2_routes else R.string.bdpm_routes) to product.routes.joinToString("\n"),
            R.string.ref2_cis to product.cis,R.string.ref2_cip to product.cip,(if(product.bdpm==null)R.string.ref2_version else R.string.bdpm_version) to product.sourceVersion,
            (if(product.bdpm==null)R.string.ref2_date else R.string.bdpm_downloaded) to product.recordedOn).forEach { (label,value)->
            Text(stringResource(label),style=MaterialTheme.typography.labelLarge)
            Text(value?.takeIf {it.isNotBlank()} ?: stringResource(R.string.ref2_unknown))
        }
        Text(stringResource(if(product.bdpm==null)R.string.ref2_status_unknown else R.string.bdpm_status_omitted));Text(stringResource(R.string.ref2_documents_unknown))
        product.sourceUrl?.let { url->
            Text(url)
            TextButton(onClick={external=true}){Text(stringResource(R.string.ref2_open_source))}
        }
        if(failed)Text(stringResource(R.string.ref2_browser_error),color=MaterialTheme.colorScheme.error)
    }
    if(external)AlertDialog(onDismissRequest={external=false},title={Text(stringResource(R.string.ref2_open_source))},text={Text(stringResource(R.string.ref2_external))},
        confirmButton={TextButton(onClick={external=false;failed=runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(product.sourceUrl)))}.isFailure}){Text(stringResource(R.string.ref2_open))}},
        dismissButton={TextButton(onClick={external=false}){Text(stringResource(R.string.cancel))}})
}

@Composable private fun UserReferenceForm(onCancel:()->Unit,onPreview:(UserReferenceProduct)->Unit) {
    var name by remember {mutableStateOf("")};var ingredients by remember {mutableStateOf("")}
    var strength by remember {mutableStateOf("")};var form by remember {mutableStateOf("")};var routes by remember {mutableStateOf("")}
    var version by remember {mutableStateOf("user-v1")};var date by remember {mutableStateOf(LocalDate.now().toString())}
    var invalid by remember {mutableStateOf(false)}
    var cis by remember {mutableStateOf("")};var cip by remember {mutableStateOf("")};var url by remember {mutableStateOf("")}
    val keyboard=LocalSoftwareKeyboardController.current
    fun optional(value:String)=value.takeIf {it.isNotBlank()}
    AlertDialog(onDismissRequest=onCancel,title={Text(stringResource(R.string.ref2_manual))},text={
        Column(Modifier.heightIn(max=LocalConfiguration.current.screenHeightDp.dp*0.4f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ref2_unverified));Text(stringResource(R.string.ref2_lines))
            if(invalid)Text(stringResource(R.string.ref2_invalid),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("reference-invalid"))
            ReferenceField(name,{name=it.take(8192)},R.string.name,"reference-name")
            ReferenceField(ingredients,{ingredients=it.take(8192)},R.string.ref2_ingredients,"reference-ingredients",true)
            ReferenceField(strength,{strength=it.take(8192)},R.string.ref2_strength,"reference-strength")
            ReferenceField(form,{form=it.take(8192)},R.string.ref2_form,"reference-form")
            ReferenceField(routes,{routes=it.take(8192)},R.string.ref2_routes,"reference-routes",true)
            ReferenceField(version,{version=it.take(8192)},R.string.ref2_version,"reference-version")
            ReferenceField(date,{date=it.take(32)},R.string.ref2_date,"reference-date")
            ReferenceField(cis,{cis=it.take(64)},R.string.ref2_cis,"reference-cis")
            ReferenceField(cip,{cip=it.take(64)},R.string.ref2_cip,"reference-cip")
            ReferenceField(url,{url=it.take(8192)},R.string.ref2_url,"reference-url")
        }},confirmButton={TextButton(enabled=name.isNotBlank() && version.isNotBlank(),onClick={try {keyboard?.hide();onPreview(validatedUserReferenceProduct(UserReferenceProduct(name,ingredients.lines().filter{it.isNotBlank()},optional(strength),optional(form),routes.lines().filter{it.isNotBlank()},optional(cis),optional(cip),optional(url),version,date)))}catch(_:ReferenceFailure){invalid=true}},modifier=Modifier.testTag("reference-preview")){Text(stringResource(R.string.ref2_preview))}},
        dismissButton={TextButton(onClick=onCancel){Text(stringResource(R.string.cancel))}})
}

package net.plainnotes.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.reference.MedicationReferenceController
import java.time.LocalDate

/** Explicit reference association; catalogue browsing never prefills medication or inventory fields. */
@OptIn(ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable fun MedicationReferenceDialog(controller:MedicationReferenceController,medicationName:(Long?)->String?={null},onDismiss:()->Unit) {
    val state by controller.state.collectAsState()
    val catalogueState=rememberBdpmCatalogueUiState()
    var query by remember {mutableStateOf("")};var form by remember {mutableStateOf("")};var route by remember {mutableStateOf("")}
    var strengthFilter by remember {mutableStateOf("")}
    var editing by remember {mutableStateOf(false)}
    var official by remember {mutableStateOf(true)}
    var details by remember {mutableStateOf(true)}
    var preview by remember {mutableStateOf<UserReferenceProduct?>(null)}
    var confirm by remember {mutableStateOf(false)};var unlink by remember {mutableStateOf(false)}
    LaunchedEffect(state.saved){if(state.saved){confirm=false;unlink=false;preview=null;editing=false}}
    val keyboard=LocalSoftwareKeyboardController.current
    val subtitle=medicationName(state.target)
    Dialog(onDismissRequest={if(controller.close())onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=true)) {
        Scaffold(modifier=Modifier.testTag("reference-window"),topBar={TopAppBar(title={Column {
            Text(stringResource(R.string.ref2_page_title),modifier=Modifier.semantics{heading()})
            subtitle?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("reference-subtitle"))}
        }},navigationIcon={NavBackButton({if(controller.close())onDismiss()},"reference-close",enabled=!state.busy)})}) {padding->
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("reference-screen"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {BoundaryCard()}
                if(state.target==null)item {Text(stringResource(R.string.ref2_save_first))}
                if(state.loading)item {LinearProgressIndicator(Modifier.fillMaxWidth().testTag("reference-loading"))}
                if(state.failed)item {
                    Text(stringResource(if(state.stale)R.string.ref2_stale else R.string.ref2_error),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("reference-error"))
                    TextButton(enabled=!state.busy,onClick={controller.open(state.target)}) {Text(stringResource(R.string.ref2_reload))}
                }
                state.current?.reference?.product?.let { product->
                    item {CurrentLinkCard(product,details,{details=!details},!state.busy && !state.loading,{unlink=true;confirm=true})}
                    if(details)item {ReferenceProductDetails(product)}
                }
                if(state.busy)item {Text(stringResource(R.string.ref2_saving),modifier=Modifier.testTag("reference-saving"))}
                item {PillTabs(listOf(stringResource(R.string.ref2_tab_catalogue),stringResource(R.string.ref2_tab_saved)),if(official)0 else 1,{official=it==0},
                    listOf("reference-official-tab","reference-saved-tab"),!state.busy)}
                if(official) item(key="bdpm-catalogue") {BdpmCataloguePanel(catalogueState){preview=it}}
                if(!official) {
                item {PillField(query,{query=it.take(256)},stringResource(R.string.ref2_search),"reference-search",Icons.Outlined.Search,Modifier.fillMaxWidth())}
                item {FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),maxItemsInEachRow=3) {
                    PillFilter(form,{form=it.take(256)},stringResource(R.string.ref2_pill_form),"reference-form-filter",Modifier.weight(1f).widthIn(min=104.dp))
                    PillFilter(strengthFilter,{strengthFilter=it.take(256)},stringResource(R.string.ref2_pill_strength),"reference-strength-filter",Modifier.weight(1f).widthIn(min=104.dp))
                    PillFilter(route,{route=it.take(256)},stringResource(R.string.ref2_pill_route),"reference-route-filter",Modifier.weight(1f).widthIn(min=104.dp))
                }}
                item {Button(enabled=!state.busy && !state.loading,shape=Corners.pill,onClick={keyboard?.hide();controller.search(query,form,route,strengthFilter)},modifier=Modifier.heightIn(min=48.dp).testTag("reference-search-go")){Text(stringResource(R.string.ref2_find))}}
                if(!state.loading && state.products.isEmpty())item {Text(stringResource(R.string.ref2_empty),modifier=Modifier.testTag("reference-empty"))}
                if(state.limited)item {Text(stringResource(R.string.ref2_limited))}
                item {Column(verticalArrangement=Arrangement.spacedBy(GroupedGap)) {
                    state.products.forEachIndexed { i,entry->
                        GroupedItem(i,state.products.size,onClick={preview=entry.product},modifier=Modifier.testTag("reference-result:${entry.snapshotUuid}")) {SavedResultRow(entry.product)}
                    }
                }}
                }
                item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled=!state.busy && !state.loading,shape=Corners.pill,onClick={editing=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("reference-manual")){Text(stringResource(R.string.ref2_manual_button))}
                    Text(stringResource(R.string.ref2_manual_caption),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=4.dp))
                }}
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

/** The boundary: linking records which product a medication is, nothing else. The bold lead is the promise; the full text follows. */
@Composable private fun BoundaryCard() {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=Corners.card,modifier=Modifier.fillMaxWidth().testTag("reference-boundary")) {
        Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Info,null,tint=MaterialTheme.colorScheme.primary)
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ref2_boundary_lead),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.ref2_boundary),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The current association: source tag, name, facts, and the two actions. Unlinking keeps its confirm dialog. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable private fun CurrentLinkCard(product:UserReferenceProduct,detailsOpen:Boolean,onToggleDetails:()->Unit,actionsEnabled:Boolean,onUnlink:()->Unit) {
    val official=product.bdpm!=null
    val mono=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace)
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLowest,shape=Corners.xl,modifier=Modifier.fillMaxWidth().testTag("reference-current")) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ref2_current_link),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.semantics{heading()})
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                DrugTag(stringResource(if(official)R.string.ref2_tag_official else R.string.ref2_tag_user),if(official)DrugLinkStatus.OFFICIAL else DrugLinkStatus.USER_ENTERED,Modifier.testTag("reference-source-tag"))
                if(product.cip!=null || product.bdpm?.packageDescription!=null)DrugTag(stringResource(R.string.ref2_tag_package),DrugLinkStatus.NONE,Modifier.testTag("reference-package-tag"))
            }
            Text(product.name,style=MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(product.ingredients.joinToString(", ").takeIf {it.isNotBlank()},product.form,product.routes.joinToString(" / ").takeIf {it.isNotBlank()}).joinToString(" · "),
                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(R.string.ref2_label_cis to product.cis,R.string.ref2_label_package to product.cip).forEach {(label,value)->
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(label),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(0.4f))
                        Text(value?.takeIf {it.isNotBlank()} ?: stringResource(R.string.ref2_unknown),style=mono,modifier=Modifier.weight(0.6f))
                    }
                }
            }
            AdaptiveActions(listOf(stringResource(if(detailsOpen)R.string.ref2_hide_source else R.string.ref2_view_source),stringResource(R.string.ref2_unlink))) {i,m->
                if(i==0)FilledTonalButton(onToggleDetails,m.testTag("reference-view"),shape=Corners.pill){Text(stringResource(if(detailsOpen)R.string.ref2_hide_source else R.string.ref2_view_source))}
                else TextButton(onUnlink,m.testTag("reference-unlink"),enabled=actionsEnabled){Text(stringResource(R.string.ref2_unlink))}
            }
        }
    }
}

/** Pill segmented control like the wellbeing tabs: a light track, the chosen segment a white pill; tab semantics. */
@Composable internal fun PillTabs(labels:List<String>,selected:Int,onSelect:(Int)->Unit,tags:List<String>,enabled:Boolean=true) {
    val c=MaterialTheme.colorScheme
    Surface(color=c.surfaceContainerHigh,shape=Corners.full,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed {i,label->
                val on=selected==i
                Surface(color=if(on)c.surfaceContainerLowest else Color.Transparent,contentColor=if(on)c.onSurface else c.onSurfaceVariant,shape=Corners.full,shadowElevation=if(on)1.dp else 0.dp,
                    modifier=Modifier.weight(1f).heightIn(min=48.dp).selectable(on,enabled=enabled,role=Role.Tab){onSelect(i)}.testTag(tags[i])) {
                    Box(contentAlignment=Alignment.Center,modifier=Modifier.padding(horizontal=8.dp,vertical=8.dp)) {
                        Text(label,style=MaterialTheme.typography.labelLarge,fontWeight=if(on)FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable internal fun PillField(value:String,onValue:(String)->Unit,label:String,tag:String,icon:ImageVector?=null,modifier:Modifier=Modifier) {
    OutlinedTextField(value,onValue,modifier.heightIn(min=56.dp).testTag(tag),label={Text(label,maxLines=1,overflow=TextOverflow.Ellipsis)},singleLine=true,shape=Corners.pill,
        leadingIcon=icon?.let {{Icon(it,null)}})
}

/** A compact filter pill: a small pill-shaped input (the label shows until something is typed); same filtering as before. */
@Composable private fun PillFilter(value:String,onValue:(String)->Unit,label:String,tag:String,modifier:Modifier=Modifier) {
    val c=MaterialTheme.colorScheme
    Surface(shape=Corners.pill,color=Color.Transparent,border=BorderStroke(1.dp,c.outline),modifier=modifier.heightIn(min=48.dp)) {
        Box(Modifier.padding(horizontal=16.dp,vertical=12.dp),contentAlignment=Alignment.CenterStart) {
            if(value.isEmpty())Text(label,style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            BasicTextField(value,onValue,singleLine=true,textStyle=MaterialTheme.typography.bodyMedium.copy(color=c.onSurface),cursorBrush=SolidColor(c.primary),
                modifier=Modifier.fillMaxWidth().testTag(tag).semantics{contentDescription=label})
        }
    }
}

@Composable private fun SavedResultRow(product:UserReferenceProduct) {
    val official=product.bdpm!=null
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(product.name,style=MaterialTheme.typography.titleSmall)
        Text(listOfNotNull(product.ingredients.joinToString(", ").takeIf {it.isNotBlank()},product.form,product.routes.joinToString(" / ").takeIf {it.isNotBlank()}).joinToString(" · "),
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        DrugTag(stringResource(if(official)R.string.ref2_tag_official else R.string.ref2_tag_user),if(official)DrugLinkStatus.OFFICIAL else DrugLinkStatus.USER_ENTERED)
        Text(stringResource(R.string.ref2_entered_on,product.recordedOn),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
            if(b.downloadUrl.contains("bdpmt.ansm.sante.fr"))Text(stringResource(R.string.official_test_source))
        }
        listOf(R.string.ref2_ingredients to product.ingredients.joinToString("\n"),R.string.ref2_strength to product.strength,
            R.string.ref2_form to product.form,(if(product.bdpm==null)R.string.ref2_routes else R.string.bdpm_routes) to product.routes.joinToString("\n"),
            R.string.ref2_cis to product.cis,R.string.ref2_cip to product.cip,(if(product.bdpm==null)R.string.ref2_version else R.string.bdpm_version) to product.sourceVersion,
            (if(product.bdpm==null)R.string.ref2_date else R.string.bdpm_downloaded) to product.recordedOn).forEach { (label,value)->
            Text(stringResource(label),style=MaterialTheme.typography.labelLarge)
            Text(value?.takeIf {it.isNotBlank()} ?: stringResource(R.string.ref2_unknown))
        }
        Text(stringResource(if(product.bdpm==null)R.string.ref2_status_unknown else R.string.bdpm_status_omitted));if(product.bdpm==null)Text(stringResource(R.string.ref2_documents_unknown))
        else product.cis?.let {BdpmOfficialPanel(it,product.name)}
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

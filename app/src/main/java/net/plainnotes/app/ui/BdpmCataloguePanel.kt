package net.plainnotes.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import org.json.JSONObject

/** Dialog-owned state: loading rows and LazyColumn recycling must not erase a query or cancel its IO. */
internal class BdpmCatalogueUiState(val catalogue:BdpmCatalogue,private val scope:CoroutineScope) {
    var query by mutableStateOf("")
    var phase by mutableStateOf("INITIAL")
    var page by mutableStateOf<BdpmPage?>(null)
    var details by mutableStateOf<BdpmProduct?>(null)
    var busy by mutableStateOf(false)
    /** The package picked inside the expanded product; survives the preview dialog so the primary button can reopen it. */
    var selectedPack by mutableStateOf<String?>(null)
    /** One product open at a time. */
    fun toggle(product:BdpmProduct){selectedPack=null;details=if(details?.cis==product.cis)null else product}
    fun search() {
        if(phase=="SEARCHING" || query.isBlank())return
        phase="SEARCHING";details=null;selectedPack=null
        scope.launch {
            try {page=catalogue.search(query);phase=if(page!!.entries.isEmpty())"EMPTY" else "RESULTS"}
            catch(e:CancellationException){throw e}
            catch(_:Exception){phase="UNAVAILABLE"}
        }
    }
    fun choose(product:BdpmProduct,pack:BdpmPackage?,onSelect:(UserReferenceProduct)->Unit) {
        if(busy)return
        busy=true
        scope.launch {
            try{onSelect(catalogue.snapshot(product,pack))}
            catch(e:CancellationException){throw e}
            catch(_:Exception){phase="UNAVAILABLE"}
            finally{busy=false}
        }
    }
}

@Composable internal fun rememberBdpmCatalogueUiState():BdpmCatalogueUiState {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    return remember(context,scope){BdpmCatalogueUiState(BdpmCatalogue(context),scope)}
}

/** Independent public catalogue. All extraction/query/snapshot work stays on IO in BdpmCatalogue. */
@Composable internal fun BdpmCataloguePanel(state:BdpmCatalogueUiState,onSelect:(UserReferenceProduct)->Unit) = with(state) {
    val c=MaterialTheme.colorScheme
    Column(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth().testTag("bdpm-panel")) {
        PillField(query,{if(phase!="SEARCHING")query=it.take(256)},stringResource(R.string.bdpm_search_label),"bdpm-search",Icons.Outlined.Search)
        Button(onClick={search()},enabled=query.isNotBlank() && phase!="SEARCHING" && !busy,shape=Corners.pill,modifier=Modifier.heightIn(min=48.dp).testTag("bdpm-search-go")) {Text(stringResource(R.string.ref2_find))}
        if(phase=="SEARCHING")LinearProgressIndicator(Modifier.fillMaxWidth())
        val meta=if(phase=="RESULTS")JSONObject(requireNotNull(page).metadata) else null
        Text(if(meta!=null)stringResource(R.string.bdpm_status_line,page!!.entries.size.let {if(page!!.limited)"$it+" else "$it"},meta.getString("downloaded_on"))
            else stringResource(when(phase){"SEARCHING"->R.string.bdpm_searching;"EMPTY"->R.string.bdpm_empty;"UNAVAILABLE"->R.string.bdpm_unavailable;"RESULTS"->R.string.bdpm_results;else->R.string.bdpm_initial}),
            style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("bdpm-status").semantics{liveRegion=LiveRegionMode.Polite})
        Text(stringResource(R.string.bdpm_scope),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().testTag("bdpm-selection-loading"))
        if(phase=="UNAVAILABLE")TextButton(onClick={search()},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.ref2_reload))}
        if(meta!=null) {
            val inputs=meta.getJSONObject("inputs")
            Text(stringResource(R.string.bdpm_dates,inputs.getJSONObject("CIS_bdpm.txt").getString("source_updated_on"),
                inputs.getJSONObject("CIS_CIP_bdpm.txt").getString("source_updated_on"),inputs.getJSONObject("CIS_COMPO_bdpm.txt").getString("source_updated_on"),meta.getString("downloaded_on")),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            Text("https://bdpmt.ansm.sante.fr/telechargement",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            if(page!!.limited)Text(stringResource(R.string.bdpm_limited))
            val entries=page!!.entries
            Column(verticalArrangement=Arrangement.spacedBy(GroupedGap)) {
                entries.forEachIndexed {i,product->
                    val open=details?.cis==product.cis
                    val expandedText=stringResource(if(open)R.string.drug_state_expanded else R.string.drug_state_collapsed)
                    GroupedItem(i,entries.size) {
                        Column {
                            val turn by animateFloatAsState(if(open)90f else 0f,motionSpec(LocalReduceMotion.current),label="chevron")
                            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(role=Role.Button){toggle(product)}.semantics(mergeDescendants=true){stateDescription=expandedText}
                                .testTag("bdpm-result:${product.cis}").padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Text(product.name,style=MaterialTheme.typography.titleSmall)
                                    Text(listOfNotNull(product.composition.map {it.name}.distinct().joinToString(", ").takeIf {it.isNotBlank()},product.form.takeIf {it.isNotBlank()},product.routes.joinToString(" / ").takeIf {it.isNotBlank()}).joinToString(" · "),
                                        style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                                    DrugTag(stringResource(R.string.ref2_tag_official),DrugLinkStatus.OFFICIAL)
                                    Text("CIS ${product.cis}",style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace),color=c.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,Modifier.graphicsLayer {rotationZ=turn},tint=c.onSurfaceVariant)
                            }
                            if(open)Column(Modifier.padding(start=16.dp,end=16.dp,bottom=16.dp).testTag("bdpm-expanded:${product.cis}"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(product.composition.joinToString("\n"){it.display()},style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                                Text(stringResource(R.string.bdpm_packages),style=MaterialTheme.typography.labelLarge)
                                Text(stringResource(R.string.bdpm_package_boundary),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                                product.packages.forEach {pack->
                                    val chosen=selectedPack==pack.cip13
                                    Surface(shape=Corners.m,color=if(chosen)c.primaryContainer.copy(alpha=0.4f) else c.surfaceContainerLowest,
                                        border=BorderStroke(if(chosen)2.dp else 1.dp,if(chosen)c.primary else c.outlineVariant),modifier=Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).selectable(chosen,enabled=!busy,role=Role.RadioButton){selectedPack=pack.cip13;choose(product,pack,onSelect)}
                                            .testTag("bdpm-package:${pack.cip13}").padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                            RadioButton(chosen,null)
                                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                                Text(pack.description,style=MaterialTheme.typography.bodyMedium)
                                                Text("CIP13 ${pack.cip13.ifEmpty{"—"}} · CIP7 ${pack.cip7.ifEmpty{"—"}}",style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace),color=c.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                TextButton(onClick={choose(product,null,onSelect)},enabled=!busy,modifier=Modifier.heightIn(min=48.dp).testTag("bdpm-product-only")){Text(stringResource(R.string.bdpm_product_only))}
                                Button(onClick={product.packages.firstOrNull {it.cip13==selectedPack}?.let {choose(product,it,onSelect)}},enabled=!busy && selectedPack!=null,shape=Corners.pill,
                                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("bdpm-link")){Text(stringResource(R.string.ref2_select))}
                            }
                        }
                    }
                }
            }
        }
    }
}

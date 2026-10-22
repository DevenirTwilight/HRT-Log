package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import org.json.JSONObject

/** Independent public catalogue. All extraction/query/snapshot work stays on IO in BdpmCatalogue. */
@Composable internal fun BdpmCataloguePanel(onSelect:(UserReferenceProduct)->Unit) {
    val context=LocalContext.current
    val catalogue=remember(context){BdpmCatalogue(context)}
    val scope=rememberCoroutineScope()
    var query by remember{mutableStateOf("")}
    var phase by remember{mutableStateOf("INITIAL")}
    var page by remember{mutableStateOf<BdpmPage?>(null)}
    var details by remember{mutableStateOf<BdpmProduct?>(null)}
    var busy by remember{mutableStateOf(false)}
    fun search() {
        if(phase=="SEARCHING" || query.isBlank())return
        phase="SEARCHING";details=null
        scope.launch {
            try {page=catalogue.search(query);phase=if(page!!.entries.isEmpty())"EMPTY" else "RESULTS"}
            catch(e:CancellationException){throw e}
            catch(_:Exception){phase="UNAVAILABLE"}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth().testTag("bdpm-panel")) {
        OutlinedTextField(query,{if(phase!="SEARCHING")query=it.take(256)},label={Text(stringResource(R.string.bdpm_search_label))},
            singleLine=true,modifier=Modifier.fillMaxWidth().testTag("bdpm-search"))
        Button(onClick={search()},enabled=query.isNotBlank() && phase!="SEARCHING" && !busy,modifier=Modifier.heightIn(min=48.dp).testTag("bdpm-search-go")) {Text(stringResource(R.string.ref2_find))}
        if(phase=="SEARCHING")LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(stringResource(when(phase){"SEARCHING"->R.string.bdpm_searching;"EMPTY"->R.string.bdpm_empty;"UNAVAILABLE"->R.string.bdpm_unavailable;"RESULTS"->R.string.bdpm_results;else->R.string.bdpm_initial}),
            modifier=Modifier.testTag("bdpm-status").semantics{liveRegion=LiveRegionMode.Polite})
        Text(stringResource(R.string.bdpm_scope),style=MaterialTheme.typography.bodySmall)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().testTag("bdpm-selection-loading"))
        if(phase=="UNAVAILABLE")TextButton(onClick={search()},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.ref2_reload))}
        if(phase=="RESULTS") {
            val m=JSONObject(requireNotNull(page).metadata)
            val inputs=m.getJSONObject("inputs")
            Text(stringResource(R.string.bdpm_dates,inputs.getJSONObject("CIS_bdpm.txt").getString("source_updated_on"),
                inputs.getJSONObject("CIS_CIP_bdpm.txt").getString("source_updated_on"),inputs.getJSONObject("CIS_COMPO_bdpm.txt").getString("source_updated_on"),m.getString("downloaded_on")),style=MaterialTheme.typography.bodySmall)
            Text("https://bdpmt.ansm.sante.fr/telechargement",style=MaterialTheme.typography.bodySmall)
            if(page!!.limited)Text(stringResource(R.string.ref2_limited))
            page!!.entries.forEach{product->
                OutlinedCard(onClick={details=if(details?.cis==product.cis)null else product},modifier=Modifier.fillMaxWidth().testTag("bdpm-result:${product.cis}")) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(product.name,style=MaterialTheme.typography.titleMedium)
                        Text(product.composition.joinToString("\n"){it.display()})
                        Text(product.form);Text(product.routes.joinToString(" / "));Text("CIS ${product.cis}")
                        Text(stringResource(R.string.bdpm_packages))
                    }
                }
                if(details?.cis==product.cis) {
                    Text(stringResource(R.string.bdpm_package_boundary))
                    fun choose(pack:BdpmPackage?) {
                        if(busy)return
                        busy=true
                        scope.launch {
                            try{onSelect(catalogue.snapshot(product,pack))}
                            catch(e:CancellationException){throw e}
                            catch(_:Exception){phase="UNAVAILABLE"}
                            finally{busy=false}
                        }
                    }
                    product.packages.forEach{pack->
                        OutlinedCard(onClick={if(!busy)choose(pack)},modifier=Modifier.fillMaxWidth().testTag("bdpm-package:${pack.cip13}")) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(pack.description)
                                Text("CIP13 ${pack.cip13.ifEmpty{"—"}} · CIP7 ${pack.cip7.ifEmpty{"—"}}")
                                Text(stringResource(R.string.bdpm_choose_package))
                            }
                        }
                    }
                    TextButton(onClick={choose(null)},enabled=!busy,modifier=Modifier.heightIn(min=48.dp).testTag("bdpm-product-only")){Text(stringResource(R.string.bdpm_product_only))}
                }
            }
        }
    }
}

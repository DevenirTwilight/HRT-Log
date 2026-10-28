package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.BdpmProduct

/**
 * Dedicated READ-ONLY path from the four official document/status cards.
 * No MedicationReferenceController, no package selection, and no association write.
 * All data is the verified packaged BDPM snapshot; external links still require consent.
 */
@Composable internal fun DrugOfficialBrowserScreen(section:String) {
    val state=rememberBdpmCatalogueUiState()
    var selected by remember(section) {mutableStateOf<BdpmProduct?>(null)}
    val title=when(section) {
        "documents" -> R.string.official_documents
        "safety" -> R.string.drug_center_safety
        "supply" -> R.string.center_shortages
        else -> R.string.official_title
    }
    Column(Modifier.fillMaxSize().testTag("official-browser").padding(horizontal=16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(selected!=null) {
            TextButton(onClick={selected=null},modifier=Modifier.heightIn(min=48.dp).testTag("official-product-back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack,null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nav_back))
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("official-product-details"),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                val product=requireNotNull(selected)
                Text(product.name,style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics{heading()})
                Text("CIS ${product.cis}",style=MaterialTheme.typography.bodyMedium)
                BdpmOfficialPanel(product.cis,expectedName=product.name,initialSection=section)
                Spacer(Modifier.height(24.dp))
            }
        } else {
            Text(stringResource(title),style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{heading()})
            Text(stringResource(R.string.official_choose_product),style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(state.query,{if(state.phase!="SEARCHING")state.query=it.take(256)},
                label={Text(stringResource(R.string.bdpm_search_label))},singleLine=true,
                modifier=Modifier.fillMaxWidth().testTag("official-product-search"))
            Button(onClick={state.search()},enabled=state.query.isNotBlank()&&state.phase!="SEARCHING",
                modifier=Modifier.heightIn(min=48.dp).testTag("official-product-search-go")) {
                Text(stringResource(R.string.ref2_find))
            }
            if(state.phase=="SEARCHING")LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.phase=="UNAVAILABLE")Text(stringResource(R.string.bdpm_unavailable),color=MaterialTheme.colorScheme.error)
            if(state.phase=="EMPTY")Text(stringResource(R.string.bdpm_empty))
            Text(stringResource(R.string.bdpm_scope),style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            val results=state.page?.entries.orEmpty()
            LazyColumn(Modifier.fillMaxSize().testTag("official-product-results"),
                verticalArrangement=Arrangement.spacedBy(GroupedGap),contentPadding=PaddingValues(bottom=24.dp)) {
                itemsIndexed(results,key={_,product->product.cis}) {i,product->
                    GroupedItem(i,results.size,onClick={selected=product},
                        modifier=Modifier.testTag("official-product:${product.cis}")) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(product.name,style=MaterialTheme.typography.titleSmall)
                            Text("CIS ${product.cis}",style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if(state.page?.limited==true)item {Text(stringResource(R.string.bdpm_limited))}
            }
        }
    }
}

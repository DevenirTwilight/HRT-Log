package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.MedicationEntity

/** A view over the existing medication/reference APIs. No catalogue, clinical advice or inventory writes. */
@Composable fun DrugReferenceCenterScreen(medications:List<MedicationEntity>,onBrowse:()->Unit,
    onReference:(MedicationEntity)->Unit,contentPadding:PaddingValues,onPlans:(()->Unit)?=null,onStock:(()->Unit)?=null) {
    LazyColumn(Modifier.fillMaxSize().testTag("drug-center-list"),contentPadding=PaddingValues(
        start=16.dp,end=16.dp,top=contentPadding.calculateTopPadding()+8.dp,
        bottom=contentPadding.calculateBottomPadding()+32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item("region") {Text(stringResource(R.string.drug_center_region),style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{heading()})}
        item("source-scope") {Text(stringResource(R.string.drug_center_short_scope))}
        item("browse") {
            Button(onClick=onBrowse,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("drug-center-browse")) {
                Text(stringResource(R.string.drug_center_search))
            }
        }
        onPlans?.let { open -> item("plans") {OutlinedButton(open,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("drug-center-plans")){Text(stringResource(R.string.medications))}} }
        onStock?.let { open -> item("stock") {OutlinedButton(open,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("drug-center-stock")){Text(stringResource(R.string.stock))}} }
        item("medications") { Text(stringResource(R.string.drug_center_medications),style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{heading()}) }
        if(medications.isEmpty())item("empty") {Text(stringResource(R.string.drug_center_empty))}
        items(medications.sortedWith(compareByDescending<MedicationEntity>{it.active}.thenBy{it.sort_order}.thenBy{it.id}),key={"medication:${it.id}"}) {medication->
            OutlinedCard(onClick={onReference(medication)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("drug-center-medication:${medication.id}")) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(medication.name,style=MaterialTheme.typography.titleSmall)
                    if(!medication.active)Text(stringResource(R.string.archived))
                    Text(stringResource(R.string.drug_center_open_reference),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item("official") {Text(stringResource(R.string.bdpm_available))}
        item("user-sources") {Text(stringResource(R.string.drug_center_user_sources))}
        item("states") {Text(stringResource(R.string.drug_center_states),style=MaterialTheme.typography.bodySmall)}
        item("identity") { DrugCenterSection(R.string.drug_center_identity,R.string.drug_center_identity_body,"drug-center-identity") }
        item("packaging") { DrugCenterSection(R.string.drug_center_packaging,R.string.drug_center_packaging_body,"drug-center-packaging") }
        item("future-heading") {Text(stringResource(R.string.center_future_modules),style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{heading()})}
        item("catalogue") {OutlinedButton(onClick=onBrowse,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("drug-center-catalogue")){Text(stringResource(R.string.bdpm_title))}}
        item("rcp") {UnavailableModuleCard(R.string.drug_center_rcp,R.string.drug_center_documents_off,"drug-center-rcp")}
        item("notice") {UnavailableModuleCard(R.string.drug_center_notice,R.string.drug_center_documents_off,"drug-center-notice")}
        item("safety") {UnavailableModuleCard(R.string.drug_center_safety,R.string.drug_center_safety_off,"drug-center-safety")}
        item("supply") {UnavailableModuleCard(R.string.center_shortages,R.string.drug_center_supply_off,"drug-center-supply")}
        item("recalls") {UnavailableModuleCard(R.string.center_recalls,R.string.drug_center_supply_off,"drug-center-recalls")}

    }
}

@Composable private fun DrugCenterSection(title:Int,body:Int,tag:String) {
    OutlinedCard(Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title),style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{heading()})
            Text(stringResource(body))
        }
    }
}

/** Existing AppShell hosts this only inside the unlocked real application. Nested reference dialog owns its write state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DrugReferenceCenterDialog(medications:List<MedicationEntity>,onBrowse:()->Unit,
    onReference:(MedicationEntity)->Unit,onStock:(()->Unit)?=null,onDismiss:()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss,
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Scaffold(modifier=Modifier.testTag("drug-center-window"),topBar={Surface {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                TextButton(onClick=onDismiss,modifier=Modifier.heightIn(min=48.dp).testTag("drug-center-close")) {
                    Text(stringResource(R.string.cancel))
                }
                Text(stringResource(R.string.drug_center_title),style=MaterialTheme.typography.titleLarge,
                    modifier=Modifier.padding(start=16.dp,end=16.dp,bottom=12.dp).semantics{heading()})
            }
        }}) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                DrugReferenceCenterScreen(medications,onBrowse,onReference,PaddingValues(),onPlans=onDismiss,onStock=onStock)
            }
        }
    }
}

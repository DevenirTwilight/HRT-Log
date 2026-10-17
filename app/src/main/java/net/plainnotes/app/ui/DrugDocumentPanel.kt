package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.UserReferenceProduct
import net.plainnotes.app.reference.*

/** Official document distribution stays OFF. User-supplied references remain clearly unverified. */
@Composable internal fun DrugDocumentPanel(product:UserReferenceProduct) {
    Column(Modifier.fillMaxWidth().testTag("drug-document-panel"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.drug_docs_title),style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{heading()})
        DrugDocumentKind.entries.forEach {kind->
            OutlinedCard(Modifier.fillMaxWidth().testTag("drug-document:${kind.name}")) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(if(kind==DrugDocumentKind.RCP)R.string.drug_docs_rcp else R.string.drug_docs_notice),style=MaterialTheme.typography.titleSmall)
                    when(val state=DrugDocuments.forProduct(product,kind)) {
                        is DocumentAvailability.Unavailable->Text(stringResource(R.string.drug_docs_unverified))
                        is DocumentAvailability.Entry->{
                            Text(stringResource(R.string.drug_docs_entry_verified,state.evidence.pageIdentityVerifiedAt.orEmpty()))
                            Text(stringResource(R.string.drug_docs_body_not_verified))
                            // No content is embedded; a future approved metadata registry supplies the exact public entry.
                            PublicProductPageAction(product.copy(sourceUrl=state.evidence.sourceUrl))
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.drug_docs_distribution_off),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.drug_docs_no_advice),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun PublicProductPageAction(product:UserReferenceProduct,
    open:((String,String?,Long?,Boolean)->DocumentOpenResult)?=null) {
    val context=LocalContext.current
    val keyboard=LocalSoftwareKeyboardController.current
    val activity=context.documentActivity()
    val owner=remember(activity,product){activity?.documentNavigationGeneration}
    var confirming by remember(product){mutableStateOf(false)}
    var error by remember(product){mutableStateOf<DocumentOpenResult?>(null)}
    val url=product.sourceUrl ?: return
    val valid=PublicProductPage.valid(url,product.cis)
    if(valid) {
        Text(url)
        TextButton(onClick={keyboard?.hide();confirming=true;error=null},modifier=Modifier.heightIn(min=48.dp).testTag("document-public-page")) {Text(stringResource(R.string.ref2_open_source))}
    } else Text(stringResource(R.string.drug_docs_invalid_link),modifier=Modifier.testTag("document-invalid-link"))
    error?.let {result->Text(stringResource(if(result==DocumentOpenResult.NOT_AUTHORIZED)R.string.drug_docs_access_changed else R.string.ref2_browser_error),
        modifier=Modifier.testTag("document-open-error"),color=MaterialTheme.colorScheme.error)}
    if(confirming)AlertDialog(onDismissRequest={confirming=false},title={Text(stringResource(R.string.ref2_open_source))},
        text={Column(Modifier.heightIn(max=LocalConfiguration.current.screenHeightDp.dp*0.45f).verticalScroll(rememberScrollState())) {Text(stringResource(R.string.drug_docs_external))}},
        confirmButton={TextButton(modifier=Modifier.heightIn(min=48.dp).testTag("document-open-confirm"),onClick={
            if(confirming) {
                confirming=false // Consume this confirmation once, including a queued second click.
                val result=open?.invoke(url,product.cis,owner,true) ?: PublicDocumentNavigation.open(context,url,product.cis,owner,true)
                error=result.takeIf{it!=DocumentOpenResult.OPENED}
            }
        }) {Text(stringResource(R.string.ref2_open))}},
        dismissButton={TextButton(modifier=Modifier.heightIn(min=48.dp).testTag("document-open-cancel"),onClick={confirming=false}){Text(stringResource(R.string.cancel))}})
}

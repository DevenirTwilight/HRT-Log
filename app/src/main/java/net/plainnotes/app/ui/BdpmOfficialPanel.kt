package net.plainnotes.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import org.json.JSONArray
import org.json.JSONObject

/** UI-11 inline disclosure rows over hash-verified public data. No user writes or in-app network requests. */
@Composable internal fun BdpmOfficialPanel(cis:String,expectedName:String?=null) {
    val context=LocalContext.current
    val catalogue=remember(context) {BdpmCatalogue(context)}
    var activeCis by remember(cis) {mutableStateOf(cis)}
    var visible by rememberSaveable(cis) {mutableStateOf(false)}
    var entry by remember(activeCis) {mutableStateOf<BdpmOfficialDetails?>(null)}
    var failed by remember(activeCis) {mutableStateOf(false)}
    var expanded by rememberSaveable(activeCis) {mutableStateOf("")}
    var link by remember(activeCis) {mutableStateOf<String?>(null)}
    var browserFailed by remember(activeCis) {mutableStateOf(false)}
    LaunchedEffect(activeCis,expectedName,visible) {
        if(!visible)return@LaunchedEffect
        try {entry=catalogue.official(activeCis,if(activeCis==cis)expectedName else null);failed=entry==null}
        catch(e:CancellationException){throw e}
        catch(_:Exception){failed=true}
    }
    val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("bdpm-official:$activeCis"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color=c.outlineVariant)
        TextButton(onClick={visible=!visible},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("bdpm-official-toggle")) {
            Text(stringResource(R.string.official_title),style=MaterialTheme.typography.titleSmall)
        }
        if(!visible)return@Column
        if(activeCis!=cis)TextButton(onClick={activeCis=cis}) {Text(stringResource(R.string.official_original_product))}
        if(failed)Text(stringResource(R.string.official_unavailable),modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
        else if(entry==null)LinearProgressIndicator(Modifier.fillMaxWidth())
        entry?.let {d->
            Text("${d.name} · CIS ${d.cis}",style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.official_snapshot,d.provenance.getString("downloaded_on")),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            @Composable fun section(key:String,title:Int,files:List<String>,content:@Composable ()->Unit) {
                val open=key in expanded.split(',')
                OfficialDisclosure(stringResource(title),open,"bdpm-official-$key",{expanded=(expanded.split(',').filter {it.isNotBlank()}.toSet().let {if(open)it-key else it+key}).joinToString(",")}) {
                    val unknown=stringResource(R.string.official_generation_unknown)
                    if(files.isNotEmpty())Text(files.joinToString("\n") {it+" · "+(d.sourceDate(it) ?: unknown)},style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                    content()
                }
            }
            section("prescribing",R.string.official_prescribing,listOf("CIS_CPD_bdpm.txt")) {
                val rows=d.data.getJSONArray("cpd")
                if(rows.length()==0)Text(stringResource(R.string.official_cpd_absent))
                for(i in 0 until rows.length())Text(if(rows.isNull(i))stringResource(R.string.ref2_unknown) else rows.getString(i))
            }
            section("generics",R.string.official_generics,listOf("CIS_GENER_bdpm.txt")) {
                Text(stringResource(R.string.official_generic_boundary))
                val groups=d.rows("groups")
                if(groups.isEmpty())Text(stringResource(R.string.official_not_provided))
                groups.forEach {g->
                    Text("${g.getString("id")} · ${g.getString("label")}",style=MaterialTheme.typography.labelLarge)
                    val members=g.getJSONArray("members")
                    for(i in 0 until members.length()) {
                        val m=members.getJSONObject(i)
                        val kind=when(m.getString("type")){"0"->"princeps";"1"->"générique";"2"->"générique par complémentarité posologique";"4"->"générique substituable";else->m.getString("type")}
                        val label="${m.nullable("name") ?: stringResource(R.string.ref2_unknown)} · CIS ${m.getString("cis")} · $kind"
                        if(m.isNull("name"))Text(label,style=MaterialTheme.typography.bodySmall)
                        else TextButton(onClick={activeCis=m.getString("cis")},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(label)}
                    }
                }
            }
            section("has",R.string.official_has,listOf("CIS_HAS_SMR_bdpm.txt","CIS_HAS_ASMR_bdpm.txt","HAS_LiensPageCT_bdpm.txt")) {
                Text(stringResource(R.string.official_has_boundary))
                val rows=d.rows("assessments")
                if(rows.isEmpty())Text(stringResource(R.string.official_assessment_absent))
                rows.forEachIndexed {i,r->
                    val key="assessment-$i";val open=key in expanded.split(',')
                    OfficialDisclosure("${r.getString("kind")} · ${r.nullable("date") ?: "—"} · ${r.getString("value")} · ${r.getString("dossier")}",open,"bdpm-$key",{expanded=(expanded.split(',').filter {it.isNotBlank()}.toSet().let {if(open)it-key else it+key}).joinToString(",")}) {
                        Text(r.getString("reason"));Text(r.getString("text"))
                        r.getJSONArray("links").strings().forEach {u->OfficialLink(u,R.string.official_has_link){link=it}}
                    }
                }
            }
            section("mitm",R.string.official_mitm,listOf("CIS_MITM.txt","CIS_bdpm.txt")) {
                Text(stringResource(R.string.official_mitm_boundary))
                if(d.rows("mitm").isEmpty())Text(stringResource(R.string.official_not_provided))
                d.rows("mitm").forEach {r->
                    Text("ATC ${r.getString("atc")} · ${r.getString("name")}")
                    Text("${r.getString("holder")}\n${r.getString("operator")}\n${r.nullable("date") ?: "—"}",style=MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.official_monitoring,d.data.nullable("monitoring") ?: stringResource(R.string.ref2_unknown)))
            }
            section("documents",R.string.official_documents,emptyList()) {
                Text(stringResource(R.string.official_documents_boundary))
                val docs=d.data.getJSONObject("documents")
                val verified=docs.optBoolean("verified") && docs.optString("cis")==d.cis
                if(verified) {
                    Text(stringResource(R.string.official_document_verified,docs.getString("retrieved_at").take(10),docs.nullable("source_updated_on") ?: stringResource(R.string.ref2_unknown)),style=MaterialTheme.typography.bodySmall)
                    val url=docs.getString("url")
                    if(docs.optBoolean("rcp"))OfficialLink(url+"#tabpanel-rcp-panel",R.string.drug_center_rcp){link=it}
                    if(docs.optBoolean("notice"))OfficialLink(url+"#tabpanel-notice-panel",R.string.drug_center_notice){link=it}
                    val ema=docs.optJSONArray("ema_links")?.strings().orEmpty()
                    if(ema.isNotEmpty()) {
                        Text(stringResource(R.string.official_ema_combined))
                        ema.forEach {u->OfficialLink(u,R.string.official_ema_link){link=it}}
                    }
                    if(!docs.optBoolean("rcp") || !docs.optBoolean("notice"))Text(stringResource(R.string.official_document_unavailable))
                    OfficialLink(url,R.string.official_product_page){link=it}
                } else Text(stringResource(R.string.official_document_unavailable))
            }
            section("safety",R.string.drug_center_safety,listOf("CIS_InfoImportantes.txt")) {
                Text(stringResource(R.string.official_safety_boundary))
                if(d.rows("important").isEmpty())Text(stringResource(R.string.official_not_provided))
                d.rows("important").forEach {r->
                    Text(r.getString("text"));Text(stringResource(R.string.official_validity,r.nullable("start") ?: "—",r.nullable("end") ?: "—"),style=MaterialTheme.typography.bodySmall)
                    r.getJSONArray("links").strings().forEach {u->OfficialLink(u,R.string.official_source_link){link=it}}
                }
            }
            section("supply",R.string.official_supply,listOf("CIS_CIP_Dispo_Spec.txt")) {
                Text(stringResource(R.string.official_supply_boundary))
                if(d.rows("supply").isEmpty())Text(stringResource(R.string.official_not_provided))
                d.rows("supply").forEach {r->
                    Text("${r.getString("label")} · ${r.getString("code")}")
                    Text(r.nullable("cip13")?.let {"CIP13 $it"} ?: stringResource(R.string.official_all_packs))
                    Text(stringResource(R.string.official_supply_dates,r.nullable("start") ?: "—",r.nullable("updated") ?: "—",r.nullable("available") ?: "—"),style=MaterialTheme.typography.bodySmall)
                    r.nullable("url")?.let {u->OfficialLink(u,R.string.official_source_link){link=it}}
                }
            }
            section("sources",R.string.official_sources,emptyList()) {
                Text(stringResource(R.string.official_source_boundary))
                Text(d.provenance.getString("license"),style=MaterialTheme.typography.bodySmall)
                val inputs=d.provenance.getJSONObject("inputs")
                inputs.keys().asSequence().sorted().forEach {name->
                    val input=inputs.getJSONObject(name)
                    Text("$name\n${input.nullable("source_updated_on") ?: stringResource(R.string.official_generation_unknown)}\n${input.getString("url")}\nSHA-256 ${input.getString("sha256")}",style=MaterialTheme.typography.bodySmall)
                }
                OfficialLink(d.provenance.getString("download_page"),R.string.official_source_link){link=it}
            }
        }
        if(browserFailed)Text(stringResource(R.string.ref2_browser_error),color=c.error)
    }
    link?.let {url->OfficialBrowserConfirmation(url,{link=null}) {browserFailed=true}}

}

private fun JSONObject.nullable(key:String)=if(isNull(key))null else optString(key).takeIf {it.isNotBlank()}
private fun JSONArray.strings()=(0 until length()).map {getString(it)}
@Composable private fun OfficialLink(url:String,label:Int,onOpen:(String)->Unit) {
    if(BdpmOfficialLinks.safe(url))TextButton(onClick={onOpen(url)},modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(label))}
}
@Composable private fun OfficialDisclosure(title:String,open:Boolean,tag:String,onToggle:()->Unit,content:@Composable ()->Unit) {
    val c=MaterialTheme.colorScheme
    val turn by animateFloatAsState(if(open)90f else 0f,motionSpec(LocalReduceMotion.current),label="official-chevron")
    val spoken=stringResource(if(open)R.string.drug_state_expanded else R.string.drug_state_collapsed)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(role=Role.Button,onClick=onToggle).testTag(tag)
            .semantics(mergeDescendants=true){stateDescription=spoken}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,Modifier.graphicsLayer {rotationZ=turn},tint=c.onSurfaceVariant)
        }
        if(open)Column(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){content()}
    }
}

/** Explicit, shared browser confirmation; public URL only, no private request values or persisted authority. */
@Composable internal fun OfficialBrowserConfirmation(url:String,onDismiss:()->Unit,onFailure:()->Unit) {
    val context=LocalContext.current
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.official_external_title))},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(stringResource(R.string.official_external_body));Text(url,style=MaterialTheme.typography.bodySmall)}},
        confirmButton={TextButton(onClick={onDismiss();if(!BdpmOfficialLinks.safe(url) || runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.isFailure)onFailure()},modifier=Modifier.testTag("bdpm-official-open-confirm")){Text(stringResource(R.string.ref2_open))}},
        dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
@Composable internal fun BdpmOfficialExternalLink(url:String,label:Int) {
    var pending by remember {mutableStateOf(false)}
    var failed by remember {mutableStateOf(false)}
    OfficialLink(url,label) {pending=true}
    if(pending)OfficialBrowserConfirmation(url,{pending=false}) {failed=true}
    if(failed)Text(stringResource(R.string.ref2_browser_error),color=MaterialTheme.colorScheme.error)
}

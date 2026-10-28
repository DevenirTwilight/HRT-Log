package net.plainnotes.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import net.plainnotes.app.R
import net.plainnotes.app.data.BdpmCatalogue
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.MedicationReference
import org.json.JSONObject

/** How a medication is linked to a product source, read from the existing reference association only. */
enum class DrugLinkStatus { OFFICIAL, USER_ENTERED, NONE }

/** Official = a snapshot copied from the BDPM catalogue (it carries BDPM provenance); anything else linked is user-entered. */
fun drugLinkStatus(reference: MedicationReference?): DrugLinkStatus {
    val product = reference?.product ?: return DrugLinkStatus.NONE
    return if (product.bdpm != null) DrugLinkStatus.OFFICIAL else DrugLinkStatus.USER_ENTERED
}

/** Facts for the "Sources" rows. A null field is never invented: the row shows a fixed description instead. */
data class DrugSourceInfo(val bdpmDate: String? = null, val savedCount: Int? = null, val savedLimited: Boolean = false)

/** The only IO the centre needs, injected by the shell: link statuses of the listed medications and the source facts. */
class DrugCenterLoaders(val links: suspend (List<Long>) -> Map<Long, DrugLinkStatus>, val sources: suspend () -> DrugSourceInfo)

/** Tests read a tag's tone here (not spoken): official, user or neutral. */
val DrugTagToneKey = SemanticsPropertyKey<String>("DrugTagTone")
var SemanticsPropertyReceiver.drugTagTone by DrugTagToneKey

/** Container / text colours of a tag, only from the UI-06 semantic tokens (official = success, user-entered = attention, other = neutral). */
fun drugTagColors(status: DrugLinkStatus, semantic: SemanticColors, scheme: ColorScheme): Pair<Color, Color> = when (status) {
    DrugLinkStatus.OFFICIAL -> semantic.success.container to semantic.success.onContainer
    DrugLinkStatus.USER_ENTERED -> semantic.attention.container to semantic.attention.onContainer
    DrugLinkStatus.NONE -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
}

private fun DrugLinkStatus.tone() = when (this) { DrugLinkStatus.OFFICIAL -> "official"; DrugLinkStatus.USER_ENTERED -> "user"; DrugLinkStatus.NONE -> "neutral" }

/** Not a merging node: inside a tappable row its words join the row's spoken text, so TalkBack reads the status too. */
@Composable internal fun DrugTag(text: String, status: DrugLinkStatus, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val (container, content) = drugTagColors(status, NotesTheme.semantic, MaterialTheme.colorScheme)
    Surface(color = container, contentColor = content, shape = Corners.pill, modifier = modifier.semantics { drugTagTone = status.tone() }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

/** The "not open yet" tag: outlined, neutral. */
@Composable private fun NotOpenTag(modifier: Modifier = Modifier) {
    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shape = Corners.pill,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = modifier.semantics { drugTagTone = "outlined" }) {
        Text(stringResource(R.string.drug_not_open_tag), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

/** UI-11 A: the shared back button; the accessible name is `nav_back`, never `cancel`. */
@Composable fun NavBackButton(onClick: () -> Unit, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(48.dp).testTag(tag)) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.nav_back))
    }
}

/** Loads the facts the centre shows from the existing reference APIs. Failures degrade to "unknown", never to invented values. */
suspend fun loadDrugSourceInfo(context: Context, saved: suspend () -> Pair<Int, Boolean>): DrugSourceInfo {
    val date = try {
        JSONObject(BdpmCatalogue(context).search("").metadata).getString("downloaded_on").takeIf { it.isNotBlank() }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    val count = try { saved() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    return DrugSourceInfo(date, count?.first, count?.second ?: false)
}

/** A view over the existing medication/reference APIs. No clinical advice or inventory writes. */
@Composable fun DrugReferenceCenterScreen(medications: List<MedicationEntity>, onBrowse: () -> Unit,
    onReference: (MedicationEntity) -> Unit, contentPadding: PaddingValues,
    links: Map<Long, DrugLinkStatus> = emptyMap(), sources: DrugSourceInfo = DrugSourceInfo()) {
    val c = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf("") }
    val open = expanded.split(',').filter { it.isNotEmpty() }.toSet()
    fun toggle(tag: String) { expanded = (if (tag in open) open - tag else open + tag).joinToString(",") }
    val sorted = remember(medications) { medications.sortedWith(compareByDescending<MedicationEntity> { it.active }.thenBy { it.sort_order }.thenBy { it.id }) }
    LazyColumn(Modifier.fillMaxSize().testTag("drug-center-list"), contentPadding = PaddingValues(
        start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 32.dp), verticalArrangement = Arrangement.spacedBy(GroupedGap)) {
        item("hero") { DrugCenterHero(onBrowse) }
        item("medications-title") { SectionHeader(R.string.drug_center_medications) }
        if (sorted.isEmpty()) item("empty") { Text(stringResource(R.string.drug_center_empty), color = c.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
        items(sorted.size, key = { "medication:${sorted[it].id}" }) { i ->
            val m = sorted[i]
            GroupedItem(i, sorted.size, onClick = { onReference(m) }, modifier = Modifier.testTag("drug-center-medication:${m.id}")) {
                MedicationRow(m, links[m.id])
            }
        }
        if (sorted.isNotEmpty()) item("link-caption") { Caption(R.string.drug_link_caption) }
        item("sources-title") { SectionHeader(R.string.drug_sources_title) }
        item("source-bdpm") {
            GroupedItem(0, 2, modifier = Modifier.testTag("drug-center-source-bdpm")) {
                SourceRow(Icons.Outlined.Shield, R.string.drug_src_bdpm_title,
                    sources.bdpmDate?.let { stringResource(R.string.drug_src_bdpm_info, it) } ?: stringResource(R.string.drug_src_bdpm_info_plain), DrugLinkStatus.OFFICIAL, R.string.drug_src_official_tag)
            }
        }
        item("source-user") {
            GroupedItem(1, 2, modifier = Modifier.testTag("drug-center-source-user")) {
                val info = sources.savedCount?.let { stringResource(if (sources.savedLimited) R.string.drug_src_user_info_many else R.string.drug_src_user_info, it) }
                    ?: stringResource(R.string.drug_src_user_info_plain)
                SourceRow(Icons.Outlined.Edit, R.string.drug_src_user_title, info, DrugLinkStatus.USER_ENTERED, R.string.drug_src_unverified_tag)
            }
        }
        item("learn-title") { SectionHeader(R.string.drug_center_learn_title) }
        val learn = listOf(Triple(R.string.drug_center_identity, R.string.drug_center_identity_body, "drug-center-identity"),
            Triple(R.string.drug_center_packaging, R.string.drug_center_packaging_body, "drug-center-packaging"),
            Triple(R.string.drug_center_states_title, R.string.drug_center_states, "drug-center-states"))
        items(learn.size, key = { "learn:${learn[it].third}" }) { i ->
            val (title, body, tag) = learn[i]
            GroupedItem(i, learn.size, modifier = Modifier.testTag(tag)) {
                ExpandableRow(stringResource(title), stringResource(body), tag in open, { toggle(tag) }, tag)
            }
        }
        item("official-title") { SectionHeader(R.string.official_title) }
        item("official-help") { Caption(R.string.official_choose_product) }
        val available=listOf(R.string.drug_center_rcp to "drug-center-rcp",R.string.drug_center_notice to "drug-center-notice",
            R.string.drug_center_safety to "drug-center-safety",R.string.center_shortages to "drug-center-supply")
        items(available.size,key={"official:${available[it].second}"}) {i->
            val (title,tag)=available[i]
            GroupedItem(i,available.size,onClick=onBrowse,modifier=Modifier.testTag(tag)) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    IconTile(futureIcon(tag),c.secondaryContainer,c.onSecondaryContainer)
                    Text(stringResource(title),style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,tint=c.onSurfaceVariant)
                }
            }
        }
        item("future-title") { SectionHeader(R.string.center_future_modules) }
        item("recalls") {GroupedItem(0,1,color=c.surfaceContainerLow) {
            Column {
                FutureRow(R.string.center_recalls,R.string.official_recall_limit,"drug-center-recalls",futureIcon("drug-center-recalls"))
                BdpmOfficialExternalLink("https://ansm.sante.fr/informations-de-securite",R.string.official_recall_query)
            }
        }}

    }
}

private fun futureIcon(tag: String) = when (tag) {
    "drug-center-rcp" -> Icons.Outlined.Description
    "drug-center-notice" -> Icons.AutoMirrored.Outlined.MenuBook
    "drug-center-safety" -> Icons.Outlined.HealthAndSafety
    "drug-center-supply" -> Icons.Outlined.Inventory2
    else -> Icons.Outlined.NotificationsNone
}

@Composable private fun SectionHeader(@StringRes title: Int) {
    Text(stringResource(title), style = sectionTitleStyle, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp).semantics { heading() })
}

@Composable private fun Caption(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable private fun IconTile(icon: ImageVector, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = Corners.m, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp)) }
    }
}

/** UI-11 B: success-container card; region, title, scope and the white pill search entry. */
@Composable private fun DrugCenterHero(onBrowse: () -> Unit) {
    val sem = NotesTheme.semantic
    Surface(color = sem.success.container, contentColor = sem.success.onContainer, shape = Corners.xxl, modifier = Modifier.fillMaxWidth().testTag("drug-center-hero")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, contentColor = MaterialTheme.colorScheme.onSurface, shape = Corners.pill) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.drug_center_region), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                }
            }
            Text(stringResource(R.string.drug_center_find_title), style = NotesTheme.typeExtra.cardTitle, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.drug_center_scope), style = MaterialTheme.typography.bodyMedium)
            Surface(onClick = onBrowse, shape = Corners.pill, color = MaterialTheme.colorScheme.surfaceContainerLowest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("drug-center-catalogue")) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.drug_center_search), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable private fun MedicationRow(m: MedicationEntity, status: DrugLinkStatus?) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Icons.Outlined.Medication, c.secondaryContainer, c.onSecondaryContainer)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.name, style = MaterialTheme.typography.titleSmall, color = if (m.active) c.onSurface else c.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                status?.let {
                    DrugTag(stringResource(when (it) { DrugLinkStatus.OFFICIAL -> R.string.drug_link_official; DrugLinkStatus.USER_ENTERED -> R.string.drug_link_user; DrugLinkStatus.NONE -> R.string.drug_link_none }),
                        it, Modifier.testTag("drug-center-link-tag:${m.id}"))
                }
                if (!m.active) DrugTag(stringResource(R.string.archived), DrugLinkStatus.NONE, Modifier.testTag("drug-center-archived-tag:${m.id}"))
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.onSurfaceVariant)
    }
}

@Composable private fun SourceRow(icon: ImageVector, @StringRes title: Int, info: String, status: DrugLinkStatus, @StringRes tag: Int) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, c.secondaryContainer, c.onSecondaryContainer)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(info, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            DrugTag(stringResource(tag), status)
        }
    }
}

@Composable private fun ExpandableRow(title: String, body: String, expanded: Boolean, onToggle: () -> Unit, tag: String) {
    val reduce = LocalReduceMotion.current
    val turn by animateFloatAsState(if (expanded) 90f else 0f, motionSpec(reduce), label = "chevron")
    val stateText = stringResource(if (expanded) R.string.drug_state_expanded else R.string.drug_state_collapsed)
    Column(Modifier.fillMaxWidth().animateContentSize(motionSpec(reduce))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) { stateDescription = stateText }.testTag("$tag-toggle").padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.graphicsLayer { rotationZ = turn }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).testTag("$tag-body"))
    }
}

@Composable private fun FutureRow(@StringRes title: Int, @StringRes reason: Int, tag: String, icon: ImageVector) {
    val c = MaterialTheme.colorScheme
    val name = stringResource(title)
    val spoken = stringResource(R.string.drug_not_open_desc, name)
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag(tag).clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, c.surfaceContainerHigh, c.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(reason), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            NotOpenTag()
        }
    }
}

/** Existing AppShell hosts this only inside the unlocked real application. Nested reference dialog owns its write state. */
@Composable fun DrugReferenceCenterDialog(medications: List<MedicationEntity>, onBrowse: () -> Unit,
    onReference: (MedicationEntity) -> Unit, loaders: DrugCenterLoaders? = null, linkVersion: Int = 0, onDismiss: () -> Unit) {
    val ids = medications.map { it.id }
    var links by remember { mutableStateOf<Map<Long, DrugLinkStatus>>(emptyMap()) }
    var sources by remember { mutableStateOf(DrugSourceInfo()) }
    LaunchedEffect(ids, linkVersion, loaders) {
        if (loaders == null) return@LaunchedEffect
        links = try { loaders.links(ids) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }
        sources = try { loaders.sources() } catch (e: CancellationException) { throw e } catch (_: Exception) { DrugSourceInfo() }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(modifier = Modifier.testTag("drug-center-window"), topBar = {
            Surface {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NavBackButton(onDismiss, "drug-center-close")
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.drug_center_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                DrugReferenceCenterScreen(medications, onBrowse, onReference, PaddingValues(), links, sources)
            }
        }
    }
}

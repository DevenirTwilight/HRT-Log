package net.plainnotes.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.plainnotes.app.R
import net.plainnotes.app.domain.LabCatalog
import net.plainnotes.app.domain.LabCategory
import java.text.Normalizer
import java.util.Locale

internal fun labSearchKey(value:String):String = Normalizer.normalize(value,Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)

internal fun filterLabChoices(names:List<Pair<String,String>>,query:String,category:LabCategory?):List<Pair<String,String>> {
    val search=labSearchKey(query.trim())
    return names.filter { (code,label)->(category==null || LabCatalog.categoryFor(code)==category) &&
        (search.isEmpty() || labSearchKey("$code $label").contains(search)) }
}

@Composable internal fun labCategoryLabel(category:LabCategory):String=stringResource(when(category) {
    LabCategory.HORMONES->R.string.lab_catalog_category_hormones
    LabCategory.CBC->R.string.lab_catalog_category_cbc
    LabCategory.LIVER->R.string.lab_catalog_category_liver
    LabCategory.KIDNEY->R.string.lab_catalog_category_kidney
    LabCategory.LIPIDS->R.string.lab_catalog_category_lipids
    LabCategory.GLUCOSE_METABOLISM->R.string.lab_catalog_category_glucose_metabolism
    LabCategory.OTHER->R.string.lab_catalog_category_other
})

/** A draft-only selector; opening, searching and canceling never writes a lab or changes the caller's row. */
@Composable internal fun LabAnalytePicker(code:String,onPick:(String)->Unit,modifier:Modifier=Modifier) {
    var open by remember { mutableStateOf(false) }
    val selected=if(code in setOf("P4_IA","P4_MS"))"P4" else code
    OutlinedButton(onClick={open=true},modifier=modifier.fillMaxWidth().heightIn(min=48.dp)) {
        Text(if(selected=="P4")stringResource(R.string.lab_p4_family) else analyteLabel(selected))
    }
    if(open) {
        var query by remember {mutableStateOf("")}
        var category by remember {mutableStateOf<LabCategory?>(null)}
        val names=ANALYTES.filter{it.code !in setOf("P4_IA","P4_MS")}.map{it.code to if(it.code=="P4")stringResource(R.string.lab_p4_family) else analyteLabel(it.code)}
        val shown=filterLabChoices(names,query,category)
        val focus=LocalFocusManager.current
        Dialog(onDismissRequest={open=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxSize().testTag("lab-analyte-picker"),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(end=16.dp),verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={open=false}){Icon(Icons.Outlined.Close,stringResource(R.string.cancel))}
                        Text(stringResource(R.string.lab_catalog_picker_title),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    }
                    OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=16.dp).testTag("lab-analyte-search"),
                        label={Text(stringResource(R.string.lab_catalog_search))},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus()}))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(category==null,{category=null},label={Text(stringResource(R.string.lab_catalog_category_all))})
                        LabCategory.entries.forEach{c->FilterChip(category==c,{category=c},label={Text(labCategoryLabel(c))},modifier=Modifier.testTag("lab-analyte-category:${c.name}"))}
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(horizontal=16.dp)) {
                        if(shown.isEmpty())item{Text(stringResource(R.string.lab_catalog_no_results),Modifier.padding(vertical=16.dp))}
                        items(shown,key={it.first}){(c,label)->
                            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).selectable(selected==c,role=Role.RadioButton,onClick={
                                focus.clearFocus();if(c!=selected)onPick(c);open=false
                            }).semantics(mergeDescendants=true){}.testTag("lab-analyte-choice:$c"),verticalAlignment=Alignment.CenterVertically) {
                                RadioButton(selected==c,onClick=null)
                                Text(label,Modifier.weight(1f).padding(vertical=12.dp),style=MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    TextButton(onClick={open=false},modifier=Modifier.fillMaxWidth().padding(8.dp)){Text(stringResource(R.string.cancel))}
                }
            }
        }
    }
}

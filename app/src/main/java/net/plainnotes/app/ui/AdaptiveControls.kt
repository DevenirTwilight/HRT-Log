package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*

/** Size the whole group from its longest translated label; never squeeze a later action to zero. */
@Composable fun AdaptiveActions(labels:List<String>, modifier:Modifier=Modifier, contentInset:Dp=80.dp, content:@Composable (Int,Modifier)->Unit) {
    val measure=rememberTextMeasurer();val density=LocalDensity.current;val style=MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Button padding plus the optional leading icon. This is spacing, not a fixed control width.
        val inset=contentInset
        val widths=labels.map{with(density){measure.measure(AnnotatedString(it),style,softWrap=false).size.width.toDp()}}
        val inline=(widths.maxOrNull() ?: 0.dp)+inset <= (maxWidth-8.dp*(labels.size-1))/labels.size.coerceAtLeast(1)
        val textWidth=with(density){(if(inline)(maxWidth-8.dp*(labels.size-1))/labels.size else maxWidth).minus(inset).roundToPx().coerceAtLeast(1)}
        val height=maxOf(48.dp,with(density){labels.maxOfOrNull{measure.measure(AnnotatedString(it),style,constraints=Constraints(maxWidth=textWidth)).size.height}?.toDp() ?: 0.dp}+16.dp)
        if(inline) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            labels.indices.forEach{content(it,Modifier.weight(1f).fillMaxHeight().heightIn(min=height))}
        } else Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            labels.indices.forEach{content(it,Modifier.fillMaxWidth().heightIn(min=height))}
        }
    }
}

/** Keep segmented choices when they fit; otherwise every whole radio row remains selectable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AdaptiveChoice(labels:List<String>, selected:Int, onSelect:(Int)->Unit, modifier:Modifier=Modifier,
                                groupLabels:List<String> = labels, selectionIcon:Boolean=true) {
    val measure=rememberTextMeasurer();val density=LocalDensity.current;val style=MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widest=groupLabels.maxOf{with(density){measure.measure(AnnotatedString(it),style,softWrap=false).size.width.toDp()}}
        if((widest+(if(selectionIcon)48.dp else 24.dp))*labels.size<=maxWidth) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                labels.forEachIndexed{i,label->SegmentedButton(selected==i,{onSelect(i)},SegmentedButtonDefaults.itemShape(i,labels.size),
                    modifier=Modifier.fillMaxHeight().heightIn(min=48.dp),icon={if(selectionIcon)SegmentedButtonDefaults.Icon(active=selected==i)}){Text(label)}}
            }
        } else {
            val labelWidth=with(density){(maxWidth-64.dp).roundToPx().coerceAtLeast(1)}
            val height=maxOf(48.dp,with(density){groupLabels.maxOf{measure.measure(AnnotatedString(it),style,constraints=Constraints(maxWidth=labelWidth)).size.height}.toDp()}+16.dp)
            Column {
                labels.forEachIndexed{i,label->Row(Modifier.fillMaxWidth().heightIn(min=height).selectable(selected==i,role=Role.RadioButton,onClick={onSelect(i)}),
                    verticalAlignment=Alignment.CenterVertically){RadioButton(selected==i,null);Text(label,Modifier.weight(1f).padding(end=16.dp),style=style)}}
            }
        }
    }
}

/** Settings choices retain their button presentation: horizontal segments or equal full-width buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AdaptiveButtonChoice(labels:List<String>, selected:Int, onSelect:(Int)->Unit,
                                     modifier:Modifier=Modifier, groupLabels:List<String> = labels) {
    val measure=rememberTextMeasurer();val density=LocalDensity.current;val style=MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widest=groupLabels.maxOf{with(density){measure.measure(AnnotatedString(it),style,softWrap=false).size.width.toDp()}}
        val inline=(widest+48.dp)*labels.size<=maxWidth
        val labelWidth=with(density){(if(inline)maxWidth/labels.size-48.dp else maxWidth-80.dp).roundToPx().coerceAtLeast(1)}
        val height=maxOf(48.dp,with(density){groupLabels.maxOf{measure.measure(AnnotatedString(it),style,constraints=Constraints(maxWidth=labelWidth)).size.height}.toDp()}+16.dp)
        if(inline) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(height)) {
                labels.forEachIndexed{i,label->SegmentedButton(selected==i,{onSelect(i)},SegmentedButtonDefaults.itemShape(i,labels.size),
                    modifier=Modifier.fillMaxHeight()){Text(label)}}
            }
        } else Column(Modifier.fillMaxWidth().selectableGroup(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed{i,label->
                val active=selected==i
                OutlinedButton(onClick={onSelect(i)},modifier=Modifier.fillMaxWidth().height(height).semantics{this.selected=active},
                    colors=ButtonDefaults.outlinedButtonColors(containerColor=if(active)MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor=if(active)MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface),
                    border=BorderStroke(1.dp,if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(18.dp)){if(active)Icon(Icons.Outlined.Check,null,Modifier.fillMaxSize())}
                        Text(label,Modifier.weight(1f),textAlign=TextAlign.Center)
                    }
                }
            }
        }
    }
}

package net.plainnotes.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * UI-09 C / D (REQUIREMENTS §146): two borrowings from the Swiss style, kept inside the Material 3 Expressive look —
 * thin rules instead of some card fills, and numbered section headings. Display only: nothing here is stored or exported.
 */

/** Tests read a rule's colour here; it is not spoken. */
val RuleColorKey = SemanticsPropertyKey<Color>("SwissRuleColor")
var SemanticsPropertyReceiver.ruleColor by RuleColorKey
/** The section number shown, for tests; the number node has no text or description, so it is never read aloud. */
val SectionNumberKey = SemanticsPropertyKey<Int>("SwissSectionNumber")
var SemanticsPropertyReceiver.sectionNumber by SectionNumberKey

/** The thin rule's colour: outlineVariant, or outline in high contrast (the rule is decoration; headings and space carry the structure). */
@Composable fun sectionRuleColor(): Color =
    if (NotesTheme.semantic.contrast == Contrast.HIGH) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant

/** A 1 dp thin rule between sections. */
@Composable fun SectionRule(modifier: Modifier = Modifier) {
    val color = sectionRuleColor()
    HorizontalDivider(modifier.semantics { ruleColor = color }, thickness = 1.dp, color = color)
}

/** "01" before a section title: tabular digits, primary, one step smaller than the title, left aligned, never read aloud. */
@Composable fun NumberedHeading(number: Int, title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        Text("%02d".format(number), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("section-number").clearAndSetSemantics { sectionNumber = number })
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
    }
}

/**
 * A numbered section without a card: a thin rule above (except the first), the numbered heading, then [content] with 12 dp
 * between items. [first] drops the rule so a list does not start with a line.
 */
@Composable fun NumberedSection(number: Int, title: String, modifier: Modifier = Modifier, first: Boolean = number == 1, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!first) SectionRule(Modifier.testTag("section-rule"))
        NumberedHeading(number, title)
        content()
    }
}

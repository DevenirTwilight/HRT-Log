package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class Lab03EntryUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private fun type(tag:String,value:String)=ui.onNodeWithTag(tag).performScrollTo().performTextInput(value)
    private fun exact(tag:String,value:String)=ui.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(value)))

    @Test fun pickerSearchCancelAndSameSelectionPreserveTypedDraft() {
        val draft=LabBatchDraft();var saves=0
        ui.setContent{NotesTheme(ThemeMode.LIGHT){LabBatchDialog({}, {_,_->saves++},draft=draft)}}
        type("lab-batch-value:0","210")
        ui.onNodeWithTag("lab-batch-analyte:0").performScrollTo().performClick()
        ui.onNodeWithTag("lab-analyte-search").performTextInput("NO_SUCH_SYNTHETIC_TEST")
        ui.onAllNodesWithTag("lab-analyte-choice:E2").assertCountEquals(0)
        ui.onNode(hasContentDescription(ui.activity.getString(R.string.cancel)) and hasAnyAncestor(hasTestTag("lab-analyte-picker"))).performClick()
        exact("lab-batch-value:0","210");assertEquals(0,saves)
        ui.onNodeWithTag("lab-batch-analyte:0").performClick()
        ui.onNodeWithTag("lab-analyte-search").performTextInput("E2")
        ui.onNodeWithTag("lab-analyte-choice:E2").performClick()
        exact("lab-batch-value:0","210");assertEquals("E2",draft.rows.single().code)
        ui.runOnIdle { draft.rows.single().apply {lo="100";hi="300";note="SYNTHETIC E2 assay"} }
        ui.onNodeWithTag("lab-batch-analyte:0").performClick()
        ui.onNodeWithTag("lab-analyte-search").performTextInput("TG")
        ui.onNodeWithTag("lab-analyte-choice:TG").performClick()
        assertEquals("TG",draft.rows.single().code);assertEquals(0,saves)
        assertEquals("",draft.rows.single().value);assertEquals("",draft.rows.single().lo);assertEquals("",draft.rows.single().hi);assertEquals("",draft.rows.single().note)
    }

    @Test fun twentyEightResultsRemainScrollableAndSaveOnceWithoutAnEstimate() {
        val saved=mutableListOf<List<LabValueEntity>>()
        ui.setContent{NotesTheme(ThemeMode.LIGHT){LabBatchDialog({}, {rows,estimate->assertFalse(estimate);saved+=rows})}}
        type("lab-batch-lab","SYNTHETIC broad report")
        listOf("CBC","LIPIDS","LIVER_EXTENDED").forEach{ui.onNodeWithTag("lab-preset:$it").performScrollTo().performClick()}
        repeat(28){type("lab-batch-value:$it",(it+1).toString())}
        ui.onNodeWithTag("lab-batch-estimate").performScrollTo().assertIsNotSelected()
        ui.onNodeWithTag("lab-batch-save").assertIsEnabled().performClick()
        assertEquals(1,saved.size);assertEquals(28,saved.single().size)
        assertEquals(28,saved.single().map{it.analyte_code}.toSet().size)
        assertEquals(28.0,saved.single().last().value,0.0)
        assertTrue(saved.single().all{it.laboratory=="SYNTHETIC broad report"})
    }

    @Test fun noteOnlyEditPreservesOriginalInstantZoneAndDistinctReferenceUnit() {
        val oldZone=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
        try {
            // Second occurrence of 02:30 during the autumn overlap; reconstructing the local time would change history.
            val original=LabValueEntity(id=99,analyte_code="TG",value=305.0,unit="mg/dL",
                sampled_utc=Instant.parse("2026-10-25T01:30:00Z").toEpochMilli(),sampled_zone="Europe/Paris",
                reference_upper=1.5,reference_unit="g/L",note="SYNTHETIC report")
            var saved:LabValueEntity?=null
            ui.setContent{NotesTheme(ThemeMode.LIGHT){LabDialog(original,{}, {saved=it})}}
            ui.onNode(hasSetTextAction() and hasText(ui.activity.getString(R.string.note))).performScrollTo().performTextReplacement("SYNTHETIC amended note")
            ui.onNodeWithText(ui.activity.getString(R.string.save)).performClick()
            assertEquals(original.copy(note="SYNTHETIC amended note"),saved)
        } finally {TimeZone.setDefault(oldZone)}
    }

    @Test fun anImportedUnknownCodeAndUnitStayEditableWithoutGuessedConversion() {
        val original=LabValueEntity(id=100,analyte_code="SYNTHETIC_OPAQUE",value=4.234567891,unit="opaque unit",
            sampled_utc=Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(),sampled_zone="Europe/Paris",
            reference_lower=1.0,reference_unit="opaque reference",note="SYNTHETIC")
        var saved:LabValueEntity?=null
        ui.setContent{NotesTheme(ThemeMode.LIGHT){LabDialog(original,{}, {saved=it})}}
        ui.onNodeWithText("SYNTHETIC_OPAQUE").assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.save)).performClick()
        assertEquals(original,saved);assertNull(toCanonical(analyte(original.analyte_code),original.value,original.unit))
    }
}

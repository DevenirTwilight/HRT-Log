package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.domain.TherapyStandard
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/** REQUIREMENTS §35a: the confirmation page saves only what the user confirms. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class HistoryPeriodDialogTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val d0=LocalDate.of(2026,6,10)
    private val standard=TherapyStandard("E2",null,"SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0,2.0))

    @Test fun confirmingACandidateSavesTheProposedStandardAndEvidence() {
        var saved:List<Any?>?=null
        val draft=PeriodDraft(null,1,null,"{}",standard,d0,d0.plusDays(29),ZoneId.of("UTC"),listOf(3,1,2),30)
        ui.setContent{MaterialTheme{HistoryPeriodDialog(draft,null,PeriodActions(confirm={k,m,s,f,u,z,_,e->saved=listOf(k,m,s,f,u,z,e)}),{})}}
        ui.onNode(hasText(ui.activity.getString(R.string.period_confirm)) and hasClickAction()).performClick()
        ui.runOnIdle{
            val (key,med,s,from,until,zone,evidence)=saved!!.let{listOf(it[0],it[1],it[2],it[3],it[4],it[5],it[6])}.let{Seven(it)}
            assertNull(key);assertEquals(1L,med);assertEquals(standard,s);assertEquals(d0,from);assertEquals(d0.plusDays(30),until);assertEquals(ZoneId.of("UTC"),zone)
            val e=org.json.JSONObject(evidence as String);assertEquals("[1,2,3]",e.getJSONArray("record_ids").toString());assertEquals(0,e.getJSONArray("user_changed").length())
        }
    }
    private class Seven(val l:List<Any?>){operator fun component1()=l[0];operator fun component2()=l[1];operator fun component3()=l[2];operator fun component4()=l[3]
        operator fun component5()=l[4];operator fun component6()=l[5];operator fun component7()=l[6]}

    @Test fun unknownFrequencyMustBeFilledInBeforeConfirming() {
        val draft=PeriodDraft(null,1,null,"{}",standard.copy(kind="OBSERVED",interval=0,doses=listOf(2.0,3.0)),d0,null,ZoneId.of("UTC"),listOf(1),5)
        ui.setContent{MaterialTheme{HistoryPeriodDialog(draft,null,PeriodActions(),{})}}
        ui.onNodeWithText(ui.activity.getString(R.string.period_frequency_required)).assertExists()
        ui.onNode(hasText(ui.activity.getString(R.string.period_confirm)) and hasClickAction()).assertIsNotEnabled()
    }
}

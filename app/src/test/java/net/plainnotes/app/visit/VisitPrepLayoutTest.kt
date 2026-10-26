package net.plainnotes.app.visit

import net.plainnotes.app.R
import net.plainnotes.app.data.VisitSection
import net.plainnotes.app.export.PdfReport
import net.plainnotes.app.export.Pdf01Fixture
import net.plainnotes.app.export.Recorder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sample 08 (REQUIREMENTS §150): every part grouped once, and the contents preview follows the real PDF order. */
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk=[35],application=android.app.Application::class)
class VisitPrepLayoutTest {
    @Test fun everyPartIsListedOnceAndDefaultsAreUnchanged() {
        val listed=VisitPrepLayout.groups.flatMap {it.second}
        assertEquals(VisitSection.entries.toSet(),listed.toSet());assertEquals(listed.size,listed.toSet().size)
        assertEquals(VisitSection.entries.toSet(),VisitPrepLayout.exportOrder.toSet())
        // The "not ticked by default" tag is the real default, unchanged by the redesign.
        assertEquals(setOf(VisitSection.PACKAGES,VisitSection.MILESTONES),VisitSection.entries.filterNot {it.defaultOn}.toSet())
        assertEquals(setOf(VisitSection.FACTS,VisitSection.REGIMEN,VisitSection.INTAKES),VisitSection.entries.filter {VisitPrepLayout.source(it)==VisitPrepLayout.Source.SUMMARISED}.toSet())
    }

    /** Renders the actual visit pack (no concentration data, as the app exports it) and reads its section titles in order. */
    @Test fun contentsPreviewMatchesTheRealPdfOrder() {
        val title=mapOf(R.string.report_current_plan to VisitSection.REGIMEN,R.string.visit_facts_title to VisitSection.FACTS,R.string.adherence_title to VisitSection.INTAKES,
            R.string.labs to VisitSection.LABS,R.string.wellbeing to VisitSection.DAILY,R.string.wb_symptoms to VisitSection.SYMPTOMS,R.string.wb_reviews to VisitSection.REVIEWS,
            R.string.visit_questions to VisitSection.QUESTIONS,R.string.visit_milestones to VisitSection.MILESTONES,R.string.wb_package_info to VisitSection.PACKAGES)
        val c=Pdf01Fixture.localized("en")
        val byText=title.entries.associate {c.getString(it.key) to it.value}
        for(chosen in listOf(VisitSection.entries.toSet(),setOf(VisitSection.QUESTIONS,VisitSection.LABS,VisitSection.FACTS),setOf(VisitSection.PACKAGES,VisitSection.REGIMEN,VisitSection.SYMPTOMS))) {
            val d=Pdf01Fixture.data(true);val spec=Pdf01Fixture.spec(chosen)
            val facts=VisitFacts.build(d,Pdf01Fixture.appointment,spec.from,spec.to,Pdf01Fixture.zone)
            val r=Recorder()
            PdfReport.render(c,d,1,null,r,visit=spec,facts=facts,digest="0".repeat(64),now=Pdf01Fixture.now,trace=r.trace)
            val sections=r.traces.zipWithNext().filter {it.first.second=="number" && it.second.second=="text"}.map {byText.getValue(it.second.third)}
            assertEquals(chosen.toString(),VisitPrepLayout.contents(chosen),sections)
        }
    }
}

package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.export.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WellbeingSummaryTest {
    @Test fun summaryIncludesBoundaryDatesAndHiddenHistoryWithoutChangingScores() {
        val item=CheckinItemEntity(1,"DAY_MOOD",null,false,0)
        val scores=listOf(CheckinScoreEntity("2026-02-09",1,2),CheckinScoreEntity("2026-02-10",1,4),CheckinScoreEntity("2026-02-11",1,3),CheckinScoreEntity("2026-02-12",1,5))
        val d=ExportData(emptyList(),emptyMap(),emptyList(),emptyList(),listOf(item),scores,emptyList(),emptyMap(),{"Synthetic"},symptoms=listOf(SymptomCheckEntity("2026-02-10","JAUNDICE"),SymptomCheckEntity("2026-02-12","JAUNDICE")),reviews=listOf(StageReviewEntity(date="2026-02-11",weight_kg=60.0),StageReviewEntity(date="2026-02-12")))
        val s=WellbeingSummary(d,LocalDate.of(2026,2,10),LocalDate.of(2026,2,11))
        assertEquals(listOf(4,3),s.scores.map{it.value});assertEquals(1,s.symptoms.size);assertEquals(1,s.reviews.size);assertEquals(60.0,s.reviews.single().weight_kg!!,0.0)
        assertEquals(scores,d.scores)
    }
}

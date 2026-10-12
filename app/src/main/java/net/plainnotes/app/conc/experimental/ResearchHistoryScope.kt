package net.plainnotes.app.conc.experimental

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.HistoricalContext
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.RecordEntity

/** Read-only exposure scope. Unknown records never prove the absence of another E2 route. */
data class ResearchHistoryScope(val otherE2Records: Int, val unknownRecords: Int, val hasSublingualE2: Boolean) {
    val compatibilityOnly get() = !hasSublingualE2 && unknownRecords == 0 && otherE2Records > 0
    val partial get() = otherE2Records > 0 || unknownRecords > 0
    companion object {
        fun inspect(records: List<RecordEntity>, rules: Map<Long,String>, nowH: Double): ResearchHistoryScope {
            var other=0;var unknown=0;var sl=false
            for(r in records) {
                if(r.deleted_at_utc!=null || r.status !in listOf("ON_TIME","LATE")) continue
                val hour=r.taken_utc?.let(ConcentrationCalculator::hours)
                if(hour!=null && (hour>nowH || hour<nowH-ConcentrationCalculator.HISTORY_DAYS*24)) continue
                val snapshot=MedicationSnapshot.decode(HistoricalContext.resolved(r,rules),r.medication_id)
                val med=snapshot?.medication(r.medication_id)
                if(med==null) { unknown++;continue }
                if(med.molecule!="E2") continue
                val p=snapshot.profile
                if(hour==null || med.route==null || p==null) { unknown++;continue }
                val expectedRoute=when(p.pk_route) { "oral"->"ORAL";"sublingual"->"SUBLINGUAL";"gel"->"GEL";"patchApply"->"PATCH";"injection"->"INJECTION";else->null }
                if(expectedRoute==null || med.route!=expectedRoute) { unknown++;continue }
                if(med.route=="SUBLINGUAL" && p.ester=="E2" && p.pk_route=="sublingual") sl=true
                else other++
            }
            return ResearchHistoryScope(other,unknown,sl)
        }
    }
}

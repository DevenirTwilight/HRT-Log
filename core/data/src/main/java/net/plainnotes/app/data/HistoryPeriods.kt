package net.plainnotes.app.data

import net.plainnotes.app.domain.TherapyStandard
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** JSON forms and state of confirmed past periods. */
object HistoryPeriods {
    const val ORIGIN="OBSERVED_USER_CONFIRMED"
    const val CONFIRMED="CONFIRMED"
    const val REVOKED="REVOKED"
    const val EXTRA="EXTRA"

    fun standardJson(s:TherapyStandard):String=JSONObject().put("version",1).put("compound",s.compound ?: JSONObject.NULL).put("ester",s.ester ?: JSONObject.NULL)
        .put("route",s.route ?: JSONObject.NULL).put("unit",s.unit ?: JSONObject.NULL).put("formulation",s.formulation ?: JSONObject.NULL)
        .put("kind",s.kind).put("interval",s.interval).put("weekly_count",s.weeklyCount).put("doses",JSONArray(s.doses)).toString()

    /** Only a known frequency can be confirmed: every N days with the doses of one dosing day. */
    fun readStandard(json:String):TherapyStandard {
        val o=JSONObject(json);require(o.getInt("version")==1)
        fun text(k:String)=if(o.isNull(k))null else o.getString(k).also{require(it.isNotBlank())}
        val kind=o.getString("kind");require(kind=="EVERY_N_DAYS")
        val interval=o.getInt("interval");require(interval in 1..365)
        val a=o.getJSONArray("doses");val doses=(0 until a.length()).map{a.getDouble(it).also{d->require(d.isFinite() && d>0)}}
        require(doses.size in 1..12)
        val compound=requireNotNull(text("compound"));val unit=requireNotNull(text("unit"))
        return TherapyStandard(compound,text("ester"),text("route"),unit,text("formulation"),kind,interval,o.getInt("weekly_count").also{require(it==0)},doses)
    }

    /** Latest revision of every period; revoked periods are returned too so the caller can show them as unconfirmed again. */
    fun latest(rows:List<HistoryPeriodEntity>)=rows.groupBy{it.period_key}.values.map{it.maxBy{r->r.revision}}

    fun confirmed(rows:List<HistoryPeriodEntity>)=latest(rows).filter{it.state==CONFIRMED}

    fun validate(row:HistoryPeriodEntity) {
        require(row.state in listOf(CONFIRMED,REVOKED) && row.origin==ORIGIN && row.revision>=1)
        java.util.UUID.fromString(row.period_key);ZoneId.of(row.zone)
        val from=LocalDate.parse(row.from_date);row.until_date?.let{require(LocalDate.parse(it)>from)}
        readStandard(row.standard_json);JSONObject(row.identity_json);JSONObject(row.evidence_json)
    }
}

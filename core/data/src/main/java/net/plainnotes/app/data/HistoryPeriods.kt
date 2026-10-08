package net.plainnotes.app.data

import net.plainnotes.app.domain.TherapyStandard
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** One row of a timeline edit (REQUIREMENTS §37b); [standard] only for PERIOD and FILL; [until] is exclusive. */
data class TimelineEditRow(val kind:String,val medicationId:Long,val standard:TherapyStandard?,val from:LocalDate,val until:LocalDate?,val zone:ZoneId,
                           val identityJson:String,val note:String="",
                           /** §38: exact bounds inside [from, until) for an edit narrower than whole days (a short saved version). */
                           val exactFromUtc:Long?=null,val exactUntilUtc:Long?=null)

/** JSON forms and state of confirmed past periods. */
object HistoryPeriods {
    const val ORIGIN="OBSERVED_USER_CONFIRMED"
    const val CONFIRMED="CONFIRMED"
    const val REVOKED="REVOKED"
    const val EXTRA="EXTRA"
    /** §37b user edits (origin USER_EDIT): fixed-bounds period, joinable fill, deleted range, stop set by the user. */
    const val USER_ORIGIN="USER_EDIT"
    const val PERIOD="PERIOD"
    const val FILL="FILL"
    const val DELETED="DELETED"
    const val STOP="STOP"
    val USER_KINDS=listOf(PERIOD,FILL,DELETED,STOP)

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

    /**
     * §38: optional exact bounds kept in evidence_json (no schema change). They must lie inside the row's own days, so
     * the dates stay the authority for overlap checks.
     */
    fun exactBounds(row:HistoryPeriodEntity):Pair<Long,Long?>? {
        val e=JSONObject(row.evidence_json);if(!e.has("exact_from_utc"))return null
        val zone=ZoneId.of(row.zone);val dayFrom=LocalDate.parse(row.from_date).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayUntil=row.until_date?.let{LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli()}
        val from=e.getLong("exact_from_utc");val until=if(e.isNull("exact_until_utc"))null else e.getLong("exact_until_utc")
        require(row.kind in USER_KINDS && from>=dayFrom && (dayUntil==null || from<dayUntil) && (until==null || until>from) && (until==null || dayUntil==null || until<=dayUntil) && (until!=null || dayUntil==null))
        return from to until
    }
    /** Latest revision of every period; revoked periods are returned too so the caller can show them as unconfirmed again. */
    fun latest(rows:List<HistoryPeriodEntity>)=rows.groupBy{it.period_key}.values.map{it.maxBy{r->r.revision}}

    fun confirmed(rows:List<HistoryPeriodEntity>)=latest(rows).filter{it.state==CONFIRMED && it.kind==CONFIRMED}
    /** Active user edits (REQUIREMENTS §37b), highest priority on the timeline. */
    fun userEdits(rows:List<HistoryPeriodEntity>)=latest(rows).filter{it.state==CONFIRMED && it.kind in USER_KINDS}

    fun validate(row:HistoryPeriodEntity) {
        require(row.state in listOf(CONFIRMED,REVOKED) && row.revision>=1)
        require(if(row.kind==CONFIRMED)row.origin==ORIGIN else row.kind in USER_KINDS && row.origin==USER_ORIGIN)
        java.util.UUID.fromString(row.period_key);row.group_key?.let{java.util.UUID.fromString(it)};ZoneId.of(row.zone)
        val from=LocalDate.parse(row.from_date);row.until_date?.let{require(LocalDate.parse(it)>from)}
        if(row.kind in listOf(DELETED,STOP))require(JSONObject(row.standard_json).length()==0) else readStandard(row.standard_json)
        JSONObject(row.identity_json);exactBounds(row)
    }
}

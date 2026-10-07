package net.plainnotes.app.data

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import net.plainnotes.app.domain.RegimenSpan
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.*
import java.time.temporal.TemporalAdjusters

/** Independent frozen instruction; schedule rules remain the reminder adapter. */
data class RegimenDefinition(val medicationJson:String,val kind:String,val interval:Int,val weekdays:Int,val dose:Double,
    val zone:String,val anchorLocal:String?,val anchorUtc:Long?,val times:List<Pair<String,Double?>>) {
    fun snapshot(medicationId:Long)=MedicationSnapshot.decode(medicationJson,medicationId)
    fun json():String=JSONObject().put("version",1).put("medication",JSONObject(medicationJson)).put("kind",kind)
        .put("interval",interval).put("weekdays",weekdays).put("dose",dose).put("zone",zone)
        .put("anchor_local",anchorLocal ?: JSONObject.NULL).put("anchor_utc",anchorUtc ?: JSONObject.NULL)
        .put("times",JSONArray(times.sortedBy{it.first}.map{JSONArray().put(it.first).put(it.second ?: JSONObject.NULL)})).toString()
    /** Ordered arrays make this independent of JSONObject key order; names/alerts/stock are not clinical changes. */
    fun signature():String {
        val m=snapshot(0);val p=m?.profile
        val phase=when(kind) {
            "EVERY_N_HOURS" -> Math.floorMod(requireNotNull(anchorUtc),interval*3_600_000L)
            "WEEKLY" -> Math.floorMod(Math.floorDiv(LocalDate.parse(anchorLocal).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay(),7L),interval.toLong())
            else -> Math.floorMod(LocalDate.parse(anchorLocal).toEpochDay(),interval.toLong())
        }
        val fields=JSONArray().put(m?.molecule ?: JSONObject.NULL).put(m?.route ?: JSONObject.NULL).put(m?.unit ?: JSONObject.NULL)
            .put(p?.ester ?: JSONObject(medicationJson).opt("ester") ?: JSONObject.NULL)
            .put(if(m?.route=="SUBLINGUAL")p?.sl_tier ?: JSONObject.NULL else JSONObject.NULL)
            .put(if(m?.route=="GEL")p?.gel_product_id ?: JSONObject.NULL else JSONObject.NULL)
            .put(if(m?.route=="PATCH")p?.patch_release_ug_day ?: JSONObject.NULL else JSONObject.NULL)
            .put(kind).put(interval).put(weekdays).put(zone).put(phase)
            .put(if(kind=="EVERY_N_HOURS")dose else JSONObject.NULL)
            .put(JSONArray(times.sortedBy{it.first}.map{JSONArray().put(it.first).put(it.second ?: dose)}))
        return MessageDigest.getInstance("SHA-256").digest(fields.toString().toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    }
    companion object {
        fun from(rule:RuleEntity,times:List<TimeEntity>)=RegimenDefinition(rule.config_snapshot,rule.kind,rule.interval,rule.weekday_mask,
            rule.dose_snapshot,rule.effective_zone,rule.anchor_local,rule.anchor_utc,times.map{it.local_time to it.dose_override})
        fun read(json:String):RegimenDefinition {
            val o=JSONObject(json);require(o.getInt("version")==1)
            val kind=o.getString("kind");require(kind in listOf("EVERY_N_DAYS","EVERY_N_HOURS","WEEKLY"))
            val interval=o.getInt("interval");require(interval in 1..36500)
            val mask=o.getInt("weekdays");require(mask in 0..127 && (kind!="WEEKLY" || mask>0))
            val dose=o.getDouble("dose");require(dose.isFinite() && dose>0)
            val zone=o.getString("zone");ZoneId.of(zone)
            val local=if(o.isNull("anchor_local"))null else o.getString("anchor_local").also{LocalDate.parse(it)}
            val utc=if(o.isNull("anchor_utc"))null else o.getLong("anchor_utc")
            require(if(kind=="EVERY_N_HOURS") utc!=null && local==null else local!=null && utc==null)
            val a=o.getJSONArray("times");val times=(0 until a.length()).map{i->a.getJSONArray(i).let{t->
                val time=t.getString(0);require(time.length==8);LocalTime.parse(time)
                time to if(t.isNull(1))null else t.getDouble(1).also{require(it.isFinite() && it>0)}
            }}
            require(times.map{it.first}.distinct().size==times.size)
            require(if(kind=="EVERY_N_HOURS")times.isEmpty() else times.isNotEmpty())
            return RegimenDefinition(o.getJSONObject("medication").toString(),kind,interval,mask,dose,zone,local,utc,times)
        }
    }
}
fun RegimenVersionEntity.span()=RegimenSpan(id,medication_id,Instant.ofEpochMilli(effective_from_utc),effective_until_utc?.let(Instant::ofEpochMilli),origin=="LEGACY_RULE")

object RegimenHistory {
    private fun readRules(db:SupportSQLiteDatabase,sql:String):List<RuleEntity> = db.query(sql).use{c->buildList {
            fun s(k:String)=c.getString(c.getColumnIndexOrThrow(k))
            fun n(k:String)=c.getLong(c.getColumnIndexOrThrow(k))
            fun nullable(k:String)=if(c.isNull(c.getColumnIndexOrThrow(k)))null else n(k)
            while(c.moveToNext())add(RuleEntity(n("id"),n("medication_id"),s("kind"),n("interval").toInt(),n("weekday_mask").toInt(),
                if(c.isNull(c.getColumnIndexOrThrow("anchor_local")))null else s("anchor_local"),s("anchor_zone"),nullable("anchor_utc"),n("effective_from_utc"),nullable("effective_until_utc"),s("effective_zone"),n("missed_tracking_from_utc"),
                c.getDouble(c.getColumnIndexOrThrow("dose_snapshot")),n("soon_snapshot").toInt(),n("late_snapshot").toInt(),s("config_snapshot")))
        }}
    private fun times(db:SupportSQLiteDatabase,id:Long)=db.query("SELECT local_time,dose_override FROM rule_time WHERE rule_id=? ORDER BY local_time",arrayOf(id)).use{c->buildList{while(c.moveToNext())add(TimeEntity(rule_id=id,local_time=c.getString(0),dose_override=if(c.isNull(1))null else c.getDouble(1)))}}
    fun validateLinks(db:SupportSQLiteDatabase) {
        val signatures=db.query("SELECT l.rule_id,v.clinical_signature FROM regimen_rule_link l JOIN regimen_version v ON v.id=l.regimen_id").use{c->buildMap{while(c.moveToNext())put(c.getLong(0),c.getString(1))}}
        readRules(db,"SELECT * FROM schedule_rule").forEach{rule->require(RegimenDefinition.from(rule,times(db,rule.id)).signature()==signatures[rule.id]){"Inconsistent regimen link"}}
    }
    /** Only stored rule snapshots are used; missing legacy parameters never come from the current profile. */
    fun seed(db:SupportSQLiteDatabase) {
        val rules=readRules(db,"SELECT r.* FROM schedule_rule r LEFT JOIN regimen_rule_link l ON l.rule_id=r.id WHERE l.rule_id IS NULL ORDER BY r.effective_from_utc,r.id")
        rules.forEach{r->
            val times=times(db,r.id)
            val definition=RegimenDefinition.from(r,times);val signature=definition.signature()
            data class Existing(val id:Long,val from:Long,val until:Long?,val signature:String)
            val last=db.query("SELECT id,effective_from_utc,effective_until_utc,clinical_signature FROM regimen_version WHERE medication_id=? ORDER BY effective_from_utc DESC,id DESC LIMIT 1",arrayOf(r.medication_id)).use{c->if(c.moveToFirst())Existing(c.getLong(0),c.getLong(1),if(c.isNull(2))null else c.getLong(2),c.getString(3))else null}
            val id=if(last!=null && last.until==r.effective_from_utc && last.signature==signature) {
                db.execSQL("UPDATE regimen_version SET effective_until_utc=? WHERE id=?",arrayOf(r.effective_until_utc,last.id));last.id
            } else if(last!=null && last.from<=r.effective_from_utc && (last.until==null || r.effective_from_utc<last.until) && last.signature==signature) {
                require(last.until==null || r.effective_until_utc!=null && r.effective_until_utc<=last.until);last.id
            } else db.insert("regimen_version",0,ContentValues().apply {
                put("medication_id",r.medication_id);put("effective_from_utc",r.effective_from_utc);put("effective_until_utc",r.effective_until_utc)
                put("zone",r.effective_zone);put("definition_json",definition.json());put("clinical_signature",signature);put("origin","LEGACY_RULE");putNull("recorded_at_utc")
            })
            db.execSQL("INSERT INTO regimen_rule_link(rule_id,regimen_id) VALUES (?,?)",arrayOf(r.id,id))
        }
    }
    suspend fun changed(dao:NotesDao,medicationId:Long,now:Long) {
        val rule=dao.rules().singleOrNull{it.medication_id==medicationId && it.effective_until_utc==null}
        val current=dao.regimens().singleOrNull{it.medication_id==medicationId && it.effective_until_utc==null}
        if(rule==null) {
            current?.let{dao.closeRegimen(it.id,maxOf(now,it.effective_from_utc+1))};return
        }
        val definition=RegimenDefinition.from(rule,dao.times(rule.id));val signature=definition.signature()
        val id=if(current?.clinical_signature==signature) current.id else {
            current?.let{dao.closeRegimen(it.id,rule.effective_from_utc)}
            dao.regimen(RegimenVersionEntity(medication_id=medicationId,effective_from_utc=rule.effective_from_utc,zone=rule.effective_zone,
                definition_json=definition.json(),clinical_signature=signature,origin="APP",recorded_at_utc=now))
        }
        if(dao.regimenLinks().none{it.rule_id==rule.id})dao.regimenLink(RegimenRuleLinkEntity(rule.id,id))
    }
}

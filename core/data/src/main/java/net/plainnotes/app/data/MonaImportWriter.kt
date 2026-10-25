package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import net.plainnotes.app.importer.MonaMigration
import net.plainnotes.app.importer.Mona
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Preview states are observations. The writer repeats them inside the actual scoped transaction. */
enum class MonaItemStatus { NEW, EXISTING, DELETED, SOURCE_CHANGED, LOCAL_CHANGED, MISSING_TARGET, DRAW_CHANGED }
data class MonaItemReview(val kind:String,val sourceId:String,val part:String,val status:MonaItemStatus)
data class MonaImportPreview(val plan:MonaMigration.Plan,val items:List<MonaItemReview>,val suggestedTargets:Map<MonaMigration.Identity,Long> = emptyMap()) {
    val addedDoses get()=items.count{it.kind=="INTAKE" && it.status==MonaItemStatus.NEW}
    val addedLabs get()=items.count{it.kind=="BLOOD_TEST" && it.status==MonaItemStatus.NEW}
    val existing get()=items.count{it.status in setOf(MonaItemStatus.EXISTING,MonaItemStatus.DELETED)}
    val conflicts get()=items.count{it.status !in setOf(MonaItemStatus.NEW,MonaItemStatus.EXISTING,MonaItemStatus.DELETED)}
}
data class MonaImportSummary(val addedDoses:Int,val addedLabs:Int,val addedDraws:Int,val medications:Int,val existing:Int,val conflicts:Int,val unsupported:Int,
    val schedules:Int,val supplies:Int,val reviews:List<MonaItemReview>)
/** Only the data module can publish a real commit; callbacks/refresh cannot manufacture it. */
class MonaCommitReceipt {
    private val claimed=java.util.concurrent.atomic.AtomicBoolean()
    @Volatile private var value:MonaImportSummary?=null
    val committed:MonaImportSummary? get()=value
    internal fun claim(){check(claimed.compareAndSet(false,true)){"Import receipt already used"}}
    internal fun acknowledge(summary:MonaImportSummary){value=summary}
}

internal object MonaImportWriter {
    private data class Current(val digest:String,val deleted:Boolean,val medication:Long?=null)
    private fun common(at:Long,zone:String,note:String?,laboratory:String?)=Mona.canonical(JSONObject().put("at",at).put("zone",zone)
        .put("note",note ?: JSONObject.NULL).put("laboratory",laboratory ?: JSONObject.NULL))
    private data class Scan(val preview:MonaImportPreview,val heads:Map<MonaReceiptStore.Key,MonaReceiptStore.Head>,val panels:Map<String,String>)
    private fun scan(db:SupportSQLiteDatabase,plan:MonaMigration.Plan):Scan {
        check(db.inTransaction())
        val heads=MonaReceiptStore.heads(db,plan.doses.map{it.sourceId}+plan.labs.map{it.sourceId})
        val current=hashMapOf<Pair<String,Long>,Current>()
        for((table,ids) in listOf("dose_record" to heads.values.mapNotNull{it.record},"lab_value" to heads.values.mapNotNull{it.lab}))
            MonaReceiptStore.rows(db,table,ids) {row->current[table to row.getLong("id")]=Current(MonaReceiptStore.fingerprint(table,row),table=="dose_record" && !row.isNull("deleted_at_utc"),if(table=="dose_record")row.getLong("medication_id") else null)}
        val retained=if(heads.values.any{it.retention=="COMMITTED" && it.record==null && it.lab==null})MonaReceiptStore.retained(db) else emptySet()
        val panels=hashMapOf<String,String>()
        for(chunk in heads.values.mapNotNull{it.panel}.distinct().chunked(400))
            db.query("SELECT uuid,sampled_utc,sampled_zone,note,laboratory FROM lab_panel WHERE uuid IN (${chunk.joinToString(","){"?"}})",chunk.toTypedArray()).use {c->while(c.moveToNext()) {
                panels[c.getString(0)]=common(c.getLong(1),c.getString(2),if(c.isNull(3))null else c.getString(3),if(c.isNull(4))null else c.getString(4))
            }}
        fun review(key:MonaReceiptStore.Key,digest:String):MonaItemStatus {
            val old=heads[key] ?: return MonaItemStatus.NEW
            if(old.sourceDigest!=digest)return MonaItemStatus.SOURCE_CHANGED
            if(old.retention=="REMOVED")return MonaItemStatus.DELETED
            val target=if(key.kind=="INTAKE")old.record?.let{current["dose_record" to it]} else old.lab?.let{current["lab_value" to it]}
            if(target==null)return if(key in retained)MonaItemStatus.DELETED else MonaItemStatus.MISSING_TARGET
            if(target.deleted)return MonaItemStatus.DELETED
            return if(target.digest==old.targetDigest)MonaItemStatus.EXISTING else MonaItemStatus.LOCAL_CHANGED
        }
        val reviews=plan.doses.map {d->val key=MonaReceiptStore.Key("INTAKE",d.sourceId);MonaItemReview(key.kind,key.id,"",review(key,d.sourceDigest))}+
            plan.labs.map {l->val key=MonaReceiptStore.Key("BLOOD_TEST",l.sourceId,l.analyte);var status=review(key,l.sourceDigest)
                if(status==MonaItemStatus.NEW) {
                    val previous=heads.values.filter{it.key.kind=="BLOOD_TEST" && it.key.id==l.sourceId}.mapNotNull{it.panel}.distinct()
                    val siblings=scannedSiblings(heads,l.sourceId)
                    val changed=siblings.any {h->h.retention!="COMMITTED" || h.lab==null || current["lab_value" to h.lab]?.digest!=h.targetDigest}
                    if(previous.isNotEmpty() && (changed || previous.size!=1 || panels[previous.single()]!=common(l.at.toEpochMilli(),l.zone,l.note,null)))status=MonaItemStatus.DRAW_CHANGED
                }
                MonaItemReview(key.kind,key.id,key.part,status)
            }
        // Reuse an unambiguous prior source-record assignment, never a name/date/dose
        // similarity or an inferred PK profile. Changed/deleted targets give no suggestion.
        val suggestions=linkedMapOf<MonaMigration.Identity,Long>()
        for((group,values) in plan.doses.groupBy{it.identity}) {
            val candidates=values.mapNotNull {d->
                val key=MonaReceiptStore.Key("INTAKE",d.sourceId);val h=heads[key]
                if(h!=null && review(key,d.sourceDigest)==MonaItemStatus.EXISTING)h.record?.let{current["dose_record" to it]?.medication} else null
            }.distinct()
            val id=candidates.singleOrNull() ?: continue
            db.query("SELECT m.molecule,m.unit,m.route,p.ester FROM medication m LEFT JOIN pk_profile p ON p.medication_id=m.id WHERE m.id=?",arrayOf(id)).use {c->
                if(c.moveToFirst() && c.getString(0)==group.molecule && c.getString(1)=="MG" &&
                    (c.getString(2)==group.route || group.molecule!="E2" && c.isNull(2)) &&
                    (c.isNull(3) || c.getString(3)==group.esterCode))suggestions[group]=id
            }
        }
        return Scan(MonaImportPreview(plan,reviews,suggestions),heads,panels)
    }
    private fun scannedSiblings(heads:Map<MonaReceiptStore.Key,MonaReceiptStore.Head>,id:String)=heads.values.filter{it.key.kind=="BLOOD_TEST" && it.key.id==id}
    fun preview(db:SupportSQLiteDatabase,plan:MonaMigration.Plan)=scan(db,plan).preview
    /** Existing medicine is an explicit user tag; source time/amount/unit/route never comes from its current regimen. */
    suspend fun write(dao:NotesDao,db:SupportSQLiteDatabase,plan:MonaMigration.Plan,targets:Map<MonaMigration.Identity,Long?>,
        names:Map<MonaMigration.Identity,String>,now:Instant,checkActive:()->Unit):MonaImportSummary {
        check(db.inTransaction());checkActive()
        val scanned=scan(db,plan);val preview=scanned.preview
        val newKeys=preview.items.filter{it.status==MonaItemStatus.NEW}.map{MonaReceiptStore.Key(it.kind,it.sourceId,it.part)}.toSet()
        val doses=plan.doses.filter{MonaReceiptStore.Key("INTAKE",it.sourceId) in newKeys}
        val labs=plan.labs.filter{MonaReceiptStore.Key("BLOOD_TEST",it.sourceId,it.analyte) in newKeys}
        // Validate every selected target before producing any clinical row.
        val medIds=linkedMapOf<MonaMigration.Identity,Long>();var medications=0
        for(group in doses.map{it.identity}.distinct()) {
            (if(targets.containsKey(group))targets[group] else preview.suggestedTargets[group])?.let {id->
                val med=dao.medication(id);val profile=dao.profile(id)
                require(compatible(group,med,profile) || preview.suggestedTargets[group]==id){"Mona medication target changed"};medIds[group]=id
            } ?: require((names[group] ?: group.label).isNotBlank())
        }
        val base=dao.medications().size
        for(group in doses.map{it.identity}.distinct())if(group !in medIds) {
            checkActive();val first=doses.first{it.identity==group}
            // Existing import-review convention: unknown packaging is visibly blank until
            // reviewed. No container/plan/PK profile/reminder is created by this placeholder.
            val review=JSONObject().put("source","mona").put("raw",JSONObject().put("capacity","").put("intakeInterval","").put("soonAlertDelay","").put("lateAlertDelay","").put("notifications","").put("mona_ester",group.esterCode ?: JSONObject.NULL))
            medIds[group]=dao.insertMedication(MedicationEntity(name=(names[group] ?: group.label).trim(),molecule=group.molecule,route=group.route,unit="MG",dose_per_intake=first.amount,
                container_capacity=first.amount,site_rotation=false,notifications_on=false,active=false,sort_order=base+medications,needs_review=review.toString()));medications++
        }
        for(d in doses) {
            checkActive();val med=dao.medication(medIds.getValue(d.identity))
            val snapshot=JSONObject(MedicationSnapshot.encode(med.copy(name=d.identity.label,molecule=d.identity.molecule,route=d.identity.route,unit="MG"),null))
                .put("ester",d.identity.esterCode ?: JSONObject.NULL)
                .put("mona_context",JSONObject().put("version",1).put("ester",d.identity.esterCode ?: JSONObject.NULL)
                    .put("injection_type",d.identity.injectionType ?: JSONObject.NULL).put("raw_route",d.identity.rawRoute).put("dosing_basis","mass"))
            val id=dao.record(RecordEntity(medication_id=med.id,taken_utc=d.at.toEpochMilli(),taken_zone=d.zone,actual_dose=d.amount,status="ON_TIME",note=d.note,
                origin="IMPORT_MONA",source_record_key="mona:intake:${d.sourceId}",revision=1,config_snapshot=snapshot.toString()))
            val actual=Trash.rows(db,"dose_record","id=?",arrayOf(id)).getJSONObject(0)
            dao.externalImport(ExternalImportEntity(source_kind="INTAKE",source_id=d.sourceId,app_version=plan.metadata.appVersion,export_date=plan.metadata.exportedAtRaw,
                source_digest=d.sourceDigest,source_json=d.sourceJson,target_digest=MonaReceiptStore.fingerprint("dose_record",actual),record_id=id,imported_utc=now.toEpochMilli()))
        }
        var draws=0
        for((sourceId,values) in labs.groupBy{it.sourceId}) {
            checkActive();val first=values.first()
            val oldUuid=scanned.heads.values.filter{it.key.kind=="BLOOD_TEST" && it.key.id==sourceId}.mapNotNull{it.panel}.distinct().singleOrNull()
            val panel=if(oldUuid!=null)requireNotNull(dao.labPanelByUuid(oldUuid)) else {
                val id=dao.insertLabPanel(LabPanelEntity(uuid=UUID.randomUUID().toString(),sampled_utc=first.at.toEpochMilli(),sampled_zone=first.zone,note=first.note,created_utc=now.toEpochMilli()));draws++;requireNotNull(dao.labPanel(id))
            }
            for(l in values) {
                checkActive();dao.analyte(AnalyteEntity(l.analyte,if(l.analyte=="E2")"pg/mL" else "ng/dL"))
                val id=dao.insertLab(LabValueEntity(analyte_code=l.analyte,value=l.value,unit=l.unit,sampled_utc=l.at.toEpochMilli(),sampled_zone=l.zone,note=l.note,panel_id=panel.id))
                val actual=Trash.rows(db,"lab_value","id=?",arrayOf(id)).getJSONObject(0)
                dao.externalImport(ExternalImportEntity(source_kind="BLOOD_TEST",source_id=l.sourceId,source_part=l.analyte,app_version=plan.metadata.appVersion,export_date=plan.metadata.exportedAtRaw,
                    source_digest=l.sourceDigest,source_json=l.sourceJson,target_digest=MonaReceiptStore.fingerprint("lab_value",actual),lab_id=id,panel_uuid=panel.uuid,imported_utc=now.toEpochMilli()))
            }
        }
        checkActive()
        return MonaImportSummary(doses.size,labs.size,draws,medications,preview.existing,preview.conflicts,plan.issues.size,plan.unsupportedSchedules,plan.unsupportedSupplies,preview.items)
    }
    fun compatible(group:MonaMigration.Identity,med:MedicationEntity,profile:ProfileEntity?):Boolean =
        med.molecule==group.molecule && med.unit=="MG" && (med.route==group.route || group.molecule!="E2" && med.route==null) &&
            if(group.esterCode==null)profile==null else profile?.ester==group.esterCode
}

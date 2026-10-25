package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import net.plainnotes.app.importer.Featherline
import net.plainnotes.app.importer.Mona
import net.plainnotes.app.importer.MonaMigration
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

enum class FeatherlineItemStatus { NEW,EXISTING,DELETED,SOURCE_CHANGED,LOCAL_CHANGED,MISSING_TARGET,GROUP_CHANGED }
data class FeatherlineItemReview(val kind:String,val id:String,val status:FeatherlineItemStatus)
data class FeatherlineImportPreview(val export:Featherline.Export,val items:List<FeatherlineItemReview>) {
    val newDoses get()=items.count{it.kind=="LOG" && it.status==FeatherlineItemStatus.NEW}
    val newLabs get()=items.count{it.kind=="RESULT" && it.status==FeatherlineItemStatus.NEW}
    val existing get()=items.count{it.status in setOf(FeatherlineItemStatus.EXISTING,FeatherlineItemStatus.DELETED)}
    val conflicts get()=items.count{it.status !in setOf(FeatherlineItemStatus.NEW,FeatherlineItemStatus.EXISTING,FeatherlineItemStatus.DELETED)}
}
data class FeatherlineImportSummary(val doses:Int,val labs:Int,val panels:Int,val medicines:Int,val existing:Int,val conflicts:Int,val unsupported:Int,val items:List<FeatherlineItemReview>)
class FeatherlineCommitReceipt {
    private val claimed=java.util.concurrent.atomic.AtomicBoolean()
    @Volatile private var value:FeatherlineImportSummary?=null
    val committed get()=value
    internal fun claim(){check(claimed.compareAndSet(false,true))}
    internal fun acknowledge(result:FeatherlineImportSummary){value=result}
}
internal object FeatherlineImportWriter {
    private data class Scan(val preview:FeatherlineImportPreview,val heads:Map<FeatherlineReceiptStore.Key,FeatherlineReceiptStore.Head>,val panels:Map<String,Long>)
    private fun scan(db:SupportSQLiteDatabase,export:Featherline.Export):Scan {
        check(db.inTransaction())
        val incoming=export.doses.map{it.id}+export.labs.map{it.id}
        val heads=FeatherlineReceiptStore.heads(db,incoming).toMutableMap()
        // Include all previously imported siblings, even absent from the new export.
        for(ids in export.labs.map{it.panel}.distinct().chunked(400))db.query("SELECT source_id FROM featherline_import_item WHERE source_kind='RESULT' AND source_panel_uuid IN (${ids.joinToString(","){"?"}})",ids.toTypedArray()).use{c->
            val siblings=mutableListOf<String>();while(c.moveToNext())siblings+=c.getString(0);heads+=FeatherlineReceiptStore.heads(db,siblings)
        }
        val current=hashMapOf<Pair<String,Long>,String>()
        for((table,ids) in listOf("dose_record" to heads.values.mapNotNull{it.record},"lab_value" to heads.values.mapNotNull{it.lab}))
            FeatherlineReceiptStore.rows(db,table,ids){r->current[table to r.getLong("id")]=FeatherlineReceiptStore.fingerprint(table,r)}
        val retained=if(heads.values.any{it.retention=="COMMITTED" && it.record==null && it.lab==null})FeatherlineReceiptStore.retained(db) else emptySet()
        fun status(kind:String,id:String,raw:String):FeatherlineItemStatus {
            val key=FeatherlineReceiptStore.Key(kind,id);val old=heads[key] ?: return FeatherlineItemStatus.NEW
            if(old.sourceDigest!=MonaMigration.digest(raw))return FeatherlineItemStatus.SOURCE_CHANGED
            if(old.retention=="REMOVED")return FeatherlineItemStatus.DELETED
            val target=if(kind=="LOG")old.record?.let{current["dose_record" to it]} else old.lab?.let{current["lab_value" to it]}
            if(target==null)return if(key in retained)FeatherlineItemStatus.DELETED else FeatherlineItemStatus.MISSING_TARGET
            if(kind=="LOG" && old.record!=null && db.query("SELECT deleted_at_utc FROM dose_record WHERE id=?",arrayOf(old.record)).use{it.moveToFirst() && !it.isNull(0)})return FeatherlineItemStatus.DELETED
            return if(target==old.targetDigest)FeatherlineItemStatus.EXISTING else FeatherlineItemStatus.LOCAL_CHANGED
        }
        val panels=hashMapOf<String,Long>();val changed=hashSetOf<String>()
        for((sourceId,values) in export.labs.groupBy{it.panel}) {
            val siblings=mutableListOf<FeatherlineReceiptStore.Head>()
            db.query("SELECT source_id FROM featherline_import_item WHERE source_kind='RESULT' AND source_panel_uuid=?",arrayOf(sourceId)).use{c->while(c.moveToNext())heads[FeatherlineReceiptStore.Key("RESULT",c.getString(0))]?.let(siblings::add)}
            if(siblings.isEmpty())continue
            val uuids=siblings.mapNotNull{it.panel}.distinct()
            val allLive=siblings.all{it.retention=="COMMITTED" && it.lab!=null && current["lab_value" to it.lab]==it.targetDigest}
            val first=values.first()
            var target:Long?=null
            if(allLive && uuids.size==1)db.query("SELECT id,sampled_utc,sampled_zone,note FROM lab_panel WHERE uuid=?",arrayOf(uuids.single())).use{c->
                if(c.moveToFirst() && c.getLong(1)==first.at && c.getString(2)==first.zone && (if(c.isNull(3))null else c.getString(3))==first.note)target=c.getLong(0)
            }
            val t=target
            if(t==null || db.query("SELECT COUNT(*) FROM lab_value WHERE panel_id=?",arrayOf(t)).use{it.moveToFirst();it.getInt(0)}!=siblings.size)changed+=sourceId
            else panels[sourceId]=t
        }
        val reviews=export.doses.map{FeatherlineItemReview("LOG",it.id,status("LOG",it.id,it.sourceJson))}+export.labs.map {
            val s=status("RESULT",it.id,it.sourceJson)
            FeatherlineItemReview("RESULT",it.id,if(s==FeatherlineItemStatus.NEW && it.panel in changed)FeatherlineItemStatus.GROUP_CHANGED else s)
        }
        return Scan(FeatherlineImportPreview(export,reviews),heads,panels)
    }
    fun preview(db:SupportSQLiteDatabase,export:Featherline.Export)=scan(db,export).preview
    suspend fun write(dao:NotesDao,db:SupportSQLiteDatabase,export:Featherline.Export,now:Instant,active:()->Unit):FeatherlineImportSummary {
        active();val scanned=scan(db,export);val preview=scanned.preview
        val keys=preview.items.filter{it.status==FeatherlineItemStatus.NEW}.map{it.kind to it.id}.toSet()
        val doses=export.doses.filter{"LOG" to it.id in keys};val labs=export.labs.filter{"RESULT" to it.id in keys}
        val medIds=hashMapOf<Featherline.Medicine,Long>();var created=0
        val base=dao.medications().size
        for((medicine,rows) in doses.groupBy{it.medicine}) {
            active()
            // Reuse only the unchanged assignment of an unchanged source UUID in this export.
            val previous=export.doses.filter{it.medicine==medicine && preview.items.any{r->r.kind=="LOG" && r.id==it.id && r.status==FeatherlineItemStatus.EXISTING}}
                .mapNotNull{scanned.heads[FeatherlineReceiptStore.Key("LOG",it.id)]?.record?.let{r->dao.recordById(r)?.medication_id}}.distinct().singleOrNull()
            val reusable=previous?.let{dao.medication(it)}?.takeIf{it.molecule==medicine.molecule && it.route==medicine.route && it.unit=="MG" && dao.profile(it.id)==null}
            if(reusable!=null){medIds[medicine]=reusable.id;continue}
            val review=JSONObject().put("source","featherline").put("raw",JSONObject().put("capacity","").put("featherline_ester",medicine.ester ?: JSONObject.NULL))
            medIds[medicine]=dao.insertMedication(MedicationEntity(name=label(medicine),molecule=medicine.molecule,route=medicine.route,unit="MG",dose_per_intake=rows.first().amount,
                container_capacity=rows.first().amount,site_rotation=false,notifications_on=false,active=false,sort_order=base+created,needs_review=review.toString()));created++
        }
        for(d in doses) {
            active();val med=dao.medication(medIds.getValue(d.medicine))
            val frozen=JSONObject(MedicationSnapshot.encode(med,null)).put("external_context",JSONObject().put("source","FEATHERLINE").put("ester",d.medicine.ester ?: JSONObject.NULL).put("source_medicine",d.medicine.sourceId))
            val id=dao.record(RecordEntity(medication_id=med.id,taken_utc=d.at,taken_zone=d.zone,actual_dose=d.amount,status="ON_TIME",origin="IMPORT_FEATHERLINE",source_record_key="featherline:log:${d.id}",revision=1,config_snapshot=frozen.toString()))
            val row=Trash.rows(db,"dose_record","id=?",arrayOf(id)).getJSONObject(0)
            dao.featherlineImport(FeatherlineImportEntity("LOG",d.id,source_digest=MonaMigration.digest(d.sourceJson),source_json=d.sourceJson,target_digest=FeatherlineReceiptStore.fingerprint("dose_record",row),record_id=id,imported_utc=now.toEpochMilli()))
        }
        var panels=0
        for((sourceId,values) in labs.groupBy{it.panel}) {
            active();val first=values.first()
            val panel=scanned.panels[sourceId]?.let{requireNotNull(dao.labPanel(it))} ?: run {
                val id=dao.insertLabPanel(LabPanelEntity(uuid=UUID.randomUUID().toString(),sampled_utc=first.at,sampled_zone=first.zone,note=first.note,created_utc=now.toEpochMilli()));panels++;requireNotNull(dao.labPanel(id))
            }
            for(l in values) {
                active();dao.analyte(AnalyteEntity(l.analyte,if(l.analyte=="E2")"pg/mL" else "ng/dL"))
                val id=dao.insertLab(LabValueEntity(analyte_code=l.analyte,value=l.value,unit=l.unit,sampled_utc=l.at,sampled_zone=l.zone,note=l.note,panel_id=panel.id))
                val row=Trash.rows(db,"lab_value","id=?",arrayOf(id)).getJSONObject(0)
                dao.featherlineImport(FeatherlineImportEntity("RESULT",l.id,source_panel_uuid=sourceId,source_digest=MonaMigration.digest(l.sourceJson),source_json=l.sourceJson,target_digest=FeatherlineReceiptStore.fingerprint("lab_value",row),lab_id=id,panel_uuid=panel.uuid,imported_utc=now.toEpochMilli()))
            }
        }
        active();return FeatherlineImportSummary(doses.size,labs.size,panels,created,preview.existing,preview.conflicts,export.issues.size,preview.items)
    }
    fun label(m:Featherline.Medicine)=listOfNotNull(m.name,m.ester,m.route).joinToString(" · ")
}

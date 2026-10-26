package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import net.plainnotes.app.importer.ChrysalideMigration
import org.json.JSONObject
import java.time.Instant

enum class ChrysalideItemStatus { NEW, EXISTING, DELETED, LOCAL_CHANGED, MISSING_TARGET }
data class ChrysalideItemReview(val sourceId:String,val status:ChrysalideItemStatus)
data class ChrysalideImportPreview(val plan:ChrysalideMigration.Plan,val items:List<ChrysalideItemReview>) {
    val newCount get()=items.count{it.status==ChrysalideItemStatus.NEW}
    val conflicts get()=items.count{it.status==ChrysalideItemStatus.LOCAL_CHANGED || it.status==ChrysalideItemStatus.MISSING_TARGET}
}
data class ChrysalideImportSummary(val added:Int,val existing:Int,val deleted:Int,val conflicts:Int,val unsupported:Int)
class ChrysalideCommitReceipt {
    private val claimed=java.util.concurrent.atomic.AtomicBoolean();@Volatile var committed:ChrysalideImportSummary?=null;private set
    internal fun claim(){check(claimed.compareAndSet(false,true))};internal fun acknowledge(v:ChrysalideImportSummary){committed=v}
}

/** Source row content digest, not Chrysalide's local auto-increment id, is the stable key. */
internal object ChrysalideImportWriter {
    private const val TABLE="chrysalide_import_item"
    private fun key(digest:String)="chry:$digest"
    private fun snapshot(i:ChrysalideMigration.Intake)=JSONObject().put("source","chrysalide-v5").put("source_id",i.sourceId)
        .put("source_digest",i.sourceDigest).put("export_digest",i.exportDigest).put("source_zone","not-provided; instant retained as UTC")
        .put("medication",JSONObject(i.medication.sourceJson)).put("log",JSONObject(i.sourceJson)).toString()
    fun preview(db:SupportSQLiteDatabase,plan:ChrysalideMigration.Plan):ChrysalideImportPreview {
        val reviews=plan.intakes.map {i->
            val exact=db.query("SELECT retention,record_id,target_digest FROM $TABLE WHERE source_digest=?",arrayOf(i.sourceDigest)).use{c->if(c.moveToFirst()) Triple(c.getString(0),if(c.isNull(1))null else c.getLong(1),c.getString(2)) else null}
            val status=if(exact==null) {
                // Chrysalide IDs are installation-local. Same ID with different content is ambiguous,
                // never a new automatic insert and never an overwrite.
                val prior=db.query("SELECT retention FROM $TABLE WHERE source_id=? LIMIT 1",arrayOf(i.sourceId.toString())).use{c->if(c.moveToFirst())c.getString(0) else null}
                when(prior){"REMOVED"->ChrysalideItemStatus.DELETED;null->ChrysalideItemStatus.NEW;else->ChrysalideItemStatus.LOCAL_CHANGED}
            } else if(exact.first=="REMOVED")ChrysalideItemStatus.DELETED else if(exact.second==null)ChrysalideItemStatus.MISSING_TARGET else {
                val row=Trash.rows(db,"dose_record","id=?",arrayOf(exact.second)).optJSONObject(0)
                if(row==null)ChrysalideItemStatus.MISSING_TARGET else when { row.opt("deleted_at_utc")!=JSONObject.NULL->ChrysalideItemStatus.DELETED
                    row.optString("origin")!="IMPORT_CHRYSALIDE" || FeatherlineReceiptStore.fingerprint("dose_record",row)!=exact.third->ChrysalideItemStatus.LOCAL_CHANGED
                    else->ChrysalideItemStatus.EXISTING }
            }
            ChrysalideItemReview(i.sourceId.toString(),status)
        }
        return ChrysalideImportPreview(plan,reviews)
    }
    suspend fun write(dao:NotesDao,db:SupportSQLiteDatabase,plan:ChrysalideMigration.Plan,targets:Map<Long,Long>,now:Instant,checkActive:()->Unit):ChrysalideImportSummary {
        check(db.inTransaction());checkActive();val p=preview(db,plan);var added=0;var existing=0;var deleted=0
        for((i,review) in plan.intakes.zip(p.items)) {
            checkActive()
            when(review.status) {
                ChrysalideItemStatus.EXISTING->{existing++;continue}
                ChrysalideItemStatus.DELETED->{deleted++;continue}
                ChrysalideItemStatus.LOCAL_CHANGED,ChrysalideItemStatus.MISSING_TARGET->continue
                ChrysalideItemStatus.NEW->{ }
            }
            val targetId=targets[i.medication.id] ?: continue
            val med=dao.medication(targetId)
            val frozen=snapshot(i)
            val recordId=dao.record(RecordEntity(medication_id=med.id,taken_utc=i.at.toEpochMilli(),taken_zone="UTC",actual_dose=null,status="ON_TIME",note=i.notes,
                origin="IMPORT_CHRYSALIDE",source_record_key=key(i.sourceDigest),revision=1,config_snapshot=frozen))
            val row=Trash.rows(db,"dose_record","id=?",arrayOf(recordId)).getJSONObject(0)
            dao.chrysalideImport(ChrysalideImportEntity(i.sourceDigest,i.sourceId.toString(),frozen,FeatherlineReceiptStore.fingerprint("dose_record",row),recordId,now.toEpochMilli()))
            added++
        }
        checkActive()
        val conflicts=p.items.count{it.status==ChrysalideItemStatus.LOCAL_CHANGED || it.status==ChrysalideItemStatus.MISSING_TARGET}+
            plan.intakes.zip(p.items).count{(i,r)->r.status==ChrysalideItemStatus.NEW && targets[i.medication.id]==null}
        return ChrysalideImportSummary(added,existing,deleted,conflicts,plan.issues.count{it.reason!=net.plainnotes.app.importer.ChrysalideMigration.Reason.MEDICATION_LOG_HAS_NO_DOSE}+plan.unsupportedCollections.values.sum())
    }
}

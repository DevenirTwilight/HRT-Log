package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

/** Transient RAM-only relations. Never relax production attachment guards or manufacture VERIFIED,
 * key envelopes, a durable generation or current-library pointers. No portable serialization.
 */
internal object AttachmentCandidateMetadata {
    /** An archive does not authorize enabling attachments. Existing callers reconstruct OFF;
     * a trusted activation flow may explicitly validate an enabled candidate. */
    fun reconstruct(db:SupportSQLiteDatabase,tables:BackupTables,p:AttachmentRestorePreflight.Plan,checkActive:()->Unit) =
        reconstruct(db,tables,p,0,checkActive)

    fun reconstruct(db:SupportSQLiteDatabase,tables:BackupTables,p:AttachmentRestorePreflight.Plan,
        featureEnabled:Int,checkActive:()->Unit) {
        RawData.reconstructIsolated(db,tables,checkActive)
        compareBusiness(db,tables,checkActive)
        write(db,p,featureEnabled)
        val expected=p.manifest.copy(dataset=p.namespace.dataset,epoch=p.namespace.epoch)
        check(read(db)==normalized(expected)) {"Candidate metadata changed history"}
        verifyTargets(db,p,featureEnabled);checkActive()
    }
    fun write(db:SupportSQLiteDatabase,p:AttachmentRestorePreflight.Plan,featureEnabled:Int=0) {
        require(featureEnabled in 0..1) {"Invalid candidate activation value"}
        check(db.inTransaction())
        if(featureEnabled==1)check(db.version>=21) {"Enabled candidate requires Room21"}
        val ddl=listOf(
            "CREATE TEMP TABLE rc_scope(dataset TEXT PRIMARY KEY,epoch TEXT NOT NULL,generation TEXT NOT NULL,vault TEXT NOT NULL,feature_enabled INTEGER NOT NULL CHECK(feature_enabled IN(0,1)))",
            "CREATE TEMP TABLE rc_identity(uuid TEXT PRIMARY KEY,kind TEXT NOT NULL,source_id INTEGER NOT NULL,target_id INTEGER,state TEXT NOT NULL,trash_id INTEGER,evidence TEXT NOT NULL,UNIQUE(kind,target_id))",
            "CREATE TEMP TABLE rc_document(uuid TEXT PRIMARY KEY,revision INTEGER NOT NULL,title TEXT NOT NULL,category TEXT NOT NULL,state TEXT NOT NULL,active TEXT NOT NULL,deleted INTEGER,FOREIGN KEY(uuid,active) REFERENCES rc_version(document,version) DEFERRABLE INITIALLY DEFERRED)",
            "CREATE TEMP TABLE rc_version(document TEXT NOT NULL,ordinal INTEGER NOT NULL,version TEXT PRIMARY KEY,object TEXT NOT NULL UNIQUE,vault TEXT NOT NULL,attempt TEXT NOT NULL,dek TEXT NOT NULL,plain_bytes INTEGER NOT NULL,cipher_bytes INTEGER NOT NULL,plain_hash TEXT NOT NULL,cipher_hash TEXT NOT NULL,mime TEXT NOT NULL,UNIQUE(document,version),UNIQUE(document,ordinal),FOREIGN KEY(document) REFERENCES rc_document(uuid) DEFERRABLE INITIALLY DEFERRED)",
            "CREATE TEMP TABLE rc_link(uuid TEXT PRIMARY KEY,record TEXT NOT NULL,document TEXT NOT NULL,revision INTEGER NOT NULL,state TEXT NOT NULL,FOREIGN KEY(record) REFERENCES rc_identity(uuid),FOREIGN KEY(document) REFERENCES rc_document(uuid))")
        ddl.forEach(db::execSQL)
        db.execSQL("INSERT INTO rc_scope VALUES(?,?,?,?,?)",arrayOf(p.namespace.dataset.toString(),p.namespace.epoch.toString(),p.namespace.generation.toString(),p.manifest.logicalVault.toString(),featureEnabled))
        p.manifest.records.forEach {r->db.execSQL("INSERT INTO rc_identity VALUES(?,?,?,?,?,?,?)",arrayOf(r.uuid.toString(),r.kind.name,r.sourceId,p.mapping.getValue(r.uuid).localId,r.state,r.trashId,r.evidenceSha256))}
        p.manifest.documents.forEach {d->db.execSQL("INSERT INTO rc_document VALUES(?,?,?,?,?,?,?)",arrayOf(d.uuid.toString(),d.revision,d.title,d.category,d.state,d.activeVersion.toString(),d.deletedUtc))}
        p.manifest.versions.forEach {v->db.execSQL("INSERT INTO rc_version VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",arrayOf(v.document.toString(),v.ordinal,v.context.version.toString(),v.context.objectId.toString(),v.context.logicalVault.toString(),v.context.attempt.toString(),v.context.dekHandle.toString(),v.manifest.plaintextBytes,v.manifest.ciphertextBytes,v.manifest.plaintextSha256,v.manifest.ciphertextSha256,v.mime))}
        p.manifest.links.forEach {l->db.execSQL("INSERT INTO rc_link VALUES(?,?,?,?,?)",arrayOf(l.uuid.toString(),l.record.toString(),l.document.toString(),l.revision,l.state))}
    }
    fun normalized(m:AttachmentBackupManifest)=m.copy(records=m.records.sortedBy {it.uuid.toString()},documents=m.documents.sortedBy {it.uuid.toString()},
        versions=m.versions.sortedWith(compareBy<AttachmentBackupManifest.Version> {it.document.toString()}.thenBy {it.ordinal}),links=m.links.sortedBy {it.uuid.toString()})
    fun read(db:SupportSQLiteDatabase):AttachmentBackupManifest {
        val root=db.query("SELECT dataset,epoch,vault FROM rc_scope").use {c->check(c.moveToFirst());Triple(UUID.fromString(c.getString(0)),UUID.fromString(c.getString(1)),UUID.fromString(c.getString(2))).also {check(!c.moveToNext())}}
        fun <T> rows(sql:String,make:(android.database.Cursor)->T):List<T> = db.query(sql).use {c->buildList {while(c.moveToNext())add(make(c))}}
        fun android.database.Cursor.uuid(i:Int)=UUID.fromString(getString(i))
        fun android.database.Cursor.optionalLong(i:Int)=if(isNull(i))null else getLong(i)
        val records=rows("SELECT uuid,kind,source_id,state,trash_id,evidence FROM rc_identity ORDER BY uuid") {c->AttachmentBackupManifest.Record(c.uuid(0),AttachmentBackupManifest.Kind.valueOf(c.getString(1)),c.getLong(2),c.getString(3),c.optionalLong(4),c.getString(5))}
        val documents=rows("SELECT uuid,revision,title,category,state,active,deleted FROM rc_document ORDER BY uuid") {c->AttachmentBackupManifest.Document(c.uuid(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.uuid(5),c.optionalLong(6))}
        val versions=rows("SELECT document,ordinal,version,object,vault,attempt,dek,plain_bytes,cipher_bytes,plain_hash,cipher_hash,mime FROM rc_version ORDER BY document,ordinal") {c->
            val context=AttachmentObjectContext(c.uuid(4),c.uuid(3),c.uuid(2),c.uuid(5),c.uuid(6))
            AttachmentBackupManifest.Version(c.uuid(0),c.getLong(1),context,AttachmentObjectStreams.Manifest(context,c.getLong(7),c.getLong(8),c.getString(9),c.getString(10)),c.getString(11))}
        val links=rows("SELECT uuid,record,document,revision,state FROM rc_link ORDER BY uuid") {c->AttachmentBackupManifest.Link(c.uuid(0),c.uuid(1),c.uuid(2),c.getLong(3),c.getString(4))}
        return AttachmentBackupManifest(root.first,root.second,root.third,records,documents,versions,links).also {it.validate()}
    }
    fun verifyTargets(db:SupportSQLiteDatabase,p:AttachmentRestorePreflight.Plan,featureEnabled:Int=0) {
        require(featureEnabled in 0..1) {"Invalid expected candidate activation"}
        for(schema in listOf("main","temp"))db.query("PRAGMA $schema.foreign_key_check").use {check(!it.moveToFirst()) {"Candidate foreign-key closure failed"}}
        db.query("SELECT uuid,kind,target_id,state FROM rc_identity").use {c->while(c.moveToNext()) {
            val id=UUID.fromString(c.getString(0));val t=p.mapping.getValue(id)
            check(t.kind.name==c.getString(1) && t.localId==if(c.isNull(2))null else c.getLong(2))
            if(c.getString(3)=="PURGED")check(c.isNull(2))
            else {
                val table=when(t.kind) {AttachmentBackupManifest.Kind.APPOINTMENT->"appointment";AttachmentBackupManifest.Kind.LAB->"lab_value";AttachmentBackupManifest.Kind.LAB_PANEL->"lab_panel";AttachmentBackupManifest.Kind.DOSE_RECORD->"dose_record"}
                if(c.getString(3)=="ACTIVE" || t.kind==AttachmentBackupManifest.Kind.DOSE_RECORD)db.query("SELECT * FROM $table WHERE id=?",arrayOf(t.localId)).use {
                    check(it.moveToFirst())
                    if(t.kind==AttachmentBackupManifest.Kind.LAB_PANEL)check(it.getString(it.getColumnIndexOrThrow("uuid"))==id.toString()) {"Candidate blood draw UUID mismatch"}
                }
                // LAB/APPOINTMENT trash remains in the original payload, proved by preflight below.
            }
        }}
        db.query("SELECT COUNT(*) FROM attachment_store").use {check(it.moveToFirst() && it.getLong(0)==0L)}
        db.query("SELECT feature_enabled FROM rc_scope").use {check(it.moveToFirst() && it.getLong(0)==featureEnabled.toLong() && !it.moveToNext())}
    }
    /** Exact full-row multiset comparison, not counts/hashes or a second business validator.
     * This local scalar notation is NOT the pending portable canonical manifest grammar.
     */
    fun compareBusiness(db:SupportSQLiteDatabase,source:BackupTables,checkActive:()->Unit) {
        fun row(r:JSONObject):String = r.keys().asSequence().sorted().joinToString("|") {key->
            val v=r.get(key);JSONObject.quote(key)+":"+when(v) {
                JSONObject.NULL->"null";is String->"s"+JSONObject.quote(v)
                is Number->"n"+BigDecimal(v.toString()).stripTrailingZeros().toString()
                else->error("Unexpected business scalar")
            }
        }
        var budget=0L
        DOMAIN_TABLES.forEach {t->
            val expected=HashMap<String,Int>()
            // A pre-Mona snapshot has no receipt table; the rebuilt table must be empty.
            // Required source tables were checked by the versioned archive reader.
            val rows=if(t in setOf(ExternalImportSchema.TABLE,FeatherlineImportSchema.TABLE,ChrysalideImportSchema.TABLE) && !source.has(t))null else source.getJSONArray(t)
            for(i in 0 until (rows?.length() ?: 0)) {checkActive();val r=requireNotNull(rows).getJSONObject(i)
                require(r.keys().asSequence().toSet()==RecordBackupCompatibility.columns.getValue(t).toSet()) {"Incomplete current snapshot row"}
                val text=row(r);budget=Math.addExact(budget,text.length.toLong()*2);require(budget<=BackupLimits.MAX_JSON_BYTES.toLong()*4)
                expected[text]=(expected[text] ?: 0)+1
            }
            RawData.forEachBackupRow(db,t,true) {r->checkActive();val text=row(r);val count=requireNotNull(expected[text]) {"Reconstructed business fact changed"}
                if(count==1)expected.remove(text) else expected[text]=count-1}
            check(expected.isEmpty()) {"Reconstructed business rows missing"}
        }
    }
}

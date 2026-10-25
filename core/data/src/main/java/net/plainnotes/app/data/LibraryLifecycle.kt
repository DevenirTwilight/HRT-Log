package net.plainnotes.app.data

import android.os.Looper
import android.system.Os
import android.system.OsConstants.ENOENT
import net.plainnotes.app.domain.AttachmentSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID

/** Internal OFF lifecycle. Fresh native root transactions, not cached scan results, authorize work.
 * Never deletes a file/key; never changes archive framing, clinical data, or the active pointer. */
internal class LibraryLifecycle(private val access:DatabaseAccess) {
    enum class Eligibility { COMPLETE, REFUSED, UNKNOWN }
    data class Inventory(val revision:Long,val items:List<LibraryResourceFacts.Item>,val eligibility:Eligibility,val reason:String)
    data class Release(val operation:UUID,val generation:UUID,val digest:ByteArray,val alreadyReleased:Boolean)
    private val context get()=access.applicationContext
    private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b)
    internal fun <T> rootWork(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,block:(NotesDatabase,NotesDatabase,()->Unit)->T):T=access.holdGeneration().use {
        check(Looper.myLooper()!=Looper.getMainLooper())
        val current=access.get(Space.PRIMARY);val root=access.legacyRoot()
        check(!root.openHelper.writableDatabase.inTransaction() && !current.openHelper.writableDatabase.inTransaction())
        val owner=cap.fork()
        fun active() {owner.check();DatabaseAccess.assertActiveHandle(current);check(AttachmentIdentity.current(current.openHelper.writableDatabase)==scope) {"Lifecycle request changed dataset"}}
        try {
            root.openHelper.writableDatabase.execSQL("PRAGMA synchronous=FULL")
            root.beginTransaction()
            try {
                active();val result=block(current,root,::active)
                owner.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                    active();root.setTransactionSuccessful();root.endTransaction();result
                }
            }finally {if(root.isOpen && root.openHelper.writableDatabase.inTransaction())root.endTransaction()}
        }finally {owner.close()}
    }
    internal fun fresh(root:NotesDatabase,current:NotesDatabase,checkActive:()->Unit):LibraryResourceFacts.Scan = try {
        LibraryResourceFacts.scan(context,root.openHelper.writableDatabase,DatabaseAccess.handleIdentity(current),checkActive=checkActive)
    }catch(e:Exception) {
        checkActive()
        // A partial scan may already have encountered unowned resources before failing.
        // Preserve an opaque UNKNOWN across restart; a later empty scan cannot resolve it.
        LibraryResourceFacts.Scan(listOf(LibraryResourceFacts.Item("scan-unavailable",DatabaseAccess.handleIdentity(current),"UNKNOWN","PARTIAL_RESOURCE_SET_NOT_PROVEN")),false,emptySet(),false)
    }
    /** Rebuild on every call. Historical UNKNOWN evidence survives a missing/replaced leaf. */
    fun inventory(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID)=rootWork(scope,cap) {current,root,active->
        val scan=fresh(root,current,active)
        persist(root,current,scope,operation,scan)
    }
    internal fun persist(root:NotesDatabase,current:NotesDatabase,scope:AttachmentIdentity.Scope,operation:UUID,scan:LibraryResourceFacts.Scan,collection:Boolean=false):Inventory {
        val raw=root.openHelper.writableDatabase;val entries=scan.items.associateBy {it.key}.toMutableMap();val historicalUnknown=mutableListOf<LibraryResourceFacts.Item>();var revision=0L
        raw.query("SELECT revision,payload,sha256 FROM library_resource_inventory WHERE singleton=1").use {c->if(c.moveToFirst()) {
            revision=c.getLong(0);val bytes=c.getBlob(1);require(MessageDigest.isEqual(hash(bytes),c.getBlob(2))) {"Inventory evidence damaged"}
            val old=JSONArray(String(bytes,Charsets.UTF_8))
            for(i in 0 until old.length()) {
                val prior=LibraryResourceFacts.Item.read(old.getJSONObject(i))
                if(prior.category=="UNKNOWN") {
                    val retained=prior.copy(reason=if(prior.key in entries)"PREVIOUS_UNKNOWN_UNRESOLVED" else "PREVIOUS_UNKNOWN_NOT_PRESENT")
                    entries[prior.key]=retained;historicalUnknown+=retained
                }
                else if(!scan.complete && prior.key !in entries)entries[prior.key]=prior.copy(reason="STALE_OBSERVATION_NEEDS_RESCAN")
            }
        }}
        var ordered=entries.values.sortedBy {it.key}
        fun encode(values:List<LibraryResourceFacts.Item>)=JSONArray().also {a->values.forEach {a.put(it.json())}}.toString().toByteArray(Charsets.UTF_8)
        var json=encode(ordered)
        if(entries.size>8192 || json.size>1024*1024-1024) {
            // Never truncate an unknown set into a COMPLETE claim. Reserve room for a durable
            // whole-set overflow witness; it remains UNKNOWN even if external files disappear.
            ordered=(historicalUnknown+
                LibraryResourceFacts.Item("unresolved-inventory-overflow","ALL","UNKNOWN","OPAQUE_RESOURCE_SET_EXCEEDS_INVENTORY_LIMIT")).distinctBy {it.key}
            json=encode(ordered)
            if(json.size>1024*1024)throw IllegalStateException("Previous unknown evidence cannot be replaced")
        }
        val eligibility:Eligibility;val reason:String
        when {
            ordered.any {it.category=="MISSING"}->{eligibility=Eligibility.REFUSED;reason="REQUIRED_MEMBER_MISSING"}
            ordered.any {it.category=="UNKNOWN"} || !scan.complete->{eligibility=Eligibility.UNKNOWN;reason="OWNER_OR_SET_NOT_PROVEN"}
            !collection && (scan.retainedBusiness || ordered.any {it.category=="RETAINED_COMMITTED"})->{eligibility=Eligibility.REFUSED;reason="RETAINED_LIBRARIES_REQUIRE_SEPARATE_EXPORT_POLICY"}
            ordered.any {it.category in setOf("RESERVED_UNMATERIALIZED","RETAINED_RESERVATION","IN_FLIGHT")}->{eligibility=Eligibility.REFUSED;reason="UNFINISHED_RESOURCES_RETAINED"}
            else->{eligibility=Eligibility.UNKNOWN;reason="FULL_SNAPSHOT_AUTHENTICATION_REQUIRED"}
        }
        val next=Math.addExact(revision,1)
        val args=arrayOf(next,operation.toString(),DatabaseAccess.handleIdentity(current),scope.dataset,scope.epoch,json,hash(json),System.currentTimeMillis())
        if(revision==0L)raw.execSQL("INSERT INTO library_resource_inventory VALUES(1,?,?,?,?,?,?,?,?)",args)
        else raw.execSQL("UPDATE library_resource_inventory SET revision=?,operation_uuid=?,generation=?,dataset=?,epoch=?,payload=?,sha256=?,observed_utc=? WHERE singleton=1",args)
        return Inventory(next,ordered,eligibility,reason)
    }
    /** COMPLETE is earned by the existing independent full snapshot/authentication, not by counts.
     * PNMEDBK1 remains a single-library format; retained nonempty libraries are explicitly refused. */
    private fun matches(snapshot:AttachmentBackupManifest,inventory:Inventory,current:String):Boolean {
        val expected=snapshot.versions.associate {v->
            val d=snapshot.documents.single {it.uuid==v.document}
            v.context.objectId.toString() to if(d.state!="ACTIVE")"TRASHED" else if(d.activeVersion!=v.context.version)"HISTORY" else "COMMITTED"
        }
        val actual=inventory.items.filter {it.library==current && "/objects/" in it.key}.associate {it.key.substringAfter("/objects/").substringBefore('/') to it.category}
        return expected==actual
    }
    fun exportEligibility(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID):Inventory {
        val before=inventory(scope,cap,operation)
        if(before.reason!="FULL_SNAPSHOT_AUTHENTICATION_REQUIRED")return before
        val current=access.get(Space.PRIMARY)
        return try {
            AttachmentBackupPreparation(DatabaseAccess.storageContext(current,context),current).capture(scope,cap).use {snapshot->
                val manifest=snapshot.manifest();val after=inventory(scope,cap,operation)
                snapshot.manifest() // Final authority/session check after the last suspended/IO phase.
                if(after.reason=="FULL_SNAPSHOT_AUTHENTICATION_REQUIRED" && matches(manifest,after,DatabaseAccess.handleIdentity(current)))
                    after.copy(eligibility=Eligibility.COMPLETE,reason="ENTIRE_SINGLE_LIBRARY_AUTHENTICATED")
                else after.copy(eligibility=Eligibility.REFUSED,reason="RETAINED_SET_CHANGED_OR_NOT_COVERED")
            }
        }catch(e:Exception) {
            // Never turn revocation/stale requests into a benign report.
            DatabaseAccess.withActiveHandle(current) {cap.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                check(AttachmentIdentity.current(current.openHelper.writableDatabase)==scope)
            }}
            before.copy(eligibility=Eligibility.REFUSED,reason="FULL_SNAPSHOT_REFUSED")
        }
    }
    fun writeArchive(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID,password:CharArray,output:OutputStream) {
        try {
        val before=inventory(scope,cap,operation)
        check(before.reason=="FULL_SNAPSHOT_AUTHENTICATION_REQUIRED") {"Complete export refused: ${before.reason}"}
        val current=access.get(Space.PRIMARY)
        AttachmentBackupPreparation(DatabaseAccess.storageContext(current,context),current).capture(scope,cap).use {snapshot->
            val manifest=snapshot.manifest()
            MedicalArchiveWriter.write(snapshot,cap,password,output)
            val after=inventory(scope,cap,operation);snapshot.manifest()
            check(after.reason=="FULL_SNAPSHOT_AUTHENTICATION_REQUIRED" && matches(manifest,after,DatabaseAccess.handleIdentity(current))) {"Export cannot claim the complete retained set"}
        }
        }finally {password.fill('\u0000')}
    }
    /** Exact target namespace and original request identity. Legacy/started/unknown owners retain
     * their full budget. Native abort + release is ONE commit, excluding a racing start-marker. */
    fun releaseUnmaterialized(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID,generation:UUID):Release=
        rootWork(scope,cap) {current,root,active->
            val raw=root.openHelper.writableDatabase
            val owner=raw.query("SELECT generation_uuid,origin_generation,origin_dataset,origin_epoch,started,released,release_digest FROM library_resource_owner WHERE operation_uuid=?",arrayOf(operation.toString())).use {c->
                check(c.moveToFirst()) {"Legacy reservation has no never-materialized proof"}
                check(c.getString(0)==generation.toString()) {"Wrong resource generation"}
                if(c.getInt(5)==1)return@rootWork Release(operation,generation,c.getBlob(6),true)
                check(c.getInt(4)==0) {"Materialization may already have begun"}
                check(c.getString(1)==DatabaseAccess.handleIdentity(current) && c.getString(2)==scope.dataset && c.getString(3)==scope.epoch) {"Resource origin is not this request scope"}
                true
            }
            check(owner)
            val plan=raw.query("SELECT state,directory_inode,database_alias,alias_plan FROM library_candidate WHERE operation_uuid=? AND generation_uuid=?",arrayOf(operation.toString(),generation.toString())).use {c->
                check(c.moveToFirst() && c.getString(0) in setOf("PREPARED","ABORTED") && c.isNull(1)) {"Referenced/materialized reservation"}
                LibraryRestoreObjects.decode(c.getBlob(3)).flatMap {s->listOf(AttachmentDeviceKeys.Alias(s.staging,AttachmentDeviceKeys.Role.STAGING).value,AttachmentDeviceKeys.Alias(s.active,AttachmentDeviceKeys.Role.ACTIVE).value)}+c.getString(2)
            }
            raw.query("SELECT 1 FROM library_authority WHERE active_generation=?",arrayOf(generation.toString())).use {check(!it.moveToFirst())}
            requireAbsent(generation)
            val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)};plan.forEach {active();check(!ks.containsAlias(it)) {"A planned key exists"}}
            val scan=LibraryResourceFacts.scan(context,raw,DatabaseAccess.handleIdentity(current),operation.toString(),active)
            val observed=persist(root,current,scope,operation,scan)
            check(scan.complete && observed.items.none {it.category in setOf("UNKNOWN","MISSING")}) {"Other resource ownership is not proven"}
            check(plan.none {it in scan.aliasReferences}) {"Resource still referenced"}
            raw.execSQL("UPDATE library_candidate SET state='ABORTED' WHERE operation_uuid=? AND state='PREPARED'",arrayOf(operation.toString()))
            val proof=hash("never-materialized-v1|$operation|$generation|${DatabaseAccess.handleIdentity(current)}|${scope.dataset}|${scope.epoch}".toByteArray(Charsets.UTF_8))
            raw.execSQL("UPDATE library_resource_owner SET released=1,release_digest=? WHERE operation_uuid=? AND started=0 AND released=0",arrayOf(proof,operation.toString()))
            persist(root,current,scope,operation,scan.copy(items=scan.items.map {if(it.key=="$generation/reservation")it.copy(category="RELEASED",reason="NEVER_MATERIALIZED_PROVEN") else it}))
            Release(operation,generation,proof,false)
        }
    private fun requireAbsent(generation:UUID) {
        try {Os.lstat(java.io.File(LibraryGenerationContext.directory(context),generation.toString()).path);error("A namespace exists")}
        catch(e:android.system.ErrnoException) {if(e.errno!=ENOENT)throw e}
    }
}

package net.plainnotes.app.data

import android.content.Context
import android.system.Os
import android.system.OsConstants.*
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.KeyStore
import java.util.UUID

/** Bounded read model of durable journals + held-directory observations. Not a validation cache. */
internal object LibraryResourceFacts {
    data class Item(val key:String,val library:String,val category:String,val reason:String,
        val bytes:Long?=null,val alias:String?=null) {
        fun json()=JSONObject().put("key",key).put("library",library).put("category",category).put("reason",reason).put("bytes",bytes ?: JSONObject.NULL).put("alias",alias ?: JSONObject.NULL)
        companion object {fun read(j:JSONObject)=Item(j.getString("key"),j.getString("library"),j.getString("category"),j.getString("reason"),if(j.isNull("bytes"))null else j.getLong("bytes"),j.optString("alias").takeUnless {it=="null"})}
    }
    data class Scan(val items:List<Item>,val retainedBusiness:Boolean,val aliasReferences:Set<String>,val complete:Boolean) {
        val unknown get()=items.any {it.category=="UNKNOWN"}
        val missing get()=items.any {it.category=="MISSING"}
    }
    private data class Fact(val name:String,val identity:AttachmentOwnedFiles.Identity?,val category:String)
    fun scan(context:Context,root:SupportSQLiteDatabase,active:String,excludeReservation:String?=null,checkActive:()->Unit,live:Pair<Context,SupportSQLiteDatabase>?=null):Scan {
        check(root.inTransaction());checkActive()
        val items=mutableListOf<Item>();val refs=mutableSetOf<String>();val knownAliases=mutableSetOf<String>();val requiredAliases=mutableSetOf<String>();val releasedAliases=mutableSetOf<String>()
        var retained=false;var complete=true
        fun add(i:Item) {require(items.size<8192) {"Lifecycle inventory entry limit"};items+=i}
        fun source(id:String,ctx:Context,db:SupportSQLiteDatabase,isRetained:Boolean) {
            checkActive()
            var rows=0L
            for(t in DOMAIN_TABLES)db.query("SELECT COUNT(*) FROM `$t`").use {it.moveToFirst();rows=Math.addExact(rows,it.getLong(0))}
            if(isRetained && rows>0)retained=true
            add(Item("$id/business",id,if(isRetained)"RETAINED_BUSINESS" else "CURRENT_BUSINESS","PERSISTED_ROWS:$rows"))
            val facts=mutableListOf<Fact>()
            db.query("""SELECT j.object_uuid,j.parent_device,j.parent_inode,j.file_device,j.file_inode,j.proof,o.phase,
                j.stage_alias_uuid,j.active_alias_uuid,v.version_uuid,d.active_version_uuid,d.state,s.store_state,j.record_epoch,s.record_epoch
                FROM attachment_journal j JOIN attachment_operation o USING(store_uuid,operation_uuid)
                JOIN attachment_store s ON s.store_uuid=j.store_uuid
                LEFT JOIN attachment_version v ON v.store_uuid=j.store_uuid AND v.version_uuid=j.version_uuid
                LEFT JOIN attachment_document d ON d.store_uuid=v.store_uuid AND d.document_uuid=v.document_uuid""").use {c->while(c.moveToNext()) {
                checkActive();require(facts.size<4096)
                val name=c.getString(0);require(UUID.fromString(name).toString()==name)
                for((column,role) in listOf(7 to AttachmentDeviceKeys.Role.STAGING,8 to AttachmentDeviceKeys.Role.ACTIVE)) {
                    val a=AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(column)),role).value;refs+=a;knownAliases+=a
                }
                val committed=c.getString(5)=="COMMITTED" && c.getString(6)=="COMMITTED" && !c.isNull(9)
                if(committed)for((column,role) in listOf(7 to AttachmentDeviceKeys.Role.STAGING,8 to AttachmentDeviceKeys.Role.ACTIVE))requiredAliases+=AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(column)),role).value
                val category=when {
                    !committed->"UNKNOWN"
                    isRetained || c.getString(12)!="CURRENT" || c.getString(13)!=c.getString(14)->"RETAINED_COMMITTED"
                    c.getString(11)!="ACTIVE"->"TRASHED"
                    c.getString(9)!=c.getString(10)->"HISTORY"
                    else->"COMMITTED"
                }
                facts+=Fact(name,if(c.isNull(4))null else AttachmentOwnedFiles.Identity(c.getLong(1),c.getLong(2),c.getLong(3),c.getLong(4)),category)
            }}
            db.query("SELECT wrapping_key_alias FROM attachment_key_envelope").use {c->while(c.moveToNext()) {checkActive();refs+=c.getString(0);knownAliases+=c.getString(0);requiredAliases+=c.getString(0)}}
            AttachmentOwnedFiles(ctx).locked().use {directory->
                val inventory=directory.inventory(4096,checkActive);val seen=mutableSetOf<Fact>();val byName=facts.groupBy {it.name}
                for(e in inventory.entries) {
                    val fact=byName[e.name]?.singleOrNull()?.takeIf {it.identity==e.identity && e.privateRegular}
                    if(fact!=null)seen+=fact
                    add(Item("$id/objects/${e.name}/${e.identity.device}/${e.identity.inode}",id,fact?.category ?: "UNKNOWN",if(fact==null)"UNPROVEN_OWNER" else "JOURNAL_AND_INODE",e.chargedBytes))
                }
                for(f in facts)if(f !in seen)add(Item("$id/journal/${f.name}",id,if(f.identity==null)"UNKNOWN" else "MISSING",if(f.identity==null)"LEGACY_RESERVATION_NO_MATERIALIZATION_PROOF" else "REFERENCED_OBJECT_ABSENT"))
            }
            if(Files.exists(File(ctx.noBackupFilesDir,AttachmentOwnedFiles.Namespace.RESTORE_QUARANTINE.directory).toPath(),LinkOption.NOFOLLOW_LINKS))AttachmentOwnedFiles(ctx,AttachmentOwnedFiles.Namespace.RESTORE_QUARANTINE).locked().use {d->
                for(e in d.inventory(4096,checkActive).entries)add(Item("$id/quarantine/${e.name}/${e.identity.device}/${e.identity.inode}",id,"UNKNOWN","QUARANTINE_OWNER_UNPROVEN",e.chargedBytes))
            }
        }
        source("LEGACY",context,root,active!="LEGACY")
        val known=mutableSetOf<String>()
        root.query("SELECT operation_uuid,generation_uuid,state,database_alias,alias_plan,directory_device,directory_inode FROM library_candidate").use {c->while(c.moveToNext()) {
            checkActive();require(known.size<4096)
            val op=c.getString(0);val id=UUID.fromString(c.getString(1));known+=id.toString()
            val aliases=LibraryRestoreObjects.decode(c.getBlob(4)).flatMap {s->listOf(AttachmentDeviceKeys.Alias(s.staging,AttachmentDeviceKeys.Role.STAGING).value,AttachmentDeviceKeys.Alias(s.active,AttachmentDeviceKeys.Role.ACTIVE).value)}+c.getString(3)
            knownAliases+=aliases;if(op!=excludeReservation)refs+=aliases
            val released=LibraryLifecycleSchema.released(root,op)
            val started=root.query("SELECT started FROM library_resource_owner WHERE operation_uuid=?",arrayOf(op)).use {if(it.moveToFirst())it.getInt(0) else null}
            val path=File(LibraryGenerationContext.directory(context),id.toString())
            val exists=Files.exists(path.toPath(),LinkOption.NOFOLLOW_LINKS)
            if(released)releasedAliases+=aliases
            val phase=when {c.getString(2) in setOf("ACTIVE","RETIRED") && exists->"COMMITTED_RESERVATION";released && !exists->"RELEASED";released->"UNKNOWN";started==0 && !exists->"RESERVED_UNMATERIALIZED";else->"RETAINED_RESERVATION"}
            add(Item("$id/reservation",id.toString(),phase,if(started==null)"LEGACY_OWNER_UNPROVEN" else c.getString(2)))
            if(exists && !released && c.getString(2) in setOf("ACTIVE","RETIRED") && !c.isNull(5) && !c.isNull(6)) {
                var ctx:LibraryGenerationContext?=null;var password:ByteArray?=null
                var db:net.zetetic.database.sqlcipher.SQLiteDatabase?=null
                try {
                    val owned=LibraryGenerationContext.open(context,id,false,c.getLong(5) to c.getLong(6)).also {ctx=it}
                    Files.newDirectoryStream(owned.noBackupFilesDir.toPath()).use {entries->for(e in entries) {
                        checkActive();val n=e.fileName.toString();val st=Os.lstat(e.toString())
                        val allowed=n in setOf("notes.db","notes.db-wal","notes.db-shm","notes.db-journal","key.wrap","key.wrap.bak","attachment-objects-v1","attachment-restore-quarantine-v1")
                        require(allowed && st.st_uid==Os.getuid() && st.st_mode and 63==0)
                        if(n.endsWith("-v1"))require(S_ISDIR(st.st_mode)) else require(S_ISREG(st.st_mode) && st.st_nlink==1L)
                        if(n=="key.wrap")require(st.st_size in 60..4096)
                        if(!n.endsWith("-v1"))add(Item("$id/local/$n",id.toString(),"LOCAL_ENCRYPTED_STORAGE","OWNED_DIRECTORY",maxOf(st.st_size,Math.multiplyExact(st.st_blocks,512))))
                    }}
                    // A read must not recover/rename a .bak or create a local key.
                    require(!File(owned.noBackupFilesDir,"key.wrap.bak").exists())
                    if(id.toString()==active && live!=null) {
                        checkActive();source(id.toString(),live.first,live.second,false)
                    } else {
                    val key=LocalDatabaseKey(owned).open("notes.db","key.wrap",c.getString(3),allowCreate=false).also {password=it}
                    val read=net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(owned.getDatabasePath("notes.db").path,key,null,net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,null).also {db=it}
                    require(read.version in 15..23) // Room21 changed activation guards, not retained archive semantics.
                    read.execSQL("PRAGMA temp_store=MEMORY");read.execSQL("PRAGMA cache_size=-2048")
                    read.query("PRAGMA mmap_size=0").use {require(it.moveToFirst() && it.getLong(0)==0L)}
                    source(id.toString(),owned,read,id.toString()!=active)
                    }

                }catch(t:Exception) {checkActive();complete=false;add(Item("$id/unreadable",id.toString(),"UNKNOWN","RETAINED_DATABASE_NOT_PROVEN"))}
                finally {try {db?.close()}finally {password?.fill(0);ctx?.close()}}
            }else if(exists) {
                complete=false;add(Item("$id/namespace",id.toString(),if(started!=null)"IN_FLIGHT" else "UNKNOWN","DIRECTORY_WITHOUT_COMPLETE_OWNER_PROOF"))
            }else if(c.getString(2) in setOf("ACTIVE","RETIRED","READY")) {
                complete=false;add(Item("$id/namespace",id.toString(),"MISSING","REQUIRED_GENERATION_ABSENT"))
            }
        }}
        val dir=LibraryGenerationContext.directory(context)
        if(Files.exists(dir.toPath(),LinkOption.NOFOLLOW_LINKS)) {
            val stat=Os.lstat(dir.path);require(S_ISDIR(stat.st_mode) && stat.st_uid==Os.getuid() && stat.st_mode and 63==0)
            Files.newDirectoryStream(dir.toPath()).use {entries->for(e in entries)if(e.fileName.toString() !in known) {checkActive();complete=false;add(Item("unknown-generation/${e.fileName}","UNKNOWN","UNKNOWN","UNREGISTERED_NAMESPACE"))}}
        }
        val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        val names=ks.aliases();var count=0
        while(names.hasMoreElements()) {
            checkActive();require(++count<=16384);val a=names.nextElement()
            if(a.startsWith("notes.obj.v1.") || a.startsWith("notes.gen.db."))add(Item("alias/$a","KEYSTORE",if(a in knownAliases && a !in releasedAliases)"RETAINED_ALIAS" else "UNKNOWN",if(a in releasedAliases)"APPEARED_AFTER_RELEASE" else if(a in knownAliases)"DURABLE_REFERENCE" else "UNKNOWN_ALIAS",alias=a))
        }
        for(a in requiredAliases)if(!ks.containsAlias(a))add(Item("missing-alias/$a","KEYSTORE","MISSING","REQUIRED_ALIAS_ABSENT",alias=a))
        return Scan(items,retained,refs,complete)
    }
}

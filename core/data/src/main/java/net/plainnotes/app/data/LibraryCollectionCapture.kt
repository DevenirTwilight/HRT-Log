package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.OutputStream
import java.util.UUID

/** OFF complete-set export. All source handles and plaintext are confined to one worker and
 * one native authority transaction. No snapshot or retained handle escapes that fence. */
internal class LibraryCollectionCapture(private val access:DatabaseAccess) {
    fun write(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID,
        password:CharArray,output:OutputStream):MedicalCollectionWriter.Receipt {
        val lifecycle=LibraryLifecycle(access)
        try {return lifecycle.rootWork(scope,cap) {current,root,active->
            val raw=root.openHelper.writableDatabase
            val thread=Thread.currentThread()
            fun fence() {check(Thread.currentThread()===thread && raw.inTransaction());active();LibraryAuthority.requireNoPending(raw);LibraryCollectionSchema.requireNoWrites(raw)}
            fence()
            val before=lifecycle.persist(root,current,scope,operation,lifecycle.fresh(root,current,::fence),collection=true)
            check(before.reason=="FULL_SNAPSHOT_AUTHENTICATION_REQUIRED") {"Collection export refused: ${before.reason}"}
            val close=ArrayList<AutoCloseable>()
            try {
                val sources=ArrayList<MedicalCollectionWriter.Source>()
                val covered=linkedSetOf<String>()
                fun source(identity:String,context:android.content.Context,db:androidx.sqlite.db.SupportSQLiteDatabase,checkDirectory:()->Unit={}) {
                    fence();require(sources.size<8)
                    val sourceScope=requireNotNull(AttachmentIdentity.current(db)) {"Unknown library identity"}
                    val session=AttachmentSession(UUID.fromString(sourceScope.dataset),UUID.fromString(sourceScope.epoch))
                    val lease=cap.own(AutoCloseable {session.revoke()});close+=lease
                    val snapshot=AttachmentBackupPreparation.fenced(context,db) {fence();checkDirectory()}
                        .capture(sourceScope,session.issue()).also {close+=it}
                    val manifest=snapshot.manifest()
                    val expected=manifest.versions.map {it.context.objectId.toString()}.toSet()
                    val actual=before.items.filter {it.library==identity && "/objects/" in it.key}
                        .map {it.key.substringAfter("/objects/").substringBefore('/')}.toSet()
                    check(expected==actual) {"Inventory and authenticated library differ"}
                    covered+=identity
                    sources+=MedicalCollectionWriter.Source(UUID.fromString(sourceScope.dataset),
                        if(identity==DatabaseAccess.handleIdentity(current))MedicalCollectionIndex.Role.ACTIVE else MedicalCollectionIndex.Role.RETAINED,snapshot)
                }
                source("LEGACY",access.applicationContext,raw)
                raw.query("SELECT generation_uuid,state,database_alias,directory_device,directory_inode,operation_uuid FROM library_candidate ORDER BY generation_uuid").use {c->
                    while(c.moveToNext()) {
                        fence()
                        if(c.getString(1)=="ABORTED" && LibraryLifecycleSchema.released(raw,c.getString(5)))continue
                        check(c.getString(1) in setOf("ACTIVE","RETIRED") && !c.isNull(3) && !c.isNull(4)) {"Unfinished library cannot be omitted"}
                        val id=UUID.fromString(c.getString(0))
                        check(c.getString(2)=="notes.gen.db.$id")
                        val directory=LibraryGenerationContext.open(access.applicationContext,id,false,c.getLong(3) to c.getLong(4)).also {close+=it}
                        // Never recover a backup envelope or create a missing key during export.
                        check(!File(directory.noBackupFilesDir,"key.wrap.bak").exists())
                        val key=LocalDatabaseKey(directory).open("notes.db","key.wrap",c.getString(2),allowCreate=false)
                        close+=AutoCloseable {key.fill(0)} // SQLCipher retains this array for later WAL connections.
                        val db=SQLiteDatabase.openDatabase(directory.getDatabasePath("notes.db").path,key,null,SQLiteDatabase.OPEN_READONLY,null)
                        close+=AutoCloseable {db.close()}
                        require(db.version in 15..17)
                        db.execSQL("PRAGMA temp_store=MEMORY");db.execSQL("PRAGMA cache_size=-2048")
                        db.query("PRAGMA mmap_size=0").use {check(it.moveToFirst() && it.getLong(0)==0L)}
                        source(id.toString(),directory,db,directory::checkCurrent)
                    }
                }
                check(before.items.filter {it.category in setOf("CURRENT_BUSINESS","RETAINED_BUSINESS")}.map {it.library}.toSet()==covered)
                val receipt=MedicalCollectionWriter.write(sources,UUID.fromString(scope.dataset),cap,password,output)
                fence()
                val after=lifecycle.persist(root,current,scope,operation,lifecycle.fresh(root,current,::fence),collection=true)
                check(after.reason=="FULL_SNAPSHOT_AUTHENTICATION_REQUIRED" && before.items==after.items) {"Complete resource set changed during export"}
                sources.forEach {it.snapshot.manifest()}
                receipt
            }finally {
                var failure:Throwable?=null
                close.asReversed().forEach {try {it.close()}catch(t:Throwable) {if(failure==null)failure=t else failure!!.addSuppressed(t)}}
                failure?.let {throw it}
            }
        }}finally {password.fill('\u0000')}
    }
}

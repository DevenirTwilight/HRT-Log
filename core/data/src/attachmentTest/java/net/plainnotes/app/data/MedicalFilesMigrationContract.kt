package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Published Room21 input, real DDL/rows and transaction failure. Shared with SQLCipher instrumentation. */
abstract class MedicalFilesMigrationContract {
    protected abstract fun factory():SupportSQLiteOpenHelper.Factory
    protected open val assetsContext:Context get()=ApplicationProvider.getApplicationContext()
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun old(name:String)=factory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
        .callback(object:SupportSQLiteOpenHelper.Callback(21) {
            override fun onCreate(db:SupportSQLiteDatabase) {
                val schema=assetsContext.assets.open("net.plainnotes.app.data.NotesDatabase/21.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject("database")}
                val entities=schema.getJSONArray("entities")
                for(i in 0 until entities.length()) {
                    val e=entities.getJSONObject(i);val table=e.getString("tableName")
                    db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                    val indices=e.optJSONArray("indices")
                    for(j in 0 until (indices?.length() ?: 0))db.execSQL(requireNotNull(indices).getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
                }
                val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())db.execSQL(setup.getString(i))
            }
            override fun onUpgrade(db:SupportSQLiteDatabase,a:Int,b:Int)=error("Old fixture may not upgrade itself")
        }).build())
    private fun seed(db:SupportSQLiteDatabase):AttachmentIdentity.Scope {
        SchemaGuards.install(db)
        db.beginTransaction()
        try {
            val s=requireNotNull(AttachmentIdentity.initialize(db,true))
            db.execSQL("INSERT INTO lab_analyte(code,canonical_unit) VALUES('E2','pg/mL')")
            db.execSQL("INSERT INTO lab_value(id,analyte_code,value,unit,sampled_utc,sampled_zone) VALUES(17,'E2',120,'pg/mL',1000,'UTC')")
            val record=AttachmentIdentity.bind(db,s,"LAB",17)
            // Byte-preservation fixture, not an authenticated-original claim. Every old FK and guard remains on.
            val doc=UUID.randomUUID().toString();val version=UUID.randomUUID().toString();val key=UUID.randomUUID().toString()
            val obj=UUID.randomUUID().toString();val operation=UUID.randomUUID().toString();val generation=UUID.randomUUID().toString()
            db.execSQL("INSERT INTO attachment_generation(store_uuid,generation_uuid,state,created_utc) VALUES(?,?,'STAGING',1)",arrayOf(s.dataset,generation))
            db.execSQL("INSERT INTO attachment_document(store_uuid,document_uuid,revision,title,category,state) VALUES(?,?,1,'SYNTHETIC preserved bytes','REPORT','ACTIVE')",arrayOf(s.dataset,doc))
            db.execSQL("INSERT INTO attachment_version(store_uuid,document_uuid,version_uuid,ordinal,object_uuid,protocol_revision,dek_handle_uuid,plaintext_sha256,ciphertext_sha256,plaintext_bytes,ciphertext_bytes,mime,status) VALUES(?,?,?,1,?,1,?,?,?,12,128,'application/pdf','STAGED')",arrayOf(s.dataset,doc,version,obj,key,ByteArray(32){it.toByte()},ByteArray(32){(it+1).toByte()}))
            db.execSQL("INSERT INTO attachment_key_envelope(store_uuid,document_uuid,version_uuid,envelope_uuid,dek_handle_uuid,wrap_epoch,wrapping_key_alias,wrapped_dek,created_utc,state) VALUES(?,?,?,?,?,1,'SYNTHETIC migration-only alias',?,1,'AVAILABLE')",arrayOf(s.dataset,doc,version,UUID.randomUUID().toString(),key,byteArrayOf(0,1,-1,2)))
            db.execSQL("INSERT INTO attachment_link(store_uuid,link_uuid,record_epoch,record_uuid,document_uuid,revision,state) VALUES(?,?,?,?,?,1,'QUARANTINED')",arrayOf(s.dataset,UUID.randomUUID().toString(),s.epoch,record,doc))
            db.execSQL("INSERT INTO attachment_operation(store_uuid,operation_uuid,idempotency_uuid,kind,phase,expected_generation,document_uuid,resource_generation_uuid,object_uuid,version_uuid,dek_handle_uuid,reserved_bytes,reserved_objects,updated_utc) VALUES(?,?,?,'IMPORT','PREPARED',1,?,?,?,?,?,128,1,1)",arrayOf(s.dataset,operation,operation,doc,generation,obj,version,key))
            db.execSQL("INSERT INTO attachment_journal(store_uuid,operation_uuid,record_epoch,physical_generation,document_uuid,record_uuid,request_sha256,attempt_uuid,object_uuid,version_uuid,dek_handle_uuid,stage_alias_uuid,active_alias_uuid,stage_ready,active_ready,stage_uses,active_uses,proof) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0,0,0,0,'RESERVED')",arrayOf(s.dataset,operation,s.epoch,generation,doc,record,ByteArray(32){(it+2).toByte()},UUID.randomUUID().toString(),obj,version,key,UUID.randomUUID().toString(),UUID.randomUUID().toString()))
            db.setTransactionSuccessful();return s
        }finally {db.endTransaction()}
    }
    private fun rows(db:SupportSQLiteDatabase)=listOf("lab_value","attachment_store","attachment_record_identity","attachment_journal","attachment_link","attachment_document","attachment_version","attachment_key_envelope","attachment_operation","attachment_generation").associateWith {table->
        db.query("SELECT * FROM `$table` ORDER BY rowid").use {c->buildList {
            while(c.moveToNext())add((0 until c.columnCount).map {column->
                when(c.getType(column)) {
                    android.database.Cursor.FIELD_TYPE_NULL->null
                    android.database.Cursor.FIELD_TYPE_BLOB->c.getBlob(column).toList()
                    android.database.Cursor.FIELD_TYPE_INTEGER->c.getLong(column)
                    android.database.Cursor.FIELD_TYPE_FLOAT->c.getDouble(column)
                    else->c.getString(column)
                }
            })
        }}
    }
    @Test fun published21RetainsRowsIdentitiesAndValidatesRoom22AfterReopen() {
        val name="synthetic-med03-upgrade-${UUID.randomUUID()}.db"
        try {
            lateinit var scope:AttachmentIdentity.Scope
            val before=old(name).use {h->scope=seed(h.writableDatabase);assertEquals(21,h.writableDatabase.version);rows(h.writableDatabase)}
            repeat(2) {
                val room=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory()).addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
                try {
                    val raw=room.openHelper.writableDatabase
                    assertEquals(23,raw.version);assertEquals(before,rows(raw));assertEquals(scope,AttachmentIdentity.current(raw))
                    raw.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
                    raw.query("PRAGMA table_info(attachment_journal)").use {c->var seen=false;while(c.moveToNext())if(c.getString(1)=="record_uuid"){assertEquals(0,c.getInt(3));seen=true};assertTrue(seen)}
                }finally {room.close()}
            }
        }finally {context.deleteDatabase(name)}
    }
    @Test fun ddlFailureRollsBackOriginalRowsTablesIndicesAndTriggers() {
        val name="synthetic-med03-rollback-${UUID.randomUUID()}.db"
        try {old(name).use {h->
            val raw=h.writableDatabase;seed(raw);val before=rows(raw)
            fun ddl()=raw.query("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY type,name").use {c->buildList {while(c.moveToNext())add(listOf(c.getString(0),c.getString(1),c.getString(2)))}}
            val schema=ddl();var injected=false
            raw.beginTransaction()
            try {MedicalFilesSchema.migrate(raw) {injected=true;throw IllegalStateException("synthetic interruption after table drops")};fail("Failure was swallowed")}
            catch(e:IllegalStateException) {assertEquals("synthetic interruption after table drops",e.message)}
            finally {raw.endTransaction()}
            assertTrue(injected);assertEquals(21,raw.version);assertEquals(before,rows(raw));assertEquals(schema,ddl())
            raw.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
        }}finally {context.deleteDatabase(name)}
    }
}

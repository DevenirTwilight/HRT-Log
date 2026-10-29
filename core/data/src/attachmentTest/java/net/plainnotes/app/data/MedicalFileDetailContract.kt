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

/** MED-04 Room23: a published Room22 database gains the user-entered file detail table without any existing row or guard changing. Shared with SQLCipher instrumentation. */
abstract class MedicalFileDetailContract {
    protected abstract fun factory():SupportSQLiteOpenHelper.Factory
    protected open val assetsContext:Context get()=ApplicationProvider.getApplicationContext()
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun old(name:String)=factory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
        .callback(object:SupportSQLiteOpenHelper.Callback(22) {
            override fun onCreate(db:SupportSQLiteDatabase) {
                val schema=assetsContext.assets.open("net.plainnotes.app.data.NotesDatabase/22.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject("database")}
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
    private fun seed(db:SupportSQLiteDatabase):Pair<AttachmentIdentity.Scope,String> {
        SchemaGuards.install(db)
        db.beginTransaction()
        try {
            val s=requireNotNull(AttachmentIdentity.initialize(db,true));val doc=UUID.randomUUID().toString()
            db.execSQL("INSERT INTO attachment_document(store_uuid,document_uuid,revision,title,category,state) VALUES(?,?,1,'SYNTHETIC kept document','REPORT','ACTIVE')",arrayOf(s.dataset,doc))
            db.setTransactionSuccessful();return s to doc
        }finally {db.endTransaction()}
    }
    private fun documents(db:SupportSQLiteDatabase)=db.query("SELECT * FROM attachment_document ORDER BY rowid").use {c->buildList {while(c.moveToNext())add((0 until c.columnCount).map {i->if(c.isNull(i))null else c.getString(i)})}}
    private fun room(name:String)=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory()).addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
    private fun rejected(db:SupportSQLiteDatabase,sql:String,vararg args:Any?) {
        try {db.execSQL(sql,arrayOf(*args));fail("Accepted: $sql ${args.toList()}")}catch(e:android.database.SQLException) {assertTrue(e.message.orEmpty(),e.message.orEmpty().isNotEmpty())}
    }
    @Test fun published22KeepsEveryRowAndAddsTheDetailTableOnce() {
        val name="synthetic-med04-upgrade-${UUID.randomUUID()}.db"
        try {
            lateinit var seeded:Pair<AttachmentIdentity.Scope,String>
            val before=old(name).use {h->seeded=seed(h.writableDatabase);assertEquals(22,h.writableDatabase.version);documents(h.writableDatabase)}
            repeat(2) {
                val room=room(name)
                try {
                    val raw=room.openHelper.writableDatabase
                    assertEquals(23,raw.version);assertEquals(before,documents(raw));assertEquals(seeded.first,AttachmentIdentity.current(raw))
                    raw.query("SELECT COUNT(*) FROM ${MedicalFileDetailSchema.TABLE}").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
                    raw.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
                    raw.query("SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE '%medical_file_detail%' ORDER BY name").use {c->
                        val names=buildList {while(c.moveToNext())add(c.getString(0))}
                        assertEquals(listOf("guard_medical_file_detail_insert","guard_medical_file_detail_update","medical_file_detail_keep","medical_file_detail_revision"),names)
                    }
                }finally {room.close()}
            }
        }finally {context.deleteDatabase(name)}
    }
    @Test fun theTableRefusesEverythingTheOperationNeverWrites() {
        val name="synthetic-med04-rules-${UUID.randomUUID()}.db"
        try {
            lateinit var seeded:Pair<AttachmentIdentity.Scope,String>
            old(name).use {h->seeded=seed(h.writableDatabase)}
            val room=room(name)
            try {
                val raw=room.openHelper.writableDatabase;val store=seeded.first.dataset;val doc=seeded.second
                val insert="INSERT INTO ${MedicalFileDetailSchema.TABLE}(store_uuid,document_uuid,file_category,document_date,institution,detail_revision,updated_utc) VALUES(?,?,?,?,?,?,?)"
                rejected(raw,insert,store,doc,"XRAY",null,null,1,1)
                rejected(raw,insert,store,doc,null,"2026-02-30",null,1,1)
                rejected(raw,insert,store,doc,null,"2026-2-3",null,1,1)
                rejected(raw,insert,store,doc,null,"10/07/2026",null,1,1)
                rejected(raw,insert,store,doc,null,null,"   ",1,1)
                rejected(raw,insert,store,doc,null,null,"x".repeat(MedicalFileDetailSchema.INSTITUTION_MAX+1),1,1)
                rejected(raw,insert,store,doc,null,null,null,0,1)
                rejected(raw,insert,store,UUID.randomUUID().toString(),null,null,null,1,1)
                raw.execSQL(insert,arrayOf(store,doc,"LAB_REPORT","2026-10-07","某医院 ABC",1,5))
                rejected(raw,insert,store,doc,null,null,null,1,1)
                val update="UPDATE ${MedicalFileDetailSchema.TABLE} SET file_category=?,document_date=?,institution=?,detail_revision=?,updated_utc=6 WHERE document_uuid=?"
                rejected(raw,update,"OTHER",null,null,3,doc)
                rejected(raw,update,"OTHER",null,null,1,doc)
                raw.execSQL(update,arrayOf(null,null,null,2,doc))
                raw.query("SELECT file_category,document_date,institution,detail_revision FROM ${MedicalFileDetailSchema.TABLE}").use {
                    assertTrue(it.moveToFirst());assertTrue(it.isNull(0) && it.isNull(1) && it.isNull(2));assertEquals(2,it.getInt(3))
                }
                rejected(raw,"DELETE FROM ${MedicalFileDetailSchema.TABLE} WHERE document_uuid=?",doc)
                rejected(raw,"DELETE FROM attachment_document WHERE document_uuid=?",doc)
            }finally {room.close()}
        }finally {context.deleteDatabase(name)}
    }
    @Test fun interruptedMigrationLeavesThePublishedDatabaseUntouched() {
        val name="synthetic-med04-rollback-${UUID.randomUUID()}.db"
        try {old(name).use {h->
            val raw=h.writableDatabase;seed(raw);val before=documents(raw)
            fun ddl()=raw.query("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY type,name").use {c->buildList {while(c.moveToNext())add(listOf(c.getString(0),c.getString(1),c.getString(2)))}}
            val schema=ddl()
            raw.beginTransaction()
            try {MedicalFileDetailSchema.migrate(raw);throw IllegalStateException("synthetic interruption after the new table")}
            catch(e:IllegalStateException) {assertEquals("synthetic interruption after the new table",e.message)}
            finally {raw.endTransaction()}
            assertEquals(22,raw.version);assertEquals(before,documents(raw));assertEquals(schema,ddl())
        }}finally {context.deleteDatabase(name)}
    }
}

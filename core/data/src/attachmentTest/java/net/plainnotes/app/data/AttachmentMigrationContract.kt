package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.security.MessageDigest
import java.util.UUID

/** Same real Room migration assertions run locally and with native SQLCipher; synthetic DBs only. */
abstract class AttachmentMigrationContract {
    protected abstract fun factory(pass: ByteArray): SupportSQLiteOpenHelper.Factory
    protected open val assetsContext: Context get() = context
    protected open fun assertEncryptedDatabase(name: String) {}
    protected val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun schema(version:Int)=assetsContext.assets
        .open("net.plainnotes.app.data.NotesDatabase/$version.json").bufferedReader().use{JSONObject(it.readText()).getJSONObject("database")}
    private fun fixture(name:String,pass:ByteArray,version:Int):SupportSQLiteOpenHelper {
        val exported=schema(version)
        val config=SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object:SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db:SupportSQLiteDatabase) {
                    val entities=exported.getJSONArray("entities")
                    for(i in 0 until entities.length()) {
                        val e=entities.getJSONObject(i);val table=e.getString("tableName")
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                        val indices=e.optJSONArray("indices") ?: org.json.JSONArray()
                        for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
                    }
                    val setup=exported.getJSONArray("setupQueries")
                    for(i in 0 until setup.length())db.execSQL(setup.getString(i))
                }
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Fixture cannot migrate")
            }).build()
        return factory(pass).create(config)
    }
    private fun fingerprint(db:SupportSQLiteDatabase):Map<String,String> {
        val entities=schema(9).getJSONArray("entities")
        return (0 until entities.length()).associate { i ->
            val t=entities.getJSONObject(i).getString("tableName")
            val canonical=buildString {
                db.query("SELECT * FROM `$t` ORDER BY rowid").use{c->while(c.moveToNext()){for(j in 0 until c.columnCount){append(c.getType(j));append(':');append(if(c.isNull(j))"<null>" else c.getString(j));append('|')};append('\n')}}
                db.query("SELECT type,name,sql FROM sqlite_master WHERE tbl_name=? AND type IN('table','index') ORDER BY type,name",arrayOf(t)).use{c->while(c.moveToNext()){append(c.getString(0));append(c.getString(1));append(c.getString(2));append('\n')}}
            }
            t to MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString(""){"%02x".format(it)}
        }
    }
    @Test fun encryptedRoom9MigratesAllLegacyRowsIndicesGuardsAndReopens()=runBlocking {
        val name="synthetic-b1-migration-${UUID.randomUUID()}.db";val pass=ByteArray(32){(it+1).toByte()}
        val password="synthetic-native-password".toCharArray()
        var backup:ByteArray?=null
        var room:NotesDatabase?=null
        try {
            val before=fixture(name,pass,9).use { helper ->
                val raw=helper.writableDatabase;SchemaGuards.install(raw)
                raw.execSQL("INSERT INTO day_note(date,text) VALUES ('2026-01-01','synthetic native migration')")
                raw.execSQL("INSERT INTO appointment(id,type,at_utc,at_zone,note,remind_minutes_before,completed_utc) VALUES (1,'ENDO',1000,'UTC','synthetic visit',0,1000)")
                raw.execSQL("INSERT INTO lab_analyte(code,canonical_unit) VALUES ('E2','pg/mL')")
                raw.execSQL("INSERT INTO lab_value(id,analyte_code,value,unit,sampled_utc,sampled_zone) VALUES (1,'E2',12,'pg/mL',1000,'UTC')")
                raw.execSQL("INSERT INTO medication(id,name,molecule,route,unit,dose_per_intake,container_capacity,site_rotation,notifications_on,active,sort_order) VALUES (1,'Synthetic native stock','OTHER','ORAL','MG',1,10,0,0,1,0)")
                raw.execSQL("INSERT INTO dose_record(id,medication_id,taken_utc,taken_zone,actual_dose,unallocated_supply_amount,status,origin,revision,config_snapshot) VALUES (1,1,1000,'UTC',1,0,'ON_TIME','APP',1,'{}')")
                raw.execSQL("INSERT INTO dose_record(id,medication_id,status,origin,revision,config_snapshot) VALUES (2,1,'SKIPPED','APP',1,'{}')")
                raw.execSQL("INSERT INTO supply_container(id,medication_id,capacity,initial_used_amount,used_amount,state) VALUES (1,1,10,0,0,'IN_USE'),(2,1,10,0,0,'IN_USE')")
                raw.execSQL("INSERT INTO supply_transaction(id,container_id,dose_record_id,dose_revision,operation_id,kind,used_delta,created_utc,created_zone) VALUES (1,1,1,1,'synthetic-consume','CONSUME',1,1000,'UTC')")
                raw.execSQL("INSERT INTO supply_transaction(id,container_id,operation_id,kind,used_delta,created_utc,created_zone) VALUES (2,2,'synthetic-adjust','ADJUST',9,1000,'UTC')")
                fun digest(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
                val box=Trash.rows(raw,"supply_container","id=?",arrayOf(2)).getJSONObject(0)
                val operation=UUID.randomUUID().toString()
                val ledger=digest(Trash.rows(raw,"supply_transaction","container_id=? ORDER BY id",arrayOf(2)).toString())
                val evidence=digest(Trash.rows(raw,"medication","id=? ORDER BY id",arrayOf(1)).toString()+
                    Trash.rows(raw,"supply_container","medication_id=? AND id!=? ORDER BY id",arrayOf(1,2)).toString()+
                    Trash.rows(raw,"dose_record","medication_id=? ORDER BY id",arrayOf(1)).toString()+
                    Trash.rows(raw,"supply_transaction","container_id IN (SELECT id FROM supply_container WHERE medication_id=?) ORDER BY id",arrayOf(1)).toString())
                val payload=JSONObject().put("version",1).put("container",box).put("ledger_sha256",ledger).put("context_sha256",evidence).put("operation_id",operation).put("action","void")
                raw.execSQL("INSERT INTO trash_item(kind,ref,item_date,deleted_utc,payload_json,state) VALUES ('CORRECTION',?,'2026-01-01',1000,?,'HIDDEN')",arrayOf("package-void:2:$operation",payload.toString()))
                raw.execSQL("UPDATE supply_container SET state='VOIDED' WHERE id=2")
                PackageCorrections.validate(raw)
                assertEquals(9,raw.version)
                val data=JSONObject().put("format",1).put("schema",9).put("tables",RawData.dump(raw)).toString().toByteArray()
                try { backup=BackupCodec.encrypt(data,password) } finally { data.fill(0) }
                fingerprint(raw)
            }
            assertEquals(29,before.size)
            fun open()=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(pass))
                .addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
            room=open();val db=room!!;val raw=db.openHelper.writableDatabase
            assertEquals(10,raw.version);assertEquals(before,fingerprint(raw))
            raw.query("PRAGMA foreign_key_check").use{assertEquals(0,it.count)}
            raw.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'attachment_%'").use{assertEquals(8,it.count)}
            val scope=db.withTransaction { requireNotNull(AttachmentIdentity.initialize(raw,true)) }
            val id=db.withTransaction { AttachmentIdentity.bind(raw,scope,"APPOINTMENT",1) }
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
            repo.restoreBackup(requireNotNull(backup),password)
            try {raw.execSQL("UPDATE supply_transaction SET used_delta=2 WHERE id=1");fail("Ledger protection lost")}
            catch(_:android.database.SQLException){}
            raw.query("SELECT state FROM supply_container WHERE id=2").use{assertTrue(it.moveToFirst());assertEquals("VOIDED",it.getString(0))}
            raw.query("SELECT SUM(capacity-used_amount) FROM supply_container WHERE state IN('IN_USE','SEALED')").use{assertTrue(it.moveToFirst());assertEquals(9.0,it.getDouble(0),0.0)}
            val next=requireNotNull(AttachmentIdentity.current(raw));assertNotEquals(scope.dataset,next.dataset);assertNotEquals(scope.epoch,next.epoch)
            assertNotEquals(id,db.withTransaction{AttachmentIdentity.bind(raw,next,"APPOINTMENT",1)})
            assertEquals(before,fingerprint(raw))
            val pending=db.withTransaction{AttachmentOperations.prepare(raw,next,UUID.randomUUID(),"IMPORT",10,1)}
            room!!.close();room=open();assertEquals(next,AttachmentIdentity.current(room!!.openHelper.writableDatabase))
            assertEquals(pending,room!!.attachments().pending(next.dataset).single().operation_uuid)
            assertEquals("PREPARED",room!!.attachments().pending(next.dataset).single().phase)
            assertEquals(before,fingerprint(room!!.openHelper.writableDatabase))
            assertEncryptedDatabase(name)
        } finally { room?.close();context.deleteDatabase(name);pass.fill(0);password.fill('\u0000');backup?.fill(0) }
    }
    @Test fun nativeOldestSchemaMigrationChainValidatesRoom10() {
        val name="synthetic-b1-oldest-${UUID.randomUUID()}.db";val pass=ByteArray(32){5};var room:NotesDatabase?=null
        try {
            fixture(name,pass,1).use{it.writableDatabase.execSQL("INSERT INTO day_note(date,text) VALUES ('2026-01-01','synthetic oldest chain')")}
            room=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(pass))
                .addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
            val raw=room!!.openHelper.writableDatabase;assertEquals(10,raw.version)
            raw.query("SELECT text FROM day_note").use{assertTrue(it.moveToFirst());assertEquals("synthetic oldest chain",it.getString(0))}
            raw.query("PRAGMA foreign_key_check").use{assertEquals(0,it.count)}
        } finally {room?.close();context.deleteDatabase(name);pass.fill(0)}
    }
    @Test fun nativeMigrationFailureRollsBackDDLAndLeavesOriginalRoom9Readable() {
        val name="synthetic-b1-migration-failure-${UUID.randomUUID()}.db";val pass=ByteArray(32){11};var room:NotesDatabase?=null
        try {
            fixture(name,pass,9).use{it.writableDatabase.execSQL("INSERT INTO day_note(date,text) VALUES ('2026-01-01','synthetic rollback sentinel')")}
            val fault=object:androidx.room.migration.Migration(9,10) {
                override fun migrate(db:SupportSQLiteDatabase) {AttachmentSchema.migrate(db);error("synthetic migration interruption")}
            }
            room=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(pass)).addMigrations(fault).build()
            try{room!!.openHelper.writableDatabase;fail("Interrupted migration reported success")}catch(_:IllegalStateException){}
            room!!.close();room=null
            fixture(name,pass,9).use {
                val db=it.writableDatabase;assertEquals(9,db.version)
                db.query("SELECT text FROM day_note").use{c->assertTrue(c.moveToFirst());assertEquals("synthetic rollback sentinel",c.getString(0))}
                db.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'attachment_%'").use{c->assertEquals(0,c.count)}
            }
            room=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(pass)).addMigrations(migration9To10).addCallback(SchemaGuards).build()
            assertEquals(10,room!!.openHelper.writableDatabase.version)
        } finally {room?.close();context.deleteDatabase(name);pass.fill(0)}
    }

    @Test fun twoEncryptedDatabasesKeepSameIntegerTargetsAndAttachmentNamespaceSeparate()=runBlocking {
        val names=listOf("synthetic-b1-primary-${UUID.randomUUID()}.db","synthetic-b1-private-${UUID.randomUUID()}.db")
        val passwords=listOf(ByteArray(32){13},ByteArray(32){17});val opened=mutableListOf<NotesDatabase>()
        try {
            for(i in 0..1) {
                fixture(names[i],passwords[i],9).use{it.writableDatabase.execSQL("INSERT INTO appointment(id,type,at_utc,at_zone,note,remind_minutes_before) VALUES (1,'ENDO',1000,'UTC',?,0)",arrayOf("synthetic isolated $i"))}
                opened+=Room.databaseBuilder(context,NotesDatabase::class.java,names[i]).openHelperFactory(factory(passwords[i]))
                    .addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
            }
            val primary=opened[0];val privateDb=opened[1]
            val scope=primary.withTransaction{requireNotNull(AttachmentIdentity.initialize(primary.openHelper.writableDatabase,true))}
            primary.withTransaction{AttachmentIdentity.bind(primary.openHelper.writableDatabase,scope,"APPOINTMENT",1)}
            privateDb.withTransaction{assertNull(AttachmentIdentity.initialize(privateDb.openHelper.writableDatabase,false))}
            assertTrue(privateDb.attachments().current().isEmpty())
            try{privateDb.withTransaction{AttachmentIdentity.bind(privateDb.openHelper.writableDatabase,scope,"APPOINTMENT",1)};fail("Cross-database target accepted")}
            catch(_:IllegalStateException){}
            for(table in listOf("attachment_store","attachment_record_identity","attachment_document","attachment_version","attachment_link","attachment_operation","attachment_generation","attachment_key_envelope"))
                privateDb.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
            assertEquals("synthetic isolated 0",primary.dao().appointmentById(1)!!.note)
            assertEquals("synthetic isolated 1",privateDb.dao().appointmentById(1)!!.note)
        } finally {opened.forEach{it.close()};names.forEach{context.deleteDatabase(it)};passwords.forEach{it.fill(0)}}
    }

}

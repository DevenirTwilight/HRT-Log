package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

/** Authoritative exported schemas 1..11, then the real production migration and Room validation. */
abstract class ReferenceMigrationContract {
    protected abstract fun factory(key:ByteArray):SupportSQLiteOpenHelper.Factory
    protected open val assetsContext:Context get()=context
    protected val context:Context get()=ApplicationProvider.getApplicationContext()
    private inline fun <T> NotesDatabase.useRoom(block:(NotesDatabase)->T):T = try {block(this)} finally {close()}
    private fun schema(version:Int)=assetsContext.assets.open("net.plainnotes.app.data.NotesDatabase/$version.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject("database")}
    private fun fixture(name:String,key:ByteArray,version:Int)=factory(key.clone()).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
        .callback(object:SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db:SupportSQLiteDatabase) {
                val definition=schema(version);val entities=definition.getJSONArray("entities")
                for(i in 0 until entities.length()) {
                    val e=entities.getJSONObject(i);val table=e.getString("tableName")
                    db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                    val indices=e.optJSONArray("indices") ?: JSONArray()
                    for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
                }
                val setup=definition.getJSONArray("setupQueries");for(i in 0 until setup.length())db.execSQL(setup.getString(i))
            }
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Fixture cannot migrate")
        }).build())
    private fun fingerprint(db:SupportSQLiteDatabase):String {
        val definitions=schema(10).getJSONArray("entities")
        return JSONObject().apply {
            for(i in 0 until definitions.length()) {
                val table=definitions.getJSONObject(i).getString("tableName");val rows=JSONArray()
                db.query("SELECT ${definitions.getJSONObject(i).getJSONArray("fields").let{fields->(0 until fields.length()).map{fields.getJSONObject(it).getString("columnName")}}.joinToString(","){"`$it`"}} FROM `$table` ORDER BY rowid").use {c->while(c.moveToNext()) rows.put(JSONArray((0 until c.columnCount).map {column->
                    JSONArray().put(c.getType(column)).put(if(c.isNull(column)) JSONObject.NULL else if(c.getType(column)==android.database.Cursor.FIELD_TYPE_BLOB)
                        android.util.Base64.encodeToString(c.getBlob(column),android.util.Base64.NO_WRAP) else if(c.getType(column)==android.database.Cursor.FIELD_TYPE_FLOAT) java.lang.Double.doubleToRawLongBits(c.getDouble(column)).toString() else c.getString(column))
                }))}
                put(table,rows)
                val ddl=JSONArray()
                db.query("SELECT type,name,sql FROM sqlite_master WHERE tbl_name=? AND type IN ('table','index') ORDER BY type,name",arrayOf(table)).use {c->while(c.moveToNext())ddl.put(JSONArray().put(c.getString(0)).put(c.getString(1)).put(if(c.isNull(2))JSONObject.NULL else c.getString(2).let { sql ->
                    if(table=="supply_container" && c.getString(0)=="table" && InventoryEncoding.present(db)) {
                        val allowed=listOf(", inventory_encoding TEXT NOT NULL DEFAULT 'LEGACY_SCALAR'",", `inventory_encoding` TEXT NOT NULL DEFAULT 'LEGACY_SCALAR'")
                        val fragment=allowed.single{sql.contains(it)}
                        sql.replace(fragment,"")
                    } else sql
                }))}
                put("ddl_$table",ddl)
            }
        }.toString()
    }
    @Test fun everyPublishedRoomOneThroughElevenUpgradesNonDestructivelyToTwelve() {
        for(version in 1..11) {
            val name="synthetic-reference-migration-$version-${UUID.randomUUID()}.db";val key=ByteArray(32){(version+it).toByte()}
            try {
                fixture(name,key,version).use {it.writableDatabase.execSQL("INSERT INTO day_note(date,text) VALUES('2026-10-16',?)",arrayOf("SYNTHETIC schema $version"))}
                Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone()))
                    .addMigrations(*allMigrations()).addCallback(SchemaGuards).allowMainThreadQueries().build().useRoom {room->
                        val raw=room.openHelper.writableDatabase;assertEquals(12,raw.version)
                        raw.query("SELECT text FROM day_note").use {assertTrue(it.moveToFirst());assertEquals("SYNTHETIC schema $version",it.getString(0));assertFalse(it.moveToNext())}
                        raw.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
                        ReferenceSchema.businessTables.forEach {table->raw.query("SELECT COUNT(*) FROM `$table`").use {it.moveToFirst();assertEquals(0,it.getInt(0))}}
                        raw.query("SELECT COUNT(*) FROM reference_runtime").use {it.moveToFirst();assertEquals(1,it.getInt(0))}
                    }
            } finally {context.deleteDatabase(name);key.fill(0)}
        }
    }
    @Test fun roomTenReverseLedgerAndAllThirtySevenLegacyEntitiesRemainBitExactAndDowngradeRejects() {
        val name="synthetic-reference-stock-${UUID.randomUUID()}.db";val key=ByteArray(32){39}
        try {
            val before=fixture(name,key,10).use {helper->
                val raw=helper.writableDatabase;SchemaGuards.install(raw)
                raw.execSQL("INSERT INTO medication(id,name,molecule,unit,dose_per_intake,container_capacity,site_rotation,notifications_on,active,sort_order) VALUES(1,'SYNTHETIC legacy stock','OTHER','TABLET',1,10,0,0,0,0)")
                raw.execSQL("INSERT INTO dose_record(id,medication_id,taken_utc,taken_zone,actual_dose,unallocated_supply_amount,status,origin,revision,config_snapshot) VALUES(1,1,1000,'UTC',1,0,'ON_TIME','APP',1,'{}')")
                raw.execSQL("INSERT INTO supply_container(id,medication_id,capacity,initial_used_amount,used_amount,state) VALUES(1,1,10,0,0,'IN_USE')")
                raw.execSQL("INSERT INTO supply_transaction(id,container_id,dose_record_id,dose_revision,operation_id,kind,used_delta,created_utc,created_zone) VALUES(1,1,1,1,'synthetic-original','CONSUME',1,1000,'UTC')")
                raw.execSQL("UPDATE dose_record SET revision=2,actual_dose=1.5 WHERE id=1")
                raw.execSQL("INSERT INTO supply_transaction(id,container_id,dose_record_id,dose_revision,operation_id,kind,used_delta,reversal_of_id,created_utc,created_zone) VALUES(2,1,1,2,'synthetic-reverse','REVERSE',-1,1,2000,'UTC')")
                raw.execSQL("INSERT INTO supply_transaction(id,container_id,dose_record_id,dose_revision,operation_id,kind,used_delta,created_utc,created_zone) VALUES(3,1,1,2,'synthetic-corrected','CONSUME',1.5,2000,'UTC')")
                fingerprint(raw)
            }
            fun open()=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
            open().useRoom {assertEquals(before,fingerprint(it.openHelper.writableDatabase));assertEquals(12,it.openHelper.writableDatabase.version);it.openHelper.writableDatabase.query("SELECT inventory_encoding FROM supply_container").use{c->while(c.moveToNext())assertEquals(InventoryEncoding.LEGACY,c.getString(0))}}
            fixture(name,key,10).use {old->
                try {old.writableDatabase;fail("Old application silently downgraded new database")} catch(_:android.database.SQLException){}
            }
            open().useRoom {assertEquals(before,fingerprint(it.openHelper.writableDatabase))}
        } finally {context.deleteDatabase(name);key.fill(0)}
    }
    @Test fun oldRoomElevenReaderCannotOpenTheNewMeasurementFormat()=kotlinx.coroutines.runBlocking {
        val name="synthetic-measured-format-fence-${UUID.randomUUID()}.db";val key=ByteArray(32){71}
        try {
            fixture(name,key,11).use {it.writableDatabase.execSQL("INSERT INTO medication(id,name,molecule,unit,dose_per_intake,container_capacity,site_rotation,notifications_on,active,sort_order) VALUES(1,'SYNTHETIC format fence','OTHER','MG',1,28,0,0,0,0)")}
            fun open()=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(*allMigrations()).addCallback(SchemaGuards).allowMainThreadQueries().build()
            val before=open().useRoom {room->
                room.withTransaction {
                    val product=UserReferenceProduct("SYNTHETIC 2mg tablets",listOf("SYNTHETIC ingredient"),sourceVersion="synthetic-v1",recordedOn="2026-10-16")
                    val draft=PackageMeasurementDraft(product,net.plainnotes.app.domain.measurement.MeasureUnit.TABLET,
                        net.plainnotes.app.domain.measurement.Meaning(net.plainnotes.app.domain.measurement.QuantityKind.USE_COUNT,"SYNTHETIC 2mg tablet"),
                        "0.5","28",net.plainnotes.app.domain.stock.StockCapacityBasis.USER_COUNTED)
                    MeasuredContainerStore.create(room,ReferenceStore.previewEpoch(room.openHelper.writableDatabase),1,UUID.randomUUID().toString(),draft,java.time.Instant.parse("2026-10-16T12:00:00Z"))
                }
                RawData.dump(room.openHelper.writableDatabase).toString()
            }
            var rejected=false
            fixture(name,key,11).use {old->try{old.writableDatabase}catch(_:android.database.SQLException){rejected=true}}
            assertTrue("Room11 reader opened the new measurement format and could misinterpret integer stock quanta",rejected)
            open().useRoom {assertEquals(before,RawData.dump(it.openHelper.writableDatabase).toString())}
        } finally {context.deleteDatabase(name);key.fill(0)}
    }
    @Test fun failedElevenToTwelveFenceRollsBackWithAllReferenceFactsAndEpochIntact() {
        val name="synthetic-fence-rollback-${UUID.randomUUID()}.db";val key=ByteArray(32){83};var invoked=false
        try {
            val before=fixture(name,key,11).use {helper->
                val raw=helper.writableDatabase;SchemaGuards.install(raw);raw.beginTransaction()
                try {
                    raw.execSQL("INSERT INTO medication(id,name,molecule,unit,dose_per_intake,container_capacity,site_rotation,notifications_on,active,sort_order) VALUES(1,'SYNTHETIC Room11 reference','OTHER','MG',1,28,0,0,0,0)")
                    ReferenceStore.initialAssociation(raw,1,UUID.randomUUID().toString(),UserReferenceProduct("SYNTHETIC old reference",sourceVersion="old-v1",recordedOn="2026-10-16"),ReferenceStore.previewEpoch(raw))
                    raw.setTransactionSuccessful()
                } finally {raw.endTransaction()}
                PublishedBusinessFixture.dump(raw).toString() to raw.query("SELECT dataset_uuid,epoch FROM reference_runtime").use{assertTrue(it.moveToFirst());it.getString(0) to it.getString(1)}
            }
            val injected=object:androidx.room.migration.Migration(11,12) {
                override fun migrate(db:SupportSQLiteDatabase) {
                    invoked=true;migration11To12.migrate(db);db.execSQL("CREATE TABLE synthetic_interrupted_fence(id INTEGER PRIMARY KEY)")
                    error("SYNTHETIC format fence interrupted")
                }
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(injected).build().useRoom {room->
                try{room.openHelper.writableDatabase;fail("Interrupted format fence succeeded")}catch(e:IllegalStateException){assertTrue(e.message.orEmpty().contains("SYNTHETIC format fence interrupted"))}
            }
            assertTrue(invoked)
            fixture(name,key,11).use {helper->
                val raw=helper.writableDatabase;assertEquals(11,raw.version);assertEquals(before.first,PublishedBusinessFixture.dump(raw).toString())
                raw.query("SELECT name FROM sqlite_master WHERE name='synthetic_interrupted_fence'").use{assertFalse(it.moveToFirst())}
                raw.query("SELECT dataset_uuid,epoch FROM reference_runtime").use{assertTrue(it.moveToFirst());assertEquals(before.second,it.getString(0) to it.getString(1))}
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(*allMigrations()).addCallback(SchemaGuards).build().useRoom {room->
                val raw=room.openHelper.writableDatabase;assertEquals(12,raw.version);assertEquals(before.first,PublishedBusinessFixture.dump(raw).toString())
                raw.query("SELECT dataset_uuid,epoch FROM reference_runtime").use{assertTrue(it.moveToFirst());assertEquals(before.second,it.getString(0) to it.getString(1))}
            }
        } finally {context.deleteDatabase(name);key.fill(0)}
    }
    @Test fun unsupportedMeasurementsInOldRoomElevenAreRejectedWithoutClearingAnything() {
        val name="synthetic-old-unrecognized-measurement-${UUID.randomUUID()}.db";val key=ByteArray(32){89}
        try {
            val before=fixture(name,key,11).use {helper->
                val raw=helper.writableDatabase;SchemaGuards.install(raw);raw.beginTransaction()
                try {
                    val scope=ReferenceStore.scope(raw)
                    ReferenceStore.insert(raw,"reference_snapshot",JSONObject().put("uuid",UUID.randomUUID().toString()).put("dataset_uuid",scope.dataset)
                        .put("kind","PRESENTATION").put("namespace","USER_ENTERED").put("source_version","unknown-old-prototype")
                        .put("payload_json","{}").put("payload_sha256",ReferencePayload.sha("{}")))
                    raw.setTransactionSuccessful()
                } finally {raw.endTransaction()}
                PublishedBusinessFixture.dump(raw).toString()
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(*allMigrations()).build().useRoom {room->
                try{room.openHelper.writableDatabase;fail("Old unknown measurement was promoted to the new format")}
                catch(e:Exception){assertTrue(generateSequence<Throwable>(e){it.cause}.any{it.message.orEmpty().contains("Unrecognized measurement data in the old database format")})}
            }
            fixture(name,key,11).use{assertEquals(11,it.writableDatabase.version);assertFalse(InventoryEncoding.present(it.writableDatabase));assertEquals(before,PublishedBusinessFixture.dump(it.writableDatabase).toString())}
        } finally {context.deleteDatabase(name);key.fill(0)}
    }
    @Test fun failedTenToElevenMigrationRollsBackDdlAndKeepsOriginalEncryptedDatabaseReadable() {
        val name="synthetic-reference-failed-migration-${UUID.randomUUID()}.db";val key=ByteArray(32){63};var invoked=false
        try {
            fixture(name,key,10).use {it.writableDatabase.execSQL("INSERT INTO day_note(date,text) VALUES('2026-10-16','SYNTHETIC intact Room10')")}
            val injected=object:androidx.room.migration.Migration(10,11) {
                override fun migrate(db:SupportSQLiteDatabase) {invoked=true;ReferenceSchema.migrate(db);error("SYNTHETIC reference migration interrupted")}
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(injected,migration11To12).build().useRoom {room->
                try {room.openHelper.writableDatabase;fail("Interrupted migration succeeded")}catch(e:IllegalStateException){assertTrue(e.message.orEmpty().contains("SYNTHETIC reference migration interrupted"))}
            }
            assertTrue(invoked)
            fixture(name,key,10).use {helper->
                val raw=helper.writableDatabase;assertEquals(10,raw.version)
                raw.query("SELECT text FROM day_note").use {assertTrue(it.moveToFirst());assertEquals("SYNTHETIC intact Room10",it.getString(0))}
                raw.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN (${(ReferenceSchema.businessTables+"reference_runtime").joinToString(",") {"'"+it+"'"}})").use {assertEquals(0,it.count)}
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(*allMigrations()).addCallback(SchemaGuards).build().useRoom {assertEquals(12,it.openHelper.writableDatabase.version)}
        } finally {context.deleteDatabase(name);key.fill(0)}
    }
}

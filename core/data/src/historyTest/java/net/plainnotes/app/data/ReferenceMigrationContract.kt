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
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin

/** Authoritative exported schemas 1..17, then the real production migration and Room validation. */
abstract class ReferenceMigrationContract {
    @Test fun syntheticFixtureByteCounterMatchesWholePlatformDocumentWithoutMutatingRows() {
        val random=java.util.Random(0x464958L)
        val texts=listOf("", "é 法 藥 \u0000 \ud83d\ude00", "\"\\\n", "\ud800", "\udc00", "x".repeat(65537))+
            List(128){CharArray(97){random.nextInt(65536).toChar()}.concatToString()}
        for(text in texts) {
            val tables=JSONObject().put("empty",JSONArray()).put("synthetic é",JSONArray()
                .put(JSONObject().put("text",text).put("blob","b64:AAH/Cg==").put("null",JSONObject.NULL)
                    .put("min",Long.MIN_VALUE).put("max",Long.MAX_VALUE).put("double",0.125).put("negativeZero",-0.0))
                .put(JSONObject().put("nested",JSONArray().put(text).put(JSONObject.NULL)))).put("last",JSONArray())
            val before=tables.toString();val expected=before.toByteArray(Charsets.UTF_8).size
            assertEquals(expected,SyntheticFixtureJsonSize.utf8Bytes(tables))
            assertEquals(before,tables.toString())
            assertThrows(BackupCodec.TooLarge::class.java){SyntheticFixtureJsonSize.utf8Bytes(tables,expected-1)}
            assertEquals(expected,SyntheticFixtureJsonSize.utf8Bytes(tables,expected))
        }
        assertEquals(2,SyntheticFixtureJsonSize.utf8Bytes(JSONObject()))
    }
    private companion object {
        /** The only DDL Room 11→12 (LAB-02) adds to a Room 10 table: exactly this column clause and this index. */
        const val LAB_PANEL_COLUMN=", `panel_id` INTEGER REFERENCES `lab_panel`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT"
        const val LAB_PANEL_INDEX="index_lab_value_panel_id"
    }
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
                // LAB-02 (Room 12) adds lab_value.panel_id, always NULL for legacy rows (checked separately); every Room 10 column stays bit-exact.
                val legacyColumns=definitions.getJSONObject(i).getJSONArray("fields").let{f->(0 until f.length()).joinToString(","){"`"+f.getJSONObject(it).getString("columnName")+"`"}}
                db.query("SELECT $legacyColumns FROM `$table` ORDER BY rowid").use {c->while(c.moveToNext()) rows.put(JSONArray((0 until c.columnCount).map {column->
                    JSONArray().put(c.getType(column)).put(if(c.isNull(column)) JSONObject.NULL else if(c.getType(column)==android.database.Cursor.FIELD_TYPE_BLOB)
                        android.util.Base64.encodeToString(c.getBlob(column),android.util.Base64.NO_WRAP) else if(c.getType(column)==android.database.Cursor.FIELD_TYPE_FLOAT) java.lang.Double.doubleToRawLongBits(c.getDouble(column)).toString() else c.getString(column))
                }))}
                put(table,rows)
                val ddl=JSONArray()
                db.query("SELECT type,name,sql FROM sqlite_master WHERE tbl_name=? AND type IN ('table','index') ORDER BY type,name",arrayOf(table)).use {c->while(c.moveToNext())if(c.getString(1)!=LAB_PANEL_INDEX)
                    ddl.put(JSONArray().put(c.getString(0)).put(c.getString(1)).put(if(c.isNull(2))JSONObject.NULL else PublishedBusinessFixture.publishedDdl(db,table,c.getString(0),c.getString(2))!!.replace(LAB_PANEL_COLUMN,"")))}
                put("ddl_$table",ddl)
            }
        }.toString()
    }
    @Test fun utf8LengthMatchesPlatformForEveryCodeUnitPairsAndBoundaries() {
        fun check(text:String) {
            val expected=text.toByteArray(Charsets.UTF_8).size.toLong();val actual=ReferencePayload.utf8Length(text)
            assertEquals(if(expected==actual) "" else "UTF16 prefix="+text.take(64).map{it.code.toString(16)},expected,actual)
        }
        for(code in 0..65535)check(code.toChar().toString())
        val random=java.util.Random(0x55465438L)
        repeat(10000) {check(String(CharArray(random.nextInt(65)){random.nextInt(65536).toChar()}))}
        listOf("", "ASCII\u0000é中😀", "\uD800\uDC00", "\uD800\uD800\uDC00", "\uD800\uDC00\uDC00",
            "\uDC00\uD800", "\uD800x\uDC00").forEach(::check)
        for(count in listOf(65535,65536,65537)) {
            check("a".repeat(count));check("é".repeat(count/2)+"a".repeat(count%2))
            check("中".repeat(count/3)+"a".repeat(count%3))
            check("😀".repeat(count/4)+"a".repeat(count%4))
        }
        assertEquals(4L,ReferencePayload.utf8Length("\uD800\uDC00"))
        assertEquals(2L,ReferencePayload.utf8Length("\uD800")+ReferencePayload.utf8Length("\uDC00"))
    }

    @Test fun storedColumnFenceMatchesTheOriginalPerColumnOracleForNullTypesAndLateRows() {
        val name="synthetic-column-oracle-${UUID.randomUUID()}.db";val key=ByteArray(32){(it+31).toByte()}
        val helper=factory(key).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object:SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db:SupportSQLiteDatabase) {for(table in DOMAIN_TABLES)db.execSQL("CREATE TABLE `$table` (`i` INTEGER, `t` TEXT, `r` REAL)")}
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Isolated SQL fixture cannot migrate")
            }).build())
        try {
            val raw=helper.writableDatabase;raw.beginTransaction()
            try {
                val good=arrayOf<Any?>(1L,"SYNTHETIC",1.25)
                val cases=listOf(arrayOf<Any?>(null,null,null),good,arrayOf<Any?>("bad","SYNTHETIC",1.25),
                    arrayOf<Any?>(0.25,"SYNTHETIC",1.25),arrayOf<Any?>(byteArrayOf(1),"SYNTHETIC",1.25),
                    arrayOf<Any?>(Long.MAX_VALUE,"SYNTHETIC",1.25),arrayOf<Any?>(3.0,"SYNTHETIC",1.25),
                    arrayOf<Any?>(1L,byteArrayOf(1),1.25),arrayOf<Any?>(1L,42,1.25),
                    arrayOf<Any?>(1L,"SYNTHÉTIQUE 雌二醇\u0000text",1.25),arrayOf<Any?>(1L,"SYNTHETIC","bad"),
                    arrayOf<Any?>(1L,"SYNTHETIC",byteArrayOf(1)),arrayOf<Any?>(1L,"SYNTHETIC",Double.POSITIVE_INFINITY),
                    arrayOf<Any?>(1L,"SYNTHETIC",Double.NEGATIVE_INFINITY),arrayOf<Any?>(1L,"SYNTHETIC",Double.MAX_VALUE),
                    arrayOf<Any?>(1L,"SYNTHETIC",-2.5),arrayOf<Any?>("bad",null,null),
                    arrayOf<Any?>(null,byteArrayOf(1),null),arrayOf<Any?>(null,null,"bad"),
                    arrayOf<Any?>(null,null,Double.NaN),arrayOf<Any?>(1L,"x".repeat(1048576),1.25),
                    arrayOf<Any?>(1L,"x".repeat(1048577),1.25))
                val batches=listOf(emptyList<Array<Any?>>())+cases.flatMap{listOf(listOf(it,good),listOf(good,it))}
                var checked=0
                for(rows in batches) {
                    raw.execSQL("DELETE FROM dose_record")
                    for(row in rows)raw.execSQL("INSERT INTO dose_record(i,t,r) VALUES(?,?,?)",row)
                    // Frozen old predicate algebra, independent of the new aggregate composition.
                    val expected=raw.query("SELECT COALESCE(MAX(CASE WHEN i IS NOT NULL AND NOT(typeof(i)='integer') THEN 1 ELSE 0 END),0), COALESCE(MAX(CASE WHEN t IS NOT NULL AND NOT(typeof(t)='text' AND length(t)<=1048576) THEN 1 ELSE 0 END),0), COALESCE(MAX(CASE WHEN r IS NOT NULL AND NOT(typeof(r) IN ('integer','real') AND r BETWEEN -1.7976931348623157e308 AND 1.7976931348623157e308) THEN 1 ELSE 0 END),0) FROM dose_record").use { c ->c.moveToFirst();(0..2).all{c.getLong(it)==0L}}
                    val actual=try{BackupValidation.validateCandidateStoredColumns(raw);true}catch(_:IllegalArgumentException){false}
                    assertEquals("Original stored-value oracle case ${checked++}",expected,actual)
                }
                assertEquals(45,checked)
            } finally {raw.endTransaction()}
        } finally {helper.close();context.deleteDatabase(name)}
    }

    // Frozen independent DOM oracle from bba9562d DataTransfer.kt, before the shared row-reader refactor.
    private fun frozenDumpBeforeStreaming(db: SupportSQLiteDatabase, tableNames: List<String> = DOMAIN_TABLES.filterNot{it in setOf("external_import_item","featherline_import_item",ChrysalideImportSchema.TABLE)}): JSONObject {
        // Explicit trusted subset is used to construct old-version fixture archives; production exports always use all tables.
        require(tableNames.distinct().size == tableNames.size && tableNames.all { it in DOMAIN_TABLES })
        val tables = JSONObject()
        tableNames.forEach { t ->
            val rows = JSONArray()
            // Fixture archives of older schemas (explicit subset) leave out columns their table does not have yet (e.g. LAB-02 panel_id).
            val present = db.query("PRAGMA table_info(`$t`)").use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
            val wanted = RecordBackupCompatibility.columns.getValue(t)
            if (tableNames == DOMAIN_TABLES) require(present.containsAll(wanted)) { "Incomplete table $t" }
            db.query("SELECT ${wanted.filter { it in present }.joinToString(",") { "`$it`" }} FROM $t").use { c ->
                while (c.moveToNext()) rows.put(JSONObject().apply {
                    for (i in 0 until c.columnCount) when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> put(c.getColumnName(i), JSONObject.NULL)
                        android.database.Cursor.FIELD_TYPE_INTEGER -> put(c.getColumnName(i), c.getLong(i))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> {
                            val exact= t=="supply_container" && c.getColumnIndex("inventory_encoding")>=0 && c.getString(c.getColumnIndex("inventory_encoding"))==InventoryEncoding.EXACT
                            if(exact && c.getColumnName(i) in setOf("capacity","initial_used_amount","used_amount"))put(c.getColumnName(i),InventoryEncoding.exactInteger(c.getDouble(i)))
                            else put(c.getColumnName(i),c.getDouble(i))
                        }
                        android.database.Cursor.FIELD_TYPE_BLOB -> put(c.getColumnName(i), "b64:" + android.util.Base64.encodeToString(c.getBlob(i), android.util.Base64.NO_WRAP))
                        else -> put(c.getColumnName(i), c.getString(i))
                    }
                })
            }
            tables.put(t, rows)
        }
        return tables
    }

    @Test fun streamedMeasuredBackupMatchesWholeDocumentBytesAndRemainsReadOnly() {
        val name="synthetic-backup-stream-${UUID.randomUUID()}.db";val key=ByteArray(32){(it+31).toByte()}
        try {
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key))
                .addCallback(SchemaGuards).allowMainThreadQueries().build().useRoom {room->
                val raw=room.openHelper.writableDatabase
                raw.beginTransaction()
                try {
                    raw.execSQL("INSERT INTO medication(id,name,molecule,route,unit,dose_per_intake,container_capacity,site_rotation,notifications_on,active,sort_order) VALUES(1,'SYNTHETIC','OTHER',NULL,'MG',0.125,28.0,0,0,1,0)")
                    raw.execSQL("INSERT INTO supply_container(id,medication_id,capacity,initial_used_amount,used_amount,state,inventory_encoding) VALUES(1,1,28.5,0.25,0.25,'SEALED','LEGACY_SCALAR')")
                    raw.execSQL("INSERT INTO supply_container(id,medication_id,capacity,initial_used_amount,used_amount,state,inventory_encoding) VALUES(2,1,56.0,1.0,1.0,'SEALED','INTEGER_QUANTA_V1')")
                    val random=java.util.Random(0x42414bL)
                    val texts=listOf("SYNTHETIC \" \\ \n é 法 藥 \u0000 \ud83d\ude00", "x".repeat(65535), "é".repeat(32769))+
                        List(24){CharArray(127){random.nextInt(65536).toChar()}.concatToString()}
                    texts.forEachIndexed{i,text->raw.execSQL("INSERT INTO day_note(date,text) VALUES(?,?)",arrayOf("synthetic-$i",text))}
                    raw.execSQL("INSERT INTO day_note(date,text) VALUES('synthetic-blob',?)",arrayOf(byteArrayOf(0,1,-1,10)))
                    val created=java.time.Instant.parse("2026-10-19T00:00:00Z")
                    // Freeze the pre-BDPM user-only wire version; maximum reader version is now 13.
                    val old=JSONObject().put("format",BackupCodec.FORMAT_VERSION).put("schema",12)
                        .put("created",created.toString()).put("tables",frozenDumpBeforeStreaming(raw)).toString().toByteArray(Charsets.UTF_8)
                    fun scalar(sql:String)=raw.query(sql).use{it.moveToFirst();it.getLong(0)}
                    val before=scalar("SELECT total_changes()");val schema=scalar("PRAGMA schema_version")
                    val streamed=MeasuredBackupJson.encode(raw,created)
                    try {
                        assertArrayEquals("Same full document bytes, including table/column order and blob encoding",old,streamed)
                        assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(old),java.security.MessageDigest.getInstance("SHA-256").digest(streamed))
                        val exact=MeasuredBackupJson.encode(raw,created,maxBytes=old.size)
                        try{assertArrayEquals(old,exact)}finally{exact.fill(0)}
                        try{MeasuredBackupJson.encode(raw,created,maxBytes=old.size-1);fail("One byte over limit accepted")}
                        catch(_:BackupCodec.TooLarge){}
                        assertEquals(before,scalar("SELECT total_changes()"));assertEquals(schema,scalar("PRAGMA schema_version"))
                    } finally {old.fill(0);streamed.fill(0)}
                } finally {raw.endTransaction()}
            }
        } finally {key.fill(0);context.deleteDatabase(name)}
    }

    @Test fun boundedBackupPlaintextChunksAreExactAndWipedOnSuccessAndFailure() {
        for(limit in listOf(0,1,3,4,5,9,17)) {
            val buffer=BackupPlainBytes(limit,4)
            val field=buffer.javaClass.getDeclaredField("chunks").apply{isAccessible=true}
            val input=ByteArray(limit){(it+1).toByte()}
            buffer.append(input)
            @Suppress("UNCHECKED_CAST") val held=(field.get(buffer) as List<ByteArray>).toList()
            assertArrayEquals(input,buffer.finish())
            try{buffer.append(byteArrayOf(77));fail("Over-limit append accepted")}
            catch(_:BackupCodec.TooLarge){}
            assertArrayEquals(input,buffer.finish())
            buffer.close();buffer.close()
            assertTrue(held.all{chunk->chunk.all{it==0.toByte()}})
            try{buffer.append(byteArrayOf());fail("Closed plaintext buffer reused")}
            catch(_:IllegalStateException){}
        }
    }

    @Test fun backupPlaintextOwnerClearsSuccessFailureAndRejectedTransfers() {
        for(failAfterCapture in listOf(false,true)) {
            val bytes=byteArrayOf(1,2,3,4)
            val owner=BackupPlaintextOwner()
            try {
                owner.use {
                    it.capture(bytes);assertSame(bytes,it.bytes())
                    if(failAfterCapture)throw IllegalStateException("SYNTHETIC cipher failure")
                }
                assertFalse(failAfterCapture)
            } catch(e:IllegalStateException) {assertTrue(failAfterCapture)}
            assertTrue(bytes.all{it==0.toByte()})
            try{owner.bytes();fail("Closed plaintext remained accessible")}catch(_:IllegalStateException){}
            val rejected=byteArrayOf(9,8)
            try{owner.capture(rejected);fail("Closed owner accepted plaintext")}catch(_:IllegalStateException){}
            assertTrue(rejected.all{it==0.toByte()})
            owner.close()
        }
        val first=byteArrayOf(4,3);val second=byteArrayOf(2,1)
        BackupPlaintextOwner().use {owner->
            owner.capture(first)
            try{owner.capture(first);fail("Duplicate ownership accepted")}catch(_:IllegalStateException){}
            assertSame(first,owner.bytes());assertArrayEquals(byteArrayOf(4,3),first)
            try{owner.capture(second);fail("Replaced live plaintext")}catch(_:IllegalStateException){}
            assertTrue(second.all{it==0.toByte()});assertArrayEquals(byteArrayOf(4,3),first)
        }
        assertTrue(first.all{it==0.toByte()})
    }

    @Test fun cancellationAfterPlaintextCaptureRollsBackAndClearsBeforeTransactionReturns()=kotlinx.coroutines.runBlocking {
        val name="synthetic-export-cancel-${UUID.randomUUID()}.db";val key=ByteArray(32){(it+43).toByte()}
        try {
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key))
                .addCallback(SchemaGuards).allowMainThreadQueries().build().useRoom {room->
                val raw=room.openHelper.writableDatabase
                val before=RawData.dump(raw).toString();val bytes=byteArrayOf(11,12,13)
                val captured=kotlinx.coroutines.CompletableDeferred<Unit>();val owner=BackupPlaintextOwner()
                val job=launch(kotlinx.coroutines.Dispatchers.Default) {
                    owner.use {
                        room.withTransaction {
                            raw.execSQL("INSERT INTO day_note(date,text) VALUES('2001-01-02','SYNTHETIC cancelled seed')")
                            it.capture(bytes);captured.complete(Unit)
                            kotlinx.coroutines.awaitCancellation()
                        }
                    }
                }
                try{kotlinx.coroutines.withTimeout(10000){captured.await()}}finally{job.cancelAndJoin()}
                assertTrue(bytes.all{it==0.toByte()});assertEquals(before,RawData.dump(raw).toString())
                try{owner.bytes();fail("Cancelled owner remained accessible")}catch(_:IllegalStateException){}
            }
        } finally {key.fill(0);context.deleteDatabase(name)}
    }

    @Test fun canonicalUuidPredicateMatchesThePlatformOracleAcrossVersionsAndMalformedForms() {
        val random=java.util.Random(0x414e534dL)
        var checked=0
        fun compare(text:String) {
            val expected=runCatching{UUID.fromString(text).toString()==text}.getOrDefault(false)
            assertEquals("Canonical UUID oracle case ${checked++}",expected,ReferencePayload.uuid(text))
        }
        repeat(10000){compare(UUID(random.nextLong(),random.nextLong()).toString())}
        for(version in 0..15)for(variant in 0..7) {
            val most=(random.nextLong() and (15L shl 12).inv()) or (version.toLong() shl 12)
            val least=(random.nextLong() and 0x1fffffffffffffffL) or (variant.toLong() shl 61)
            val text=UUID(most,least).toString()
            assertTrue("Old canonical identifiers must not acquire a v4-only restriction",ReferencePayload.uuid(text));compare(text)
        }
        val base="00112233-4455-6677-8899-aabbccddeeff"
        val chars=charArrayOf('0','9','a','f','A','F','g','-','_','+',' ', '\n','\u0000','\u0661','\uff11','\ud800')
        for(index in base.indices)for(value in chars)compare(base.toCharArray().also{it[index]=value}.concatToString())
        for(value in listOf("", "0-0-0-0-0",base.uppercase(java.util.Locale.ROOT)," "+base,base+" ",
            base.replace("-",""),"000000000-0000-0000-0000-000000000000",base.dropLast(1),base+"0",
            "00000000-0000-0000-0000-000000000000","ffffffff-ffff-ffff-ffff-ffffffffffff","x".repeat(100000)))compare(value)
        assertEquals(10716,checked)
    }

    @Test fun everyPublishedRoomOneThroughElevenUpgradesNonDestructivelyToTwelve() {
        for(version in 1..18) {
            val name="synthetic-reference-migration-$version-${UUID.randomUUID()}.db";val key=ByteArray(32){(version+it).toByte()}
            try {
                fixture(name,key,version).use {it.writableDatabase.execSQL("INSERT INTO day_note(date,text) VALUES('2026-10-16',?)",arrayOf("SYNTHETIC schema $version"))}
                Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone()))
                    .addMigrations(*allMigrations()).addCallback(SchemaGuards).allowMainThreadQueries().build().useRoom {room->
                        val raw=room.openHelper.writableDatabase;assertEquals(23,raw.version)
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
            open().useRoom {assertEquals(before,fingerprint(it.openHelper.writableDatabase));assertEquals(23,it.openHelper.writableDatabase.version);it.openHelper.writableDatabase.query("SELECT inventory_encoding FROM supply_container").use{c->while(c.moveToNext())assertEquals(InventoryEncoding.LEGACY,c.getString(0))}}
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
            for(maximum in listOf(11,12)) {
                var rejected=false
                fixture(name,key,maximum).use {old->try{old.writableDatabase}catch(_:android.database.SQLException){rejected=true}}
                assertTrue("Old Room$maximum reader opened the new measurement format and could misinterpret integer stock quanta",rejected)
            }
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
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(injected,migration12To13,migration13To14,migration14To15,migration15To16,migration16To17,migration17To18,migration18To19,migration19To20,migration20To21,migration21To22,migration22To23).build().useRoom {room->
                try{room.openHelper.writableDatabase;fail("Interrupted format fence succeeded")}catch(e:IllegalStateException){assertTrue(e.message.orEmpty().contains("SYNTHETIC format fence interrupted"))}
            }
            assertTrue(invoked)
            fixture(name,key,11).use {helper->
                val raw=helper.writableDatabase;assertEquals(11,raw.version);assertEquals(before.first,PublishedBusinessFixture.dump(raw).toString())
                raw.query("SELECT name FROM sqlite_master WHERE name='synthetic_interrupted_fence'").use{assertFalse(it.moveToFirst())}
                raw.query("SELECT dataset_uuid,epoch FROM reference_runtime").use{assertTrue(it.moveToFirst());assertEquals(before.second,it.getString(0) to it.getString(1))}
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(*allMigrations()).addCallback(SchemaGuards).build().useRoom {room->
                val raw=room.openHelper.writableDatabase;assertEquals(23,raw.version);assertEquals(before.first,PublishedBusinessFixture.dump(raw).toString())
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
    @Test fun publishedRoomTwelveMayLackEncodingButClaimedThirteenMustRejectIt() {
        val name="synthetic-encoding-version-boundary-${UUID.randomUUID()}.db";val key=ByteArray(32){101}
        try {
            fixture(name,key,12).use {h->
                val raw=h.writableDatabase;assertFalse(InventoryEncoding.present(raw));assertEquals(12,raw.version)
                InventoryEncoding.install(raw)
                raw.version=13
                try {InventoryEncoding.install(raw);fail("Claimed current format accepted a missing encoding field")}
                catch(_:IllegalArgumentException){}
                finally {raw.version=12}
                assertFalse(InventoryEncoding.present(raw));assertEquals(12,raw.version)
            }
        } finally {context.deleteDatabase(name);key.fill(0)}
    }

    @Test fun failedMainTwelveToThirteenFenceKeepsPanelLinksLegacyBitsAndEpoch() {
        val name="synthetic-main12-fence-${UUID.randomUUID()}.db";val key=ByteArray(32){97};var invoked=false
        try {
            val before=fixture(name,key,12).use {h->
                val raw=h.writableDatabase;SchemaGuards.install(raw);raw.beginTransaction()
                try {
                    raw.execSQL("INSERT INTO medication(id,name,molecule,unit,dose_per_intake,container_capacity,site_rotation,notifications_on,active,sort_order) VALUES(1,'SYNTHETIC main12','OTHER','MG',1,20.25,0,0,0,0)")
                    raw.execSQL("INSERT INTO supply_container(id,medication_id,capacity,initial_used_amount,used_amount,state) VALUES(1,1,20.25,0.5,0.5,'SEALED')")
                    raw.execSQL("INSERT INTO lab_analyte(code,canonical_unit) VALUES('E2','pg/mL')")
                    raw.execSQL("INSERT INTO lab_panel(id,uuid,sampled_utc,sampled_zone,created_utc) VALUES(7,?,1000,'UTC',1000)",arrayOf(UUID.randomUUID().toString()))
                    raw.execSQL("INSERT INTO lab_value(id,analyte_code,value,unit,sampled_utc,sampled_zone,panel_id) VALUES(1,'E2',100,'pg/mL',1000,'UTC',7)")
                    ReferenceStore.initialAssociation(raw,1,UUID.randomUUID().toString(),UserReferenceProduct("SYNTHETIC unchanged reference",sourceVersion="synthetic-v1",recordedOn="2026-10-19"),ReferenceStore.previewEpoch(raw))
                    raw.setTransactionSuccessful()
                } finally {raw.endTransaction()}
                PublishedBusinessFixture.dump(raw,schema=11).toString() to raw.query("SELECT dataset_uuid,epoch FROM reference_runtime").use{assertTrue(it.moveToFirst());it.getString(0) to it.getString(1)}
            }
            val fault=object:androidx.room.migration.Migration(12,13) {
                override fun migrate(db:SupportSQLiteDatabase) {
                    invoked=true;migration12To13.migrate(db)
                    db.execSQL("CREATE TABLE synthetic_inventory_fence_interrupt(id INTEGER PRIMARY KEY)")
                    error("SYNTHETIC main12 inventory fence interrupted")
                }
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(fault,migration13To14,migration14To15,migration15To16,migration16To17,migration17To18,migration18To19,migration19To20,migration20To21,migration21To22,migration22To23).build().useRoom {room->
                try{room.openHelper.writableDatabase;fail("Interrupted main12 inventory fence succeeded")}
                catch(e:Exception){assertTrue(generateSequence<Throwable>(e){it.cause}.any{it.message.orEmpty().contains("SYNTHETIC main12 inventory fence interrupted")})}
            }
            assertTrue(invoked)
            fixture(name,key,12).use {h->
                val raw=h.writableDatabase;assertEquals(12,raw.version);assertFalse(InventoryEncoding.present(raw))
                assertEquals(before.first,PublishedBusinessFixture.dump(raw,schema=11).toString())
                raw.query("SELECT name FROM sqlite_master WHERE name='synthetic_inventory_fence_interrupt'").use{assertFalse(it.moveToFirst())}
                raw.query("SELECT dataset_uuid,epoch FROM reference_runtime").use{assertTrue(it.moveToFirst());assertEquals(before.second,it.getString(0) to it.getString(1))}
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(*allMigrations()).addCallback(SchemaGuards).build().useRoom {room->
                val raw=room.openHelper.writableDatabase;assertEquals(23,raw.version)
                assertEquals(before.first,PublishedBusinessFixture.dump(raw,schema=11).toString())
                raw.query("SELECT inventory_encoding,capacity,initial_used_amount,used_amount FROM supply_container").use{assertTrue(it.moveToFirst());assertEquals(InventoryEncoding.LEGACY,it.getString(0));assertEquals(20.25,it.getDouble(1),0.0);assertEquals(0.5,it.getDouble(2),0.0);assertEquals(0.5,it.getDouble(3),0.0)}
                raw.query("SELECT panel_id FROM lab_value").use{assertTrue(it.moveToFirst());assertEquals(7,it.getInt(0))}
                raw.query("SELECT dataset_uuid,epoch FROM reference_runtime").use{assertTrue(it.moveToFirst());assertEquals(before.second,it.getString(0) to it.getString(1))}
            }
        } finally {context.deleteDatabase(name);key.fill(0)}
    }

    @Test fun failedTenToElevenMigrationRollsBackDdlAndKeepsOriginalEncryptedDatabaseReadable() {
        val name="synthetic-reference-failed-migration-${UUID.randomUUID()}.db";val key=ByteArray(32){63};var invoked=false
        try {
            fixture(name,key,10).use {it.writableDatabase.execSQL("INSERT INTO day_note(date,text) VALUES('2026-10-16','SYNTHETIC intact Room10')")}
            val injected=object:androidx.room.migration.Migration(10,11) {
                override fun migrate(db:SupportSQLiteDatabase) {invoked=true;ReferenceSchema.migrate(db);error("SYNTHETIC reference migration interrupted")}
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(injected,migration11To12,migration12To13,migration13To14,migration14To15,migration15To16,migration16To17,migration17To18,migration18To19,migration19To20,migration20To21,migration21To22,migration22To23).build().useRoom {room->
                try {room.openHelper.writableDatabase;fail("Interrupted migration succeeded")}catch(e:IllegalStateException){assertTrue(e.message.orEmpty().contains("SYNTHETIC reference migration interrupted"))}
            }
            assertTrue(invoked)
            fixture(name,key,10).use {helper->
                val raw=helper.writableDatabase;assertEquals(10,raw.version)
                raw.query("SELECT text FROM day_note").use {assertTrue(it.moveToFirst());assertEquals("SYNTHETIC intact Room10",it.getString(0))}
                raw.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN (${(ReferenceSchema.businessTables+"reference_runtime").joinToString(",") {"'"+it+"'"}})").use {assertEquals(0,it.count)}
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone())).addMigrations(*allMigrations()).addCallback(SchemaGuards).build().useRoom {assertEquals(23,it.openHelper.writableDatabase.version)}
        } finally {context.deleteDatabase(name);key.fill(0)}
    }
    @Test fun monaSeventeenToEighteenIsAdditiveAndFailedUpgradeRollsBack() {
        val name="synthetic-mona-migration-${UUID.randomUUID()}.db";val key=ByteArray(32){91}
        try {
            val before=fixture(name,key,17).use {h->
                h.writableDatabase.execSQL("INSERT INTO day_note(date,text) VALUES('2026-10-25','SYNTHETIC unchanged before Mona')")
                PublishedBusinessFixture.dump(h.writableDatabase).toString()
            }
            var reached=false
            val fault=object:androidx.room.migration.Migration(17,18) {
                override fun migrate(db:SupportSQLiteDatabase) {migration17To18.migrate(db);reached=true;error("synthetic Mona migration interrupted")}
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone()))
                .addMigrations(fault,migration18To19,migration19To20,migration20To21,migration21To22,migration22To23).build().useRoom {room->
                    try {room.openHelper.writableDatabase;fail("Interrupted upgrade accepted")}
                    catch(e:Exception){assertTrue(generateSequence<Throwable>(e){it.cause}.any{it.message.orEmpty().contains("synthetic Mona migration interrupted")})}
                }
            assertTrue(reached)
            fixture(name,key,17).use {h->
                assertEquals(17,h.writableDatabase.version)
                assertEquals(before,PublishedBusinessFixture.dump(h.writableDatabase).toString())
                h.writableDatabase.query("SELECT name FROM sqlite_master WHERE name='external_import_item'").use{assertFalse(it.moveToFirst())}
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone()))
                .addMigrations(*allMigrations()).addCallback(SchemaGuards).build().useRoom {room->
                    val raw=room.openHelper.writableDatabase;assertEquals(23,raw.version)
                    assertEquals(before,PublishedBusinessFixture.dump(raw).toString())
                    raw.query("SELECT COUNT(*) FROM external_import_item").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
                    raw.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
                }
            fixture(name,key,17).use {h->try{h.writableDatabase;fail("Room17 reader accepted Room18")}catch(_:android.database.SQLException){}}
        } finally {context.deleteDatabase(name);key.fill(0)}
    }
    @Test fun featherlineEighteenToNineteenIsAdditiveAndFailedUpgradeRollsBack() {
        val name="synthetic-featherline-migration-${UUID.randomUUID()}.db";val key=ByteArray(32){91}
        try {
            val before=fixture(name,key,18).use {h->
                h.writableDatabase.execSQL("INSERT INTO day_note(date,text) VALUES('2026-10-25','SYNTHETIC unchanged before Featherline')")
                PublishedBusinessFixture.dump(h.writableDatabase).toString()
            }
            var reached=false
            val fault=object:androidx.room.migration.Migration(18,19) {
                override fun migrate(db:SupportSQLiteDatabase) {migration18To19.migrate(db);reached=true;error("synthetic Featherline migration interrupted")}
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone()))
                .addMigrations(fault,migration19To20,migration20To21,migration21To22,migration22To23).build().useRoom {room->
                    try {room.openHelper.writableDatabase;fail("Interrupted upgrade accepted")}
                    catch(e:Exception){assertTrue(generateSequence<Throwable>(e){it.cause}.any{it.message.orEmpty().contains("synthetic Featherline migration interrupted")})}
                }
            assertTrue(reached)
            fixture(name,key,18).use {h->
                assertEquals(18,h.writableDatabase.version)
                assertEquals(before,PublishedBusinessFixture.dump(h.writableDatabase).toString())
                h.writableDatabase.query("SELECT name FROM sqlite_master WHERE name='featherline_import_item'").use{assertFalse(it.moveToFirst())}
            }
            Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone()))
                .addMigrations(*allMigrations()).addCallback(SchemaGuards).build().useRoom {room->
                    val raw=room.openHelper.writableDatabase;assertEquals(23,raw.version)
                    assertEquals(before,PublishedBusinessFixture.dump(raw).toString())
                    raw.query("SELECT COUNT(*) FROM featherline_import_item").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
                    raw.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
                }
            fixture(name,key,19).use {h->try{h.writableDatabase;fail("Room19 reader accepted Room20")}catch(_:android.database.SQLException){}}
        } finally {context.deleteDatabase(name);key.fill(0)}
    }

}

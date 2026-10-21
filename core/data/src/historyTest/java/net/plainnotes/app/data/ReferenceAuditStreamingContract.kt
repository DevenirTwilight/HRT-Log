package net.plainnotes.app.data

import android.content.Context
import android.database.MatrixCursor
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Exact synthetic reference-row and frozen pre-refactor hash contracts, shared by JVM and SQLCipher. */
abstract class ReferenceAuditStreamingContract {
    protected abstract fun factory(key:ByteArray):SupportSQLiteOpenHelper.Factory
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private lateinit var name:String
    private lateinit var db:NotesDatabase
    private val raw get()=db.openHelper.writableDatabase
    @Before fun setup() {
        name="synthetic-reference-stream-${UUID.randomUUID()}.db"
        db=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(ByteArray(32){(it+17).toByte()}))
            .allowMainThreadQueries().addCallback(SchemaGuards).build()
        raw
    }
    @After fun cleanup(){db.close();context.deleteDatabase(name)}
    /** Frozen function from99e536fe: do not redirect this oracle to the candidate helper. */
    private fun beforeCaptureHash(capture:JSONObject):String {
        val facts=JSONObject(capture.toString()).apply{remove("payload_sha256")}
        return ReferencePayload.sha(ReferencePayload.canonical(facts))
    }
    private fun compareCapture(row:JSONObject) {
        val source=row.toString()
        val before=runCatching{beforeCaptureHash(row)}
        val current=runCatching{ReferenceStore.captureHash(row)}
        assertEquals("Accepted set differs: $source",before.isSuccess,current.isSuccess)
        if(before.isSuccess)assertEquals(before.getOrThrow(),current.getOrThrow())
        assertEquals("Hash must not mutate caller",source,row.toString())
    }
    @Test fun primitiveCaptureHashPreservesFrozenBytesAndRejections() {
        val id="00000000-0000-4000-8000-000000000001"
        val base=JSONObject().put("uuid",id).put("dataset_uuid",id).put("record_uuid",id)
            .put("record_revision",1).put("capture_revision",1).put("kind","CONTEXT")
            .put("context_event_uuid",JSONObject.NULL).put("actual_snapshot_uuid",JSONObject.NULL).put("container_uuid",JSONObject.NULL)
        compareCapture(base)
        compareCapture(JSONObject(base.toString()).put("payload_sha256","ignored"))
        for(value in listOf(Int.MIN_VALUE.toLong(),-1L,0L,1L,Int.MAX_VALUE.toLong(),Int.MAX_VALUE.toLong()+1,Long.MIN_VALUE,Long.MAX_VALUE)) {
            compareCapture(JSONObject(base.toString()).put("record_revision",value))
        }
        for(value in listOf("","SYNTHETIC œ µg 片 🧪","SYNTHETIC \\\"\b\t\n\r\u000c\u0001 / ","a".repeat(2048),"a".repeat(2049),"\u0000","\ud800","\udc00")) {
            compareCapture(JSONObject(base.toString()).put("extra",value))
        }
        // Extra/missing primitive fields retain the original pure-hash behavior; domain validation is separate.
        compareCapture(JSONObject().put("payload_sha256",JSONObject.NULL))
        compareCapture(JSONObject().put("extra",Long.MAX_VALUE))
    }
    @Test fun nonPrimitiveCaptureHashRetainsFrozenRoundTripFallback() {
        for(value in listOf<Any>(true,false,1.0,1.25,-0.0,JSONArray().put(1).put("SYNTHETIC"),
            JSONObject().put("nested",1.0),JSONObject().put("nested",JSONObject.NULL))) {
            compareCapture(JSONObject().put("value",value).put("payload_sha256","ignored"))
            compareCapture(JSONObject().put("exact",1).put("payload_sha256",value))
        }
    }
    @Test fun positionedDecoderPreservesTypesAndReturnsIndependentObjects() {
        MatrixCursor(arrayOf("text","integer","missing"),2).use {cursor->
            cursor.addRow(arrayOf("SYNTHETIC 片 🧪",Long.MAX_VALUE,null))
            cursor.addRow(arrayOf("SYNTHETIC second",Long.MIN_VALUE,null))
            assertTrue(cursor.moveToFirst())
            val first=ReferenceStore.row(cursor)
            assertEquals("SYNTHETIC 片 🧪",first.getString("text"));assertEquals(Long.MAX_VALUE,first.getLong("integer"));assertTrue(first.isNull("missing"))
            first.put("text","MUTATED")
            assertEquals("SYNTHETIC 片 🧪",ReferenceStore.row(cursor).getString("text"))
            assertTrue(cursor.moveToNext());assertEquals(Long.MIN_VALUE,ReferenceStore.row(cursor).getLong("integer"))
        }
        for(value in listOf<Any>(1.25,byteArrayOf(1,2)))MatrixCursor(arrayOf("invalid"),1).use {cursor->
            cursor.addRow(arrayOf(value));assertTrue(cursor.moveToFirst())
            assertTrue(runCatching{ReferenceStore.row(cursor)}.isFailure)
        }
    }
    @Test fun referenceScansDoNotRereadTheirOwnPrimaryKeysAndStillReadPredecessors()=runBlocking {
        db.withTransaction {
            val med=db.dao().insertMedication(MedicationEntity(name="SYNTHETIC reference audit",molecule="OTHER",unit="MG",dose_per_intake=1.0,
                container_capacity=28.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
            val scope=ReferenceStore.scope(raw)
            val product=UserReferenceProduct("SYNTHETIC source",sourceVersion="synthetic-v1",recordedOn="2026-10-21")
            val previous=UUID.randomUUID().toString()
            ReferenceStore.associate(raw,scope,med,0,previous,product)
            ReferenceStore.associate(raw,scope,med,1,UUID.randomUUID().toString(),product.copy(name="SYNTHETIC corrected"))
            val record=RecordEntity(medication_id=med,taken_utc=1000,taken_zone="UTC",actual_dose=1.0,status="ON_TIME",origin="APP",revision=1,config_snapshot="{}")
            ReferenceStore.captureContext(raw,record.copy(id=db.dao().record(record)))
            val calls=ArrayList<Pair<String,List<Any?>>>()
            val observed=object:SupportSQLiteDatabase by raw {
                override fun query(sql:String)=raw.query(sql).also{calls+=sql to emptyList()}
                override fun query(sql:String,bindArgs:Array<out Any?>)=raw.query(sql,bindArgs).also{calls+=sql to bindArgs.toList()}
            }
            ReferenceSchema.validateCandidateReferences(observed)
            val eventReads=calls.filter{it.first=="SELECT * FROM `reference_event` WHERE operation_uuid=?"}
            assertEquals("Only the actual predecessor dependency is reread",listOf(listOf(previous)),eventReads.map{it.second})
            assertFalse("Capture scan must decode its positioned row",calls.any{it.first=="SELECT * FROM `record_reference_capture` WHERE uuid=?"})
            assertEquals(2,calls.count{it.first=="SELECT * FROM `reference_snapshot` WHERE uuid=?"})
            assertEquals(1,raw.query("SELECT COUNT(*) FROM record_reference_capture").use{it.moveToFirst();it.getInt(0)})
            // A raw malformed capture still fails when read directly from the scan, then rolls back.
            raw.execSQL("SAVEPOINT synthetic_bad_capture")
            try {
                raw.execSQL("DROP TRIGGER reference_immutable_record_reference_capture_update")
                raw.execSQL("UPDATE record_reference_capture SET payload_sha256=?",arrayOf("0".repeat(64)))
                assertTrue(runCatching{ReferenceSchema.validateCandidateReferences(observed)}.isFailure)
            } finally{raw.execSQL("ROLLBACK TO synthetic_bad_capture");raw.execSQL("RELEASE synthetic_bad_capture")}
            ReferenceSchema.validateCandidateReferences(observed)
        }
    }
}

package net.plainnotes.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.time.LocalDate

/** Fixed synthetic time reproduces the real CI constraint failure without sleeps or changing old assertions. */
abstract class DailyTrashCollisionContract {
    protected abstract fun factory(key:ByteArray):androidx.sqlite.db.SupportSQLiteOpenHelper.Factory
    protected open fun encrypted(name:String) {}
    protected val context:Context get()=ApplicationProvider.getApplicationContext()
    private lateinit var name:String
    private val key=ByteArray(32){(it+67).toByte()}
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private val day=LocalDate.of(2026,6,10)
    private val now=Instant.parse("2026-06-10T12:00:00Z")
    private val raw get()=db.openHelper.writableDatabase
    @Before fun open() {
        name="synthetic-daily-trash-${java.util.UUID.randomUUID()}.db"
        db=Room.databaseBuilder(context,NotesDatabase::class.java,name).openHelperFactory(factory(key.clone()))
            .addMigrations(*allMigrations()).allowMainThreadQueries().addCallback(SchemaGuards).build()
        raw;encrypted(name)
        repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
    }
    @After fun close(){db.close();context.deleteDatabase(name)}
    @Test fun sameMillisecondScoreSwapPreservesBothValuesThroughBackup()=runBlocking {
        val item=repo.checkinItems().first().id
        repo.setScore(day,item,2,now);repo.setScore(day,item,null,now)
        val original=repo.trash().single()
        repo.setScore(day,item,5,now);repo.restoreTrash(original.id,now)
        assertEquals(2,db.dao().scores(day.toString(),day.toString()).single().value)
        val displaced=repo.trash().single()
        assertNotEquals(original.ref,displaced.ref)
        assertEquals(now.toEpochMilli(),displaced.deleted_utc)
        assertEquals(5,JSONObject(displaced.payload_json).getJSONObject("tables").getJSONArray("checkin_score").getJSONObject(0).getInt("value"))
        val password="SYNTHETIC-backup-password".toCharArray()
        val archive=repo.exportBackup(password)
        try{repo.restoreBackup(archive,password)}finally{password.fill('\u0000');archive.fill(0)}
        assertEquals(listOf(displaced),repo.trash())
        repo.restoreTrash(displaced.id,now)
        assertEquals(5,db.dao().scores(day.toString(),day.toString()).single().value)
        assertEquals(2,JSONObject(repo.trash().single().payload_json).getJSONObject("tables").getJSONArray("checkin_score").getJSONObject(0).getInt("value"))
    }
    @Test fun sameMillisecondNoteSwapPreservesBothTexts()=runBlocking {
        repo.setNote(day,"SYNTHETIC first",now);repo.setNote(day,"",now)
        val original=repo.trash().single()
        repo.setNote(day,"SYNTHETIC second",now);repo.restoreTrash(original.id,now)
        assertEquals("SYNTHETIC first",repo.notes(day,day).single().text)
        val displaced=repo.trash().single()
        assertNotEquals(original.ref,displaced.ref)
        assertEquals(now.toEpochMilli(),displaced.deleted_utc)
        repo.restoreTrash(displaced.id,now)
        assertEquals("SYNTHETIC second",repo.notes(day,day).single().text)
        assertEquals("SYNTHETIC first",JSONObject(repo.trash().single().payload_json).getJSONObject("tables").getJSONArray("day_note").getJSONObject(0).getString("text"))
    }
    @Test fun collisionChangesOnlyNewDailySnapshotIdentityAndKeepsOtherKindsUnique()=runBlocking {
        // Low-level index fixtures only; these marker payloads are not claimed to be valid backups.
        for(kind in listOf(Trash.SCORE,Trash.NOTE,Trash.SYMPTOM)) {
            val ref="SYNTHETIC-$kind@${now.toEpochMilli()}"
            db.withTransaction {
                Trash.insert(raw,kind,ref,day.toString(),JSONObject().put("synthetic",1),now.toEpochMilli())
                Trash.insert(raw,kind,ref,day.toString(),JSONObject().put("synthetic",2),now.toEpochMilli())
            }
            raw.query("SELECT ref,deleted_utc,payload_json FROM trash_item WHERE kind=? ORDER BY id",arrayOf(kind)).use { c ->
                assertEquals(2,c.count);assertTrue(c.moveToFirst());assertEquals(ref,c.getString(0))
                assertEquals(1,JSONObject(c.getString(2)).getInt("synthetic"));assertEquals(now.toEpochMilli(),c.getLong(1))
                assertTrue(c.moveToNext());assertNotEquals(ref,c.getString(0))
                assertEquals(2,JSONObject(c.getString(2)).getInt("synthetic"));assertEquals(now.toEpochMilli(),c.getLong(1))
            }
        }
        Trash.insert(raw,Trash.PERIOD,"SYNTHETIC-stable-reference",day.toString(),JSONObject(),now.toEpochMilli())
        try {
            db.withTransaction{Trash.insert(raw,Trash.PERIOD,"SYNTHETIC-stable-reference",day.toString(),JSONObject(),now.toEpochMilli())}
            fail("Reference-backed kinds must still reject duplicate identity")
        } catch(_:SQLiteConstraintException){}
        raw.query("SELECT COUNT(*) FROM trash_item WHERE kind='PERIOD'").use{assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0))}
    }
    @Test fun failedReinsertRollsBackSwapIncludingNewTrashEntry()=runBlocking {
        val item=repo.checkinItems().first().id
        repo.setScore(day,item,2,now);repo.setScore(day,item,null,now)
        val original=repo.trash().single();repo.setScore(day,item,5,now)
        val before=RawData.dump(raw).toString()
        raw.execSQL("CREATE TRIGGER synthetic_restore_failure BEFORE INSERT ON checkin_score BEGIN SELECT RAISE(ABORT,'SYNTHETIC_RESTORE_FAILURE'); END")
        try {
            try{repo.restoreTrash(original.id,now);fail("Synthetic failure was not reached")}
            catch(e:SQLiteConstraintException){assertTrue("Must reach injected reinsert failure, not fail earlier on identity",e.message.orEmpty().contains("SYNTHETIC_RESTORE_FAILURE"))}
        } finally{raw.execSQL("DROP TRIGGER synthetic_restore_failure")}
        assertEquals(before,RawData.dump(raw).toString())
        assertEquals(5,db.dao().scores(day.toString(),day.toString()).single().value)
        assertEquals(listOf(original),repo.trash())
    }
}

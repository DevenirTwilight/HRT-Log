package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.RuleKind
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class TestosteroneSnapshotBackupTest {
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    @Before fun open() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo=NotesRepository(object: DatabaseAccess(c) { override fun get(space:Space)=db })
    }
    @After fun close()=db.close()
    @Test fun productSelectionAndActualFrozenMassSurviveEncryptedBackupWithoutRewritingOldUnknowns()=runBlocking {
        val now=Instant.parse("2026-10-30T12:00:00Z")
        val m=MedicationEntity(name="SYNTHETIC T",molecule="T",route=null,unit="MG",dose_per_intake=50.0,container_capacity=1500.0,soon_alert_minutes=15,late_after_minutes=120,
            site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val id=repo.saveMedication(m,null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(20,0)),emptySet(),now.minusSeconds(86400))
        val old=RecordEntity(medication_id=id,taken_utc=now.minusSeconds(3600).toEpochMilli(),taken_zone="UTC",actual_dose=50.0,
            status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(m,null))
        val oldId=db.dao().record(old)
        val selected=m.copy(id=id,route="T_ANDROGEL_50")
        repo.saveMedication(selected,null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(20,0)),emptySet(),now)
        val newId=db.dao().record(old.copy(config_snapshot=MedicationSnapshot.encode(selected,null),taken_utc=now.toEpochMilli()))
        val before=repo.records().associateBy { it.id }
        val password="synthetic-t-history-backup".toCharArray()
        val archive=repo.exportBackup(password)
        repo.restoreBackup(archive,password)
        val after=repo.records().associateBy { it.id }
        assertEquals(before,after)
        assertNull(MedicationSnapshot.decode(after.getValue(oldId).config_snapshot,id)!!.route)
        assertEquals("T_ANDROGEL_50",MedicationSnapshot.decode(after.getValue(newId).config_snapshot,id)!!.route)
        assertEquals("T_ANDROGEL_50",db.dao().medication(id).route)
        assertEquals(50.0,after.getValue(newId).actual_dose!!,0.0)
    }
}

package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class SchemaConstraintTest {
    private lateinit var db:NotesDatabase
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();db.openHelper.writableDatabase}
    @After fun close(){db.close()}
    private fun medication()=MedicationEntity(name="Synthetic A",molecule="E2",route="ORAL",unit="MG",dose_per_intake=1.0,container_capacity=10.0,soon_alert_minutes=15,late_after_minutes=120,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    private fun seed():Long=runBlocking {
        val id=db.dao().insertMedication(medication())
        db.dao().rule(RuleEntity(medication_id=id,kind="EVERY_N_DAYS",interval=1,weekday_mask=0,anchor_local="2026-01-01",anchor_zone="UTC",effective_from_utc=0,effective_zone="UTC",missed_tracking_from_utc=0,dose_snapshot=1.0,soon_snapshot=15,late_snapshot=120,config_snapshot="{}"))
        id
    }
    private fun reject(block:()->Unit){try{block();fail("Expected constraint rejection")}catch(e:Exception){assertNotNull(e)}}
    private fun sql(s:String)=db.openHelper.writableDatabase.execSQL(s)
    @Test fun missedHasNoActualValues(){val id=seed();reject{runBlocking{db.dao().record(RecordEntity(medication_id=id,status="MISSED",actual_dose=1.0,origin="APP",revision=1,config_snapshot="{}"))}}}
    @Test fun takenNeedsZoneAndPositiveDose(){val id=seed();reject{runBlocking{db.dao().record(RecordEntity(medication_id=id,status="ON_TIME",taken_utc=0,taken_zone="UTC",actual_dose=0.0,origin="APP",revision=1,config_snapshot="{}"))}}}
    @Test fun unknownImportActualRemainsNull(){val id=seed();runBlocking{db.dao().record(RecordEntity(medication_id=id,status="ON_TIME",taken_utc=0,taken_zone="UTC",origin="IMPORT_TM",revision=1,config_snapshot="{}"));assertNull(db.dao().records().single().actual_dose)}}
    @Test fun overridesComposeButEmptyAndUnpairedAreRejected(){val id=seed();runBlocking {
        db.dao().insertOverride(OverrideEntity(medication_id=id,rule_version_id=1,slot_key="wall:1@2026-01-01T12:00:00",rescheduled_utc=1000,rescheduled_zone="UTC",dose_override=2.0,skipped=true))
        assertTrue(db.dao().overrides().single().skipped)
    };reject{runBlocking{db.dao().insertOverride(OverrideEntity(medication_id=id,rule_version_id=1,slot_key="empty",skipped=false))}}
        reject{runBlocking{db.dao().insertOverride(OverrideEntity(medication_id=id,rule_version_id=1,slot_key="unpaired",rescheduled_utc=1000,skipped=false))}}}
    @Test fun overlappingVersionsRejectedAndHalfOpenAccepted(){seed();val r=runBlocking{db.dao().rules().single()};reject{runBlocking{db.dao().rule(r.copy(id=0,effective_from_utc=100))}}
        runBlocking{db.withTransaction { db.dao().updateRule(r.copy(effective_until_utc=100));db.dao().rule(r.copy(id=0,effective_from_utc=100,missed_tracking_from_utc=100)) }}
        assertEquals(2,runBlocking{db.dao().rules().size})
    }
    @Test fun activeSlotUniqueButSoftDeletedHistoryAllowed(){val id=seed();val r=RecordEntity(medication_id=id,rule_version_id=1,slot_key="wall:1@2026-01-01T12:00:00",scheduled_utc=1,scheduled_zone="UTC",status="MISSED",origin="AUTO_MISSED",revision=1,config_snapshot="{}")
        runBlocking{db.dao().record(r)};reject{runBlocking{db.dao().record(r)}}
        runBlocking{db.dao().updateRecord(db.dao().records().single().copy(deleted_at_utc=2));db.dao().record(r)}
    }
    @Test fun foreignKeysRejectDanglingOverride(){seed();reject{runBlocking{db.dao().insertOverride(OverrideEntity(medication_id=1,rule_version_id=99,slot_key="x",skipped=true))}}}
    @Test fun ledgerSpansContainersAndIsAppendOnly(){val id=seed();val record=runBlocking{db.dao().record(RecordEntity(medication_id=id,status="ON_TIME",taken_utc=1,taken_zone="UTC",actual_dose=2.0,origin="APP",revision=1,config_snapshot="{}"))}
        sql("INSERT INTO supply_container(id,medication_id,capacity,initial_used_amount,used_amount,state) VALUES(1,$id,1,0,0,'IN_USE'),(2,$id,10,0,0,'IN_USE')")
        for(c in 1..2)sql("INSERT INTO supply_transaction(id,container_id,dose_record_id,dose_revision,operation_id,kind,used_delta,created_utc,created_zone) VALUES($c,$c,$record,1,'op','CONSUME',1,1,'UTC')")
        reject{sql("UPDATE supply_transaction SET used_delta=0 WHERE id=1")};reject{sql("DELETE FROM supply_transaction WHERE id=1")}
        sql("INSERT INTO supply_transaction(id,container_id,dose_record_id,dose_revision,operation_id,kind,used_delta,reversal_of_id,created_utc,created_zone) VALUES(3,1,$record,1,'undo','REVERSE',-1,1,2,'UTC')")
        db.openHelper.writableDatabase.query("SELECT used_amount FROM supply_container WHERE id=1").use{it.moveToFirst();assertEquals(0.0,it.getDouble(0),0.0)}
        reject{sql("INSERT INTO supply_transaction(container_id,dose_record_id,dose_revision,operation_id,kind,used_delta,reversal_of_id,created_utc,created_zone) VALUES(1,$record,1,'undo2','REVERSE',-1,1,2,'UTC')")}
    }
    @Test fun failedVersionSwitchRollsBack(){seed();val r=runBlocking{db.dao().rules().single()}
        reject{runBlocking{db.withTransaction{db.dao().updateRule(r.copy(effective_until_utc=100));db.dao().rule(r.copy(id=0,interval=0,effective_from_utc=100,missed_tracking_from_utc=100))}}}
        assertNull(runBlocking{db.dao().rules().single().effective_until_utc})
    }
    @Test fun labBoundsArePerSampleAndInvalidOrderingRejected(){sql("INSERT INTO lab_analyte VALUES('E2','pg/mL')")
        sql("INSERT INTO lab_value(id,analyte_code,value,unit,sampled_utc,sampled_zone,reference_lower,reference_upper,reference_unit) VALUES(1,'E2',10,'pg/mL',1,'UTC',1,20,'pg/mL'),(2,'E2',10,'pg/mL',2,'UTC',2,30,'pg/mL')")
        reject{sql("UPDATE lab_value SET reference_lower=100 WHERE id=1")}
    }
}

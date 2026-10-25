package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class MonaSchemaTest {
    private lateinit var db:NotesDatabase
    @Before fun open() {db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),NotesDatabase::class.java)
        .allowMainThreadQueries().addCallback(SchemaGuards).build();db.openHelper.writableDatabase}
    @After fun close(){db.close()}
    @Test fun distinctSourceOriginDoesNotImpersonateTheExistingImporters()=runBlocking {
        val id=db.dao().insertMedication(MedicationEntity(name="Synthetic source",molecule="E2",route="ORAL",unit="MG",dose_per_intake=2.0,
            container_capacity=2.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
        db.dao().record(RecordEntity(medication_id=id,taken_utc=1000,taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="IMPORT_MONA",
            source_record_key="mona:intake:1",revision=1,config_snapshot="{}"))
        assertEquals("IMPORT_MONA",db.dao().records().single().origin)
    }
    @Test fun immutableSourceReceiptsHaveDedicatedStorage() {
        db.openHelper.writableDatabase.query("SELECT source_app,source_kind,source_id,source_part,source_digest,source_json,retention FROM external_import_item").use {
            assertFalse(it.moveToFirst())
        }
    }
}

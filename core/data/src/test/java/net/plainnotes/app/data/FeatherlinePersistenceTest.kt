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
class FeatherlinePersistenceTest {
    private lateinit var db:NotesDatabase
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();db.openHelper.writableDatabase}
    @After fun close(){db.close()}
    @Test fun uuidReceiptsHaveTheirOwnTable(){db.openHelper.writableDatabase.query("SELECT source_id,source_panel_uuid,source_digest,record_id,lab_id,retention FROM featherline_import_item").use{assertFalse(it.moveToFirst())}}
    @Test fun clinicalOriginDoesNotImpersonateMona()=runBlocking {
        val id=db.dao().insertMedication(MedicationEntity(name="SYNTHETIC Featherline",molecule="E2",route="ORAL",unit="MG",dose_per_intake=2.0,container_capacity=2.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
        db.dao().record(RecordEntity(medication_id=id,taken_utc=1000,taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="IMPORT_FEATHERLINE",source_record_key="featherline:log:00112233-4455-6677-8899-aabbccddeeff",revision=1,config_snapshot="{}"))
        assertEquals("IMPORT_FEATHERLINE",db.dao().records().single().origin)
    }
}

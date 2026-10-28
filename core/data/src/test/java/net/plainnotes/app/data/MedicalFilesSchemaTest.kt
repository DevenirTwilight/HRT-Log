package net.plainnotes.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MedicalFilesSchemaTest {
    @Test fun persistedPanelHasItsOwnIdentityEvenWhenAnUnrelatedResultHasTheSameInteger()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),NotesDatabase::class.java)
            .allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {db.withTransaction {
            val raw=db.openHelper.writableDatabase
            val scope=requireNotNull(AttachmentIdentity.initialize(raw,true))
            val panel=UUID.randomUUID().toString();val result=UUID.randomUUID().toString()
            raw.execSQL("INSERT INTO lab_panel(id,uuid,sampled_utc,sampled_zone,created_utc) VALUES(17,?,1000,'UTC',1000)",arrayOf(panel))
            raw.execSQL("INSERT INTO lab_analyte(code,canonical_unit) VALUES('E2','pg/mL')")
            raw.execSQL("INSERT INTO lab_value(id,analyte_code,value,unit,sampled_utc,sampled_zone) VALUES(17,'E2',120,'pg/mL',2000,'UTC')")
            fun bind(kind:String,uuid:String)=raw.execSQL("INSERT INTO attachment_record_identity(store_uuid,record_epoch,record_uuid,target_kind,legacy_id,state,evidence_digest) VALUES(?,?,?,?,17,'ACTIVE',?)",arrayOf(scope.dataset,scope.epoch,uuid,kind,ByteArray(32)))
            bind("LAB",result)
            bind("LAB_PANEL",panel)
            raw.query("SELECT target_kind,record_uuid FROM attachment_record_identity ORDER BY target_kind").use {
                assertTrue(it.moveToFirst());assertEquals("LAB",it.getString(0));assertEquals(result,it.getString(1))
                assertTrue(it.moveToNext());assertEquals("LAB_PANEL",it.getString(0));assertEquals(panel,it.getString(1));assertFalse(it.moveToNext())
            }
        }}finally {db.close()}
    }
}

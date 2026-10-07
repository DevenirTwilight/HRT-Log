package net.plainnotes.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

/** Exercise the exported v1 schema and actual Android SQLite before installing v2 triggers. */
class MigrationBaselineTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),NotesDatabase::class.java)
    @Test fun exportedV1MigratesWithoutChangingHistoricalScores(){
        helper.createDatabase("migration-baseline",1).use{db->
            db.execSQL("INSERT INTO checkin_item (id,builtin_key,custom_label,enabled,sort_order) VALUES (1,'MOOD',NULL,1,0),(2,'PAIN',NULL,1,1),(3,'APPETITE',NULL,1,2)")
            db.execSQL("INSERT INTO checkin_score (date,item_id,value) VALUES ('2026-02-10',1,4),('2026-02-09',2,2),('2025-01-01',3,3)")
        }
        helper.runMigrationsAndValidate("migration-baseline",2,true,migration1To2{LocalDate.of(2026,2,10)}).use{db->
            SchemaGuards.install(db)
            db.query("SELECT builtin_key FROM checkin_item WHERE id=1").use{assertTrue(it.moveToFirst());assertEquals("DAY_MOOD",it.getString(0))}
            db.query("SELECT value FROM checkin_score WHERE item_id=1").use{assertTrue(it.moveToFirst());assertEquals(4,it.getInt(0))}
            db.query("SELECT enabled FROM checkin_item WHERE id=2").use{assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0))}
            db.query("SELECT enabled FROM checkin_item WHERE id=3").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
            db.execSQL("INSERT INTO stage_review (date,effects_json,weight_kg) VALUES ('2026-02-10','{}',60)")
            db.query("SELECT weight_kg FROM stage_review").use{assertTrue(it.moveToFirst());assertEquals(60.0,it.getDouble(0),0.0)}
        }
    }
}

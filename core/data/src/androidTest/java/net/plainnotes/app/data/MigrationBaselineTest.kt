package net.plainnotes.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

/** Exercise the exported v1 schema and actual Android SQLite before installing current triggers. */
class MigrationBaselineTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),NotesDatabase::class.java)
    @Test fun exportedV1MigratesWithoutChangingHistoricalScores(){
        helper.createDatabase("migration-baseline",1).use{db->
            db.execSQL("INSERT INTO checkin_item (id,builtin_key,custom_label,enabled,sort_order) VALUES (1,'MOOD',NULL,1,0),(2,'PAIN',NULL,1,1),(3,'APPETITE',NULL,1,2)")
            db.execSQL("INSERT INTO checkin_score (date,item_id,value) VALUES ('2026-02-10',1,4),('2026-02-09',2,2),('2025-01-01',3,3)")
        }
        helper.runMigrationsAndValidate("migration-baseline",7,true,*allMigrations{LocalDate.of(2026,2,10)}).use{db->
            SchemaGuards.install(db)
            db.query("SELECT builtin_key FROM checkin_item WHERE id=1").use{assertTrue(it.moveToFirst());assertEquals("DAY_MOOD",it.getString(0))}
            db.query("SELECT value FROM checkin_score WHERE item_id=1").use{assertTrue(it.moveToFirst());assertEquals(4,it.getInt(0))}
            db.query("SELECT enabled FROM checkin_item WHERE id=2").use{assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0))}
            db.query("SELECT enabled FROM checkin_item WHERE id=3").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
            db.execSQL("INSERT INTO stage_review (date,effects_json,weight_kg) VALUES ('2026-02-10','{}',60)")
            db.query("SELECT weight_kg FROM stage_review").use{assertTrue(it.moveToFirst());assertEquals(60.0,it.getDouble(0),0.0)}
        }
    }
    @Test fun exportedV3ReconstructsStoredInstructionsAndMergesReminderOnlyChanges(){
        helper.createDatabase("migration-regimens",3).use{db->
            db.execSQL("INSERT INTO medication (id,name,molecule,route,unit,dose_per_intake,container_capacity,site_rotation,notifications_on,active,sort_order) VALUES (1,'Synthetic current name','E2','ORAL','MG',3,40,0,1,1,0)")
            val snapshot="{\"name\":\"Synthetic historical name\",\"molecule\":\"E2\",\"route\":\"ORAL\",\"unit\":\"MG\",\"ester\":\"EV\"}"
            listOf(Triple(1L,1000L,2000L),Triple(2L,2000L,3000L),Triple(3L,3000L,null)).forEach{(id,from,until)->
                db.execSQL("INSERT INTO schedule_rule (id,medication_id,kind,interval,weekday_mask,anchor_local,anchor_zone,anchor_utc,effective_from_utc,effective_until_utc,effective_zone,missed_tracking_from_utc,dose_snapshot,soon_snapshot,late_snapshot,config_snapshot) VALUES (?,1,'EVERY_N_DAYS',1,0,'2026-01-01','UTC',NULL,?,?,'UTC',0,?,15,?,?)",arrayOf<Any?>(id,from,until,if(id==3L)3.0 else 2.0,if(id==2L)120 else 60,snapshot))
                db.execSQL("INSERT INTO rule_time (rule_id,local_time,dose_override) VALUES (?,'20:00:00',NULL)",arrayOf(id))
            }
        }
        helper.runMigrationsAndValidate("migration-regimens",7,true,migration3To4,migration4To5,migration5To6,migration6To7).use{db->
            SchemaGuards.install(db);RegimenHistory.validateLinks(db)
            db.query("SELECT definition_json,effective_from_utc,effective_until_utc,origin,recorded_at_utc FROM regimen_version ORDER BY effective_from_utc").use{c->
                assertEquals(2,c.count);assertTrue(c.moveToFirst());val frozen=RegimenDefinition.read(c.getString(0))
                assertEquals(2.0,frozen.dose,0.0);assertEquals("Synthetic historical name",frozen.snapshot(1)!!.name)
                assertEquals(1000L,c.getLong(1));assertEquals(3000L,c.getLong(2));assertEquals("LEGACY_RULE",c.getString(3));assertTrue(c.isNull(4))
                assertTrue(c.moveToNext());assertEquals(3.0,RegimenDefinition.read(c.getString(0)).dose,0.0)
            }
        }
    }

    @Test fun exportedV4KeepsLabWithoutInventingSamplingContext() {
        helper.createDatabase("migration-lab-context",4).use{db->
            db.execSQL("INSERT INTO lab_analyte(code,canonical_unit) VALUES ('E2','pg/mL')")
            db.execSQL("INSERT INTO lab_value(id,analyte_code,value,unit,sampled_utc,sampled_zone) VALUES (1,'E2',120,'pg/mL',1000,'UTC')")
        }
        helper.runMigrationsAndValidate("migration-lab-context",7,true,migration4To5,migration5To6,migration6To7).use{db->
            SchemaGuards.install(db)
            db.query("SELECT value,sampled_utc FROM lab_value WHERE id=1").use{assertTrue(it.moveToFirst());assertEquals(120.0,it.getDouble(0),0.0);assertEquals(1000L,it.getLong(1))}
            db.query("SELECT * FROM lab_context_revision").use{assertEquals(0,it.count)}
        }
    }

    @Test fun exportedV5KeepsAppointmentsUnconfirmedWithNoQuestionsOrPacks() {
        helper.createDatabase("migration-visits",5).use{db->
            db.execSQL("INSERT INTO appointment(id,type,at_utc,at_zone,location,practitioner,note,remind_minutes_before) VALUES (1,'ENDO',1000,'UTC','Synthetic place','Synthetic clinic','Synthetic note',60)")
        }
        helper.runMigrationsAndValidate("migration-visits",7,true,migration5To6,migration6To7).use{db->
            SchemaGuards.install(db)
            db.query("SELECT type,at_utc,practitioner,note,completed_utc FROM appointment WHERE id=1").use{assertTrue(it.moveToFirst());assertEquals("ENDO",it.getString(0));assertEquals(1000L,it.getLong(1))
                assertEquals("Synthetic clinic",it.getString(2));assertEquals("Synthetic note",it.getString(3));assertTrue(it.isNull(4))}
            db.query("SELECT * FROM visit_question").use{assertEquals(0,it.count)}
            db.query("SELECT * FROM visit_pack").use{assertEquals(0,it.count)}
            db.execSQL("INSERT INTO visit_pack(appointment_id,generated_utc,zone,range_from,range_to,sections,language,template_version,input_digest,facts_json) VALUES (1,2000,'UTC','2026-01-01','2026-02-01','FACTS','en',1,?,'{}')",arrayOf("0".repeat(64)))
            try{db.execSQL("UPDATE visit_pack SET range_from='2025-01-01'");fail()}catch(_:Exception){}
        }
    }
    @Test fun exportedV6KeepsRecordsAndStartsWithNothingConfirmed() {
        helper.createDatabase("migration-history-periods",6).use{db->
            db.execSQL("INSERT INTO medication (id,name,molecule,route,unit,dose_per_intake,container_capacity,site_rotation,notifications_on,active,sort_order) VALUES (1,'Synthetic','E2','SUBLINGUAL','MG',1,30,0,0,1,0)")
            db.execSQL("INSERT INTO dose_record (id,medication_id,taken_utc,taken_zone,actual_dose,status,origin,source_record_key,revision,config_snapshot) VALUES (1,1,1000,'UTC',1,'ON_TIME','IMPORT_HT','ht:synthetic:1',1,'{}')")
        }
        helper.runMigrationsAndValidate("migration-history-periods",7,true,migration6To7).use{db->
            SchemaGuards.install(db)
            db.query("SELECT actual_dose,origin,taken_utc FROM dose_record WHERE id=1").use{assertTrue(it.moveToFirst());assertEquals(1.0,it.getDouble(0),0.0);assertEquals("IMPORT_HT",it.getString(1));assertEquals(1000L,it.getLong(2))}
            db.query("SELECT * FROM history_period_revision").use{assertEquals(0,it.count)}
            db.query("SELECT * FROM record_annotation").use{assertEquals(0,it.count)}
        }
    }
}

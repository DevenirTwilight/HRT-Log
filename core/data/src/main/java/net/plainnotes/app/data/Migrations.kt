package net.plainnotes.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

/** Schema 1 -> 2: wellbeing redesign (REQUIREMENTS 15, 15b). Only adds columns and tables; then upgrades the wellbeing items. */
fun migration1To2(today: () -> LocalDate = LocalDate::now) = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `checkin_item` ADD COLUMN `legacy` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `supply_container` ADD COLUMN `source_note` TEXT")
        db.execSQL("ALTER TABLE `supply_container` ADD COLUMN `batch` TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS `stage_review` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `effects_json` TEXT NOT NULL, `tolerance_note` TEXT, `risk_note` TEXT, `smoking` TEXT, `systolic` INTEGER, `diastolic` INTEGER, `weight_kg` REAL, `satisfaction` INTEGER, `satisfaction_note` TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `symptom_check` (`date` TEXT NOT NULL, `group_id` TEXT NOT NULL, `note` TEXT, PRIMARY KEY(`date`, `group_id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `review_effect` (`effect_id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`effect_id`))")
        WellbeingUpgrade.apply(db, today())
    }
}

/** Every migration, in order; also used to know which backup schemas can be restored. */
val migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `symptom_check` ADD COLUMN `context_snapshot` TEXT")
    }
}
val migration3To4 = object : Migration(3, 4) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS regimen_version (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, medication_id INTEGER NOT NULL, effective_from_utc INTEGER NOT NULL, effective_until_utc INTEGER, zone TEXT NOT NULL, definition_json TEXT NOT NULL, clinical_signature TEXT NOT NULL, origin TEXT NOT NULL, recorded_at_utc INTEGER, FOREIGN KEY(medication_id) REFERENCES medication(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_regimen_version_medication_id ON regimen_version(medication_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS regimen_rule_link (rule_id INTEGER NOT NULL, regimen_id INTEGER NOT NULL, PRIMARY KEY(rule_id), FOREIGN KEY(rule_id) REFERENCES schedule_rule(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(regimen_id) REFERENCES regimen_version(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_regimen_rule_link_regimen_id ON regimen_rule_link(regimen_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS milestone (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, kind TEXT NOT NULL, title TEXT, note TEXT)")
        RegimenHistory.seed(db)
    }
}
val migration4To5 = object : Migration(4,5) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS lab_context_revision (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, lab_id INTEGER NOT NULL, revision INTEGER NOT NULL, captured_utc INTEGER NOT NULL, origin TEXT NOT NULL, context_json TEXT NOT NULL, FOREIGN KEY(lab_id) REFERENCES lab_value(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_lab_context_revision_lab_id_revision ON lab_context_revision(lab_id,revision)")
    }
}
val migration5To6 = object : Migration(5,6) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE appointment ADD COLUMN completed_utc INTEGER")
        db.execSQL("CREATE TABLE IF NOT EXISTS visit_question (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, appointment_id INTEGER NOT NULL, sort_order INTEGER NOT NULL, text TEXT NOT NULL, status TEXT NOT NULL, answer_note TEXT, FOREIGN KEY(appointment_id) REFERENCES appointment(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_visit_question_appointment_id ON visit_question(appointment_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS visit_pack (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, appointment_id INTEGER NOT NULL, generated_utc INTEGER NOT NULL, zone TEXT NOT NULL, range_from TEXT NOT NULL, range_to TEXT NOT NULL, sections TEXT NOT NULL, language TEXT NOT NULL, template_version INTEGER NOT NULL, input_digest TEXT NOT NULL, facts_json TEXT NOT NULL, FOREIGN KEY(appointment_id) REFERENCES appointment(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_visit_pack_appointment_id ON visit_pack(appointment_id)")
    }
}
val migration6To7 = object : Migration(6,7) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS history_period_revision (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, period_key TEXT NOT NULL, revision INTEGER NOT NULL, state TEXT NOT NULL, medication_id INTEGER NOT NULL, identity_json TEXT NOT NULL, standard_json TEXT NOT NULL, from_date TEXT NOT NULL, until_date TEXT, zone TEXT NOT NULL, evidence_json TEXT NOT NULL, origin TEXT NOT NULL, created_utc INTEGER NOT NULL, FOREIGN KEY(medication_id) REFERENCES medication(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_history_period_revision_period_key_revision ON history_period_revision(period_key,revision)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_history_period_revision_medication_id ON history_period_revision(medication_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS record_annotation (record_id INTEGER NOT NULL, kind TEXT NOT NULL, created_utc INTEGER NOT NULL, PRIMARY KEY(record_id, kind), FOREIGN KEY(record_id) REFERENCES dose_record(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
    }
}
/** REQUIREMENTS §37b: user edits of the timeline share the append-only period table. Existing rows are confirmed periods. */
val migration7To8 = object : Migration(7,8) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history_period_revision ADD COLUMN kind TEXT NOT NULL DEFAULT 'CONFIRMED'")
        db.execSQL("ALTER TABLE history_period_revision ADD COLUMN group_key TEXT")
    }
}
fun allMigrations(today: () -> LocalDate = LocalDate::now): Array<Migration> = arrayOf(migration1To2(today), migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8)

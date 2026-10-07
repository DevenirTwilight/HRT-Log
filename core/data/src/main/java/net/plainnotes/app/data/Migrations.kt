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
fun allMigrations(today: () -> LocalDate = LocalDate::now): Array<Migration> = arrayOf(migration1To2(today), migration2To3, migration3To4)

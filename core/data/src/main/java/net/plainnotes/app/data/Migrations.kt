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
fun allMigrations(today: () -> LocalDate = LocalDate::now): Array<Migration> = arrayOf(migration1To2(today), migration2To3)

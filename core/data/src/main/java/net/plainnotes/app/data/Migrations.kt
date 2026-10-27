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

/** Runtime migrations. PNBAK1 record serialization compatibility is separately frozen at 9. */
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
/** REQUIREMENTS §39: the recycle bin; existing soft-deleted records and build 23 deleted periods move into it. */
val migration8To9 = object : Migration(8,9) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS trash_item (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, ref TEXT NOT NULL, item_date TEXT NOT NULL, deleted_utc INTEGER NOT NULL, payload_json TEXT NOT NULL, state TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_trash_item_kind_ref ON trash_item(kind,ref)")
        Trash.backfill(db)
    }
}
val migration9To10 = object : Migration(9,10) {
    override fun migrate(db:SupportSQLiteDatabase) = AttachmentSchema.migrate(db)
}
val migration10To11 = object : Migration(10,11) {
    override fun migrate(db:SupportSQLiteDatabase) = ReferenceSchema.migrate(db)
}
/** LAB-02: lab panels (one blood draw). Adds a table and a nullable column only; existing results stay ungrouped (panel_id NULL). */
val migration11To12 = object : Migration(11,12) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `lab_panel` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `sampled_utc` INTEGER NOT NULL, `sampled_zone` TEXT NOT NULL, `laboratory` TEXT, `note` TEXT, `created_utc` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lab_panel_uuid` ON `lab_panel` (`uuid`)")
        db.execSQL("ALTER TABLE `lab_value` ADD COLUMN `panel_id` INTEGER REFERENCES `lab_panel`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lab_value_panel_id` ON `lab_value` (`panel_id`)")
    }
}
/** Database-format fence: Room12 readers must never interpret future integer stock quanta as scalar units.
 * Only an explicit LEGACY storage marker is added; no legacy amount is rewritten. Published Room12 supported product references.
 */
val migration12To13 = object : Migration(12,13) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT 1 FROM reference_snapshot WHERE kind='PRESENTATION' LIMIT 1").use {require(!it.moveToFirst()) {"Unrecognized measurement data in the old database format"}}
        db.query("SELECT 1 FROM measured_operation LIMIT 1").use {require(!it.moveToFirst()) {"Unrecognized measured operation in the old database format"}}
        ReferenceSchema.validate(db)
        db.execSQL("ALTER TABLE supply_container ADD COLUMN inventory_encoding TEXT NOT NULL DEFAULT 'LEGACY_SCALAR'")
    }
}
val migration13To14 = object : Migration(13,14) {
    override fun migrate(db:SupportSQLiteDatabase) = AttachmentJournalSchema.migrate(db)
}

/** Additive local authority, never a business backup schema or a destructive replacement. */
val migration14To15=object:Migration(14,15) {
    override fun migrate(db:SupportSQLiteDatabase)=LibraryGenerationSchema.migrate(db)
}

val migration15To16=object:Migration(15,16) { override fun migrate(db:SupportSQLiteDatabase)=LibraryLifecycleSchema.migrate(db) }

val migration16To17=object:Migration(16,17) { override fun migrate(db:SupportSQLiteDatabase)=LibraryCollectionSchema.migrate(db) }

/** Mona source receipts only; existing medical rows and unknown foreign identities are unchanged. */
val migration17To18=object:Migration(17,18) { override fun migrate(db:SupportSQLiteDatabase)=ExternalImportSchema.migrate(db) }

val migration18To19=object:Migration(18,19) { override fun migrate(db:SupportSQLiteDatabase)=FeatherlineImportSchema.migrate(db) }
/** Chrysalide dose-unknown history and purge-resistant source identity; additive only. */
val migration19To20=object:Migration(19,20) { override fun migrate(db:SupportSQLiteDatabase)=ChrysalideImportSchema.migrate(db) }

val migration20To21=object:Migration(20,21) { override fun migrate(db:SupportSQLiteDatabase)=AttachmentActivationSchema.migrate(db) }

fun allMigrations(today: () -> LocalDate = LocalDate::now): Array<Migration> = arrayOf(migration1To2(today), migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9, migration9To10, migration10To11, migration11To12, migration12To13, migration13To14, migration14To15, migration15To16, migration16To17, migration17To18,migration18To19,migration19To20,migration20To21)

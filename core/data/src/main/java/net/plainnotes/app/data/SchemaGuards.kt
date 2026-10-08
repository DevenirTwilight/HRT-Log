package net.plainnotes.app.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object SchemaGuards : RoomDatabase.Callback() {
    private const val finite = "1.7976931348623157e308"
    private val predicates = mapOf(
        "lab_context_revision" to "NEW.revision>=1 AND NEW.origin IN ('AT_ENTRY','RECONSTRUCTED','SAMPLE_CHANGED') AND length(NEW.context_json)>=2",
        "regimen_version" to "NEW.origin IN ('APP','LEGACY_RULE') AND length(NEW.zone)>0 AND length(NEW.definition_json)>=2 AND length(NEW.clinical_signature)=64 AND (NEW.effective_until_utc IS NULL OR NEW.effective_until_utc>NEW.effective_from_utc) AND NOT EXISTS (SELECT 1 FROM regimen_version r WHERE r.medication_id=NEW.medication_id AND r.id!=NEW.id AND (r.effective_until_utc IS NULL OR NEW.effective_from_utc<r.effective_until_utc) AND (NEW.effective_until_utc IS NULL OR r.effective_from_utc<NEW.effective_until_utc))",
        "regimen_rule_link" to "EXISTS (SELECT 1 FROM schedule_rule r JOIN regimen_version v ON v.id=NEW.regimen_id WHERE r.id=NEW.rule_id AND r.medication_id=v.medication_id AND r.effective_from_utc>=v.effective_from_utc AND (v.effective_until_utc IS NULL OR r.effective_until_utc IS NOT NULL AND r.effective_until_utc<=v.effective_until_utc))",
        "milestone" to "length(NEW.date)=10 AND NEW.kind IN (${MILESTONE_KINDS.joinToString(","){"'$it'"}}) AND (NEW.kind!='CUSTOM' OR length(trim(NEW.title))>0)",
        "pk_profile" to "length(NEW.ester)>0 AND length(NEW.pk_route)>0 AND (NEW.sl_tier IS NULL OR NEW.sl_tier BETWEEN 0 AND 3) AND (NEW.gel_product_id IS NULL OR NEW.gel_product_id>0) AND (NEW.gel_area_cm2 IS NULL OR NEW.gel_area_cm2>0 AND NEW.gel_area_cm2<=$finite) AND (NEW.patch_release_ug_day IS NULL OR NEW.patch_release_ug_day>0 AND NEW.patch_release_ug_day<=$finite)",
        "medication" to "length(trim(NEW.name))>0 AND NEW.dose_per_intake>0 AND NEW.dose_per_intake<=$finite AND NEW.container_capacity>0 AND NEW.container_capacity<=$finite AND (NEW.soon_alert_minutes IS NULL OR NEW.soon_alert_minutes>=0) AND (NEW.late_after_minutes IS NULL OR NEW.late_after_minutes>=0)",
        "schedule_rule" to "NEW.interval>0 AND NEW.interval<=36500 AND NEW.kind IN ('EVERY_N_DAYS','EVERY_N_HOURS','WEEKLY') AND (NEW.effective_until_utc IS NULL OR NEW.effective_until_utc>NEW.effective_from_utc) AND NEW.missed_tracking_from_utc>=NEW.effective_from_utc AND NEW.dose_snapshot>0 AND NEW.dose_snapshot<=$finite AND NEW.late_snapshot>=0 AND NEW.soon_snapshot>=0 AND ((NEW.kind='EVERY_N_HOURS' AND NEW.anchor_utc IS NOT NULL AND NEW.anchor_local IS NULL) OR (NEW.kind!='EVERY_N_HOURS' AND NEW.anchor_local IS NOT NULL AND NEW.anchor_utc IS NULL)) AND (NEW.kind!='WEEKLY' OR NEW.weekday_mask BETWEEN 1 AND 127) AND NOT EXISTS (SELECT 1 FROM schedule_rule r WHERE r.medication_id=NEW.medication_id AND r.id!=NEW.id AND (r.effective_until_utc IS NULL OR NEW.effective_from_utc<r.effective_until_utc) AND (NEW.effective_until_utc IS NULL OR r.effective_from_utc<NEW.effective_until_utc))",
        "rule_time" to "length(NEW.local_time)=8 AND (NEW.dose_override IS NULL OR (NEW.dose_override>0 AND NEW.dose_override<=$finite))",
        "slot_override" to "((NEW.rescheduled_utc IS NULL AND NEW.rescheduled_zone IS NULL) OR (NEW.rescheduled_utc IS NOT NULL AND length(NEW.rescheduled_zone)>0)) AND (NEW.dose_override IS NULL OR (NEW.dose_override>0 AND NEW.dose_override<=$finite)) AND NEW.skipped IN (0,1) AND (NEW.rescheduled_utc IS NOT NULL OR NEW.dose_override IS NOT NULL OR NEW.skipped=1) AND EXISTS (SELECT 1 FROM schedule_rule WHERE id=NEW.rule_version_id AND medication_id=NEW.medication_id)",
        "dose_record" to "NEW.status IN ('ON_TIME','LATE','MISSED','SKIPPED') AND NEW.origin IN ('APP','IMPORT_TM','IMPORT_HT','AUTO_MISSED') AND ((NEW.scheduled_utc IS NULL AND NEW.scheduled_zone IS NULL) OR (NEW.scheduled_utc IS NOT NULL AND length(NEW.scheduled_zone)>0)) AND (NEW.slot_key IS NULL OR (NEW.rule_version_id IS NOT NULL AND NEW.scheduled_utc IS NOT NULL)) AND (NEW.rule_version_id IS NULL OR EXISTS (SELECT 1 FROM schedule_rule WHERE id=NEW.rule_version_id AND medication_id=NEW.medication_id)) AND ((NEW.status IN ('MISSED','SKIPPED') AND NEW.taken_utc IS NULL AND NEW.taken_zone IS NULL AND NEW.actual_dose IS NULL AND NEW.site IS NULL AND NOT EXISTS (SELECT 1 FROM supply_transaction WHERE dose_record_id=NEW.id GROUP BY container_id HAVING sum(used_delta)!=0)) OR (NEW.status IN ('ON_TIME','LATE') AND NEW.taken_utc IS NOT NULL AND length(NEW.taken_zone)>0 AND ((NEW.actual_dose>0 AND NEW.actual_dose<=$finite) OR (NEW.actual_dose IS NULL AND NEW.origin='IMPORT_TM')))) AND (NEW.planned_dose IS NULL OR (NEW.planned_dose>0 AND NEW.planned_dose<=$finite)) AND NEW.revision>=1 AND (NEW.unallocated_supply_amount IS NULL OR NEW.unallocated_supply_amount>=0)",
        "supply_container" to "NEW.capacity>0 AND NEW.capacity<=$finite AND NEW.initial_used_amount>=0 AND NEW.initial_used_amount<=NEW.capacity AND NEW.used_amount>=0 AND NEW.used_amount<=NEW.capacity AND NEW.state IN ('SEALED','IN_USE','EMPTY','DISCARDED')",
        "supply_transaction" to "NEW.used_delta!=0 AND abs(NEW.used_delta)<=$finite AND ((NEW.kind='ADJUST' AND NEW.dose_record_id IS NULL AND NEW.reversal_of_id IS NULL) OR (NEW.kind='CONSUME' AND NEW.used_delta>0 AND NEW.reversal_of_id IS NULL AND EXISTS (SELECT 1 FROM dose_record WHERE id=NEW.dose_record_id AND status IN ('ON_TIME','LATE') AND deleted_at_utc IS NULL AND revision=NEW.dose_revision)) OR (NEW.kind='REVERSE' AND EXISTS (SELECT 1 FROM supply_transaction t WHERE t.id=NEW.reversal_of_id AND t.kind='CONSUME' AND t.container_id=NEW.container_id AND t.dose_record_id=NEW.dose_record_id AND NEW.used_delta=-t.used_delta))) AND EXISTS (SELECT 1 FROM supply_container c WHERE c.id=NEW.container_id AND c.used_amount+NEW.used_delta BETWEEN 0 AND c.capacity) AND (NEW.dose_record_id IS NULL OR EXISTS (SELECT 1 FROM dose_record d JOIN supply_container c ON c.medication_id=d.medication_id WHERE d.id=NEW.dose_record_id AND c.id=NEW.container_id))",
        "appointment" to "NEW.remind_minutes_before>=0 AND length(NEW.at_zone)>0 AND (NEW.completed_utc IS NULL OR NEW.completed_utc>0)",
        "history_period_revision" to "NEW.revision>=1 AND NEW.state IN ('CONFIRMED','REVOKED') AND ((NEW.kind='CONFIRMED' AND NEW.origin='OBSERVED_USER_CONFIRMED') OR (NEW.kind IN ('PERIOD','FILL','DELETED','STOP') AND NEW.origin='USER_EDIT')) AND (NEW.group_key IS NULL OR length(NEW.group_key)=36) AND length(NEW.period_key)=36 AND length(NEW.from_date)=10 AND (NEW.until_date IS NULL OR (length(NEW.until_date)=10 AND NEW.until_date>NEW.from_date)) AND length(NEW.zone)>0 AND length(NEW.standard_json)>=2 AND length(NEW.identity_json)>=2 AND length(NEW.evidence_json)>=2 AND NEW.created_utc>0 AND (NEW.revision=1 OR EXISTS (SELECT 1 FROM history_period_revision p WHERE p.period_key=NEW.period_key AND p.revision=NEW.revision-1 AND p.medication_id=NEW.medication_id))",
        "record_annotation" to "NEW.kind IN ('EXTRA') AND NEW.created_utc>0",
        "visit_question" to "length(trim(NEW.text))>0 AND NEW.status IN ('OPEN','ASKED') AND NEW.sort_order>=0",
        "visit_pack" to "NEW.generated_utc>0 AND length(NEW.zone)>0 AND length(NEW.range_from)=10 AND length(NEW.range_to)=10 AND NEW.range_from<=NEW.range_to AND length(NEW.sections)>0 AND length(NEW.language)>0 AND NEW.template_version>=1 AND length(NEW.input_digest)=64 AND NEW.input_digest NOT GLOB '*[^0-9a-f]*' AND length(NEW.facts_json)>=2",
        "checkin_score" to "NEW.value BETWEEN 1 AND 5",
        "lab_value" to "NEW.value>0 AND NEW.value<=$finite AND length(NEW.unit)>0 AND (NEW.reference_lower IS NULL OR abs(NEW.reference_lower)<=$finite) AND (NEW.reference_upper IS NULL OR abs(NEW.reference_upper)<=$finite) AND (NEW.reference_lower IS NULL OR NEW.reference_upper IS NULL OR NEW.reference_lower<=NEW.reference_upper) AND ((NEW.reference_lower IS NULL AND NEW.reference_upper IS NULL) OR length(NEW.reference_unit)>0)",
        "stage_review" to "length(NEW.date)=10 AND length(NEW.effects_json)>=2 AND (NEW.smoking IS NULL OR NEW.smoking IN ('YES','NO')) AND (NEW.systolic IS NULL OR (NEW.systolic>0 AND NEW.systolic<1000)) AND (NEW.diastolic IS NULL OR (NEW.diastolic>0 AND NEW.diastolic<1000)) AND (NEW.weight_kg IS NULL OR (NEW.weight_kg>0 AND NEW.weight_kg<=$finite)) AND (NEW.satisfaction IS NULL OR NEW.satisfaction BETWEEN 1 AND 5)",
        "symptom_check" to "length(NEW.date)=10 AND length(NEW.group_id)>0",
        "checkin_score" to "NEW.value BETWEEN 1 AND 5 AND length(NEW.date)=10",
        "pk_settings" to "NEW.id=1 AND (NEW.current_weight_kg IS NULL OR (NEW.current_weight_kg>0 AND NEW.current_weight_kg<=$finite))",
    )
    override fun onCreate(db: SupportSQLiteDatabase) = install(db)
    override fun onOpen(db: SupportSQLiteDatabase) = install(db)
    fun validateRestored(db: SupportSQLiteDatabase) {
        predicates.filterKeys{it!="supply_transaction"}.forEach{(table,predicate)->
            db.query("SELECT 1 FROM `$table` AS NEW WHERE COALESCE(($predicate),0)=0 LIMIT 1").use{
                require(!it.moveToFirst()){ "Invalid $table" }
            }
        }
    }
    fun install(db: SupportSQLiteDatabase) {
        // Same Room index name/columns; partial predicate implements active-record uniqueness.
        db.execSQL("DROP INDEX IF EXISTS index_dose_record_slot_key")
        db.execSQL("CREATE UNIQUE INDEX index_dose_record_slot_key ON dose_record(slot_key) WHERE deleted_at_utc IS NULL")
        predicates.forEach { (table,predicate) ->
            val operations=if(table=="supply_transaction") listOf("INSERT") else listOf("INSERT","UPDATE")
            // Re-created on every open so a predicate change reaches existing databases.
            operations.forEach { op -> db.execSQL("DROP TRIGGER IF EXISTS guard_${table}_${op.lowercase()}"); db.execSQL("CREATE TRIGGER guard_${table}_${op.lowercase()} BEFORE $op ON $table BEGIN SELECT CASE WHEN COALESCE(($predicate),0)=0 THEN RAISE(ABORT,'Invalid $table') END; END") }
        }
        db.execSQL("CREATE TRIGGER IF NOT EXISTS immutable_history_period_update BEFORE UPDATE ON history_period_revision BEGIN SELECT RAISE(ABORT,'Append-only history period'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS immutable_history_period_delete BEFORE DELETE ON history_period_revision BEGIN SELECT RAISE(ABORT,'Append-only history period'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS immutable_visit_pack_update BEFORE UPDATE ON visit_pack BEGIN SELECT RAISE(ABORT,'Frozen visit pack'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS immutable_lab_context_update BEFORE UPDATE ON lab_context_revision BEGIN SELECT RAISE(ABORT,'Frozen lab context'); END")
        val frozen=listOf("id","medication_id","effective_from_utc","zone","definition_json","clinical_signature","origin","recorded_at_utc").joinToString(" AND "){"NEW.$it IS OLD.$it"}
        db.execSQL("CREATE TRIGGER IF NOT EXISTS immutable_regimen_update BEFORE UPDATE ON regimen_version WHEN NOT ($frozen) BEGIN SELECT RAISE(ABORT,'Frozen regimen definition'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS immutable_regimen_link_update BEFORE UPDATE ON regimen_rule_link BEGIN SELECT RAISE(ABORT,'Frozen regimen link'); END")
        listOf("UPDATE","DELETE").forEach { op -> db.execSQL("CREATE TRIGGER IF NOT EXISTS immutable_supply_${op.lowercase()} BEFORE $op ON supply_transaction BEGIN SELECT RAISE(ABORT,'Append-only supply ledger'); END") }
        db.execSQL("CREATE TRIGGER IF NOT EXISTS supply_cache AFTER INSERT ON supply_transaction BEGIN UPDATE supply_container SET used_amount=used_amount+NEW.used_delta WHERE id=NEW.container_id; END")
    }
}

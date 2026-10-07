package net.plainnotes.app.data

import androidx.room.*

@Entity(tableName = "medication")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val molecule: String,
    val route: String? = null,
    val unit: String,
    val dose_per_intake: Double,
    val container_capacity: Double,
    val expiry_days_after_open: Int? = null,
    val soon_alert_minutes: Int? = null,
    val late_after_minutes: Int? = null,
    val site_rotation: Boolean,
    val site_set: String? = null,
    val notifications_on: Boolean,
    val active: Boolean,
    val sort_order: Int,
    val needs_review: String? = null,
)

@Entity(tableName = "pk_profile", foreignKeys = [ForeignKey(entity = MedicationEntity::class, parentColumns = ["id"], childColumns = ["medication_id"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["medication_id"], unique = false)])
data class ProfileEntity(
    @PrimaryKey val medication_id: Long,
    val ester: String,
    val pk_route: String,
    val sl_tier: Int? = null,
    val gel_product_id: Int? = null,
    val gel_site: String? = null,
    val gel_area_cm2: Double? = null,
    val patch_release_ug_day: Double? = null,
)

@Entity(tableName = "schedule_rule", foreignKeys = [ForeignKey(entity = MedicationEntity::class, parentColumns = ["id"], childColumns = ["medication_id"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["medication_id"], unique = false)])
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medication_id: Long,
    val kind: String,
    val interval: Int,
    val weekday_mask: Int,
    val anchor_local: String? = null,
    val anchor_zone: String,
    val anchor_utc: Long? = null,
    val effective_from_utc: Long,
    val effective_until_utc: Long? = null,
    val effective_zone: String,
    val missed_tracking_from_utc: Long,
    val dose_snapshot: Double,
    val soon_snapshot: Int,
    val late_snapshot: Int,
    val config_snapshot: String,
)

@Entity(tableName = "rule_time", foreignKeys = [ForeignKey(entity = RuleEntity::class, parentColumns = ["id"], childColumns = ["rule_id"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["rule_id", "local_time"], unique = true)])
data class TimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rule_id: Long,
    val local_time: String,
    val dose_override: Double? = null,
)

@Entity(tableName = "slot_override", foreignKeys = [ForeignKey(entity = MedicationEntity::class, parentColumns = ["id"], childColumns = ["medication_id"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = RuleEntity::class, parentColumns = ["id"], childColumns = ["rule_version_id"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["slot_key"], unique = true), Index(value = ["medication_id"], unique = false), Index(value = ["rule_version_id"], unique = false)])
data class OverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medication_id: Long,
    val rule_version_id: Long,
    val slot_key: String,
    val rescheduled_utc: Long? = null,
    val rescheduled_zone: String? = null,
    val dose_override: Double? = null,
    val skipped: Boolean,
)

@Entity(tableName = "dose_record", foreignKeys = [ForeignKey(entity = MedicationEntity::class, parentColumns = ["id"], childColumns = ["medication_id"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = RuleEntity::class, parentColumns = ["id"], childColumns = ["rule_version_id"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["slot_key"], unique = true), Index(value = ["source_record_key"], unique = true), Index(value = ["medication_id"], unique = false), Index(value = ["rule_version_id"], unique = false)])
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medication_id: Long,
    val rule_version_id: Long? = null,
    val slot_key: String? = null,
    val scheduled_utc: Long? = null,
    val scheduled_zone: String? = null,
    val planned_dose: Double? = null,
    val late_after_minutes_snapshot: Int? = null,
    val taken_utc: Long? = null,
    val taken_zone: String? = null,
    val actual_dose: Double? = null,
    val unallocated_supply_amount: Double? = null,
    val status: String,
    val site: String? = null,
    val note: String? = null,
    val origin: String,
    val source_record_key: String? = null,
    val revision: Int,
    val deleted_at_utc: Long? = null,
    val config_snapshot: String,
)

@Entity(tableName = "supply_container", foreignKeys = [ForeignKey(entity = MedicationEntity::class, parentColumns = ["id"], childColumns = ["medication_id"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["medication_id"], unique = false)])
data class ContainerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medication_id: Long,
    val capacity: Double,
    val initial_used_amount: Double,
    val used_amount: Double,
    val opened_on: String? = null,
    val state: String,
    /** Optional, user-entered; never evaluated. */
    val source_note: String? = null,
    val batch: String? = null,
)

@Entity(tableName = "supply_transaction", foreignKeys = [ForeignKey(entity = ContainerEntity::class, parentColumns = ["id"], childColumns = ["container_id"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["dose_record_id"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = SupplyEntryEntity::class, parentColumns = ["id"], childColumns = ["reversal_of_id"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["operation_id", "container_id", "kind"], unique = true), Index(value = ["reversal_of_id"], unique = true), Index(value = ["container_id"], unique = false), Index(value = ["dose_record_id"], unique = false)])
data class SupplyEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val container_id: Long,
    val dose_record_id: Long? = null,
    val dose_revision: Int? = null,
    val operation_id: String,
    val kind: String,
    val used_delta: Double,
    val reversal_of_id: Long? = null,
    val created_utc: Long,
    val created_zone: String,
    val reason: String? = null,
)

@Entity(tableName = "appointment")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val at_utc: Long,
    val at_zone: String,
    val location: String? = null,
    val practitioner: String? = null,
    val note: String? = null,
    val remind_minutes_before: Int,
)

@Entity(tableName = "checkin_item")
data class CheckinItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val builtin_key: String? = null,
    val custom_label: String? = null,
    val enabled: Boolean,
    val sort_order: Int,
    /** Item kept from the previous (Trans Memo-style) set, shown under "previous items". */
    @ColumnInfo(defaultValue = "0") val legacy: Boolean = false,
)

/** Periodic review following the four aspects of HAS R40 (effects, tolerance, risk factors, satisfaction). All optional. */
@Entity(tableName = "stage_review")
data class StageReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    /** Effect id -> "NOT_YET" | "NOTICED" | "UNSURE", plus optional "<id>:note" entries. */
    val effects_json: String = "{}",
    val tolerance_note: String? = null,
    val risk_note: String? = null,
    /** "YES" | "NO" | null. */
    val smoking: String? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val weight_kg: Double? = null,
    val satisfaction: Int? = null,
    val satisfaction_note: String? = null,
)

/** A symptom group from the bundled official-source catalog noticed on a day; no score. */
@Entity(tableName = "symptom_check", primaryKeys = ["date", "group_id"])
data class SymptomCheckEntity(
    val date: String,
    val group_id: String,
    val note: String? = null,
)

/** Visibility of a stage-review effect item; no row means shown. */
@Entity(tableName = "review_effect")
data class ReviewEffectEntity(
    @PrimaryKey val effect_id: String,
    val enabled: Boolean,
)

@Entity(tableName = "checkin_score", primaryKeys = ["date", "item_id"], foreignKeys = [ForeignKey(entity = CheckinItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["item_id"], unique = false)])
data class CheckinScoreEntity(
    val date: String,
    val item_id: Long,
    val value: Int,
)

@Entity(tableName = "day_note")
data class DayNoteEntity(
    @PrimaryKey val date: String,
    val text: String,
)

@Entity(tableName = "lab_analyte")
data class AnalyteEntity(
    @PrimaryKey val code: String,
    val canonical_unit: String,
)

@Entity(tableName = "lab_value", foreignKeys = [ForeignKey(entity = AnalyteEntity::class, parentColumns = ["code"], childColumns = ["analyte_code"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["analyte_code"], unique = false)])
data class LabValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analyte_code: String,
    val value: Double,
    val unit: String,
    val sampled_utc: Long,
    val sampled_zone: String,
    val reference_lower: Double? = null,
    val reference_upper: Double? = null,
    val reference_unit: String? = null,
    val laboratory: String? = null,
    val note: String? = null,
)

@Entity(tableName = "pk_settings")
data class PkSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val current_weight_kg: Double? = null,
)

@Entity(tableName = "retained_slot", foreignKeys = [ForeignKey(entity = RuleEntity::class, parentColumns = ["id"], childColumns = ["rule_id"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = MedicationEntity::class, parentColumns = ["id"], childColumns = ["medication_id"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["rule_id"], unique = false), Index(value = ["medication_id"], unique = false)])
data class RetainedEntity(
    @PrimaryKey val slot_key: String,
    val rule_id: Long,
    val medication_id: Long,
    val original_utc: Long,
    val at_utc: Long,
    val zone: String,
    val dose: Double,
    val soon_minutes: Int,
    val late_minutes: Int,
    val tracking_from_utc: Long,
)

@Entity(tableName = "reminder_mapping", indices = [Index(value = ["generation"], unique = false)])
data class ReminderMappingEntity(
    @PrimaryKey val opaque_id: String,
    val generation: String,
    val identity: String,
    val trigger_utc: Long,
    val sent: Boolean,
)

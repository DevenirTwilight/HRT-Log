package net.plainnotes.app.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao interface NotesDao {
    @Query("SELECT * FROM medication ORDER BY sort_order, id") fun observeMedications(): Flow<List<MedicationEntity>>
    @Query("SELECT * FROM medication ORDER BY id") suspend fun medications(): List<MedicationEntity>
    @Query("SELECT * FROM medication WHERE id = :id") suspend fun medication(id: Long): MedicationEntity
    @Insert suspend fun insertMedication(value: MedicationEntity): Long
    @Update suspend fun updateMedication(value: MedicationEntity)
    @Query("SELECT * FROM pk_profile WHERE medication_id=:id") suspend fun profile(id:Long): ProfileEntity?
    @Upsert suspend fun profile(value:ProfileEntity)
    @Query("SELECT * FROM schedule_rule ORDER BY effective_from_utc") suspend fun rules(): List<RuleEntity>
    @Insert suspend fun rule(value: RuleEntity): Long
    @Update suspend fun updateRule(value: RuleEntity)
    @Query("SELECT * FROM rule_time WHERE rule_id = :id") suspend fun times(id: Long): List<TimeEntity>
    @Insert suspend fun time(value: TimeEntity)
    @Query("SELECT * FROM slot_override") suspend fun overrides(): List<OverrideEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOverride(value: OverrideEntity): Long
    @Update suspend fun updateOverride(value: OverrideEntity)
    @Query("DELETE FROM slot_override WHERE slot_key = :key") suspend fun deleteOverride(key: String)
    @Query("SELECT * FROM dose_record WHERE deleted_at_utc IS NULL") suspend fun records(): List<RecordEntity>
    @Insert suspend fun record(value: RecordEntity): Long
    @Update suspend fun updateRecord(value: RecordEntity)
    @Query("SELECT * FROM retained_slot") suspend fun retained(): List<RetainedEntity>
    @Upsert suspend fun retain(value: RetainedEntity)
    @Query("SELECT * FROM appointment ORDER BY at_utc") fun observeAppointments(): Flow<List<AppointmentEntity>>
    @Query("SELECT * FROM appointment ORDER BY at_utc") suspend fun appointments(): List<AppointmentEntity>
    @Insert suspend fun appointment(value: AppointmentEntity): Long
    @Query("SELECT * FROM reminder_mapping") suspend fun mappings(): List<ReminderMappingEntity>
    @Insert suspend fun mapping(value: ReminderMappingEntity)
    @Query("UPDATE reminder_mapping SET sent=1 WHERE opaque_id=:id") suspend fun markSent(id:String)
    @Query("DELETE FROM reminder_mapping WHERE trigger_utc < :before") suspend fun pruneMappings(before:Long)
    @Query("DELETE FROM reminder_mapping WHERE generation != :generation") suspend fun clearOldMappings(generation:String)
}

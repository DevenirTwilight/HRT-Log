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
    @Query("SELECT * FROM dose_record WHERE id = :id") suspend fun recordById(id: Long): RecordEntity?
    @Query("SELECT * FROM supply_container ORDER BY medication_id, id") suspend fun containers(): List<ContainerEntity>
    @Query("SELECT * FROM supply_container WHERE id = :id") suspend fun container(id: Long): ContainerEntity
    @Insert suspend fun insertContainer(value: ContainerEntity): Long
    @Query("UPDATE supply_container SET capacity = :capacity WHERE id = :id") suspend fun setContainerCapacity(id: Long, capacity: Double)
    @Query("UPDATE supply_container SET state = :state, opened_on = :openedOn WHERE id = :id") suspend fun setContainerState(id: Long, state: String, openedOn: String?)
    @Query("SELECT * FROM supply_transaction WHERE dose_record_id = :recordId ORDER BY id") suspend fun supplyFor(recordId: Long): List<SupplyEntryEntity>
    @Insert suspend fun supply(value: SupplyEntryEntity): Long
    @Query("SELECT * FROM checkin_item ORDER BY sort_order, id") suspend fun checkinItems(): List<CheckinItemEntity>
    @Insert suspend fun insertCheckinItem(value: CheckinItemEntity): Long
    @Update suspend fun updateCheckinItem(value: CheckinItemEntity)
    @Query("SELECT * FROM checkin_score WHERE date BETWEEN :from AND :to") suspend fun scores(from: String, to: String): List<CheckinScoreEntity>
    @Upsert suspend fun score(value: CheckinScoreEntity)
    @Query("DELETE FROM checkin_score WHERE date = :date AND item_id = :item") suspend fun deleteScore(date: String, item: Long)
    @Query("SELECT * FROM day_note WHERE date BETWEEN :from AND :to") suspend fun notes(from: String, to: String): List<DayNoteEntity>
    @Upsert suspend fun note(value: DayNoteEntity)
    @Query("DELETE FROM day_note WHERE date = :date") suspend fun deleteNote(date: String)
    @Query("SELECT * FROM lab_value ORDER BY sampled_utc") suspend fun labs(): List<LabValueEntity>
    @Insert suspend fun insertLab(value: LabValueEntity): Long
    @Update suspend fun updateLab(value: LabValueEntity)
    @Query("DELETE FROM lab_value WHERE id = :id") suspend fun deleteLab(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun analyte(value: AnalyteEntity)
    @Query("SELECT * FROM pk_settings WHERE id = 1") suspend fun pkSettings(): PkSettingsEntity?
    @Upsert suspend fun pkSettings(value: PkSettingsEntity)
    @Query("SELECT * FROM reminder_mapping") suspend fun mappings(): List<ReminderMappingEntity>
    @Insert suspend fun mapping(value: ReminderMappingEntity)
    @Query("UPDATE reminder_mapping SET sent=1 WHERE opaque_id=:id") suspend fun markSent(id:String)
    @Query("DELETE FROM reminder_mapping WHERE trigger_utc < :before") suspend fun pruneMappings(before:Long)
    @Query("DELETE FROM reminder_mapping WHERE generation != :generation") suspend fun clearOldMappings(generation:String)
}

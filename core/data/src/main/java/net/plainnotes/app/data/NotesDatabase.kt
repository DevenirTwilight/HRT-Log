package net.plainnotes.app.data
import androidx.room.*
@Database(entities = [MedicationEntity::class, ProfileEntity::class, RuleEntity::class, TimeEntity::class, OverrideEntity::class, RecordEntity::class, ContainerEntity::class, SupplyEntryEntity::class, AppointmentEntity::class, CheckinItemEntity::class, CheckinScoreEntity::class, DayNoteEntity::class, AnalyteEntity::class, LabValueEntity::class, PkSettingsEntity::class, RetainedEntity::class, ReminderMappingEntity::class, StageReviewEntity::class, SymptomCheckEntity::class, ReviewEffectEntity::class], version = 2, exportSchema = true)
abstract class NotesDatabase : RoomDatabase() { abstract fun dao(): NotesDao }

package net.plainnotes.app.data

import net.plainnotes.app.domain.*

/** Committed display facts, collected in one read transaction. No DAO/write capability escapes.
 * Does not materialize missed records, seed defaults, migrate history, or certify a pending save. */
data class NotesReadSnapshot(
    val medications:List<MedicationEntity>, val rules:List<RuleEntity>, val times:Map<Long,List<TimeEntity>>,
    val profiles:Map<Long,ProfileEntity>, val appointments:List<AppointmentEntity>, val slots:List<TimelineEntry>, val upcoming:List<TimelineEntry>,
    val records:List<RecordEntity>, val containers:List<ContainerEntity>, val items:List<CheckinItemEntity>,
    val scores:List<CheckinScoreEntity>, val notes:List<DayNoteEntity>, val reviews:List<StageReviewEntity>,
    val symptoms:List<SymptomCheckEntity>, val effects:List<ReviewEffectEntity>, val regimens:List<RegimenVersionEntity>,
    val regimenLinks:List<RegimenRuleLinkEntity>, val milestones:List<MilestoneEntity>, val labs:List<LabValueEntity>,
    val labContexts:List<LabContextEntity>, val visitQuestions:List<VisitQuestionEntity>, val visitPacks:List<VisitPackEntity>,
    val historyPeriods:List<HistoryPeriodEntity>, val annotations:List<RecordAnnotationEntity>, val trash:List<TrashItemEntity>,
    val trashedRecords:List<RecordEntity>, val stockForecasts:Map<Long,StockForecast>, val containersWithLedger:Set<Long>,
    val voidedContainers:List<ContainerEntity>, val containersWithConsumption:Set<Long>, val referenceEpoch:String?,
    val labPanels:List<LabPanelEntity>,
)

/** The same factual inputs as the existing calculation reader; contains no model or parameters. */
data class CalculationReadInputs(
    val medications:List<MedicationEntity>, val profiles:Map<Long,ProfileEntity>, val records:List<RecordEntity>,
    val labs:List<LabValueEntity>, val weight:Double?, val planned:List<TimelineEntry>, val snapshots:Map<Long,String>,
)

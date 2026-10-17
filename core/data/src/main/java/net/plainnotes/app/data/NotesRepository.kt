package net.plainnotes.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.domain.*
import org.json.JSONObject
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton

/** Built-in well-being items, in display order. */
val CHECKIN_DEFAULTS=DAILY_KEYS
const val BACKFILL_MAX_DAYS=731L

@Singleton class NotesRepository(private val access: DatabaseAccess, private val pinned: Space?) {
    @Inject constructor(access: DatabaseAccess) : this(access, null)
    /** A repository that always works on [space], whatever the UI has selected (used by background reminders). */
    fun pinnedTo(space: Space) = NotesRepository(access, space)
    val space get() = pinned ?: access.current
    fun select(space: Space) = access.select(space)
    private fun db() = access.get(space)
    suspend fun <T> transaction(block: suspend (NotesDao) -> T): T = withContext(Dispatchers.IO) {
        val db=db();db.withTransaction { block(db.dao()) }
    }
    /** Capture the database once: changing the selected space cannot redirect a suspended reference transaction. */
    private suspend fun <T> transactionWithSql(required: Space? = null, block: suspend (NotesDao, androidx.sqlite.db.SupportSQLiteDatabase) -> T): T {
        val target=space
        if(required!=null && target!=required) throw ReferenceFailure(ReferenceError.WRONG_SPACE)
        return withContext(Dispatchers.IO) {
            val database=access.get(target)
            database.withTransaction { block(database.dao(),database.openHelper.writableDatabase) }
        }
    }
    /** Offline scan over immutable user-entered snapshots; bounded results, no product/name coalescing. */
    suspend fun savedReferenceProducts(query:String="",form:String="",route:String="",strength:String=""):SavedReferencePage = transactionWithSql(Space.PRIMARY) { _,raw ->
        require(listOf(query,form,route,strength).all {it.length<=256})
        val entries=mutableListOf<SavedReferenceProduct>()
        var limited=false
        raw.query("SELECT uuid,payload_json FROM reference_snapshot WHERE kind='PRODUCT' AND namespace='USER_ENTERED' ORDER BY uuid").use { cursor ->
            while(cursor.moveToNext()) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val product=ReferencePayload.readProduct(JSONObject(cursor.getString(1)))
                if(ReferenceSearch.matches(product,query,form,route,strength)) {
                    if(entries.size==200) {limited=true;break}
                    entries+=SavedReferenceProduct(cursor.getString(0),product)
                }
            }
        }
        SavedReferencePage(entries.toList(),limited)
    }
    suspend fun referencePreviewEpoch():String = transactionWithSql(Space.PRIMARY) { _,raw -> ReferenceStore.previewEpoch(raw) }
    /** The caller's epoch was captured with its displayed record list, before any asynchronous restore or ID reuse. */
    suspend fun recordedReferenceHistory(expectedEpoch:String,record:Long):RecordedReferenceHistory = transactionWithSql(Space.PRIMARY) { dao,raw ->
        if(ReferenceStore.previewEpoch(raw)!=expectedEpoch)throw ReferenceFailure(ReferenceError.STALE_SCOPE)
        val stored=dao.recordById(record) ?: throw ReferenceFailure(ReferenceError.UNKNOWN_IDENTITY)
        val scope=ReferenceStore.existingScope(raw)
        RecordedReferenceHistory(stored.revision,scope?.let{ReferenceStore.recordContexts(raw,it,record)} ?: emptyList())
    }
    suspend fun medicationReferenceState(medication: Long): MedicationReferenceState = transactionWithSql(Space.PRIMARY) { _, raw ->
        val scope=ReferenceStore.existingScope(raw)
        MedicationReferenceState(scope,scope?.let {ReferenceStore.current(raw,it,medication)},ReferenceStore.previewEpoch(raw))
    }
    suspend fun associateInitialMedication(medication: Long, operationUuid: String, product: UserReferenceProduct, expectedPreviewEpoch: String): MedicationReferenceState = transactionWithSql(Space.PRIMARY) { _, raw ->
        ReferenceStore.initialAssociation(raw,medication,operationUuid,product,expectedPreviewEpoch)
    }
    suspend fun referenceScope(): ReferenceScope = transactionWithSql(Space.PRIMARY) { _, raw -> ReferenceStore.scope(raw) }
    suspend fun medicationReference(scope: ReferenceScope, medication: Long): MedicationReference? = transactionWithSql(Space.PRIMARY) { _, raw ->
        ReferenceStore.current(raw,scope,medication)
    }
    suspend fun recordReferenceContexts(scope: ReferenceScope, record: Long): List<RecordReferenceContext> = transactionWithSql(Space.PRIMARY) { _, raw ->
        ReferenceStore.recordContexts(raw,scope,record)
    }
    suspend fun associateMedication(scope: ReferenceScope, medication: Long, expectedRevision: Int, operationUuid: String, product: UserReferenceProduct?): MedicationReference = transactionWithSql(Space.PRIMARY) { _, raw ->
        ReferenceStore.associate(raw,scope,medication,expectedRevision,operationUuid,product)
    }
    // Internal production integration; UI activation waits for shared forecast admission.
    internal suspend fun measuredWriteTicket(scope:ReferenceScope,medication:Long,record:Long?=null):MeasuredWriteTicket = transactionWithSql(Space.PRIMARY) { _,raw ->
        MeasuredUseIntegration.ticket(raw,scope,medication,record)
    }
    internal suspend fun recordMeasuredUse(ticket:MeasuredWriteTicket,operationUuid:String,draft:MeasuredActualUseDraft,created:Instant=Instant.now(),checkpoint:(ActualUseCheckpoint)->Unit={}):RecordedMeasuredUse {
        val target=space
        if(target!=Space.PRIMARY)throw ReferenceFailure(ReferenceError.WRONG_SPACE)
        val frozenDraft=draft.copy(uses=draft.uses.toList())
        return withContext(Dispatchers.IO) {val database=access.get(target);database.withTransaction {
            MeasuredUseIntegration.create(database,ticket,operationUuid,frozenDraft,created,checkpoint)
        }}
    }
    private suspend fun <T> measuredTransaction(block:suspend(NotesDatabase)->T):T {
        val target=space
        if(target!=Space.PRIMARY)throw ReferenceFailure(ReferenceError.WRONG_SPACE)
        return withContext(Dispatchers.IO){val database=access.get(target);database.withTransaction{block(database)}}
    }
    internal suspend fun createMeasuredPackage(expectedEpoch:String,expectedMedication:MedicationEntity,operationUuid:String,draft:PackageMeasurementDraft,created:Instant=Instant.now()):CreatedMeasuredContainer {
        val frozenDraft=draft.copy(product=draft.product.copy(ingredients=draft.product.ingredients.toList(),routes=draft.product.routes.toList()),relations=draft.relations.toList())
        return measuredTransaction {database->
            val raw=database.openHelper.writableDatabase
            if(ReferenceStore.previewEpoch(raw)!=expectedEpoch)throw ReferenceFailure(ReferenceError.STALE_SCOPE)
            if(database.dao().medication(expectedMedication.id)!=expectedMedication)throw ReferenceFailure(ReferenceError.STALE_REVISION)
            val result=MeasuredContainerStore.create(database,expectedEpoch,expectedMedication.id,operationUuid,frozenDraft,created)
            MeasuredConsumptionStore.auditStagedDataset(raw);result
        }
    }
    internal suspend fun countMeasuredStock(ticket:MeasuredWriteTicket,operationUuid:String,input:ConfirmedStockCount,created:Instant=Instant.now()):MeasuredCountReceipt =
        measuredTransaction{MeasuredUseIntegration.count(it,ticket,operationUuid,input,created)}
    internal suspend fun reviseMeasuredUse(ticket:MeasuredWriteTicket,sourceOperation:String,reverseOperation:String,newOperation:String,draft:MeasuredActualUseDraft,created:Instant=Instant.now(),checkpoint:(ActualUseCheckpoint)->Unit={}):RecordedMeasuredUse {
        val frozenDraft=draft.copy(uses=draft.uses.toList())
        return measuredTransaction{MeasuredUseIntegration.revise(it,ticket,sourceOperation,reverseOperation,newOperation,frozenDraft,created,checkpoint)}
    }
    internal suspend fun deleteMeasuredUse(ticket:MeasuredWriteTicket,sourceOperation:String,reverseOperation:String,created:Instant=Instant.now()):RecordEntity =
        measuredTransaction{MeasuredUseIntegration.delete(it,ticket,sourceOperation,reverseOperation,created)}
    internal suspend fun restoreMeasuredUse(ticket:MeasuredWriteTicket,newOperation:String,draft:MeasuredActualUseDraft,created:Instant=Instant.now(),checkpoint:(ActualUseCheckpoint)->Unit={}):RecordedMeasuredUse {
        val frozenDraft=draft.copy(uses=draft.uses.toList())
        return measuredTransaction{MeasuredUseIntegration.restore(it,ticket,newOperation,frozenDraft,created,checkpoint)}
    }
    internal suspend fun correctMeasuredPackage(ticket:MeasuredWriteTicket,operationUuid:String,containerUuid:String,action:MeasuredPackageAction,userConfirmed:Boolean,created:Instant=Instant.now()):MeasuredPackageReceipt =
        measuredTransaction{MeasuredUseIntegration.packageAction(it,ticket,operationUuid,containerUuid,action,userConfirmed,created)}
    suspend fun medications()=withContext(Dispatchers.IO){db().dao().medications()}
    suspend fun saveMilestone(value:MilestoneEntity)=transaction{dao->
        LocalDate.parse(value.date);require(value.kind in MILESTONE_KINDS)
        require(value.kind!="CUSTOM" || !value.title.isNullOrBlank())
        val normalized=value.copy(title=value.title?.trim()?.takeIf{it.isNotEmpty()},note=value.note?.trim()?.takeIf{it.isNotEmpty()})
        val inserted=dao.milestone(normalized)
        normalized.copy(id=if(value.id==0L)inserted else value.id)
    }
    /** §39: into the recycle bin (restorable); see [Trash]. */
    suspend fun deleteMilestone(id:Long,now:Instant=Instant.now())=transaction{dao->
        val rows=Trash.rows(sql(),"milestone","id=?",arrayOf(id));if(rows.length()==0)return@transaction
        Trash.insert(sql(),Trash.MILESTONE,"$id",rows.getJSONObject(0).getString("date"),Trash.payload("milestone" to rows),now.toEpochMilli());dao.deleteMilestone(id)}
    private fun sql()=db().openHelper.writableDatabase
    suspend fun appointments()=withContext(Dispatchers.IO){db().dao().appointments()}
    // --- Confirmed past periods and record annotations (REQUIREMENTS §35/35a). Append-only; records are never changed. ---
    suspend fun historyPeriods()=withContext(Dispatchers.IO){db().dao().historyPeriods()}
    suspend fun annotations()=withContext(Dispatchers.IO){db().dao().annotations()}
    private suspend fun appendPeriod(dao:NotesDao,row:HistoryPeriodEntity):Long {
        HistoryPeriods.validate(row);requireNotNull(dao.medication(row.medication_id))
        val all=dao.historyPeriods()
        val previous=all.filter{it.period_key==row.period_key}.maxByOrNull{it.revision}
        require(row.revision==(previous?.revision ?: 0)+1 && (previous==null || previous.medication_id==row.medication_id))
        if(row.state==HistoryPeriods.CONFIRMED && row.kind !in listOf(HistoryPeriods.DELETED,HistoryPeriods.STOP)) {
            val versions=dao.regimens();val (from,until)=periodBounds(row,versions)
            // Two confirmed periods of one medication never overlap.
            // Two confirmed periods of one medication never overlap; nor do two user edits (§37b rule c, same medicine only).
            val peers=if(row.kind==HistoryPeriods.CONFIRMED)HistoryPeriods.confirmed(all) else HistoryPeriods.userEdits(all).filter{it.kind in listOf(HistoryPeriods.PERIOD,HistoryPeriods.FILL)}
            require(peers.none{other->other.period_key!=row.period_key && other.medication_id==row.medication_id &&
                periodBounds(other,versions).let{(a,b)->a<(until ?: Instant.MAX) && from<(b ?: Instant.MAX)}}){"Overlapping period"}
        }
        return dao.insertHistoryPeriod(row.copy(id=0))
    }
    private fun periodBounds(row:HistoryPeriodEntity,versions:List<RegimenVersionEntity> = emptyList()):Pair<Instant,Instant?> {
        if(row.kind==HistoryPeriods.PERIOD)return HistoryPeriods.effectiveBounds(row,versions).let{(a,b)->Instant.ofEpochMilli(a) to b?.let(Instant::ofEpochMilli)}
        val exact=HistoryPeriods.exactBounds(row);val zone=ZoneId.of(row.zone)
        return (exact?.first?.let(Instant::ofEpochMilli) ?: LocalDate.parse(row.from_date).atStartOfDay(zone).toInstant()) to
            (if(exact!=null)exact.second?.let(Instant::ofEpochMilli) else row.until_date?.let{LocalDate.parse(it).atStartOfDay(zone).toInstant()})
    }
    private fun current(all:List<HistoryPeriodEntity>,key:String)=requireNotNull(all.filter{it.period_key==key}.maxByOrNull{it.revision})
    /**
     * One timeline edit (REQUIREMENTS §37b): revokes the user edits in [replace], then appends [rows] under one new group
     * key. Records, saved plan versions and confirmed periods are never written. Returns the group key.
     */
    suspend fun editTimeline(replace:List<String>,rows:List<TimelineEditRow>,now:Instant=Instant.now()):String=transaction { dao ->
        require(rows.isNotEmpty())
        // A newly deleted range removes older user periods too. A later user edit may cover an older deletion.
        val implicit=HistoryPeriods.userEdits(dao.historyPeriods()).filter{u->u.kind==HistoryPeriods.PERIOD && u.period_key !in replace && rows.any{r->
            if(r.kind!=HistoryPeriods.DELETED || r.medicationId!=u.medication_id)false else {
                val zone=r.zone;val a=r.exactFromUtc?.let(Instant::ofEpochMilli) ?: r.from.atStartOfDay(zone).toInstant()
                val b=if(r.exactFromUtc!=null)r.exactUntilUtc?.let(Instant::ofEpochMilli) else r.until?.atStartOfDay(zone)?.toInstant()
                val (x,y)=periodBounds(u,dao.regimens());a<(y ?: Instant.MAX) && x<(b ?: Instant.MAX)
            }
        }}
        val survivors=implicit.flatMap{u->
            var pieces=listOf(periodBounds(u,dao.regimens()))
            rows.filter{it.kind==HistoryPeriods.DELETED && it.medicationId==u.medication_id}.forEach{r->
                val a=r.exactFromUtc?.let(Instant::ofEpochMilli) ?: r.from.atStartOfDay(r.zone).toInstant()
                val b=(if(r.exactFromUtc!=null)r.exactUntilUtc?.let(Instant::ofEpochMilli) else r.until?.atStartOfDay(r.zone)?.toInstant()) ?: Instant.MAX
                pieces=pieces.flatMap{(x,y)->val end=y ?: Instant.MAX;if(a>=end || b<=x)listOf(x to y) else buildList{if(x<a)add(x to a);if(b<end)add(b to y)}}
            }
            pieces.map{(a,b)->val z=ZoneId.of(u.zone);val from=a.atZone(z).toLocalDate();val until=b?.atZone(z)?.let{if(it.toLocalTime()==LocalTime.MIDNIGHT)it.toLocalDate() else it.toLocalDate().plusDays(1)}
                TimelineEditRow(HistoryPeriods.PERIOD,u.medication_id,HistoryPeriods.readStandard(u.standard_json),from,until,z,u.identity_json,
                    exactFromUtc=a.toEpochMilli(),exactUntilUtc=b?.toEpochMilli(),evidenceJson=u.evidence_json)}
        }
        val replacementKeys=(replace+implicit.map{it.period_key}).distinct()
        val group=java.util.UUID.randomUUID().toString()
        replacementKeys.forEach{key->val c=current(dao.historyPeriods(),key);require(c.kind in HistoryPeriods.USER_KINDS)
            if(c.state==HistoryPeriods.CONFIRMED)appendPeriod(dao,c.copy(id=0,revision=c.revision+1,state=HistoryPeriods.REVOKED,created_utc=now.toEpochMilli()))}
        (survivors+rows).forEach{r->
            require(r.kind in HistoryPeriods.USER_KINDS && (r.standard!=null)==(r.kind in listOf(HistoryPeriods.PERIOD,HistoryPeriods.FILL)))
            val evidence=org.json.JSONObject(r.evidenceJson).put("replaces",org.json.JSONArray(r.replaces ?: replacementKeys)).put("note",r.note)
            if(r.kind==HistoryPeriods.PERIOD && !evidence.has("stated_utc"))evidence.put("stated_utc",now.toEpochMilli())
            r.exactFromUtc?.let{evidence.put("exact_from_utc",it).put("exact_until_utc",r.exactUntilUtc ?: org.json.JSONObject.NULL)}
            val rowGroup=if(r.trash)java.util.UUID.randomUUID().toString() else group
            appendPeriod(dao,HistoryPeriodEntity(period_key=java.util.UUID.randomUUID().toString(),revision=1,state=HistoryPeriods.CONFIRMED,medication_id=r.medicationId,
                identity_json=r.identityJson,standard_json=r.standard?.let(HistoryPeriods::standardJson) ?: "{}",from_date=r.from.toString(),until_date=r.until?.toString(),
                zone=r.zone.id,evidence_json=evidence.toString(),origin=HistoryPeriods.USER_ORIGIN,created_utc=now.toEpochMilli(),kind=r.kind,group_key=rowGroup))
            if(r.trash)Trash.insert(sql(),Trash.PERIOD,rowGroup,r.from.toString(),org.json.JSONObject(),now.toEpochMilli())
        };group
    }
    /** §40: convert a build-24 display atomically; old revisions and trash references remain intact. */
    suspend fun migrateTimeline(replace:List<String>,rows:List<TimelineEditRow>,now:Instant=Instant.now())=transaction { dao ->
        replace.distinct().forEach{key->val c=current(dao.historyPeriods(),key)
            if(c.state==HistoryPeriods.CONFIRMED)appendPeriod(dao,c.copy(id=0,revision=c.revision+1,state=HistoryPeriods.REVOKED,created_utc=now.toEpochMilli()))}
        if(rows.isNotEmpty())editTimeline(emptyList(),rows,now)
    }
    /** Undoes one edit: its rows are revoked and the user edits it replaced come back. Nothing is deleted. */
    suspend fun undoTimelineEdit(group:String,now:Instant=Instant.now())=transaction { dao ->
        val all=dao.historyPeriods()
        val active=HistoryPeriods.userEdits(all).filter{it.group_key==group};require(active.isNotEmpty()){"Nothing to undo"}
        val replaced=active.flatMap{r->org.json.JSONObject(r.evidence_json).optJSONArray("replaces")?.let{a->(0 until a.length()).map(a::getString)}.orEmpty()}.distinct()
        active.forEach{appendPeriod(dao,it.copy(id=0,revision=it.revision+1,state=HistoryPeriods.REVOKED,created_utc=now.toEpochMilli()))}
        replaced.forEach{key->
            val rows=dao.historyPeriods().filter{it.period_key==key};val last=rows.maxBy{it.revision}
            if(last.state==HistoryPeriods.REVOKED)rows.filter{it.state==HistoryPeriods.CONFIRMED}.maxByOrNull{it.revision}?.let{
                appendPeriod(dao,it.copy(id=0,revision=last.revision+1,created_utc=now.toEpochMilli()))}
        }
    }
    // --- Recycle bin (REQUIREMENTS §39) ---
    suspend fun trash()=withContext(Dispatchers.IO){db().dao().trash().filter{it.state==Trash.TRASHED}}
    /** Deleting a period: the deletion edit goes to the bin, so it can be restored or purged. */
    suspend fun deletePeriod(replace:List<String>,rows:List<TimelineEditRow>,now:Instant=Instant.now())=transaction { _ ->
        val group=editTimeline(replace,rows.map{it.copy(trash=it.kind==HistoryPeriods.DELETED)},now)
        val latest=sql().query("SELECT ref FROM trash_item WHERE kind='PERIOD' AND deleted_utc=? ORDER BY id DESC LIMIT 1",arrayOf(now.toEpochMilli())).use{if(it.moveToFirst())it.getString(0) else group};latest
    }
    private fun trashItem(dao:NotesDao,id:Long)=sql().query("SELECT * FROM trash_item WHERE id=?",arrayOf(id)).use{c->require(c.moveToFirst()){"No such item"}
        TrashItemEntity(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getString(5),c.getString(6))}
    /** Legacy recycle-bin operations still name pre-conversion keys. Retire their migration-only descendants. */
    private suspend fun retireMigratedDescendants(dao:NotesDao,keys:Set<String>,now:Instant) {
        HistoryPeriods.userEdits(dao.historyPeriods()).filter{r->JSONObject(r.evidence_json).optJSONArray("migrated_from")?.let{a->(0 until a.length()).any{a.getString(it) in keys}}==true}.forEach{r->
            appendPeriod(dao,r.copy(id=0,revision=r.revision+1,state=HistoryPeriods.REVOKED,created_utc=now.toEpochMilli()))
        }
    }
    private suspend fun restoreLegacyBackground(dao:NotesDao,deleted:List<HistoryPeriodEntity>,now:Instant) {
        val all=dao.historyPeriods()
        val keys=HistoryPeriods.userEdits(all).flatMap{r->JSONObject(r.evidence_json).optJSONArray("migrated_from")?.let{a->(0 until a.length()).map(a::getString)}.orEmpty()}.distinct().filter{k->
            val source=all.filter{it.period_key==k && it.state==HistoryPeriods.CONFIRMED}.maxByOrNull{it.revision}
            source!=null && deleted.any{d->source.medication_id==d.medication_id && periodBounds(source).let{(a,b)->periodBounds(d).let{(x,y)->a<=x && (b ?: Instant.MAX)>=(y ?: Instant.MAX)}}}
        }.toSet()
        retireMigratedDescendants(dao,keys,now)
        keys.forEach{k->val rows=dao.historyPeriods().filter{it.period_key==k};val last=rows.maxBy{it.revision}
            if(last.state==HistoryPeriods.REVOKED)rows.filter{it.state==HistoryPeriods.CONFIRMED}.maxByOrNull{it.revision}?.let{r->
                appendPeriod(dao,r.copy(id=0,revision=last.revision+1,created_utc=now.toEpochMilli()))}}
    }
    /** Puts an item back exactly as it was. A daily value that was re-entered meanwhile goes to the bin in its place. */
    suspend fun restoreTrash(id:Long,now:Instant=Instant.now())=transaction { dao ->
        val t=trashItem(dao,id);require(t.state==Trash.TRASHED)
        when(t.kind) {
            in Trash.SNAPSHOT_KINDS->{
                val payload=org.json.JSONObject(t.payload_json);val row=payload.getJSONObject("tables").let{tb->tb.getJSONArray(payload.getJSONArray("order").getString(0)).getJSONObject(0)}
                when(t.kind) {
                    Trash.SCORE->setScore(LocalDate.parse(row.getString("date")),row.getLong("item_id"),null,now)
                    Trash.NOTE->setNote(LocalDate.parse(row.getString("date")),"",now)
                    Trash.SYMPTOM->if(dao.symptomChecks(row.getString("date"),row.getString("date")).any{it.group_id==row.getString("group_id")})setSymptomCheck(LocalDate.parse(row.getString("date")),row.getString("group_id"),false)
                }
                Trash.reinsert(sql(),payload)
            }
            Trash.RECORD->{val r=requireNotNull(dao.recordById(t.ref.toLong()));require(r.deleted_at_utc!=null)
                val back=r.copy(deleted_at_utc=null,revision=r.revision+1);dao.updateRecord(back)
                if(back.status in listOf("ON_TIME","LATE"))SupplyLedger.allocate(dao,dao.recordById(back.id)!!)}
            Trash.PERIOD->{
                val all=dao.historyPeriods();val deleted=HistoryPeriods.userEdits(all).filter{it.group_key==t.ref}
                val users=HistoryPeriods.userEdits(all).filter{it.kind==HistoryPeriods.PERIOD && it.group_key!=t.ref};val versions=dao.regimens()
                if(deleted.any{d->val (a,b)=periodBounds(d,versions);users.any{u->(u.medication_id==d.medication_id) && periodBounds(u,versions).let{(x,y)->a<(y ?: Instant.MAX) && x<(b ?: Instant.MAX)}}})throw PeriodRestoreConflict()
                if(deleted.any{JSONObject(it.evidence_json).has("restore_rows")}) {
                    val back=deleted.flatMap{d->JSONObject(d.evidence_json).optJSONArray("restore_rows")?.let{a->(0 until a.length()).map(a::getJSONObject)}.orEmpty()}
                    deleted.forEach{d->appendPeriod(dao,d.copy(id=0,revision=d.revision+1,state=HistoryPeriods.REVOKED,created_utc=now.toEpochMilli()))}
                    back.forEach{p->
                        val a=Instant.ofEpochMilli(p.getLong("from_utc"));val b=if(p.isNull("until_utc"))null else Instant.ofEpochMilli(p.getLong("until_utc"))
                        val original=current(dao.historyPeriods(),p.getString("period_key"));require(original.state==HistoryPeriods.REVOKED)
                        val zone=ZoneId.of(original.zone);val from=a.atZone(zone).toLocalDate()
                        val until=b?.atZone(zone)?.let{if(it.toLocalTime()==LocalTime.MIDNIGHT)it.toLocalDate() else it.toLocalDate().plusDays(1)}
                        val oldBounds=periodBounds(original,versions)
                        val restored=if(oldBounds==(a to b))original else original.copy(from_date=from.toString(),until_date=until?.toString(),
                            evidence_json=JSONObject(original.evidence_json).put("exact_from_utc",a.toEpochMilli()).put("exact_until_utc",b?.toEpochMilli() ?: JSONObject.NULL).toString())
                        appendPeriod(dao,restored.copy(id=0,revision=original.revision+1,state=HistoryPeriods.CONFIRMED,created_utc=now.toEpochMilli()))
                    }
                } else {restoreLegacyBackground(dao,deleted,now);undoTimelineEdit(t.ref,now)}
            }
            Trash.CORRECTION->{
                val all=dao.historyPeriods();val keys=all.filter{it.group_key==t.ref}.map{it.period_key}.distinct()
                val replaced=all.filter{it.group_key==t.ref}.flatMap{r->org.json.JSONObject(r.evidence_json).optJSONArray("replaces")?.let{a->(0 until a.length()).map(a::getString)}.orEmpty()}.distinct()
                val permitted=HistoryPeriods.userEdits(all).filter{u->u.period_key in replaced || JSONObject(u.evidence_json).optJSONArray("migrated_from")?.let{a->(0 until a.length()).any{a.getString(it) in replaced}}==true}.map{it.period_key}.toSet()
                val restoring=keys.mapNotNull{k->all.filter{it.period_key==k && it.state==HistoryPeriods.CONFIRMED}.maxByOrNull{it.revision}}.filter{it.kind in listOf(HistoryPeriods.PERIOD,HistoryPeriods.FILL)}
                if(restoring.any{r->val (a,b)=periodBounds(r,dao.regimens());HistoryPeriods.userEdits(all).any{u->u.kind==HistoryPeriods.PERIOD && u.period_key !in permitted && u.medication_id==r.medication_id &&
                    periodBounds(u,dao.regimens()).let{(x,y)->a<(y ?: Instant.MAX) && x<(b ?: Instant.MAX)}}})throw PeriodRestoreConflict()
                retireMigratedDescendants(dao,replaced.toSet(),now)
                replaced.forEach{key->current(dao.historyPeriods(),key).takeIf{it.state==HistoryPeriods.CONFIRMED}?.let{appendPeriod(dao,it.copy(id=0,revision=it.revision+1,state=HistoryPeriods.REVOKED,created_utc=now.toEpochMilli()))}}
                keys.forEach{key->val rows=dao.historyPeriods().filter{it.period_key==key};val last=rows.maxBy{it.revision}
                    if(last.state==HistoryPeriods.REVOKED)rows.filter{it.state==HistoryPeriods.CONFIRMED}.maxByOrNull{it.revision}?.let{appendPeriod(dao,it.copy(id=0,revision=last.revision+1,created_utc=now.toEpochMilli()))}}
            }
        }
        AttachmentIdentity.restored(sql(),t)
        sql().execSQL("DELETE FROM trash_item WHERE id=?",arrayOf(id))
    }
    private fun purgeKeys(keys:Collection<String>) {
        if(keys.isEmpty())return
        keys.forEach{sql().execSQL("INSERT OR IGNORE INTO purge_permit(period_key) VALUES (?)",arrayOf(it))}
        keys.forEach{sql().execSQL("DELETE FROM history_period_revision WHERE period_key=?",arrayOf(it))}
        sql().execSQL("DELETE FROM purge_permit")
    }
    /**
     * Deletes an item for good. The user's own content is removed from the database. A record still in the stock
     * ledger, or a period deletion over plans the system made ([systemUnderneath]), is hidden for good instead.
     * Returns true when it was really removed, false when it was hidden.
     */
    suspend fun purgeTrash(id:Long,systemUnderneath:Boolean=true):Boolean=transaction { dao ->
        val t=trashItem(dao,id);require(t.state==Trash.TRASHED)
        val removed=when(t.kind) {
            in Trash.SNAPSHOT_KINDS->true
            Trash.RECORD->{val rid=t.ref.toLong()
                val inLedger=sql().query("SELECT 1 FROM supply_transaction WHERE dose_record_id=? LIMIT 1",arrayOf(rid)).use{it.moveToFirst()}
                if(inLedger)false else {sql().execSQL("DELETE FROM record_annotation WHERE record_id=?",arrayOf(rid));sql().execSQL("DELETE FROM dose_record WHERE id=?",arrayOf(rid));true}}
            Trash.PERIOD->{val all=dao.historyPeriods();val group=all.filter{it.group_key==t.ref}
                val replaced=group.flatMap{r->org.json.JSONObject(r.evidence_json).optJSONArray("replaces")?.let{a->(0 until a.length()).map(a::getString)}.orEmpty()}
                    .filter{k->all.any{it.period_key==k && it.kind in HistoryPeriods.USER_KINDS}}
                purgeKeys(replaced.distinct())
                if(systemUnderneath)false else {purgeKeys(group.map{it.period_key}.distinct());true}}
            Trash.CORRECTION->{purgeKeys(dao.historyPeriods().filter{it.group_key==t.ref}.map{it.period_key}.distinct());true}
            else->error("Unknown kind")
        }
        AttachmentIdentity.purged(sql(),t)
        if(removed)sql().execSQL("DELETE FROM trash_item WHERE id=?",arrayOf(id)) else sql().execSQL("UPDATE trash_item SET state='HIDDEN' WHERE id=?",arrayOf(id))
        removed
    }
    /** The user's own "extra dose" statement on a taken record. */
    suspend fun setExtra(recordId:Long,extra:Boolean,now:Instant=Instant.now())=transaction { dao ->
        val r=requireNotNull(dao.recordById(recordId));require(r.deleted_at_utc==null && r.status in listOf("ON_TIME","LATE"))
        if(extra)dao.insertAnnotation(RecordAnnotationEntity(recordId,HistoryPeriods.EXTRA,now.toEpochMilli())) else dao.deleteAnnotation(recordId,HistoryPeriods.EXTRA)
    }
    suspend fun profile(id:Long)=withContext(Dispatchers.IO){db().dao().profile(id)}
    suspend fun rules()=withContext(Dispatchers.IO){db().dao().rules()}
    private suspend fun ruleModels(dao:NotesDao)=dao.rules().map { r ->
        ScheduleRule(r.id,r.medication_id,RuleKind.valueOf(r.kind),r.interval,r.anchor_local?.let(LocalDate::parse),r.anchor_utc?.let(Instant::ofEpochMilli),ZoneId.of(r.anchor_zone),
            Instant.ofEpochMilli(r.effective_from_utc),r.effective_until_utc?.let(Instant::ofEpochMilli),Instant.ofEpochMilli(r.missed_tracking_from_utc),
            dao.times(r.id).map{RuleTime(LocalTime.parse(it.local_time),it.dose_override)},DayOfWeek.entries.filter{r.weekday_mask and (1 shl (it.value-1))!=0}.toSet(),r.dose_snapshot,r.soon_snapshot,r.late_snapshot)
    }
    private fun OverrideEntity.model()=SlotOverride(slot_key,rescheduled_utc?.let(Instant::ofEpochMilli),rescheduled_zone?.let(ZoneId::of),dose_override,skipped)
    private fun RecordEntity.model()=DoseRecord(slot_key,medication_id,if(unconfirmed) DoseStatus.UNCONFIRMED else DoseStatus.valueOf(status),taken_utc?.let(Instant::ofEpochMilli),taken_zone?.let(ZoneId::of),actual_dose,site,origin.startsWith("IMPORT_"),deleted_at_utc!=null,scheduled_utc?.let(Instant::ofEpochMilli),scheduled_zone?.let(ZoneId::of),planned_dose,late_after_minutes_snapshot,rule_version_id)
    private fun RetainedEntity.model()=Slot(slot_key,rule_id,medication_id,Instant.ofEpochMilli(original_utc),Instant.ofEpochMilli(at_utc),ZoneId.of(zone),dose,soon_minutes,late_minutes,Instant.ofEpochMilli(tracking_from_utc))
    private suspend fun timeline(dao:NotesDao,now:Instant,from:Instant,to:Instant,zone:ZoneId):List<TimelineEntry> {
        val meds=dao.medications().associateBy{it.id}
        val rules=ruleModels(dao).filter { meds[it.medicationId]?.needs_review==null }
        val retracted=dao.deletedRecords().filter{it.status=="SKIPPED" && SkipSlotEvidence.has(it)}.mapNotNull{it.slot_key}.toSet()
        return Timeline.build(rules,dao.overrides().map{it.model()},dao.records().map{it.model()},from,to,now,zone,dao.retained().map{it.model()},retracted)
    }
    private suspend fun reconcile(dao:NotesDao,now:Instant,zone:ZoneId) {
        val from=dao.rules().minOfOrNull{it.missed_tracking_from_utc}?.let(Instant::ofEpochMilli) ?: now
        if(from>now)return
        timeline(dao,now,from,now.plusMillis(1),zone).filter{it.state==SlotState.UNCONFIRMED}.forEach { entry ->
            if(dao.records().none{it.slot_key==entry.slot.key}) dao.record(recordFor(dao,entry.slot,"MISSED",origin="AUTO_MISSED"))
        }
    }
    suspend fun calendar(now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault(),displayFrom:LocalDate=LocalDate.now(zone)):List<TimelineEntry> = transaction { dao ->
        reconcile(dao,now,zone)
        val from=dao.rules().minOfOrNull{it.missed_tracking_from_utc}?.let(Instant::ofEpochMilli) ?: now
        val window=timeline(dao,now,displayFrom.atStartOfDay(zone).toInstant(),displayFrom.plusDays(14).atStartOfDay(zone).toInstant(),zone)
        val unfinished=timeline(dao,now,minOf(from,now),now.plusMillis(1),zone)
            .filter{it.state in listOf(SlotState.PENDING,SlotState.SOON,SlotState.OVERDUE)}
        (window+unfinished).distinctBy{it.slot.key}.sortedWith(compareBy<TimelineEntry>{it.slot.at}.thenBy{it.slot.key})
    }
    /** Pending delays made before first unlock are reconciled without domain data in DPS. */
    suspend fun reconcileCache(cache:AlarmCache)=transaction { dao ->
        cache.alarms.forEach { alarm ->
            val source=dao.mappings().firstOrNull{it.opaque_id==alarm.opaqueId && it.generation==cache.generation} ?: return@forEach
            if(alarm.consumed)dao.markSent(source.opaque_id)
            else if(alarm.triggerMillis!=source.trigger_utc) {
                dao.markSent(source.opaque_id)
                val identity="snooze:${source.opaque_id}"
                if(dao.mappings().none{it.identity==identity})dao.mapping(ReminderMappingEntity(java.util.UUID.randomUUID().toString(),cache.generation,identity,alarm.triggerMillis,false))
            }
        }
    }
    /** Resolve only in credential storage; a stale notification never follows a changed schedule. */
    suspend fun reminderSlot(id:String,generation:String?=null):Slot? {
        val mappings=transaction{it.mappings()}
        var source=mappings.firstOrNull{it.opaque_id==id && (generation==null || it.generation==generation)} ?: return null
        val visited=mutableSetOf<String>()
        while(source.identity.startsWith("snooze:")) {
            if(!visited.add(source.opaque_id))return null
            source=mappings.firstOrNull{it.opaque_id==source.identity.removePrefix("snooze:")} ?: return null
        }
        val type=source.identity.substringAfterLast(':')
        if(type !in listOf("SOON","DUE","LATE"))return null
        val key=source.identity.substringBeforeLast(':')
        val entry=calendar().firstOrNull{it.slot.key==key} ?: return null
        if(entry.state in listOf(SlotState.SKIPPED,SlotState.ON_TIME,SlotState.LATE))return null
        val trigger=when(type){"SOON"->entry.slot.at.minusSeconds(entry.slot.soonMinutes.toLong()*60);"LATE"->entry.slot.at.plusSeconds(entry.slot.lateMinutes.toLong()*60+1);else->entry.slot.at}
        return entry.slot.takeIf{trigger.toEpochMilli()==source.trigger_utc}
    }
    suspend fun saveMedication(value:MedicationEntity,ester:String?,kind:RuleKind,interval:Int,times:List<LocalTime>,weekdays:Set<DayOfWeek>,now:Instant=Instant.now(),pk:ProfileEntity?=null,resizeContainers:Boolean=false,timeDoses:List<Double?>?=null):Long=transaction { dao ->
        RegimenHistory.seed(db().openHelper.writableDatabase)
        val effectiveNow=Instant.ofEpochMilli(now.toEpochMilli())
        require(value.name.isNotBlank() && value.dose_per_intake.isFinite() && value.dose_per_intake>0)
        val zone=ZoneId.systemDefault();reconcile(dao,effectiveNow,zone)
        val id=if(value.id==0L)dao.insertMedication(value) else {dao.updateMedication(value);value.id}
        if(resizeContainers) {
            val measured=dao.measuredContainerIds().toSet()
            resizableContainers(dao.containers().filter{it.id !in measured},id,value.container_capacity).forEach{dao.setContainerCapacity(it.id,value.container_capacity)}
        }
        val cut=effectiveNow.toEpochMilli()
        val newProfile=profileFor(id,value,ester,pk)
        val snapshot=MedicationSnapshot.encode(value,newProfile)
        // Editing only metadata (name, stock, notifications, profile) keeps the plan: no new version, so no slot can appear twice.
        val current=dao.rules().filter { it.medication_id==id && it.effective_until_utc==null }.singleOrNull()
        val oldTimes=current?.let{dao.times(it.id)}.orEmpty()
        require(timeDoses==null || timeDoses.size==times.size)
        require(timeDoses.orEmpty().all{it==null || it.isFinite() && it>0})
        fun clock(t:LocalTime)=t.withNano(0).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        if(kind!=RuleKind.EVERY_N_HOURS && timeDoses==null && oldTimes.any{it.dose_override!=null})
            require(times.map(::clock).toSet()==oldTimes.map{it.local_time}.toSet()){"Dose correspondence is required for a non-uniform plan edit"}
        val wantedDoses=timeDoses ?: times.map{t->oldTimes.firstOrNull{it.local_time==clock(t)}?.dose_override}
        require(times.distinct().size==times.size)
        val desiredTimes=times.mapIndexed{i,t->TimeEntity(rule_id=0,local_time=clock(t),dose_override=wantedDoses[i])}
        if(value.active && current!=null && samePlan(dao,current,value,kind,interval,times,weekdays,zone,snapshot,wantedDoses)) {
            if(current.config_snapshot!=snapshot) dao.updateRule(current.copy(config_snapshot=snapshot))
            saveProfile(dao,id,value,ester,pk)
            RegimenHistory.changed(dao,id,cut)
            return@transaction id
        }
        val cadenceUnchanged=current?.let{r->
            if(r.kind!=kind.name || r.interval!=interval || r.effective_zone!=zone.id) false else {
                val proposed=r.copy(weekday_mask=weekdays.sumOf{1 shl (it.value-1)},dose_snapshot=value.dose_per_intake,config_snapshot=snapshot)
                val newTimes=if(kind==RuleKind.EVERY_N_HOURS)emptyList() else desiredTimes.map{it.copy(rule_id=r.id)}
                RegimenDefinition.from(r,dao.times(r.id)).signature()==RegimenDefinition.from(proposed,newTimes).signature()
            }
        } ?: false
        // Preserve pending/overridden slots born before the cutover without persisting future PENDING rows.
        val old=dao.rules().filter { it.medication_id==id && it.effective_until_utc==null }
        if(old.isNotEmpty()) {
            val earliest=old.minOf{it.effective_from_utc}
            val keep=timeline(dao,effectiveNow,Instant.ofEpochMilli(earliest),effectiveNow.plusSeconds(86400*14),zone)
                .filter{it.slot.medicationId==id && it.slot.original<effectiveNow && it.state in listOf(SlotState.PENDING,SlotState.SOON,SlotState.OVERDUE)}
            keep.forEach{dao.retain(it.slot.retained())}
            val models=ruleModels(dao).filter{r->old.any{it.id==r.id}}
            val retainedKeys=dao.retained().map{it.slot_key}.toSet()
            dao.overrides().filter{it.medication_id==id}.forEach { change ->
                models.firstNotNullOfOrNull{ScheduleEngine.resolve(it,change.slot_key,zone)}?.takeIf{it.original<effectiveNow && it.key !in retainedKeys}?.let{dao.retain(it.retained())}
            }
            old.forEach{require(cut>it.effective_from_utc);dao.updateRule(it.copy(effective_until_utc=cut))}
        }
        saveProfile(dao,id,value,ester,pk)
        if(value.active) {
            val soon=requireNotNull(value.soon_alert_minutes);val late=requireNotNull(value.late_after_minutes)
            val anchorDate=if(cadenceUnchanged)current!!.anchor_local else if(kind==RuleKind.EVERY_N_HOURS)null else effectiveNow.atZone(zone).toLocalDate().toString()
            val mask=weekdays.sumOf{1 shl (it.value-1)}
            val r=RuleEntity(medication_id=id,kind=kind.name,interval=interval,weekday_mask=mask,anchor_local=anchorDate,anchor_zone=zone.id,
                anchor_utc=if(cadenceUnchanged)current!!.anchor_utc else if(kind==RuleKind.EVERY_N_HOURS)cut else null,effective_from_utc=cut,effective_until_utc=null,effective_zone=zone.id,
                missed_tracking_from_utc=cut,dose_snapshot=value.dose_per_intake,soon_snapshot=soon,late_snapshot=late,
                config_snapshot=snapshot)
            require(interval in 1..36500); require(kind!=RuleKind.WEEKLY || mask>0)
            require(kind==RuleKind.EVERY_N_HOURS || times.isNotEmpty())
            val rid=dao.rule(r)
            if(kind!=RuleKind.EVERY_N_HOURS)desiredTimes.forEach{dao.time(it.copy(rule_id=rid))}
        }
        RegimenHistory.changed(dao,id,cut);id
    }
    private fun profileFor(id:Long,value:MedicationEntity,ester:String?,pk:ProfileEntity?):ProfileEntity? =
        if(value.molecule=="E2" && value.route!=null && ester!=null)
            ProfileEntity(id,ester,when(value.route){"ORAL"->"oral";"SUBLINGUAL"->"sublingual";"GEL"->"gel";"PATCH"->"patchApply";"INJECTION"->"injection";else->error("Unsupported estradiol route")},
                pk?.sl_tier,pk?.gel_product_id,pk?.gel_site,pk?.gel_area_cm2,pk?.patch_release_ug_day)
        else null
    private suspend fun saveProfile(dao:NotesDao,id:Long,value:MedicationEntity,ester:String?,pk:ProfileEntity?) {
        profileFor(id,value,ester,pk)?.let{dao.profile(it)}
    }
    /** True when the edit leaves the plan as it is: same kind, interval, weekdays, times, dose, alert windows, zone and compound (the name may differ). */
    private suspend fun samePlan(dao:NotesDao,r:RuleEntity,value:MedicationEntity,kind:RuleKind,interval:Int,times:List<LocalTime>,weekdays:Set<DayOfWeek>,zone:ZoneId,snapshot:String,timeDoses:List<Double?>):Boolean {
        val mask=weekdays.sumOf{1 shl (it.value-1)}
        val ruleTimes=dao.times(r.id)
        val wanted=if(kind==RuleKind.EVERY_N_HOURS) emptySet() else times.map{it.withNano(0).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))}.toSet()
        fun compound(json:String)=JSONObject(json).let{o->listOf("molecule","route","unit","ester").map{k->if(o.isNull(k))null else o.opt(k)?.toString()}}
        return r.kind==kind.name && r.interval==interval && r.weekday_mask==mask && r.effective_zone==zone.id &&
            r.dose_snapshot==value.dose_per_intake && r.soon_snapshot==value.soon_alert_minutes && r.late_snapshot==value.late_after_minutes &&
            (kind==RuleKind.EVERY_N_HOURS || ruleTimes.associate{it.local_time to it.dose_override}==times.mapIndexed{i,t->t.withNano(0).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) to timeDoses[i]}.toMap()) && ruleTimes.map{it.local_time}.toSet()==wanted && ruleTimes.size==wanted.size &&
            compound(r.config_snapshot)==compound(snapshot) &&
            MedicationSnapshot.decode(r.config_snapshot,r.medication_id)?.profile==MedicationSnapshot.decode(snapshot,r.medication_id)?.profile
    }
    /** Explicit user confirmation, restricted to missing and compatible saved fields. No inventory mutation. */
    suspend fun confirmHistoricalContext(medication:MedicationEntity,profile:ProfileEntity?,from:LocalDate,to:LocalDate,now:Instant=Instant.now()):Int=transaction { dao ->
        require(!to.isBefore(from));val candidate=MedicationSnapshot.encode(medication,profile);val zone=ZoneId.systemDefault()
        val rows=dao.records().filter{it.medication_id==medication.id && it.deleted_at_utc==null && it.status in listOf("ON_TIME","LATE") && it.taken_utc?.let{t->Instant.ofEpochMilli(t).atZone(zone).toLocalDate() in from..to}==true && HistoricalContext.incomplete(it)}
        val snapshots=dao.rules().associate{it.id to it.config_snapshot}
        require(rows.isNotEmpty());val contexts=rows.map{r->r.id to HistoricalContext.confirmed(r.config_snapshot,candidate,"USER_CONFIRMED",now,HistoricalContext.resolved(r,snapshots))}
        contexts.forEach{(id,json)->dao.recordContext(id,json)};contexts.size
    }
    suspend fun removeMedication(id:Long,now:Instant=Instant.now())=transaction { dao ->
        RegimenHistory.seed(db().openHelper.writableDatabase)
        reconcile(dao,now,ZoneId.systemDefault())
        val m=dao.medication(id);dao.updateMedication(m.copy(active=false,notifications_on=false))
        dao.rules().filter{it.medication_id==id && it.effective_until_utc==null}.forEach{dao.updateRule(it.copy(effective_until_utc=maxOf(now.toEpochMilli(),it.effective_from_utc+1)))}
        RegimenHistory.changed(dao,id,dao.rules().filter{it.medication_id==id}.maxOfOrNull{it.effective_until_utc ?: now.toEpochMilli()} ?: now.toEpochMilli())
    }
    private fun Slot.retained()=RetainedEntity(key,ruleId,medicationId,original.toEpochMilli(),original.toEpochMilli(),originalZone.id,originalDose,soonMinutes,lateMinutes,trackingFrom.toEpochMilli())
    private suspend fun recordFor(dao:NotesDao,slot:Slot,status:String,taken:Instant?=null,dose:Double?=null,origin:String="APP"):RecordEntity {
        val m=dao.medication(slot.medicationId)
        val snapshot=dao.rules().single { it.id==slot.ruleId }.config_snapshot
        return RecordEntity(medication_id=m.id,rule_version_id=slot.ruleId,slot_key=slot.key,scheduled_utc=slot.at.toEpochMilli(),scheduled_zone=slot.zone.id,
            planned_dose=slot.dose,late_after_minutes_snapshot=slot.lateMinutes,taken_utc=taken?.toEpochMilli(),taken_zone=if(taken==null)null else ZoneId.systemDefault().id,
            actual_dose=dose,status=status,origin=origin,revision=1,config_snapshot=snapshot)
    }
    /** No inferred matching: the user chooses an unfinished same-medication slot on the intake's local day. */
    suspend fun importedCandidates(id:Long,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault()):List<TimelineEntry> = transaction { dao ->
        val record=dao.recordById(id) ?: error("Record missing")
        require(record.deleted_at_utc==null && record.origin.startsWith("IMPORT_") && record.slot_key==null && record.scheduled_utc==null && record.taken_utc!=null)
        val day=Instant.ofEpochMilli(record.taken_utc).atZone(zone).toLocalDate()
        reconcile(dao,now,zone)
        timeline(dao,now,day.atStartOfDay(zone).toInstant(),day.plusDays(1).atStartOfDay(zone).toInstant(),zone)
            .filter{it.slot.medicationId==record.medication_id && it.state !in listOf(SlotState.ON_TIME,SlotState.LATE,SlotState.SKIPPED)}
    }
    /** Attach schedule metadata only. Imported intake data and the stock ledger remain untouched. */
    suspend fun linkImported(id:Long,key:String,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault())=transaction { dao ->
        val record=dao.recordById(id) ?: error("Record missing")
        require(record.deleted_at_utc==null && record.origin.startsWith("IMPORT_") && record.taken_utc!=null)
        if(record.slot_key==key)return@transaction
        val slot=importedCandidates(id,now,zone).single{it.slot.key==key}.slot
        val existing=dao.records().singleOrNull{it.slot_key==key}
        require(existing==null || existing.status=="MISSED")
        if(existing!=null)dao.updateRecord(existing.copy(deleted_at_utc=now.toEpochMilli(),revision=existing.revision+1))
        val taken=Instant.ofEpochMilli(record.taken_utc)
        val status=if(taken>slot.at.plusSeconds(slot.lateMinutes.toLong()*60)) "LATE" else "ON_TIME"
        dao.updateRecord(record.copy(rule_version_id=slot.ruleId,slot_key=key,scheduled_utc=slot.at.toEpochMilli(),scheduled_zone=slot.zone.id,
            planned_dose=slot.dose,late_after_minutes_snapshot=slot.lateMinutes,status=status,revision=record.revision+1))
    }
    suspend fun complete(slot:Slot,taken:Instant,dose:Double,site:String?=null)=transactionWithSql { dao, raw ->
        val existing=dao.records().singleOrNull{it.slot_key==slot.key}
        if(existing?.status in listOf("ON_TIME","LATE"))return@transactionWithSql
        require(existing?.status!="SKIPPED");require(dose.isFinite()&&dose>0)
        val status=ScheduleEngine.complete(slot,taken,ZoneId.systemDefault(),dose).status.name
        val result=recordFor(dao,slot,status,taken,dose).copy(unallocated_supply_amount=dose,site=site?.takeIf{it.isNotBlank()})
        val id=if(existing==null)dao.record(result) else {dao.updateRecord(result.copy(id=existing.id,revision=existing.revision+1,config_snapshot=existing.config_snapshot));existing.id}
        val remaining=SupplyLedger.allocate(dao,dao.recordById(id)!!)

        ReferenceStore.captureContext(raw,dao.recordById(id)!!)
        remaining
    }
    /**
     * Batch backfill ("batch add" in HRT tracker): one intake per day in [from]..[to] at each of [times], past instants only.
     * An open scheduled slot at exactly that instant is completed; otherwise an unscheduled intake is added, unless the
     * medication already has an intake within an hour of it. History only: no stock is deducted. Returns the count added.
     */
    suspend fun backfill(medicationId:Long,from:LocalDate,to:LocalDate,times:List<LocalTime>,dose:Double,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault())=transaction { dao ->
        require(dose.isFinite()&&dose>0&&!to.isBefore(from)&&times.isNotEmpty()&&java.time.temporal.ChronoUnit.DAYS.between(from,to)<=BACKFILL_MAX_DAYS)
        reconcile(dao,now,zone)
        val m=dao.medication(medicationId)
        val instants=generateSequence(from){it.plusDays(1)}.takeWhile{!it.isAfter(to)}
            .flatMap{d->times.distinct().sorted().map{ScheduleEngine.wallInstant(d.atTime(it),zone)}}.filter{it.isBefore(now)}.toList()
        if(instants.isEmpty())return@transaction 0
        val slots=timeline(dao,now,instants.first(),now.plusMillis(1),zone).filter{it.slot.medicationId==medicationId}.associateBy{it.slot.at}
        val records=dao.records().filter{it.medication_id==medicationId&&it.deleted_at_utc==null}.toMutableList()
        val taken=records.mapNotNull{it.taken_utc}.toMutableList()
        val snap=MedicationSnapshot.encode(m,dao.profile(medicationId))
        var added=0
        instants.forEach { t ->
            val slot=slots[t]?.slot
            val existing=slot?.let{s->records.singleOrNull{it.slot_key==s.key}}
            if(slot!=null&&existing?.status!in listOf("ON_TIME","LATE","SKIPPED")) {
                val status=ScheduleEngine.complete(slot,t,zone,dose).status.name
                val r=recordFor(dao,slot,status,t,dose)
                if(existing==null)dao.record(r) else dao.updateRecord(r.copy(id=existing.id,revision=existing.revision+1,config_snapshot=existing.config_snapshot))
            } else if(slot==null&&taken.none{kotlin.math.abs(it-t.toEpochMilli())<3_600_000}) {
                dao.record(RecordEntity(medication_id=medicationId,taken_utc=t.toEpochMilli(),taken_zone=zone.id,actual_dose=dose,status="ON_TIME",origin="APP",revision=1,config_snapshot=snap))
            } else return@forEach
            taken+=t.toEpochMilli(); added++
        }
        added
    }
    suspend fun unscheduled(id:Long,taken:Instant,dose:Double,site:String?=null,extra:Boolean=false)=transactionWithSql { dao, raw ->
        require(dose.isFinite()&&dose>0);val m=dao.medication(id)
        val snap=MedicationSnapshot.encode(m,dao.profile(id))
        val rid=dao.record(RecordEntity(medication_id=id,taken_utc=taken.toEpochMilli(),taken_zone=ZoneId.systemDefault().id,actual_dose=dose,unallocated_supply_amount=dose,site=site?.takeIf{it.isNotBlank()},
            status="ON_TIME",origin="APP",revision=1,config_snapshot=snap))
        SupplyLedger.allocate(dao,dao.recordById(rid)!!)
        ReferenceStore.captureContext(raw,dao.recordById(rid)!!)
        if(extra)dao.insertAnnotation(RecordAnnotationEntity(rid,HistoryPeriods.EXTRA,System.currentTimeMillis()))
    }
    suspend fun override(slot:Slot,value:SlotOverride,now:Instant=Instant.now())=transaction { dao ->
        applyOverride(dao,slot,value,now)
        reconcile(dao,now,ZoneId.systemDefault())
    }
    /** Shared override writes; callers decide whether reconciliation is global or limited to their correction. */
    private suspend fun applyOverride(dao:NotesDao,slot:Slot,value:SlotOverride,now:Instant,captureRetraction:Boolean=false) {
        require(slot.key==value.key)
        val record=dao.records().singleOrNull{it.slot_key==slot.key}
        require(record?.status !in listOf("ON_TIME","LATE","MISSED")) { "Edit recorded history instead" }
        // The first explicit skip still has the actual original slot (before a move or dose override).
        // A later SKIPPED timeline entry contains the effective record snapshot and must not replace this evidence.
        if(value.skipped && record==null)dao.retain(slot.retained())
        val previous=dao.overrides().singleOrNull{it.slot_key==slot.key}
        if(value.isDefault)dao.deleteOverride(slot.key) else {
            val next=OverrideEntity(previous?.id ?: 0,slot.medicationId,slot.ruleId,slot.key,value.rescheduled?.toEpochMilli(),value.zone?.id,value.dose,value.skipped)
            if(previous==null)dao.insertOverride(next)else dao.updateOverride(next)
        }
        if(record?.status=="SKIPPED" && !value.skipped)dao.updateRecord(record.copy(deleted_at_utc=now.toEpochMilli(),revision=record.revision+1,
            config_snapshot=if(captureRetraction)SkipSlotEvidence.capture(record.config_snapshot,slot) else record.config_snapshot))
        if(value.skipped && record==null) {
            val skipped=recordFor(dao,value.apply(slot),"SKIPPED")
            dao.record(skipped.copy(config_snapshot=SkipSlotEvidence.capture(skipped.config_snapshot,slot)))
        }
        if(value.skipped && record?.status=="SKIPPED") {
            val skipped=recordFor(dao,value.apply(slot),"SKIPPED")
            dao.updateRecord(skipped.copy(id=record.id,revision=record.revision+1,config_snapshot=SkipSlotEvidence.capture(skipped.config_snapshot,slot,record)))
        }
    }
    /** Undo the user's skip only, preserving schedule versions, original evidence, moves, doses and all stock. */
    suspend fun undoSkipped(expected:RecordEntity,now:Instant=Instant.now())=transaction { dao ->
        val record=dao.recordById(expected.id) ?: throw SkipUndoStale()
        if(record!=expected || record.deleted_at_utc!=null || record.status!="SKIPPED")throw SkipUndoStale()
        val key=record.slot_key ?: throw SkipUndoUnavailable()
        val change=dao.overrides().singleOrNull{it.slot_key==key} ?: throw SkipUndoUnavailable()
        if(!change.skipped || change.medication_id!=record.medication_id || change.rule_version_id!=record.rule_version_id ||
            record.origin!="APP" || record.taken_utc!=null || record.actual_dose!=null ||
            (record.unallocated_supply_amount ?: 0.0)!=0.0 || dao.supplyFor(record.id).isNotEmpty())throw SkipUndoUnavailable()
        val model=ruleModels(dao).singleOrNull{it.id==record.rule_version_id && it.medicationId==record.medication_id} ?: throw SkipUndoUnavailable()
        val evidence=SkipSlotEvidence.read(record)
        if(evidence!=null && evidence.key!=key)throw SkipUndoUnavailable()
        // A legacy moved wall-clock row (even with derived retention) does not prove its original zone.
        // Unmoved records prove their original clock directly; elapsed keys prove their absolute anchor.
        if(evidence==null && change.rescheduled_utc!=null && model.kind!=RuleKind.EVERY_N_HOURS)throw SkipUndoUnavailable()
        val savedRule=dao.rules().single{it.id==model.id}
        val zone=evidence?.zone ?: (if(change.rescheduled_utc==null)record.scheduled_zone else savedRule.anchor_zone)
            ?.let{runCatching{ZoneId.of(it)}.getOrNull()} ?: throw SkipUndoUnavailable()
        val original=ScheduleEngine.resolve(model,key,zone) ?: throw SkipUndoUnavailable()
        if(evidence!=null && original.original!=evidence.at)throw SkipUndoUnavailable()
        val unit=MedicationSnapshot.decode(record.config_snapshot,record.medication_id)?.unit
            ?: MedicationSnapshot.decode(savedRule.config_snapshot,record.medication_id)?.unit ?: throw SkipUndoUnavailable()
        if(unit!=dao.medication(record.medication_id).unit)throw SkipUndoUnitConflict()
        val effective=change.model().apply(original)
        if(effective.at.toEpochMilli()!=record.scheduled_utc || effective.zone.id!=record.scheduled_zone ||
            effective.dose!=record.planned_dose || effective.lateMinutes!=record.late_after_minutes_snapshot)throw SkipUndoUnavailable()
        dao.retain(original.retained())
        applyOverride(dao,original,change.model().copy(skipped=false),now,captureRetraction=true)
        // Same Timeline projection as calendar/reconcile, but this transaction may write only the chosen slot.
        val restored=timeline(dao,now,effective.at,effective.at.plusMillis(1),zone).singleOrNull{it.slot.key==key} ?: throw SkipUndoUnavailable()
        if(restored.state==SlotState.UNCONFIRMED)dao.record(recordFor(dao,restored.slot,"MISSED",origin="AUTO_MISSED"))
    }
    suspend fun currentOverride(key:String)=withContext(Dispatchers.IO){db().dao().overrides().singleOrNull{it.slot_key==key}?.model() ?: SlotOverride(key)}
    /** Explicit past-visit recording; confirmation time is separate from the occurrence and no clinical facts are inferred. */
    suspend fun saveHistoricalVisit(value:AppointmentEntity,expected:AppointmentEntity?=null,now:Instant=Instant.now()):AppointmentEntity=transaction { dao ->
        ZoneId.of(value.at_zone)
        require(value.at_utc<=now.toEpochMilli() && value.remind_minutes_before>=0)
        require(value.type in listOf("ENDO","GP","LAB","PSY","SURGERY","OTHER") || expected?.type==value.type && value.type.isNotBlank())
        val clean=value.copy(location=value.location?.trim()?.takeIf{it.isNotEmpty()},practitioner=value.practitioner?.trim()?.takeIf{it.isNotEmpty()},note=value.note?.trim()?.takeIf{it.isNotEmpty()})
        if(expected==null) {
            require(value.id==0L)
            val inserted=clean.copy(completed_utc=now.toEpochMilli(),remind_minutes_before=0)
            inserted.copy(id=dao.appointment(inserted))
        } else {
            require(value.id==expected.id && expected.id>0)
            val current=dao.appointmentById(expected.id) ?: throw MedicalVisitConflict()
            if(current!=expected)throw MedicalVisitConflict()
            val updated=clean.copy(completed_utc=current.completed_utc ?: now.toEpochMilli())
            dao.updateAppointment(updated);updated
        }
    }
    suspend fun retractHistoricalVisit(expected:AppointmentEntity,now:Instant=Instant.now())=transaction { dao ->
        if(dao.appointmentById(expected.id)!=expected || expected.completed_utc==null || expected.at_utc>now.toEpochMilli())throw MedicalVisitConflict()
        dao.updateAppointment(expected.copy(completed_utc=null))
    }
    /** The existing appointment bin preserves the same ID, questions, answers and immutable exported-pack metadata. */
    suspend fun deleteHistoricalVisit(expected:AppointmentEntity,now:Instant=Instant.now())=transaction { dao ->
        if(dao.appointmentById(expected.id)!=expected || expected.at_utc>now.toEpochMilli())throw MedicalVisitConflict()
        deleteAppointment(expected.id,now)
    }
    suspend fun appointment(value:AppointmentEntity)=saveAppointment(value)
    /** Inserts or edits an appointment; the confirmed-visit time is kept as stored unless changed through [setVisitCompleted]. */
    suspend fun saveAppointment(value:AppointmentEntity)=transaction { dao ->
        ZoneId.of(value.at_zone);require(value.remind_minutes_before>=0)
        val clean=value.copy(location=value.location?.trim()?.takeIf{it.isNotEmpty()},practitioner=value.practitioner?.trim()?.takeIf{it.isNotEmpty()},note=value.note?.trim()?.takeIf{it.isNotEmpty()})
        if(value.id==0L)dao.appointment(clean.copy(completed_utc=null)) else {
            val old=requireNotNull(dao.appointmentById(value.id));dao.updateAppointment(clean.copy(completed_utc=old.completed_utc));value.id
        }
    }
    suspend fun setVisitCompleted(id:Long,completed:Boolean,now:Instant=Instant.now())=transaction { dao ->
        val old=requireNotNull(dao.appointmentById(id));dao.updateAppointment(old.copy(completed_utc=if(completed)now.toEpochMilli() else null))
    }
    /** Also removes the appointment's questions and visit-pack records; files already exported are not touched. */
    /** §39: the appointment goes to the bin with its questions and generated visit packs, and comes back with them. */
    suspend fun deleteAppointment(id:Long,now:Instant=Instant.now())=transaction { dao ->
        val a=Trash.rows(sql(),"appointment","id=?",arrayOf(id));if(a.length()==0)return@transaction
        val day=java.time.Instant.ofEpochMilli(a.getJSONObject(0).getLong("at_utc")).atZone(ZoneId.of(a.getJSONObject(0).getString("at_zone"))).toLocalDate().toString()
        Trash.insert(sql(),Trash.APPOINTMENT,"$id",day,Trash.payload("appointment" to a,"visit_question" to Trash.rows(sql(),"visit_question","appointment_id=? ORDER BY id",arrayOf(id)),
            "visit_pack" to Trash.rows(sql(),"visit_pack","appointment_id=? ORDER BY id",arrayOf(id))),now.toEpochMilli())
        dao.deleteVisitQuestions(id);dao.deleteVisitPacks(id);dao.deleteAppointment(id) }
    suspend fun visitQuestions()=withContext(Dispatchers.IO){db().dao().visitQuestions()}
    suspend fun saveVisitQuestion(value:VisitQuestionEntity)=transaction { dao ->
        val text=value.text.trim();require(text.isNotEmpty() && value.status in listOf("OPEN","ASKED"))
        val answer=value.answer_note?.trim()?.takeIf{it.isNotEmpty()}
        if(value.id==0L){requireNotNull(dao.appointmentById(value.appointment_id))
            val next=(dao.visitQuestions().filter{it.appointment_id==value.appointment_id}.maxOfOrNull{it.sort_order} ?: -1)+1
            dao.insertVisitQuestion(value.copy(text=text,answer_note=answer,sort_order=next))
        } else { val old=dao.visitQuestions().single{it.id==value.id};dao.updateVisitQuestion(old.copy(text=text,status=value.status,answer_note=answer));value.id }
    }
    suspend fun deleteVisitQuestion(id:Long)=transaction{it.deleteVisitQuestion(id)}
    /** Swaps a question with its neighbour; order is kept dense from 0. */
    suspend fun moveVisitQuestion(id:Long,up:Boolean)=transaction { dao ->
        val all=dao.visitQuestions();val q=all.single{it.id==id}
        val list=all.filter{it.appointment_id==q.appointment_id}.sortedWith(compareBy({it.sort_order},{it.id})).toMutableList()
        val i=list.indexOf(q);val j=if(up)i-1 else i+1
        if(j in list.indices){java.util.Collections.swap(list,i,j)}
        list.forEachIndexed{n,v->if(v.sort_order!=n)dao.updateVisitQuestion(v.copy(sort_order=n))}
    }
    suspend fun visitPacks()=withContext(Dispatchers.IO){db().dao().visitPacks()}
    suspend fun recordVisitPack(value:VisitPackEntity)=transaction { dao ->
        requireNotNull(dao.appointmentById(value.appointment_id));VisitSection.parse(value.sections);ZoneId.of(value.zone)
        require(LocalDate.parse(value.range_from)<=LocalDate.parse(value.range_to));JSONObject(value.facts_json)
        dao.insertVisitPack(value.copy(id=0))
    }
    suspend fun records()=withContext(Dispatchers.IO){db().dao().records()}

    // --- Import / backup / wipe ---
    suspend fun importTransMemo(plan:net.plainnotes.app.importer.TransMemo.Plan,overwrite:Boolean,zone:ZoneId=ZoneId.systemDefault())=withContext(Dispatchers.IO) {
        val db=db();db.withTransaction { TransMemoWriter.write(db.dao(),db.openHelper.writableDatabase,plan,overwrite,zone) }
    }
    suspend fun importHrtTracker(plan:net.plainnotes.app.importer.HrtTracker.Plan,targets:Map<net.plainnotes.app.importer.HrtTracker.Group,Long?>,
                                 names:Map<net.plainnotes.app.importer.HrtTracker.Group,String>,weightKg:Double?,zone:ZoneId=ZoneId.systemDefault())=transaction { dao ->
        HrtTrackerWriter.write(dao,plan,targets,names,weightKg,zone)
    }
    suspend fun exportBackup(password:CharArray):ByteArray=exportBackupFrom(space,password,null)
    /** No UI caller until measured restore and forecast admission is complete. */
    internal suspend fun exportMeasuredBackup(password:CharArray,expectedEpoch:String):ByteArray {
        val target=space
        if(target!=Space.PRIMARY)throw ReferenceFailure(ReferenceError.WRONG_SPACE)
        return exportBackupFrom(target,password,expectedEpoch)
    }
    private suspend fun exportBackupFrom(target:Space,password:CharArray,measuredEpoch:String?):ByteArray=withContext(Dispatchers.IO) {
        val db=access.get(target)
        val json=db.withTransaction {
            val raw=db.openHelper.writableDatabase
            if(measuredEpoch!=null && ReferenceStore.previewEpoch(raw)!=measuredEpoch)throw ReferenceFailure(ReferenceError.STALE_SCOPE)
            RegimenHistory.seed(raw)
            if(measuredEpoch==null){InventoryEncoding.legacyOnly(raw);ReferenceSchema.validate(raw)}
            else MeasuredConsumptionStore.auditStagedDataset(raw)
            JSONObject().put("format",BackupCodec.FORMAT_VERSION).put("schema",RecordBackupCompatibility.SCHEMA)
            .put("created",Instant.now().toString()).put("tables",RawData.dump(db.openHelper.writableDatabase)) }
        val plain=json.toString().toByteArray(Charsets.UTF_8)
        try{BackupCodec.encrypt(plain,password)}finally{plain.fill(0)}
    }
    /** Replaces all data with the backup; throws [BackupCodec.WrongPassword] or [BackupCodec.BadFile] without touching anything. */
    /**
     * Restores a backup from this schema or an older one (older ones are upgraded right after the rows are written, with the
     * same steps as the Room migration). A backup from a newer schema is refused.
     */
    suspend fun restoreBackup(data:ByteArray,password:CharArray,today:LocalDate=LocalDate.now())=
        restoreBackupTo(space,data,password,today,null)
    /** Internal integration entry; shares strict parsing and the atomic legacy replacement implementation. */
    internal suspend fun restoreMeasuredBackup(data:ByteArray,password:CharArray,expectedEpoch:String,today:LocalDate=LocalDate.now(),checkpoint:(RestoreCheckpoint)->Unit = {}) {
        val target=space
        if(target!=Space.PRIMARY)throw ReferenceFailure(ReferenceError.WRONG_SPACE)
        restoreBackupTo(target,data,password,today,expectedEpoch,checkpoint)
    }
    private suspend fun restoreBackupTo(target:Space,data:ByteArray,password:CharArray,today:LocalDate,measuredEpoch:String?,checkpoint:(RestoreCheckpoint)->Unit = {})=withContext(Dispatchers.IO) {
        val plain=BackupCodec.decrypt(data,password)
        val json=try{BackupJsonReader.read(plain)}finally{plain.fill(0)}
        val schema=RecordBackupCompatibility.readVersion(json)
        val tables=json.getJSONObject("tables")
        require(schema>=10 || ReferenceSchema.businessTables.none {tables.has(it)}) { "Misdeclared reference backup version" }
        RecordBackupCompatibility.validateInventoryVersion(schema,tables)
        val required=DOMAIN_TABLES.filterNot{(schema<2 && it in listOf("stage_review","symptom_check","review_effect")) || (schema<4 && it in listOf("regimen_version","regimen_rule_link","milestone")) || (schema<5 && it=="lab_context_revision") || (schema<6 && it in listOf("visit_question","visit_pack")) || (schema<7 && it in listOf("history_period_revision","record_annotation")) || (schema<9 && it=="trash_item") || (schema<10 && it in ReferenceSchema.businessTables)}
        require(required.all{tables.has(it)}) { "Incomplete backup" }
        val db=access.get(target)
        db.withTransaction {
            val sql=db.openHelper.writableDatabase
            if(measuredEpoch!=null && ReferenceStore.previewEpoch(sql)!=measuredEpoch)throw ReferenceFailure(ReferenceError.STALE_SCOPE)
            RawData.restore(access.applicationContext,sql,tables,upgradeRegimens=schema<4,backfillTrash=schema<9,measured=measuredEpoch!=null,checkpoint=checkpoint)
            if(schema<2) WellbeingUpgrade.apply(sql,today)
        }
    }
    /** Irreversibly deletes the database, its key file and Keystore key. */
    fun destroyAll()=access.destroy(space)
    fun destroy(space: Space)=access.destroy(space)

    // --- History edits (stock is corrected through REVERSE + new CONSUME entries) ---
    /** Changes time and/or amount of a recorded intake, or turns a missed slot into a backfilled intake. */
    suspend fun editRecord(id:Long,taken:Instant,dose:Double,now:Instant=Instant.now())=transaction { dao ->
        require(dose.isFinite()&&dose>0&&!taken.isAfter(now.plusSeconds(60)))
        val r=requireNotNull(dao.recordById(id)).also{require(it.deleted_at_utc==null&&it.status!="SKIPPED")}
        SupplyLedger.reverse(dao,id)
        val late=r.scheduled_utc?.let{s->r.late_after_minutes_snapshot?.let{l->taken.toEpochMilli()>s+l*60_000L}} ?: false
        val next=r.copy(taken_utc=taken.toEpochMilli(),taken_zone=ZoneId.systemDefault().id,actual_dose=dose,status=if(late)"LATE" else "ON_TIME",revision=r.revision+1)
        dao.updateRecord(next);SupplyLedger.allocate(dao,dao.recordById(id)!!)
    }
    /** Soft-deletes a taken intake; a scheduled slot becomes due again and may later be marked missed. */
    suspend fun deleteRecord(id:Long,now:Instant=Instant.now())=transaction { dao ->
        val r=requireNotNull(dao.recordById(id));require(r.status in listOf("ON_TIME","LATE"))
        if(r.deleted_at_utc!=null)return@transaction
        SupplyLedger.reverse(dao,id)
        dao.updateRecord(r.copy(deleted_at_utc=now.toEpochMilli(),revision=r.revision+1))
        val day=Instant.ofEpochMilli(r.taken_utc ?: r.scheduled_utc ?: now.toEpochMilli()).atZone(r.taken_zone?.let{z->runCatching{ZoneId.of(z)}.getOrNull()} ?: ZoneId.systemDefault()).toLocalDate()
        sql().execSQL("DELETE FROM trash_item WHERE kind='RECORD' AND ref=?",arrayOf("$id"))
        Trash.insert(sql(),Trash.RECORD,"$id",day.toString(),org.json.JSONObject(),now.toEpochMilli())
    }

    // --- Stock ---
    suspend fun containers()=withContext(Dispatchers.IO){db().dao().containers()}
    /** Adds [count] containers; the first one is opened right away when nothing is in use. */
    suspend fun addContainers(medicationId:Long,capacity:Double,count:Int,openFirst:Boolean,source:String?=null,batch:String?=null)=transaction { dao ->
        require(capacity.isFinite()&&capacity>0&&count in 1..50)
        repeat(count){i-> dao.insertContainer(ContainerEntity(medication_id=medicationId,capacity=capacity,initial_used_amount=0.0,used_amount=0.0,
            opened_on=if(openFirst&&i==0)LocalDate.now().toString() else null,state=if(openFirst&&i==0)"IN_USE" else "SEALED",
            source_note=source?.trim()?.takeIf{it.isNotEmpty()},batch=batch?.trim()?.takeIf{it.isNotEmpty()})) }
    }
    /** Closes the open container(s) of a medication and opens a sealed one (or a new one with [capacity]). */
    suspend fun replaceContainer(medicationId:Long,capacity:Double)=transaction { dao ->
        val all=dao.containers().filter{it.medication_id==medicationId}
        val measured=dao.measuredContainerIds().toSet()
        require(all.none{it.id in measured}) { "Measured containers require explicit package replacement" }
        all.filter{it.state=="IN_USE"}.forEach{dao.setContainerState(it.id,if(it.capacity-it.used_amount<=1e-9)"EMPTY" else "DISCARDED",it.opened_on)}
        val sealed=all.filter{it.state=="SEALED"}.minByOrNull{it.id}
        if(sealed!=null)dao.setContainerState(sealed.id,"IN_USE",LocalDate.now().toString())
        else dao.insertContainer(ContainerEntity(medication_id=medicationId,capacity=capacity,initial_used_amount=0.0,used_amount=0.0,opened_on=LocalDate.now().toString(),state="IN_USE"))
    }
    suspend fun setRemaining(containerId:Long,remaining:Double)=transaction { SupplyLedger.setRemaining(it,containerId,remaining) }
    suspend fun discardContainer(containerId:Long)=transaction { dao ->
        require(containerId !in dao.measuredContainerIds()) { "Measured container requires explicit package discard" }
        val c=dao.container(containerId);require(c.state!="VOIDED");dao.setContainerState(c.id,"DISCARDED",c.opened_on)
    }
    /**
     * B4 "check stock": sets what is really left in an open or used-up package through the ledger's ADJUST entry. A
     * used-up package with something left becomes open again; no package is created, no dose record changes. Sealed
     * packages are full by definition and discarded ones are not reopened here.
     */
    suspend fun correctStock(containerId:Long,remaining:Double)=transaction { dao ->
        val c=dao.container(containerId)
        require(c.state in setOf("IN_USE","EMPTY")){"Only an open or used-up package can be corrected"}
        SupplyLedger.setRemaining(dao,containerId,remaining)
    }
    /** Packages with at least one ledger entry (consumption, reversal or adjustment). */
    suspend fun containersWithLedger()=transaction { dao -> dao.containersWithLedger().toSet() }
    /** Logical correction: ADJUST is audit history, not evidence of an actual intake. */
    suspend fun removeMistakenContainer(containerId:Long)=transactionWithSql { dao,raw ->
        require(containerId !in dao.measuredContainerIds()) {"Exact package correction requires a current confirmation ticket"}
        PackageCorrections.void(raw,dao,containerId,Instant.now())
    }
    suspend fun restoreVoidedContainer(containerId:Long)=transactionWithSql { dao,raw ->
        require(containerId !in dao.measuredContainerIds()) {"Exact package correction requires a current confirmation ticket"}
        PackageCorrections.restore(raw,dao,containerId,Instant.now())
    }
    suspend fun voidedContainers()=transaction { it.voidedContainers() }

    // --- Well-being ---
    suspend fun checkinItems()=transaction { dao ->
        if(dao.checkinItems().isEmpty()) CHECKIN_DEFAULTS.forEachIndexed{i,k->dao.insertCheckinItem(CheckinItemEntity(builtin_key=k,enabled=true,sort_order=i))}
        dao.checkinItems()
    }
    suspend fun saveCheckinItem(v:CheckinItemEntity)=transaction { dao -> if(v.id==0L)dao.insertCheckinItem(v.copy(sort_order=(dao.checkinItems().maxOfOrNull{it.sort_order} ?: 0)+1)) else {dao.updateCheckinItem(v);v.id} }
    suspend fun reorderCheckinItems(ids:List<Long>)=transaction { dao ->
        val items=dao.checkinItems().associateBy{it.id};require(ids.size==items.size && ids.toSet()==items.keys)
        ids.forEachIndexed{i,id->dao.updateCheckinItem(items.getValue(id).copy(sort_order=i))}
    }
    suspend fun scores(from:LocalDate,to:LocalDate)=withContext(Dispatchers.IO){db().dao().scores(from.toString(),to.toString())}
    suspend fun setScore(date:LocalDate,item:Long,value:Int?,now:Instant=Instant.now())=transaction { dao -> if(value==null){
            val rows=Trash.rows(sql(),"checkin_score","date=? AND item_id=?",arrayOf(date.toString(),item))
            if(rows.length()>0)Trash.insert(sql(),Trash.SCORE,"$date|$item@${now.toEpochMilli()}",date.toString(),Trash.payload("checkin_score" to rows),now.toEpochMilli())
            dao.deleteScore(date.toString(),item)} else {require(value in 1..5);dao.score(CheckinScoreEntity(date.toString(),item,value))} }
    // --- Stage reviews, symptom checks, effect visibility (REQUIREMENTS 15) ---
    suspend fun stageReviews()=withContext(Dispatchers.IO){db().dao().stageReviews()}
    suspend fun saveStageReview(v:StageReviewEntity)=transaction { dao ->
        LocalDate.parse(v.date);require(v.satisfaction==null||v.satisfaction in 1..5)
        require(v.smoking==null||v.smoking in listOf("YES","NO"))
        require(v.systolic==null||v.systolic in 1..999);require(v.diastolic==null||v.diastolic in 1..999)
        require(v.weight_kg==null||v.weight_kg.isFinite()&&v.weight_kg>0)
        val effects=JSONObject(v.effects_json)
        effects.keys().forEach{key->val value=effects.get(key);require(value is String && (key.endsWith(":note")||value in listOf("NOT_YET","NOTICED","UNSURE")))}
        val id=dao.stageReview(v);if(v.id!=0L)v.id else id
    }
    suspend fun deleteStageReview(id:Long,now:Instant=Instant.now())=transaction { dao ->
        val rows=Trash.rows(sql(),"stage_review","id=?",arrayOf(id));if(rows.length()==0)return@transaction
        Trash.insert(sql(),Trash.REVIEW,"$id",rows.getJSONObject(0).getString("date"),Trash.payload("stage_review" to rows),now.toEpochMilli());dao.deleteStageReview(id) }
    suspend fun symptomChecks(from:LocalDate,to:LocalDate)=withContext(Dispatchers.IO){db().dao().symptomChecks(from.toString(),to.toString())}
    suspend fun setSymptomCheck(date:LocalDate,group:String,checked:Boolean,note:String?=null,
        contextProvider:((List<MedicationEntity>,Map<Long,ProfileEntity>)->String?)?=null)=transaction { dao ->
        if(checked) {
            val previous=dao.symptomChecks(date.toString(),date.toString()).firstOrNull{it.group_id==group}
            val meds=dao.medications()
            val context=if(previous!=null)previous.context_snapshot else contextProvider?.invoke(meds,meds.mapNotNull{m->dao.profile(m.id)?.let{m.id to it}}.toMap())
            dao.symptomCheck(SymptomCheckEntity(date.toString(),group,note?.takeIf{it.isNotBlank()},context))
        } else {
            val rows=Trash.rows(sql(),"symptom_check","date=? AND group_id=?",arrayOf(date.toString(),group))
            if(rows.length()>0)Trash.insert(sql(),Trash.SYMPTOM,"$date|$group@${Instant.now().toEpochMilli()}",date.toString(),Trash.payload("symptom_check" to rows),Instant.now().toEpochMilli())
            dao.deleteSymptomCheck(date.toString(),group)
        }
    }
    suspend fun confirmMissed(id:Long)=transaction { dao ->
        val r=requireNotNull(dao.recordById(id));require(r.unconfirmed && r.deleted_at_utc==null)
        dao.updateRecord(r.copy(origin="APP",revision=r.revision+1))
    }
    suspend fun reviewEffects()=withContext(Dispatchers.IO){db().dao().reviewEffects()}
    suspend fun setReviewEffect(id:String,enabled:Boolean)=transaction { it.reviewEffect(ReviewEffectEntity(id,enabled)) }
    suspend fun setContainerInfo(id:Long,source:String?,batch:String?)=transaction { it.setContainerInfo(id,source?.trim()?.takeIf{s->s.isNotEmpty()},batch?.trim()?.takeIf{s->s.isNotEmpty()}) }
    suspend fun notes(from:LocalDate,to:LocalDate)=withContext(Dispatchers.IO){db().dao().notes(from.toString(),to.toString())}
    suspend fun setNote(date:LocalDate,text:String,now:Instant=Instant.now())=transaction { dao -> if(text.isBlank()){
            val rows=Trash.rows(sql(),"day_note","date=?",arrayOf(date.toString()))
            if(rows.length()>0)Trash.insert(sql(),Trash.NOTE,"$date@${now.toEpochMilli()}",date.toString(),Trash.payload("day_note" to rows),now.toEpochMilli())
            dao.deleteNote(date.toString())} else dao.note(DayNoteEntity(date.toString(),text)) }
    /** Planned slots in [from, to) for forecasting; reconciles first so past slots carry their final state. */
    suspend fun planned(from:Instant,to:Instant,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault())=transaction { dao -> timeline(dao,now,from,to,zone) }
    /**
     * The open doses the stock forecast walks (B4): unfinished doses since tracking began plus the next
     * [StockForecasts.WINDOW_DAYS] days. The stock page, the calendar and stock reminders all use this one input.
     */
    private suspend fun stockDemandFrom(dao:NotesDao,now:Instant,zone:ZoneId):List<TimelineEntry> {
        val from=dao.rules().minOfOrNull{it.missed_tracking_from_utc}?.let(Instant::ofEpochMilli) ?: now
        return (timeline(dao,now,minOf(from,now),now.plusMillis(1),zone)+timeline(dao,now,now,now.plus(java.time.Duration.ofDays(StockForecasts.WINDOW_DAYS)),zone))
            .filter{it.state in listOf(SlotState.PENDING,SlotState.SOON,SlotState.OVERDUE)}.distinctBy{it.slot.key}
    }
    suspend fun stockDemand(now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault())=transaction { dao ->stockDemandFrom(dao,now,zone)}
    /** One captured SQL transaction; integer quanta never enter the legacy scalar forecast. */
    suspend fun stockForecasts(now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault())=transactionWithSql { dao,raw ->
        val meds=dao.medications();val containers=dao.containers();val demand=stockDemandFrom(dao,now,zone)
        val forecasts=StockForecasts.forecastAll(meds,containers,demand)
        val modern=containers.any{it.inventory_encoding!=InventoryEncoding.LEGACY} || raw.query("SELECT 1 FROM measured_operation LIMIT 1").use{it.moveToFirst()}
        if(!modern)return@transactionWithSql forecasts
        val balances=try {MeasuredConsumptionStore.forecastBalances(raw,MeasuredConsumptionStore.auditStagedDataset(raw))}
            catch(_:ReferenceFailure){return@transactionWithSql forecasts.mapValues{(_,f)->f.copy(available=null,lastCovered=null,firstShort=null,uncertainty=setOf(StockUnknownReason.INVALID_NEEDS_REVIEW))}}
        val unresolved=raw.query("SELECT r.medication_id,o.unknown_reason FROM measured_operation o JOIN reference_identity i ON i.uuid=o.record_uuid AND i.dataset_uuid=o.dataset_uuid AND i.kind='RECORD' JOIN dose_record r ON r.id=i.live_handle WHERE o.kind='CONSUME' AND o.record_revision=r.revision AND r.deleted_at_utc IS NULL AND (o.unknown_reason IS NOT NULL OR o.unallocated_quantity IS NOT NULL)").use{c->buildList{while(c.moveToNext())add(c.getLong(0) to if(c.isNull(1))null else c.getString(1))}}
        val version=ReferencePayload.sha(ReferenceStore.previewEpoch(raw)+Trash.rows(raw,"supply_container","1 ORDER BY id",emptyArray()).toString()+Trash.rows(raw,"measured_operation","1 ORDER BY operation_uuid",emptyArray()).toString()+
            demand.joinToString("|"){it.slot.key+":"+it.slot.at.toEpochMilli()+":"+it.slot.dose.toRawBits()})
        forecasts.mapValues { (id,f) ->
            val issues=unresolved.filter{it.first==id}.map{if(it.second=="STOCK_EFFECT_NOT_CONFIRMED")StockUnknownReason.STOCK_EFFECT_UNKNOWN else StockUnknownReason.UNALLOCATED}.toSet()
            val physical=balances[id].orEmpty()
            val reasons=(if(physical.isEmpty())f.uncertainty else (f.uncertainty-StockUnknownReason.UNKNOWN_SOURCE))+issues
            if(reasons.isEmpty())f.copy(snapshotSha256=version)
            else f.copy(available=null,lastCovered=null,firstShort=null,uncertainty=reasons,physicalBalances=physical,snapshotSha256=version)
        }
    }
    suspend fun labs()=withContext(Dispatchers.IO){db().dao().labs()}
    suspend fun labContexts()=withContext(Dispatchers.IO){db().dao().labContexts()}
    private suspend fun captureLab(dao:NotesDao,lab:LabValueEntity,origin:String,now:Instant,estimate:String?) {
        val old=dao.labContexts().filter{it.lab_id==lab.id}.maxByOrNull{it.revision}
        val captured=maxOf(now.toEpochMilli(),old?.captured_utc ?: Long.MIN_VALUE)
        val json=LabContext.build(lab,dao.records(),dao.regimens(),dao.rules().associate{it.id to it.config_snapshot},estimate,dao.historyPeriods())
        dao.labContext(LabContextEntity(lab_id=lab.id,revision=(old?.revision ?: 0)+1,captured_utc=captured,origin=origin,context_json=json))
    }
    suspend fun saveLab(value:LabValueEntity,now:Instant=Instant.now(),recapture:Boolean=false,estimate:suspend(NotesDao,LabValueEntity)->String?={_,_->null})=transaction { dao ->
        require(value.value.isFinite() && value.value>0 && value.analyte_code.isNotBlank() && value.unit.isNotBlank())
        ZoneId.of(value.sampled_zone)
        val old=if(value.id==0L)null else requireNotNull(dao.labs().singleOrNull{it.id==value.id})
        dao.analyte(AnalyteEntity(value.analyte_code,value.unit))
        val saved=if(value.id==0L)value.copy(id=dao.insertLab(value)) else value.also{dao.updateLab(it)}
        val sampleChanged=old!=null && (old.sampled_utc!=value.sampled_utc || old.sampled_zone!=value.sampled_zone || old.analyte_code!=value.analyte_code)
        if(old==null || sampleChanged || recapture)
            captureLab(dao,saved,if(old==null)"AT_ENTRY" else if(sampleChanged)"SAMPLE_CHANGED" else "RECONSTRUCTED",now,estimate(dao,saved))
    }
    suspend fun rebuildLabContext(id:Long,now:Instant=Instant.now(),estimate:suspend(NotesDao,LabValueEntity)->String?={_,_->null})=transaction { dao ->
        val lab=requireNotNull(dao.labs().singleOrNull{it.id==id})
        captureLab(dao,lab,"RECONSTRUCTED",now,estimate(dao,lab))
    }
    /** §39: the sample goes to the bin with its frozen contexts and comes back with them unchanged. */
    suspend fun deleteLab(id:Long,now:Instant=Instant.now())=transaction { dao ->
        val lab=Trash.rows(sql(),"lab_value","id=?",arrayOf(id));if(lab.length()==0)return@transaction
        val l=lab.getJSONObject(0);val day=Instant.ofEpochMilli(l.getLong("sampled_utc")).atZone(ZoneId.of(l.getString("sampled_zone"))).toLocalDate().toString()
        Trash.insert(sql(),Trash.LAB,"$id",day,Trash.payload("lab_value" to lab,"lab_context_revision" to Trash.rows(sql(),"lab_context_revision","lab_id=? ORDER BY revision",arrayOf(id))),now.toEpochMilli())
        dao.deleteLab(id) }
    /** Body weight is a single current PK parameter (no history in V1). */
    suspend fun weight()=withContext(Dispatchers.IO){db().dao().pkSettings()?.current_weight_kg}
    suspend fun setWeight(kg:Double)=transaction { require(kg.isFinite() && kg>0 && kg<1000); it.pkSettings(PkSettingsEntity(1,kg)) }
}

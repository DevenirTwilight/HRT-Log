package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import net.plainnotes.app.domain.measurement.MeasureResult
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

internal data class CreatedMeasuredContainer(
    val scope:ReferenceScope,val container:ContainerEntity,val containerUuid:String,
    val presentationEventUuid:String,val presentation:FrozenPackageMeasurement,
)

/** P4 transaction adapter, not exposed through the app/repository yet.
 * Activating callers requires the database/backup format fence and old-path display/allocation isolation.
 * Never upgrades an old container or derives its identity from a name/current medication reference.
 */
internal object MeasuredContainerStore {
    private fun fail(code:ReferenceError):Nothing=throw ReferenceFailure(code)
    suspend fun create(
        db:SupportSQLiteDatabase,dao:NotesDao,expectedEpoch:String,medication:Long,
        operationUuid:String,draft:PackageMeasurementDraft,created:Instant,
    ):CreatedMeasuredContainer {
        check(db.inTransaction())
        if(ReferenceStore.previewEpoch(db)!=expectedEpoch)fail(ReferenceError.STALE_SCOPE)
        if(!ReferencePayload.uuid(operationUuid) || created.toEpochMilli()<0)fail(ReferenceError.INVALID_SOURCE)
        val frozen=PackageMeasurementPayload.freeze(draft)
        val scale=frozen.stockScale()
        if(scale !is MeasureResult.Success)fail(ReferenceError.INVALID_SOURCE)
        val scope=ReferenceStore.scope(db)
        // Identity creation occurs only inside this mutation transaction, never during preview.
        val owner=ReferenceStore.identity(db,scope,"MEDICATION",medication,null)
        if(ReferenceStore.row(db,"measured_operation","operation_uuid=?",arrayOf(operationUuid))!=null)fail(ReferenceError.OPERATION_CONFLICT)
        val previous=ReferenceStore.row(db,"reference_event","operation_uuid=?",arrayOf(operationUuid))
        if(previous!=null) {
            val identity=ReferenceStore.row(db,"reference_identity","uuid=?",arrayOf(previous.getString("subject_uuid"))) ?: fail(ReferenceError.OPERATION_CONFLICT)
            val expected=ReferenceStore.requestHash(scope.dataset,identity.getString("uuid"),0,"LINK",JSONObject(frozen.payload))
            if(previous.getString("dataset_uuid")!=scope.dataset || previous.getString("action")!="LINK" || previous.getInt("revision")!=1 ||
                identity.getString("dataset_uuid")!=scope.dataset || identity.getString("kind")!="CONTAINER" || ReferencePayload.optional(identity,"owner_uuid")!=owner ||
                previous.getString("request_sha256")!=expected)fail(ReferenceError.OPERATION_CONFLICT)
            val snapshot=ReferenceStore.row(db,"reference_snapshot","uuid=?",arrayOf(previous.getString("snapshot_uuid"))) ?: fail(ReferenceError.INVALID_SOURCE)
            if(snapshot.getString("dataset_uuid")!=scope.dataset || snapshot.getString("kind")!="PRESENTATION" || snapshot.getString("namespace")!="USER_ENTERED" ||
                snapshot.getString("source_version")!=draft.product.sourceVersion || snapshot.getString("payload_json")!=frozen.payload || snapshot.getString("payload_sha256")!=frozen.sha256)fail(ReferenceError.OPERATION_CONFLICT)
            // Numeric handles are read as integers, never guessed from a tombstone or coerced from text.
            if(identity.isNull("live_handle"))fail(ReferenceError.UNKNOWN_IDENTITY)
            val container=dao.container(identity.getLong("live_handle"))
            if(container.medication_id!=medication)fail(ReferenceError.UNKNOWN_IDENTITY)
            if(container.capacity!=scale.value.capacityQuanta.toDouble() || container.initial_used_amount!=0.0)fail(ReferenceError.INVALID_SOURCE)
            return CreatedMeasuredContainer(scope,container,identity.getString("uuid"),operationUuid,frozen)
        }
        val capacity=scale.value.capacityQuanta.toDouble() // Exact bounded integer bridge; not a physical-unit Double.
        val handle=dao.insertContainer(ContainerEntity(medication_id=medication,capacity=capacity,initial_used_amount=0.0,used_amount=0.0,state="SEALED"))
        val subject=ReferenceStore.identity(db,scope,"CONTAINER",handle,owner)
        val snapshot=UUID.randomUUID().toString()
        ReferenceStore.insert(db,"reference_snapshot",JSONObject().put("uuid",snapshot).put("dataset_uuid",scope.dataset).put("kind","PRESENTATION")
            .put("namespace","USER_ENTERED").put("source_version",draft.product.sourceVersion).put("payload_json",frozen.payload).put("payload_sha256",frozen.sha256))
        ReferenceStore.insert(db,"reference_event",JSONObject().put("operation_uuid",operationUuid).put("dataset_uuid",scope.dataset).put("subject_uuid",subject)
            .put("revision",1).put("previous_uuid",JSONObject.NULL).put("action","LINK").put("snapshot_uuid",snapshot)
            .put("request_sha256",ReferenceStore.requestHash(scope.dataset,subject,0,"LINK",JSONObject(frozen.payload))).put("created_utc",created.toEpochMilli()))
        return CreatedMeasuredContainer(scope,dao.container(handle),subject,operationUuid,frozen)
    }
}

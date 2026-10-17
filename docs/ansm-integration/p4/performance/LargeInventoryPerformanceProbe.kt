package net.plainnotes.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockCapacityBasis
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Explicit manual fixture; never a normal CI or APK source. No real health data. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class LargeInventoryPerformanceProbe {
    private fun rows(tables:JSONObject,table:String)=tables.getJSONArray(table).let{a->(0 until a.length()).map{a.getJSONObject(it)}}
    private fun rewrite(value:Any,map:Map<String,String>):Any=when(value) {
        is String->map[value] ?: value
        is JSONObject->JSONObject().also{out->value.keys().forEach{k->out.put(k,rewrite(value.get(k),map))}}
        is JSONArray->JSONArray((0 until value.length()).map{rewrite(value.get(it),map)})
        else->value
    }
    @Test fun thousandsOfFrozenPackagesAndTenYearHistory()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        val report=JSONObject().put("status","IN_PROGRESS").put("environment","Robolectric API35 framework SQLite; not SQLCipher/low-memory API26/hardware")
        val file=File("build/reports/ansm-p4-performance/large-ten-year.json").apply{parentFile.mkdirs()}
        val running=AtomicBoolean(true);val peak=AtomicLong(0)
        val sampler=Thread {
            while(running.get()) {val r=Runtime.getRuntime();peak.accumulateAndGet(r.totalMemory()-r.freeMemory(),::maxOf);Thread.sleep(10)}
        }.apply{isDaemon=true;start()}
        fun stage(name:String){report.put("stage",name);file.writeText(report.toString(2))}
        suspend fun timed(name:String,action:suspend()->Unit) {stage(name);val start=System.nanoTime();action();report.put(name+"_ms",(System.nanoTime()-start)/1_000_000.0);file.writeText(report.toString(2))}
        try {
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
            val now=Instant.parse("2026-10-17T12:00:00Z")
            val med=MedicationEntity(name="SYNTHETIC 多語 ten-year fixture",molecule="OTHER",unit="MG",dose_per_intake=0.125,container_capacity=28.0,
                soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0)
            val m=db.dao().insertMedication(med);val saved=db.dao().medication(m)
            val seeds=ArrayList<CreatedMeasuredContainer>()
            timed("real_seed_generation") {repeat(20) {v->
                val meaning=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC compound-$v ester/hydrate not equivalent 有效成分 非等效 成分 non équivalent "+"x".repeat(80))
                val box=repo.createMeasuredPackage(repo.referencePreviewEpoch(),saved,UUID.randomUUID().toString(),PackageMeasurementDraft(
                    UserReferenceProduct("SYNTHETIC $v composé à longue dénomination 药品 藥品 "+"x".repeat(100),sourceVersion="synthetic-large-v$v",recordedOn="2026-10-17"),
                    MeasureUnit.TABLET,meaning,"0.5","28",StockCapacityBasis.USER_COUNTED),now)
                repo.recordMeasuredUse(repo.measuredWriteTicket(box.scope,m),UUID.randomUUID().toString(),MeasuredActualUseDraft(now.minusSeconds(v*21600L).toEpochMilli(),"UTC",0.125,
                    listOf(ConfirmedStockUse(box.containerUuid,box.presentationEventUuid,box.presentation.sha256,Quantity(BigDecimal("0.5"),MeasureUnit.TABLET,meaning),true)),MeasuredStockEffect.DEDUCT_CURRENT,userConfirmed=true),now)
                seeds.add(box)
            }}
            val raw=db.openHelper.writableDatabase
            val seed=db.withTransaction{RawData.dump(raw)}
            val count=2000;val legacy=14600-count
            val fixture=JSONObject().also{out->DOMAIN_TABLES.forEach{out.put(it,JSONArray())}}
            fixture.put("medication",seed.getJSONArray("medication"));fixture.put("reference_dataset",seed.getJSONArray("reference_dataset"))
            rows(seed,"reference_identity").filter{it.getString("kind")=="MEDICATION"}.forEach{fixture.getJSONArray("reference_identity").put(it)}
            val firstRecord=rows(seed,"dose_record").first()
            repeat(legacy) {n->fixture.getJSONArray("dose_record").put(JSONObject(firstRecord.toString()).put("id",n+1)
                .put("taken_utc",now.minusSeconds((14600-n).toLong()*21600).toEpochMilli()).put("unallocated_supply_amount",0.125))}
            var previousUuid:String?=null;var previousHash:String?=null
            timed("synthetic_fixture_expansion") {repeat(count) {n->
                val box=seeds[n%seeds.size]
                val event=rows(seed,"reference_event").single{it.getString("operation_uuid")==box.presentationEventUuid}
                val op=rows(seed,"measured_operation").single{JSONObject(it.getString("input_json")).getJSONObject("request").getJSONArray("uses").getJSONObject(0).getString("container_uuid")==box.containerUuid}
                val recordIdentity=rows(seed,"reference_identity").single{it.getString("uuid")==op.getString("record_uuid")}
                val record=rows(seed,"dose_record").single{it.getLong("id")==recordIdentity.getLong("live_handle")}
                val selected=linkedMapOf(
                    "supply_container" to rows(seed,"supply_container").filter{it.getLong("id")==box.container.id},
                    "dose_record" to listOf(record),
                    "reference_identity" to rows(seed,"reference_identity").filter{it.getString("uuid") in setOf(box.containerUuid,recordIdentity.getString("uuid"))},
                    "reference_snapshot" to rows(seed,"reference_snapshot").filter{it.getString("uuid")==event.getString("snapshot_uuid")},
                    "reference_event" to listOf(event),
                    "record_reference_capture" to rows(seed,"record_reference_capture").filter{it.getString("record_uuid")==recordIdentity.getString("uuid")},
                    "measured_operation" to listOf(op),
                    "measured_allocation" to rows(seed,"measured_allocation").filter{it.getString("operation_uuid")==op.getString("operation_uuid")},
                    "supply_transaction" to rows(seed,"supply_transaction").filter{it.getLong("dose_record_id")==record.getLong("id")})
                val replacements=HashMap<String,String>()
                selected.filterKeys{it in setOf("reference_identity","reference_snapshot","reference_event","record_reference_capture","measured_operation","measured_allocation")}.forEach{(table,values)->
                    values.forEach{r->val key=if(table in setOf("reference_event","measured_operation"))"operation_uuid" else "uuid";replacements[r.getString(key)]=UUID.randomUUID().toString()}}
                val copied=selected.mapValues{(_,values)->values.map{rewrite(it,replacements) as JSONObject}}
                copied.getValue("supply_container").single().put("id",n+1)
                val actualAt=now.minusSeconds((count-n).toLong()*21600).toEpochMilli()
                copied.getValue("dose_record").single().put("id",legacy+n+1).put("taken_utc",actualAt)
                copied.getValue("reference_identity").forEach{r->val id=if(r.getString("kind")=="RECORD")legacy+n+1 else n+1;r.put("original_handle",id).put("live_handle",id)}
                copied.getValue("supply_transaction").single().put("id",n+1).put("container_id",n+1).put("dose_record_id",legacy+n+1)
                copied.getValue("measured_allocation").single().put("ledger_entry_id",n+1)
                val e=copied.getValue("reference_event").single();val snapshot=copied.getValue("reference_snapshot").single()
                e.put("request_sha256",ReferenceStore.requestHash(e.getString("dataset_uuid"),e.getString("subject_uuid"),0,"LINK",JSONObject(snapshot.getString("payload_json"))))
                copied.getValue("record_reference_capture").forEach{it.put("payload_sha256",ReferenceStore.captureHash(it))}
                val operation=copied.getValue("measured_operation").single()
                val input=rewrite(JSONObject(op.getString("input_json")),replacements) as JSONObject
                input.getJSONObject("request").getJSONObject("record").put("taken_utc",actualAt)
                // Every clone is a fresh package with one use; causal order is regenerated, not trusted from seed positions.
                input.getJSONArray("captures").getJSONObject(0).put("ledger_head_id","0").put("used_before_quanta","0").put("stock_state","SEALED")
                input.put("chain",JSONObject().put("sequence",(n+1).toString()).put("previous_operation_uuid",previousUuid ?: JSONObject.NULL).put("previous_payload_sha256",previousHash ?: JSONObject.NULL))
                val encoded=ReferencePayload.canonical(input)
                operation.put("input_json",encoded).put("request_sha256",ReferencePayload.sha(ReferencePayload.canonical(input.getJSONObject("request"))))
                previousUuid=operation.getString("operation_uuid");previousHash=ReferencePayload.sha(encoded)
                copied.forEach{(table,values)->values.forEach{fixture.getJSONArray(table).put(it)}}
            }}
            val bytes=fixture.toString().toByteArray(Charsets.UTF_8).size
            report.put("raw_business_json_bytes",bytes).put("records",14600).put("containers",count).put("measured_operations",count).put("presentation_snapshots",count)
            report.put("distinct_synthetic_product_variants",20).put("row_count",DOMAIN_TABLES.sumOf{fixture.getJSONArray(it).length()})
            timed("validated_staging_and_restore") {db.withTransaction{RawData.restore(context,raw,fixture,measured=true)}}
            timed("full_audit") {db.withTransaction{val values=MeasuredConsumptionStore.auditStagedDataset(raw);assertEquals(count,values.size);assertTrue(values.all{it.usedQuanta==1L})}}
            val containerIdentity=rows(fixture,"reference_identity").single{it.getString("kind")=="CONTAINER" && it.getLong("live_handle")==1L}
            val event=rows(fixture,"reference_event").single{it.getString("subject_uuid")==containerIdentity.getString("uuid")}
            val snapshot=rows(fixture,"reference_snapshot").single{it.getString("uuid")==event.getString("snapshot_uuid")}
            val scale=(PackageMeasurementPayload.read(snapshot.getString("payload_json")).stockScale() as MeasureResult.Success).value
            // The sampler still includes fixture generation. Do not retain a second full test-only graph during real backup/restore.
            DOMAIN_TABLES.forEach{fixture.remove(it);seed.remove(it)}
            seeds.clear()
            timed("shared_forecast") {val forecast=repo.stockForecasts(now,ZoneId.of("Europe/Paris")).getValue(m);assertEquals(StockForecast.Status.UNKNOWN,forecast.status)}
            var archive=byteArrayOf()
            val password="synthetic-performance-only".toCharArray()
            try {
                timed("full_encrypted_backup") {archive=repo.exportMeasuredBackup(password,repo.referencePreviewEpoch())}
                report.put("encrypted_archive_bytes",archive.size)
                timed("full_encrypted_restore") {repo.restoreMeasuredBackup(archive,password,repo.referencePreviewEpoch())}
            } finally{password.fill('\u0000');archive.fill(0)}
            timed("restored_full_audit") {db.withTransaction{assertEquals(count,MeasuredConsumptionStore.auditStagedDataset(raw).size)}}
            val writeTimes=JSONArray()
            timed("three_post_restore_writes") {repeat(3) {
                val scope=repo.referenceScope();val start=System.nanoTime()
                val draft=MeasuredActualUseDraft(now.toEpochMilli(),"UTC",0.125,listOf(ConfirmedStockUse(containerIdentity.getString("uuid"),event.getString("operation_uuid"),snapshot.getString("payload_sha256"),scale.quantum,true)),MeasuredStockEffect.DEDUCT_CURRENT,userConfirmed=true)
                val operation=UUID.randomUUID().toString();val ticket=repo.measuredWriteTicket(scope,m)
                repo.recordMeasuredUse(ticket,operation,draft,now)
                writeTimes.put((System.nanoTime()-start)/1_000_000.0)
            }}
            report.put("post_restore_write_including_ticket_ms",writeTimes)
            timed("post_write_full_audit") {db.withTransaction{val projections=MeasuredConsumptionStore.auditStagedDataset(raw);assertEquals(count,projections.size);assertEquals(4L,projections.single{it.containerUuid==containerIdentity.getString("uuid")}.usedQuanta)}}
            report.put("peak_observed_jvm_heap_bytes",peak.get()).put("heap_sample_interval_ms",10).put("heap_is_not_native_rss",true)
            report.put("physical_device_acceptance","NOT_RUN").put("api26_low_memory_acceptance","NOT_RUN")
            val limits=linkedMapOf("validated_staging_and_restore_ms" to 120000.0,"full_audit_ms" to 15000.0,"shared_forecast_ms" to 15000.0,"full_encrypted_backup_ms" to 120000.0,"full_encrypted_restore_ms" to 120000.0,"restored_full_audit_ms" to 15000.0)
            val exceeded=limits.filter{(key,value)->report.getDouble(key)>value}.keys.toMutableList()
            if((0 until writeTimes.length()).any{writeTimes.getDouble(it)>5000.0})exceeded.add("post_restore_write_including_ticket_ms")
            if(bytes>32*1024*1024)exceeded.add("raw_business_json_bytes")
            if(peak.get()>768L*1024*1024)exceeded.add("peak_observed_jvm_heap_bytes")
            report.put("budget_exceeded",JSONArray(exceeded)).put("status",if(exceeded.isEmpty())"DIAGNOSTIC_BUDGET_PASS" else "DIAGNOSTIC_BUDGET_FAIL")
            file.writeText(report.toString(2));assertEquals("Diagnostic budgets; device acceptance remains NOT_RUN",emptyList<String>(),exceeded)
        } catch(t:Throwable){report.put("failure",t.javaClass.name+": "+t.message);report.put("status","FAIL");file.writeText(report.toString(2));throw t}
        finally {running.set(false);sampler.join(1000);db.close()}
    }
}

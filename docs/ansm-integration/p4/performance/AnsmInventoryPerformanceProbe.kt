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

/** Manual synthetic diagnostic, copied to JVM test sources only by the explicit runner. Not an APK source. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class AnsmInventoryPerformanceProbe {
    @Test fun mixedFiveAndTenYearHistoryAndExactGrowth()=runBlocking {
        val useCount=(System.getenv("ANSM_PERF_USES") ?: "100").toInt().also{require(it in 10..5000)}
        val cases=listOf(5 to useCount,10 to useCount,0 to useCount*2)
        val folder=File("build/reports/ansm-p4-performance").apply{mkdirs()}
        for((years,uses) in cases) {
            val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
            try {
                val repo=NotesRepository(object:DatabaseAccess(ApplicationProvider.getApplicationContext()){override fun get(space:Space)=db})
                val med=MedicationEntity(name="SYNTHETIC performance fixture",molecule="OTHER",unit="MG",dose_per_intake=0.125,container_capacity=10000.0,
                    soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0)
                val now=Instant.parse("2026-10-17T12:00:00Z")
                val m=db.dao().insertMedication(med);val saved=db.dao().medication(m)
                val history=if(years==0)0 else years*365*4-uses
                db.withTransaction {repeat(history) {n->db.dao().record(RecordEntity(medication_id=m,taken_utc=now.minusSeconds((history-n+uses).toLong()*21600).toEpochMilli(),taken_zone="UTC",
                    actual_dose=0.125,unallocated_supply_amount=0.125,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(saved,null)))}}
                val meaning=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC frozen tablet")
                val box=repo.createMeasuredPackage(repo.referencePreviewEpoch(),saved,UUID.randomUUID().toString(),PackageMeasurementDraft(
                    UserReferenceProduct("SYNTHETIC performance product",sourceVersion="synthetic-perf-v1",recordedOn="2026-10-17"),MeasureUnit.TABLET,meaning,"0.5","10000",StockCapacityBasis.USER_COUNTED),now)
                val times=JSONArray()
                repeat(uses) {n->
                    val draft=MeasuredActualUseDraft(now.minusSeconds((uses-n).toLong()*21600).toEpochMilli(),"UTC",0.125,
                        listOf(ConfirmedStockUse(box.containerUuid,box.presentationEventUuid,box.presentation.sha256,Quantity(BigDecimal("0.5"),MeasureUnit.TABLET,meaning),true)),MeasuredStockEffect.DEDUCT_CURRENT,userConfirmed=true)
                    val start=System.nanoTime()
                    repo.recordMeasuredUse(repo.measuredWriteTicket(box.scope,m),UUID.randomUUID().toString(),draft,now)
                    times.put((System.nanoTime()-start)/1_000_000.0)
                    if(n<3 || (n+1)%25==0)File(folder,"years-${years}-exact-${uses}.json").writeText(JSONObject().put("status","IN_PROGRESS")
                        .put("mixed_history_years",years).put("legacy_facts",history).put("requested_exact_facts",uses).put("completed_exact_facts",n+1)
                        .put("write_including_ticket_ms",times).put("physical_device_acceptance","NOT_RUN").toString(2))
                }
                val start=System.nanoTime();val forecast=repo.stockForecasts(now,ZoneId.of("UTC")).getValue(m);val forecastMs=(System.nanoTime()-start)/1_000_000.0
                assertEquals(StockForecast.Status.UNKNOWN,forecast.status)
                val auditStart=System.nanoTime();db.withTransaction{assertEquals(uses.toLong(),MeasuredConsumptionStore.auditStagedDataset(db.openHelper.writableDatabase).single().usedQuanta)}
                val auditMs=(System.nanoTime()-auditStart)/1_000_000.0
                val report=JSONObject().put("status","DIAGNOSTIC_ONLY").put("environment","Robolectric API35 framework SQLite; not SQLCipher/device acceptance")
                    .put("mixed_history_years",years).put("legacy_facts",history).put("exact_facts",uses).put("write_including_ticket_ms",times).put("forecast_ms",forecastMs).put("audit_ms",auditMs)
                    .put("all_exact_ten_year_acceptance","NOT_RUN").put("physical_device_acceptance","NOT_RUN")
                File(folder,"years-${years}-exact-${uses}.json").writeText(report.toString(2))
            } finally {db.close()}
        }
    }
}

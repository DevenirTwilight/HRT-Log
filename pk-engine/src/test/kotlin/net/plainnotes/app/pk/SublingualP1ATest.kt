package net.plainnotes.app.pk

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Random
import kotlin.math.*

/** P1-A acceptance, synthetic inputs only. Does not validate human dose proportionality. */
class SublingualP1ATest {
    private val grid = doubleArrayOf(.25, .5, .75, 46.0/60, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 24.0)
    private fun sl(time: Double = 0.0, dose: Double = 2.0, tier: Int = 2, id: String = "s") =
        DoseEvent(id, Route.SUBLINGUAL, time, dose, Ester.E2, 80.0, DoseExtras(sublingualTier = tier.toDouble()))
    private fun curve(events: List<DoseEvent>, times: DoubleArray = grid, amp: Double = 1.0, rate: Double = 1.0) =
        Engine.simulate(events, grid = times) { c, _ -> if (c == Curve.E2) Scale(amp, rate) else Scale() }!!.curves.getValue(Curve.E2)
    private fun stable(m: FittedModel, t: Double) = if (t < 0) 0.0 else m.terms.sumOf { (a, k) -> a * exp(-k*t) * -expm1(-(m.ka-k)*t) }
    private fun slAt(e: DoseEvent, t: Double): Double {
        val choice = Engine.choose(e) as ModelChoice.Use
        return choice.parts.sumOf { (_, m, share) -> e.doseMG * share * stable(m, t-e.timeH) }
    }
    private fun ordered(b: BandedCurve) {
        for (i in b.timeH.indices) {
            val v = listOf(b.p5[i], b.p25[i], b.p75[i], b.p95[i])
            assertTrue(v.all { it.isFinite() && it >= 0 })
            assertTrue(v.zipWithNext().all { (a,b) -> a <= b })
            assertTrue(b.center[i].isFinite() && b.center[i] >= 0)
        }
    }

    @Test fun allSublingualPartsIgnoreRateAtAndAcrossTheCriticalPoint() {
        val critical = 1.0005/.9995
        val rates = listOf(1e-12, .9, 1.0, Math.nextDown(critical), critical, Math.nextUp(critical), 1.1, 1e12)
        for (dose in listOf(1.0,2.0,4.0)) for (tier in 0..3) {
            val event = sl(dose=dose,tier=tier)
            for (amp in listOf(1e-6,1.0,1e6)) for (rate in rates) {
                val values = curve(listOf(event),amp=amp,rate=rate)
                grid.indices.forEach { i ->
                    val expected = slAt(event,grid[i])*amp
                    assertEquals(expected,values[i],max(1e-8,expected*1e-8))
                    assertTrue(values[i]>0 && values[i].isFinite())
                }
            }
            val m = PkParams.model("E2_SL")
            rates.forEach { rate -> grid.forEach { t ->
                assertEquals(m.response(t,dose,80.0),m.response(t,dose,80.0,rateScale=rate),0.0)
            } }
        }
    }

    @Test fun pureSlPosteriorMatchesIndependentOneDimensionalGaussianMap() {
        val events = listOf(sl());val variance = ln(1+.6*.6)
        for (times in listOf(listOf(1.0),listOf(1.0,1.0,1.0),listOf(.5,1.0,3.0))) {
            val labs = times.mapIndexed { i,t -> LabResult("l$i",t,slAt(events.single(),t)*1.5,LabUnit.PG_ML) }
            val fit = LabFit.fit(events,labs)
            val expectedVar = 1/(1/variance+times.size/(LabFit.SIGMA_LAB*LabFit.SIGMA_LAB))
            val expectedU = expectedVar*times.size*ln(1.5)/(LabFit.SIGMA_LAB*LabFit.SIGMA_LAB)
            assertFalse(fit.rateAdjustable);assertEquals(2,fit.algorithmVersion)
            assertEquals(0.0,fit.logRate,0.0);assertEquals(expectedU,fit.logAmplitude,1e-8)
            assertEquals(expectedVar,fit.cov[0],1e-9)
            assertEquals(listOf(0.0,0.0,0.0),fit.cov.drop(1))
        }
    }

    @Test fun amplitudeMapWithBaselineAndHighlyDiscordantLabsRemainsFinite() {
        val events = listOf(sl())
        for (factor in listOf(.001,1.5,1e6)) {
            val labs = listOf(LabResult("baseline",-1.0,20.0,LabUnit.PG_ML)) + listOf(.5,1.0,3.0).mapIndexed { i,t ->
                LabResult("l$i",t,20+factor*slAt(events.single(),t),LabUnit.PG_ML)
            }
            val fit = LabFit.fit(events,labs,confirmedBaselineLabIds=setOf("baseline"))
            assertEquals(20.0,fit.baselinePGmL!!,0.0)
            assertTrue(fit.logAmplitude.isFinite() && fit.cov[0]>0 && fit.cov[0].isFinite())
            assertEquals(0.0,fit.logRate,0.0)
            ordered(LabFit.bands(events,grid,labs,CalibrationMode.RETROSPECTIVE,confirmedBaselineLabIds=setOf("baseline")).getValue(Curve.E2))
        }
        val large = LabFit.fit(events,listOf(LabResult("large",1.0,slAt(events.single(),1.0)*1e4,LabUnit.PG_ML)))
        assertTrue(exp(large.logAmplitude)>20, "No concentration or new amplitude ceiling was used to fix the defect")
    }

    @Test fun thirtyDayHistoriesAndDoseCorrectionsSuperposeWithoutRateDependence() {
        for (tau in listOf(6.0,12.0,24.0)) {
            val events = (0 until (720/tau).toInt()).filter { it%5!=1 }.map {
                sl(-it*tau, if(it==0)1.0 else 2.0,id="s$it")
            }
            val actual = curve(events,amp=1.7,rate=1e9)
            grid.indices.forEach { i ->
                val expected = events.sumOf { slAt(it,grid[i]) }*1.7
                assertEquals(expected,actual[i],max(1e-7,expected*1e-8))
            }
            for(mode in CalibrationMode.entries) ordered(LabFit.bands(events,grid,emptyList(),mode,40).getValue(Curve.E2))
        }
    }

    @Test fun mixedRoutesSeparateSwallowedSlFromRealOralAndRetainTheirRateCalibration() {
        val fixed = listOf(sl(tier=0)) // large swallowed component makes indirect leakage easy to detect
        val other = listOf(DoseEvent("oral",Route.ORAL,0.0,2.0,Ester.E2,80.0),
            DoseEvent("im",Route.INJECTION,-36.0,5.0,Ester.EV,80.0),
            DoseEvent("gel",Route.GEL,-12.0,1.5,Ester.E2,80.0,DoseExtras(gelProductId=1.0)),
            DoseEvent("patch",Route.PATCH_APPLY,-24.0,1.0,Ester.E2,80.0,DoseExtras(releaseRateUGPerDay=50.0)))
        val actual = curve(fixed+other,amp=1.7,rate=1.1)
        val a = curve(fixed,amp=1.7);val b = curve(other,amp=1.7,rate=1.1)
        grid.indices.forEach { assertEquals(a[it]+b[it],actual[it],1e-6) }
        assertTrue(abs(curve(other,rate=.9)[4]-curve(other,rate=1.1)[4])>1)
        val labs = listOf(0,4,8).map { LabResult("l$it",grid[it],actual[it],LabUnit.PG_ML) }
        val fit = LabFit.fit(fixed+other,labs)
        assertTrue(fit.rateAdjustable);assertTrue(fit.cov[3]>0)
        assertTrue(fit.cov.all { it.isFinite() });assertEquals(fit.cov[1],fit.cov[2],0.0)
        assertTrue(fit.cov[0]*fit.cov[3]-fit.cov[1]*fit.cov[2]>0)
        val band = LabFit.bands(fixed+other,grid,labs,CalibrationMode.RETROSPECTIVE).getValue(Curve.E2)
        val expected = curve(fixed+other,amp=exp(fit.logAmplitude),rate=exp(fit.logRate))
        grid.indices.forEach { assertEquals(expected[it],band.center[it],1e-8) };ordered(band)
    }

    @Test fun pgMlAndPmolLabsAndOutlierWarningRetainConsistentFits() {
        val events=listOf(sl());val labs=listOf(.5,1.0,3.0).mapIndexed{i,t->LabResult("l$i",t,slAt(events.single(),t)*1.5,LabUnit.PG_ML)}+
            LabResult("outlier",2.0,slAt(events.single(),2.0)*30,LabUnit.PG_ML)
        val a=LabFit.fit(events,labs);val b=LabFit.fit(events,labs.map{it.copy(concValue=it.concValue*Pk.PMOL_PER_PG,unit=LabUnit.PMOL_L)})
        assertEquals(a.logAmplitude,b.logAmplitude,1e-9);assertEquals(a.cov[0],b.cov[0],1e-9)
        assertTrue("outlier" in a.excludedLabIds);assertEquals(0.0,a.logRate,0.0)
        ordered(LabFit.bands(events,grid,labs,CalibrationMode.RETROSPECTIVE).getValue(Curve.E2))
    }

    @Test fun posteriorDrawsUseOnlyAmplitudeAndMatchSeedAndSortedQuantiles() {
        val events=listOf(sl());val labs=listOf(LabResult("l",1.0,slAt(events.single(),1.0)*1.5,LabUnit.PG_ML))
        val fit=LabFit.fit(events,labs);val population=curve(events)
        for(samples in listOf(1,2,40,200)) {
            val rnd=Random(20261006L);val draws=DoubleArray(samples){val z=DoubleArray(4){rnd.nextGaussian()};exp(fit.logAmplitude+sqrt(fit.cov[0])*z[0])}.sorted()
            val band=LabFit.bands(events,grid,labs,CalibrationMode.RETROSPECTIVE,samples).getValue(Curve.E2)
            grid.indices.forEach {i->
                assertEquals(population[i]*exp(fit.logAmplitude),band.center[i],1e-7)
                assertEquals(population[i]*draws[(.05*(samples-1)).toInt()],band.p5[i],1e-7)
                assertEquals(population[i]*draws[(.95*(samples-1)).toInt()],band.p95[i],1e-7)
                assertTrue(band.p5[i]>0)
            };ordered(band)
            val again=LabFit.bands(events,grid,labs,CalibrationMode.RETROSPECTIVE,samples).getValue(Curve.E2)
            assertArrayEquals(band.p95,again.p95,0.0)
        }
    }

    @Test fun nonSlFactorsCovarianceAndPriorPosteriorBandsMatchActualVersionOneGolden() {
        val baseline=JSONObject(javaClass.getResourceAsStream("/non-sl-calibration-v1.json")!!.bufferedReader().readText())
        val times=doubleArrayOf(.25,.5,1.0,2.0,6.0,12.0,24.0)
        val rows=baseline.getJSONArray("cases")
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i);val name=row.getString("name")
            val event=when(name) {
                "oral_e2" -> DoseEvent(name,Route.ORAL,0.0,2.0,Ester.E2,80.0)
                "oral_ev" -> DoseEvent(name,Route.ORAL,0.0,2.0,Ester.EV,80.0)
                "injection" -> DoseEvent(name,Route.INJECTION,0.0,5.0,Ester.EV,80.0)
                "gel" -> DoseEvent(name,Route.GEL,0.0,1.5,Ester.E2,80.0,DoseExtras(gelProductId=1.0))
                else -> DoseEvent(name,Route.PATCH_APPLY,0.0,1.0,Ester.E2,80.0,DoseExtras(releaseRateUGPerDay=50.0,patchInstanceId="p"))
            }
            val events=listOf(event);val population=curve(events,times)
            val labs=listOf(1,3,5).map{LabResult("l$it",times[it],population[it]*1.35,LabUnit.PG_ML)}
            val fit=LabFit.fit(events,labs);assertTrue(fit.rateAdjustable)
            assertEquals(row.getDouble("amplitude"),exp(fit.logAmplitude),1e-12)
            assertEquals(row.getDouble("rate"),exp(fit.logRate),1e-12)
            fun values(key:String)=row.getJSONArray(key).let{a->DoubleArray(a.length()){a.getDouble(it)}}
            assertArrayEquals(values("cov"),fit.cov,1e-12)
            val prior=LabFit.bands(events,times,emptyList(),CalibrationMode.RETROSPECTIVE).getValue(Curve.E2)
            val posterior=LabFit.bands(events,times,labs,CalibrationMode.RETROSPECTIVE).getValue(Curve.E2)
            for((key,array) in listOf("population" to prior.center,"prior_p5" to prior.p5,"prior_p95" to prior.p95,
                "calibrated" to posterior.center,"posterior_p5" to posterior.p5,"posterior_p95" to posterior.p95)) assertArrayEquals(values(key),array,1e-9)
        }
    }

    @Test fun nonE2CurvesDoNotInheritSlAmplitudePolicyAndEmptyHistoryIsSafe() {
        val non=listOf(DoseEvent("c",Route.ORAL,0.0,10.0,Ester.CPA,80.0),DoseEvent("spi",Route.ORAL,0.0,25.0,Ester.SPI,80.0),DoseEvent("p",Route.ORAL,0.0,100.0,Ester.P4,80.0))
        val a=LabFit.bands(non,grid,emptyList(),CalibrationMode.RETROSPECTIVE)
        val b=LabFit.bands(listOf(sl())+non,grid,emptyList(),CalibrationMode.RETROSPECTIVE)
        a.forEach{(k,v)->assertArrayEquals(v.center,b.getValue(k).center,0.0);assertArrayEquals(v.p95,b.getValue(k).p95,0.0)}
        assertTrue(LabFit.bands(emptyList(),grid,emptyList(),CalibrationMode.RETROSPECTIVE).isEmpty())
        assertTrue(LabFit.fit(emptyList(),emptyList()).logAmplitude.isFinite())
    }

    @Test fun exportWarmPerformanceAndSyntheticStabilitySummary() {
        val events=(0 until 120).map{sl(it*6.0,id="s$it")};val times=DoubleArray(2881){it*.25}
        val population=curve(events,times);val labs=listOf(200,1000,2000).map{LabResult("l$it",times[it],population[it]*1.5,LabUnit.PG_ML)}
        repeat(2){LabFit.bands(events,times,labs,CalibrationMode.RETROSPECTIVE)}
        ordered(LabFit.bands(events,times,labs,CalibrationMode.RETROSPECTIVE).getValue(Curve.E2))
        val seconds=(0 until 5).map{val start=System.nanoTime();LabFit.bands(events,times,labs,CalibrationMode.RETROSPECTIVE);(System.nanoTime()-start)/1e9}
        assertTrue(seconds.all{it<20})
        val out=JSONObject().put("algorithm_version",LabFit.ALGORITHM_VERSION).put("synthetic_only",true)
            .put("performance",JSONObject().put("events",120).put("grid_points",2881).put("samples",200).put("warmups",2).put("seconds",JSONArray(seconds)))
        val file=File("build/reports/pk-p1b/stability.json");file.parentFile.mkdirs();file.writeText(out.toString(2)+"\n")
    }
}

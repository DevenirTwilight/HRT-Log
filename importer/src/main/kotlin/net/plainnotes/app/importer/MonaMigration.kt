package net.plainnotes.app.importer

import org.json.JSONObject
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** A finite, source-specific clinical projection. Never a prescription, inventory or PK model. */
object MonaMigration {
    enum class Reason { NOT_TAKEN, INVALID_TIME, TIME_PRECISION, INVALID_DOSE, DOSE_PRECISION, UNKNOWN_IDENTITY,
        UNSUPPORTED_ROUTE, UNKNOWN_ESTER, UNSUPPORTED_QUANTITY, UNSUPPORTED_UNIT, UNSUPPORTED_FIELD,
        INVALID_LAB, UNKNOWN_LAB_UNIT, EMPTY_BLOOD_TEST }
    data class Issue(val kind:String,val sourceId:String,val part:String="",val reason:Reason)
    data class Identity(val name:String,val molecule:String,val route:String,val rawRoute:String,val ester:String?,val esterCode:String?,val injectionType:String?,val sourceSchedule:String?=null,val sourceSupply:String?=null) {
        val label:String get()=listOfNotNull(name,ester,rawRoute,injectionType,sourceSchedule?.let{"schedule #$it"},sourceSupply?.let{"supply #$it"}).joinToString(" · ")
    }
    data class Dose(val sourceId:String,val at:Instant,val zone:String,val amount:Double,val original:String,val identity:Identity,val note:String?,val sourceJson:String,val sourceDigest:String)
    data class Lab(val sourceId:String,val analyte:String,val at:Instant,val zone:String,val value:Double,val original:String,val unit:String,val note:String?,val sourceJson:String,val sourceDigest:String)
    data class Plan(val metadata:Mona.Metadata,val doses:List<Dose>,val labs:List<Lab>,val issues:List<Issue>,val sourceIntakes:Int,val sourceDraws:Int,
        val sourceLabValues:Int,val unsupportedSchedules:Int,val unsupportedSupplies:Int,val sourceWarnings:List<Mona.Issue>)
    private val molecules=mapOf("estradiol" to "E2","testosterone" to "T","progesterone" to "P4","cyproterone acetate" to "CPA",
        "spironolactone" to "SPI","bicalutamide" to "BICA","finasteride" to "FIN","dutasteride" to "DUT","dihydrotestosterone" to "DHT")
    private val routes=mapOf("oral" to "ORAL","sublingual" to "SUBLINGUAL","injection" to "INJECTION","gel" to "GEL","patch" to "PATCH")
    private val esters=mapOf("enanthate" to "EN","valerate" to "EV","cypionate" to "EC","undecylate" to "EU","benzoate" to "EB","cypionateSuspension" to "EC_SUSPENSION")
    private fun timing(at:Mona.Timing?):Reason? {
        if(at?.instant==null || at.zone==null || runCatching{ZoneId.of(at.zone)}.isFailure)return Reason.INVALID_TIME
        if(at.instant.nano%1_000_000!=0 || runCatching{at.instant.toEpochMilli()}.isFailure)return Reason.TIME_PRECISION
        return null
    }
    private fun exact(value:BigDecimal?):Double?=value?.toDouble()?.takeIf{it.isFinite() && BigDecimal.valueOf(it).compareTo(value)==0}
    fun digest(sourceJson:String):String=MessageDigest.getInstance("SHA-256").digest(sourceJson.toByteArray(Charsets.UTF_8))
        .joinToString(""){(it.toInt() and 255).toString(16).padStart(2,'0')}
    fun plan(export:Mona.Export):Plan {
        val issues=mutableListOf<Issue>();val doses=mutableListOf<Dose>();val labs=mutableListOf<Lab>()
        for(row in export.intakes) {
            fun reject(reason:Reason){issues+=Issue("INTAKE",row.id,reason=reason)}
            if(row.taken==null){reject(Reason.NOT_TAKEN);continue}
            val timeError=timing(row.taken);if(timeError!=null){reject(timeError);continue}
            val molecule=row.molecule;val code=molecule?.name?.trim()?.lowercase(Locale.ROOT)?.let(molecules::get)
            if(molecule==null || code==null){reject(Reason.UNKNOWN_IDENTITY);continue}
            val route=routes[row.route];if(route==null){reject(Reason.UNSUPPORTED_ROUTE);continue}
            if(row.ester!=null && row.ester !in esters){reject(Reason.UNKNOWN_ESTER);continue}
            if(row.dosingBasis!="mass"){reject(Reason.UNSUPPORTED_QUANTITY);continue}
            if(row.doseUnit!="mg"){reject(Reason.UNSUPPORTED_UNIT);continue}
            if(row.dose==null || row.dose.signum()<=0){reject(Reason.INVALID_DOSE);continue}
            val amount=exact(row.dose);if(amount==null){reject(Reason.DOSE_PRECISION);continue}
            if(row.injectionType!=null && row.injectionType !in setOf("intramuscular","subcutaneous")){reject(Reason.UNSUPPORTED_ROUTE);continue}
            if(export.issues.any {it.type=="medication_intakes" && it.sourceId==row.id && it.reason==Mona.Reason.UNSUPPORTED_FIELD}){reject(Reason.UNSUPPORTED_FIELD);continue}
            // No ester, route subtype, gel product, patch release or SL tier is defaulted.
            val raw=export.sourceRows.getValue("medication_intakes:${row.id}")
            doses+=Dose(row.id,row.taken.instant!!,row.taken.zone!!,amount,row.doseOriginal!!,
                Identity(molecule.name,code,route,row.route!!,row.ester,row.ester?.let(esters::get),row.injectionType,row.scheduleId,row.medicationSupplyItemId),row.notes,raw,digest(raw))
        }
        for(row in export.bloodTests) {
            if(row.values.isEmpty()){issues+=Issue("BLOOD_TEST",row.id,reason=Reason.EMPTY_BLOOD_TEST);continue}
            for(value in row.values) {
                fun reject(reason:Reason){issues+=Issue("BLOOD_TEST",row.id,value.analyte,reason)}
                val timeError=timing(row.timing);if(timeError!=null){reject(timeError);continue}
                val allowed=if(value.analyte=="E2")setOf("pg/mL","pmol/L") else setOf("ng/dL","ng/mL","nmol/L")
                if(value.unit !in allowed){reject(Reason.UNKNOWN_LAB_UNIT);continue}
                if(value.value==null || value.value.signum()<0){reject(Reason.INVALID_LAB);continue}
                val number=exact(value.value);if(number==null){reject(Reason.DOSE_PRECISION);continue}
                if(export.issues.any{it.type=="blood_tests" && it.sourceId==row.id && it.reason==Mona.Reason.UNSUPPORTED_FIELD}){reject(Reason.UNSUPPORTED_FIELD);continue}
                val raw=JSONObject(export.sourceRows.getValue("blood_tests:${row.id}"))
                // Typed analyte fragments preserve source id/time/zone/notes and only their
                // own reported value. Permanent purge must not leave T inside E2 evidence.
                raw.remove(if(value.analyte=="E2")"testosteroneLevels" else "estradiolLevels")
                val fragment=Mona.canonical(raw)
                labs+=Lab(row.id,value.analyte,row.timing!!.instant!!,row.timing.zone!!,number,value.valueOriginal!!,value.unit!!,row.notes,fragment,digest(fragment))
            }
        }
        return Plan(export.metadata,doses.toList(),labs.toList(),issues.toList(),export.intakes.size,export.bloodTests.size,
            export.bloodTests.sumOf{it.values.size},export.schedules.size,export.supplies.size,export.issues)
    }
}

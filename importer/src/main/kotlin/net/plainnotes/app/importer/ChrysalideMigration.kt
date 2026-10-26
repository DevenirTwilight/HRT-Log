package net.plainnotes.app.importer

/** Only positive, timestamped source events survive. Dose is intentionally absent: MedicationLog
 * has no event dose and Medication.dosage is a mutable regimen field, never historical evidence. */
object ChrysalideMigration {
    enum class Reason { NOT_TAKEN, DATE_ONLY_BLOOD_TEST, MEDICATION_LOG_HAS_NO_DOSE, UNSUPPORTED_COLLECTION }
    data class Issue(val collection:String,val sourceId:String?,val reason:Reason,val count:Int=1)
    data class Intake(val sourceId:Long,val exportDigest:String,val medication:Chrysalide.Medication,val at:java.time.Instant,val notes:String?,val sideEffects:String?,val applicationZone:String?,val sourceJson:String,val sourceDigest:String)
    data class Plan(val export:Chrysalide.Export,val intakes:List<Intake>,val issues:List<Issue>,val unsupportedCollections:Map<String,Int>)
    fun plan(export:Chrysalide.Export):Plan {
        val meds=export.medications.associateBy{it.id};val issues=mutableListOf<Issue>();val intakes=mutableListOf<Intake>()
        export.logs.forEach { row ->
            if(!row.taken){issues+=Issue("medicationLogs",row.id.toString(),Reason.NOT_TAKEN);return@forEach}
            val med=meds[row.medicationId]?:error("parser guarantees medication reference")
            val evidence=med.sourceJson+"\n"+row.sourceJson
            if(evidence.toByteArray().size>64000)throw Chrysalide.InvalidExport("TOO_LARGE")
            val digest=digest(export.exportDigest+"\n"+evidence)
            intakes+=Intake(row.id,export.exportDigest,med,row.at,row.notes,row.sideEffects,row.applicationZone,row.sourceJson,digest)
            issues+=Issue("medicationLogs",row.id.toString(),Reason.MEDICATION_LOG_HAS_NO_DOSE)
        }
        export.bloodTests.forEach { issues+=Issue("bloodTests",it.id.toString(),Reason.DATE_ONLY_BLOOD_TEST) }
        val represented=intakes.map{it.medication.id}.toSet()
        val extra=export.medications.count{it.id !in represented}
        val unsupported=if(extra==0)export.unsupported else export.unsupported + ("medication_definitions_without_importable_taken_logs" to extra)
        return Plan(export,intakes,issues,unsupported)
    }
    fun digest(evidence:String)=java.security.MessageDigest.getInstance("SHA-256").digest(evidence.toByteArray()).joinToString(""){"%02x".format(it)}
}

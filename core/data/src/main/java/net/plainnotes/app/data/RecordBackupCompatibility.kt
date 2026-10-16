package net.plainnotes.app.data

/** PNBAK1 business serialization has an independent version from Room; crypto framing is unchanged. */
internal object RecordBackupCompatibility {
    const val SCHEMA = 10
    /** Version gate runs before opening any database. The supported maximum is explicit for legacy-reader contract tests. */
    fun readVersion(json: org.json.JSONObject, supported: Int = SCHEMA): Int {
        require(supported>=1)
        val format=json.opt("format");val version=json.opt("schema")
        if((format !is Int && format !is Long) || (format as Number).toLong()!=BackupCodec.FORMAT_VERSION.toLong() ||
            (version !is Int && version !is Long) || (version as Number).toLong()<1) throw BackupCodec.BadFile("incompatible version")
        val value=(version as Number).toLong()
        if(value>supported.toLong()) throw BackupCodec.NewerBackup()
        return value.toInt()
    }
    val columns = mapOf(
        "medication" to listOf("id","name","molecule","route","unit","dose_per_intake","container_capacity","expiry_days_after_open","soon_alert_minutes","late_after_minutes","site_rotation","site_set","notifications_on","active","sort_order","needs_review"),
        "pk_profile" to listOf("medication_id","ester","pk_route","sl_tier","gel_product_id","gel_site","gel_area_cm2","patch_release_ug_day"),
        "schedule_rule" to listOf("id","medication_id","kind","interval","weekday_mask","anchor_local","anchor_zone","anchor_utc","effective_from_utc","effective_until_utc","effective_zone","missed_tracking_from_utc","dose_snapshot","soon_snapshot","late_snapshot","config_snapshot"),
        "rule_time" to listOf("id","rule_id","local_time","dose_override"),
        "slot_override" to listOf("id","medication_id","rule_version_id","slot_key","rescheduled_utc","rescheduled_zone","dose_override","skipped"),
        "dose_record" to listOf("id","medication_id","rule_version_id","slot_key","scheduled_utc","scheduled_zone","planned_dose","late_after_minutes_snapshot","taken_utc","taken_zone","actual_dose","unallocated_supply_amount","status","site","note","origin","source_record_key","revision","deleted_at_utc","config_snapshot"),
        "supply_container" to listOf("id","medication_id","capacity","initial_used_amount","used_amount","opened_on","state","source_note","batch"),
        "supply_transaction" to listOf("id","container_id","dose_record_id","dose_revision","operation_id","kind","used_delta","reversal_of_id","created_utc","created_zone","reason"),
        "appointment" to listOf("id","type","at_utc","at_zone","location","practitioner","note","remind_minutes_before","completed_utc"),
        "checkin_item" to listOf("id","builtin_key","custom_label","enabled","sort_order","legacy"),
        "checkin_score" to listOf("date","item_id","value"),
        "day_note" to listOf("date","text"),
        "lab_analyte" to listOf("code","canonical_unit"),
        "lab_value" to listOf("id","analyte_code","value","unit","sampled_utc","sampled_zone","reference_lower","reference_upper","reference_unit","laboratory","note"),
        "pk_settings" to listOf("id","current_weight_kg"),
        "retained_slot" to listOf("slot_key","rule_id","medication_id","original_utc","at_utc","zone","dose","soon_minutes","late_minutes","tracking_from_utc"),
        "stage_review" to listOf("id","date","effects_json","tolerance_note","risk_note","smoking","systolic","diastolic","weight_kg","satisfaction","satisfaction_note"),
        "symptom_check" to listOf("date","group_id","note","context_snapshot"),
        "review_effect" to listOf("effect_id","enabled"),
        "regimen_version" to listOf("id","medication_id","effective_from_utc","effective_until_utc","zone","definition_json","clinical_signature","origin","recorded_at_utc"),
        "regimen_rule_link" to listOf("rule_id","regimen_id"),
        "milestone" to listOf("id","date","kind","title","note"),
        "lab_context_revision" to listOf("id","lab_id","revision","captured_utc","origin","context_json"),
        "visit_question" to listOf("id","appointment_id","sort_order","text","status","answer_note"),
        "visit_pack" to listOf("id","appointment_id","generated_utc","zone","range_from","range_to","sections","language","template_version","input_digest","facts_json"),
        "history_period_revision" to listOf("id","period_key","revision","state","medication_id","identity_json","standard_json","from_date","until_date","zone","evidence_json","origin","created_utc","kind","group_key"),
        "record_annotation" to listOf("record_id","kind","created_utc"),
        "trash_item" to listOf("id","kind","ref","item_date","deleted_utc","payload_json","state")
    ) + ReferenceSchema.columns
}

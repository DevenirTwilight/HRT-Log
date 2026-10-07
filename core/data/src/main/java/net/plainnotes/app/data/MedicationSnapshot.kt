package net.plainnotes.app.data

import org.json.JSONObject

/** Input context, independent of the mutable medication/profile. No model parameters or body-weight history. */
data class MedicationSnapshot(val name: String?, val molecule: String?, val route: String?, val unit: String?, val profile: ProfileEntity?) {
    fun medication(id: Long): MedicationEntity? {
        val compound = molecule ?: return null
        val amountUnit = unit ?: return null
        return MedicationEntity(id, name ?: "#$id", compound, route, amountUnit, 1.0, 1.0,
            site_rotation = false, notifications_on = false, active = false, sort_order = 0)
    }

    companion object {
        fun encode(m: MedicationEntity, p: ProfileEntity?): String = JSONObject()
            .put("snapshot_version", 2).put("name", m.name).put("molecule", m.molecule)
            .put("route", m.route ?: JSONObject.NULL).put("unit", m.unit).put("ester", p?.ester ?: JSONObject.NULL)
            .put("pk_profile", p?.let { JSONObject().put("ester", it.ester).put("pk_route", it.pk_route)
                .put("sl_tier", it.sl_tier ?: JSONObject.NULL).put("gel_product_id", it.gel_product_id ?: JSONObject.NULL)
                .put("gel_site", it.gel_site ?: JSONObject.NULL).put("gel_area_cm2", it.gel_area_cm2 ?: JSONObject.NULL)
                .put("patch_release_ug_day", it.patch_release_ug_day ?: JSONObject.NULL) } ?: JSONObject.NULL).toString()

        fun decode(json: String, medicationId: Long): MedicationSnapshot? = runCatching {
            val o = JSONObject(json)
            require(!o.has("snapshot_version") || o.getInt("snapshot_version")==2)
            fun text(key: String): String? = if (o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() }
            val saved = o.optJSONObject("pk_profile")
            val profile = if (saved != null) {
                fun number(key: String): Int? = if (saved.isNull(key)) null else saved.getInt(key)
                fun decimal(key: String): Double? = if (saved.isNull(key)) null else saved.getDouble(key)
                ProfileEntity(medicationId, saved.getString("ester"), saved.getString("pk_route"), number("sl_tier"), number("gel_product_id"),
                    if (saved.isNull("gel_site")) null else saved.getString("gel_site"), decimal("gel_area_cm2"), decimal("patch_release_ug_day"))
            } else if (!o.has("snapshot_version")) {
                // Legacy scalar snapshots can establish route/ester, but never missing product/SL/patch settings.
                val pkRoute = when (text("route")) {
                    "ORAL" -> "oral"; "SUBLINGUAL" -> "sublingual"; "GEL" -> "gel"; "PATCH" -> "patchApply"; "INJECTION" -> "injection"; else -> null
                }
                text("ester")?.let { ester -> pkRoute?.let { ProfileEntity(medicationId, ester, it) } }
            } else null
            MedicationSnapshot(text("name"), text("molecule"), text("route"), text("unit"), profile)
        }.getOrNull()
    }
}

/** Storage remains compatible with old backups. AUTO_MISSED is lack of a record, not confirmation of omission. */
val RecordEntity.unconfirmed: Boolean get() = status == "MISSED" && origin == "AUTO_MISSED"

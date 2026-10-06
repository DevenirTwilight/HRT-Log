package net.plainnotes.app.pk

/** Administration route; [code] is the value stored in the medication profile. */
enum class Route(val code: String) {
    INJECTION("injection"), PATCH_APPLY("patchApply"), PATCH_REMOVE("patchRemove"),
    GEL("gel"), ORAL("oral"), SUBLINGUAL("sublingual");
    companion object { fun of(code: String) = entries.first { it.code == code } }
}

/** Compound or estradiol ester. Doses are always the mass of this compound, not E2-equivalent. */
enum class Ester { E2, EB, EV, EC, EN, EU, CPA, BICA, SPI, P4 }

/** Per-event inputs that only some routes use. */
data class DoseExtras(
    /** Patch: nominal release rate of this application (µg/day). */
    val releaseRateUGPerDay: Double? = null,
    /** Sublingual: hold-time tier index (see E2_SL `tier_minutes` in pk-params.json). */
    val sublingualTier: Double? = null,
    /** Gel: product id (see [GEL_PRODUCT_IDS]). */
    val gelProductId: Double? = null,
    /** Patch: id of this application, and on a removal event the application it ends. */
    val patchInstanceId: String? = null,
    val patchRemovalFor: String? = null,
)

/** One administration. [timeH] is hours since the Unix epoch; [weightKG] is the body weight to use. */
data class DoseEvent(
    val id: String,
    val route: Route,
    val timeH: Double,
    val doseMG: Double,
    val ester: Ester,
    val weightKG: Double,
    val extras: DoseExtras = DoseExtras(),
)

enum class LabUnit { PG_ML, PMOL_L }

data class LabResult(val id: String, val timeH: Double, val concValue: Double, val unit: LabUnit)

/** Gel products the editor offers: 1 Oestrogel, 2 Estreva, 3 EstroGel, 4 Divigel, 5 other / home-made. */
val GEL_PRODUCT_IDS = listOf(1, 2, 3, 4, 5)

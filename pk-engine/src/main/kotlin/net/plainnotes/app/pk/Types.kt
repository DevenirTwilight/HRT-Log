package net.plainnotes.app.pk

/*
 * Ported from TransmtfTeam/Transmtf-HRT-Tracker (commit 8c9abdde, types.ts), MIT License,
 * Copyright (c) 2025 Transmtf Team. See pk-engine/UPSTREAM_LICENSE.
 */

/** Administration route; [code] matches the upstream serialized value. */
enum class Route(val code: String) {
    INJECTION("injection"), PATCH_APPLY("patchApply"), PATCH_REMOVE("patchRemove"),
    GEL("gel"), ORAL("oral"), SUBLINGUAL("sublingual");
    companion object { fun of(code: String) = entries.first { it.code == code } }
}

/** Compound / ester. Doses are always the mass of this compound, not E2-equivalent. */
enum class Ester { E2, EB, EV, EC, EN, EU, CPA, BICA, SPI, P4 }

/**
 * Optional per-event inputs. Numeric fields keep the upstream index encodings
 * (gelSite, sublingualTier, gelCoApplied) so parity tests can feed identical events.
 */
data class DoseExtras(
    val concentrationMGmL: Double? = null,
    val areaCM2: Double? = null,
    val releaseRateUGPerDay: Double? = null,
    val sublingualTheta: Double? = null,
    val sublingualTier: Double? = null,
    val gelSite: Double? = null,
    val gelProductId: Double? = null,
    val gelWashAfterH: Double? = null,
    val gelCoverage: Double? = null,
    val gelCoApplied: Double? = null,
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

class SimulationResult(
    val timeH: DoubleArray,
    val concPGmL: DoubleArray,
    val concPGmLE2: DoubleArray,
    /** CPA component in ng/mL (upstream's legacy name keeps "PGmL"). */
    val concPGmLCPA: DoubleArray,
    /** Non-E2 compounds in ng/mL. */
    val byCompound: Map<Ester, DoubleArray>,
    val auc: Double,
)

enum class LabUnit { PG_ML, PMOL_L }

data class LabResult(val id: String, val timeH: Double, val concValue: Double, val unit: LabUnit)

/** JavaScript `Math.round`: halves round toward +∞. */
internal fun jsRound(x: Double): Double = kotlin.math.floor(x + 0.5)

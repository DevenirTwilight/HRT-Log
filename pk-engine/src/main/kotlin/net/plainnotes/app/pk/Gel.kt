package net.plainnotes.app.pk

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/*
 * Transdermal gel model ported from TransmtfTeam/Transmtf-HRT-Tracker (commit 8c9abdde, pk.ts),
 * MIT License, Copyright (c) 2025 Transmtf Team. Values are the upstream population priors.
 */

/** Application site; ordinal order matches upstream `GEL_SITE_ORDER` (arm, thigh, scrotal, abdomen). */
enum class GelSite(val factor: Double) { ARM(1.0), THIGH(1.0), SCROTAL(8.0), ABDOMEN(1.1) }

data class GelProduct(
    val id: Int,
    val key: String,
    val concentrationMGmL: Double,
    val defaultAreaCM2: Double,
    val refDoseMG: Double,
    val kPenBase: Double,
    val kLoss: Double,
    val kRel: Double,
)

object Gel {
    val PRODUCTS = listOf(
        GelProduct(1, "oestrogel", 0.6, 750.0, 1.5, 0.140, 1.260, 0.022),
        GelProduct(2, "estreva", 1.0, 400.0, 1.5, 0.154, 1.246, 0.022),
        GelProduct(3, "estrogel", 0.6, 750.0, 1.5, 0.140, 1.260, 0.022),
        GelProduct(4, "divigel", 1.0, 200.0, 1.0, 0.168, 1.232, 0.024),
        GelProduct(5, "diy", 1.0, 400.0, 2.0, 0.112, 1.288, 0.022),
    )
    const val PALM_AREA_CM2 = 175.0
    /** Coverage templates: key to area in cm²; `null` means the product's labelled area. */
    val COVERAGE: List<Pair<String, Double?>> = listOf(
        "product" to null, "palm1" to PALM_AREA_CM2, "palm2" to 2 * PALM_AREA_CM2, "palm3" to 3 * PALM_AREA_CM2,
        "thigh" to 200.0, "arm" to 750.0, "arms2" to 1500.0,
    )
    /** none / sunscreen (−16 % AUC) / moisturizer (+38 % AUC), EstroGel label. */
    val CO_APPLICATION_FACTORS = doubleArrayOf(1.0, 0.84, 1.38)

    private const val SIGMA_SAT = 0.008
    private const val CONC_REF = 1.0

    fun product(id: Int?): GelProduct = PRODUCTS.firstOrNull { it.id == id } ?: PRODUCTS[0]
    /** Upstream compares ids with `===`, so only an exact numeric match selects a product. */
    fun product(id: Double?): GelProduct = PRODUCTS.firstOrNull { id != null && it.id.toDouble() == id } ?: PRODUCTS[0]

    data class Kinetics(val kPen: Double, val kLoss: Double, val kRel: Double)

    fun resolveKinetics(product: GelProduct, site: GelSite, doseMG: Double, areaCM2: Double): Kinetics {
        val area = if (areaCM2 > 0) areaCM2 else product.defaultAreaCM2
        val sigma = if (doseMG > 0) doseMG / area else 0.0
        val sigmaRef = if (product.defaultAreaCM2 > 0) product.refDoseMG / product.defaultAreaCM2 else 0.0
        val concRel = if (product.concentrationMGmL > 0) product.concentrationMGmL / CONC_REF else 1.0
        val sigmaSat = SIGMA_SAT * concRel
        var density = if (sigma > 0) (1 + sigmaRef / sigmaSat) / (1 + sigma / sigmaSat) else 1.0
        density = min(2.0, max(0.5, density))
        if (site == GelSite.SCROTAL) density = 1.0
        return Kinetics(product.kPenBase * site.factor * density, product.kLoss, product.kRel)
    }

    /** Only exact known indices map to a factor; anything else is neutral. */
    fun coApplicationFactor(extras: DoseExtras): Double {
        val raw = extras.gelCoApplied ?: return 1.0
        if (!raw.isFinite()) return 1.0
        val idx = jsRound(raw).toInt()
        if (idx < 0 || idx >= CO_APPLICATION_FACTORS.size) return 1.0
        return CO_APPLICATION_FACTORS[idx]
    }

    fun siteOf(extras: DoseExtras): GelSite {
        val idx = min(GelSite.entries.size - 1.0, max(0.0, jsRound(extras.gelSite ?: 0.0))).toInt()
        return GelSite.entries[idx]
    }

    private fun separate(a: Double, b: Double, c: Double): Triple<Double, Double, Double> {
        val eps = 1e-6
        var y = b; var z = c
        if (abs(a - y) < eps) y += eps
        if (abs(a - z) < eps) z += eps
        if (abs(y - z) < eps) z += eps
        return Triple(a, y, z)
    }

    private fun central3(dose: Double, kPen: Double, kRel: Double, l1: Double, l2: Double, l3: Double, t: Double): Double {
        val (a, b, c) = separate(l1, l2, l3)
        val e1 = exp(-a * t) / ((b - a) * (c - a))
        val e2 = exp(-b * t) / ((a - b) * (c - b))
        val e3 = exp(-c * t) / ((a - c) * (b - c))
        return dose * kPen * kRel * (e1 + e2 + e3)
    }

    private fun skin2(dose: Double, kPen: Double, l1: Double, l2: Double, t: Double): Double {
        val b = if (abs(l1 - l2) < 1e-6) l2 + 1e-6 else l2
        return dose * kPen * (exp(-l1 * t) - exp(-b * t)) / (b - l1)
    }

    /** Surface → skin reservoir → central cascade; returns the central amount (mg). */
    fun centralAmount(doseMG: Double, tau: Double, kPen: Double, kLoss: Double, kRel: Double, ke: Double, washAfterH: Double? = null): Double {
        if (!doseMG.isFinite() || !tau.isFinite() || !kPen.isFinite() || !kLoss.isFinite() || !kRel.isFinite() || !ke.isFinite()) return 0.0
        if (tau <= 0 || doseMG <= 0 || kPen <= 0 || kRel <= 0 || ke <= 0 || kLoss < 0 || kPen + kLoss <= 0) return 0.0
        val l1 = kPen + kLoss; val l2 = kRel; val l3 = ke
        val wash = if (washAfterH != null && washAfterH.isFinite() && washAfterH > 0) washAfterH else Double.POSITIVE_INFINITY
        if (tau <= wash) return max(0.0, central3(doseMG, kPen, kRel, l1, l2, l3, tau))
        val skinAtWash = skin2(doseMG, kPen, l1, l2, wash)
        val centralAtWash = central3(doseMG, kPen, kRel, l1, l2, l3, wash)
        val s = tau - wash
        val c = if (abs(l2 - l3) < 1e-6) l3 + 1e-6 else l3
        val mc = centralAtWash * exp(-c * s) + kRel * skinAtWash * (exp(-l2 * s) - exp(-c * s)) / (c - l2)
        return max(0.0, mc)
    }

    fun eventCentralAmount(event: DoseEvent, tau: Double, ke: Double): Double {
        if (tau <= 0 || event.doseMG <= 0) return 0.0
        val ex = event.extras
        val product = product(ex.gelProductId)
        val site = siteOf(ex)
        val area = ex.areaCM2?.takeIf { it > 0 } ?: product.defaultAreaCM2
        val k = resolveKinetics(product, site, event.doseMG, area)
        return centralAmount(event.doseMG, tau, k.kPen, k.kLoss, k.kRel, ke, ex.gelWashAfterH) * coApplicationFactor(ex)
    }
}

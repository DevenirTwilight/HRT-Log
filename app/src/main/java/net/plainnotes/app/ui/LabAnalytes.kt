package net.plainnotes.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.plainnotes.app.R
import net.plainnotes.app.domain.LabAnalyteDefinition
import net.plainnotes.app.domain.LabCatalog
import net.plainnotes.app.domain.LabConversionResult
import java.math.BigDecimal

/** Legacy factors stay unchanged. Affine/new units use their own registered converter, never a fake scalar factor. */
class Analyte(val code:String,val units:List<Pair<String,Double>>,internal val definition:LabAnalyteDefinition?=null) {
    val unitOptions:List<String> =(units.map{it.first}+definition?.units.orEmpty()).distinct()
}
private val LEGACY_ANALYTES = listOf(
    Analyte("E2", listOf("pg/mL" to 1.0, "pmol/L" to 3.671)),
    Analyte("T", listOf("ng/dL" to 1.0, "nmol/L" to 0.03467)),
    // Progesterone by assay method: results from different methods never share a chart. "P4" = method unknown (also imports).
    Analyte("P4", listOf("ng/mL" to 1.0, "nmol/L" to 3.18)),
    Analyte("P4_IA", listOf("ng/mL" to 1.0, "nmol/L" to 3.18)),
    Analyte("P4_MS", listOf("ng/mL" to 1.0, "nmol/L" to 3.18)),
    Analyte("PRL", listOf("ng/mL" to 1.0, "mIU/L" to 21.2)),
    Analyte("LH", listOf("IU/L" to 1.0)), Analyte("FSH", listOf("IU/L" to 1.0)),
    Analyte("SHBG", listOf("nmol/L" to 1.0)),
    Analyte("ALT", listOf("U/L" to 1.0)), Analyte("AST", listOf("U/L" to 1.0)), Analyte("GGT", listOf("U/L" to 1.0)),
    Analyte("CREA", listOf("µmol/L" to 1.0, "mg/dL" to 1 / 88.42)),
    Analyte("K", listOf("mmol/L" to 1.0)),
)

val ANALYTES:List<Analyte> = LEGACY_ANALYTES.map{Analyte(it.code,it.units,LabCatalog.find(it.code))}+
    LabCatalog.all.filter{d->LEGACY_ANALYTES.none{it.code==d.code}}.map{Analyte(it.code,emptyList(),it)}
fun analyte(code:String)=ANALYTES.firstOrNull{it.code==code} ?: Analyte(code,emptyList())
/** The old Double division remains the first path, bit-for-bit, for every legacy unit. */
fun toCanonical(a:Analyte,value:Double,unit:String):Double? {
    a.units.firstOrNull{it.first==unit}?.let{return value/it.second}
    if(!value.isFinite())return null
    val result=a.definition?.toCanonical(BigDecimal.valueOf(value),unit) as? LabConversionResult.Success ?: return null
    return result.value.toDouble().takeIf{it.isFinite()}
}
/** For a report range or explicit unit view; raw saved results are never rewritten. */
internal fun convertLabUnit(code:String,value:Double,from:String,to:String):Double? {
    if(!value.isFinite())return null
    if(from==to)return value
    val result=LabCatalog.find(code)?.convert(BigDecimal.valueOf(value),from,to) as? LabConversionResult.Success ?: return null
    return result.value.toDouble().takeIf{it.isFinite()}
}

@Composable fun analyteLabel(code:String):String {
    val resource:Int?=when(code) {
    "E2" -> R.string.lab_e2; "T" -> R.string.lab_t; "P4" -> R.string.lab_p4; "P4_IA" -> R.string.lab_p4_ia; "P4_MS" -> R.string.lab_p4_ms; "PRL" -> R.string.lab_prl; "LH" -> R.string.lab_lh; "FSH" -> R.string.lab_fsh
    "SHBG" -> R.string.lab_shbg; "ALT" -> R.string.lab_alt; "AST" -> R.string.lab_ast; "GGT" -> R.string.lab_ggt; "CREA" -> R.string.lab_crea; "K" -> R.string.lab_k
    "TC" -> R.string.lab_catalog_tc
    "TG" -> R.string.lab_catalog_tg
    "HDL_C" -> R.string.lab_catalog_hdl_c
    "LDL_C" -> R.string.lab_catalog_ldl_c
    "GLU_FASTING" -> R.string.lab_catalog_glu_fasting
    "GLU_RANDOM" -> R.string.lab_catalog_glu_random
    "HBA1C" -> R.string.lab_catalog_hba1c
    "RBC" -> R.string.lab_catalog_rbc
    "HB" -> R.string.lab_catalog_hb
    "HCT" -> R.string.lab_catalog_hct
    "MCV" -> R.string.lab_catalog_mcv
    "MCH" -> R.string.lab_catalog_mch
    "MCHC" -> R.string.lab_catalog_mchc
    "WBC" -> R.string.lab_catalog_wbc
    "PLT" -> R.string.lab_catalog_plt
    "NEUT_ABS" -> R.string.lab_catalog_neut_abs
    "NEUT_PCT" -> R.string.lab_catalog_neut_pct
    "LYMPH_ABS" -> R.string.lab_catalog_lymph_abs
    "LYMPH_PCT" -> R.string.lab_catalog_lymph_pct
    "MONO_ABS" -> R.string.lab_catalog_mono_abs
    "MONO_PCT" -> R.string.lab_catalog_mono_pct
    "EOS_ABS" -> R.string.lab_catalog_eos_abs
    "EOS_PCT" -> R.string.lab_catalog_eos_pct
    "BASO_ABS" -> R.string.lab_catalog_baso_abs
    "BASO_PCT" -> R.string.lab_catalog_baso_pct
    "ALP" -> R.string.lab_catalog_alp
    "TBIL" -> R.string.lab_catalog_tbil
    "ALB" -> R.string.lab_catalog_alb
    "UREA" -> R.string.lab_catalog_urea
    "NA" -> R.string.lab_catalog_na
    "EGFR" -> R.string.lab_catalog_egfr
    else->null
    }
    return resource?.let{stringResource(it)} ?: code
}

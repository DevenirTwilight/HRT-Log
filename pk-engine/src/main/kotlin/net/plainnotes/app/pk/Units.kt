package net.plainnotes.app.pk

/** Unit conversions and curve helpers shared by the engine and the interface. */
object Pk {
    /** Molar masses (g/mol) from the molecular formulas with standard atomic weights. */
    private val MOLAR_MASS = mapOf(
        Ester.E2 to 272.39,   // C18H24O2
        Ester.EB to 376.50,   // benzoate C25H28O3
        Ester.EV to 356.51,   // valerate C23H32O3
        Ester.EC to 396.57,   // cypionate C26H36O3
        Ester.EN to 384.56,   // enanthate C25H36O3
        Ester.EU to 440.67,   // undecylate C29H44O3
    )

    /**
     * Testosterone (288.42 g/mol) and two ester preparations with direct ANSM RCP
     * product-mass cross-checks (ANDROTARDYL TE 250 mg ~= 180 mg parent T;
     * NEBIDO TU 1000 mg ~= 631.5 mg parent T).
     *
     * Molecular weights are formula-derived, not fitted pharmacokinetic
     * parameters. Converting administered ester mass does NOT predict the
     * concentration of circulating total/free testosterone.
     */
    private const val TESTOSTERONE_MOLAR_MASS = 288.42 // C19H28O2
    private val TESTOSTERONE_ESTERS = mapOf(
        Ester.T to TESTOSTERONE_MOLAR_MASS,
        Ester.TE to 400.60, // testosterone enanthate C26H40O3
        Ester.TU to 456.71, // testosterone undecanoate C30H48O3
    )

    /** Parent testosterone mass per mg of recorded testosterone compound. */
    fun toTestosteroneFactor(ester: Ester): Double =
        TESTOSTERONE_MOLAR_MASS / (TESTOSTERONE_ESTERS[ester]
            ?: error("$ester is not a testosterone compound"))

    /** 1 pg/mL of estradiol in pmol/L (1000 / 272.39). */
    const val PMOL_PER_PG = 3.671

    /** Estradiol mass per mass of the ester (molar mass ratio); 1 for estradiol itself. */
    fun toE2Factor(ester: Ester): Double = MOLAR_MASS.getValue(Ester.E2) / (MOLAR_MASS[ester] ?: error("$ester is not an estradiol ester"))

    /** Linear interpolation of a curve sampled at increasing [timeH]; clamps outside the range, null when empty. */
    fun interpolate(timeH: DoubleArray, values: DoubleArray, hour: Double): Double? {
        if (timeH.isEmpty()) return null
        if (hour <= timeH.first()) return values.first()
        if (hour >= timeH.last()) return values.last()
        var i = java.util.Arrays.binarySearch(timeH, hour)
        if (i >= 0) return values[i]
        i = -i - 1
        val f = (hour - timeH[i - 1]) / (timeH[i] - timeH[i - 1])
        return values[i - 1] + f * (values[i] - values[i - 1])
    }
}

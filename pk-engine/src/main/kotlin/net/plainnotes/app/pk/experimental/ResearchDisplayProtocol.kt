package net.plainnotes.app.pk.experimental

import java.security.MessageDigest

/** Product research display contract, separate from the frozen population engine and personal labs. */
object ResearchDisplayProtocol {
    const val version = "price-figure-weighted-history-v1"
    const val m2Id = "p2x-39"
    const val m1Id = "p2az-m1-09"
    const val m1ProjectionVersion = "p2az-m1-price-projection-v1"
    // Explicit study-background assumption for M1, matched to the representative M2, not measured predose.
    const val m1BackgroundPgMl = 24.0
    fun candidate(model: ResearchModel): ExperimentalCandidate = when(model) {
        ResearchModel.M2 -> ResearchSublingualV01.candidates.single { it.id == m2Id }
        ResearchModel.M1 -> requireNotNull(ResearchM1Catalog.candidate(m1Id))
        ResearchModel.LEGACY -> error("Legacy uses the unchanged production result")
    }
    fun anchor(model: ResearchModel, candidate: ExperimentalCandidate = candidate(model)): ExperimentalStudyAnchor {
        require(model != ResearchModel.LEGACY)
        require(if(model==ResearchModel.M2) candidate in ResearchSublingualV01.candidates else candidate in ResearchM1Catalog.candidates)
        // Only the M1 background changes for this new, explicitly named projection; its shape stays frozen.
        val projected = if(model == ResearchModel.M1) candidate.copy(assumedPriceBaselinePgMl=m1BackgroundPgMl) else candidate
        return PriceFigure1997.anchor(projected)
    }
    fun signature(c: ExperimentalCandidate) = listOf(c.id,c.transitStages,c.fastRatePerHour,
        c.eliminationRatePerHour,c.slowRatePerHour,c.slowWeight,c.exposedDataPseudoLoss,
        c.assumedPriceBaselinePgMl,c.rosanoBaselineCapPmolL).joinToString("|")
    fun parameterHash(c: ExperimentalCandidate) = MessageDigest.getInstance("SHA-256")
        .digest(signature(c).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

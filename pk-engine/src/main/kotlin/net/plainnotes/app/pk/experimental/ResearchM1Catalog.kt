package net.plainnotes.app.pk.experimental

/** P2-AZ 32-candidate M1 family. Manual ID selection, no winner or personal fit. Not P2-V. */
object ResearchM1Catalog {
    const val version = "p2az-m1-grid-v1"
    const val parameterSha256 = "4df0fa815ccfcdba4b260539b8c6f434204e6ae56982c537e78a47f66164764a"
    val candidates = listOf(
        ExperimentalCandidate("p2az-m1-00", 1, 4.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-01", 1, 4.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-02", 1, 8.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-03", 1, 8.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-04", 1, 12.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-05", 1, 12.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-06", 1, 16.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-07", 1, 16.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-08", 3, 4.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-09", 3, 4.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-10", 3, 8.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-11", 3, 8.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-12", 3, 12.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-13", 3, 12.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-14", 3, 16.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-15", 3, 16.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-16", 6, 4.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-17", 6, 4.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-18", 6, 8.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-19", 6, 8.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-20", 6, 12.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-21", 6, 12.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-22", 6, 16.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-23", 6, 16.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-24", 9, 4.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-25", 9, 4.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-26", 9, 8.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-27", 9, 8.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-28", 9, 12.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-29", 9, 12.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-30", 9, 16.0, 0.4, 0.2, 0.0, 0.0, 0.0, 0.0),
        ExperimentalCandidate("p2az-m1-31", 9, 16.0, 1.2, 0.2, 0.0, 0.0, 0.0, 0.0),
    )
    fun candidate(id: String) = candidates.singleOrNull { it.id == id }
}

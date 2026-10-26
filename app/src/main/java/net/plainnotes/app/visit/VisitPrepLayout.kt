package net.plainnotes.app.visit

import net.plainnotes.app.data.VisitSection

/**
 * Sample 08 (docs/design/claude-ui-2026-10-17, owner-approved 2026-10-26, REQUIREMENTS §150): how the visit-pack
 * choices are grouped and labelled. Pure; the selection itself and its defaults stay [VisitSection.defaultOn].
 */
object VisitPrepLayout {
    enum class Group { VISIT, LABS, OTHER }
    /** What a part is made of. Only facts true for every record: the app summarises, or the user entered/imported it. */
    enum class Source { SUMMARISED, ENTERED }

    val groups:List<Pair<Group,List<VisitSection>>> = listOf(
        Group.VISIT to listOf(VisitSection.QUESTIONS,VisitSection.REGIMEN,VisitSection.INTAKES,VisitSection.FACTS,VisitSection.MILESTONES),
        Group.LABS to listOf(VisitSection.LABS),
        Group.OTHER to listOf(VisitSection.SYMPTOMS,VisitSection.REVIEWS,VisitSection.DAILY,VisitSection.PACKAGES),
    )

    fun source(section:VisitSection)=when(section) {
        VisitSection.FACTS,VisitSection.REGIMEN,VisitSection.INTAKES->Source.SUMMARISED
        else->Source.ENTERED
    }

    /** Order of the parts in the exported PDF (Exports.kt PdfReport). Pinned against a real render by VisitPrepLayoutTest. */
    val exportOrder:List<VisitSection> = listOf(VisitSection.REGIMEN,VisitSection.FACTS,VisitSection.INTAKES,VisitSection.LABS,VisitSection.DAILY,
        VisitSection.SYMPTOMS,VisitSection.REVIEWS,VisitSection.QUESTIONS,VisitSection.MILESTONES,VisitSection.PACKAGES)

    fun contents(chosen:Set<VisitSection>)=exportOrder.filter {it in chosen}
}

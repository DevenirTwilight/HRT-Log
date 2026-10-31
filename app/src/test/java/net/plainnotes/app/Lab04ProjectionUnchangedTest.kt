package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.timeline.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.*

/**
 * LAB-04 §5.9: the period projection is unchanged. The same synthetic data (one period, a six-result panel, loose results)
 * gives, field by field, the same projection output as before LAB-04: the digest below was recorded by running this file
 * on the base commit (bdba7e45). The two projection sources are pinned to their base bytes as well.
 */
class Lab04ProjectionUnchangedTest {
    private val day = 86_400_000L
    private val start = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
    private fun lab(id: Long, code: String, at: Long, laboratory: String?, panel: Long?) =
        LabValueEntity(id, code, 10.0, "IU/L", at, "UTC", 1.0, 5.0, "IU/L", laboratory, null, panel)
    private fun extra(): NotesViewModel.ExtraState {
        val med = MedicationEntity(1, "Synthetic", "E2", "ORAL", "MG", 2.0, 30.0, site_rotation = false, notifications_on = false, active = true, sort_order = 0)
        val d = RegimenDefinition(MedicationSnapshot.encode(med, ProfileEntity(1, "EV", "oral")), "EVERY_N_DAYS", 1, 0, 2.0, "UTC", "2026-01-01", null, listOf("08:00:00" to null))
        val t1 = start + 20 * day; val t3 = start + 30 * day
        return NotesViewModel.ExtraState(regimens = listOf(RegimenVersionEntity(1, 1, start, null, "UTC", d.json(), d.signature(), "APP", start)),
            labs = listOf("FSH", "LH", "E2", "T", "ALT", "AST").mapIndexed { i, c -> lab(i + 1L, c, t1, "Synthetic Lab A", 1) } +
                listOf(lab(20, "PRL", start + 25 * day, null, null), lab(30, "E2", t3, "Synthetic Lab B", null), lab(31, "T", t3, "Synthetic Lab B", null),
                    lab(32, "LH", t3, "Synthetic Lab C", null), lab(40, "FSH", start + 200 * day, null, null)),
            labPanels = listOf(LabPanelEntity(1, "u1", t1, "UTC", "Synthetic Lab A", null, t1)))
    }
    private fun dump(t: PeriodTimeline): String = buildString {
        t.projection.periods.forEach { appendLine("P|${it.key}|${it.from}|${it.until}") }
        (t.events.map { "E" to it } + t.upcoming.map { "U" to it }).forEach { (k, e) ->
            appendLine("$k|${e.key}|${e.kind}|${e.at}|${e.date}|${e.displayPeriodKey}|${e.exactRegimenIds.sorted()}|${e.dateOnly}|${e.source}") }
    }
    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test fun projectionOutputIsTheSameAsBefore() {
        val out = dump(PeriodTimelineProjection.build(extra(), emptyList(), Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("UTC")))
        println("LAB04_PROJECTION\n$out")
        assertEquals(BASE_DIGEST, sha(out))
    }

    // PR177 authorizes exactly the explicit APP skip delta, not any LAB-04 projection change.
    // Pin the complete approved source, undo only its four exact edits, then retain BOTH old base hashes.
    private fun originalBytes(path:String,source:String):String {
        val digest=BASE_SOURCES.getValue(path)
        if(sha(source)==digest)return source
        val approval=org.json.JSONObject(File("src/test/resources/explicit-skip-projection-approval.json").readText())
        assertEquals("Only the owner-authorized skip source may differ",approval.getString("path"),path)
        assertEquals("Original LAB-04 hash is never replaced",digest,approval.getString("baseSha256"))
        assertEquals("Exact authorized source; any further edit needs its own review",approval.getString("approvedSha256"),sha(source))
        var original=source
        val edits=approval.getJSONArray("exactChanges")
        assertEquals("Only the four explicit-skip edits",4,edits.length())
        for(i in 0 until edits.length()) {
            val edit=edits.getJSONObject(i);val after=edit.getString("approved")
            assertEquals("One exact approved edit",1,Regex(Regex.escape(after)).findAll(original).count())
            original=original.replace(after,edit.getString("base"))
        }
        return original
    }
    @Test fun projectionSourcesAreTheBaseBytes() {
        for ((path, digest) in BASE_SOURCES) assertEquals(path, digest, sha(originalBytes(path,File(path).readText())))
    }
    @Test fun skipApprovalStillRejectsAnyOtherProjectionEdit() {
        val path="src/main/java/net/plainnotes/app/timeline/PeriodTimeline.kt"
        val source=File(path).readText()
        for(changed in listOf(source.replace("it.sampled_utc","it.id"),source+"\n// unrelated edit\n")) {
            var rejected=false
            try { originalBytes(path,changed) } catch(_:AssertionError){rejected=true}
            org.junit.Assert.assertTrue("The old LAB-04 source boundary must remain strict",rejected)
        }
    }

    companion object {
        const val BASE_DIGEST = "3c598beaa12dfb283f76da5b78d16efa8a7e198984b86771c457bdac951c3292"
        val BASE_SOURCES = mapOf("src/main/java/net/plainnotes/app/timeline/LongitudinalProjection.kt" to "bfa2c9f73c80318dd16663e8cb8c8e722373b9c29956af074d2d313fcb2240a1",
            "src/main/java/net/plainnotes/app/timeline/PeriodTimeline.kt" to "365d7f51d39008b89f261da8796570a75e92cc290a9559b7c64d65760783d879")
    }
}

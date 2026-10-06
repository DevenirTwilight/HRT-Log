package net.plainnotes.app.importer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class HrtTrackerTest {
    private val export = HrtTracker.read(javaClass.getResource("/hrttracker_v2_synthetic.json")!!.readText())

    @Test fun readsTimesLabsAndWeight() {
        assertEquals(13, export.events.size); assertEquals(61.5, export.weightKg)
        assertEquals(Instant.ofEpochSecond(491000L * 3600), export.events[0].at)
        assertEquals(Instant.ofEpochSecond(491012L * 3600 + 900), export.events[1].at) // .25 h
        assertEquals(listOf("pg/mL", "pmol/L"), export.labs.map { it.unit }) // unknown unit dropped
    }

    @Test fun groupsAndSkipsWithoutGuessing() {
        val p = HrtTracker.preview(export)
        val groups = p.groups.keys
        assertTrue(HrtTracker.Group("SUBLINGUAL", "E2", 1, null, null, null, null) in groups)
        assertTrue(HrtTracker.Group("SUBLINGUAL", "EV", 3, null, null, null, null) in groups)
        assertTrue(HrtTracker.Group("GEL", "E2", null, 2, "THIGH", 400.0, null) in groups)
        assertTrue(HrtTracker.Group("PATCH", "E2", null, null, null, null, 50.0) in groups)
        assertEquals("CPA", groups.single { it.ester == "CPA" }.molecule)
        assertEquals(mapOf("sublingual_custom" to 1, "gel_custom_product" to 1, "gel_wash_or_coapplied" to 1, "patch_remove" to 1, "unknown_route" to 1, "custom_gel_products" to 1), p.skipped)
        assertEquals(1, p.duplicates); assertTrue(p.needsDuplicateChoice)
    }

    @Test fun duplicatesNeedAChoice() {
        assertThrows<HrtTracker.MissingChoice> { HrtTracker.plan(export, null) }
        assertEquals(8, HrtTracker.plan(export, HrtTracker.Duplicates.KEEP_ALL).intakes.size)
        val merged = HrtTracker.plan(export, HrtTracker.Duplicates.MERGE)
        assertEquals(7, merged.intakes.size); assertEquals(1, merged.skipped["duplicate_merged"])
        assertEquals(listOf("ht:s1", "ht:s2"), merged.intakes.take(2).map { it.sourceKey })
        assertEquals(1.0, merged.intakes.single { it.group.route == "PATCH" }.dose) // one patch, rate in the profile
    }

    @Test fun rejectsBrokenInput() {
        assertThrows<HrtTracker.InvalidExport> { HrtTracker.read("not json") }
        assertThrows<HrtTracker.InvalidExport> { HrtTracker.read("""{"meta":{"version":9},"events":[]}""") }
        assertThrows<HrtTracker.InvalidExport> { HrtTracker.read("""{"events":[{"route":"oral"}]}""") }
        assertEquals(1, HrtTracker.read("""[{"id":"a","route":"oral","timeH":1,"doseMG":2,"ester":"EV","extras":{}}]""").events.size) // legacy bare array
    }
}

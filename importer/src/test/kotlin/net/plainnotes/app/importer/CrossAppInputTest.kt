package net.plainnotes.app.importer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream

class CrossAppInputTest {
    private val event="""{"id":"synthetic-id","route":"oral","timeH":491012.25,"doseMG":2,"ester":"EV"}"""
    @Test fun actualTrackerAndLegacyArrayKeepOriginalClinicalMeaning() {
        for(text in listOf("""{"meta":{"version":2},"events":[$event]}""","[$event]")) {
            val (identity,parsed)=CrossAppInput.tracker(text)
            val old=HrtTracker.read(text)
            assertEquals(CrossAppInput.Source.HRT_TRACKER,identity.source)
            assertEquals(old.events.single().at,parsed.events.single().at)
            assertEquals("EV",parsed.events.single().ester);assertEquals(2.0,parsed.events.single().doseMg)
        }
    }
    @Test fun ambiguousEmptyAndSimilarOtherApplicationsCannotBecomeTracker() {
        listOf("[]","{}","""{"events":[]}""","""{"events":[$event],"metadata":{},"data":{}}""",
            """{"medications":[],"medicationLogs":[]}""","""{"events":[{"route":"oral"}]}""").forEach {
            assertThrows<Exception> {CrossAppInput.tracker(it)}
        }
    }
    @Test fun unsupportedFutureVersionIsRefusedByOriginalParser() {
        assertThrows<HrtTracker.InvalidExport> {CrossAppInput.tracker("""{"meta":{"version":3},"events":[$event]}""")}
    }
    @Test fun contentSignaturesDoNotDependOnFilename() {
        assertEquals(CrossAppInput.Kind.SQLITE,CrossAppInput.kind("SQLite format 3\u0000".toByteArray()))
        assertEquals(CrossAppInput.Kind.UNSUPPORTED,CrossAppInput.kind("SQLite format 4\u0000".toByteArray()))
        assertEquals(CrossAppInput.Kind.JSON,CrossAppInput.kind(" \n[".toByteArray()))
        assertEquals(CrossAppInput.Kind.UNSUPPORTED,CrossAppInput.kind("HRTBKP1".toByteArray()))
        assertEquals(CrossAppInput.Kind.UNSUPPORTED,CrossAppInput.kind("PNBAK1".toByteArray()))
    }
    @Test fun byteLimitCannotBeAvoidedBySingleByteReadsOrSkip() {
        val b=CrossAppInput.bounded(ByteArrayInputStream(ByteArray(5)),4)
        repeat(4){assertEquals(0,b.read())};assertThrows<CrossAppInput.Rejected>{b.read()}
        assertThrows<CrossAppInput.Rejected>{CrossAppInput.bounded(ByteArrayInputStream(byteArrayOf(1)),2).skip(1)}
    }
    @Test fun malformedUtf8DepthAndLargeStringsAreRefusedBeforeTree() {
        assertThrows<Exception>{CrossAppInput.readJson(ByteArrayInputStream(byteArrayOf(0xC3.toByte(),0x28)))}
        assertThrows<CrossAppInput.Rejected>{CrossAppInput.validateJson("[".repeat(49)+"]".repeat(49))}
        assertThrows<CrossAppInput.Rejected>{CrossAppInput.validateJson("[\""+"x".repeat(65537)+"\"]")}
        CrossAppInput.validateJson("""{"note":"quoted [ } \\\" stay data"}""")
    }
    @Test fun cancellationCheckRemainsOwnedByCaller() {
        var calls=0
        assertThrows<IllegalStateException>{CrossAppInput.readJson(ByteArrayInputStream("[$event]".toByteArray())) {if(++calls>1)error("synthetic cancellation")}}
    }
}

package net.plainnotes.app.data

import org.junit.Assert.*
import org.junit.Test

class AttachmentStorageBudgetTest {
    private val reserve=16L*1024*1024+65536
    private fun report(bytes:Long=0,free:Long=Long.MAX_VALUE,aliases:Int=0,complete:Boolean=true,
        missing:Long=0,entries:Int=0,unit:Long=4096):AttachmentStorageBudget.Report =
        AttachmentStorageBudget.Report(AttachmentStorageBudget.Category.entries.associateWith {
            AttachmentStorageBudget.Usage(if(it==AttachmentStorageBudget.Category.UNKNOWN && bytes>0)1 else 0,
                if(it==AttachmentStorageBudget.Category.UNKNOWN)bytes else 0,if(it==AttachmentStorageBudget.Category.UNKNOWN)bytes else 0)
        },0,aliases,missing,complete,free,unit,entries)
    private fun denied(b:AttachmentStorageBudget,r:AttachmentStorageBudget.Report,reason:AttachmentStorageBudget.Refusal) {
        assertEquals(AttachmentStorageBudget.Decision.Denied(reason),b.evaluate(r))
    }
    @Test fun exactByteAndSpaceBoundaryIncludeWorstCaseCiphertext() {
        val b=AttachmentStorageBudget(maximumBytes=reserve+123,minimumFreeBytes=100)
        assertEquals(AttachmentStorageBudget.Decision.Allowed(reserve),b.evaluate(report(123,reserve+100)))
        denied(b,report(124),AttachmentStorageBudget.Refusal.BYTE_BUDGET)
        denied(b,report(123,reserve+99),AttachmentStorageBudget.Refusal.LOW_SPACE)
    }
    @Test fun unknownBytesAreChargedAndIncompleteInventoryNeverMeansZero() {
        denied(AttachmentStorageBudget(maximumBytes=reserve),report(1),AttachmentStorageBudget.Refusal.BYTE_BUDGET)
        denied(AttachmentStorageBudget(),report(complete=false),AttachmentStorageBudget.Refusal.INCOMPLETE_INVENTORY)
        denied(AttachmentStorageBudget(),report(missing=1),AttachmentStorageBudget.Refusal.MISSING_OBJECTS)
    }
    @Test fun eachAttemptRequiresTwoNewAliasIdentitiesAndOneEntry() {
        val b=AttachmentStorageBudget(maximumAliases=4,maximumEntries=3,minimumFreeBytes=0)
        assertTrue(b.evaluate(report(aliases=2,entries=2)) is AttachmentStorageBudget.Decision.Allowed)
        denied(b,report(aliases=3),AttachmentStorageBudget.Refusal.ALIAS_BUDGET)
        denied(b,report(entries=3),AttachmentStorageBudget.Refusal.ENTRY_BUDGET)
    }
    @Test fun overflowFailsClosedWithoutFloatingPoint() {
        denied(AttachmentStorageBudget(maximumBytes=Long.MAX_VALUE),report(Long.MAX_VALUE),AttachmentStorageBudget.Refusal.RANGE)
        denied(AttachmentStorageBudget(minimumFreeBytes=Long.MAX_VALUE),report(),AttachmentStorageBudget.Refusal.RANGE)
        denied(AttachmentStorageBudget(),report(unit=Long.MAX_VALUE),AttachmentStorageBudget.Refusal.RANGE)
    }
    @Test fun allRetainedCategoriesContributeIndependently() {
        val categories=AttachmentStorageBudget.Category.entries.associateWith {AttachmentStorageBudget.Usage(1,5,4096)}
        val r=AttachmentStorageBudget.Report(categories,4096,10,0,true,Long.MAX_VALUE,4096,5)
        assertEquals(24576L,r.chargedBytes)
        denied(AttachmentStorageBudget(maximumBytes=reserve+24575),r,AttachmentStorageBudget.Refusal.BYTE_BUDGET)
    }
    @Test fun roundUpAllocationHasNoUndercharge() {
        for(unit in listOf(1L,512L,4096L,65536L)) {
            val d=AttachmentStorageBudget(maximumBytes=Long.MAX_VALUE,minimumFreeBytes=0).evaluate(report(unit=unit)) as AttachmentStorageBudget.Decision.Allowed
            assertTrue(d.reservedBytes>=reserve);assertEquals(0L,d.reservedBytes%unit)
            assertTrue(d.reservedBytes-reserve<unit)
        }
    }
    @Test fun publicationChecksActualUsageWithoutReservingAnotherAttempt() {
        val b=AttachmentStorageBudget(maximumBytes=123,maximumAliases=2,maximumEntries=1,minimumFreeBytes=100)
        assertEquals(AttachmentStorageBudget.Decision.Allowed(0),b.retained(report(123,100,2,entries=1)))
        assertEquals(AttachmentStorageBudget.Decision.Denied(AttachmentStorageBudget.Refusal.BYTE_BUDGET),b.retained(report(124,100,2,entries=1)))
        assertEquals(AttachmentStorageBudget.Decision.Denied(AttachmentStorageBudget.Refusal.LOW_SPACE),b.retained(report(123,99,2,entries=1)))
    }
    @Test fun invalidConfigurationAndUnknownCategoriesRefuse() {
        for(block in listOf<()->Unit>({AttachmentStorageBudget(maximumBytes=-1)},{AttachmentStorageBudget(maximumEntries=0)},
            {AttachmentStorageBudget(minimumFreeBytes=-1)},{AttachmentStorageBudget(maximumAliases=-1)},
            {AttachmentStorageBudget.Report(emptyMap(),0,0,0,true,0,4096,0)})) {
            try {block();fail("Invalid budget accepted")}catch(_:IllegalArgumentException){}
        }
    }
}

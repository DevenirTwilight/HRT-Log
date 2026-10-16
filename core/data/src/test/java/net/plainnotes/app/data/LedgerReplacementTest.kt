package net.plainnotes.app.data

import org.junit.Assert.*
import org.junit.Test

class LedgerReplacementTest {
    private fun link(id: Long, parent: Long? = null) = LedgerReplacement.Link(id, parent)
    @Test fun childAndParentNeverShareDeleteLevel() {
        val levels = LedgerReplacement.deletionLevels(listOf(link(1), link(2, 1), link(3), link(4, 3)))
        assertEquals(listOf(listOf(2L, 4L), listOf(1L, 3L)), levels)
    }
    @Test fun deepHostileGraphIsBoundedAndNonRecursive() {
        val links = (1..BackupLimits.MAX_ROWS).map { link(it.toLong(), if (it == 1) null else it - 1L) }
        val levels = LedgerReplacement.deletionLevels(links)
        assertEquals(BackupLimits.MAX_ROWS, levels.size)
        assertEquals(listOf(BackupLimits.MAX_ROWS.toLong()), levels.first())
        assertEquals(listOf(1L), levels.last())
    }
    @Test fun duplicateOrMissingOrCyclicIdentityCannotBecomeAPartialDelete() {
        listOf(listOf(link(1), link(1)), listOf(link(2, 1)), listOf(link(1, 2), link(2, 1)), listOf(link(1, 1))).forEach {
            try { LedgerReplacement.deletionLevels(it); fail("Invalid graph accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun overBudgetRejectsBeforeGraphAllocation() {
        try { LedgerReplacement.deletionLevels(List(BackupLimits.MAX_ROWS + 1) { link(it.toLong()) }); fail("Unbounded graph") }
        catch (_: BackupCodec.TooLarge) { }
    }
}

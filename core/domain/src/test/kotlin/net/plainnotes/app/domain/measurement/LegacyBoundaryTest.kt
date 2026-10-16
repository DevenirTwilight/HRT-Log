package net.plainnotes.app.domain.measurement

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Information-loss counterexamples, not a replacement implementation of the production ledger. */
class LegacyBoundaryTest {
    private data class Legacy(val medication: Long, val capacity: Double, val used: Double)
    private data class World(val unit: String, val strength: String?, val product: String?, val nominalOnly: Boolean) {
        fun oldProjection() = Legacy(1, 28.0, 0.0)
    }
    @Test fun `different strengths have identical old scalar representation`() {
        val a = World("TABLET", "2 mg", "SYNTH-A", false)
        val b = a.copy(strength = "1 mg", product = "SYNTH-B")
        assertNotEquals(a, b); assertEquals(a.oldProjection(), b.oldProjection())
    }
    @Test fun `container scalar cannot recover its own physical unit`() {
        val a = World("TABLET", null, null, false)
        assertEquals(a.oldProjection(), a.copy(unit = "MG").oldProjection())
    }
    @Test fun `old identity unknown cannot be inferred from a current product`() {
        val a = World("PUMP", null, null, true)
        assertEquals(a.oldProjection(), a.copy(product = "SYNTH-NEW", nominalOnly = false).oldProjection())
    }
    @Test fun `binary decimal subtraction is not exact inventory arithmetic`() {
        var remaining = 1.0
        repeat(10) { remaining -= 0.1 }
        assertNotEquals(0.0, remaining)
    }
}

package net.plainnotes.app.pk

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Formulation mass factors are NOT testosterone serum-concentration predictions. */
class TestosteroneMassTest {
    @Test fun testosteroneParentAndEstersUseDifferentMasses() {
        assertEquals(1.0, Pk.toTestosteroneFactor(Ester.T), 1e-12)
        assertEquals(180.0, 250.0 * Pk.toTestosteroneFactor(Ester.TE), 0.2,
            "ANSM ANDROTARDYL 250 mg testosterone enanthate contains about 180 mg testosterone")
        assertEquals(631.5, 1000.0 * Pk.toTestosteroneFactor(Ester.TU), 0.2,
            "ANSM NEBIDO 1000 mg testosterone undecanoate contains about 631.5 mg testosterone")
    }

    @Test fun estradiolAndTestosteroneEstersCannotBeInterchanged() {
        assertThrows(IllegalStateException::class.java) { Pk.toTestosteroneFactor(Ester.EV) }
        assertThrows(IllegalStateException::class.java) { Pk.toE2Factor(Ester.TE) }
        assertEquals(1.0, Pk.toE2Factor(Ester.E2), 1e-12)
    }

    @Test fun unsupportedTestosteroneInjectionRemainsExplicitlyUnsupportedUntilValidated() {
        val te = DoseEvent("t-te", Route.INJECTION, 0.0, 250.0, Ester.TE, 70.0)
        val tu = DoseEvent("t-tu", Route.INJECTION, 0.0, 1000.0, Ester.TU, 70.0)
        assertEquals(ModelChoice.None(Unsupported.NO_LITERATURE_ESTER), Engine.choose(te))
        assertEquals(ModelChoice.None(Unsupported.NO_LITERATURE_ESTER), Engine.choose(tu))
        val r = Engine.simulate(listOf(te, tu))!!
        assertTrue(r.curves.isEmpty(), "No invented absolute ng/mL testosterone curve")
        assertEquals(Unsupported.NO_LITERATURE_ESTER, r.unsupported["t-te"])
        assertEquals(Unsupported.NO_LITERATURE_ESTER, r.unsupported["t-tu"])
    }
}

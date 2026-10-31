package net.plainnotes.app.pk.formulations

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import net.plainnotes.app.pk.formulations.TestosteroneFormulations as T

class TestosteroneFormulationsTest {
    private val gel = T.product("T_ANDROGEL_50")!!
    private val grid = DoubleArray(721) { it / 12.0 - 2 }
    @Test fun exactProductIdentityAndRouteNeverFallback() {
        assertNull(T.product("ANDROGEL"))
        assertNull(T.product("T"))
        assertNotEquals(gel.code, T.product("T_ANDROGEL_162")!!.code)
        assertEquals(T.Status.RECORD_ONLY, T.product("T_ANDROGEL_162")!!.status)
        assertEquals(T.Status.RECORD_ONLY, T.product("T_CYPIONATE_SC")!!.status)
        assertNotEquals(T.product("T_CYPIONATE_IM")!!.route, T.product("T_CYPIONATE_SC")!!.route)
    }
    @Test fun massesAreCompoundMassNotVolumeOrEquivalents() {
        val te = T.product("T_ANDROTARDYL_IM")!!
        assertEquals(180.0, T.parentMg(250.0, "MG", te)!!, 0.1)
        assertEquals(T.parentMg(1.0, "MG", te)!!, T.parentMg(1000.0, "UG", te)!!, 1e-12)
        assertEquals(T.parentMg(1.0, "MG", te)!!, T.parentMg(1_000_000.0, "NG", te)!!, 1e-12)
        assertEquals(T.parentMg(250.0, "MG", te), T.parentMg(1.0, "ML", te))
        assertEquals(631.5, T.parentMg(4.0, "ML", T.product("T_NEBIDO_IM")!!)!!, 0.1)
        assertNull(T.parentMg(1.0, "ML", gel))
        assertNull(T.parentMg(1.0, "ML", T.product("T_CYPIONATE_IM")!!))
        assertNull(T.parentMg(1.0, "PUMP", gel))
        assertNull(T.parentMg(Double.NaN, "MG", te))
    }
    @Test fun causalFiniteNonnegativeAndNormalizedWithoutAbsoluteUnits() {
        T.products.filter { it.status == T.Status.RESEARCH_SHAPE_ONLY }.forEach { p ->
            val r = T.shape(p, listOf(T.Event(0.0, 50.0)), grid)!!
            r.variants.forEach { curve ->
                assertTrue(curve.all { it.isFinite() && it in 0.0..1.0 })
                assertEquals(1.0, curve.maxOrNull()!!, 1e-12)
                grid.indices.filter { grid[it] <= 0 }.forEach { assertEquals(0.0, curve[it]) }
            }
            assertFalse(r.variants[0].contentEquals(r.variants[2]))
        }
    }
    @Test fun repeatChangedDoseMissedDoseAndStoppingUseOnlyActualEvents() {
        val many = listOf(T.Event(0.0, 50.0), T.Event(24.0, 75.0), T.Event(72.0, 25.0))
        val end = DoubleArray(1000) { it * 0.5 }
        val curve = T.shape(gel, many, end)!!.relative
        assertTrue(curve.last() < 1e-4)
        val one = T.shape(gel, listOf(many.first()), end)!!.relative
        assertFalse(curve.contentEquals(one))
        assertNull(T.shape(T.product("T_JATENZO")!!, many, end))
    }
    @Test fun longActingResidueIsNotCutAt180Days() {
        val result = T.shape(T.product("T_NEBIDO_IM")!!, listOf(T.Event(-24.0 * 365, 631.5)), doubleArrayOf(0.0, 24.0, 48.0))!!
        assertTrue(result.relative.last() > 0)
        assertTrue(result.relative.first() > result.relative.last())
    }
    @Test fun firstOrderAndTransitRemainStableForCoincidentRates() {
        listOf(0.0001, 1.0, 100.0).forEach { t ->
            assertEquals(T.firstOrder(t, 0.5, 0.5), T.firstOrder(t, 0.5, 0.5 + 1e-12), 1e-9)
            assertEquals(T.transit(t, 0.5, 0.5), T.transit(t, 0.5, 0.5 + 1e-12), 1e-9)
        }
        assertTrue(T.transit(10000.0, 2.0, 0.01).isFinite())
    }
    @Test fun analyticInputsMatchIndependentNumericalConvolution() {
        // Midpoint quadrature of input(s)*exp(-ke*(t-s)), not a second copy of the formulas.
        val input=0.5; val ke=24*kotlin.math.ln(2.0)
        for(t in listOf(0.1,1.0,10.0,30.0)) {
            val n=40_000; val dt=t/n
            val first=(0 until n).sumOf { i ->
                val s=(i+0.5)*dt
                input*kotlin.math.exp(-input*s)*kotlin.math.exp(-ke*(t-s))*dt
            }
            val second=(0 until n).sumOf { i ->
                val s=(i+0.5)*dt
                input*input*s*kotlin.math.exp(-input*s)*kotlin.math.exp(-ke*(t-s))*dt
            }
            assertEquals(first,T.firstOrder(t,input,ke),first*1e-4+1e-10)
            assertEquals(second,T.transit(t,input,ke),second*1e-4+1e-10)
        }
    }
    @Test fun invalidGridAndEventFailRatherThanQuietlyTruncate() {
        assertThrows(IllegalArgumentException::class.java) { T.shape(gel, listOf(T.Event(0.0, 1.0)), doubleArrayOf(0.0, 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { T.shape(gel, listOf(T.Event(0.0, -1.0)), grid) }
        assertThrows(IllegalStateException::class.java) { T.shape(gel, listOf(T.Event(0.0, 1.0)), grid) { error("cancel") } }
    }
}

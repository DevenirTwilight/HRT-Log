package net.plainnotes.app.disguise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcTest {
    @Test fun arithmetic() {
        assertEquals("7", Calc.result("1+2×3"))
        assertEquals("9", Calc.result("(1+2)×3"))
        assertEquals("9", Calc.result("(1+2)×(3"))    // missing ")" closed at the end
        assertEquals("0.3", Calc.result("0.1+0.2"))
        assertEquals("-4", Calc.result("−(2+2)"))
        assertEquals("0.333333333333", Calc.result("1÷3"))
        assertEquals("1234", Calc.result("1234"))
        assertEquals("1e15", Calc.result("1000000×1000000000"))
    }

    @Test fun percent() {
        assertEquals("0.5", Calc.result("50%"))
        assertEquals("110", Calc.result("100+10%"))
        assertEquals("90", Calc.result("100−10%"))
        assertEquals("20", Calc.result("200×10%"))
    }

    @Test fun invalid() {
        listOf("", "1+", "×2", "1÷0", "1..2", ")", "2(3)").forEach { assertNull(it, Calc.result(it)) }
    }
}

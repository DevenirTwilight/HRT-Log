package net.plainnotes.app.disguise

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Expression evaluator for the calculator shell: + − × ÷, unary minus, parentheses (missing closing ones are
 * added), postfix % (divides by 100; after + or − it is a percentage of the left operand, as on pocket calculators).
 */
object Calc {
    private val mc = MathContext.DECIMAL64
    class Error : Exception()

    fun evaluate(expr: String): BigDecimal {
        val p = Parser(expr.replace('×', '*').replace('÷', '/').replace('−', '-').filterNot { it.isWhitespace() })
        val v = p.sum(); if (p.i < p.s.length) throw Error()
        return v
    }

    fun format(v: BigDecimal): String {
        val r = v.round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        return if (r.compareTo(BigDecimal.ZERO) == 0) "0" else if (r.abs() >= BigDecimal("1e12") || (r.abs() < BigDecimal("1e-9"))) r.toString().replace("E+", "e").replace("E", "e") else r.toPlainString()
    }

    /** Result text for the display, or null when the input is incomplete or invalid. */
    fun result(expr: String): String? = try { format(evaluate(expr)) } catch (_: Exception) { null }

    private class Parser(val s: String) {
        var i = 0
        private fun peek() = s.getOrNull(i)
        fun sum(): BigDecimal {
            var v = product()
            while (peek() == '+' || peek() == '-') {
                val op = s[i++]; val r = product(percentOf = v).first
                v = if (op == '+') v.add(r, mc) else v.subtract(r, mc)
            }
            return v
        }
        private fun product(): BigDecimal = product(null).first
        /** Returns the value and whether it was a trailing percentage applied to [percentOf]. */
        fun product(percentOf: BigDecimal?): Pair<BigDecimal, Boolean> {
            var (v, pct) = unary(percentOf)
            while (peek() == '*' || peek() == '/') {
                val op = s[i++]; val r = unary(null).first
                v = if (op == '*') v.multiply(r, mc) else { if (r.signum() == 0) throw Error(); v.divide(r, mc) }
                pct = false
            }
            return v to pct
        }
        private fun unary(percentOf: BigDecimal?): Pair<BigDecimal, Boolean> {
            if (peek() == '-') { i++; val (v, p) = unary(percentOf); return v.negate() to p }
            if (peek() == '+') { i++; return unary(percentOf) }
            var v = atom(); var pct = false
            while (peek() == '%') { i++; v = if (percentOf != null && !pct) percentOf.multiply(v, mc).divide(BigDecimal(100), mc) else v.divide(BigDecimal(100), mc); pct = true }
            return v to pct
        }
        private fun atom(): BigDecimal {
            if (peek() == '(') {
                i++; val v = sum()
                if (peek() == ')') i++ else if (i < s.length) throw Error()
                return v
            }
            val start = i
            while (peek()?.let { it.isDigit() || it == '.' } == true) i++
            val t = s.substring(start, i)
            if (t.isEmpty() || t.count { it == '.' } > 1 || t == ".") throw Error()
            return BigDecimal(t)
        }
    }
}

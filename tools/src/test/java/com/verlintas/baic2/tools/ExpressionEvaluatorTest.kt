package com.verlintas.baic2.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExpressionEvaluatorTest {

    private fun eval(expression: String) = ExpressionEvaluator(expression).evaluate()

    @Test
    fun respectsPrecedence() {
        assertEquals(17.0, eval("12 + 5 * 1"))
        assertEquals(45.0, eval("(12+3)*3"))
    }

    @Test
    fun handlesDecimalsAndUnary() {
        assertEquals(3.5, eval("7 / 2"))
        assertEquals(-3.0, eval("-3"))
        assertEquals(6.0, eval("--6"))
    }

    @Test
    fun powerIsRightAssociative() {
        assertEquals(512.0, eval("2^3^2"))
    }

    @Test
    fun moduloAndDivisionByZero() {
        assertEquals(1.0, eval("7 % 3"))
        assertFailsWith<ArithmeticException> { eval("1/0") }
    }

    @Test
    fun rejectsGarbage() {
        assertFailsWith<IllegalArgumentException> { eval("1 + abc") }
        assertFailsWith<IllegalArgumentException> { eval("(1+2") }
        assertFailsWith<IllegalArgumentException> { eval("") }
    }

    @Test
    fun whitespaceIsTolerated() {
        assertEquals(21.0, eval("  ( 3 + 4 ) * 3 "))
        assertTrue(eval("2.5 * 2") == 5.0)
    }
}

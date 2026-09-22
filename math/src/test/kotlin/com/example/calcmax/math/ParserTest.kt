package com.example.calcmax.math
import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    private fun p(s: String) = Parser(s).parse()
    @Test fun precedence() { assertEquals("*",p("2+3*4").args[1].value); assertEquals("unary",p("-2^2").kind); assertEquals("^",p("2^3^2").args[1].value) }
    @Test fun exactLiteral() { assertEquals("1",p("1/3").args[0].value); assertEquals("number",p("1.234567890123456789").kind) }
    @Test fun structures() { assertEquals("list",p("det([[1,2],[3,4]])").args[0].kind); assertEquals("relation",p("solve(x^2=1,x)").args[0].kind); assertEquals("*",p("2x").value) }
    @Test fun unicode() { assertEquals("^",p("x²").value); assertEquals("sqrt",p("√8").value); assertEquals("degree",p("30°").value) }
    @Test fun editor() { assertEquals("sqrt(8)",Editor().insert("sqrt()",5).insert("8").source); assertEquals("2+",Editor("2+3").delete().source); assertEquals("7",Editor("2+3",3,0).insert("7").source) }
    @Test fun invalid() { for(s in listOf("", "1..2", "2+", "a.__class__", "f(1,)", "[1,2")) assertThrows(SyntaxException::class.java) { p(s) } }
    @Test fun complexity() { assertThrows(SyntaxException::class.java) { p("(".repeat(200)+"1"+")".repeat(200)) } }
}

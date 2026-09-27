package com.kirinonakar.calcmax.math
import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    private fun p(s: String) = Parser(s).parse()
    @Test fun precedence() { assertEquals("*",p("2+3*4").args[1].value); assertEquals("unary",p("-2^2").kind); assertEquals("^",p("2^3^2").args[1].value) }
    @Test fun exactLiteral() { assertEquals("1",p("1/3").args[0].value); assertEquals("number",p("1.234567890123456789").kind) }
    @Test fun halfUpRoundParses() { assertEquals("roundh",p("roundh(1.225,2)").value) }
    @Test fun randomCallTakesNoArguments() {
        assertEquals("rnd",p("rnd()").value)
        assertTrue(p("rnd()").args.isEmpty())
        assertTrue(Parser("rnd()",true).parse().args.isEmpty())
        assertFalse(Editor("rnd()",4).tree()!!.nodes().any {it.kind=="hole"})
        assertThrows(SyntaxException::class.java) { p("sin()") }
    }
    @Test fun structures() { assertEquals("list",p("det([[1,2],[3,4]])").args[0].kind); assertEquals("relation",p("solve(x^2=1,x)").args[0].kind); assertEquals("*",p("2x").value) }
    @Test fun adjacentListsMultiplyImplicitly() { assertEquals("*",p("[1,2][3,4]").value); assertEquals("binary",p("[[1,0],[0,1]][[4,5],[6,7]]").kind) }
    @Test fun operatorSlotBetweenOperandsParses() { assertEquals("*",Parser("2()3",true).parse().value); assertEquals("*",Parser("[[1,0],[0,1]]()[[4,5],[6,7]]",true).parse().value) }
    @Test fun directSecondPageStructures() {
        assertEquals(listOf("x","y"),p("{x,y}").args.map{it.value})
        assertEquals("set",p("{x,y}").kind)
        assertEquals("list",Parser("[[,],[,]]",true).parse().kind)
        assertEquals(4,Parser("[[,],[,]]",true).parse().nodes().count {it.kind=="hole"})
        assertEquals("[[1,],[,]]",Editor().insert("[[,],[,]]",2).insert("1").source)
        assertEquals(9,Parser("[[,,],[,,],[,,]]",true).parse().nodes().count {it.kind=="hole"})
        assertEquals("[[1,,],[,,],[,,]]",Editor().insert("[[,,],[,,],[,,]]",2).insert("1").source)
    }
    @Test fun integrationTuple() {
        val integral=p("integrate(exp(-x^2)*cos(2x), (x, 0, oo))")
        assertEquals("tuple",integral.args[1].kind)
        assertEquals(listOf("x","0","oo"),integral.args[1].args.map{it.value})
        assertEquals("*",integral.args[0].args[1].args[0].value)
        assertEquals("tuple",p("(x,)").kind)
    }
    @Test fun unicode() { assertEquals("^",p("x²").value); assertEquals("sqrt",p("√8").value); assertEquals("degree",p("30°").value) }
    @Test fun sexagesimalInputAndIncompleteFields() {
        val tree=p("2°20′30″")
        assertEquals("sexagesimal",tree.kind)
        assertEquals(listOf("2","20","30"),tree.args.map{it.value})
        val addition=p("2°20′30″+0°39′30″")
        assertEquals("+",addition.value)
        assertTrue(addition.args.all{it.kind=="sexagesimal"})
        assertEquals("degree",p("30°").value)
        val pending=Parser("2°20",true).parse()
        assertEquals("sexagesimal",pending.kind)
        assertTrue(pending.nodes().any{it.kind=="hole"})
    }
    @Test fun editor() { assertEquals("sqrt(8)",Editor().insert("sqrt()",5).insert("8").source); assertEquals("2+",Editor("2+3").delete().source); assertEquals("7",Editor("2+3",3,0).insert("7").source) }
    @Test fun invalid() { for(s in listOf("", "1..2", "2+", "a.__class__", "f(1,)", "[1,2", "(x,,0)")) assertThrows(SyntaxException::class.java) { p(s) } }
    @Test fun complexity() { assertThrows(SyntaxException::class.java) { p("(".repeat(200)+"1"+")".repeat(200)) } }
    @Test fun editingHoles() {
        val tree=Editor("()/()").tree()!!
        assertEquals("hole",tree.args[0].args[0].kind)
        assertEquals(1,tree.args[0].args[0].start)
        assertEquals("sqrt",Editor("sqrt()").tree()!!.value)
        assertThrows(SyntaxException::class.java) { p("()/()") }
    }
    @Test fun engineeringCatalogSyntax() {
        for(source in listOf(
            "taylor(exp(x),x,0,4)",
            "gradient(x^2+y^2,[x,y])",
            "dsolve(diff(y(t),t)=y(t),y(t),t)",
            "laplace(sin(t),t,s)",
            "charpoly([[1,2],[3,4]],x)",
            "convert(qty(1,V)/qty(1,ohm),A)"
        )) assertEquals("call",p(source).kind)
    }
}

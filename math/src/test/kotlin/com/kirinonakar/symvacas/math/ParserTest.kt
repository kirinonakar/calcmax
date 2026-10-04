package com.kirinonakar.symvacas.math

import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    private fun p(s: String) = Parser(s).parse()
    @Test fun precedence() { assertEquals("*",p("2+3*4").args[1].value); assertEquals("unary",p("-2^2").kind); assertEquals("^",p("2^3^2").args[1].value) }
    @Test fun structures() { assertEquals("list",p("det([[1,2],[3,4]])").args[0].kind); assertEquals("relation",p("solve(x^2=1,x)").args[0].kind); assertEquals("*",p("2x").value) }
    @Test fun savedMatrixRowsParseAcrossNewlines() {
        val matrix=p("[\n[1, 2],\n[3, 4]]")
        assertEquals("list",matrix.kind)
        assertEquals(2,matrix.args.size)
        assertTrue(matrix.args.all {it.kind=="list"&&it.args.size==2})
    }
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
    @Test fun unicode() { assertEquals("^",p("x²").value); assertEquals("sqrt",p("√8").value); assertEquals("degree",p("30°").value) }
    @Test fun invalid() { for(s in listOf("", "1..2", "2+", "a.__class__", "f(1,)", "[1,2", "(x,,0)")) assertThrows(SyntaxException::class.java) { p(s) } }
    @Test fun complexity() { assertThrows(SyntaxException::class.java) { p("(".repeat(200)+"1"+")".repeat(200)) } }
}

class EvaluationPolicyTest {
    @Test fun multiArgumentCallsWaitForEquals() {
        assertTrue(requiresExplicitEvaluation(Parser("rnd()",true).parse()))
        assertTrue(requiresExplicitEvaluation(Parser("integrate(e)").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("roundh(1.225,2)").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("integrate(,x)",true).parse()))
        assertTrue(requiresExplicitEvaluation(Parser("integrate(exp(-x^2)*cos(2x),(x,0,oo))").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("1+dot([1,2],[3,4])").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("f(1)").parse(),setOf("f")))
    }

    @Test fun entryHelpersPreviewWhileTyping() {
        assertFalse(requiresExplicitEvaluation(Parser("log(100)").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("log(100,10)").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("nthroot(8,3)").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("mixed(1,1,2)").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("mod(17,5)").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("divmod(17,5)").parse()))
    }

}

class ConstantInputTest {
    @Test fun insertionSeparatesNumbersAndBothIdentifierBoundaries() {
        assertEquals("2*e",Editor("2").insertConstant("e").source)
        val middle=Editor("xy",1).insertConstant("pi")
        assertEquals("x*pi*y",middle.source)
        assertEquals(4,middle.cursor)
        assertEquals("Ans*pi",Editor("Ans*x").selectRange(4,5).insertConstant("pi").source)
        assertEquals("sin(pi)",Editor("sin()",4).insertConstant("pi").source)
        assertEquals("pi",Editor("Ans").selectRange(0,3).insertConstant("pi").source)
        assertEquals("pie",Editor().insertOperand("pie").source)
    }
}

class BracketAutoCloseTest {
    @Test fun openerInsertsTheMatchingCloserAndKeepsTheCaretInside() {
        assertEquals(BracketEdit("print()",6),BracketAutoClose.typed("print",5,"print(",6))
        assertEquals(BracketEdit("a()b",2),BracketAutoClose.typed("ab",1,"a(b",2))
        assertEquals(BracketEdit("{}",1),BracketAutoClose.typed("",0,"{",1))
        assertEquals(BracketEdit("a[]b",2),BracketAutoClose.typed("ab",1,"a[b",2))
    }
}

class TypedParensTest {
    @Test fun shiftDropsPairsThatWereEditedOrLost() {
        assertEquals(emptyList<IntRange>(),TypedParens.shift(listOf(2..4),"5×()","5×(3)"))
        assertEquals(emptyList<IntRange>(),TypedParens.shift(listOf(2..4),"5×()","abc"))
    }
}

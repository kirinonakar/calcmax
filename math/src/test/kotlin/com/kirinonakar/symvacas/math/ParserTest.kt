package com.kirinonakar.symvacas.math

import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    private fun p(s: String) = Parser(s).parse()
    @Test fun precedence() { assertEquals("*",p("2+3*4").args[1].value); assertEquals("unary",p("-2^2").kind); assertEquals("^",p("2^3^2").args[1].value) }
    @Test fun desmosPiecewiseAndRestrictionsPreserveWholeExpressionScope() {
        val piece=p("f(x)={x<0:x^2,x>=0:2*x}").args[1]
        assertEquals("piecewise",piece.kind)
        assertEquals(listOf("^","*"),piece.args.map {it.args[0].value})
        assertEquals(listOf("<",">="),piece.args.map {it.args[1].value})
        for(source in listOf("y=(1-e^(-x/900)){0<=x<=3000}","y=1-e^(-x/900) {0<=x<=3000}")) {
            val rhs=p(source).args[1];assertEquals("restriction",rhs.value)
            assertEquals("<=",rhs.args[0].args[1].value)
            assertEquals("<=",rhs.args[0].args[1].args[0].value)
            assertEquals("3000",rhs.args[0].args[1].args[1].value)
        }
        assertEquals("*",p("y=(1-e^(-3000/900))*e^(-(x-3000)/80){x>3000}").args[1].args[0].args[0].value)
        assertEquals("-",p("y=1-e^(-x/900) {0<=x<=3000}").args[1].args[0].args[0].value)
        assertEquals("true",p("{x<0:x^2,2*x}").args.last().args[1].value)
        assertEquals("set",p("{1,2}").kind)
        for(source in listOf("{x<0:}","{x<0:1,2,x>0:3}","x{}"))assertThrows(SyntaxException::class.java){p(source)}
    }

    @Test fun structuredParserPreservesOperatorSlotsSetsAndMatrixTemplates() {
        run { // operatorSlotBetweenOperandsParses
            assertEquals("*",Parser("2()3",true).parse().value); assertEquals("*",Parser("[[1,0],[0,1]]()[[4,5],[6,7]]",true).parse().value)
        }
        run { // directSecondPageStructures
            assertEquals(listOf("x","y"),p("{x,y}").args.map{it.value})
            assertEquals("set",p("{x,y}").kind)
            assertEquals("list",Parser("[[,],[,]]",true).parse().kind)
            assertEquals(4,Parser("[[,],[,]]",true).parse().nodes().count {it.kind=="hole"})
            assertEquals("[[1,],[,]]",Editor().insert("[[,],[,]]",2).insert("1").source)
            assertEquals(9,Parser("[[,,],[,,],[,,]]",true).parse().nodes().count {it.kind=="hole"})
            assertEquals("[[1,,],[,,],[,,]]",Editor().insert("[[,,],[,,],[,,]]",2).insert("1").source)
        }
    }

    @Test fun invalid() {
        run { // invalid
            for(s in listOf("", "1..2", "2+", "a.__class__", "f(1,)", "[1,2", "(x,,0)")) assertThrows(SyntaxException::class.java) { p(s) }
        }
        run { // complexity
            assertThrows(SyntaxException::class.java) { p("(".repeat(200)+"1"+")".repeat(200)) }
        }
    }

}

class EvaluationPolicyTest {
    @Test fun evaluationPolicySeparatesExplicitCallsFromEntryPreviews() {
        run { // multiArgumentCallsWaitForEquals
            assertTrue(requiresExplicitEvaluation(Parser("rnd()",true).parse()))
            assertTrue(requiresExplicitEvaluation(Parser("integrate(e)").parse()))
            assertTrue(requiresExplicitEvaluation(Parser("roundh(1.225,2)").parse()))
            assertTrue(requiresExplicitEvaluation(Parser("integrate(,x)",true).parse()))
            assertTrue(requiresExplicitEvaluation(Parser("integrate(exp(-x^2)*cos(2x),(x,0,oo))").parse()))
            assertTrue(requiresExplicitEvaluation(Parser("1+dot([1,2],[3,4])").parse()))
            assertTrue(requiresExplicitEvaluation(Parser("f(1)").parse(),setOf("f")))
        }
        run { // entryHelpersPreviewWhileTyping
            assertFalse(requiresExplicitEvaluation(Parser("log(100)").parse()))
            assertFalse(requiresExplicitEvaluation(Parser("log(100,10)").parse()))
            assertFalse(requiresExplicitEvaluation(Parser("nthroot(8,3)").parse()))
            assertFalse(requiresExplicitEvaluation(Parser("mixed(1,1,2)").parse()))
            assertFalse(requiresExplicitEvaluation(Parser("mod(17,5)").parse()))
            assertFalse(requiresExplicitEvaluation(Parser("divmod(17,5)").parse()))
        }
    }

}

class BracketAutoCloseTest {
}

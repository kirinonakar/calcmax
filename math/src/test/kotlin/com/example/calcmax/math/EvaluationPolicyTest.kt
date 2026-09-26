package com.example.calcmax.math

import org.junit.Assert.*
import org.junit.Test

class EvaluationPolicyTest {
    @Test fun multiArgumentCallsWaitForEquals() {
        assertTrue(requiresExplicitEvaluation(Parser("integrate(e)").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("integrate(,x)",true).parse()))
        assertTrue(requiresExplicitEvaluation(Parser("integrate(exp(-x^2)*cos(2x),(x,0,oo))").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("1+dot([1,2],[3,4])").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("f(1)").parse(),setOf("f")))
    }

    @Test fun simpleExpressionsCanPreview() {
        assertFalse(requiresExplicitEvaluation(Parser("2+3*4").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("cos(2*x)").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("f(1)").parse()))
    }

    @Test fun entryHelpersPreviewWhileTyping() {
        assertFalse(requiresExplicitEvaluation(Parser("log(100)").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("log(100,10)").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("nthroot(8,3)").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("mixed(1,1,2)").parse()))
    }
}

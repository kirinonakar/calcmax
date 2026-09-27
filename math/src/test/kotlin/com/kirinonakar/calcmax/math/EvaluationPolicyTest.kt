package com.kirinonakar.calcmax.math

import org.junit.Assert.*
import org.junit.Test

class EvaluationPolicyTest {
    @Test fun multiArgumentCallsWaitForEquals() {
        assertTrue(requiresExplicitEvaluation(Parser("rnd()",true).parse()))
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
        assertFalse(requiresExplicitEvaluation(Parser("mod(17,5)").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("divmod(17,5)").parse()))
    }

    @Test fun distributionAndFinanceEntriesWaitForEquals() {
        assertFalse(requiresExplicitEvaluation(Parser("normcdf(0)").parse()))
        assertFalse(requiresExplicitEvaluation(Parser("invnorm(0.975)").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("tcdf(2.228)").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("invt(0.9)").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("npv(0.1)").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("tvmpmt(360,0.05/12,250000)").parse()))
        assertTrue(requiresExplicitEvaluation(Parser("cagr(1000)").parse()))
    }
}

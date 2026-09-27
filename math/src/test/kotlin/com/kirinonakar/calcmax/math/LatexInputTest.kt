package com.kirinonakar.calcmax.math

import org.junit.Assert.*
import org.junit.Test

class LatexInputTest {
    @Test fun fractionPaste() {
        val converted=LatexInput.convert("\\[\\frac{x^2+1}{x-1}\\]")
        assertEquals("(x^2+1)/(x-1)",converted)
        assertEquals("/",Parser(converted!!).parse().value)
    }

    @Test fun definiteIntegralPaste() {
        val converted=LatexInput.convert("\\[\\int_{1}^{e}\\frac{1}{x}\\,dx=1\\]")
        assertNotNull(converted)
        val tree=Parser(converted!!).parse()
        assertEquals("relation",tree.kind)
        assertEquals("integrate",tree.args[0].value)
        assertEquals(listOf("x","1","e"),tree.args[0].args.drop(1).map {it.value})
    }

    @Test fun gaussianIntegralPaste() {
        val converted=LatexInput.convert("$$\\int_{0}^{\\infty} e^{-x^2} \\times \\cos(2x) \\, dx$$")
        assertNotNull(converted)
        val tree=Parser(converted!!).parse()
        assertEquals("integrate",tree.value)
        assertEquals("oo",tree.args[3].value)
        assertEquals("*",tree.args[0].value)
    }

    @Test fun gaussianIntegralPasteWithImplicitProduct() {
        val converted=LatexInput.convert("$$\\int_{0}^{\\infty} e^{-x^2} \\cos(2x) \\, dx$$")
        val tree=Parser(converted!!).parse()
        assertEquals("integrate",tree.value)
        assertEquals("*",tree.args[0].value)
        assertEquals("∘",tree.args[0].displayOperator)
    }

    @Test fun functionAfterPowerIsImplicit() {
        val inserted=Editor("e^(-x^2)").insert("cos()",4)
        assertEquals("e^(-x^2)cos()",inserted.source)
        val tree=Parser("e^(-x^2)cos(2x)").parse()
        assertEquals("*",tree.value)
        assertEquals("∘",tree.displayOperator)
    }
}

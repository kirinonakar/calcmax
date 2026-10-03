package com.kirinonakar.calcmax.math

import org.junit.Assert.*
import org.junit.Test

class LatexInputTest {
    @Test fun fractionPaste() {
        val converted=LatexInput.convert("\\[\\frac{x^2+1}{x-1}\\]")
        assertEquals("((x^2+1)/(x-1))",converted)
        assertEquals("/",Parser(converted!!).parse().args[0].value)
    }

    @Test fun indexedRootAndFractionalPowerPaste() {
        val body="\\sqrt[3]{5} \\times 25^{\\frac{1}{3}}"
        for(source in listOf(body,"$$${body}$$","$${body}$","\\[$body\\]","\\($body\\)")) {
            val converted=LatexInput.convert(source)
            assertEquals("nthroot(5,3)*25^(((1)/(3)))",converted)
            val tree=Parser(converted!!).parse()
            assertEquals("*",tree.value)
            assertEquals("nthroot",tree.args[0].value)
            assertEquals(listOf("5","3"),tree.args[0].args.map {it.value})
            assertEquals("^",tree.args[1].value)
        }
    }

    @Test fun nestedRootsAndExpressionDegrees() {
        assertEquals("nthroot(sqrt(((1)/(2))),3)",LatexInput.convert("\\sqrt [3] {\\sqrt{\\frac{1}{2}}}"))
        assertEquals("nthroot(nthroot(64,3),1+1)",LatexInput.convert("\\sqrt[1+1]{\\sqrt[3]{64}}"))
        assertEquals("nthroot(16,((4)/(2)))",LatexInput.convert("\\sqrt[\\frac{4}{2}]{16}"))
        assertEquals("nthroot(-8,3)",LatexInput.convert("\\sqrt[3]{-8}"))
    }

    @Test fun wrappedLatexReplacesSelectionAndRetainsSurroundingExpressionAndCaret() {
        val previous=Editor("1+2+3",3,2)
        val converted=LatexInput.convertEdit(previous,"1+$$\\sqrt[3]{5} \\times 25^{\\frac{1}{3}}$$+3")!!
        assertEquals("1+nthroot(5,3)*25^(((1)/(3)))+3",converted.source)
        assertEquals(converted.source.length-2,converted.cursor)
        assertEquals(converted.cursor,converted.anchor)
        assertNotNull(converted.tree())
        assertEquals("1+sqrt(4)",LatexInput.convertEdit(Editor("1+"),"1+$\\sqrt{4}$")!!.source)
        assertNull(LatexInput.convertEdit(Editor("1+"),"1+2"))
        assertNull(LatexInput.convertEdit(previous,"1+$$\\sqrt[3]$$+3"))
    }

    @Test fun fractionsRemainWholePowerBases() {
        for(command in listOf("frac","dfrac","tfrac")) {
            val converted=LatexInput.convert("\\$command{1}{2}^2")
            assertEquals("((1)/(2))^2",converted)
            val tree=Parser(converted!!).parse()
            assertEquals("^",tree.value)
            assertEquals("/",tree.args[0].args[0].value)
        }
        assertEquals("ln(2)",LatexInput.convert("\\ln(2)"))
        assertEquals("25^(1/3)",LatexInput.convert("25^{1/3}"))
    }

    @Test fun incompleteOrUnsupportedLatexDoesNotConvert() {
        for(source in listOf("\\sqrt[3{5}","\\sqrt[]{5}","\\sqrt[3]{}","\\sqrt[3]{5", "\\sqrt[3]", "\\frac{1}","$$\\unknown{1}$$","$$\\sqrt{5}$")) {
            assertNull(source,LatexInput.convert(source))
        }
        assertNull(LatexInput.convert("1/3+1/6"))
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

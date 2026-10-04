package com.kirinonakar.calcmax.math

import org.junit.Assert.*
import org.junit.Test

class LatexInputTest {
    @Test fun trigonometricFractionsKeepFunctionsAndTheirPowersSeparate() {
        val body="\\frac{\\sin\\theta}{1-\\cos^2\\theta}"
        for(source in listOf(body,"$$${body}$$","$${body}$","\\[$body\\]","\\($body\\)")) {
            val converted=LatexInput.convert(source)
            assertEquals("((sin(theta))/(1-cos(theta)^(2)))",converted)
            val nodes=Parser(converted!!).parse().nodes()
            assertEquals(listOf("sin","cos"),nodes.filter {it.kind=="call"}.map {it.value})
            assertEquals(listOf("theta","theta"),nodes.filter {it.kind=="symbol"}.map {it.value})
            assertEquals("cos",nodes.first {it.kind=="binary"&&it.value=="^"}.args[0].value)
        }
        assertEquals("sin(x^2)",LatexInput.convert("\\sin x^2"))
        assertEquals("sin(cos(theta)^(2))",LatexInput.convert("\\sin\\cos^2\\theta"))
        assertEquals("theta",Parser("θ").parse().value)
        assertEquals("theta",Parser("sin(θ)^2").parse().args[0].args[0].value)
        for(source in listOf("\\sin", "\\cos^2", "\\sin^", "\\sin^{} x"))assertNull(source,LatexInput.convert(source))
    }

    @Test fun thetaEquationPaste() {
        val body="\\cos\\left(\\frac{\\pi}{2} + \\theta\\right) = -\\frac{1}{5}"
        for(source in listOf(body,"$$${body}$$","$${body}$","\\[$body\\]","\\($body\\)")) {
            val converted=LatexInput.convert(source)
            assertEquals("cos(((pi)/(2))+theta)=-((1)/(5))",converted)
            val tree=Parser(converted!!).parse()
            assertEquals("relation",tree.kind)
            assertEquals("cos",tree.args[0].value)
            assertEquals(listOf("pi","theta"),tree.nodes().filter {it.kind=="symbol"}.map {it.value})
        }
        assertEquals("theta",LatexInput.convert("\\theta"))
        assertEquals("theta^(2)",LatexInput.convert("\\theta^{2}"))
        assertNull(LatexInput.convert("$$\\thetaUnknown$$"))
    }

    @Test fun thetaPasteReplacesSelectionAndKeepsCaret() {
        val converted=LatexInput.convertEdit(Editor("1+x+3",3,2),"1+$\\theta$+3")!!
        assertEquals("1+theta+3",converted.source)
        assertEquals(7,converted.cursor)
        assertEquals(converted.cursor,converted.anchor)
        assertNotNull(converted.tree())
    }

    @Test fun logarithmPasteKeepsBaseAndArgumentSeparate() {
        val body="a = 2 \\log \\frac{1}{\\sqrt{10}} + \\log_2 20 "
        for(source in listOf(body,"$$${body}$$","$${body}$","\\[$body\\]","\\($body\\)")) {
            val converted=LatexInput.convert(source)
            assertEquals("a=2log((1)/(sqrt(10)))+log(20,2)",converted)
            val tree=Parser(converted!!).parse()
            assertEquals("relation",tree.kind)
            assertEquals(listOf("a"),tree.nodes().filter {it.kind=="symbol"}.map {it.value})
            val basedLog=tree.args[1].args[1]
            assertEquals("call",basedLog.kind)
            assertEquals("log",basedLog.value)
            assertEquals(listOf("20","2"),basedLog.args.map {it.value})
        }
        for((source,expected) in listOf(
            "\\log_{10}{100}" to "log(100,10)",
            "\\log_2 \\left(20\\right)" to "log(20,2)",
            "\\log_2 \\frac{1}{\\sqrt{10}}" to "log((1)/(sqrt(10)),2)",
            "\\log_{\\sqrt{2}} 4" to "log(4,sqrt(2))",
            "\\log_2 x^2+1" to "log(x^2,2)+1",
            "\\log_2 \\log_3 9" to "log(log(9,3),2)",
            "\\log\\,100" to "log(100)",
            "\\log(8,2)" to "log(8,2)"
        ))assertEquals(source,expected,LatexInput.convert(source))
        for(source in listOf("\\log_", "\\log_2", "\\log_{} 20", "\\log_2 +20", "\\log_{2 20")) {
            assertNull(source,LatexInput.convert(source))
        }
    }

    @Test fun logarithmEquationRemovesOperandFencesAndPreservesArgumentPowers() {
        val body="\\log_{2}(x-3) = \\log_{4}(3x-5)"
        for(source in listOf(body,"$$${body}$$","$${body}$","\\[$body\\]","\\($body\\)")) {
            val converted=LatexInput.convert(source)
            assertEquals("log(x-3,2)=log(3x-5,4)",converted)
            val tree=Parser(converted!!).parse()
            assertEquals("relation",tree.kind)
            assertEquals(listOf(listOf("binary","number"),listOf("binary","number")),tree.args.map {log->log.args.map {it.kind}})
        }
        assertEquals("log(x-3,2)=log(3x-5,4)",LatexInput.convert("\\log_{2}\\left(x-3\\right)=\\log_{4}{3x-5}"))
        assertEquals("log((x-3)^2,2)",LatexInput.convert("\\log_{2}(x-3)^2"))
        assertEquals("log(x-3,1+1)",LatexInput.convert("\\log_{1+1}((x-3))"))
    }

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

    @Test fun integralEquationsAllowWhitespaceAroundBoundsAndDifferentials() {
        for(body in listOf(
            "\\int _{-2}^{a} f(x) dx = \\int _{-2}^{0} f(x) dx",
            "\\int_{-2}^{a} f(x) dx = \\int_{-2}^{0} f(x) dx",
            "\\int _ {-2} ^ {a} f(x) \\, d x = \\int _ {-2} ^ {0} f(x) \\, d x"
        ))for(source in listOf(body,"$$${body}$$","$${body}$","\\[$body\\]","\\($body\\)")) {
            val converted=LatexInput.convert(source)
            assertEquals(source,"integrate(f(x),x,-2,a)=integrate(f(x),x,-2,0)",converted)
            val tree=Parser(converted!!).parse()
            assertEquals("relation",tree.kind)
            assertEquals(listOf("integrate","integrate"),tree.args.map {it.value})
            assertEquals(listOf("a","0"),tree.args.map {it.args[3].value})
        }
        for(source in listOf("\\int _{-2} f(x) dx","\\int _{-2}^{a} f(x)","\\int _{}^{a} f(x) dx")) {
            assertNull(source,LatexInput.convert(source))
        }
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

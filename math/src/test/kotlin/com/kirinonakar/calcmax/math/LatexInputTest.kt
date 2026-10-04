package com.kirinonakar.calcmax.math

import org.junit.Assert.*
import org.junit.Test

class LatexInputTest {
    @Test fun extendedLatexMatchesSharedWebFixtures() {
        val cases=javaClass.getResourceAsStream("/latex-input.tsv")!!.bufferedReader().readLines()
        for(line in cases) {
            val (source,expected)=line.split('\t')
            for(wrapped in listOf(source,"$${source}$","$$${source}$$","\\[$source\\]","\\($source\\)")) {
                assertEquals(wrapped,expected,LatexInput.convert(wrapped))
                assertNotNull(Parser(expected).parse())
            }
        }
        for(name in LatexInput.symbolLabels.keys)assertEquals(name,LatexInput.convert("\\$name"))
        for(source in listOf("\\sum k","\\sum_{k}^{3} k","\\prod_{k=1} k","\\int_0 x dx","\\int_0^1 x","\\int x dxfoo","\\begin{pmatrix}1&2\\\\3\\end{pmatrix}","\\begin{matrix}1&\\end{matrix}","\\begin{matrix}1","\\begin{cases}x\\end{cases}","\\binom{5}"))assertNull(source,LatexInput.convert(source))
        val paste="\\sum_{k=1}^{5} k^2"
        val edit=LatexInput.convertEdit(Editor("1+x+3",3,2),"1+$${paste}$+3")!!
        assertEquals("1+sum(k^2,k,1,5)+3",edit.source)
        assertEquals(edit.source.length-2,edit.cursor)
    }
    @Test fun limitPastePreservesApproachFunctionPowerAndScope() {
        val body="\\lim_{x \\to 0} \\frac{3x^2}{\\sin^2 x}"
        val expected="limit(3x^2/sin(x)^2,x,0)"
        for(source in listOf(body,"$$${body}$$","$${body}$","\\[$body\\]","\\($body\\)")) {
            assertEquals(expected,LatexInput.convert(source))
            val tree=Parser(LatexInput.convert(source)!!).parse()
            assertEquals("limit",tree.value)
            assertEquals(listOf("x","0"),tree.args.drop(1).map {it.value})
            assertEquals("sin",tree.args[0].args[1].args[0].value)
        }
        for((source,converted) in listOf(
            "\\lim _ {x \\rightarrow \\infty} \\frac{1}{x}" to "limit(1/x,x,oo)",
            "\\lim_{x \\to 0^+} 1/x" to "limit(1/x,x,0,right)",
            "\\lim_{x \\to 0^{-}} 1/x" to "limit(1/x,x,0,left)",
            "\\lim_{\\theta \\to \\pi} \\cos\\theta" to "limit(cos(theta),theta,pi)",
            "(\\lim_{x \\to 0} x)+2" to "(limit(x,x,0))+2",
            "\\left(\\lim_{x \\to 0} x\\right)+2" to "(limit(x,x,0))+2",
            "\\lim_{x \\to 0} x=0" to "limit(x,x,0)=0",
            "\\lim_{x \\to 0} (x+1)" to "limit(x+1,x,0)",
            "\\lim_{x \\to 0} \\frac{3x^2+1}{\\sin^2 x+2}" to "limit((3x^2+1)/(sin(x)^2+2),x,0)",
            "\\lim_{x \\to 0} \\frac{x}{2}^2" to "limit((x/2)^2,x,0)",
            "\\lim_{x \\to 0} x^{\\frac{1}{2}}" to "limit(x^(1/2),x,0)",
            "\\lim_{x \\to 0} \\frac{1}{x(x+1)}" to "limit(1/x(x+1),x,0)",
            "\\lim_{x \\to 0} \\frac{1}{2x}" to "limit(1/(2x),x,0)",
            "\\lim_{x \\to 0} (x)(x+1)" to "limit((x)(x+1),x,0)",
            "\\lim_{x \\to 0} (x^2)^3" to "limit((x^2)^3,x,0)",
            "\\frac{\\lim_{x \\to 0} x+1}{2}" to "((limit(x+1,x,0))/(2))"
        ))assertEquals(source,converted,LatexInput.convert(source))
        for(source in listOf("\\lim","\\lim_x x","\\lim_{x 0} x","\\lim_{x+1 \\to 0} x","\\lim_{x \\to} x","\\lim_{x \\to 0}"))assertNull(source,LatexInput.convert(source))
        val edit=LatexInput.convertEdit(Editor("1+2+3",3,2),"1+$$${body}$$+3")!!
        assertEquals("1+$expected+3",edit.source)
        assertEquals(2+expected.length,edit.cursor)
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

    @Test fun incompleteOrUnsupportedLatexDoesNotConvert() {
        for(source in listOf("\\sqrt[3{5}","\\sqrt[]{5}","\\sqrt[3]{}","\\sqrt[3]{5", "\\sqrt[3]", "\\frac{1}","$$\\unknown{1}$$","$$\\sqrt{5}$")) {
            assertNull(source,LatexInput.convert(source))
        }
        assertNull(LatexInput.convert("1/3+1/6"))
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

}

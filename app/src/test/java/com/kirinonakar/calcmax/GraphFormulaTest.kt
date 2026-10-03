package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.graphEquationTree
import com.kirinonakar.calcmax.ui.graphShadeFormula
import com.kirinonakar.calcmax.ui.regressionFormulaGraphSource
import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.calculator.graphExpressionTarget
import com.kirinonakar.calcmax.calculator.removeGraphSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphFormulaTest {
    @Test fun removalTargetsTheDisplayedOccurrenceAndPreservesShadingAndBlankLines() {
        val source="\n x \n[shade] x, 0\n\nx\nx+1"
        assertEquals("\n x \n[shade] x, 0\n\nx+1",removeGraphSource(source,1))
        assertEquals("\n x \n\nx\nx+1",removeGraphSource(source,0,shading=true))
        assertEquals(source,removeGraphSource(source,8))
    }
    @Test fun everyGraphKindAllowsDeletingTheLastSource() {
        for(kind in listOf("cartesian","parametric","polar","sequence","surface","differential")) {
            assertEquals("",removeGraphSource("x",0,kind))
        }
        assertEquals("y",removeGraphSource("x\ny",0,"surface"))
        assertEquals("x\ny",removeGraphSource("x\ny",1,"surface"))
    }
    @Test fun cartesianTransfersAndCaptionsPreserveFunctionsAndImplicitEquations() {
        for(source in listOf("x+1","y=x+1","y^2+x^2=1","x*y-1","x=2")) {
            assertEquals("cartesian" to source,graphExpressionTarget(source))
            assertNotNull(graphEquationTree("cartesian",source,0))
        }
        assertEquals("surface" to "x^2+y^2",graphExpressionTarget("z=x^2+y^2"))
        assertEquals("0",graphEquationTree("cartesian","x*y-1",0)!!.getJSONArray("args").getJSONObject(1).getString("value"))
        assertEquals("y",graphEquationTree("cartesian","y=x+1",0)!!.getJSONArray("args").getJSONObject(0).getString("value"))
    }
    @Test fun implicitFormulasPreserveEquationsAndAppendZeroToBareExpressions() {
        val equation=graphEquationTree("implicit","x^2+y^2=1",0)!!
        assertEquals("relation",equation.getString("kind"))
        assertEquals("binary",equation.getJSONArray("args").getJSONObject(0).getString("kind"))
        assertEquals("1",equation.getJSONArray("args").getJSONObject(1).getString("value"))
        val bare=graphEquationTree("implicit","x*y-1",0)!!
        assertEquals("0",bare.getJSONArray("args").getJSONObject(1).getString("value"))
    }
    @Test fun eachGraphKindBuildsASymbolicEquation() {
        val cases=listOf(
            Triple("cartesian","sin(x)","f2"),
            Triple("parametric","[cos(t),sin(t)]","list"),
            Triple("polar","2*cos(t)","r"),
            Triple("sequence","u(n-1)+u(n-2)","u"),
            Triple("surface","sin(sqrt(x^2+y^2))","z"),
            Triple("differential","y-t","binary")
        )
        cases.forEach {(kind,source,left)->
            val tree=graphEquationTree(kind,source,1)
            assertNotNull("$kind should have a formula",tree)
            assertEquals("relation",tree!!.getString("kind"))
            val lhs=tree.getJSONArray("args").getJSONObject(0)
            assertEquals(if(left=="binary")"binary" else if(left=="list")"list" else if(left=="z")"symbol" else "call",lhs.getString("kind"))
            if(left !in listOf("binary","list"))assertEquals(left,lhs.getString("value"))
        }
    }

    @Test fun shadedFunctionsAndRangeStayAsExpressionTrees() {
        val shade=graphShadeFormula("[shade] sin(x), cos(x), -pi..pi")!!
        assertEquals(2,shade.expressions.size)
        assertEquals("call",shade.expressions[0].getString("kind"))
        val range=shade.range ?: error("Expected shade range")
        assertEquals("unary",range.first.getString("kind"))
        assertEquals("symbol",range.second.getString("kind"))
    }

    @Test fun graphCaptionsRoundOnlyNumericFractions() {
        val surface=graphEquationTree("surface","x/2+y/3",0)!!
        val surfaceTerms=surface.getJSONArray("args").getJSONObject(1).getJSONArray("args")
        assertEquals("0.500",surfaceTerms.getJSONObject(0).getJSONArray("args").getJSONObject(0).getString("value"))
        assertEquals("0.333",surfaceTerms.getJSONObject(1).getJSONArray("args").getJSONObject(0).getString("value"))

        val differential=graphEquationTree("differential","y/2",0)!!
        val sides=differential.getJSONArray("args")
        assertEquals("/",sides.getJSONObject(0).getString("value"))
        assertEquals("0.500",sides.getJSONObject(1).getJSONArray("args").getJSONObject(0).getString("value"))

        val shade=graphShadeFormula("[shade] y<x/2, 1/3..2/3")!!
        assertEquals("0.500",shade.expressions[0].getJSONArray("args").getJSONObject(1).getJSONArray("args").getJSONObject(0).getString("value"))
        val bounds=shade.range ?: error("Expected shade range")
        assertEquals("0.333",bounds.first.getString("value"))
        assertEquals("0.667",bounds.second.getString("value"))
    }

    @Test fun transferredRegressionAndGraphCaptionUseDisplayDigits() {
        val source="0.123456789*x + x/7"
        val sent=regressionFormulaGraphSource(source,5)!!
        Parser(sent).parse()
        assertTrue(sent.contains("0.12346"))
        assertTrue(sent.contains("0.14286"))
        val caption=graphEquationTree("cartesian",sent,0,5)!!.toString()
        assertTrue(caption.contains("0.12346"))
        assertTrue(caption.contains("0.14286"))
        val finer=graphEquationTree("cartesian","0.123456789*x + x/7",0,8)!!.toString()
        assertTrue(finer.contains("0.12345679"))
        assertTrue(finer.contains("0.14285714"))
    }
}

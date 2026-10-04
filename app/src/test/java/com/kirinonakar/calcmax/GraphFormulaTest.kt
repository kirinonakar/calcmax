package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.calculator.appendGraphSource
import com.kirinonakar.calcmax.calculator.graphExpressionTarget
import com.kirinonakar.calcmax.calculator.graphInputTree
import com.kirinonakar.calcmax.calculator.loadGraphColors
import com.kirinonakar.calcmax.calculator.normalizeGraphColors
import com.kirinonakar.calcmax.calculator.removeGraphSource
import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.ui.graphEquationTree
import com.kirinonakar.calcmax.ui.graphTracePointAtX
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GraphFormulaTest {
    @Test fun namedCartesianInputsPlotTheirBodiesAndKeepTheirCaptions() {
        for(source in listOf("f(x)=x+1","g(x)=sin(x)","f2(x)=2*x^2")) {
            assertEquals(Parser(source).parse().args[1].json(),graphInputTree(source).json())
            assertEquals("cartesian" to source,graphExpressionTarget(source))
            assertEquals(Parser(source).parse().args[0].value,graphEquationTree("cartesian",source,0)!!.getJSONArray("args").getJSONObject(0).getString("value"))
        }
        for(source in listOf("y=x+1","sin(x)=0","x^2+y^2=1"))assertEquals(Parser(source).parse().json(),graphInputTree(source).json())
        assertEquals(Parser("f(x)=x+1").parse().json(),graphInputTree("f(x)=x+1","implicit").json())
    }
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
}

class GraphTraceTest {
    @Test fun implicitCurvesChooseTheBranchNearTheTouchedY() {
        val curve=listOf(-2.0 to 2.0,2.0 to 2.0,null,2.0 to -2.0,-2.0 to -2.0)
        assertEquals(0.5 to 2.0,graphTracePointAtX(curve,0.5,1.0))
        assertEquals(0.5 to -2.0,graphTracePointAtX(curve,0.5,-1.0))
        assertEquals(0.0 to 0.5,graphTracePointAtX(listOf(0.0 to -2.0,0.0 to 2.0),0.0,0.5))
    }

    @Test fun missingAndNonFiniteSamplesDoNotConnectUnrelatedCurveSegments() {
        for(gap in listOf(null,0.0 to Double.NaN,Double.POSITIVE_INFINITY to 0.0)) {
            assertNull(graphTracePointAtX(listOf(-1.0 to -1.0,gap,1.0 to 1.0),0.0,0.0))
        }
        assertNull(graphTracePointAtX(emptyList(),0.0,0.0))
        assertNull(graphTracePointAtX(listOf(0.0 to 0.0,1.0 to 1.0),2.0,0.0))
        assertNull(graphTracePointAtX(listOf(0.0 to 0.0),Double.NaN,0.0))
        assertEquals(1.0 to 1.0,graphTracePointAtX(listOf(1.0 to 1.0),1.0,0.0))
    }
}

class GraphColorsTest {
    @Test fun sixIndependentOverridesAndIndividualResetsSurviveStorage() {
        val colors=listOf("#123456","#abcdef","#000000","#ffffff","#ff0000","#00ff00")
        assertEquals(colors,loadGraphColors(JSONArray(colors).toString()))
        val reset=normalizeGraphColors(colors.mapIndexed {i,color->if(i==3)null else color})
        assertEquals(colors.take(3)+listOf(null)+colors.drop(4),loadGraphColors(JSONArray(reset).toString()))
        assertEquals(colors,normalizeGraphColors(colors+"#112233"))
    }
}

class GraphDisplayFormatTest {
    @Test fun transfersAppendAndPreserveEquationsAndShading() {
        val source="sin(x)\ny=x+1\n[shade] y<x"
        assertEquals("$source\nx^2+y^2=1",appendGraphSource(source,"x^2+y^2=1"))
        assertEquals(source,appendGraphSource(source,"y=x+1"))
        assertEquals("x",appendGraphSource("","x"))
        assertThrows(IllegalArgumentException::class.java) {appendGraphSource("x\n2*x\n3*x\n4*x\n5*x\n6*x","7*x")}
        assertThrows(IllegalArgumentException::class.java) {appendGraphSource("x+y","x-y","surface")}
    }
}

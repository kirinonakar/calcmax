package com.kirinonakar.symvacas

import com.kirinonakar.symvacas.calculator.appendGraphSource
import com.kirinonakar.symvacas.calculator.loadGraphColors
import com.kirinonakar.symvacas.calculator.normalizeGraphColors
import com.kirinonakar.symvacas.calculator.removeGraphSource
import com.kirinonakar.symvacas.calculator.removeGraphInputLine
import com.kirinonakar.symvacas.calculator.graphExpressionTarget
import com.kirinonakar.symvacas.calculator.isImplicitSurface
import com.kirinonakar.symvacas.calculator.hasImplicitSurface
import com.kirinonakar.symvacas.calculator.graphCurveLimit
import com.kirinonakar.symvacas.calculator.graphShadeEntry
import com.kirinonakar.symvacas.calculator.graphShadingBody
import com.kirinonakar.symvacas.calculator.isGraphShading
import com.kirinonakar.symvacas.ui.graphTracePointAtX
import com.kirinonakar.symvacas.ui.graphSelectedCurveIndex
import com.kirinonakar.symvacas.calculator.graphAnalysisTarget
import com.kirinonakar.symvacas.calculator.GraphAnalysisTarget
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GraphFormulaTest {
    @Test fun fiveThreeDimensionalGraphsAllowDeletingTheLastAndDetectLaterImplicitRows() {
        for(kind in listOf("surface","space")) {
            var source="";for(i in 1..5)source=appendGraphSource(source,"x+$i",kind)
            val full=source
            assertEquals(5,graphCurveLimit(kind))
            assertThrows(IllegalArgumentException::class.java){appendGraphSource(full,"x+6",kind)}
            assertEquals(source.lines().dropLast(1).joinToString("\n"),removeGraphSource(source,4,kind))
        }
        assertTrue(hasImplicitSurface("z=x+y\nx^2+y^2+z^2=a"))
        assertFalse(hasImplicitSurface("z=x+y\nz=x-y"))
        assertEquals(1,graphCurveLimit("differential"));assertEquals(20,graphCurveLimit("cartesian"))
    }
    @Test fun threeDimensionalTransferPreservesImplicitEquationsAndScaledCurves() {
        val implicit="x^2+y^2+z^2+sin(4*x)+sin(4*y)+sin(4*z)=a"
        assertEquals("surface" to implicit,graphExpressionTarget(implicit));assertTrue(isImplicitSurface(implicit))
        assertFalse(isImplicitSurface("z=sin(x)*cos(y)"))
        assertEquals("surface" to "z=x+z",graphExpressionTarget("z=x+z"))
        val curve="C(t)=4*(sin(t),cos(t),0.6*sin(2*t))"
        assertEquals("space" to curve,graphExpressionTarget(curve))
    }
    @Test fun twentyCurvesAllowShadingAndDeletingTheLastCurve() {
        var source=(1..4).joinToString("\n") {"[s] $it<x<${it+1},y<1"}
        for(index in 1..20)source=appendGraphSource(source,"x+$index")
        assertEquals(24,source.lines().size)
        val full=source
        assertThrows(IllegalArgumentException::class.java) {appendGraphSource(full,"x+21")}
        assertEquals(source.lines().dropLast(1).joinToString("\n"),removeGraphSource(source,19))
    }
    @Test fun inputLineDeletionIncludesBlankAndDuplicateLines() {
        val source="x\n\nx\n[s] y<x\n"
        assertEquals("x\nx\n[s] y<x\n",removeGraphInputLine(source,1))
        assertEquals("x\n\n[s] y<x\n",removeGraphInputLine(source,2))
        assertEquals("x\n\nx\n[s] y<x",removeGraphInputLine(source,4))
        assertEquals("",removeGraphInputLine("x",0))
        assertEquals(source,removeGraphInputLine(source,20))
    }
    @Test fun analysisTargetsDistinguishDerivativesFromTheirSourceForIntersections() {
        val original=graphAnalysisTarget(0,0,0)
        val first=graphAnalysisTarget(-1,0,0)
        val second=graphAnalysisTarget(-2,0,0)
        assertEquals(GraphAnalysisTarget(0,0),original);assertEquals(GraphAnalysisTarget(0,1),first);assertEquals(GraphAnalysisTarget(0,2),second)
        assertNotEquals(original,first);assertNotEquals(first,second)
        assertEquals(GraphAnalysisTarget(2,1),graphAnalysisTarget(-1,2,1))
        assertNull(graphAnalysisTarget(-1,null,0));assertNull(graphAnalysisTarget(-2,0,null))
    }
    @Test fun selectedDerivativesTraceTheirOwnSamplesAndFollowResponseIndices() {
        val curves=listOf(listOf(-1.0 to -1.0,1.0 to 1.0),listOf(-1.0 to 2.0,1.0 to 2.0),listOf(-1.0 to 6.0,1.0 to 6.0))
        for((order,expected) in listOf(0 to .5,1 to 2.0,2 to 6.0)) {
            val index=graphSelectedCurveIndex(0,order,1,2)
            assertEquals(.5 to expected,graphTracePointAtX(curves[index],.5,0.0))
        }
        val withoutFirst=listOf(curves[0],curves[2])
        assertEquals(.5 to 6.0,graphTracePointAtX(withoutFirst[graphSelectedCurveIndex(0,2,-1,1)],.5,0.0))
        assertEquals(-1,graphSelectedCurveIndex(0,1))
        assertEquals(-1,graphSelectedCurveIndex(0,2))
    }
    @Test fun chainedShadingBoundsAndAliasPreserveCurveIndices() {
        run { // chainedShadingBoundsAndAliasPreserveCurveIndices
            for(prefix in listOf("[shade]","[s]")) {
                val source="$prefix 1<x<3, 1<y<3"
                assertTrue(isGraphShading(source))
                val entry=graphShadeEntry(graphShadingBody(source))
                assertEquals("region",entry.getString("mode"))
                assertEquals(listOf("1","3","1","3"),(0..3).map {entry.getJSONArray("trees").getJSONObject(it).getString("value")})
                assertEquals(listOf("x","x","y","y"),(0..3).map {entry.getJSONArray("constraints").getJSONObject(it).getString("axis")})
                assertEquals(listOf("lower","upper","lower","upper"),(0..3).map {entry.getJSONArray("constraints").getJSONObject(it).getString("side")})
                assertEquals(source,removeGraphSource("x\n$source",0))
                assertEquals("x",removeGraphSource("x\n$source",0,shading=true))
                assertEquals("$source\nx",appendGraphSource(source,"x"))
            }
            assertEquals("halfplane",graphShadeEntry("y<x^2").getString("mode"))
            assertEquals("band",graphShadeEntry("sin(x),cos(x),0..pi").getString("mode"))
            val reverse=graphShadeEntry("3>=x>1,3>y>=1")
            assertEquals(listOf("upper","lower","upper","lower"),(0..3).map {reverse.getJSONArray("constraints").getJSONObject(it).getString("side")})
            for(source in listOf("x<x+1","y<y+1","1<y<3, x, x^2, x^3","x=1"))assertThrows(IllegalArgumentException::class.java) {graphShadeEntry(source)}
        }
        run { // shadingFunctionsCombineXAndYConstraints
            for(source in listOf("sin(x), cos(x), -1<x<2, y<0","y<0, -1<x<2, sin(x), cos(x)")) {
                val entry=graphShadeEntry(source)
                assertEquals("band",entry.getString("mode"))
                assertEquals(2,entry.getJSONArray("trees").length())
                assertEquals(2,entry.getJSONArray("xBounds").length())
                val bounds=entry.getJSONArray("yBounds")
                assertEquals(1,bounds.length());assertEquals("upper",bounds.getJSONObject(0).getString("side"))
                assertEquals("0",bounds.getJSONObject(0).getJSONObject("tree").getString("value"))
            }
        }
    }

    @Test fun removalTargetsTheDisplayedOccurrenceAndPreservesShadingAndBlankLines() {
        run { // removalTargetsTheDisplayedOccurrenceAndPreservesShadingAndBlankLines
            val source="\n x \n[shade] x, 0\n\nx\nx+1"
            assertEquals("\n x \n[shade] x, 0\n\nx+1",removeGraphSource(source,1))
            assertEquals("\n x \n\nx\nx+1",removeGraphSource(source,0,shading=true))
            assertEquals(source,removeGraphSource(source,8))
        }
        run { // everyGraphKindAllowsDeletingTheLastSource
            for(kind in listOf("cartesian","parametric","polar","sequence","surface","differential")) {
                assertEquals("",removeGraphSource("x",0,kind))
            }
            assertEquals("y",removeGraphSource("x\ny",0,"surface"))
            assertEquals("x",removeGraphSource("x\ny",1,"surface"))
            assertEquals("x\ny",removeGraphSource("x\ny",1,"differential"))
        }
    }

}

class GraphTraceTest {
    @Test fun implicitCurvesChooseTheBranchNearTheTouchedY() {
        run { // implicitCurvesChooseTheBranchNearTheTouchedY
            val curve=listOf(-2.0 to 2.0,2.0 to 2.0,null,2.0 to -2.0,-2.0 to -2.0)
            assertEquals(0.5 to 2.0,graphTracePointAtX(curve,0.5,1.0))
            assertEquals(0.5 to -2.0,graphTracePointAtX(curve,0.5,-1.0))
            assertEquals(0.0 to 0.5,graphTracePointAtX(listOf(0.0 to -2.0,0.0 to 2.0),0.0,0.5))
        }
        run { // missingAndNonFiniteSamplesDoNotConnectUnrelatedCurveSegments
            for(gap in listOf(null,0.0 to Double.NaN,Double.POSITIVE_INFINITY to 0.0)) {
                assertNull(graphTracePointAtX(listOf(-1.0 to -1.0,gap,1.0 to 1.0),0.0,0.0))
            }
            assertNull(graphTracePointAtX(emptyList(),0.0,0.0))
            assertNull(graphTracePointAtX(listOf(0.0 to 0.0,1.0 to 1.0),2.0,0.0))
            assertNull(graphTracePointAtX(listOf(0.0 to 0.0),Double.NaN,0.0))
            assertEquals(1.0 to 1.0,graphTracePointAtX(listOf(1.0 to 1.0),1.0,0.0))
        }
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

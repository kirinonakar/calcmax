package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.calculator.appendGraphSource
import com.kirinonakar.calcmax.calculator.graphExpressionTarget
import com.kirinonakar.calcmax.calculator.graphInputTree
import com.kirinonakar.calcmax.calculator.loadGraphColors
import com.kirinonakar.calcmax.calculator.normalizeGraphColors
import com.kirinonakar.calcmax.calculator.removeGraphSource
import com.kirinonakar.calcmax.calculator.graphShadeEntry
import com.kirinonakar.calcmax.calculator.graphShadingBody
import com.kirinonakar.calcmax.calculator.isGraphShading
import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.ui.graphEquationTree
import com.kirinonakar.calcmax.ui.graphTracePointAtX
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GraphFormulaTest {
    @Test fun chainedShadingBoundsAndAliasPreserveCurveIndices() {
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
    @Test fun shadingFunctionsAcceptInequalityIntervals() {
        for(source in listOf("sin(x),cos(x),-pi<x<pi","sin(x),cos(x),pi>=x>=-pi")) {
            val entry=graphShadeEntry(source)
            assertEquals("band",entry.getString("mode"))
            assertEquals(listOf("sin","cos"),(0..1).map {entry.getJSONArray("trees").getJSONObject(it).getString("value")})
            val bounds=entry.getJSONArray("xBounds")
            val lower=(0..1).map {bounds.getJSONObject(it)}.first {it.getString("side")=="lower"}.getJSONObject("tree")
            assertEquals("pi",lower.getJSONArray("args").getJSONObject(0).getString("value"))
        }
        assertEquals("lower",graphShadeEntry("x, x>0").getJSONArray("xBounds").getJSONObject(0).getString("side"))
        for(source in listOf("sin(x),cos(x),y<y+1","x,x^2,x^3,-pi<x<pi"))assertThrows(IllegalArgumentException::class.java) {graphShadeEntry(source)}
    }
    @Test fun shadingFunctionsCombineXAndYConstraints() {
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

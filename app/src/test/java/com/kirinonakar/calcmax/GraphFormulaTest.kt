package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.graphEquationTree
import com.kirinonakar.calcmax.ui.graphShadeFormula
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GraphFormulaTest {
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
}

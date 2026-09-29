package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.decimalFractionFormulaTree
import com.kirinonakar.calcmax.ui.regressionFormulaDisplayTree
import com.kirinonakar.calcmax.math.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegressionFormulaTest {
    @Test fun customRegressionExampleParsesWithIndependentVariableAndBounds() {
        val tree=Parser("regression([[0,1],[10,0.9],[20,0.8]],custom,(1-f)*exp(-b*D)+f*exp(-b*Dstar),b,[[f,0.2,0,1],[D,0.001,0]])").parse()
        assertEquals("regression",tree.value)
        assertEquals(5,tree.args.size)
        assertEquals("custom",tree.args[1].value)
        assertEquals("b",tree.args[3].value)
    }

    @Test fun fractionalCoefficientsBecomeThreePlaceDecimals() {
        val sum=decimalFractionFormulaTree("x/2 + 1/3")!!
        val terms=sum.getJSONArray("args")
        val slope=terms.getJSONObject(0)
        assertEquals("*",slope.getString("value"))
        assertEquals("0.500",slope.getJSONArray("args").getJSONObject(0).getString("value"))
        assertEquals("0.333",terms.getJSONObject(1).getString("value"))

        val combined=decimalFractionFormulaTree("3*x/2")!!
        assertEquals("1.500",combined.getJSONArray("args").getJSONObject(0).getString("value"))
        assertEquals("0.667",decimalFractionFormulaTree("2/3")!!.getString("value"))
    }

    @Test fun nestedFractionsAreRoundedButSymbolicDivisionRemains() {
        val exponential=decimalFractionFormulaTree("exp(x/3 + 1/2)")!!
        val sum=exponential.getJSONArray("args").getJSONObject(0)
        assertEquals("0.333",sum.getJSONArray("args").getJSONObject(0).getJSONArray("args").getJSONObject(0).getString("value"))
        assertEquals("0.500",sum.getJSONArray("args").getJSONObject(1).getString("value"))
        assertEquals("/",decimalFractionFormulaTree("1/x")!!.getString("value"))
    }

    @Test fun fittedFormulaUsesConfiguredFractionalPlaces() {
        val source="0.123456789*x + 1/7"
        val five=regressionFormulaDisplayTree(source,5)!!.toString()
        val ten=regressionFormulaDisplayTree(source,10)!!.toString()
        assertTrue(five.contains("0.12346"))
        assertTrue(five.contains("0.14286"))
        assertTrue(ten.contains("0.123456789"))
        assertTrue(ten.contains("0.1428571429"))
    }
}

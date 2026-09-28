package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.decimalFractionFormulaTree
import org.junit.Assert.assertEquals
import org.junit.Test

class RegressionFormulaTest {
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
}

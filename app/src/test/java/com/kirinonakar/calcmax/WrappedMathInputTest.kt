package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.ui.inputMathParts
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WrappedMathInputTest {
    private fun parts(source:String)=inputMathParts(JSONObject(Parser(source,true).parse().json()),source)
    @Test fun wrappingKeepsFractionsPowersAndRootsAsMathTrees() {
        val result=parts("1/2+sqrt(2)^3")
        assertEquals(3,result.size)
        assertEquals("/",result[0].getString("value"))
        assertEquals(" + ",result[1].getString("value"))
        assertEquals("^",result[2].getString("value"))
        assertEquals("sqrt",result[2].getJSONArray("args").getJSONObject(0).getString("value"))
        assertEquals(4,result[2].getInt("start"))
        assertEquals(13,result[2].getInt("end"))
    }
    @Test fun parenthesesAndPrecedenceSurviveWrapping() {
        val result=parts("(2+3)*4+5/6")
        assertEquals(5,result.size)
        assertEquals("group",result[0].getString("kind"))
        assertEquals(" × ",result[1].getString("value"))
        assertEquals("/",result.last().getString("value"))
    }
    @Test fun longNumbersRetainEveryDigitAndSourceRange() {
        val source="1234567890".repeat(10)
        val result=parts(source)
        assertEquals(source,result.joinToString(""){it.getString("value")})
        result.forEachIndexed {i,node->assertEquals(i,node.getInt("start"));assertEquals(i+1,node.getInt("end"))}
    }
    @Test fun invalidExpressionsStillExposeEditableMathTokens() {
        val result=inputMathParts(null,"12+*3")
        assertEquals(listOf("12","+","×","3"),result.map{it.getString("value")})
        assertEquals(3,result[2].getInt("start"))
        assertEquals(4,result[2].getInt("end"))
    }
    @Test fun calculusKeepsExactlyTheOriginalRendererNodes() {
        val source="integrate(x^2,x,0,1)+1/2"
        val tree=JSONObject(Parser(source,true).parse().json())
        val result=inputMathParts(tree,source)
        assertEquals(tree.getJSONArray("args").getJSONObject(0).toString(),result[0].toString())
        assertEquals(tree.getJSONArray("args").getJSONObject(1).toString(),result[2].toString())
        val coefficient=parts("2*x+3")
        assertEquals("binary",coefficient[0].getString("kind"))
        assertEquals("*",coefficient[0].getString("value"))
    }
}

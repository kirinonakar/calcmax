package com.kirinonakar.symvacas

import com.kirinonakar.symvacas.calculator.HistoryEntry
import com.kirinonakar.symvacas.calculator.ResultDisplayMode
import com.kirinonakar.symvacas.math.Parser
import com.kirinonakar.symvacas.ui.CopyCycle
import com.kirinonakar.symvacas.ui.ResultDisplayFormat
import com.kirinonakar.symvacas.ui.inputMathParts
import com.kirinonakar.symvacas.ui.mathPreview
import com.kirinonakar.symvacas.ui.resultMathParts
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryPreviewTest {
    private fun entry(source: String = "1/2", exact: String = "1/2", input: String = "", response: String = "") =
        HistoryEntry(1, source, exact, "0.5", "Scientific/CAS", inputTree = input, response = response)

    @Test fun legacyFractionsAndMatricesStillRenderAsMath() {
        val fraction = entry().mathPreview()
        assertNotNull(fraction.input)
        assertNotNull(fraction.response!!.getJSONObject("tree"))
        val matrix = entry(exact = "Matrix([[1,2],[3,4]])").mathPreview()
        assertEquals("list", matrix.response!!.getJSONObject("tree").getString("kind"))
    }

    @Test fun savedTreesAndResponseMetadataArePreserved() {
        val tree = """{"kind":"number","value":"0.5"}"""
        val response = """{"exact":"1/2","decimal":"0.5","tree":$tree,"decimalTree":$tree,"conditions":["x>0"]}"""
        val preview = entry(input = tree, response = response).mathPreview()
        assertEquals("0.5", preview.input!!.getString("value"))
        assertEquals("x>0", preview.response!!.getJSONArray("conditions").getString(0))
    }

    @Test fun deeplyNestedAndWideTreesUseText() {
        var tree = """{"kind":"number","value":"1"}"""
        repeat(100) { tree = """{"kind":"group","args":[$tree]}""" }
        val fallback = entry(response = """{"exact":"1/2","tree":$tree}""").mathPreview().response
        assertNotNull(fallback)
        assertTrue(fallback.toString().length < 500)
        assertNull(entry(exact = "(".repeat(100) + "1" + ")".repeat(100),
            response = """{"exact":"1/2","tree":$tree}""").mathPreview().response)
        val wide = JSONObject().put("exact", "1/2").put("tree", JSONObject().put("kind", "sum")
            .put("args", org.json.JSONArray(List(121) { JSONObject().put("kind", "number").put("value", "1") })))
        assertNull(entry(response = wide.toString()).mathPreview().response)
        assertNull(entry(source = "(".repeat(100) + "1" + ")".repeat(100)).mathPreview().input)
    }

}

class CopyCycleTest {
    @Test fun copyAlternatesBetweenAnswerAndExpression() {
        var copyExpression=false
        val copied=mutableListOf<String>()
        val labels=mutableListOf<String>()
        repeat(4) {
            val target=CopyCycle.next("42","3+4",copyExpression)
            copied+=target.text
            labels+=target.label
            copyExpression=target.expressionNext
        }
        assertEquals(listOf("42","3+4","42","3+4"),copied)
        assertEquals(listOf("Copy =","Copy ƒ","Copy =","Copy ƒ"),labels)
        assertFalse(copyExpression)
    }
    @Test fun selectionTakesPriorityWithoutAdvancingCopyCycle() {
        val target=CopyCycle.next("42","12+30",false,"12")
        assertEquals("12",target.text)
        assertFalse(target.expressionNext)
    }
}

class ResultDisplayFormatTest {
    @Test fun displayDigitsCountFractionalPlacesInPlainAndPowerNotation() {
        fun leaf(value:String)=JSONObject().put("kind","number").put("value",value)
        assertEquals("12342456656.12345",ResultDisplayFormat.formatTree(leaf("12342456656.1234549"),ResultDisplayMode.OFF,false,maxFractionDigits=5).getString("value"))
        assertEquals("12345.12345",ResultDisplayFormat.formatText("12345.1234549",ResultDisplayMode.OFF,false,maxFractionDigits=5))
        assertEquals("12345.12346",ResultDisplayFormat.formatText("12345.123456789",ResultDisplayMode.OFF,false,maxFractionDigits=5))
        assertEquals("123.00011",ResultDisplayFormat.formatText("123.000109876",ResultDisplayMode.OFF,false,maxFractionDigits=5))
        assertEquals("12342456656",ResultDisplayFormat.formatText("12342456656",ResultDisplayMode.OFF,false,maxFractionDigits=5))
        assertEquals("1.23456×10^10",ResultDisplayFormat.formatText("1.23456123e10",ResultDisplayMode.OFF,false,maxFractionDigits=5))
        assertEquals("1.23456×10^10",ResultDisplayFormat.formatText("12345612345.6789",ResultDisplayMode.SCIENTIFIC,false,maxFractionDigits=5))
        val power=ResultDisplayFormat.formatTree(leaf("1.23456123e10"),ResultDisplayMode.OFF,false,maxFractionDigits=5)
        assertEquals("1.23456",power.getJSONArray("args").getJSONObject(0).getString("value"))
    }
}

class WrappedMathInputTest {
    private fun parts(source:String)=inputMathParts(JSONObject(Parser(source,true).parse().json()),source)
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

class WrappedMathResultTest {
    private fun node(kind:String,value:String="",vararg args:JSONObject)=JSONObject().put("kind",kind).put("value",value).put("args",org.json.JSONArray(args.toList()))
    @Test fun complexRootsWrapWithoutLosingSignsBracesOrCommas() {
        val real=node("number","-1.16730397826141868425604589985")
        val imaginary=node("product","",node("number","1.08395410131771066843034449298"),node("text","I"))
        val complex=node("sum","",node("number","-0.181232444469875383901800237781"),node("unary","-",imaginary))
        val result=node("set","",real,complex)
        val before=result.toString()
        val parts=resultMathParts(result)
        assertEquals(3,parts.size)
        assertEquals("{",parts.first().first().getString("value"))
        assertEquals(", ",parts.first().last().getString("value"))
        assertEquals("unary",parts.last()[0].getString("kind"))
        assertEquals("}",parts.last().last().getString("value"))
        assertEquals(before,result.toString())
    }
}

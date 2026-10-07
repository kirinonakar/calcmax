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

class ResultCopyTextTest {
    private fun leaf(value:String)=JSONObject().put("kind","number").put("value",value)
    private fun text(value:String)=JSONObject().put("kind","text").put("value",value)
    private fun args(vararg nodes:JSONObject)=org.json.JSONArray(nodes.toList())

    @Test fun copiedAnswerRoundsToTheDisplayedDigits() {
        val result=JSONObject().put("exact","0.333333333333333333333333333333").put("decimal","0.333333333333333333333333333333")
            .put("decimalTree",leaf("0.333333333333333333333333333333"))
        assertEquals("0.3333333333",ResultDisplayFormat.resultText(result,true,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
    }

    @Test fun copiedAnswerFollowsNotationAndGrouping() {
        val notation=JSONObject().put("exact","12345612345.6789").put("decimal","12345612345.6789").put("decimalTree",leaf("12345612345.6789"))
        assertEquals("1.23456×10^10",ResultDisplayFormat.resultText(notation,true,false,ResultDisplayMode.SCIENTIFIC,false,false,0,false,false,5))
        val grouped=JSONObject().put("exact","1234567.891").put("decimal","1234567.891").put("decimalTree",leaf("1234567.891"))
        assertEquals("1,234,567.891",ResultDisplayFormat.resultText(grouped,true,false,ResultDisplayMode.OFF,true,false,0,false,false,10))
    }

    @Test fun copiedAnswerKeepsFractionsAndRoundsEveryListElement() {
        val fraction=JSONObject().put("exact","1/2").put("decimal","0.5").put("tree",JSONObject().put("kind","fraction").put("args",args(text("1"),text("2"))))
        assertEquals("1/2",ResultDisplayFormat.resultText(fraction,false,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
        val list=JSONObject().put("exact","[1.4142135623730950488016887242097, -1.4142135623730950488016887242097]").put("decimal","[1.4142135623730950488016887242097, -1.4142135623730950488016887242097]")
            .put("decimalTree",JSONObject().put("kind","list").put("args",args(leaf("1.4142135623730950488016887242097"),leaf("-1.4142135623730950488016887242097"))))
        assertEquals("[1.4142135624, -1.4142135624]",ResultDisplayFormat.resultText(list,true,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
    }

    @Test fun copiedAnswerFollowsMixedAndDmsDisplays() {
        val fraction=JSONObject().put("exact","7/2").put("decimal","3.5").put("tree",JSONObject().put("kind","fraction").put("args",args(text("7"),text("2"))))
        assertEquals("3 1/2",ResultDisplayFormat.resultText(fraction,false,true,ResultDisplayMode.OFF,false,false,0,false,false,10))
        val dms=JSONObject().put("exact","12.5125").put("decimal","12.5125").put("dms",true)
            .put("tree",JSONObject().put("kind","dms").put("args",args(leaf("12"),leaf("30"),leaf("45"))))
        assertEquals("12°30′45″",ResultDisplayFormat.resultText(dms,false,false,ResultDisplayMode.OFF,false,false,0,true,false,10))
    }

    @Test fun copiedAnswerRoundsRelationsAndFallsBackForUnknownTrees() {
        val relation=JSONObject().put("exact","Eq(x, 0.73908513321516064165531208767)").put("decimal","x = 0.73908513321516064165531208767")
            .put("tree",JSONObject().put("kind","relation").put("value","=").put("args",args(JSONObject().put("kind","symbol").put("value","x"),leaf("0.73908513321516064165531208767"))))
        assertEquals("x = 0.7390851332",ResultDisplayFormat.resultText(relation,false,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
        val sum=JSONObject().put("exact","0.5 + 0.25").put("decimal","0.5 + 0.25").put("tree",JSONObject().put("kind","sum").put("args",args(leaf("0.5"),leaf("0.25"))))
        assertEquals("0.5 + 0.25",ResultDisplayFormat.resultText(sum,true,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
    }

    @Test fun copiedAnswerRoundsStatisticsRows() {
        val rows=JSONObject().put("kind","rows").put("args",args(
            JSONObject().put("kind","row").put("value","mean").put("args",args(leaf("1.2345678901234567"))),
            JSONObject().put("kind","row").put("value","stdev").put("args",args(leaf("0.9876543210987654")))))
        val result=JSONObject().put("exact","mean: 1.2345678901234567\nstdev: 0.9876543210987654").put("decimal","mean: 1.2345678901234567\nstdev: 0.9876543210987654").put("decimalTree",rows)
        assertEquals("mean: 1.2345678901\nstdev: 0.9876543211",ResultDisplayFormat.resultText(result,true,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
    }
}

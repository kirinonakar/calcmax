package com.kirinonakar.symvacas
import com.kirinonakar.symvacas.ui.negativeFractionNumerator
import com.kirinonakar.symvacas.calculator.EngineResultCodec
import org.json.JSONArray
import kotlin.math.sin

import com.kirinonakar.symvacas.calculator.HistoryEntry
import com.kirinonakar.symvacas.calculator.ResultDisplayMode
import com.kirinonakar.symvacas.math.Parser
import com.kirinonakar.symvacas.ui.CopyCycle
import com.kirinonakar.symvacas.ui.ResultDisplayFormat
import com.kirinonakar.symvacas.ui.mathPreview
import com.kirinonakar.symvacas.ui.resultMathParts
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryPreviewTest {
    private fun entry(source: String = "1/2", exact: String = "1/2", input: String = "", response: String = "") =
        HistoryEntry(1, source, exact, "0.5", "Scientific/CAS", inputTree = input, response = response)

    @Test fun historyRecordsPreserveMetadataAndRejectInvalidMath() {
        run { // legacyFractionsAndMatricesStillRenderAsMath
            val fraction = entry().mathPreview()
            assertNotNull(fraction.input)
            assertNotNull(fraction.response!!.getJSONObject("tree"))
            val matrix = entry(exact = "Matrix([[1,2],[3,4]])").mathPreview()
            assertEquals("list", matrix.response!!.getJSONObject("tree").getString("kind"))
        }
        run { // savedTreesAndResponseMetadataArePreserved
            val tree = """{"kind":"number","value":"0.5"}"""
            val response = """{"exact":"1/2","decimal":"0.5","tree":$tree,"decimalTree":$tree,"conditions":["x>0"]}"""
            val preview = entry(input = tree, response = response).mathPreview()
            assertEquals("0.5", preview.input!!.getString("value"))
            assertEquals("x>0", preview.response!!.getJSONArray("conditions").getString(0))
        }
        run { // deeplyNestedAndWideTreesUseText
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

}

class CopyCycleTest {
    @Test fun copyCyclePreservesSelectionAndAnswerExpressionOrder() {
        run { // copyAlternatesBetweenAnswerAndExpression
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
        run { // selectionTakesPriorityWithoutAdvancingCopyCycle
            val target=CopyCycle.next("42","12+30",false,"12")
            assertEquals("12",target.text)
            assertFalse(target.expressionNext)
        }
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

    @Test fun copiedAnswersMatchPrecisionNotationAndGrouping() {
        run { // copiedAnswerRoundsToTheDisplayedDigits
            val result=JSONObject().put("exact","0.333333333333333333333333333333").put("decimal","0.333333333333333333333333333333")
                .put("decimalTree",leaf("0.333333333333333333333333333333"))
            assertEquals("0.3333333333",ResultDisplayFormat.resultText(result,true,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
        }
        run { // copiedAnswerFollowsNotationAndGrouping
            val notation=JSONObject().put("exact","12345612345.6789").put("decimal","12345612345.6789").put("decimalTree",leaf("12345612345.6789"))
            assertEquals("1.23456×10^10",ResultDisplayFormat.resultText(notation,true,false,ResultDisplayMode.SCIENTIFIC,false,false,0,false,false,5))
            val grouped=JSONObject().put("exact","1234567.891").put("decimal","1234567.891").put("decimalTree",leaf("1234567.891"))
            assertEquals("1,234,567.891",ResultDisplayFormat.resultText(grouped,true,false,ResultDisplayMode.OFF,true,false,0,false,false,10))
        }
    }

    @Test fun copiedStructuredAnswersPreserveFractionsListsAndAngleFormats() {
        run { // copiedAnswerKeepsFractionsAndRoundsEveryListElement
            val fraction=JSONObject().put("exact","1/2").put("decimal","0.5").put("tree",JSONObject().put("kind","fraction").put("args",args(text("1"),text("2"))))
            assertEquals("1/2",ResultDisplayFormat.resultText(fraction,false,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
            val list=JSONObject().put("exact","[1.4142135623730950488016887242097, -1.4142135623730950488016887242097]").put("decimal","[1.4142135623730950488016887242097, -1.4142135623730950488016887242097]")
                .put("decimalTree",JSONObject().put("kind","list").put("args",args(leaf("1.4142135623730950488016887242097"),leaf("-1.4142135623730950488016887242097"))))
            assertEquals("[1.4142135624, -1.4142135624]",ResultDisplayFormat.resultText(list,true,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
        }
        run { // copiedAnswerFollowsMixedAndDmsDisplays
            val fraction=JSONObject().put("exact","7/2").put("decimal","3.5").put("tree",JSONObject().put("kind","fraction").put("args",args(text("7"),text("2"))))
            assertEquals("3 1/2",ResultDisplayFormat.resultText(fraction,false,true,ResultDisplayMode.OFF,false,false,0,false,false,10))
            val dms=JSONObject().put("exact","12.5125").put("decimal","12.5125").put("dms",true)
                .put("tree",JSONObject().put("kind","dms").put("args",args(leaf("12"),leaf("30"),leaf("45"))))
            assertEquals("12°30′45″",ResultDisplayFormat.resultText(dms,false,false,ResultDisplayMode.OFF,false,false,0,true,false,10))
        }
    }

    @Test fun copiedReportsHandleRelationsStatisticsAndUnknownTrees() {
        run { // copiedAnswerRoundsRelationsAndFallsBackForUnknownTrees
            val relation=JSONObject().put("exact","Eq(x, 0.73908513321516064165531208767)").put("decimal","x = 0.73908513321516064165531208767")
                .put("tree",JSONObject().put("kind","relation").put("value","=").put("args",args(JSONObject().put("kind","symbol").put("value","x"),leaf("0.73908513321516064165531208767"))))
            assertEquals("x = 0.7390851332",ResultDisplayFormat.resultText(relation,false,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
            val sum=JSONObject().put("exact","0.5 + 0.25").put("decimal","0.5 + 0.25").put("tree",JSONObject().put("kind","sum").put("args",args(leaf("0.5"),leaf("0.25"))))
            assertEquals("0.5 + 0.25",ResultDisplayFormat.resultText(sum,true,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
        }
        run { // copiedAnswerRoundsStatisticsRows
            val rows=JSONObject().put("kind","rows").put("args",args(
                JSONObject().put("kind","row").put("value","mean").put("args",args(leaf("1.2345678901234567"))),
                JSONObject().put("kind","row").put("value","stdev").put("args",args(leaf("0.9876543210987654")))))
            val result=JSONObject().put("exact","mean: 1.2345678901234567\nstdev: 0.9876543210987654").put("decimal","mean: 1.2345678901234567\nstdev: 0.9876543210987654").put("decimalTree",rows)
            assertEquals("mean: 1.2345678901\nstdev: 0.9876543211",ResultDisplayFormat.resultText(result,true,false,ResultDisplayMode.OFF,false,false,0,false,false,10))
        }
    }

}

class EngineResultCodecTest {
    @Test fun maximumDensitySurfaceFitsBinderAndRestoresEveryCoordinate() {
        run { // maximumDensitySurfaceFitsBinderAndRestoresEveryCoordinate
            val mesh=JSONArray()
            for(row in 0..96) {
                val points=JSONArray()
                for(col in 0..96) {
                    val x=(col+.1234567890123)/Math.PI
                    val y=(row+.2345678901234)/Math.E
                    points.put(JSONArray(listOf(x,y,sin(x+y))))
                }
                mesh.put(points)
            }
            val result=JSONObject().put("ok",true).put("surface",mesh).toString()
            // String parcels would exceed Binder's shared 1 MiB transaction buffer.
            assertTrue(result.toByteArray(Charsets.UTF_16LE).size>1024*1024)
            val compressed=EngineResultCodec.compress(result)!!
            assertTrue(compressed.size<256*1024)
            assertEquals(result,EngineResultCodec.decode(null,compressed))
            val restored=JSONObject(EngineResultCodec.decode(null,compressed)).getJSONArray("surface")
            assertEquals(97,restored.length())
            assertEquals(mesh.getJSONArray(96).getJSONArray(96).getDouble(2),restored.getJSONArray(96).getJSONArray(96).getDouble(2),0.0)
        }
        run { // compressionPreservesLargeUnicodeResults
            val result="계산 결과 · π · 😀".repeat(10000)
            assertEquals(result,EngineResultCodec.decode(null,EngineResultCodec.compress(result)))
        }
    }

}

class FractionSignTest {
    @Test fun onlyAWholeNumeratorsMinusMovesBeforeTheFraction() {
        for(source in listOf("-1/5","(-1)/5","-x/5","-(x+1)/5")) {
            val tree=JSONObject(Parser(source).parse().json())
            val before=tree.toString()
            val negative=negativeFractionNumerator(tree)!!
            val positive=negative.getJSONArray("args").getJSONObject(0)
            assertEquals(source.indexOf('-'),negative.getInt("start"))
            assertEquals(source.indexOf('-')+1,positive.getInt("start"))
            assertEquals(before,tree.toString())
        }
        for(source in listOf("1/5","(-x+1)/5","(-1)^2/5","1/-5","-1÷5")) {
            assertNull(source,negativeFractionNumerator(JSONObject(Parser(source).parse().json())))
        }
        val result=JSONObject("""{"kind":"fraction","args":[{"kind":"unary","value":"-","args":[{"kind":"number","value":"1"}]},{"kind":"number","value":"5"}]}""")
        assertEquals("1",negativeFractionNumerator(result)!!.getJSONArray("args").getJSONObject(0).getString("value"))
    }
}

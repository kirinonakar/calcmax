package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.resultMathParts
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

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
    @Test fun fractionsAndMatricesRemainIntactAndPlusStaysWithItsTerm() {
        val fraction=node("fraction","",node("number","1"),node("number","2"))
        val matrix=node("matrix","",node("list","",node("number","1")))
        val parts=resultMathParts(node("sum","",fraction,matrix))
        assertEquals(2,parts.size)
        assertSame(fraction,parts[0][0])
        assertEquals(" + ",parts[1][0].getString("value"))
        assertSame(matrix,parts[1][1])
        assertEquals(1,resultMathParts(fraction).size)
    }
}

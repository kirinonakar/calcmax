package com.kirinonakar.symvacas

import com.kirinonakar.symvacas.math.Parser
import com.kirinonakar.symvacas.ui.negativeFractionNumerator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

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

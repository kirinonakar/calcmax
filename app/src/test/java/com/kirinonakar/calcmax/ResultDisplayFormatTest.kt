package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.calculator.ResultDisplayMode
import com.kirinonakar.calcmax.ui.ResultDisplayFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultDisplayFormatTest {
    @Test fun scientificLiteralUsesPowerNotationWithNotationOff() {
        val result=ResultDisplayFormat.formatTree(JSONObject().put("kind","number").put("value","1e+100"),ResultDisplayMode.OFF,false)
        assertEquals("binary",result.getString("kind"))
        assertEquals("1",result.getJSONArray("args").getJSONObject(0).getString("value"))
        assertEquals("100",result.getJSONArray("args").getJSONObject(1).getJSONArray("args").getJSONObject(1).getString("value"))
        assertEquals("1×10^100",ResultDisplayFormat.formatText("1e+100",ResultDisplayMode.OFF,false))
        assertEquals("12",ResultDisplayFormat.formatTree(JSONObject().put("kind","number").put("value","12"),ResultDisplayMode.OFF,false).getString("value"))
    }
}

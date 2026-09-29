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
        assertEquals("5.109725724×10^31342",ResultDisplayFormat.formatText("5.109725724e+31342",ResultDisplayMode.OFF,false))
        assertEquals("12",ResultDisplayFormat.formatTree(JSONObject().put("kind","number").put("value","12"),ResultDisplayMode.OFF,false).getString("value"))
    }

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

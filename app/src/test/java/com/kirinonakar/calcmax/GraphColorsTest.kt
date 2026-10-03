package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.calculator.loadGraphColors
import com.kirinonakar.calcmax.calculator.normalizeGraphColors
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class GraphColorsTest {
    @Test fun missingAndMalformedSettingsUseThemeDefaults() {
        for(source in listOf(null,"broken","{}","[]"))assertEquals(List(6) {null},loadGraphColors(source))
        assertEquals(listOf("#ff0000",null,null,null,"#abc123",null),loadGraphColors("[\"#FF0000\",null,\"red\",\"#fff\",\"#AbC123\"]"))
    }
    @Test fun sixIndependentOverridesAndIndividualResetsSurviveStorage() {
        val colors=listOf("#123456","#abcdef","#000000","#ffffff","#ff0000","#00ff00")
        assertEquals(colors,loadGraphColors(JSONArray(colors).toString()))
        val reset=normalizeGraphColors(colors.mapIndexed {i,color->if(i==3)null else color})
        assertEquals(colors.take(3)+listOf(null)+colors.drop(4),loadGraphColors(JSONArray(reset).toString()))
        assertEquals(colors,normalizeGraphColors(colors+"#112233"))
    }
}

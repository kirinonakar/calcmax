package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.calculator.HistoryEntry
import com.kirinonakar.calcmax.ui.historyPreview
import com.kirinonakar.calcmax.ui.mathPreview
import org.json.JSONObject
import org.junit.Assert.*
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

    @Test fun invalidJsonFallsBackToLegacyExpressions() {
        val preview = entry(input = "{invalid", response = "{invalid").mathPreview()
        assertNotNull(preview.input)
        assertNotNull(preview.response)
    }

    @Test fun excessiveSerializedDataAndExpressionsUseTextWithoutParsing() {
        val huge = "9".repeat(100_000)
        val preview = entry(source = huge, exact = huge, input = "{\"kind\":\"number\",\"value\":\"$huge\"}",
            response = "{\"exact\":\"$huge\"}").mathPreview()
        assertNull(preview.input)
        assertNull(preview.response)
        assertEquals("9".repeat(49) + "…", huge.historyPreview())
        assertNull(entry(response = " ".repeat(20_001)).mathPreview().response)
    }

    @Test fun malformedChildrenAndDecimalFallbackTreesAreRejected() {
        for (key in listOf("tree", "decimalTree", "numericTree", "numericDecimalTree")) {
            val malformed = """{"exact":"1/2","$key":{"kind":"sum","args":[1,null]}}"""
            assertNull(key, entry(response = malformed).mathPreview().response)
        }
        assertNull(entry(response = """{"exact":"1/2","tree":{"kind":"sum","args":{}}}""").mathPreview().response)
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

    @Test fun bracketCharactersAndEscapedQuotesInValuesAreSafe() {
        val tree = JSONObject().put("kind", "text").put("value", "[\"".repeat(100)).toString()
        assertNotNull(entry(input = tree).mathPreview().input)
    }

    @Test fun previewTruncatesAtCodePointBoundaries() {
        assertEquals("😀".repeat(50), "😀".repeat(50).historyPreview())
        assertEquals("😀".repeat(49) + "…", "😀".repeat(51).historyPreview())
        assertEquals("", "".historyPreview())
    }
}

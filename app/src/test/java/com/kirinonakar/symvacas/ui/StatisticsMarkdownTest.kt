package com.kirinonakar.symvacas.ui

import com.kirinonakar.symvacas.calculator.ResultDisplayMode
import org.json.JSONObject
import com.kirinonakar.symvacas.calculator.HistoryEntry
import org.junit.Assert.*
import org.junit.Test

class StatisticsMarkdownTest {
    private fun format(cell:JSONObject)=ResultDisplayFormat.resultText(cell,true,false,ResultDisplayMode.OFF,true,false,0,false,false,3)
    @Test fun completeMarkdownKeepsRowsPrecisionAndLiteralLabels() {
        val result=JSONObject("""{"exact":"source should not be copied","note":"Result only","statisticsReport":{"title":"Descriptive statistics","sections":[{"title":"Summary","columns":["Metric","Value"],"rows":[["mean",{"decimal":"1.234567"}]],"copyRows":[["mean",{"decimal":"1.234567"}],["A|B\n<row>",{"decimal":"12345.6789"}]]}]}}""")
        val text=statisticsResultMarkdown(result,::format){it}
        assertTrue(text.contains("| Metric | Value |\n| --- | --- |\n| mean | 1.235 |"))
        assertTrue(text.contains("| A\\|B<br>&lt;row&gt; | 12,345.679 |"))
        assertTrue(text.endsWith("Result only"))
        assertFalse(text.contains("source should not be copied"))
        val korean=statisticsResultMarkdown(result,::format){translateLabel(it,"ko")}
        assertTrue(korean.startsWith("## "+translateLabel("Descriptive statistics","ko")))
        assertTrue(korean.contains("| "+translateLabel("Metric","ko")+" | "+translateLabel("Value","ko")+" |"))
        assertTrue(korean.contains("A\\|B"))
    }
    @Test fun dedicatedReportAndPlainResultsUseTheirRespectiveCopyFormats() {
        val result=JSONObject("""{"statisticsCopyReport":{"title":"Regression","sections":[{"title":"coefficients","columns":["Parameter","Estimate"],"rows":[["b0",{"decimal":"0.3333333"}]]}]}}""")
        assertTrue(statisticsResultMarkdown(result,::format){it}.contains("| b0 | 0.333 |"))
        assertEquals("0.333",statisticsResultMarkdown(JSONObject().put("decimal","0.3333333"),::format){it})
    }
    @Test fun fittedReportCopiesItsOwnResponseAfterTheMainResultChanges() {
        val report=JSONObject().put("model","logistic").put("n",6)
        val fitted=JSONObject().put("regression",report).put("statisticsCopyReport",JSONObject().put("title","Regression").put("sections",org.json.JSONArray()))
        assertSame(fitted,regressionResultForCopy(fitted,emptyList(),report))
        val current=JSONObject().put("exact","123").put("statisticsReport",JSONObject().put("title","Mean"))
        val saved=HistoryEntry(1,"regression([[0,0],[1,1]],logistic)","f(x)","f(x)","Data & Statistics",response=fitted.toString())
        val recovered=regressionResultForCopy(current,listOf(saved),report)!!
        assertTrue(statisticsResultMarkdown(recovered,::format){it}.startsWith("## Regression"))
        assertNull(regressionResultForCopy(current,emptyList(),report))
        assertNull(regressionResultForCopy(current,listOf(saved),JSONObject().put("model","logistic").put("n",9)))
    }
    @Test fun copiedEquationAndStringCoefficientsFollowEveryDisplayedDigitSetting() {
        val result=JSONObject("""{"exact":"0.123456*x+1.987654","regression":{"fitScale":"y"},"statisticsCopyReport":{"title":"Regression","sections":[{"title":"Summary","columns":["Metric","Value"],"rows":[["Fitted expression",{"exact":"unrounded source"}]]},{"title":"coefficients","columns":["Parameter","Estimate"],"rows":[["b1","0.123456"],["b0","1.987654"]]}]}}""")
        for((digits,equation) in listOf(0 to "y = 0*x+2",3 to "y = 0.123*x+1.988",6 to "y = 0.123456*x+1.987654")) {
            assertEquals(equation,regressionEquationCopyText(result,digits))
            val copied=statisticsResultMarkdown(result,{statisticsFormattedCopyCell(it,digits)},equation){it}
            assertTrue(copied.contains("### Regression equation\n\n```text\n$equation\n```"))
            assertFalse(copied.contains("unrounded source"))
            val slope=ResultDisplayFormat.formatText("0.123456",ResultDisplayMode.OFF,false,maxFractionDigits=digits)
            assertTrue(copied.contains("| b1 | $slope |"))
        }
        assertTrue(regressionEquationCopyText(result,3,mapOf("x" to "dose"),"response = ")!!.contains("response = 0.123*dose+1.988"))
    }
    @Test fun logisticAndCompositeCellsRoundTheirInternalNumbersWithoutChangingTheResult() {
        val result=JSONObject().put("exact","1/(1+exp(-0.123456*x+0.654321))").put("regression",JSONObject().put("fitScale","binomial"))
        val original=result.toString()
        val equation=regressionEquationCopyText(result,3)!!
        assertTrue(equation.startsWith("P(y = 1) = "))
        assertTrue(equation.contains("0.123"));assertTrue(equation.contains("0.654"))
        assertFalse(equation.contains("0.123456"));assertEquals(original,result.toString())
        val cell=JSONObject("""{"exact":"x + 1.23456789","decimal":"x + 1.23456789","decimalTree":{"kind":"sum","args":[{"kind":"symbol","value":"x"},{"kind":"number","value":"1.23456789"}]}}""")
        assertEquals("x+1.235",statisticsFormattedCopyCell(cell,3))
        val product=JSONObject("""{"decimal":"1.23456789*x","decimalTree":{"kind":"product","args":[{"kind":"number","value":"1.23456789"},{"kind":"symbol","value":"x"}]}}""")
        assertEquals("1.235*x",statisticsFormattedCopyCell(product,3))
        val power=JSONObject().put("exact","0.123456*x**2+1.987654").put("regression",JSONObject().put("fitScale","y"))
        assertTrue(regressionEquationCopyText(power,3)!!.contains("x^2"))
        result.getJSONObject("regression").put("model","randomforest")
        assertNull(regressionEquationCopyText(result,3))
    }
}

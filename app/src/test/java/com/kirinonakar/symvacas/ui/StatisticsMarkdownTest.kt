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
}

package com.kirinonakar.symvacas

import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.math.Parser
import com.kirinonakar.symvacas.ui.importStatisticsCsv
import com.kirinonakar.symvacas.ui.previewStatisticsCsv
import com.kirinonakar.symvacas.ui.regressionFormulaDisplayTree
import com.kirinonakar.symvacas.ui.multivariateRegressionDisplayTree
import com.kirinonakar.symvacas.ui.statisticsRegressionTable
import com.kirinonakar.symvacas.ui.statisticsRegressionVariables
import com.kirinonakar.symvacas.ui.statisticsCsvLine
import com.kirinonakar.symvacas.ui.statisticsDataSource
import com.kirinonakar.symvacas.ui.statisticsDateAxis
import com.kirinonakar.symvacas.ui.statisticsGroupedValues
import com.kirinonakar.symvacas.ui.statisticsNumericRows
import com.kirinonakar.symvacas.ui.statisticsRecallSource
import com.kirinonakar.symvacas.ui.statisticsTestCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsDataSourceTest {
    @Test fun directInputDetectsArbitraryHeadersWithoutRemovingFirstExpressionsOrGroups() {
        val source="Treatment,Measurement\nA,1\nB,2\nA,3"
        val rows=com.kirinonakar.symvacas.ui.statisticsRows(source)
        assertEquals(listOf(listOf("A","1"),listOf("B","2"),listOf("A","3")),rows)
        assertEquals(listOf("A" to listOf(1.0,3.0),"B" to listOf(2.0)),com.kirinonakar.symvacas.ui.statisticsPlotPanels(rows,"xy","first")[0].series)
        assertEquals("[[1,2],[2,4]]",statisticsDataSource("Time,Outcome\n1,2\n2,4","xy"))
        assertEquals(listOf(listOf("1/2"),listOf("2")),com.kirinonakar.symvacas.ui.statisticsRows("Value label\n1/2\n2"))
        assertEquals(listOf(listOf("sqrt(2)"),listOf("3")),com.kirinonakar.symvacas.ui.statisticsRows("sqrt(2)\n3"))
        assertEquals(listOf(listOf("pi"),listOf("2")),com.kirinonakar.symvacas.ui.statisticsRows("pi\n2"))
        assertEquals(listOf(listOf("A",""),listOf("B","2")),com.kirinonakar.symvacas.ui.statisticsRows("A,\nB,2"))
        assertEquals(listOf(listOf("1"),listOf("2")),com.kirinonakar.symvacas.ui.statisticsRows("\uFEFF1\n2"))
        assertTrue(previewStatisticsCsv(source).hasHeader)
    }

    @Test fun plotGroupsKeepRawLabelsAndExcludeOnlyMissingOrNonfiniteObservations() {
        val source="A,1,10\nB,2,20\nA,,30\n,99,99\nB,NaN,40"
        assertEquals(listOf("A · y" to listOf(1.0),"A · z" to listOf(10.0,30.0),"B · y" to listOf(2.0),"B · z" to listOf(20.0,40.0)),
            com.kirinonakar.symvacas.ui.statisticsPlotSeries(com.kirinonakar.symvacas.ui.statisticsRows(source),"xyz","first"))
        val panels=com.kirinonakar.symvacas.ui.statisticsPlotPanels(com.kirinonakar.symvacas.ui.statisticsRows(source),"xyz","first")
        assertEquals(listOf("y","z"),panels.map {it.label})
        assertEquals(listOf("A" to listOf(1.0),"B" to listOf(2.0)),panels[0].series)
        assertEquals(listOf("A" to listOf(10.0,30.0),"B" to listOf(20.0,40.0)),panels[1].series)
        val last=listOf(listOf("1","A"),listOf("2","B"),listOf("3","A"))
        assertEquals(listOf("A" to listOf(1.0,3.0),"B" to listOf(2.0)),com.kirinonakar.symvacas.ui.statisticsPlotSeries(last,"xy","last"))
        val dates=listOf(listOf("2024-01-01","1,234"),listOf("2024-01-01",""),listOf("2","5"))
        assertEquals(listOf("2024-01-01" to listOf(1234.0),"2" to listOf(5.0)),com.kirinonakar.symvacas.ui.statisticsPlotSeries(dates,"xy","first"))
        assertEquals(emptyList<Pair<String,List<Double>>>(),com.kirinonakar.symvacas.ui.statisticsPlotSeries(listOf(listOf("","7")),"xy","first"))
    }

    @Test fun nColumnsPreserveImportStorageAnalysisAndRegressionOrder() {
        val csv="1,2,3,4\n5,6,7,8"
        assertEquals("[[1,2,3,4],[5,6,7,8]]",statisticsDataSource(csv,"columns:4"))
        assertEquals("[[1,2,3,4],[5,6,7,8]]",statisticsRecallSource(Editor(),csv,"columns:4"))
        val preview=previewStatisticsCsv("a,b,c,d\n"+csv)
        assertEquals(csv,importStatisticsCsv(preview,listOf(0,1,2,3),true))
        val rows=com.kirinonakar.symvacas.ui.statisticsRows(csv)
        assertEquals("ttest(0,[4,8])",statisticsTestCommand("t test",rows,"columns:4","x4","Two-sided","0","2","95"))
        assertEquals("anova([1,5],[2,6],[3,7],[4,8])",statisticsTestCommand("ANOVA",rows,"columns:4","x","Two-sided","0","2","95"))
        val complete=(1..5).map {listOf("$it","${it+1}","${it+2}","${it+3}")}
        assertEquals("[[2,3,4,1],[3,4,5,2],[4,5,6,3],[5,6,7,4],[6,7,8,5]]",statisticsRegressionTable(complete,"columns:4","logistic",0))
        assertEquals("[[2,1],[6,5]]",statisticsRegressionTable(rows,"xy","polynomial",0))
    }

    @Test fun excelPasteKeepsDateAndPaddedThousandsAsTwoColumns() {
        val dates=listOf("2022-12-02","2023-01-15","2023-02-05","2023-03-05","2023-04-01","2023-05-01","2023-06-01","2023-07-01","2023-08-01","2023-09-01")
        val values=listOf("166,682","168,254","169,131","172,166","175,120","177,330","177,409","181,512","181,286","183,566")
        val days=listOf("1","45","66","94","121","151","182","212","243","274")
        val source=dates.zip(values).joinToString("\r\n",postfix="\r\n") {(date,value)->"$date\t \u00a0\u00a0        $value\u00a0 "}
        val rows=com.kirinonakar.symvacas.ui.statisticsRows(source).filter {row->row.any(String::isNotBlank)}
        assertEquals(dates.zip(values).map {(date,value)->listOf(date,value)},rows)
        val expected=days.zip(values).map {(day,value)->listOf(day,value.replace(",",""))}
        assertEquals(expected,statisticsNumericRows(rows,statisticsDateAxis(rows)))
        val table=expected.joinToString(",","[","]") {it.joinToString(",","[","]")}
        assertEquals(table,statisticsDataSource(source,"xy"))
        assertEquals(10,Parser("regression($table,linear)").parse().args.first().args.size)
        val preview=previewStatisticsCsv(source)
        assertFalse(preview.hasHeader)
        assertEquals(2,preview.columnCount)
        assertEquals(table,statisticsDataSource(importStatisticsCsv(preview,listOf(0,1),false),"xy"))
    }

    @Test fun tabSeparatedRowsPreserveQuotesCommasAndMissingCells() {
        val source="x\ty\tz\r\n\"A,B\"\t\"1,234\"\t\r\n\t\"C\"\"D\"\t5\r\n1,2,3"
        assertEquals(listOf(listOf("A,B","1,234",""),listOf("","C\"D","5"),listOf("1","2","3")),com.kirinonakar.symvacas.ui.statisticsRows(source))
        val row=listOf("a\tb","1,234")
        assertEquals("\"a\tb\",\"1,234\"",statisticsCsvLine(row))
        assertEquals(listOf(row),com.kirinonakar.symvacas.ui.statisticsRows(statisticsCsvLine(row)))
        assertEquals(listOf(listOf("1","2","3")),com.kirinonakar.symvacas.ui.statisticsRows("1,2,3"))
    }

    @Test fun dateXUsesCalendarDaySpacingAndCanBeRecalledNumerically() {
        val rows=com.kirinonakar.symvacas.ui.statisticsRows("2024-02-28,2\n2024/02/29,4\n2024.3.2,8")
        val axis=statisticsDateAxis(rows)!!
        assertEquals(listOf(listOf("1","2"),listOf("2","4"),listOf("4","8")),axis.numericRows(rows))
        assertEquals("[[1,2],[2,4],[4,8]]",statisticsDataSource("2024-02-28,2\n2024/02/29,4\n2024.3.2,8","xy"))
    }

    @Test fun importDetectsArbitraryHeaderAndMapsSelectedColumns() {
        val preview=previewStatisticsCsv("\uFEFFtime,noise,result,weight\r\n1,99,10,0.5\r\n2,88,20,0.7\r\n")
        assertTrue(preview.hasHeader)
        assertEquals(4,preview.columnCount)
        assertEquals("Column 3: result",preview.labels[2])
        assertEquals("10,1,0.5\n20,2,0.7",importStatisticsCsv(preview,listOf(2,0,3),true))
    }

    @Test fun savedPairsKeepTheirOuterListOutsideRegressionSlot() {
        val csv="1,2\n2,4"
        val literal="[[1,2],[2,4]]"
        assertEquals(literal,statisticsRecallSource(Editor(),csv,"xy"))
        val mean="mean([])"
        assertEquals(literal,statisticsRecallSource(Editor(mean,mean.indexOf("[]")+1),csv,"xy"))
        val regression="regression([],linear)"
        assertEquals(literal,statisticsRecallSource(Editor(regression,regression.indexOf("[]")+1),csv,"xy",committed=true))
    }
}

class StatisticsTestCommandsTest {
    private fun command(procedure: String, rows: List<List<String>>, kind: String = "list", column: String = "x") =
        statisticsTestCommand(procedure, rows, kind, column, "Two-sided", "0", "2", "95")

    @Test fun rankTestsKeepPairingAndIndependentMissingCells() {
        val rows=listOf(listOf("1","4"),listOf("2",""),listOf("","5"),listOf("3","6"))
        assertEquals("wilcoxon([1,3],[4,6])",command("Wilcoxon",rows,"xy"))
        assertEquals("mannwhitney([1,2,3],[4,5,6])",command("Mann–Whitney",rows,"xy"))
        assertEquals("kruskal([1,2,3],[4,5,6])",command("Kruskal–Wallis",rows,"xy"))
        assertEquals("wilcoxon([1,2,3])",command("Wilcoxon",listOf(listOf("1"),listOf("2"),listOf("3"))))
        assertEquals("wilcoxon([1,3],[4,6],right)",statisticsTestCommand("Wilcoxon",rows,"xy","x","Right","0","2","95"))
        val grouped=listOf(listOf("a","1"),listOf("b","4"),listOf("a","2"),listOf("b","5"))
        assertEquals("mannwhitney([1,2],[4,5])",statisticsTestCommand("Mann–Whitney",grouped,"xy","x","Two-sided","0","2","95",grouping="group-value"))
        val xyz=listOf(listOf("1","4","7"),listOf("2","5","8"),listOf("3","6","9"))
        assertEquals("mannwhitney([7,8,9],[1,2,3])",statisticsTestCommand("Mann–Whitney",xyz,"xyz","x","Two-sided","0","2","95",firstGroup="z",secondGroup="x"))
        assertEquals("wilcoxon([1,2,3],[4,5,6])",command("Wilcoxon",xyz,"xyz"))
    }

    @Test fun testsUseTheCurrentTableValues() {
        assertEquals("ttest(0,[1,2,3])", command("t test", listOf(listOf("1"), listOf("2"), listOf("3"))))
        assertEquals("ttest(0,[1,9,3])", command("t test", listOf(listOf("1"), listOf("9"), listOf("3"))))
        assertEquals("ttest(0,[1/2,9,3])", command("t test", listOf(listOf("1/2"), listOf("9"), listOf("3"))))
        assertEquals("zinterval(95,2,[1,9,3])", command("z interval", listOf(listOf("1"), listOf("9"), listOf("3"))))
    }

    @Test fun twoColumnProceduresUseTheChosenColumns() {
        val rows = listOf(listOf("10", "15"), listOf("20", "20"), listOf("30", "25"))
        assertEquals("ztest(0,2,[15,20,25])", command("z test", rows, "xy", "y"))
        assertEquals("ttest2(0,[10,20,30],[15,20,25])", command("t test", rows, "xy", "x-y"))
        assertEquals("ttestpaired(0,[10,20,30],[15,20,25])", command("t test", rows, "xy", "paired"))
        assertEquals("ztest2(0,2,3,[10,20,30],[15,20,25])", statisticsTestCommand("z test",rows,"xy","x-y","Two-sided","0","2","95","3"))
        assertEquals("chi2independence([10,20,30],[15,20,25],1)", command("χ² test", rows, "xy"))
        assertEquals("anova([10,20,30],[15,20,25])", command("ANOVA", rows, "xy"))
    }

    @Test fun insufficientOrInvalidDataCannotRun() {
        assertNull(command("t test", listOf(listOf("1"))))
        assertNull(command("t test", listOf(listOf("1"), listOf(""))))
        assertNull(command("Shapiro–Wilk", listOf(listOf("1"), listOf(""), listOf("2"))))
        assertNull(command("χ² test", listOf(listOf("10"), listOf("20"))))
        assertNull(command("χ² test", listOf(listOf("10", "15"), listOf("20", "")), "xy"))
        assertNull(command("Fisher exact", listOf(listOf("0", "1"), listOf("1", "")), "xy"))
        assertNull(statisticsTestCommand("t interval", listOf(listOf("1"), listOf("2")), "list", "x", "Two-sided", "0", "2", "100"))
        assertEquals("ttest(0,[1,2],right)", statisticsTestCommand("t test", listOf(listOf("1"), listOf("2")), "list", "x", "Right", "0", "2", "95"))
    }

    @Test fun groupValueLayoutUsesYObservationsAndPairedCategories() {
        val rows=listOf(listOf("control","1"),listOf("treated","4"),listOf("control","2"),listOf("treated","5"),listOf("control","3"),listOf("treated","6"))
        assertEquals(listOf("control" to listOf("1","2","3"),"treated" to listOf("4","5","6")),statisticsGroupedValues(rows))
        fun grouped(test:String,first:String="control",second:String="treated")=
            statisticsTestCommand(test,rows,"xy","x","Two-sided","0","2","95","3","group-value",first,second)
        assertEquals("anova([1,2,3],[4,5,6])",grouped("ANOVA"))
        assertEquals("tukey([1,2,3],[4,5,6])",grouped("Tukey HSD"))
        assertEquals("ttest2(0,[1,2,3],[4,5,6])",grouped("t test"))
        assertEquals("ztest2(0,2,3,[1,2,3],[4,5,6])",grouped("z test"))
        assertEquals("shapiro([1,2,3])",grouped("Shapiro–Wilk"))
        assertEquals("tinterval(95,[4,5,6])",grouped("t interval",first="treated"))
        assertNull(grouped("t test",second="control"))
        val categories=listOf(listOf("control","yes"),listOf("treated","no"),listOf("control","no"),listOf("treated","yes"))
        fun groupedCategories(test:String)=statisticsTestCommand(test,categories,"xy","x","Two-sided","0","2","95",grouping="group-value")
        assertEquals("chi2independence([1,2,1,2],[1,2,2,1],1)",groupedCategories("χ² test"))
        assertEquals("fisherexact([1,2,1,2],[1,2,2,1])",groupedCategories("Fisher exact"))
        assertNull(grouped("Fisher exact"))
    }
}

class RegressionFormulaTest {
    @Test fun coefficientLabelsTrackHeaderColumnsAndSelectedResponse() {
        fun labels(kind:String,mode:String,response:Int,csv:String="")=com.kirinonakar.symvacas.ui.statisticsRegressionParameterLabels(kind,mode,response,csv)
        assertEquals(mapOf("b0" to "Intercept","b1" to "Age (y)","b2" to "Weight (z)"),labels("xyz","logistic",0,"Outcome,Age,Weight\n0,20,50\n1,30,60"))
        assertEquals(mapOf("b0" to "Intercept","b1" to "x","b2" to "y"),labels("xyz","multiple",2))
        assertEquals(mapOf("b0" to "Intercept","b1" to "x","b2" to "z"),labels("xyz","multiple",1))
        val polynomial=labels("xy","polynomial",0)
        assertEquals("y",polynomial["b1"])
        assertEquals("y²",polynomial["b2"])
        assertEquals("y¹⁰",polynomial["b10"])
        assertTrue(labels("xy","custom",1).isEmpty())
    }

    @Test fun logisticResponseChoiceReordersOnlyCompleteRowsAndMapsThePredictors() {
        val rows=listOf(listOf("0","10","20"),listOf("1","11","21"),listOf("","12","22"),listOf("0","13","23"),listOf("1","14","24"))
        assertEquals("[[10,20,0],[11,21,1],[13,23,0],[14,24,1]]",statisticsRegressionTable(rows,"xyz","logistic",0))
        assertEquals("[[10,20,0],[11,21,1],[13,23,0],[14,24,1]]",statisticsRegressionTable(rows,"xyz","multiple",0))
        assertEquals("[[0,20,10],[1,21,11],[0,23,13],[1,24,14]]",statisticsRegressionTable(rows,"xyz","logistic",1))
        assertEquals(mapOf("x1" to "y","x2" to "z"),statisticsRegressionVariables("xyz",0))
        assertEquals(mapOf("x" to "y"),statisticsRegressionVariables("xy",0))
        assertEquals("[[10,0],[11,1],[13,0],[14,1]]",statisticsRegressionTable(rows,"xy","logistic",0))
        assertNull(statisticsRegressionTable(rows,"xy","logistic",2))
        assertEquals("missing cells in the input table stay untouched","",rows[2][0])
    }

    @Test fun multivariateCaptionsMatchDataColumnsInsideLogisticExpressions() {
        val formula="1/(1+exp(-0.123456789*x1+0.987654321*x2+x10))"
        val displayed=multivariateRegressionDisplayTree(formula,5)!!
        val values=mutableListOf<String>()
        fun symbols(node:org.json.JSONObject) {
            if(node.optString("kind")=="symbol")values.add(node.optString("value"))
            node.optJSONArray("args")?.let {args->repeat(args.length()){symbols(args.getJSONObject(it))}}
        }
        symbols(displayed)
        assertEquals(listOf("x","y","x10"),values)
        assertTrue(displayed.toString().contains("0.12346"))
        assertTrue(displayed.toString().contains("0.98765"))
        assertTrue("the original stored predictor names stay intact",regressionFormulaDisplayTree(formula,5)!!.toString().contains("x1"))
    }

    @Test fun fittedFormulaUsesConfiguredFractionalPlaces() {
        val source="0.123456789*x + 1/7"
        val five=regressionFormulaDisplayTree(source,5)!!.toString()
        val ten=regressionFormulaDisplayTree(source,10)!!.toString()
        assertTrue(five.contains("0.12346"))
        assertTrue(five.contains("0.14286"))
        assertTrue(ten.contains("0.123456789"))
        assertTrue(ten.contains("0.1428571429"))
    }
}

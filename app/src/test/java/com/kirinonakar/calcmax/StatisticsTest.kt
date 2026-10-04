package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.math.Editor
import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.ui.importStatisticsCsv
import com.kirinonakar.calcmax.ui.previewStatisticsCsv
import com.kirinonakar.calcmax.ui.regressionFormulaDisplayTree
import com.kirinonakar.calcmax.ui.statisticsCsvLine
import com.kirinonakar.calcmax.ui.statisticsDataSource
import com.kirinonakar.calcmax.ui.statisticsDateAxis
import com.kirinonakar.calcmax.ui.statisticsGroupedValues
import com.kirinonakar.calcmax.ui.statisticsNumericRows
import com.kirinonakar.calcmax.ui.statisticsRecallSource
import com.kirinonakar.calcmax.ui.statisticsTestCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsDataSourceTest {
    @Test fun excelPasteKeepsDateAndPaddedThousandsAsTwoColumns() {
        val dates=listOf("2022-12-02","2023-01-15","2023-02-05","2023-03-05","2023-04-01","2023-05-01","2023-06-01","2023-07-01","2023-08-01","2023-09-01")
        val values=listOf("166,682","168,254","169,131","172,166","175,120","177,330","177,409","181,512","181,286","183,566")
        val days=listOf("1","45","66","94","121","151","182","212","243","274")
        val source=dates.zip(values).joinToString("\r\n",postfix="\r\n") {(date,value)->"$date\t \u00a0\u00a0        $value\u00a0 "}
        val rows=com.kirinonakar.calcmax.ui.statisticsRows(source).filter {row->row.any(String::isNotBlank)}
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
        assertEquals(listOf(listOf("A,B","1,234",""),listOf("","C\"D","5"),listOf("1","2","3")),com.kirinonakar.calcmax.ui.statisticsRows(source))
        val row=listOf("a\tb","1,234")
        assertEquals("\"a\tb\",\"1,234\"",statisticsCsvLine(row))
        assertEquals(listOf(row),com.kirinonakar.calcmax.ui.statisticsRows(statisticsCsvLine(row)))
        assertEquals(listOf(listOf("1","2","3")),com.kirinonakar.calcmax.ui.statisticsRows("1,2,3"))
    }

    @Test fun dateXUsesCalendarDaySpacingAndCanBeRecalledNumerically() {
        val rows=com.kirinonakar.calcmax.ui.statisticsRows("2024-02-28,2\n2024/02/29,4\n2024.3.2,8")
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

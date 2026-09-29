package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.statisticsTestCommand
import com.kirinonakar.calcmax.ui.statisticsCorrelationCommand
import com.kirinonakar.calcmax.ui.statisticsGroupedValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatisticsTestCommandsTest {
    private fun command(procedure: String, rows: List<List<String>>, kind: String = "list", column: String = "x") =
        statisticsTestCommand(procedure, rows, kind, column, "Two-sided", "0", "2", "95")

    @Test fun correlationUsesOnlyCompletePairs() {
        val rows = listOf(listOf(" 1 ", "2"), listOf("", "100"), listOf("3", ""), listOf("4", " 8 "))
        assertEquals("correlation([1,4],[2,8])", statisticsCorrelationCommand(rows, "xy"))
        assertNull(statisticsCorrelationCommand(rows, "list"))
        assertNull(statisticsCorrelationCommand(listOf(listOf("1", "2"), listOf("3", "")), "xy"))
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
        assertEquals("chi2independence([10,20,30],[15,20,25])", command("χ² test", rows, "xy"))
        assertEquals("anova([10,20,30],[15,20,25])", command("ANOVA", rows, "xy"))
    }

    @Test fun missingCellsAreSkippedAccordingToTheTest() {
        val rows = listOf(listOf("1", "1"), listOf("", "0"), listOf("3", ""), listOf("4", "1"))
        assertEquals("ttest(0,[1,3,4])", command("t test", rows, "xy"))
        assertEquals("ttest2(0,[1,3,4],[1,0,1])", command("t test", rows, "xy", "x-y"))
        assertEquals("ttestpaired(0,[1,4],[1,1])", command("t test", rows, "xy", "paired"))
        assertEquals("chi2independence([1,4],[1,1])", command("χ² test", rows, "xy"))
        assertEquals("fisherexact([1,4],[1,1])", command("Fisher exact", rows, "xy"))
        assertEquals("fisherexact([1,4],[1,1],right)", statisticsTestCommand("Fisher exact",rows,"xy","x","Right","0","2","95"))
        assertEquals("anova([1,3,4],[1,0,1])", command("ANOVA", rows, "xy"))
        assertEquals("shapiro([1,3,4])", command("Shapiro–Wilk", rows, "xy"))
        assertEquals("shapiro([1,0,1])", command("Shapiro–Wilk", rows, "xy", "y"))
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

    @Test fun threeColumnsSupplyAnovaTukeyAndSingleColumnTests() {
        val rows=listOf(listOf("1","4","7"),listOf("2","5","8"),listOf("3","6","9"))
        assertEquals("anova([1,2,3],[4,5,6],[7,8,9])",command("ANOVA",rows,"xyz"))
        assertEquals("tukey([1,2,3],[4,5,6],[7,8,9])",command("Tukey HSD",rows,"xyz"))
        assertEquals("ttest(0,[7,8,9])",command("t test",rows,"xyz","z"))
        assertNull(command("ANOVA",rows.dropLast(2),"xyz"))
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
        assertEquals("chi2independence([1,2,1,2],[1,2,2,1])",groupedCategories("χ² test"))
        assertEquals("fisherexact([1,2,1,2],[1,2,2,1])",groupedCategories("Fisher exact"))
        assertNull(grouped("Fisher exact"))
    }
}

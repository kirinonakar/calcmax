package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.statisticsTestCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        assertEquals("chi2test([10,20,30],[15,20,25])", command("χ² test", rows, "xy"))
        assertEquals("anova([10,20,30],[15,20,25])", command("ANOVA", rows, "xy"))
    }

    @Test fun incompleteOrInvalidDataCannotRun() {
        assertNull(command("t test", listOf(listOf("1"))))
        assertNull(command("t test", listOf(listOf("1"), listOf(""))))
        assertNull(command("χ² test", listOf(listOf("10"), listOf("20"))))
        assertNull(command("χ² test", listOf(listOf("10", "15"), listOf("20", "")), "xy"))
        assertNull(statisticsTestCommand("t interval", listOf(listOf("1"), listOf("2")), "list", "x", "Two-sided", "0", "2", "100"))
        assertEquals("ttest(0,[1,2],right)", statisticsTestCommand("t test", listOf(listOf("1"), listOf("2")), "list", "x", "Right", "0", "2", "95"))
    }
}

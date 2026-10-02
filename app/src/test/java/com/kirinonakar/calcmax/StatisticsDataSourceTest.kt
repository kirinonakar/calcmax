package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.math.Editor
import com.kirinonakar.calcmax.ui.statisticsDataSource
import com.kirinonakar.calcmax.ui.statisticsRecallSource
import com.kirinonakar.calcmax.ui.previewStatisticsCsv
import com.kirinonakar.calcmax.ui.importStatisticsCsv
import com.kirinonakar.calcmax.ui.statisticsDateAxis
import com.kirinonakar.calcmax.ui.parseStatisticsDate
import com.kirinonakar.calcmax.ui.statisticsCsvLine
import com.kirinonakar.calcmax.ui.statisticsNumericRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun quotedThousandsInDateSeriesPlotAndRegressAsNumbers() {
        val source="2022-12-02,\"166,682\"\n2023-01-15,\"168,254\"\n2023-02-05,\"169,131\""
        val preview=previewStatisticsCsv(source)
        assertFalse(preview.hasHeader)
        val csv=importStatisticsCsv(preview,listOf(0,1),false)
        val rows=com.kirinonakar.calcmax.ui.statisticsRows(csv)
        val numeric=statisticsNumericRows(rows,statisticsDateAxis(rows))
        assertEquals(listOf("1","166682"),numeric[0])
        assertEquals(listOf("45","168254"),numeric[1])
        assertEquals("2022-12-02,\"166,682\"",statisticsCsvLine(rows[0]))
        val table=statisticsDataSource(csv,"xy")
        assertEquals("[[1,166682],[45,168254],[66,169131]]",table)
        assertEquals("regression",Parser("regression($table,linear)").parse().value)
    }

    @Test fun headerDetectionHandlesQuotedThousands() {
        val preview=previewStatisticsCsv("date,value\n2022-12-02,\"166,682\"\n2023-01-15,\"168,254\"")
        assertTrue(preview.hasHeader)
        assertEquals("[[1,166682],[45,168254]]",statisticsDataSource(importStatisticsCsv(preview,listOf(0,1),true),"xy"))
    }

    @Test fun dateXUsesCalendarDaySpacingAndCanBeRecalledNumerically() {
        val rows=com.kirinonakar.calcmax.ui.statisticsRows("2024-02-28,2\n2024/02/29,4\n2024.3.2,8")
        val axis=statisticsDateAxis(rows)!!
        assertEquals(listOf(listOf("1","2"),listOf("2","4"),listOf("4","8")),axis.numericRows(rows))
        assertEquals("[[1,2],[2,4],[4,8]]",statisticsDataSource("2024-02-28,2\n2024/02/29,4\n2024.3.2,8","xy"))
    }

    @Test fun importedDateColumnWorksAfterHeaderRemovalAndReordering() {
        val preview=previewStatisticsCsv("value,date,unused\n2,2024-01-01,0\n4,2024-01-03,0")
        assertTrue(preview.hasHeader)
        val csv=importStatisticsCsv(preview,listOf(1,0),true)
        assertEquals("2024-01-01,2\n2024-01-03,4",csv)
        assertEquals("[[1,2],[3,4]]",statisticsDataSource(csv,"xy"))
    }

    @Test fun invalidDatesAreSkippedAndNumericXStaysNumeric() {
        assertEquals(null,parseStatisticsDate("2024-02-30"))
        val rows=com.kirinonakar.calcmax.ui.statisticsRows("2024-01-01,2\n2024-02-30,3\n2024-01-03,4")
        assertEquals("[[1,2],[3,4]]",statisticsDataSource(rows.joinToString("\n") {it.joinToString(",")},"xy"))
        assertEquals(null,statisticsDateAxis(com.kirinonakar.calcmax.ui.statisticsRows("1,2\n2,4")))
        assertEquals("[[1,2],[2,4]]",statisticsDataSource("1,2\n2,4","xy"))
    }

    @Test fun importDetectsArbitraryHeaderAndMapsSelectedColumns() {
        val preview=previewStatisticsCsv("\uFEFFtime,noise,result,weight\r\n1,99,10,0.5\r\n2,88,20,0.7\r\n")
        assertTrue(preview.hasHeader)
        assertEquals(4,preview.columnCount)
        assertEquals("Column 3: result",preview.labels[2])
        assertEquals("10,1,0.5\n20,2,0.7",importStatisticsCsv(preview,listOf(2,0,3),true))
    }

    @Test fun importKeepsNumericFirstRowAndAllowsHeaderOverride() {
        val preview=previewStatisticsCsv("1,2\n3,4")
        assertFalse(preview.hasHeader)
        assertEquals("1,2\n3,4",importStatisticsCsv(preview,listOf(0,1),false))
        assertEquals("3,4",importStatisticsCsv(preview,listOf(0,1),true))
    }

    @Test fun importPreservesQuotedCellsAfterReordering() {
        val preview=previewStatisticsCsv("name,value\n\"A,B\",10\n\"C\"\"D\",20")
        assertTrue(preview.hasHeader)
        assertEquals("10,\"A,B\"\n20,\"C\"\"D\"",importStatisticsCsv(preview,listOf(1,0),true))
    }

    @Test fun importRecognizesPartlyBlankAndGroupHeadersWithoutDroppingCategoryData() {
        assertTrue(previewStatisticsCsv(",measurement\n1,12\n2,14").hasHeader)
        val grouped=previewStatisticsCsv("group,value\ncontrol,1\ntreated,2")
        assertTrue(grouped.hasHeader)
        assertEquals("control,1\ntreated,2",importStatisticsCsv(grouped,listOf(0,1),true))
        assertFalse(previewStatisticsCsv("control,1\ntreated,2").hasHeader)
    }

    @Test fun savedListRecallsAsCalculatorList() {
        val source=statisticsDataSource("value\r\n1\r\n2\r\n", "list")
        assertEquals("[1,2]",source)
        assertEquals("list",Parser(source).parse().kind)
    }

    @Test fun savedPairsRecallAsNestedCalculatorList() {
        val source=statisticsDataSource("x,y\n1,2\n3,\n,4\n5,6", "xy")
        assertEquals("[[1,2],[5,6]]",source)
        assertEquals("list",Parser(source).parse().kind)
    }

    @Test fun savedTriplesRecallAsThreeColumnRows() {
        val source=statisticsDataSource("x,y,z\n1,2,3\n4,5,\n6,7,8","xyz")
        assertEquals("[[1,2,3],[6,7,8]]",source)
        assertEquals(3,Parser(source).parse().args.first().args.size)
    }

    @Test fun groupValueCsvHeaderIsNotAnObservation() {
        assertEquals(listOf(listOf("control","1"),listOf("treated","2")),com.kirinonakar.calcmax.ui.statisticsRows("group,value\ncontrol,1\ntreated,2"))
        assertEquals(listOf(listOf("group","1"),listOf("x","2")),com.kirinonakar.calcmax.ui.statisticsRows("group,1\nx,2"))
    }

    @Test fun savedPairsFillRegressionTemplateWithoutExtraList() {
        val template="regression([],linear)"
        val editor=Editor(template,template.indexOf("[]")+1)
        val insertion=statisticsRecallSource(editor,"x,y\n1,2\n2,4\n3,6","xy")
        assertEquals("[1,2],[2,4],[3,6]",insertion)
        val completed=editor.insert(insertion).source
        assertEquals("regression([[1,2],[2,4],[3,6]],linear)",completed)
        val regression=Parser(completed).parse()
        assertEquals("call",regression.kind)
        assertEquals("regression",regression.value)
        assertEquals(3,regression.args.first().args.size)
        regression.args.first().args.forEach {pair->assertEquals(2,pair.args.size)}
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

package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.math.Editor
import com.kirinonakar.calcmax.ui.statisticsDataSource
import com.kirinonakar.calcmax.ui.statisticsRecallSource
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsDataSourceTest {
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

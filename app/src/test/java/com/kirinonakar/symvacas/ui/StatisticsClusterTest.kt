package com.kirinonakar.symvacas.ui

import org.junit.Assert.*
import org.junit.Test

class StatisticsClusterTest {
    @Test fun singleLinkageMatchesNearestPairMergeHeights() {
        val vectors=listOf(listOf(0.0),listOf(10.0),listOf(1.0),listOf(11.0))
        val result=statisticsHierarchy(vectors)
        assertEquals(listOf(0,2,1,3),result.order)
        assertEquals(1.0/11,result.links[0].height,1e-12);assertEquals(1.0/11,result.links[1].height,1e-12);assertEquals(9.0/11,result.links[2].height,1e-12)
        assertEquals(result,statisticsHierarchy(vectors))
    }
    @Test fun clusteredMapPreservesValuesLabelsAndMissingCellsAcrossBothAxes() {
        val data=StatisticsHeatMapData(listOf("a","b","c","d"),listOf(
            StatisticsHeatMapRow("A",listOf(0.0,10.0,1.0,11.0)),StatisticsHeatMapRow("B",listOf(10.0,20.0,11.0,21.0)),
            StatisticsHeatMapRow("C",listOf(1.0,11.0,2.0,12.0)),StatisticsHeatMapRow("D",listOf(11.0,21.0,12.0,22.0)),StatisticsHeatMapRow("Missing",listOf(null,null,null,null))))
        val result=clusteredHeatMap(data)
        assertEquals(listOf("a","c","b","d"),result.columns);assertEquals(listOf("A","C","B","D","Missing"),result.rows.map {it.label})
        assertEquals(3,result.rowLinks.size);assertEquals(3,result.columnLinks.size)
        result.rows.forEach {row->result.columns.forEachIndexed {index,name->assertEquals(data.rows.first {it.label==row.label}.values[data.columns.indexOf(name)],row.values[index])}}
    }
    @Test fun averageCompleteWardAndMetricsFollowLanceWilliamsHeights() {
        val average=statisticsHierarchy(listOf(listOf(0.0),listOf(10.0),listOf(1.0),listOf(11.0)),"average","euclidean")
        assertEquals(listOf(0,2,1,3),average.order)
        assertEquals(1.0/11,average.links[0].height,1e-12);assertEquals(1.0/11,average.links[1].height,1e-12);assertEquals(10.0/11,average.links[2].height,1e-12)
        val complete=statisticsHierarchy(listOf(listOf(0.0),listOf(10.0),listOf(1.0),listOf(11.0)),"complete","euclidean")
        assertEquals(1.0,complete.links[2].height,1e-12)
        val ward=statisticsHierarchy(listOf(listOf(0.0),listOf(2.0),listOf(10.0),listOf(12.0)),"ward","euclidean")
        assertEquals(listOf(0,1,2,3),ward.order)
        assertEquals(1.0/6,ward.links[0].height,1e-12);assertEquals(1.0/6,ward.links[1].height,1e-12);assertEquals(kotlin.math.sqrt(25.0/18),ward.links[2].height,1e-12)
        assertEquals(1.25,statisticsHierarchy(listOf(listOf(0.0,0.0),listOf(3.0,4.0))).links[0].height,1e-12)
        assertEquals(1.75,statisticsHierarchy(listOf(listOf(0.0,0.0),listOf(3.0,4.0)),"single","manhattan").links[0].height,1e-12)
        assertEquals(0.0,statisticsHierarchy(listOf(listOf(1.0,2.0,3.0),listOf(2.0,4.0,6.0)),"single","correlation").links[0].height,1e-12)
    }
    @Test fun constantDisconnectedAndExtremeVectorsKeepAllLeavesAndFiniteLinks() {
        assertEquals(StatisticsHierarchy(emptyList(),emptyList()),statisticsHierarchy(emptyList()))
        val cases=listOf(listOf(listOf(5.0),listOf(5.0),listOf(5.0)),listOf(listOf(null,1.0),listOf(2.0,null),listOf(null,null)),listOf(listOf(-1e308),listOf(0.0),listOf(1e308)),listOf(listOf(1e-300),listOf(2e-300),listOf(3e-300)))
        cases.forEach {vectors->val result=statisticsHierarchy(vectors);assertEquals(vectors.size,result.order.distinct().size);assertTrue(result.links.all {it.height.isFinite()&&it.leftHeight.isFinite()&&it.rightHeight.isFinite()})}
        assertTrue(statisticsHierarchy(listOf(listOf(null,1.0),listOf(2.0,null),listOf(null,null))).links.isEmpty())
    }
}

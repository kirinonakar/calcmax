package com.kirinonakar.symvacas.ui

import org.junit.Assert.*
import org.junit.Test

class StatisticsPlotDataTest {
    private fun assertSwarm(positions:List<Double>,halfWidth:Double):StatisticsBeeswarm {
        val swarm=beeswarmLayout(positions,3.0,halfWidth)
        assertEquals(positions.size,swarm.offsets.size);assertTrue(swarm.radius>0&&swarm.radius<=3)
        swarm.offsets.forEach {assertTrue(it.isFinite()&&kotlin.math.abs(it)+swarm.radius<=halfWidth+1e-8)}
        positions.indices.forEach {i->for(j in 0 until i)assertTrue("points $i and $j overlap",kotlin.math.hypot(positions[i]-positions[j],swarm.offsets[i]-swarm.offsets[j])>=2*swarm.radius-1e-8)}
        assertEquals(swarm,beeswarmLayout(positions,3.0,halfWidth))
        return swarm
    }

    @Test fun beeswarmCentersIsolatedValuesAndSeparatesEqualAndNearbyValues() {
        assertEquals(StatisticsBeeswarm(3.0,emptyList()),beeswarmLayout(emptyList(),3.0,28.0))
        assertEquals(listOf(0.0,0.0,0.0),assertSwarm(listOf(0.0,20.0,40.0),28.0).offsets)
        val swarm=assertSwarm(listOf(20.0,0.0,0.0,0.0,1.0,2.0,21.0,40.0),28.0)
        assertEquals(3.0,swarm.radius,0.0);assertEquals(0.0,swarm.offsets[1],0.0)
        assertTrue(swarm.offsets.any {it>0}&&swarm.offsets.any {it<0})
        val pair=beeswarmLayout(listOf(0.0,1.0),3.0,28.0)
        assertEquals(kotlin.math.sqrt(6.45*6.45-1),kotlin.math.abs(pair.offsets[1]),1e-12)
    }

    @Test fun denseSwarmsPreserveEveryPointAndFitWithinTheGroupWidth() {
        listOf(List(100){0.0},List(100){it*.01},List(80){0.0}+List(80){1.0}+listOf(20.0)).forEach {assertTrue(assertSwarm(it,23.0).radius<3)}
        val large=beeswarmLayout(List(5000){50.0},3.0,28.0)
        assertEquals(5000,large.offsets.size);assertTrue(large.offsets.all {kotlin.math.abs(it)+large.radius<=28+1e-8})
    }

    @Test fun violinDensityHandlesSymmetryExtremeScalesAndDegenerateSamples() {
        val density=violinDensity(listOf(0.0,1.0,2.0))
        assertEquals(97,density.size);assertEquals(1.0,density[48].first,0.0);assertEquals(1.0,density[48].second,0.0)
        density.forEachIndexed {index,point->assertEquals(point.second,density[96-index].second,1e-12)}
        listOf(listOf(-1e308,0.0,1e308),listOf(1e-300,2e-300,3e-300)).forEach {values->assertTrue(violinDensity(values).all {it.first.isFinite()&&it.second.isFinite()})}
        assertTrue(violinDensity(listOf(3.0)).isEmpty());assertTrue(violinDensity(listOf(3.0,3.0,3.0)).isEmpty())
    }

    @Test fun heatMapPreservesZeroMissingCellsAndRawRowLabels() {
        val data=statisticsHeatMapData(listOf(listOf("A","0","1,234"),listOf("B","","-2"),listOf("A","NaN","Infinity")),"xyz","first")
        assertEquals(listOf("y","z"),data.columns);assertEquals(listOf("A","B","A"),data.rows.map {it.label})
        assertEquals(listOf(listOf(0.0,1234.0),listOf(null,-2.0),listOf(null,null)),data.rows.map {it.values})
        assertEquals(.5,heatMapFraction(0.0,0.0,0.0),0.0)
        assertEquals(0.0,heatMapFraction(-1e308,-1e308,1e308),0.0);assertEquals(1.0,heatMapFraction(1e308,-1e308,1e308),0.0)
    }

    @Test fun correlationUsesCompletePairsAndRejectsConstantColumns() {
        val rows=listOf(listOf("1","2","-1",".1","A"),listOf("2","4","-2",".1","B"),listOf("3","8","",".1","C"),listOf("","10","-4",".1","D"),listOf("5","","-5",".1","E"),listOf("","","",".1","F"))
        val data=statisticsCorrelationHeatMap(rows,"columns:5")
        assertEquals(listOf("x","y","z","x4"),data.columns)
        assertEquals(.9819805060619657,data.rows[0].values[1]!!,1e-12);assertEquals(-1.0,data.rows[0].values[2]!!,1e-12)
        assertEquals(3,data.rows[0].counts!![1]);assertEquals(3,data.rows[0].counts!![2]);assertEquals(listOf(null,null,null,null),data.rows[3].values)
        data.rows.forEachIndexed {i,row->row.values.forEachIndexed {j,value->assertEquals(value,data.rows[j].values[i])}}
        assertNull(statisticsCorrelationHeatMap(listOf(listOf("1",""),listOf("","2")),"xy").rows[0].values[1])
        assertEquals(-1.0,statisticsCorrelationHeatMap(listOf(listOf("-1e308","1e308"),listOf("0","0"),listOf("1e308","-1e308")),"xy").rows[0].values[1]!!,1e-12)
    }
}

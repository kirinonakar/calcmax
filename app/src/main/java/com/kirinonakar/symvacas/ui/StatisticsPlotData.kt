package com.kirinonakar.symvacas.ui

import kotlin.math.*

/** Gaussian KDE with Scott's bandwidth, normalized to each violin's peak. */
internal fun violinDensity(values:List<Double>):List<Pair<Double,Double>> {
    val finite=values.filter(Double::isFinite)
    if(finite.size<2)return emptyList()
    val scale=finite.maxOf {abs(it)}.takeIf {it>0} ?: 1.0
    val data=finite.map {it/scale};val lo=data.min();val hi=data.max()
    if(lo==hi)return emptyList()
    val mean=data.sumOf {it/data.size}
    val sd=sqrt(data.sumOf {(it-mean).pow(2)/(data.size-1)})
    val bandwidth=max(sd*data.size.toDouble().pow(-.2),(hi-lo)/1000)
    val samples=List(97) {index->
        val fraction=index/96.0;val at=lo*(1-fraction)+hi*fraction
        at*scale to data.sumOf {exp(-.5*((at-it)/bandwidth).pow(2))}
    }
    val peak=samples.maxOf {it.second}
    return samples.map {it.first to it.second/peak}
}

internal data class StatisticsBeeswarm(val radius:Double,val offsets:List<Double>)

/** Pack circles along the category axis without changing the value coordinates. */
internal fun beeswarmLayout(axisPositions:List<Double>,preferredRadius:Double,halfWidth:Double):StatisticsBeeswarm {
    if(axisPositions.isEmpty())return StatisticsBeeswarm(preferredRadius,emptyList())
    val order=axisPositions.indices.sortedWith(compareBy<Int> {axisPositions[it]}.thenBy {it})
    var duplicates=1;var run=1
    for(i in 1 until order.size) {
        run=if(axisPositions[order[i]]==axisPositions[order[i-1]])run+1 else 1
        duplicates=max(duplicates,run)
    }
    var radius=min(preferredRadius,halfWidth/(1+ceil((duplicates-1)/2.0)*2.15))
    fun pack(radius:Double):List<Double>? {
        val spacing=radius*2.15;val offsets=MutableList(axisPositions.size){0.0};val placed=mutableListOf<Pair<Double,Double>>()
        var first=0;var i=0
        while(i<order.size) {
            val axis=axisPositions[order[i]]
            while(first<placed.size&&axis-placed[first].first>=spacing)first++
            if(first==placed.size) {
                var end=i+1;while(end<order.size&&axisPositions[order[end]]==axis)end++
                for(j in i until end) {
                    val rank=j-i;val offset=if(rank==0)0.0 else ceil(rank/2.0)*spacing*(if(rank%2==1)1 else -1)
                    if(abs(offset)+radius>halfWidth+1e-9)return null
                    offsets[order[j]]=offset;placed.add(axis to offset)
                }
                i=end;continue
            }
            val intervals=(first until placed.size).map {index->
                val (otherAxis,otherOffset)=placed[index];val distance=axis-otherAxis
                val reach=sqrt(max(0.0,spacing*spacing-distance*distance))
                (otherOffset-reach) to (otherOffset+reach)
            }.sortedBy {it.first}
            val merged=mutableListOf<Pair<Double,Double>>()
            intervals.forEach {interval->
                val last=merged.lastOrNull()
                if(last!=null&&interval.first<last.second)merged[merged.lastIndex]=last.first to max(last.second,interval.second) else merged.add(interval)
            }
            val blocked=merged.firstOrNull {it.first<0&&it.second>0}
            val offset=when {
                blocked==null->0.0
                abs(blocked.first)<blocked.second->blocked.first
                abs(blocked.first)>blocked.second->blocked.second
                i%2==1->blocked.second
                else->blocked.first
            }
            if(abs(offset)+radius>halfWidth+1e-9)return null
            offsets[order[i]]=offset;placed.add(axis to offset);i++
        }
        return offsets
    }
    var offsets=pack(radius)
    while(offsets==null){radius*=.8;offsets=pack(radius)}
    return StatisticsBeeswarm(radius,offsets)
}

internal data class StatisticsHeatMapRow(val label:String,val values:List<Double?>,val counts:List<Int>?=null)
internal data class StatisticsHeatMapData(val columns:List<String>,val rows:List<StatisticsHeatMapRow>,val correlation:Boolean=false)
private fun plotNumber(value:String?)=value?.statisticsNumericCell()?.toDoubleOrNull()?.takeIf(Double::isFinite)

internal fun statisticsHeatMapData(rows:List<List<String>>,kind:String,grouping:String="columns"):StatisticsHeatMapData {
    val names=statisticsColumnNames(kind)
    val groupColumn=if(names.size>1&&grouping in listOf("first","last")) {if(grouping=="first")0 else names.lastIndex} else -1
    val indices=names.indices.filter {it!=groupColumn}
    return StatisticsHeatMapData(indices.map {names[it]},rows.mapIndexed {index,row->
        StatisticsHeatMapRow(if(groupColumn<0)(index+1).toString() else row.getOrNull(groupColumn)?.trim().orEmpty().ifBlank {(index+1).toString()},indices.map {plotNumber(row.getOrNull(it))})
    })
}

internal fun statisticsCorrelationHeatMap(rows:List<List<String>>,kind:String):StatisticsHeatMapData {
    val columns=statisticsColumnNames(kind).mapIndexed {index,name->name to rows.map {plotNumber(it.getOrNull(index))}}.filter {it.second.any {value->value!=null}}
    val cells=columns.map {a->columns.map {b->
        val pairs=a.second.mapIndexedNotNull {index,x->val y=b.second[index];if(x!=null&&y!=null)x to y else null}
        val n=pairs.size
        if(n<2||pairs.all {it.first==pairs[0].first}||pairs.all {it.second==pairs[0].second})null to n else {
            val sx=pairs.maxOf {abs(it.first)}.takeIf {it>0} ?: 1.0;val sy=pairs.maxOf {abs(it.second)}.takeIf {it>0} ?: 1.0
            val mx=pairs.sumOf {it.first/sx/n};val my=pairs.sumOf {it.second/sy/n}
            var xx=0.0;var yy=0.0;var xy=0.0
            pairs.forEach {(x,y)->val dx=x/sx-mx;val dy=y/sy-my;xx+=dx*dx;yy+=dy*dy;xy+=dx*dy}
            (if(xx>0&&yy>0) {if(a===b)1.0 else (xy/(sqrt(xx)*sqrt(yy))).coerceIn(-1.0,1.0)} else null) to n
        }
    }}
    return StatisticsHeatMapData(columns.map {it.first},columns.mapIndexed {index,column->StatisticsHeatMapRow(column.first,cells[index].map {it.first},cells[index].map {it.second})},true)
}

internal fun heatMapFraction(value:Double,minimum:Double,maximum:Double):Double {
    if(minimum==maximum)return .5
    val scale=max(abs(minimum),abs(maximum)).takeIf {it>0} ?: 1.0
    return ((value/scale-minimum/scale)/(maximum/scale-minimum/scale)).coerceIn(0.0,1.0)
}

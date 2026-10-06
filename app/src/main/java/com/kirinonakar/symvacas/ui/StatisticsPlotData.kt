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
internal data class StatisticsHeatMapData(val columns:List<String>,val rows:List<StatisticsHeatMapRow>,val correlation:Boolean=false,val clustered:Boolean=false,val rowLinks:List<StatisticsClusterLink> = emptyList(),val columnLinks:List<StatisticsClusterLink> = emptyList(),val mode:String="raw",val correlationMethod:String="pearson")
internal fun statisticsPlotNumber(value:String?)=value?.statisticsNumericCell()?.toDoubleOrNull()?.takeIf(Double::isFinite)

internal fun statisticsHeatMapData(rows:List<List<String>>,kind:String,grouping:String="columns",mode:String="raw",columnNames:List<String> = statisticsColumnNames(kind)):StatisticsHeatMapData {
    val names=statisticsColumnNames(kind)
    val groupColumn=if(names.size>1&&grouping in listOf("first","last")) {if(grouping=="first")0 else names.lastIndex} else -1
    val indices=names.indices.filter {it!=groupColumn}
    val values=rows.map {row->indices.map {statisticsPlotNumber(row.getOrNull(it))}.toMutableList()}.toMutableList()
    when(mode) {
        "zrow"->values.forEach(::standardizeHeatMapValues)
        "zcolumn"->indices.indices.forEach {column->
            val standardized=values.map {it[column]}.toMutableList();standardizeHeatMapValues(standardized)
            standardized.forEachIndexed {row,value->values[row][column]=value}
        }
    }
    return StatisticsHeatMapData(indices.map {columnNames.getOrNull(it)?.takeIf(String::isNotBlank) ?: names[it]},rows.mapIndexed {index,row->
        StatisticsHeatMapRow(if(groupColumn<0)(index+1).toString() else row.getOrNull(groupColumn)?.trim().orEmpty().ifBlank {(index+1).toString()},values[index])
    },mode=mode)
}

private fun standardizeHeatMapValues(values:MutableList<Double?>) {
    val finite=values.mapIndexedNotNull {index,value->value?.let {index to it}}
    if(finite.isEmpty())return
    val scale=finite.maxOf {abs(it.second)}.takeIf {it>0} ?: 1.0
    val mean=finite.sumOf {it.second/scale/finite.size}
    val deviation=sqrt(finite.sumOf {(it.second/scale-mean).pow(2)/finite.size})
    finite.forEach {(index,value)->values[index]=if(deviation==0.0)0.0 else (value/scale-mean)/deviation}
}

internal fun statisticsCorrelationHeatMap(rows:List<List<String>>,kind:String,method:String="pearson",xColumns:List<Int>?=null,yColumns:List<Int>?=null,columnNames:List<String> = statisticsColumnNames(kind)):StatisticsHeatMapData {
    val defaults=statisticsColumnNames(kind)
    val usable=defaults.indices.filter {column->rows.any {statisticsPlotNumber(it.getOrNull(column))!=null}}
    val x=(xColumns?:usable).filter(usable::contains);val y=(yColumns?:usable).filter(usable::contains)
    val cells=y.map {yi->x.map {xi->
        val pairs=rows.mapNotNull {row->val a=statisticsPlotNumber(row.getOrNull(xi));val b=statisticsPlotNumber(row.getOrNull(yi));if(a==null||b==null)null else a to b}
        correlationCoefficient(pairs,method) to pairs.size
    }}
    return StatisticsHeatMapData(x.map {columnNames.getOrNull(it)?.takeIf(String::isNotBlank) ?: defaults[it]},y.mapIndexed {index,column->
        StatisticsHeatMapRow(columnNames.getOrNull(column)?.takeIf(String::isNotBlank) ?: defaults[column],cells[index].map {it.first},cells[index].map {it.second})
    },correlation=true,mode="correlation",correlationMethod=method)
}

private fun correlationCoefficient(source:List<Pair<Double,Double>>,method:String):Double? {
    if(source.size<2)return null
    val pairs=when(method) {"spearman"->rankPairs(source);"kendall"->return kendallTauB(source);else->source}
    val x=pairs.map {it.first};val y=pairs.map {it.second}
    if(x.all {it==x.first()}||y.all {it==y.first()})return null
    val sx=x.maxOf {abs(it)}.takeIf {it>0} ?: 1.0;val sy=y.maxOf {abs(it)}.takeIf {it>0} ?: 1.0
    val mx=x.sumOf {it/sx/x.size};val my=y.sumOf {it/sy/y.size}
    var xx=0.0;var yy=0.0;var xy=0.0
    pairs.forEach {(a,b)->val dx=a/sx-mx;val dy=b/sy-my;xx+=dx*dx;yy+=dy*dy;xy+=dx*dy}
    return if(xx>0&&yy>0)(xy/sqrt(xx*yy)).coerceIn(-1.0,1.0) else null
}

private fun rankPairs(pairs:List<Pair<Double,Double>>):List<Pair<Double,Double>> {
    val ranks=Array(pairs.size){DoubleArray(2)}
    for(axis in 0..1) {
        val ordered=pairs.indices.sortedWith(compareBy<Int> {if(axis==0)pairs[it].first else pairs[it].second}.thenBy {it})
        var start=0
        while(start<ordered.size) {
            val first=if(axis==0)pairs[ordered[start]].first else pairs[ordered[start]].second
            var end=start+1
            while(end<ordered.size&&(if(axis==0)pairs[ordered[end]].first else pairs[ordered[end]].second)==first)end++
            val averageRank=(start+1+end)/2.0
            for(index in start until end)ranks[ordered[index]][axis]=averageRank
            start=end
        }
    }
    return ranks.map {it[0] to it[1]}
}

private fun kendallTauB(pairs:List<Pair<Double,Double>>):Double? {
    val n=pairs.size;val total=n.toDouble()*(n-1)/2.0
    fun ties(axis:Int)=pairs.groupingBy {if(axis==0)it.first else it.second}.eachCount().values.sumOf {it.toDouble()*(it-1)/2.0}
    val tiesX=ties(0);val tiesY=ties(1);val sortedY=pairs.map {it.second}.distinct().sorted();val ranks=sortedY.withIndex().associate {(index,value)->value to index+1}
    val tree=DoubleArray(sortedY.size+1)
    fun query(index:Int):Double {var i=index;var sum=0.0;while(i>0){sum+=tree[i];i-=i and -i};return sum}
    fun add(index:Int){var i=index;while(i<tree.size){tree[i]++;i+=i and -i}}
    val sorted=pairs.sortedWith(compareBy<Pair<Double,Double>> {it.first}.thenBy {it.second});var previous=0.0;var score=0.0;var start=0
    while(start<sorted.size) {
        var end=start+1;while(end<sorted.size&&sorted[end].first==sorted[start].first)end++
        for(index in start until end){val rank=ranks.getValue(sorted[index].second);score+=query(rank-1)-(previous-query(rank))}
        for(index in start until end){add(ranks.getValue(sorted[index].second));previous++}
        start=end
    }
    val denominator=sqrt((total-tiesX)*(total-tiesY))
    return if(denominator>0)(score/denominator).coerceIn(-1.0,1.0) else null
}

internal fun heatMapFraction(value:Double,minimum:Double,maximum:Double):Double {
    if(minimum==maximum)return .5
    val scale=max(abs(minimum),abs(maximum)).takeIf {it>0} ?: 1.0
    return ((value/scale-minimum/scale)/(maximum/scale-minimum/scale)).coerceIn(0.0,1.0)
}

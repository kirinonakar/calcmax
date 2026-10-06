package com.kirinonakar.symvacas.ui

import kotlin.math.abs
import kotlin.math.sqrt

internal data class StatisticsClusterLink(val left:Double,val right:Double,val leftHeight:Double,val rightHeight:Double,val height:Double)
internal data class StatisticsHierarchy(val order:List<Int>,val links:List<StatisticsClusterLink>)

/** Single linkage using a minimum spanning forest, with linear working memory. */
internal fun statisticsHierarchy(vectors:List<List<Double?>>):StatisticsHierarchy {
    val n=vectors.size;val dimensions=vectors.firstOrNull()?.size ?: 0
    if(n==0)return StatisticsHierarchy(emptyList(),emptyList())
    val scale=vectors.maxOfOrNull {row->row.filterNotNull().maxOfOrNull {abs(it)} ?: 0.0}?.takeIf {it>0} ?: 1.0
    val data=Array(n){row->DoubleArray(dimensions){column->vectors[row].getOrNull(column)?.div(scale) ?: Double.NaN}}
    fun distance(a:Int,b:Int):Double {
        var sum=0.0;var count=0
        for(i in 0 until dimensions){val x=data[a][i];val y=data[b][i];if(x.isFinite()&&y.isFinite()){sum+=(x-y)*(x-y);count++}}
        return if(count>0)sqrt(sum*dimensions/count) else Double.POSITIVE_INFINITY
    }
    data class Edge(val a:Int,val b:Int,val height:Double)
    val visited=BooleanArray(n);val nearest=DoubleArray(n){Double.POSITIVE_INFINITY};val from=IntArray(n){-1};val edges=mutableListOf<Edge>()
    repeat(n) {
        var next=-1
        for(i in 0 until n)if(!visited[i]&&(next<0||nearest[i]<nearest[next]))next=i
        visited[next]=true;if(from[next]>=0)edges.add(Edge(from[next],next,nearest[next]))
        for(i in 0 until n)if(!visited[i]){val d=distance(next,i);if(d<nearest[i]){nearest[i]=d;from[i]=next}}
    }
    edges.sortWith(compareBy<Edge>{it.height}.thenBy {it.a}.thenBy {it.b})
    val parent=IntArray(n){it};val roots=IntArray(n){it};val minimum=IntArray(2*n){it};val left=IntArray(2*n){-1};val right=IntArray(2*n){-1};val height=DoubleArray(2*n)
    fun find(value:Int):Int {var i=value;while(parent[i]!=i){parent[i]=parent[parent[i]];i=parent[i]};return i}
    var nextNode=n
    edges.forEach {edge->
        var a=find(edge.a);var b=find(edge.b)
        if(a!=b) {
            if(minimum[roots[a]]>minimum[roots[b]]){val swap=a;a=b;b=swap}
            left[nextNode]=roots[a];right[nextNode]=roots[b];minimum[nextNode]=minimum[roots[a]];height[nextNode]=edge.height
            roots[a]=nextNode++;parent[b]=a
        }
    }
    val forest=(0 until n).filter {find(it)==it}.map {roots[it]}.sortedBy {minimum[it]}
    val order=mutableListOf<Int>();val stack=forest.asReversed().toMutableList()
    while(stack.isNotEmpty()){val node=stack.removeAt(stack.lastIndex);if(node<n)order.add(node) else {stack.add(right[node]);stack.add(left[node])}}
    val centers=DoubleArray(nextNode);order.forEachIndexed {index,node->centers[node]=index.toDouble()}
    val links=(n until nextNode).map {node->
        centers[node]=(centers[left[node]]+centers[right[node]])/2
        StatisticsClusterLink(centers[left[node]],centers[right[node]],height[left[node]],height[right[node]],height[node])
    }
    return StatisticsHierarchy(order,links)
}

internal fun clusteredHeatMap(data:StatisticsHeatMapData):StatisticsHeatMapData {
    val rows=statisticsHierarchy(data.rows.map {it.values})
    val columns=statisticsHierarchy(data.columns.indices.map {column->data.rows.map {it.values[column]}})
    return data.copy(columns=columns.order.map {data.columns[it]},rows=rows.order.map {i->data.rows[i].copy(values=columns.order.map {data.rows[i].values[it]},counts=data.rows[i].counts?.let {counts->columns.order.map {counts[it]}})},clustered=true,rowLinks=rows.links,columnLinks=columns.links)
}

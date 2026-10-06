package com.kirinonakar.symvacas.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class StatisticsClusterLink(val left:Double,val right:Double,val leftHeight:Double,val rightHeight:Double,val height:Double)
internal data class StatisticsHierarchy(val order:List<Int>,val links:List<StatisticsClusterLink>)

/** Hierarchical clustering; single linkage keeps the linear-memory spanning forest.
 *  Ward is only defined for squared Euclidean distances, so it pins the metric. */
internal fun statisticsHierarchy(vectors:List<List<Double?>>,linkage:String="single",metric:String="euclidean"):StatisticsHierarchy {
    val n=vectors.size;val dimensions=vectors.firstOrNull()?.size ?: 0
    if(n==0)return StatisticsHierarchy(emptyList(),emptyList())
    val effectiveMetric=if(linkage=="ward")"euclidean" else metric
    val scale=vectors.maxOfOrNull {row->row.filterNotNull().maxOfOrNull {abs(it)} ?: 0.0}?.takeIf {it>0} ?: 1.0
    val data=Array(n){row->DoubleArray(dimensions){column->vectors[row].getOrNull(column)?.div(scale) ?: Double.NaN}}
    fun distance(a:Int,b:Int):Double {
        var squares=0.0;var absolute=0.0;var count=0;var sumX=0.0;var sumY=0.0;var sumXX=0.0;var sumYY=0.0;var sumXY=0.0
        for(i in 0 until dimensions) {
            val x=data[a][i];val y=data[b][i]
            if(x.isFinite()&&y.isFinite()) {
                val delta=x-y;squares+=delta*delta;absolute+=abs(delta);count++
                if(effectiveMetric=="correlation"){sumX+=x;sumY+=y;sumXX+=x*x;sumYY+=y*y;sumXY+=x*y}
            }
        }
        if(count==0)return Double.POSITIVE_INFINITY
        return when(effectiveMetric) {
            "manhattan"->absolute*dimensions/count
            "correlation"->if(count<2)Double.POSITIVE_INFINITY else {
                val covariance=sumXY-sumX*sumY/count;val varianceX=sumXX-sumX*sumX/count;val varianceY=sumYY-sumY*sumY/count
                if(varianceX<=0||varianceY<=0)1.0 else 1-covariance/sqrt(varianceX*varianceY)
            }
            else->sqrt(squares*dimensions/count)
        }
    }
    if(linkage=="single")return singleLinkage(n){a,b->distance(a,b)}
    require(n<=800) {"Average, complete and Ward linkage support at most 800 labels; use single linkage for larger matrices"}
    return matrixLinkage(n,linkage){a,b->distance(a,b)}
}

/** Sorted minimum-spanning-forest edges give exactly the single-linkage hierarchy. */
private fun singleLinkage(n:Int,distance:(Int,Int)->Double):StatisticsHierarchy {
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

/** Cached nearest-neighbour agglomeration with Lance–Williams updates. */
private fun matrixLinkage(n:Int,linkage:String,distance:(Int,Int)->Double):StatisticsHierarchy {
    val matrix=DoubleArray(n*n);val sizes=IntArray(n){1};val active=BooleanArray(n){true};val least=IntArray(n){it};val root=IntArray(n){it}
    for(i in 0 until n)for(j in i+1 until n) {
        val value=distance(i,j);val stored=if(linkage=="ward")value*value else value
        val safe=if(stored.isFinite())stored else Double.POSITIVE_INFINITY
        matrix[i*n+j]=safe;matrix[j*n+i]=safe
    }
    val left=IntArray(2*n){-1};val right=IntArray(2*n){-1};val heights=DoubleArray(2*n)
    val nearest=IntArray(n){-1};val best=DoubleArray(n){Double.POSITIVE_INFINITY}
    fun refresh(i:Int) {
        var target=-1;var value=Double.POSITIVE_INFINITY
        for(j in 0 until n)if(active[j]&&j!=i){val candidate=matrix[i*n+j];if(candidate<value){value=candidate;target=j}}
        nearest[i]=target;best[i]=value
    }
    for(i in 0 until n)refresh(i)
    var count=n;var nextNode=n
    while(count>1) {
        var a=-1;var value=Double.POSITIVE_INFINITY
        for(i in 0 until n)if(active[i]&&(best[i]<value||(best[i]==value&&a>=0&&least[i]<least[a]))){value=best[i];a=i}
        if(a<0||!value.isFinite())break
        val b=nearest[a];if(b<0)break
        val first=if(least[a]<=least[b])a else b;val second=if(first==a)b else a
        val sizeA=sizes[first];val sizeB=sizes[second];val squared=matrix[first*n+second]
        heights[nextNode]=if(linkage=="ward")sqrt(max(0.0,squared)) else squared
        left[nextNode]=root[first];right[nextNode]=root[second];root[first]=nextNode
        least[first]=min(least[first],least[second])
        for(c in 0 until n)if(active[c]&&c!=first) {
            val otherA=matrix[first*n+c];val otherB=matrix[second*n+c]
            val merged=when(linkage) {
                "average"->(sizeA*otherA+sizeB*otherB)/(sizeA+sizeB)
                "complete"->max(otherA,otherB)
                else->((sizeA+sizes[c])*otherA+(sizeB+sizes[c])*otherB-sizes[c]*squared)/(sizeA+sizeB+sizes[c])
            }
            val safe=if(merged.isFinite())merged else Double.POSITIVE_INFINITY
            matrix[first*n+c]=safe;matrix[c*n+first]=safe
        }
        sizes[first]=sizeA+sizeB;active[second]=false
        refresh(first)
        for(c in 0 until n)if(active[c]&&c!=first&&(nearest[c]==first||nearest[c]==second))refresh(c)
        count--;nextNode++
    }
    val forest=(0 until n).filter {active[it]}.sortedBy {least[it]}.map {root[it]}
    val order=mutableListOf<Int>();val stack=forest.asReversed().toMutableList()
    while(stack.isNotEmpty()){val node=stack.removeAt(stack.lastIndex);if(node<n)order.add(node) else {stack.add(right[node]);stack.add(left[node])}}
    val centers=DoubleArray(nextNode);order.forEachIndexed {index,node->centers[node]=index.toDouble()}
    val links=(n until nextNode).map {node->
        centers[node]=(centers[left[node]]+centers[right[node]])/2
        StatisticsClusterLink(centers[left[node]],centers[right[node]],heights[left[node]],heights[right[node]],heights[node])
    }
    return StatisticsHierarchy(order,links)
}

internal fun clusteredHeatMap(data:StatisticsHeatMapData,linkage:String="single",metric:String="euclidean"):StatisticsHeatMapData {
    val rows=statisticsHierarchy(data.rows.map {it.values},linkage,metric)
    val columns=statisticsHierarchy(data.columns.indices.map {column->data.rows.map {it.values[column]}},linkage,metric)
    return data.copy(columns=columns.order.map {data.columns[it]},rows=rows.order.map {i->data.rows[i].copy(values=columns.order.map {data.rows[i].values[it]},counts=data.rows[i].counts?.let {counts->columns.order.map {counts[it]}})},clustered=true,rowLinks=rows.links,columnLinks=columns.links)
}

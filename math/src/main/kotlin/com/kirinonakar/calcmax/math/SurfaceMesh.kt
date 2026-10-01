package com.kirinonakar.calcmax.math

import kotlin.math.*

data class SurfaceBounds(val xmin:Double,val xmax:Double,val ymin:Double,val ymax:Double,val zmin:Double,val zmax:Double) {
    val limits=listOf(xmin to xmax,ymin to ymax,zmin to zmax)
}
data class SurfaceFace(val points:List<DoubleArray>,val depth:Double,val height:Double,val light:Double)

class SurfaceProjection(val bounds:SurfaceBounds,rotation:Double,elevation:Double) {
    private val theta=Math.toRadians(rotation)
    private val tilt=Math.toRadians(elevation)
    fun normalize(point:DoubleArray)=DoubleArray(3) { i->val (low,high)=bounds.limits[i];2*(point[i]-low)/(high-low)-1 }
    // Screen x, screen y, and depth increasing toward the camera.
    fun project(point:DoubleArray):DoubleArray {
        val p=normalize(point)
        val horizontal=p[0]*cos(theta)-p[1]*sin(theta)
        val depth=p[0]*sin(theta)+p[1]*cos(theta)
        return doubleArrayOf(horizontal,-depth*sin(tilt)-p[2]*cos(tilt),p[2]*sin(tilt)-depth*cos(tilt))
    }
}

object SurfaceMesh {
    fun sampleCount(xmin:Double,xmax:Double,ymin:Double,ymax:Double,requested:Int=26,automatic:Boolean=true,zoom:Double=1.0):Int {
        if(!automatic)return requested.coerceIn(12,96)
        return ceil(max(26.0,max(xmax-xmin,ymax-ymin)*8)*sqrt(zoom)).coerceIn(12.0,96.0).toInt()
    }
    fun zRange(low:Double,high:Double):Pair<Double,Double> {
        if(!low.isFinite()||!high.isFinite())return -1.0 to 1.0
        if(high>low)return low to high
        val padding=max(1.0,abs(low)*.1)
        return low-padding to low+padding
    }
    private fun finite(point:DoubleArray?)=point!=null&&point.size==3&&point.all(Double::isFinite)
    private fun interpolate(a:DoubleArray,b:DoubleArray,t:Double)=DoubleArray(3) {a[it]+(b[it]-a[it])*t}
    fun clipSegment(a:DoubleArray?,b:DoubleArray?,bounds:SurfaceBounds):Pair<DoubleArray,DoubleArray>? {
        if(!finite(a)||!finite(b))return null
        a!!;b!!
        var start=0.0;var end=1.0
        for((axis,limit) in bounds.limits.withIndex()) {
            val (low,high)=limit;val delta=b[axis]-a[axis]
            if(delta==0.0) {if(a[axis]<low||a[axis]>high)return null;continue}
            val t1=(low-a[axis])/delta;val t2=(high-a[axis])/delta
            start=max(start,min(t1,t2));end=min(end,max(t1,t2))
            if(start>=end)return null
        }
        return interpolate(a,b,start) to interpolate(a,b,end)
    }
    fun clipPolygon(points:List<DoubleArray>,bounds:SurfaceBounds):List<DoubleArray> {
        if(!points.all(::finite))return emptyList()
        var polygon=points
        for((axis,limit) in bounds.limits.withIndex())for((edge,above) in listOf(limit.first to true,limit.second to false)) {
            if(polygon.isEmpty())return emptyList()
            val input=polygon;val output=mutableListOf<DoubleArray>()
            var a=input.last();var insideA=if(above)a[axis]>=edge else a[axis]<=edge
            for(b in input) {
                val insideB=if(above)b[axis]>=edge else b[axis]<=edge
                if(insideA!=insideB)output.add(interpolate(a,b,(edge-a[axis])/(b[axis]-a[axis])))
                if(insideB)output.add(b)
                a=b;insideA=insideB
            }
            polygon=output
        }
        return if(polygon.size>=3)polygon else emptyList()
    }
    fun faces(mesh:List<List<DoubleArray?>>,projection:SurfaceProjection):List<SurfaceFace> {
        val bounds=projection.bounds;val faces=mutableListOf<SurfaceFace>()
        for(row in 0 until mesh.lastIndex)for(col in 0 until min(mesh[row].size,mesh[row+1].size)-1) {
            val cell=listOf(mesh[row][col],mesh[row][col+1],mesh[row+1][col+1],mesh[row+1][col])
            if(!cell.all(::finite))continue
            for(indices in listOf(listOf(0,1,2),listOf(0,2,3))) {
                val triangle=indices.map {cell[it]!!};val points=clipPolygon(triangle,bounds)
                if(points.isEmpty())continue
                val (a,b,c)=triangle.map(projection::normalize)
                val u=DoubleArray(3) {b[it]-a[it]};val v=DoubleArray(3) {c[it]-a[it]}
                val normal=doubleArrayOf(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
                val length=sqrt(normal.sumOf {it*it});if(length==0.0)continue
                val light=.35+.65*abs((normal[0]*-.4+normal[1]*-.5+normal[2]*.75)/(length*sqrt(.4*.4+.5*.5+.75*.75)))
                faces.add(SurfaceFace(points,points.map {projection.project(it)[2]}.average(),points.map {(it[2]-bounds.zmin)/(bounds.zmax-bounds.zmin)}.average(),light))
            }
        }
        return faces.sortedBy {it.depth}
    }
}

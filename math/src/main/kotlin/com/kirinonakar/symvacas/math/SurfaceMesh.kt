package com.kirinonakar.symvacas.math

import kotlin.math.*

data class SurfaceBounds(val xmin:Double,val xmax:Double,val ymin:Double,val ymax:Double,val zmin:Double,val zmax:Double) {
    val limits=listOf(xmin to xmax,ymin to ymax,zmin to zmax)
}
data class SurfaceLighting(val start:DoubleArray,val end:DoubleArray,val min:Double,val max:Double)
data class SurfaceFace(val points:List<DoubleArray>,val depth:Double,val height:Double,val light:Double,val lighting:SurfaceLighting?=null)

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
    fun sampleCount(xmin:Double,xmax:Double,ymin:Double,ymax:Double,requested:Int=26,automatic:Boolean=true,zoom:Double=1.0,implicit:Boolean=false):Int {
        val maximum=if(implicit)32 else 96
        if(!automatic)return requested.coerceIn(12,maximum)
        return ceil(max(26.0,max(xmax-xmin,ymax-ymin)*8)*sqrt(zoom)).coerceIn(12.0,maximum.toDouble()).toInt()
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
    fun lightingGradient(points:List<DoubleArray>,levels:List<Double>):SurfaceLighting? {
        val (p,a,b)=points;val dx1=a[0]-p[0];val dy1=a[1]-p[1];val dx2=b[0]-p[0];val dy2=b[1]-p[1]
        val determinant=dx1*dy2-dx2*dy1
        if(abs(determinant)<1e-14)return null
        val l1=levels[1]-levels[0];val l2=levels[2]-levels[0]
        val gx=(l1*dy2-l2*dy1)/determinant;val gy=(dx1*l2-dx2*l1)/determinant
        val length2=gx*gx+gy*gy;val low=levels.min();val high=levels.max()
        if(!length2.isFinite()||length2<1e-16||high-low<1e-7)return null
        val offset=(low-levels[0])/length2;val span=(high-low)/length2
        val start=doubleArrayOf(p[0]+gx*offset,p[1]+gy*offset)
        val end=doubleArrayOf(start[0]+gx*span,start[1]+gy*span)
        return if(start.all(Double::isFinite)&&end.all(Double::isFinite))SurfaceLighting(start,end,low,high) else null
    }
    fun faces(mesh:List<List<DoubleArray?>>,projection:SurfaceProjection,triangles:List<List<DoubleArray>> = emptyList(),triangleNormals:List<List<DoubleArray>> = emptyList()):List<SurfaceFace> {
        val bounds=projection.bounds;val faces=mutableListOf<SurfaceFace>()
        val input=triangles.toMutableList()
        for(row in 0 until mesh.lastIndex)for(col in 0 until min(mesh[row].size,mesh[row+1].size)-1) {
            val cell=listOf(mesh[row][col],mesh[row][col+1],mesh[row+1][col+1],mesh[row+1][col])
            if(!cell.all(::finite))continue
            input.add(listOf(cell[0]!!,cell[1]!!,cell[2]!!));input.add(listOf(cell[0]!!,cell[2]!!,cell[3]!!))
        }
        for((index,triangle) in input.withIndex()) {
                if(triangle.size!=3 || !triangle.all(::finite))continue
                val points=clipPolygon(triangle,bounds)
                if(points.isEmpty())continue
                val (a,b,c)=triangle.map(projection::normalize)
                val u=DoubleArray(3) {b[it]-a[it]};val v=DoubleArray(3) {c[it]-a[it]}
                val normal=doubleArrayOf(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
                val length=sqrt(normal.sumOf {it*it});if(length==0.0)continue
                val light=.35+.65*abs((normal[0]*-.4+normal[1]*-.5+normal[2]*.75)/(length*sqrt(.4*.4+.5*.5+.75*.75)))
                val normals=triangleNormals.getOrNull(index)
                val lighting=if(normals?.size==3 && normals.all {finite(it)&&sqrt(it.sumOf {v->v*v})>0}) {
                    val levels=normals.mapIndexed {i,n->
                        val scaled=DoubleArray(3) {axis->n[axis]*(bounds.limits[axis].second-bounds.limits[axis].first)}
                        val size=sqrt(scaled.sumOf {it*it})
                        val brightness=.35+.65*abs((scaled[0]*-.4+scaled[1]*-.5+scaled[2]*.75)/(size*sqrt(.4*.4+.5*.5+.75*.75)))
                        (.65+.35*((triangle[i][2]-bounds.zmin)/(bounds.zmax-bounds.zmin)).coerceIn(0.0,1.0))*brightness
                    }
                    lightingGradient(triangle.map(projection::project),levels)
                } else null
                faces.add(SurfaceFace(points,points.map {projection.project(it)[2]}.average(),points.map {(it[2]-bounds.zmin)/(bounds.zmax-bounds.zmin)}.average(),light,lighting))
        }
        return faces.sortedBy {it.depth}
    }
}

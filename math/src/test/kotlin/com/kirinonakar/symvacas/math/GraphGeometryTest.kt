package com.kirinonakar.symvacas.math

import org.junit.Assert.*
import org.junit.Test

class GraphClipTest {
    private fun clip(a:Pair<Double,Double>?,b:Pair<Double,Double>?)=GraphClip.segment(a,b,-10.0,10.0,-5.0,5.0)
    @Test fun hugeExponentialSegmentsClipBeforeConversionToScreenFloats() {
        val segment=clip(0.0 to 0.0,1.0 to 1e99)!!
        assertEquals(0.0 to 0.0,segment.first)
        assertEquals(5e-99,segment.second.first,1e-110);assertEquals(5.0,segment.second.second,0.0)
        assertEquals(segment.second to segment.first,clip(1.0 to 1e99,0.0 to 0.0))
        assertNull(clip(1.0 to 1e50,2.0 to 1e99))
        assertNull(clip(null,0.0 to 0.0))
        assertEquals((-5.0 to -5.0) to (5.0 to 5.0),clip(-100.0 to -100.0,100.0 to 100.0))
        assertEquals((0.0 to -5.0) to (0.0 to 5.0),clip(0.0 to -1e99,0.0 to 1e99))
    }
}

class SurfaceMeshTest {
    @Test fun combinedSurfacesSortTogetherAndRetainEachClosedShellsCulling() {
        val box=SurfaceBounds(-1.0,1.0,-1.0,1.0,-1.0,1.0)
        val triangle=listOf(doubleArrayOf(-.5,-.5,0.0),doubleArrayOf(.5,-.5,0.0),doubleArrayOf(0.0,.5,0.0))
        val group=SurfaceMesh.prepare(emptyList(),box,listOf(triangle))
        val closed=group.copy(closed=true)
        val combined=SurfaceMesh.combine(listOf(group,closed,group,closed,group))
        assertFalse(combined.convex);assertFalse(combined.closed)
        for(elevation in listOf(-32.0,32.0)) {
            val faces=SurfaceMesh.project(combined,SurfaceProjection(box,35.0,elevation))
            for(i in listOf(0,2,4))assertTrue(faces.any {it.group==i})
            assertTrue(faces.filter {it.group in listOf(1,3)}.all {it.front})
            assertTrue(faces.zipWithNext().all {(a,b)->a.depth<=b.depth})
        }
    }
    private val bounds=SurfaceBounds(-1.0,1.0,-1.0,1.0,-1.0,1.0)
    private fun p(x:Double,y:Double,z:Double)=doubleArrayOf(x,y,z)
    private fun inside(point:DoubleArray)=point.all {it>=-1.0-1e-12&&it<=1.0+1e-12}
    @Test fun convexEllipsoidsKeepBackFacesBehindFrontFacesAtAllAngles() {
        val vertices=listOf(p(2.0,0.0,0.0),p(-2.0,0.0,0.0),p(0.0,1.0,0.0),p(0.0,-1.0,0.0),p(0.0,0.0,.5),p(0.0,0.0,-.5))
        val indices=listOf(listOf(0,2,4),listOf(2,1,4),listOf(1,3,4),listOf(3,0,4),listOf(2,0,5),listOf(1,2,5),listOf(3,1,5),listOf(0,3,5))
        val triangles=indices.map {face->face.map {vertices[it]}}
        val normals=indices.map {face->face.map {i->DoubleArray(3){axis->vertices[i][axis]/listOf(4.0,1.0,.25)[axis]}}}
        val box=SurfaceBounds(-3.0,3.0,-2.0,2.0,-1.0,1.0)
        val prepared=SurfaceMesh.prepare(emptyList(),box,triangles,normals,true)
        assertTrue(prepared.closed)
        val cut=box.copy(xmax=.5);val clipped=SurfaceMesh.prepare(emptyList(),cut,triangles,normals,true);assertFalse(clipped.closed)
        for(rotation in listOf(0.0,35.0,90.0,135.0,180.0,270.0,359.0))for(elevation in listOf(-90.0,-32.0,0.0,32.0,90.0)) {
            val faces=SurfaceMesh.project(prepared,SurfaceProjection(box,rotation,elevation));assertTrue(faces.isNotEmpty());assertTrue(faces.all {it.front})
            var front=false
            for(face in SurfaceMesh.project(clipped,SurfaceProjection(cut,rotation,elevation))) {if(face.front)front=true else assertFalse(front)}
        }
    }
    @Test fun smoothLightingReproducesVerticesAndAgreesAcrossSharedEdges() {
        val points=listOf(p(0.0,0.0,0.0),p(2.0,0.0,0.0),p(0.0,2.0,0.0))
        val first=SurfaceMesh.lightingGradient(points,listOf(.3,.8,.5))!!
        val second=SurfaceMesh.lightingGradient(listOf(points[1],p(2.0,2.0,0.0),points[2]),listOf(.8,.9,.5))!!
        fun brightness(gradient:SurfaceLighting,point:DoubleArray):Double {
            val dx=gradient.end[0]-gradient.start[0];val dy=gradient.end[1]-gradient.start[1]
            return gradient.min+(gradient.max-gradient.min)*((point[0]-gradient.start[0])*dx+(point[1]-gradient.start[1])*dy)/(dx*dx+dy*dy)
        }
        points.forEachIndexed {i,p->assertEquals(listOf(.3,.8,.5)[i],brightness(first,p),1e-12)}
        for(t in listOf(0.0,.25,.5,.75,1.0)) {
            val point=p(2*(1-t),2*t,0.0)
            assertEquals(brightness(first,point),brightness(second,point),1e-12)
        }
        assertNull(SurfaceMesh.lightingGradient(points,listOf(.5,.5,.5)))
    }
    @Test fun adaptiveDensityGrowsWithRangeAndZoomAndRespectsBudgetAndManualControl() {
        assertEquals(26,SurfaceMesh.sampleCount(-1.0,1.0,-1.0,1.0))
        assertEquals(80,SurfaceMesh.sampleCount(-5.0,5.0,-3.0,3.0))
        assertTrue(SurfaceMesh.sampleCount(-5.0,5.0,-3.0,3.0,zoom=.4)<80)
        assertEquals(96,SurfaceMesh.sampleCount(-5.0,5.0,-3.0,3.0,zoom=3.0))
        assertEquals(96,SurfaceMesh.sampleCount(-1000.0,1000.0,-3.0,3.0))
        assertEquals(40,SurfaceMesh.sampleCount(-5.0,5.0,-3.0,3.0,40,false,3.0))
    }
    @Test fun surfaceGeometryClipsAndOrdersFacesWithoutBridgingHoles() {
        run { // clipsSegmentsAcrossAllThreeAxesAndRejectsHoles
            val segment=SurfaceMesh.clipSegment(p(-2.0,0.0,-2.0),p(2.0,0.0,2.0),bounds)!!
            assertArrayEquals(p(-1.0,0.0,-1.0),segment.first,1e-12)
            assertArrayEquals(p(1.0,0.0,1.0),segment.second,1e-12)
            assertNull(SurfaceMesh.clipSegment(p(0.0,0.0,2.0),p(1.0,1.0,2.0),bounds))
            assertNull(SurfaceMesh.clipSegment(null,p(1.0,1.0,0.0),bounds))
            assertNull(SurfaceMesh.clipSegment(p(Double.NaN,0.0,0.0),p(1.0,1.0,0.0),bounds))
            val clipped=SurfaceMesh.clipPolygon(listOf(p(-2.0,-2.0,-2.0),p(2.0,-2.0,0.0),p(0.0,2.0,2.0)),bounds)
            assertTrue(clipped.size>=3);assertTrue(clipped.all(::inside))
            assertTrue(SurfaceMesh.clipPolygon(listOf(p(0.0,0.0,2.0),p(1.0,0.0,3.0),p(0.0,1.0,3.0)),bounds).isEmpty())
        }
        run { // facesAreClippedShadedAndOrderedAfterRotation
            val mesh=listOf(listOf(p(-1.0,-1.0,-2.0),p(1.0,-1.0,0.0)),listOf(p(-1.0,1.0,0.0),p(1.0,1.0,2.0)))
            for(rotation in listOf(0.0,35.0,90.0,180.0,270.0,359.0))for(elevation in listOf(-90.0,-45.0,0.0,32.0,90.0)) {
                val projection=SurfaceProjection(bounds,rotation,elevation)
                val faces=SurfaceMesh.faces(mesh,projection)
                assertEquals(2,faces.size)
                assertTrue(faces.all {face->face.points.all(::inside)&&face.height in 0.0..1.0&&face.light in .35..1.0})
                assertTrue(faces.zipWithNext().all {(a,b)->a.depth<=b.depth})
            }
            val missing=listOf(listOf(null,p(1.0,-1.0,0.0)),listOf(p(-1.0,1.0,0.0),p(1.0,1.0,0.0)))
            assertTrue(SurfaceMesh.faces(missing,SurfaceProjection(bounds,35.0,32.0)).isEmpty())
        }
    }

    @Test fun projectionAndConstantRangesRemainFinite() {
        run { // projectionAndConstantRangesRemainFinite
            val original=SurfaceProjection(bounds,0.0,32.0).project(p(1.0,0.0,0.0))
            val rotated=SurfaceProjection(bounds,90.0,32.0).project(p(1.0,0.0,0.0))
            assertEquals(1.0,original[0],1e-12);assertEquals(0.0,rotated[0],1e-12)
            assertNotEquals(original[2],rotated[2],1e-12)
            assertEquals(37.8 to 46.2,SurfaceMesh.zRange(42.0,42.0))
            assertEquals(-1.0 to 1.0,SurfaceMesh.zRange(0.0,0.0))
        }
        run { // zeroSeparationNeverProducesInfiniteZoom
            assertEquals(1.0 to 1.0,GraphZoom.factors("xy",0f,0f,20f,20f))
        }
    }
}

class PiAxisTest {

}

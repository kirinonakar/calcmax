package com.kirinonakar.calcmax.math

import org.junit.Assert.*
import org.junit.Test

class SurfaceMeshTest {
    private val bounds=SurfaceBounds(-1.0,1.0,-1.0,1.0,-1.0,1.0)
    private fun p(x:Double,y:Double,z:Double)=doubleArrayOf(x,y,z)
    private fun inside(point:DoubleArray)=point.all {it>=-1.0-1e-12&&it<=1.0+1e-12}
    @Test fun adaptiveDensityGrowsWithRangeAndZoomAndRespectsBudgetAndManualControl() {
        assertEquals(26,SurfaceMesh.sampleCount(-1.0,1.0,-1.0,1.0))
        assertEquals(80,SurfaceMesh.sampleCount(-5.0,5.0,-3.0,3.0))
        assertTrue(SurfaceMesh.sampleCount(-5.0,5.0,-3.0,3.0,zoom=.4)<80)
        assertEquals(96,SurfaceMesh.sampleCount(-5.0,5.0,-3.0,3.0,zoom=3.0))
        assertEquals(96,SurfaceMesh.sampleCount(-1000.0,1000.0,-3.0,3.0))
        assertEquals(40,SurfaceMesh.sampleCount(-5.0,5.0,-3.0,3.0,40,false,3.0))
    }
    @Test fun clipsSegmentsAcrossAllThreeAxesAndRejectsHoles() {
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
    @Test fun facesAreClippedShadedAndOrderedAfterRotation() {
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
    @Test fun projectionAndConstantRangesRemainFinite() {
        val original=SurfaceProjection(bounds,0.0,32.0).project(p(1.0,0.0,0.0))
        val rotated=SurfaceProjection(bounds,90.0,32.0).project(p(1.0,0.0,0.0))
        assertEquals(1.0,original[0],1e-12);assertEquals(0.0,rotated[0],1e-12)
        assertNotEquals(original[2],rotated[2],1e-12)
        assertEquals(37.8 to 46.2,SurfaceMesh.zRange(42.0,42.0))
        assertEquals(-1.0 to 1.0,SurfaceMesh.zRange(0.0,0.0))
    }
}

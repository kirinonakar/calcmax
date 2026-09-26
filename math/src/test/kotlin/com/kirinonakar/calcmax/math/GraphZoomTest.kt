package com.kirinonakar.calcmax.math
import org.junit.Assert.*
import org.junit.Test

class GraphZoomTest {
    @Test fun independentAxesAndDiagonalScale() {
        assertEquals("x",GraphZoom.axis(100f,10f))
        assertEquals("y",GraphZoom.axis(10f,100f))
        assertEquals("xy",GraphZoom.axis(100f,100f))
        assertEquals(2.0 to 1.0,GraphZoom.factors("x",100f,0f,200f,0f))
        assertEquals(1.0 to 2.0,GraphZoom.factors("y",0f,100f,0f,200f))
        assertEquals(2.0 to 2.0,GraphZoom.factors("xy",100f,100f,200f,200f))
    }
    @Test fun zeroSeparationNeverProducesInfiniteZoom() {
        assertEquals(1.0 to 1.0,GraphZoom.factors("xy",0f,0f,20f,20f))
    }
}

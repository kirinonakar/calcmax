package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.graphTracePointAtX
import org.junit.Assert.*
import org.junit.Test

class GraphTraceTest {
    @Test fun steepCurveKeepsTheTouchedXEvenWhenTheFingerIsAwayFromTheCurve() {
        val point=graphTracePointAtX(listOf(-10.0 to -20.0,10.0 to 20.0),1.25,-4.0)!!
        assertEquals(1.25,point.first,0.0)
        assertEquals(2.5,point.second,1e-12)
    }

    @Test fun implicitCurvesChooseTheBranchNearTheTouchedY() {
        val curve=listOf(-2.0 to 2.0,2.0 to 2.0,null,2.0 to -2.0,-2.0 to -2.0)
        assertEquals(0.5 to 2.0,graphTracePointAtX(curve,0.5,1.0))
        assertEquals(0.5 to -2.0,graphTracePointAtX(curve,0.5,-1.0))
        assertEquals(0.0 to 0.5,graphTracePointAtX(listOf(0.0 to -2.0,0.0 to 2.0),0.0,0.5))
    }

    @Test fun missingAndNonFiniteSamplesDoNotConnectUnrelatedCurveSegments() {
        for(gap in listOf(null,0.0 to Double.NaN,Double.POSITIVE_INFINITY to 0.0)) {
            assertNull(graphTracePointAtX(listOf(-1.0 to -1.0,gap,1.0 to 1.0),0.0,0.0))
        }
        assertNull(graphTracePointAtX(emptyList(),0.0,0.0))
        assertNull(graphTracePointAtX(listOf(0.0 to 0.0,1.0 to 1.0),2.0,0.0))
        assertNull(graphTracePointAtX(listOf(0.0 to 0.0),Double.NaN,0.0))
        assertEquals(1.0 to 1.0,graphTracePointAtX(listOf(1.0 to 1.0),1.0,0.0))
    }
}

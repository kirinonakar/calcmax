package com.kirinonakar.calcmax.math
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.PI
class PiAxisTest {
    @Test fun radianLabels(){assertEquals("π",PiAxis.label(PI));assertEquals("−π/2",PiAxis.label(-PI/2));assertEquals("3π/4",PiAxis.label(3*PI/4));assertEquals("0",PiAxis.label(0.0))}
    @Test fun finiteGridSpacing(){for(range in listOf(.01,1.0,20.0,1e8)){assertTrue(PiAxis.step(range)>0);assertTrue(range/PiAxis.step(range)<=8.01)}}
}

package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.graphDisplayNumber
import com.kirinonakar.calcmax.calculator.appendGraphSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GraphDisplayFormatTest {
    @Test fun graphNumbersUseDecimalPlacesAndKeepEditableScientificNotation() {
        assertEquals("12345.123",graphDisplayNumber(12345.123456789,3))
        assertEquals("-1.235",graphDisplayNumber(-1.23456789,3))
        assertEquals("0",graphDisplayNumber("-0.00001",3))
        assertEquals("-1e-5",graphDisplayNumber(-0.00001,3))
        assertEquals("1.235e-10",graphDisplayNumber("1.23456789e-10",3))
        assertEquals("1.23456789",graphDisplayNumber(1.23456789,10))
        assertEquals("—",graphDisplayNumber(Double.NaN,3))
    }

    @Test fun transfersAppendAndPreserveEquationsAndShading() {
        val source="sin(x)\ny=x+1\n[shade] y<x"
        assertEquals("$source\nx^2+y^2=1",appendGraphSource(source,"x^2+y^2=1"))
        assertEquals(source,appendGraphSource(source,"y=x+1"))
        assertEquals("x",appendGraphSource("","x"))
        assertThrows(IllegalArgumentException::class.java) {appendGraphSource("x\n2*x\n3*x\n4*x\n5*x\n6*x","7*x")}
        assertThrows(IllegalArgumentException::class.java) {appendGraphSource("x+y","x-y","surface")}
    }
}

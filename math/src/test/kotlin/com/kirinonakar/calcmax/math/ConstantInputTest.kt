package com.kirinonakar.calcmax.math

import org.junit.Assert.*
import org.junit.Test

class ConstantInputTest {
    @Test fun consecutiveConstantsAreProducts() {
        for(left in listOf("Ans","pi","e","i","oo")) {
            for(right in listOf("Ans","pi","e","i","oo")) {
                val editor=Editor().insertOperand(left).insertOperand(right)
                assertEquals("$left*$right",editor.source)
                assertEquals("*",editor.tree()?.value)
                assertEquals(listOf(left,right),editor.tree()?.args?.map {it.value})
            }
        }
    }
    @Test fun insertionSeparatesNumbersAndBothIdentifierBoundaries() {
        assertEquals("2*e",Editor("2").insertConstant("e").source)
        val middle=Editor("xy",1).insertConstant("pi")
        assertEquals("x*pi*y",middle.source)
        assertEquals(4,middle.cursor)
        assertEquals("Ans*pi",Editor("Ans*x").selectRange(4,5).insertConstant("pi").source)
        assertEquals("sin(pi)",Editor("sin()",4).insertConstant("pi").source)
        assertEquals("pi",Editor("Ans").selectRange(0,3).insertConstant("pi").source)
        assertEquals("pie",Editor().insertOperand("pie").source)
    }
}

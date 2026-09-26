package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.CopyCycle
import org.junit.Assert.*
import org.junit.Test

class CopyCycleTest {
    @Test fun copyAlternatesBetweenAnswerAndExpression() {
        var copyExpression=false
        val copied=mutableListOf<String>()
        val labels=mutableListOf<String>()
        repeat(4) {
            val target=CopyCycle.next("42","3+4",copyExpression)
            copied+=target.text
            labels+=target.label
            copyExpression=target.expressionNext
        }
        assertEquals(listOf("42","3+4","42","3+4"),copied)
        assertEquals(listOf("Copy =","Copy ƒ","Copy =","Copy ƒ"),labels)
        assertFalse(copyExpression)
    }
    @Test fun expressionIsCopiedWhenNoAnswerExists() {
        val target=CopyCycle.next(null,"3+4",false)
        assertEquals("3+4",target.text)
        assertFalse(target.expressionNext)
    }
    @Test fun blankExpressionFallsBackToAnswer() {
        val target=CopyCycle.next("42","",true)
        assertEquals("42",target.text)
        assertTrue(target.expressionNext)
    }
    @Test fun blankInputCopiesNothing() {
        val target=CopyCycle.next("   ","",false)
        assertEquals("",target.text)
        assertFalse(target.expressionNext)
    }
}

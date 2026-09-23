package com.example.calcmax.math
import org.junit.Assert.*
import org.junit.Test
class StructuredEditorTest {
    @Test fun exponentHasSeparateInsideAndOutsideStops() {
        val selected=Editor("3^2+6").selectRange(2,3)
        val inside=selected.move(1)
        assertEquals(3,inside.cursor)
        assertEquals("3^(2+3)+6",inside.insert("+3").source)
        val outside=inside.move(1)
        assertEquals(3,outside.cursor)
        assertNull(outside.exponent)
        assertEquals("3^2+3+6",outside.insert("+3").source)
    }
    @Test fun parenthesesDoNotSkipTheInnerCursorPosition() {
        val inside=Editor("(x)").selectRange(1,2).move(1)
        assertEquals(2,inside.cursor)
        assertEquals("(x+1)",inside.insert("+1").source)
        assertEquals("(x)+1",inside.move(1).insert("+1").source)
    }
    @Test fun adjacentTermsHaveOneCaretOwner() {
        val editor=Editor("sin(3pi)",5)
        assertEquals(4..5,editor.cursorTarget())
    }
    @Test fun groupedExponentEditingKeepsItsGrouping() {
        val editor=Editor("3^(2)+6").selectRange(3,4).move(1)
        assertEquals("3^(2+3)+6",editor.insert("+3").source)
        assertEquals("3^(2)+3+6",editor.move(1).insert("+3").source)
    }
    @Test fun leavingDenominatorPlacesCaretOnTheFractionAxis(){
        val editor=Editor("(1)/(3)+6",6).move(1)
        assertEquals(0..7,editor.cursorTarget())
        assertEquals("(1)/(3)+2+6",editor.insert("+2").source)
    }
}

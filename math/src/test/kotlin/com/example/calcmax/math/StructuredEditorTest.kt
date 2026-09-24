package com.example.calcmax.math
import org.junit.Assert.*
import org.junit.Test
class StructuredEditorTest {
    @Test fun emptyStatisticListKeepsDelimitersAfterCursorPlacement() {
        val editor=Editor("mean([])").placeInToken(5,7,6)
        assertEquals(6,editor.cursor)
        assertEquals("mean([1,2,3])",editor.insert("1,2,3").source)
    }
    @Test fun malformedExpressionStillHasAddressableCursor() {
        val e=Editor("123-434+545)",0)
        assertNull(e.tree())
        assertEquals(0..3,e.cursorTarget())
        assertNotNull(e.insert("(").tree())
        val inside=e.selectRange(0,3).placeInToken(0,3,1)
        assertEquals("1923-434+545)",inside.insert("9").source)
        assertEquals(0..3,inside.cursorTarget())
        val continued=Editor("1234").placeInToken(0,4,4).insert("+")
        assertNull(continued.activeToken)
        assertNotNull(continued.cursorTarget())
    }
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
    @Test fun unclosedParenthesisIsMarkedOpen() {
        assertEquals("open",Editor("3*(").tree()!!.args[1].value)
        assertEquals("group",Editor("3*(5)").tree()!!.args[1].kind)
        assertEquals("",Editor("3*(5)").tree()!!.args[1].value)
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
    @Test fun trailingOperatorHolesOwnTheCaret() {
        val addition=Editor("49").insert("+")
        assertEquals(3..3,addition.cursorTarget())
        assertEquals("49+2",addition.insert("2").source)

        val division=Editor("49").insert("÷")
        assertEquals("/",division.tree()?.value)
        assertEquals(3..3,division.cursorTarget())
        val denominator=division.insert("2")
        assertEquals("49÷2",denominator.source)
        assertEquals("÷",denominator.tree()?.displayOperator)
        assertTrue(denominator.tree()!!.json().contains("\"displayOperator\":\"÷\""))
        assertEquals(3..4,denominator.cursorTarget())
        assertEquals(3..5,denominator.insert("3").cursorTarget())
        val afterFraction=denominator.move(1)
        assertEquals(0..4,afterFraction.cursorTarget())
        assertEquals(3..4,afterFraction.move(-1).cursorTarget())
        assertEquals(0..4,denominator.after(0,4).cursorTarget())

        val fraction=Editor("49").selectRange(0,2).insert("(49)/()",6)
        assertEquals(6..6,fraction.cursorTarget())
        assertEquals("(49)/(2)",fraction.insert("2").source)
        assertEquals("",fraction.insert("2").tree()?.displayOperator)
    }
}

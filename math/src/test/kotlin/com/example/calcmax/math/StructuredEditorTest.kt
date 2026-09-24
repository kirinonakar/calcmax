package com.example.calcmax.math
import org.junit.Assert.*
import org.junit.Test
class StructuredEditorTest {
    @Test fun deletingMiddleProductTermKeepsABoxThenRemovesItsOperator() {
        val source="integrate(3*3*4,x,,)"
        val middle=Editor(source,source.indexOf("*3")+2).delete()
        assertEquals("integrate(3*()*4,x,,)",middle.source)
        assertEquals("integrate",middle.tree()?.value)
        assertFalse(middle.tree()!!.nodes().any {it.kind=="binary"&&it.value=="^"})
        val joined=middle.delete()
        assertEquals("integrate(3*4,x,,)",joined.source)
        assertEquals("integrate",joined.tree()?.value)
        val unicode="integrate(3×3×4,x,,)"
        assertEquals("integrate(3×()×4,x,,)",Editor(unicode,unicode.indexOf("×3")+2).delete().source)
        assertEquals(unicode.replace("×3", ""),Editor(unicode,unicode.indexOf("×3")+2).delete().delete().source)
        val spaced="integrate(3 * 3 * 4,x,,)"
        val spacedHole=Editor(spaced,spaced.indexOf("* 3")+3).delete()
        assertEquals("integrate(3 * () * 4,x,,)",spacedHole.source)
        assertEquals("integrate(3 * 4,x,,)",spacedHole.delete().source)
    }
    @Test fun deletingEmptyExponentBoxRemovesPowerTailInsideIntegral() {
        val source="integrate(3^(2),x,,)"
        val empty=Editor(source,source.indexOf('2')+1).delete()
        assertEquals("integrate(3^(),x,,)",empty.source)
        assertEquals("integrate(3,x,,)",empty.delete().source)
        assertEquals("integrate(3,x,,)",empty.deleteForward().source)
        assertEquals(empty,empty.move(1))
        val base=empty.move(-1)
        assertEquals(source.indexOf('3')+1,base.cursor)
        assertEquals("integrate(39^(),x,,)",base.insert("9").source)
        assertEquals("integrate(3^(9),x,,)",empty.insert("9").source)
        val outsidePower=empty.insert("9").move(1)
        assertEquals("integrate(3^(9)*8,x,,)",outsidePower.insert("8").source)
    }
    @Test fun fractionHiddenSlotsNavigateAndEmptyDenominatorDeletesFractionTail() {
        val source="integrate(()/(),x,,)"
        val numerator=Editor(source,source.indexOf('(' ,10)+1).insert("3")
        assertEquals("integrate((3)/(),x,,)",numerator.source)
        val denominator=numerator.move(1)
        assertEquals(denominator.source.indexOf("/(")+2,denominator.cursor)
        assertEquals(numerator.cursor,denominator.move(-1).cursor)
        assertEquals("integrate((3)/(4),x,,)",denominator.insert("4").source)
        assertEquals("integrate(3,x,,)",denominator.delete().source)
        assertEquals("integrate(3,x,,)",denominator.deleteForward().source)
        assertEquals(denominator,denominator.move(1))
        val emptyNumerator=Editor(source,source.indexOf('(',10)+1)
        assertEquals(emptyNumerator,emptyNumerator.delete())
        val filledDenominator=denominator.insert("4")
        val outsideFraction=filledDenominator.move(1)
        assertEquals("integrate((3)/(4)*5,x,,)",outsideFraction.insert("5").source)
        assertEquals("integrate",outsideFraction.insert("5").tree()?.value)
        val withFollowingFactor="integrate((33)/(45)*3,x,,)"
        val afterHiddenClose=Editor(withFollowingFactor,withFollowingFactor.indexOf(")*3")+1)
        assertEquals("integrate((33)/(45)*6*3,x,,)",afterHiddenClose.insert("6").source)
        assertEquals("integrate",afterHiddenClose.insert("6").tree()?.value)
        val emptyAfterClose="integrate((33)/()*3,x,,)"
        val afterEmptyClose=Editor(emptyAfterClose,emptyAfterClose.indexOf(")*3")+1)
        assertEquals("integrate((33)/(6)*3,x,,)",afterEmptyClose.insert("6").source)
        val bare=Editor("integrate(9/(),x,,)",11)
        assertEquals(bare.source.indexOf("/(")+2,bare.move(1).cursor)
        assertEquals(bare.cursor,bare.move(1).move(-1).cursor)
    }
    @Test fun deletingSelectedDenominatorAlsoRemovesHiddenNumeratorGrouping() {
        val source="integrate((33)/(45)*3,x,,)"
        val denominatorStart=source.indexOf("(45)")
        val group=Editor(source).selectRange(denominatorStart,denominatorStart+4).delete()
        assertEquals("integrate(33*3,x,,)",group.source)
        assertEquals("integrate",group.tree()?.value)
        val number=Editor(source).selectRange(denominatorStart+1,denominatorStart+3).delete()
        assertEquals(group.source,number.source)
        val backspaces=Editor(source,denominatorStart+3).delete().delete().delete()
        assertEquals(group.source,backspaces.source)
        val sumSource="integrate((3+3)/(),x,,)"
        val sumDenominator=sumSource.indexOf("/()")+2
        val sum=Editor(sumSource,sumDenominator).delete()
        assertEquals("integrate((3+3),x,,)",sum.source)
        assertEquals("integrate((3+3)*6,x,,)",sum.insert("6").source)
    }
    @Test fun deleteAfterHiddenDenominatorCloseKeepsFractionStructure() {
        val source="integrate((33)/(45)*3,x,,)"
        val position=source.indexOf(")*3")+1
        val outside=Editor(source,position)
        val fraction=outside.tree()!!.args[0].args[0]
        assertEquals(fraction.start..fraction.end,outside.cursorTarget())
        val deleted=outside.delete()
        assertEquals("integrate((33)/(4)*3,x,,)",deleted.source)
        assertEquals("integrate",deleted.tree()?.value)
        assertEquals(source,Editor(source,position-1).deleteForward().source)
        val emptySource="integrate((33)/()*3,x,,)"
        val emptyPosition=emptySource.indexOf(")*3")+1
        assertEquals("integrate(33*3,x,,)",Editor(emptySource,emptyPosition).delete().source)
    }
    @Test fun deleteBeforeIntegralExponentClearsBaseInsteadOfPowerOperator() {
        val source="integrate(3^2,x,,)"
        val beforeExponent=Editor(source,source.indexOf('2'),exponent=source.indexOf('2')..source.indexOf('2')+1)
        val empty=beforeExponent.delete()
        assertEquals("integrate(()^2,x,,)",empty.source)
        assertEquals(11,empty.cursor)
        assertEquals("integrate",empty.tree()?.value)
        assertEquals("^",empty.tree()?.args?.firstOrNull()?.value)
        assertEquals(empty,empty.delete())
        assertEquals(empty.source,Editor(source,source.indexOf('^')).deleteForward().source)
    }
    @Test fun filledIntegralPowerBoxMovesPastHiddenClosingParenthesis() {
        val source="integrate(3^2,x,,)"
        val filled=Editor(source,source.indexOf('2')).delete().insert("9")
        assertEquals("integrate((9)^2,x,,)",filled.source)
        val exponent=filled.move(1)
        assertEquals(filled.source.indexOf('2'),exponent.cursor)
        assertEquals("integrate((9)^(82),x,,)",exponent.insert("8").source)
        assertEquals("integrate",exponent.insert("8").tree()?.value)
        val backToBase=exponent.move(-1)
        assertEquals(filled.cursor,backToBase.cursor)
        assertEquals("integrate((98)^2,x,,)",backToBase.insert("8").source)
        assertEquals(exponent.cursor,backToBase.move(1).cursor)
    }
    @Test fun exponentKeyShowsCaretAfterWholePowerInsideIntegral() {
        val editor=Editor("integrate(,x,,)",10).insert("5").insert("^2")
        assertEquals("integrate(5^2,x,,)",editor.source)
        assertEquals(13,editor.cursor)
        assertEquals(10..13,editor.cursorTarget())
        assertEquals("integrate(5^2*4,x,,)",editor.insert("4").source)
        assertEquals("integrate",editor.insert("4").tree()?.value)
        assertEquals("integrate(5^2*sin(),x,,)",editor.insert("sin()").source)
        val insideExponent=editor.move(-1)
        assertEquals(12..13,insideExponent.cursorTarget())
        assertEquals("integrate(5^(24),x,,)",insideExponent.insert("4").source)
        assertEquals(10..13,insideExponent.move(1).cursorTarget())
    }
    @Test fun deletingIntegralInputPreservesItsHiddenOpeningParenthesis() {
        val filled=Editor("integrate(,x,,)",10).insert("2")
        val empty=filled.delete()
        assertEquals("integrate(,x,,)",empty.source)
        assertEquals(empty,empty.delete())
        assertEquals("call",empty.tree()?.kind)
        assertEquals("integrate",empty.tree()?.value)
        assertEquals(empty.source,Editor(empty.source,9).deleteForward().source)
        assertEquals("",empty.selectRange(0,empty.source.length).delete().source)
    }
    @Test fun deletingPowerBaseInsideIntegralLeavesEditableBox() {
        val original=Editor("integrate(x^2,x,,)",11)
        val empty=original.delete()
        assertEquals("integrate(()^2,x,,)",empty.source)
        assertEquals(11,empty.cursor)
        assertEquals("call",empty.tree()?.kind)
        val power=empty.tree()!!.args[0]
        assertEquals("^",power.value)
        assertEquals("hole",power.args[0].args[0].kind)
        assertEquals(empty,empty.delete())
        assertEquals("integrate((3)^2,x,,)",empty.insert("3").source)
        val exponent=empty.move(1)
        assertEquals(empty.source.indexOf('2'),exponent.cursor)
        assertEquals(exponent.cursor..exponent.cursor+1,exponent.exponent)
        assertEquals("call",exponent.insert("3").tree()?.kind)
        assertEquals("()^2",Editor("x^2",0).deleteForward().source)
    }
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

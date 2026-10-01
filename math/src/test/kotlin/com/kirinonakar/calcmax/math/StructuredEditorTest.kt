package com.kirinonakar.calcmax.math
import org.junit.Assert.*
import org.junit.Test
class StructuredEditorTest {
    @Test fun deletingEmptyFunctionTemplatesRemovesTheWholeCall() {
        for(template in listOf("sin()","cos()","tan()","asin()","sinh()","sqrt()","cbrt()","log()","ln()",
            "log(,)","nthroot(,)","mixed(,,)","nPr(,)","det()","inverse()","transpose()","norm()",
            "diff(,x)","integrate(,x,,)","nderivative(,x,)","limit(,x,)","sum(,x,,)","product(,x,,)","mean([])")) {
            val position=template.indexOf('(')+1
            val editor=Editor(template,position)
            assertEquals(template,"",editor.delete().source)
            assertEquals(template,"",editor.deleteForward().source)
            assertEquals(0,editor.delete().cursor)
        }
        assertEquals("1+",Editor("1+sin()",6).delete().source)
        assertEquals("sin()",Editor("sin(cos())",8).delete().source)
        assertEquals("()^2",Editor("sin()^2",4).delete().source)
        assertEquals("2*()*3",Editor("2*sin()*3",6).delete().source)
        assertEquals("log(,2)",Editor("log(,2)",4).delete().source)
        assertEquals("integrate(,y,,)",Editor("integrate(,y,,)",10).delete().source)
        assertEquals("sin()",Editor("sin(2)",5).delete().source)
        assertEquals("",Editor("sin(2)",5).delete().delete().source)
        assertEquals("",Editor("log(,)",5).delete().source)
        assertEquals("",Editor("mean([])",6).delete().source)
    }
    @Test fun parentSelectsVisibleSumAfterEmptyPrefix() {
        for(source in listOf("sqrt()A+B","()^2A+B")) {
            val b=Editor(source,source.length).parent()
            assertEquals("B",source.substring(b.anchor,b.cursor))
            val sum=b.parent()
            assertEquals("A+B",source.substring(sum.anchor,sum.cursor))
            val plus=source.indexOf('+')
            val fromOperator=Editor(source).selectRange(plus,plus+1).parent()
            assertEquals("A+B",source.substring(fromOperator.anchor,fromOperator.cursor))
            val complete=sum.parent()
            assertEquals(source,source.substring(complete.anchor,complete.cursor))
        }

        val filled="sqrt(9)A+B"
        val filledB=Editor(filled,filled.length).parent()
        assertEquals(filled,filled.substring(filledB.parent().anchor,filledB.parent().cursor))
    }
    @Test fun functionArgumentPositionsRequireBalancedParentheses() {
        assertTrue(Editor("integrate(,x,,)",10).inCallArgument())
        assertTrue(Editor("log(,)",4).inCallArgument())
        assertTrue(Editor("log(,)",5).inCallArgument())
        assertFalse(Editor("log(,)",0).inCallArgument())
    }
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
    @Test fun fillingAnEmptyProductSlotDropsTheParentheses() {
        val slot=Editor("5*5*5",3).delete()
        assertEquals("5*()*5",slot.source)
        assertEquals(2..4,slot.emptyProductSlot())
        assertEquals("5*5*5",slot.replaceSlot(2..4,"5").source)
        assertEquals(3,slot.replaceSlot(2..4,"5").cursor)
        assertEquals("5*sin()*5",slot.replaceSlot(2..4,"sin()",4).source)
        assertEquals(2..4,Editor("5*()*5",4).emptyProductSlot())
        assertEquals(2..4,Editor("5×()×5",3).emptyProductSlot())
        assertEquals("5×5×5",Editor("5×()×5",3).replaceSlot(2..4,"5").source)
        assertNull(Editor("2()3",2).emptyProductSlot())
        assertNull(Editor("()^2",1).emptyProductSlot())
        assertNull(Editor("5*(5)*5",4).emptyProductSlot())
        val fraction="integrate((33)/()*3,x,,)"
        assertNull(Editor(fraction,fraction.indexOf(")*3")+1).emptyProductSlot())
    }
    @Test fun deletingOperatorLeavesReusableSlotPreservingMatrices() {
        val source="[[1,0],[0,1]]+[[4,5],[6,7]]"
        val at=source.indexOf('+')
        val slot=Editor(source).selectRange(at,at+1).delete()
        assertEquals("[[1,0],[0,1]]()[[4,5],[6,7]]",slot.source)
        assertEquals(at+1,slot.cursor)
        assertEquals("[[1,0],[0,1]]×[[4,5],[6,7]]",slot.insert("×").source)
        assertEquals("binary",slot.tree()?.kind)
        assertEquals("[[1,0],[0,1]][[4,5],[6,7]]",Editor(slot.source,slot.cursor).delete().source)
        assertEquals("3",Editor("-3").selectRange(0,1).delete().source)
        assertEquals("2()3",Editor("2+3").selectRange(1,2).delete().source)
        assertEquals("2×3",Editor("2()3",2).insert("×").source)
        assertEquals("23",Editor("2+3",2).delete().source)
        assertEquals("23",Editor("2+3",1).deleteForward().source)
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
        assertEquals("integrate(5^2sin(),x,,)",editor.insert("sin()").source)
        val insideExponent=editor.move(-1)
        assertEquals(12..13,insideExponent.cursorTarget())
        assertEquals("integrate(5^(24),x,,)",insideExponent.insert("4").source)
        assertEquals(10..13,insideExponent.move(1).cursorTarget())
    }
    @Test fun deletingIntegralInputThenEmptyTemplateRemovesTheWholeCall() {
        val filled=Editor("integrate(,x,,)",10).insert("2")
        val empty=filled.delete()
        assertEquals("integrate(,x,,)",empty.source)
        assertEquals("",empty.delete().source)
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
    @Test fun unclosedContainersAreMarkedOpen() {
        assertEquals("open",Parser("{1,2",true).parse().value)
        assertEquals("open",Parser("[1,2",true).parse().value)
        assertEquals("",Parser("{1,2}",true).parse().value)
        assertEquals("",Parser("[1,2]",true).parse().value)
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
    @Test fun emptyPowerTemplateLandsInsideHiddenExponentParenthesis() {
        val template=Editor("()^()",1)
        val base=template.insert("2")
        assertEquals("(2)^()",base.source)
        val exponent=base.move(1)
        assertEquals(base.source.indexOf("^(")+2,exponent.cursor)
        assertEquals(5..5,exponent.cursorTarget())
        assertEquals("(2)^(3)",exponent.insert("3").source)
        assertEquals(2,exponent.move(-1).cursor)
        assertEquals(1,template.move(1).move(-1).cursor)
        assertEquals("()^(3)",template.move(1).insert("3").source)
        assertEquals("(2)^(89)",Editor("(2)^(9)",2).move(1).insert("8").source)
        assertEquals("(2)^(89)*4",Editor("(2)^(9)",2).move(1).insert("8").move(1).move(1).insert("4").source)
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

    @Test fun matrixArrowKeysMoveBetweenElementsAndRows() {
        val source="[[1,2],[3,4]]"
        // right from the end of a row wraps onto the first element of the next row
        assertEquals(8,Editor(source,5).moveMatrix(0,1)?.cursor)
        // left from the first element of a row lands at the end of the previous row
        assertEquals(5,Editor(source,8).moveMatrix(0,-1)?.cursor)
        // up and down keep the column
        assertEquals(2,Editor(source,8).moveMatrix(-1,0)?.cursor)
        assertEquals(8,Editor(source,2).moveMatrix(1,0)?.cursor)
        assertEquals(9,Editor(source,3).moveMatrix(1,0)?.cursor)
        // inside an element and outside a matrix the ordinary movement keeps working
        assertNull(Editor(source,2).moveMatrix(0,1))
        assertNull(Editor("1+2",1).moveMatrix(0,1))
        // the empty template the matrix dialog inserts navigates hole by hole
        val template="[[,],[,]]"
        assertEquals(3,Editor(template,2).moveMatrix(0,1)?.cursor)
        assertEquals(6,Editor(template,3).moveMatrix(0,1)?.cursor)
        assertEquals(3,Editor(template,6).moveMatrix(0,-1)?.cursor)
        assertEquals(7,Editor(template,3).moveMatrix(1,0)?.cursor)
    }
}

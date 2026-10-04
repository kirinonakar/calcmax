package com.kirinonakar.symvacas.math
import org.junit.Assert.*
import org.junit.Test
class StructuredEditorTest {
    @Test fun rightClosesAnUnclosedFunctionAndMovesOutside() {
        for(name in listOf("sin","cos","tan","asin","sinh","sqrt","f")) {
            val input=Editor("$name(").insert("9").move(1)
            assertEquals("$name(9)",input.source)
            assertEquals(input.source.length,input.cursor)
            assertEquals("$name(9)+1",input.insert("+1").source)
            assertEquals("$name(9)*2",input.insert("2").source)
            assertEquals(input,input.move(1))
        }
        val inner=Editor("sin(cos(9").move(1)
        assertEquals("sin(cos(9)",inner.source)
        assertEquals("sin(cos(9)+1",inner.insert("+1").source)
        assertEquals("sin(cos(9))",inner.move(1).source)
        assertEquals("1+sin(9)+2",Editor("1+sin(9").move(1).insert("+2").source)
    }
    @Test fun rightKeepsClosedFunctionsAndIncompleteArgumentsIntact() {
        val input=Editor().insert("sin()",4).insert("9").move(1)
        assertEquals("sin(9)",input.source)
        assertEquals(input.source.length,input.cursor)
        assertEquals("sin(9)+1",input.insert("+1").source)
        for(source in listOf("sin(","sin(9+","log(,9"))
            assertEquals(source,Editor(source).move(1).source)
        assertEquals(5,Editor("sin(99",4).move(1).cursor)
        assertEquals("sin(99",Editor("sin(99",4).move(1).source)
    }
    @Test fun equalInputFinishesFormulaCallsAfterRightArrow() {
        val input=Editor("diff(,x)",5).insert("x").move(1).insert("=").insert("a")
        assertEquals("diff(x,x)=a",input.source)
        assertEquals("relation",input.tree()?.kind)
        assertEquals("diff",input.tree()?.args?.get(0)?.value)
        for(source in listOf("diff(x,x)","integrate(x,x)","sin(x)","sin(diff(x,x))","f(x)")) {
            val at=source.indexOf('x')+1
            assertEquals(source+"=a",Editor(source,at).insert("=a").source)
            val typed=Editor(source,at).typedRelation(source.substring(0,at)+"="+source.substring(at),at+1)!!
            assertEquals(source+"=",typed.source)
            assertEquals(typed.source.length,typed.cursor)
        }
        assertEquals("1+diff(x,x)=a+2",Editor("1+diff(x,x)+2",8).insert("=a").source)
    }
    @Test fun solverEquationsAndSelectionsKeepTheirScopes() {
        assertEquals("solve(x=1,x)",Editor("solve(x,x)",7).insert("=1").source)
        val nested="dsolve(diff(y(t),t),y(t),t)"
        val at=nested.indexOf("y(t)")+3
        assertEquals("dsolve(diff(y(t),t)=y(t),y(t),t)",Editor(nested,at).insert("=y(t)").source)
        assertEquals("solve(sin(x)=0,x)",Editor("solve(sin(x),x)",11).insert("=0").source)
        assertEquals("diff(x=0,x)",Editor("diff(x^2,x)").selectRange(6,8).insert("=0").source)
        assertNull(Editor("solve(x,x)",7).typedRelation("solve(x=,x)",8))
        assertNull(Editor("sin(x)",5).typedRelation("sin(x+1)",7))
    }
    @Test fun deletingEmptyPowerAndFractionTemplatesRemovesTheWholeStructure() {
        for(template in listOf("()^2","()^3","()^(-1)","()^()","()/()")) {
            val editor=Editor(template,1)
            assertEquals(template,"",editor.delete().source)
            assertEquals(template,"",editor.deleteForward().source)
            assertEquals(0,editor.delete().cursor)
            val nested="sin($template)"
            assertEquals("sin()",Editor(nested,5).delete().source)
            assertEquals("1+",Editor("1+$template",3).delete().source)
        }
        assertEquals("",Editor("()^()",4).delete().source)
        assertEquals("",Editor("()/()",4).delete().source)
        assertEquals("2*()*3",Editor("2*()^2*3",3).delete().source)
        assertEquals("3",Editor("3^()",3).delete().source)
        assertEquals("3",Editor("(3)/()",5).delete().source)
        assertEquals("(3+4)",Editor("(3+4)/()",7).delete().source)
        val filled=Editor("()^2",1).insert("2")
        assertEquals("(2)^2",filled.source)
        assertEquals("()^2",filled.delete().source)
        assertEquals("",filled.delete().delete().source)
    }
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
        assertEquals("integrate(,x,,)",emptyNumerator.delete().source)
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
    @Test fun groupedExponentEditingKeepsItsGrouping() {
        val editor=Editor("3^(2)+6").selectRange(3,4).move(1)
        assertEquals("3^(2+3)+6",editor.insert("+3").source)
        assertEquals("3^(2)+3+6",editor.move(1).insert("+3").source)
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

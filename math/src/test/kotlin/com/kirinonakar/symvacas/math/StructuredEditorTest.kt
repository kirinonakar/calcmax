package com.kirinonakar.symvacas.math
import org.junit.Assert.*
import org.junit.Test
class StructuredEditorTest {
    @Test fun slashStaysInExponentUntilRightExits() {
        for(editor in listOf(Editor("25^1"),Editor().insert("25").insert("^").insert("1"),Editor("25^(1)",5))) {
            val divided=editor.insert("/").insert("3")
            val tree=divided.tree()!!
            assertEquals("^",tree.value)
            assertEquals("/",tree.args[1].args[0].value)
            val outside=editor.move(1).insert("/").insert("3").tree()!!
            assertEquals("/",outside.value)
            assertEquals("^",outside.args[0].value)
        }
        val direct=Editor("25^1").typedDivision("25^1/",5)!!
        assertEquals("25^(1/())",direct.source)
        assertEquals("25^1/()",Editor("25^1").move(1).typedDivision("25^1/",5)?.source)
        val fraction=Editor("25^1").fractionInput().insert("3").tree()!!
        assertEquals("^",fraction.value)
        assertEquals("/",fraction.args[1].args[0].value)
        val outsideFraction=Editor("25^1").move(1).fractionInput().insert("3").tree()!!
        assertEquals("/",outsideFraction.value)
        assertEquals("^",outsideFraction.args[0].args[0].value)
    }
    @Test fun divisionKeepsMinusInHiddenDenominatorUntilRight() {
        val denominator=Editor().insert("25").insert("/").insert("3")
        assertEquals("25/(3)",denominator.source)
        val inside=denominator.insert("-").insert("1")
        assertEquals("25/(3-1)",inside.source)
        assertEquals("-",inside.tree()!!.args[1].args[0].value)
        assertEquals("25/(3)-1",denominator.move(1).insert("-").insert("1").source)
        assertEquals("25/()",Editor("25").typedDivision("25/",3)?.source)
    }
    @Test fun displayedSymbolsDeleteAsAUnit() {
        for(name in LatexInput.symbolLabels.keys+"oo") {
            val source="sin($name)"
            val end=4+name.length
            for(at in 5..end)assertEquals(name,"sin()",Editor(source,at).delete().source)
            for(at in 4 until end)assertEquals(name,"sin()",Editor(source,at).deleteForward().source)
            for(at in name.indices) {
                val deleted=Editor(name,at).atomicSymbolDeletion(name.removeRange(at,at+1))
                assertEquals(name,"",deleted?.source)
                assertEquals(name,0,deleted?.cursor)
            }
            assertEquals(name,"()^2",Editor("$name^2",name.length).delete().source)
            assertEquals(name,"2*()",Editor("2*$name",2).deleteForward().source)
            assertEquals(name,"${name.dropLast(1)}(x)",Editor("$name(x)",name.length).delete().source)
            assertEquals(name,"${name.dropLast(1)}_value",Editor("${name}_value",name.length).delete().source)
        }
        for(name in listOf("thet","mytheta","Ans","sin","log","theta2")) {
            assertEquals(name.dropLast(1),Editor(name).delete().source)
            assertNull(Editor(name).atomicSymbolDeletion(name.dropLast(1)))
        }
        assertEquals("tta",Editor("theta").selectRange(1,3).delete().source)
        assertNull(Editor("theta").selectRange(1,2).atomicSymbolDeletion("teta"))
        assertEquals("1+*",Editor("1+*theta").delete().source)
    }
    @Test fun arrowsCrossFunctionHeadsAndSymbolsInOneStep() {
        val source="sin(60)+cos(60)"
        var editor=Editor(source)
        for(expected in listOf(14,13,12,8,7,6,5,4,0)) {
            editor=editor.move(-1)
            assertEquals(expected,editor.cursor)
            assertEquals(source,editor.source)
        }
        for(name in listOf("sin","cos","tan","asin","sinh","log","ln","exp","sqrt","cbrt","nthroot","integrate","f","my_func")) {
            for(input in listOf("1+$name(60)","1+$name()","1+$name(","1+$name (60)","1+*$name(60)")) {
                val begin=input.indexOf(name)
                val inside=input.indexOf('(')+1
                assertEquals(input,begin,Editor(input,inside).move(-1).cursor)
                assertEquals(input,inside,Editor(input,begin).move(1).cursor)
            }
        }
        for(symbol in listOf("pi","theta","Ans","oo","hbar","epsilon0","my_value","π","∞","<=","!=","->")) {
            val input="1+$symbol+2"
            val end=2+symbol.length
            assertEquals(input,2,Editor(input,end).move(-1).cursor)
            assertEquals(input,end,Editor(input,2).move(1).cursor)
            if(symbol.length>1) {
                assertEquals(input,2,Editor(input,3).move(-1).cursor)
                assertEquals(input,end,Editor(input,3).move(1).cursor)
            }
        }
        assertEquals(4,Editor("sin(cos(60))",8).move(-1).cursor)
        assertEquals(0,Editor("sin(cos(60))",4).move(-1).cursor)
        assertEquals(2,Editor("123.45",3).move(-1).cursor)
        assertEquals(4,Editor("123.45",3).move(1).cursor)
        assertEquals(1,Editor("theta").selectRange(1,4).move(-1).cursor)
        assertEquals(4,Editor("theta").selectRange(1,4).move(1).cursor)
    }
    @Test fun functionNavigationClosesCompleteArgumentsAndPreservesIncompleteInput() {
        run { // rightClosesAnUnclosedFunctionAndMovesOutside
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
        run { // rightKeepsClosedFunctionsAndIncompleteArgumentsIntact
            val input=Editor().insert("sin()",4).insert("9").move(1)
            assertEquals("sin(9)",input.source)
            assertEquals(input.source.length,input.cursor)
            assertEquals("sin(9)+1",input.insert("+1").source)
            for(source in listOf("sin(","sin(9+","log(,9"))
                assertEquals(source,Editor(source).move(1).source)
            assertEquals(5,Editor("sin(99",4).move(1).cursor)
            assertEquals("sin(99",Editor("sin(99",4).move(1).source)
        }
    }

    @Test fun relationEditingPreservesFunctionAndSolverScopes() {
        run { // equalInputFinishesFormulaCallsAfterRightArrow
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
        run { // solverEquationsAndSelectionsKeepTheirScopes
            assertEquals("solve(x=1,x)",Editor("solve(x,x)",7).insert("=1").source)
            val nested="dsolve(diff(y(t),t),y(t),t)"
            val at=nested.indexOf("y(t)")+3
            assertEquals("dsolve(diff(y(t),t)=y(t),y(t),t)",Editor(nested,at).insert("=y(t)").source)
            assertEquals("solve(sin(x)=0,x)",Editor("solve(sin(x),x)",11).insert("=0").source)
            assertEquals("diff(x=0,x)",Editor("diff(x^2,x)").selectRange(6,8).insert("=0").source)
            assertNull(Editor("solve(x,x)",7).typedRelation("solve(x=,x)",8))
            assertNull(Editor("sin(x)",5).typedRelation("sin(x+1)",7))
        }
    }

    @Test fun emptyTemplatesDeleteAtomicallyWithoutRemovingFilledArguments() {
        run { // deletingEmptyPowerAndFractionTemplatesRemovesTheWholeStructure
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
        run { // deletingEmptyFunctionTemplatesRemovesTheWholeCall
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
        run { // deletingIntegralInputThenEmptyTemplateRemovesTheWholeCall
            val filled=Editor("integrate(,x,,)",10).insert("2")
            val empty=filled.delete()
            assertEquals("integrate(,x,,)",empty.source)
            assertEquals("",empty.delete().source)
            assertEquals("call",empty.tree()?.kind)
            assertEquals("integrate",empty.tree()?.value)
            assertEquals(empty.source,Editor(empty.source,9).deleteForward().source)
            assertEquals("",empty.selectRange(0,empty.source.length).delete().source)
        }
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
        run { // deletingMiddleProductTermKeepsABoxThenRemovesItsOperator
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
        run { // deletingOperatorLeavesReusableSlotPreservingMatrices
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
    }

    @Test fun fractionHiddenSlotsNavigateAndEmptyDenominatorDeletesFractionTail() {
        run { // fractionHiddenSlotsNavigateAndEmptyDenominatorDeletesFractionTail
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
        run { // deleteAfterHiddenDenominatorCloseKeepsFractionStructure
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
    }

    @Test fun groupedExponentEditingKeepsItsGrouping() {
        val editor=Editor("3^(2)+6").selectRange(3,4).move(1)
        assertEquals("3^(2+3)+6",editor.insert("+3").source)
        assertEquals("3^(2)+3+6",editor.move(1).insert("+3").source)
    }
    @Test fun consecutiveExponentDigitsStayInsideFunctionArguments() {
        for(template in listOf("integrate(,x,,)","integrate(,x)","diff(,x)","sin()")) {
            val start=template.indexOf('(')+1
            for(power in listOf("^","^()")) {
                val editor=Editor(template,start).insert("x").insert(power,if(power=="^()")2 else 1)
                    .insert("4").insert("4")
                val expected=template.substring(0,start)+"x^(44)"+template.substring(start)
                assertEquals("$template $power",expected,editor.source)
                val exponent=editor.tree()!!.args[0].args[1].args[0]
                assertEquals("44",exponent.value)
                assertEquals(exponent.start..exponent.end,editor.cursorTarget())
                assertEquals(expected.replace("44","444"),editor.insert("4").source)
                assertEquals(expected.replace("44","4"),editor.delete().source)
                val outside=editor.move(1)
                assertEquals(expected.replace("x^(44)","x^(44)*4"),outside.insert("4").source)
                assertEquals(expected.replace("44","445"),outside.move(-1).insert("5").source)
            }
        }
        // A completed square remains a complete operand; the next digit is a factor.
        assertEquals("integrate(x^2*4,x,,)",Editor("integrate(,x,,)",10).insert("x").insert("^2").insert("4").source)
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
    @Test fun matrixEdgesExitAndReenterWithoutExposingRowBrackets() {
        for(matrix in listOf("[[3,5],[3,6]]","[[33,55],[33,66]]","[[,],[,]]","[[1]]","[[1,2,3]]","[[1],[2],[3]]")) {
            for(source in listOf(matrix,"1+$matrix+2","det($matrix)")) {
                val node=Editor(source).tree()!!.nodes().first {it.kind=="list"&&it.args.firstOrNull()?.kind=="list"}
                val last=node.args.last().args.last()
                val inside=Editor(source,last.end)
                val outside=inside.moveMatrix(0,1)!!
                assertEquals(source,node.end,outside.cursor)
                assertEquals(source,node.end,inside.move(1).cursor)
                assertEquals(source,last.end,outside.moveMatrix(0,-1)?.cursor)
                assertEquals(source,last.end,outside.move(-1).cursor)
                assertEquals(source,source,outside.source)
                val edited=outside.move(-1).insert("7")
                assertEquals(source,source.substring(0,last.end)+"7"+source.substring(last.end),edited.source)
            }
        }
        val filled=Editor("[[,],[,]]",2).insert("3").move(1).insert("5").move(1).insert("3").move(1).insert("6")
        assertEquals("[[3,5],[3,6]]",filled.source)
        assertEquals("[[3,5],[3,6]]*3",filled.move(1).insert("3").source)
        assertEquals("[[3,5],[3,67]]",filled.move(1).move(-1).insert("7").source)
    }
    @Test fun factorsAfterMatricesUseExplicitMultiplicationForKeypadAndTypedInput() {
        val matrix="[[3,5],[3,6]]"
        for(source in listOf(matrix,"1+$matrix+2","det($matrix)")) {
            val end=source.indexOf(matrix)+matrix.length
            for(position in listOf(end-1,end)) {
                val editor=Editor(source,position)
                for(text in listOf("3",".5","x","theta","sin()","(3+4)","[[1,0],[0,1]]","√4","∞")) {
                    val result=editor.insert(text)
                    val expected=source.substring(0,end)+"*"+text+source.substring(end)
                    assertEquals(text,expected,result.source)
                    assertEquals(text,end+1+text.length,result.cursor)
                    assertNotNull(text,result.tree())
                    assertEquals(text,expected,editor.insertOperand(text).source)
                    val updated=source.substring(0,position)+text+source.substring(position)
                    assertEquals(text,expected,editor.typedMatrixFactor(updated,position+text.length)?.source)
                }
            }
            for(operator in listOf("+","-","*","/","^",",", ")", "]"))
                assertEquals(operator,source.substring(0,end)+(if(operator=="/")"/()" else operator)+source.substring(end),Editor(source,end).insert(operator).source)
        }
        assertEquals(matrix+"*sin(3)","sin(3)".fold(Editor(matrix)){editor,char->editor.insert(char.toString())}.source)
        assertEquals(matrix+"*34",Editor(matrix).insert("3").insert("4").source)
        assertEquals("[[3,5],[3,67]]",Editor(matrix,matrix.length-2).insert("7").source)
        assertEquals(matrix+"*sin()",Editor(matrix).insert("sin()",4).source)
        assertEquals(matrix.length+5,Editor(matrix).insert("sin()",4).cursor)
        assertNull(Editor(matrix).selectRange(0,matrix.length).typedMatrixFactor("3",1))
        assertEquals(matrix+"=3",Editor(matrix).insert("=3").source)
        assertEquals("det($matrix)=3",Editor("det($matrix)",matrix.length+4).insert("=3").source)
        assertNull(Editor("[[3,5],[3,6]").typedMatrixFactor("[[3,5],[3,6]7",12))
    }
}

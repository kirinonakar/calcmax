package com.kirinonakar.symvacas.ui

import com.kirinonakar.symvacas.math.Parser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EquationFormulaTextTest {
    private fun node(kind:String,value:String="",vararg args:JSONObject)=JSONObject().put("kind",kind).put("value",value).put("args",JSONArray(args.toList()))
    @Test fun copiedSubstitutionKeepsTheUnsimplifiedParenthesizedExpression() {
        val x=node("symbol","x")
        val replacement=node("parentheses","",node("sum","",node("number","1"),node("unary","-",x)))
        val formula=node("relation","=",node("sum","",x,node("unary","-",replacement)),node("number","2"))
        val text=equationFormulaText(formula)
        assertEquals("x-(1-x) = 2",text)
        val parsed=Parser(text).parse()
        assertEquals("relation",parsed.kind)
        assertEquals("-",parsed.args[0].value)
        assertEquals("group",parsed.args[0].args[1].kind)
    }
    @Test fun copiedFractionalExponentAndNestedDenominatorKeepTheirPrecedence() {
        val x=node("symbol","x");val one=node("number","1");val two=node("number","2")
        val fraction=node("fraction","",one,two)
        val power=Parser(equationFormulaText(node("power","",x,fraction))).parse()
        assertEquals("^",power.value)
        assertEquals("group",power.args[1].kind)
        assertEquals("/",power.args[1].args[0].value)
        val nested=Parser(equationFormulaText(node("fraction","",x,fraction))).parse()
        assertEquals("group",nested.args[1].kind)
        assertEquals("/",nested.args[1].args[0].value)
    }
    @Test fun fullReportCopyIncludesLocalizedTextDisplayedSubstitutionAndAdvancedMatrices() {
        val x=node("symbol","x")
        val tree=node("relation","==",node("sum","",x,node("unary","-",node("parentheses","",node("sum","",node("number","1"),node("unary","-",x))))),node("number","2"))
        val step=JSONObject().put("title","Substitute into the second equation")
            .put("explanationParts",JSONArray().put(JSONObject().put("text","Subtract {term} from both sides.").put("values",JSONObject().put("term","x"))))
            .put("equations",JSONArray().put(JSONObject().put("tree",tree).put("exact","Eq(2*x - 1, 2)")))
        val final=JSONObject().put("title","Computed result").put("tree",node("relation","=",x,node("number","1.23456")))
        val advanced=JSONObject().put("title","Augmented matrix")
            .put("variableOrder",JSONObject().put("tree",node("list","",x,node("symbol","y"))))
            .put("operation",node("relation","←",node("symbol","R2"),node("symbol","R1")))
            .put("equations",JSONArray().put(JSONObject().put("tree",node("matrix","",node("list","",node("number","1"),node("number","0"),node("number","2"))))))
        val report=JSONObject().put("method","Substitution method").put("steps",JSONArray().put(step).put(final)).put("advancedSteps",JSONArray().put(advanced))
        val text=solutionStepsCopyText(report,3){translateLabel(it,"ko")}
        assertTrue(text.startsWith(translateLabel("Step-by-step solution","ko")))
        assertTrue(text.contains("양변에서 x를 뺍니다."))
        assertTrue(text.contains("x-(1-x) = 2"))
        assertTrue(text.contains("x = 1.235"))
        assertTrue(text.contains("R2 ← R1\n[[1,0,2]]"))
        assertTrue(text.contains("[x,y]"))
        assertFalse(text.contains("Eq(2*x"))
    }
}

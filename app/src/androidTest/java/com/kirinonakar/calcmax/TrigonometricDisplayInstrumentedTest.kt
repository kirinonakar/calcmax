package com.kirinonakar.calcmax

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kirinonakar.calcmax.math.LatexInput
import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.ui.MathNode
import com.kirinonakar.calcmax.ui.theme.CalcmaxTheme
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrigonometricDisplayInstrumentedTest {
    @get:Rule val compose=createComposeRule()

    @Test fun squareIsRaisedAfterTheFunctionNameAndBeforeItsArgument() {
        compose.setContent {CalcmaxTheme {MathNode(JSONObject(Parser("sin(theta)^2").parse().json()))}}
        val name=compose.onNodeWithText("sin",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val exponent=compose.onNodeWithText("2",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val argument=compose.onNodeWithText("θ",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(exponent.left>=name.right)
        assertTrue(exponent.top<name.top)
        assertTrue(argument.left>=exponent.right)
    }

    @Test fun suppliedLatexFractionDisplaysBothThetaSymbolsAndTheCosineSquare() {
        val source=LatexInput.convert("$$\\frac{\\sin\\theta}{1-\\cos^2\\theta}$$")!!
        compose.setContent {CalcmaxTheme {MathNode(JSONObject(Parser(source).parse().json()))}}
        compose.onAllNodesWithText("θ",useUnmergedTree=true).assertCountEquals(2)
        val sine=compose.onNodeWithText("sin",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val cosine=compose.onNodeWithText("cos",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val exponent=compose.onNodeWithText("2",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(sine.bottom<cosine.top)
        assertTrue(exponent.left>=cosine.right)
    }
}

package com.kirinonakar.calcmax

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kirinonakar.calcmax.math.LatexInput
import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.ui.MathNode
import com.kirinonakar.calcmax.ui.theme.CalcmaxTheme
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MathParenthesesInstrumentedTest {
    @get:Rule val compose=createComposeRule()

    @Test fun parenthesesEncloseFractionsAndNestedExpressionsAtTheirActualHeight() {
        val integral=LatexInput.convert("$$\\int_{0}^{1} \\left( \\frac{x}{x} \\right) dx$$")!!
        val cases=listOf("integral" to integral,"function" to "sin(x/2)","nested" to "(1/(x/2))","ordinary" to "(x)+y")
        compose.setContent {CalcmaxTheme("Light") {
            Column(Modifier.testTag("parentheses-preview").background(LocalInstrument.current.display).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                cases.forEach {(name,source)->Box(Modifier.testTag(name)){MathNode(JSONObject(Parser(source).parse().json()))}}
            }
        }}
        val ordinary=compose.onNode(hasTestTag("math-opening-parenthesis") and hasAnyAncestor(hasTestTag("ordinary")),useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val ordinaryX=compose.onNode(hasText("x") and hasAnyAncestor(hasTestTag("ordinary")),useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals(ordinaryX.height,ordinary.height,1f)
        for(name in listOf("integral","function","nested")) {
            val ancestor=hasAnyAncestor(hasTestTag(name))
            val openings=compose.onAllNodes(hasTestTag("math-opening-parenthesis") and ancestor,useUnmergedTree=true).fetchSemanticsNodes()
            val closings=compose.onAllNodes(hasTestTag("math-closing-parenthesis") and ancestor,useUnmergedTree=true).fetchSemanticsNodes()
            val terms=compose.onAllNodes(hasText("x") and ancestor,useUnmergedTree=true).fetchSemanticsNodes().map {it.boundsInRoot}
            val contents=compose.onNode(hasTestTag("math-fenced-content") and ancestor,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertEquals("$name must have exactly one opening parenthesis",1,openings.size)
            assertEquals(openings.size,closings.size)
            for(fence in openings+closings) {
                val bounds=fence.boundsInRoot
                assertTrue("$name must stretch above the numerator",bounds.top<terms.minOf {it.top})
                assertTrue("$name must stretch below the denominator",bounds.bottom>terms.maxOf {it.bottom})
                assertTrue("$name must be taller than an ordinary parenthesis",bounds.height>ordinary.height*1.5f)
                assertTrue("$name must fit the expression height",bounds.height<contents.height*1.08f)
                assertTrue("$name must not add excessive space above the expression",contents.top-bounds.top<contents.height*.04f)
                assertTrue("$name must not add excessive space below the expression",bounds.bottom-contents.bottom<contents.height*.04f)
            }
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.filesDir,"qa/stretchy-parentheses.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use {compose.onNodeWithTag("parentheses-preview").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}

package com.kirinonakar.symvacas

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.ui.PythonScreen
import com.kirinonakar.symvacas.ui.ScientificWorkspace
import com.kirinonakar.symvacas.ui.theme.SymvaCASTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Controlled IME sizes verify layout geometry without relying on an installed keyboard. */
@RunWith(AndroidJUnit4::class)
class KeyboardLayoutInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private val store=ViewModelStore()
    private fun model():CalculatorModel {
        val application=ApplicationProvider.getApplicationContext<Application>()
        return ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory(application))[CalculatorModel::class.java]
    }
    @After fun clearModel() {compose.runOnIdle {store.clear()}}

    @Test fun keyboardOverlaysKeypadAndMovesExpandedTapeAboveItsTop() {
        var expanded by mutableStateOf(false)
        var imeFraction by mutableFloatStateOf(0f)
        var keyboardTop=0f
        compose.setContent {
            SymvaCASTheme("Light") {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density=LocalDensity.current
                    val bars=WindowInsets.systemBars.union(WindowInsets.displayCutout).getBottom(density)
                    val overlap=maxHeight*imeFraction
                    SideEffect {keyboardTop=with(density){(maxHeight-overlap).toPx()}}
                    ScientificWorkspace(model(),expanded,{expanded=!expanded},{},
                        imeInsets=WindowInsets(bottom=with(density){overlap.toPx().toInt()}+bars))
                }
            }
        }
        for(scr in listOf(false,true)) {
            compose.runOnIdle {expanded=scr;imeFraction=0f}
            val before=compose.onNodeWithContentDescription("Calculator keypad").fetchSemanticsNode().boundsInRoot
            for(fraction in listOf(.3f,.6f,0f)) {
                compose.runOnIdle {imeFraction=fraction}
                val after=compose.onNodeWithContentDescription("Calculator keypad").fetchSemanticsNode().boundsInRoot
                assertEquals(before.top,after.top,.5f)
                assertEquals(before.bottom,after.bottom,.5f)
                val actions=compose.onNodeWithContentDescription(if(scr)"Restore full keypad" else "Expand calculation screen").fetchSemanticsNode().boundsInRoot
                assertTrue("Display actions must stay above the keyboard",actions.bottom<=keyboardTop+1f)
            }
        }
    }

    @Test fun pythonCompletionsRemainVisibleWhenViewportShrinksAndSuggestionsAppear() {
        var viewportHeight by mutableStateOf(500.dp)
        var imeHeight by mutableStateOf(0.dp)
        compose.setContent {
            SymvaCASTheme("Light") {
                // Prevent the real keyboard from changing the controlled viewport.
                InterceptPlatformTextInput(interceptor={_,_->awaitCancellation()}) {
                    Box(Modifier.fillMaxWidth().height(viewportHeight).testTag("Python viewport")) {
                        PythonScreen(model(),WindowInsets(bottom=imeHeight))
                    }
                }
            }
        }
        compose.runOnIdle {model().language="en";model().editPython("p")}
        compose.onNodeWithText("Python code").performClick()
        compose.runOnIdle {viewportHeight=240.dp;imeHeight=300.dp}
        compose.onNodeWithText("Python code").performTextInput("r")
        compose.onNodeWithContentDescription("Python completions").assertIsDisplayed()
        val viewport=compose.onNodeWithTag("Python viewport").fetchSemanticsNode().boundsInRoot
        val completions=compose.onNodeWithContentDescription("Python completions").fetchSemanticsNode().boundsInRoot
        assertTrue(completions.top>=viewport.top)
        assertTrue(completions.bottom<=viewport.bottom)
        compose.onNodeWithText("print",useUnmergedTree=true).performClick()
        compose.runOnIdle {assertEquals("print",model().pythonSource)}
    }
}

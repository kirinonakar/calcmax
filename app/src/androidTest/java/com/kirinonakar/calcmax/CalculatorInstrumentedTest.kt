package com.kirinonakar.calcmax

import android.graphics.Bitmap
import android.app.UiModeManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kirinonakar.calcmax.calculator.*
import com.kirinonakar.calcmax.math.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CalculatorInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(compose.activity)[CalculatorModel::class.java]
    private fun capture(name: String) {
        val file=File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use { val roots=compose.onAllNodes(isRoot());roots[roots.fetchSemanticsNodes().lastIndex].captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun undoRevertsEachInputAndClear() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().poweredOn=true;model().secondKeys=false;model().clear(recordUndo=false)}
        listOf("1","+","2").forEach {compose.onNodeWithContentDescription(it).performClick()}
        compose.runOnIdle {assertEquals("1+2",model().editor.source)}
        compose.onNodeWithContentDescription("Undo last input").performClick()
        compose.runOnIdle {assertEquals("1+",model().editor.source)}
        compose.onNodeWithContentDescription("Undo last input").performClick()
        compose.runOnIdle {assertEquals("1",model().editor.source)}
        compose.onNodeWithContentDescription("Undo last input").performClick()
        compose.runOnIdle {assertEquals("",model().editor.source);assertFalse(model().canUndo)}
        compose.runOnIdle {model().insert("123");model().edit(model().editor.delete());assertEquals("12",model().editor.source)}
        compose.onNodeWithContentDescription("Undo last input").performClick()
        compose.runOnIdle {assertEquals("123",model().editor.source);model().clear()}
        compose.onNodeWithContentDescription("Undo last input").performClick()
        compose.runOnIdle {assertEquals("123",model().editor.source);model().clear(recordUndo=false);model().edit(Editor("2+3"));model().calculate()}
        compose.waitUntil(30000){model().committed}
        compose.runOnIdle {model().fresh(Editor("-"));assertEquals("-",model().editor.source)}
        compose.onNodeWithContentDescription("Undo last input").performClick()
        compose.runOnIdle {assertEquals("2+3",model().editor.source);model().clear(recordUndo=false);model().edit(Editor("A+1"));model().startCalc();model().insertCalcValue("42")}
        compose.onNodeWithContentDescription("Undo last input").performClick()
        compose.runOnIdle {assertEquals("",model().calcSession?.input?.source);model().cancelCalc();model().clear(recordUndo=false)}
    }
    @Test fun integralPowerBaseMovesDirectlyToExponent() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().secondKeys=false;model().clear();model().edit(Editor("integrate(x^2,x,,)",11))}
        compose.onNodeWithContentDescription("DEL").performClick()
        compose.runOnIdle {assertEquals("integrate(()^2,x,,)",model().editor.source)}
        compose.onNodeWithContentDescription("Cursor right").performClick()
        compose.runOnIdle {assertEquals(model().editor.source.indexOf('2'),model().editor.cursor)}
        compose.onNodeWithContentDescription("3").performClick()
        compose.runOnIdle {assertEquals("call",model().editor.tree()?.kind);assertEquals("integrate",model().editor.tree()?.value);model().clear()}
    }
    @Test fun calcPromptsForVariablesAndReusesStoredValues() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().clear();model().edit(Editor("A+2B"))}
        compose.onNodeWithContentDescription("CALC").performClick()
        compose.runOnIdle {assertEquals("A",model().calcSession?.name)}
        compose.onNodeWithContentDescription("3").performClick()
        compose.runOnIdle {assertEquals("3",model().calcSession?.input?.source)}
        compose.onNodeWithContentDescription("=").performClick()
        compose.waitUntil(30000){!model().busy&&model().calcSession?.name=="B"}
        compose.runOnIdle {assertEquals("3",model().calcSession?.accepted?.get("A")?.optString("value"))}
        compose.onNodeWithContentDescription("4").performClick()
        compose.onNodeWithContentDescription("=").performClick()
        compose.waitUntil(30000){!model().busy&&model().committed}
        compose.runOnIdle {assertEquals("11",model().result?.optString("exact"));assertEquals("3",model().lastCalcValues["A"]?.optString("value"));assertEquals("4",model().lastCalcValues["B"]?.optString("value"));assertTrue(model().variables.has("A"));assertTrue(model().variables.has("B"))}
        compose.onNodeWithContentDescription("CALC").performClick()
        compose.onNodeWithContentDescription("=").performClick()
        compose.waitUntil(30000){!model().busy&&model().calcSession?.name=="B"}
        compose.onNodeWithContentDescription("=").performClick()
        compose.waitUntil(30000){!model().busy&&model().committed}
        compose.runOnIdle {assertEquals("11",model().result?.optString("exact"));model().clear()}
    }
    @Test fun tappingEmptyStatisticListPreservesItsBrackets() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().clear();model().insert("mean([])",6)}
        compose.onNodeWithContentDescription("Empty list; tap to enter values").performClick()
        compose.runOnIdle {model().insert("1,2,3");assertEquals("mean([1,2,3])",model().editor.source)}
    }
    @Test fun statisticsCellsFocusWhenTappedNearTheirEdges() {
        compose.runOnIdle {model().mode="Statistics"}
        compose.onNodeWithText("New").performClick()
        for(tap in 0 until 16) {
            val index=tap%2
            val cell=compose.onNodeWithTag("statistics-cell-$index-0")
            cell.performScrollTo()
            cell.performTouchInput {click(androidx.compose.ui.geometry.Offset(
                if(tap<8)4f else width-4f,
                if(tap%4<2)4f else height-4f
            ))}
            cell.assertIsFocused()
        }
        compose.onNodeWithTag("statistics-cell-1-0").performTextInput("9")
        compose.onNodeWithTag("statistics-cell-1-0").assertTextContains("9",substring=true)
        compose.onNodeWithText("x,y data").performClick()
        val yCell=compose.onNodeWithTag("statistics-cell-0-1")
        yCell.performScrollTo()
        yCell.performTouchInput {click(androidx.compose.ui.geometry.Offset(4f,height/2f))}
        yCell.assertIsFocused()
        yCell.performTextInput("5")
        yCell.assertTextContains("5",substring=true)
    }
    @Test fun statisticsCellFocusSurvivesFingerRollPastTouchSlop() {
        compose.runOnIdle {model().mode="Statistics"}
        compose.onNodeWithText("New").performClick()
        val slop=android.view.ViewConfiguration.get(compose.activity).scaledTouchSlop
        val roll=slop*1.25f
        val cell=compose.onNodeWithTag("statistics-cell-1-0").performScrollTo()
        // Physical fingers roll slightly while tapping and the table scroll already claims that movement,
        // which used to cancel the tap and leave the cell unfocused on real devices.
        cell.performTouchInput {swipeUp(startY=height/2f+roll/2f,endY=height/2f-roll/2f,durationMillis=40)}
        cell.assertIsFocused()
        cell.performTextInput("7")
        cell.assertTextContains("7",substring=true)
    }
    @Test fun statisticsCellTapImmediatelyAfterFlingFocuses() {
        compose.runOnIdle {
            model().saveDataSet("AaTouchRows",(1..60).joinToString("\n"),"list")
            model().mode="Statistics"
        }
        compose.onAllNodesWithText("AaTouchRows").onFirst().performScrollTo().performClick()
        val table=compose.onNodeWithTag("statistics-table").performScrollTo()
        table.performTouchInput {
            swipeUp(startY=height*.8f,endY=height*.2f,durationMillis=80)
            click(androidx.compose.ui.geometry.Offset(width/2f,height/2f))
        }
        assertTrue(compose.onAllNodes(isFocused()).fetchSemanticsNodes().any {
            runCatching {it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].startsWith("statistics-cell-")}.getOrDefault(false)
        })
        compose.runOnIdle {model().deleteDataSet("AaTouchRows")}
    }
    @Test fun divisionAndAdditionPlaceCaretWithoutAnEmptyBox() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().clear();model().edit(Editor("49"))}
        compose.onNodeWithContentDescription("÷").performClick()
        compose.runOnIdle {assertEquals("49÷",model().editor.source);assertEquals(3..3,model().editor.cursorTarget())}
        compose.onNodeWithContentDescription("Empty expression slot").assertDoesNotExist()
        compose.onNodeWithText("│",useUnmergedTree=true).assertExists()
        compose.onNodeWithContentDescription("2").performClick()
        compose.runOnIdle {assertEquals("49÷2",model().editor.source);assertEquals(3..4,model().editor.cursorTarget())}
        compose.onNode(hasText(" ÷ ") and hasAnyAncestor(hasContentDescription("Current expression")),useUnmergedTree=true).assertExists()
        compose.onNodeWithContentDescription("After fraction").assertDoesNotExist()
        compose.runOnIdle {model().clear();model().edit(Editor("49"))}
        compose.onNodeWithContentDescription("+").performClick()
        compose.runOnIdle {assertEquals("49+",model().editor.source);assertEquals(3..3,model().editor.cursorTarget())}
        compose.onNodeWithContentDescription("Empty expression slot").assertDoesNotExist()
        compose.onNodeWithText("│",useUnmergedTree=true).assertExists()
    }
    @Test fun pythonWorkspaceRunsSavedSourceThroughService() {
        compose.runOnIdle {
            model().mode="Python"
            model().newPythonFile()
            model().editPython("import math\nprint(math.sqrt(81))")
            model().runPython()
        }
        compose.waitUntil(30000) {model().pythonOutput.contains("9.0") || model().pythonError.isNotBlank()}
        compose.runOnIdle {
            assertEquals("",model().pythonError)
            assertEquals("9.0\n",model().pythonOutput)
            assertEquals("import math\nprint(math.sqrt(81))",model().pythonSource)
        }
    }
    @Test fun pythonWorkspaceAcceptsInputThroughService() {
        compose.runOnIdle {
            model().mode="Python"
            model().newPythonFile()
            model().editPython("a=float(input(\"a=\"))\nprint(a*2)")
            model().runPython()
        }
        compose.waitUntil(30000) {model().pythonInputPrompt == "a=" || model().pythonError.isNotBlank()}
        compose.runOnIdle {assertEquals("",model().pythonError);model().submitPythonInput("2.5")}
        compose.waitUntil(30000) {model().pythonHasRun || model().pythonError.isNotBlank()}
        compose.runOnIdle {
            assertEquals("",model().pythonError)
            assertEquals("a=2.5\n5.0\n",model().pythonOutput)
        }
    }
    @Test fun pythonCatalogInsertsIntoCodeAtCursor() {
        compose.runOnIdle {model().mode="Python";model().newPythonFile();model().editPython("print()",6,6)}
        compose.onNodeWithText("Catalog").performClick()
        compose.onNodeWithText("abs()").performClick()
        compose.runOnIdle {
            assertEquals("import calcmax_catalog as calc\nfrom calcmax_catalog import x, y, z, t, pi\nprint(calc.abs())",model().pythonSource)
            assertTrue(model().pythonSource.substring(0,model().pythonSelectionStart).endsWith("print(calc.abs("))
        }
    }
    @Test fun secondPageInsertsStructuresAndGraphsCurrentExpression() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().clear();model().secondKeys=true;model().edit(Editor("y=x^2+1"))}
        compose.onNodeWithContentDescription("Graph current expression").performClick()
        compose.runOnIdle {assertEquals("Graph",model().mode);assertEquals("cartesian",model().graphKind);assertEquals("x^2+1",model().graphSource)}
        compose.runOnIdle {model().mode="Scientific/CAS";model().clear();model().secondKeys=true}
        compose.onNodeWithContentDescription("Insert matrix, choose size").performClick()
        compose.onNodeWithText("Insert").performClick()
        compose.runOnIdle {assertEquals("[[,],[,]]",model().editor.source);assertEquals(2,model().editor.cursor)}
        compose.runOnIdle {model().clear();model().secondKeys=true}
        compose.onNodeWithContentDescription("SHIFT").performClick()
        compose.onNodeWithContentDescription("Insert matrix, choose size").performClick()
        compose.onNodeWithContentDescription("Increase Rows").performClick()
        compose.onNodeWithText("Insert").performClick()
        compose.runOnIdle {assertEquals("[[,],[,],[,]]",model().editor.source);assertEquals(2,model().editor.cursor)}
        compose.runOnIdle {model().clear();model().secondKeys=true}
        listOf("{","x",",","y","}").forEach {key->compose.onNodeWithContentDescription(key).performClick()}
        compose.runOnIdle {assertEquals("{x,y}",model().editor.source);assertEquals("set",model().editor.tree()?.kind)}
        compose.runOnIdle {model().clear();model().secondKeys=true}
        compose.onNodeWithContentDescription("SHIFT").performClick()
        compose.onNodeWithContentDescription("[").performClick()
        compose.onNodeWithContentDescription("SHIFT").performClick()
        compose.onNodeWithContentDescription("]").performClick()
        compose.runOnIdle {assertEquals("[]",model().editor.source)}
    }
    @Test fun tokenCursorMalformedInputAndClearAll() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().clear();model().edit(Editor("1234",4,0))}
        val number=compose.onNode(hasText("1234") and hasAnyAncestor(hasContentDescription("Current expression")),useUnmergedTree=true)
        val widthWithoutCursor=number.fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle {model().edit(Editor("1234",2))}
        assertEquals(widthWithoutCursor,number.fetchSemanticsNode().boundsInRoot.width,0.1f)
        compose.runOnIdle {model().mode="Scientific/CAS";model().clear();model().edit(Editor("1234"))}
        val token=compose.onNode(hasText("1234") and hasAnyAncestor(hasContentDescription("Current expression")),useUnmergedTree=true)
        token.performTouchInput{click(center)}
        compose.runOnIdle{assertEquals(0,model().editor.anchor);assertEquals(4,model().editor.cursor)}
        compose.onNode(hasText("1234") and hasAnyAncestor(hasContentDescription("Current expression")),useUnmergedTree=true).performTouchInput{click(androidx.compose.ui.geometry.Offset(width*.3f,height/2f))}
        compose.runOnIdle{assertEquals(model().editor.anchor,model().editor.cursor);assertTrue(model().editor.cursor in 1..2)}
        compose.onNode(hasText("1234") and hasAnyAncestor(hasContentDescription("Current expression")),useUnmergedTree=true).performTouchInput{click(androidx.compose.ui.geometry.Offset(width*.85f,height/2f))}
        compose.runOnIdle{assertTrue(model().editor.cursor>=3);model().edit(Editor("123-434+545)",0))}
        compose.onNode(hasText("123",substring=true) and hasAnyAncestor(hasContentDescription("Current expression")),useUnmergedTree=true).assertExists()
        compose.runOnIdle{model().insert("(");assertNotNull(model().editor.tree());model().store("A","42")}
        compose.waitUntil(30000){!model().busy&&model().variables.has("A")}
        compose.onNodeWithContentDescription("RCL").performClick()
        compose.onNodeWithText("A = ").assertExists()
        capture("recall-values")
        compose.onNodeWithText("Close").performClick()
        var count=0
        compose.runOnIdle{count=model().history.size;model().precision=10;model().displayDigits=8;model().inputFont=27f;model().haptics=true;model().sound=true;model().save()}
        compose.onNodeWithContentDescription("SHIFT").performClick()
        compose.onNodeWithContentDescription("AC").performClick()
        compose.runOnIdle{assertEquals("",model().editor.source);assertEquals(0,model().variables.length());assertTrue(model().tape.isEmpty());assertEquals(count,model().history.size);assertEquals(10,model().precision);assertEquals(8,model().displayDigits);assertEquals(27f,model().inputFont);model().inputFont=25f;model().precision=30;model().displayDigits=10;model().sound=false;model().save()}
    }
    @Test fun recallTapInsertsVariableAndStoKeepsEditor() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().poweredOn=true;model().secondKeys=false;model().clear();model().store("A","42")}
        compose.waitUntil(30000){!model().busy&&model().variables.has("A")}
        compose.runOnIdle {model().edit(Editor("2+"))}
        compose.onNodeWithContentDescription("RCL").performClick()
        compose.onNodeWithText("Recall variable").assertExists()
        compose.onNodeWithText("Value / expression").assertDoesNotExist()
        compose.onNodeWithText("A = ").performClick()
        compose.runOnIdle {assertEquals("2+A",model().editor.source)}
        compose.onNodeWithText("Recall variable").assertDoesNotExist()
        compose.onNodeWithContentDescription("SHIFT").performClick()
        compose.onNodeWithContentDescription("STO").performClick()
        compose.onNodeWithText("Variables & functions").assertExists()
        compose.onNodeWithText("Value / expression").assertExists()
        compose.onNodeWithText("n").assertExists()
        compose.onNodeWithText("A = ").performClick()
        compose.onNodeWithTag("stored-variable-value").assertTextContains("42")
        compose.onNodeWithContentDescription("Paste current expression").performClick()
        compose.onNodeWithTag("stored-variable-value").assertTextContains("2+A")
        compose.onNodeWithText("Delete all").performClick()
        compose.runOnIdle {assertEquals(0,model().variables.length())}
        compose.onNodeWithText("Variables & functions").assertExists()
        compose.onNodeWithText("Done").performClick()
    }
    @Test fun storedPowerAlignsVariableNameWithBase() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().poweredOn=true;model().secondKeys=false;model().clear();model().store("A","x^2")}
        compose.waitUntil(30000){!model().busy&&model().variables.has("A")}
        compose.onNodeWithContentDescription("RCL").performClick()
        val inRow=hasAnyAncestor(hasTestTag("stored-variable-A"))
        val label=compose.onNode(hasText("A = ") and inRow,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val base=compose.onNode(hasText("x") and inRow,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals(label.center.y,base.center.y,with(compose.density){3.dp.toPx()})
        compose.onNodeWithText("Close").performClick()
    }
    @Test fun storedSquareRootUsesRadicalInRecallAndStore() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().poweredOn=true;model().secondKeys=false;model().clearAllScreen();model().edit(Editor("sqrt(2)"));model().calculate()}
        compose.waitUntil(30000){model().committed&&model().variables.has("Ans")}
        compose.onNodeWithContentDescription("RCL").performClick()
        val answerRow=hasAnyAncestor(hasTestTag("stored-variable-Ans"))
        compose.onNode(hasText("2") and answerRow,useUnmergedTree=true).assertExists()
        compose.onNode(hasText("1") and answerRow,useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("SHIFT").performClick()
        compose.onNodeWithContentDescription("STO").performClick()
        compose.onNodeWithText("Ans = ").performClick()
        compose.onNodeWithText("sqrt(2)").assertExists()
        compose.onNodeWithText("Done").performClick()
    }
    @Test fun rootKeyAfterVariableInsertsMultiplicationAndRadical() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().poweredOn=true;model().secondKeys=false;model().clear()}
        compose.onNodeWithContentDescription("ALPHA").performClick()
        compose.onNodeWithContentDescription("(−)").performClick()
        compose.onNodeWithContentDescription("√").performClick()
        compose.runOnIdle {assertEquals("A*sqrt()",model().editor.source);assertEquals("binary",model().editor.tree()?.kind);assertEquals("*",model().editor.tree()?.value)}
        val expression=hasAnyAncestor(hasContentDescription("Current expression"))
        compose.onNode(hasText("×",substring=true) and expression,useUnmergedTree=true).assertExists()
        compose.onNode(hasText("sqrt",substring=true) and expression,useUnmergedTree=true).assertDoesNotExist()
    }
    @Test fun calculusKeysAfterVariableKeepTheirMathSymbols() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().poweredOn=true;model().secondKeys=false;model().clear();model().insert("A")}
        compose.onNodeWithContentDescription("∫").performClick()
        compose.runOnIdle {assertEquals("A*integrate(,x,,)",model().editor.source);assertEquals("*",model().editor.tree()?.value);assertEquals("integrate",model().editor.tree()?.args?.get(1)?.value)}
        val expression=hasAnyAncestor(hasContentDescription("Current expression"))
        compose.onNode(hasText("∫") and expression,useUnmergedTree=true).assertExists()
        compose.onNode(hasText("integrate",substring=true) and expression,useUnmergedTree=true).assertDoesNotExist()
        compose.runOnIdle {model().clear();model().insert("A")}
        compose.onNodeWithContentDescription("2nd").performClick()
        compose.onNodeWithContentDescription("d/dx").performClick()
        compose.runOnIdle {assertEquals("A*diff(,x)",model().editor.source);assertEquals("*",model().editor.tree()?.value);assertEquals("diff",model().editor.tree()?.args?.get(1)?.value)}
        compose.onNode(hasText("diff",substring=true) and expression,useUnmergedTree=true).assertDoesNotExist()
    }
    @Test fun storedFormulaUsesCurrentVariablesAndDeleteAllClearsList() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().poweredOn=true;model().secondKeys=false;model().clear();model().store("A","2")}
        compose.waitUntil(30000){!model().busy&&model().variables.has("A")}
        compose.runOnIdle {model().store("B","3")}
        compose.waitUntil(30000){!model().busy&&model().variables.has("B")}
        compose.runOnIdle {model().store("C","A+B")}
        compose.waitUntil(30000){!model().busy&&model().variables.has("C")}
        compose.runOnIdle {assertEquals("binary",model().variables.getJSONObject("C").getString("kind"));assertEquals("+",model().variables.getJSONObject("C").getString("value"));model().store("B","5")}
        compose.waitUntil(30000){!model().busy&&model().variables.optJSONObject("B")?.optString("value")=="5"}
        compose.runOnIdle {model().store("B","C+1")}
        compose.waitUntil(30000){!model().busy&&model().error.contains("Cyclic variable definition")}
        compose.runOnIdle {assertEquals("5",model().variables.optJSONObject("B")?.optString("value"))}
        compose.runOnIdle {model().edit(Editor("C"));model().calculate()}
        compose.waitUntil(30000){model().committed}
        compose.runOnIdle {assertEquals("7",model().result?.optString("exact"))}
        compose.onNodeWithContentDescription("RCL").performClick()
        compose.onNodeWithText("C = ").assertExists()
        val formulaRow=hasAnyAncestor(hasTestTag("stored-variable-C"))
        compose.onNode(hasText("A") and formulaRow,useUnmergedTree=true).assertExists()
        compose.onNode(hasText("B") and formulaRow,useUnmergedTree=true).assertExists()
        compose.onNode(hasText("7") and formulaRow,useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithText("Delete all").performClick()
        compose.runOnIdle {assertEquals(0,model().variables.length());assertEquals("C",model().editor.source)}
    }
    @Test fun calcExpandsRecalledFormulaIntoInputVariables() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().poweredOn=true;model().secondKeys=false;model().clear();model().store("A","2")}
        compose.waitUntil(30000){!model().busy&&model().variables.has("A")}
        compose.runOnIdle {model().store("B","3")}
        compose.waitUntil(30000){!model().busy&&model().variables.has("B")}
        compose.runOnIdle {model().store("C","A+B")}
        compose.waitUntil(30000){!model().busy&&model().variables.has("C")}
        compose.onNodeWithContentDescription("RCL").performClick()
        compose.onNodeWithText("C = ").performClick()
        compose.onNodeWithContentDescription("CALC").performClick()
        compose.runOnIdle {assertEquals(listOf("A","B"),model().calcSession?.names);assertEquals("A",model().calcSession?.name)}
        compose.onNodeWithText("C = ").assertExists()
        compose.onNodeWithContentDescription("4").performClick()
        compose.onNodeWithContentDescription("=").performClick()
        compose.waitUntil(30000){!model().busy&&model().calcSession?.name=="B"}
        compose.onNodeWithContentDescription("5").performClick()
        compose.onNodeWithContentDescription("=").performClick()
        compose.waitUntil(30000){!model().busy&&model().committed}
        compose.runOnIdle {assertEquals("9",model().result?.optString("exact"));assertEquals("binary",model().variables.optJSONObject("C")?.optString("kind"))}
        compose.onNodeWithText("C = ").assertExists()
    }
    @Test fun calcPowerFormulaKeepsStatusOnOneLine() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().poweredOn=true;model().secondKeys=false;model().clear();model().store("A","2")}
        compose.waitUntil(30000){!model().busy&&model().variables.has("A")}
        compose.runOnIdle {model().store("C","A^2")}
        compose.waitUntil(30000){!model().busy&&model().variables.has("C")}
        compose.runOnIdle {model().edit(Editor("C"));model().startCalc();assertEquals(listOf("A"),model().calcSession?.names);model().insertCalcValue("3");model().submitCalcValue()}
        compose.waitUntil(30000){!model().busy&&model().committed}
        val label=compose.onNodeWithText("C = ",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val base=compose.onNode(hasText("A") and hasAnyAncestor(hasTestTag("calc-formula")),useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val input=compose.onNodeWithText("A = ",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val tolerance=with(compose.density){3.dp.toPx()}
        assertEquals(label.center.y,base.center.y,tolerance)
        assertEquals(label.center.y,input.center.y,tolerance)
    }
    @Test fun equationAndCustomFunctionWorkspaces() {
        compose.runOnIdle{model().clear();model().mode="Equations";model().equationKind="Quadratic";model().equationCoefficients=listOf("1","-5","6","0");model().equationSystem="x+y=3\nx-y=1";model().equationVariables="x,y"}
        compose.onNodeWithText("Solve",useUnmergedTree=true).performClick()
        compose.waitUntil(30000){!model().busy&&model().result!=null}
        compose.runOnIdle{assertEquals("{2, 3}",model().result!!.getString("exact"))}
        capture("equation-solver")
        compose.onNodeWithText("System").performClick()
        compose.onNodeWithText("Solve",useUnmergedTree=true).performClick()
        compose.waitUntil(30000){!model().busy&&model().result?.optString("exact")?.contains("y")==true}
        compose.runOnIdle{assertTrue(model().error,model().error.isEmpty());model().mode="Functions"}
        compose.onNodeWithText("Save function").performClick()
        compose.runOnIdle{assertTrue(model().functions.has("f"));assertEquals("x^2+1",model().functions.getJSONObject("f").getString("source"))}
        capture("custom-functions")
        compose.runOnIdle{model().mode="Scientific/CAS";model().clear()}
        compose.onNodeWithText("Catalog").performClick()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithText("f()").assertExists()
        compose.onNodeWithText("f()").performClick()
        compose.runOnIdle{assertEquals("f()",model().editor.source);assertEquals(2,model().editor.cursor)}
        compose.runOnIdle{model().mode="Scientific/CAS";model().clear();model().edit(Editor("f(3)+sinc(0)"));model().calculate()}
        compose.waitUntil(30000){!model().busy&&model().result!=null}
        compose.runOnIdle{assertEquals("11",model().result!!.getString("exact"))}
    }
    @Test fun customFunctionTransferRoundTrips() {
        compose.runOnIdle {model().clearMemory();model().mode="Functions";model().define("g","x,y","x+y",showResult=false)}
        compose.onNodeWithText("Export").assertExists()
        compose.onNodeWithText("Import").assertExists()
        compose.runOnIdle {
            val exported=model().exportFunctions()
            assertTrue(exported.contains("calcmax.functions"))
            model().removeVariable("g")
            assertFalse(model().functions.has("g"))
            assertEquals("Imported 1 function: 1 new",model().importFunctions(exported))
            assertEquals("x+y",model().functions.getJSONObject("g").getString("source"))
            assertEquals("Imported 1 function: 1 replaced",model().importFunctions(exported))
            assertEquals("",model().importFunctions("not json"))
            assertEquals("This file is not valid JSON",model().error)
            model().removeVariable("g")
        }
    }
    @Test fun functionsListScrollsWithoutMovingTheEditor() {
        compose.runOnIdle {
            model().clearMemory();model().mode="Functions"
            (1..12).forEach{index->model().define("f$index","x","x+$index",showResult=false)}
        }
        compose.onNodeWithText("f9(x)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save function").assertIsDisplayed()
        compose.runOnIdle {(1..12).forEach{index->model().removeVariable("f$index")}}
    }
    @Test fun directionalGraphPinches() {
        compose.runOnIdle{model().mode="Graph";model().xMin=-10.0;model().xMax=10.0;model().yMin=-5.0;model().yMax=5.0}
        val graph=compose.onNode(hasContentDescription("Graph with",substring=true))
        graph.performTouchInput {
            down(0,androidx.compose.ui.geometry.Offset(width*.35f,height*.5f));down(1,androidx.compose.ui.geometry.Offset(width*.65f,height*.5f))
            moveTo(0,androidx.compose.ui.geometry.Offset(width*.2f,height*.5f));moveTo(1,androidx.compose.ui.geometry.Offset(width*.8f,height*.5f));up(0);up(1)
        }
        compose.runOnIdle{assertTrue(model().xMax-model().xMin<18.0);assertEquals(10.0,model().yMax-model().yMin,.001);model().xMin=-10.0;model().xMax=10.0}
        graph.performTouchInput {
            down(0,androidx.compose.ui.geometry.Offset(width*.5f,height*.35f));down(1,androidx.compose.ui.geometry.Offset(width*.5f,height*.65f))
            moveTo(0,androidx.compose.ui.geometry.Offset(width*.5f,height*.2f));moveTo(1,androidx.compose.ui.geometry.Offset(width*.5f,height*.8f));up(0);up(1)
        }
        compose.runOnIdle{assertEquals(20.0,model().xMax-model().xMin,.001);assertTrue(model().yMax-model().yMin<9.0)}
    }
    @Test fun differentialGraphSelectorWorksAfterPanning() {
        compose.runOnIdle {model().mode="Graph";model().changeGraphKind("cartesian")}
        compose.onNodeWithText("Diff eq").performScrollTo().performTouchInput {click()}
        compose.waitUntil(30000) {model().graphKind=="differential" && !model().graphBusy && model().graphData!=null}
        val graph=compose.onNode(hasContentDescription("Graph with",substring=true))
        graph.performTouchInput {swipeRight()}
        compose.runOnIdle {assertTrue(model().xMin < -5.0)}
        compose.runOnIdle {model().xMin=1e12;model().xMax=1e12+0.000244140625}
        compose.onNodeWithText("Diff eq").performTouchInput {swipeRight()}
        compose.onNodeWithText("Cartesian").assertIsDisplayed().performTouchInput {click()}
        compose.runOnIdle {assertEquals("cartesian",model().graphKind)}
    }
    @Test fun keyboardOverlaysWithoutMovingKeys() {
        compose.runOnIdle{model().mode="Scientific/CAS";model().clear()}
        val before=compose.onNodeWithContentDescription("AC").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Keyboard").performClick()
        compose.onNodeWithContentDescription("Expression input").performClick().performTextInput("1234")
        compose.waitUntil(10000){androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true}
        val after=compose.onNodeWithContentDescription("AC").fetchSemanticsNode().boundsInRoot
        assertEquals(before,after)
        capture("keyboard-overlay")
        compose.activityRule.scenario.onActivity{androidx.core.view.WindowCompat.getInsetsController(it.window,it.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())}
    }
    @Test fun scientificCalculationAndThemes() {
        compose.runOnIdle { model().clear();model().mode="Scientific/CAS";model().theme="Light" }
        compose.onNodeWithContentDescription("2").performClick()
        compose.onNodeWithContentDescription("+").performClick()
        compose.onNodeWithContentDescription("3").performClick()
        compose.onNodeWithContentDescription("×").performClick()
        compose.onNodeWithContentDescription("4").performClick()
        compose.onNodeWithContentDescription("=").performClick()
        compose.waitUntil(30000) { !model().busy && model().result!=null }
        compose.runOnIdle { assertEquals("14",model().result!!.getString("exact"));assertEquals("2+3×4",model().editor.source) }
        capture("scientific-light")
        compose.runOnIdle { model().theme="Dark" }
        compose.waitForIdle();capture("scientific-dark")
        compose.runOnIdle { assertEquals("2+3×4",model().editor.source);assertEquals("14",model().result!!.getString("exact"));model().theme="System" }
    }
    @Test fun nativeCasAndGraphScreens() {
        compose.runOnIdle { model().clear();model().mode="Scientific/CAS";model().theme="Dark";model().edit(Editor("integrate(x^2*exp(x),x)"));model().calculate() }
        compose.waitUntil(30000) { !model().busy && model().result!=null }
        compose.runOnIdle { assertTrue(model().error,model().error.isEmpty());assertTrue(model().result!!.getString("exact").contains("exp(x)")) }
        capture("cas-dark")
        compose.runOnIdle { model().mode="Graph";model().graphSource="sin(x)\ncos(x)";model().xMin=-10.0;model().xMax=10.0;model().plot() }
        compose.waitUntil(30000) { !model().graphBusy && model().graphData!=null }
        capture("graph-dark")
        compose.runOnIdle { model().theme="Light" }
        compose.waitForIdle();capture("graph-light")
        compose.runOnIdle { model().mode="Matrix" }
        compose.waitForIdle();capture("matrix-light")
        compose.runOnIdle { model().mode="Statistics" }
        compose.waitForIdle();capture("statistics-light")
    }
    @Test fun multiArgumentInputWaitsForEquals() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().clear();model().edit(Editor("integrate(e)"))}
        Thread.sleep(300)
        compose.runOnIdle {
            assertEquals("",model().error)
            assertNull(model().result)
            model().edit(Editor("integrate(x^2,(x,0,1))"))
        }
        Thread.sleep(300)
        compose.runOnIdle {
            assertEquals("",model().error)
            assertNull(model().result)
            model().calculate()
        }
        compose.waitUntil(30000) {model().committed||model().error.isNotEmpty()}
        compose.runOnIdle {assertEquals("",model().error);assertEquals("1/3",model().result!!.getString("exact"))}
    }
    @Test fun engineIpcExactAndCancellationRecovery() = runBlocking {
        val client=EngineClient(compose.activity.applicationContext)
        fun request(source:String)=JSONObject().put("tree",JSONObject(Parser(source).parse().json())).put("angle","RAD")
        try {
            val r=client.execute(request("1/3+1/6"));assertTrue(r.toString(),r.getBoolean("ok"));assertEquals("1/2",r.getString("exact"))
            val derivative=client.execute(request("diff(sin(x^2),x)"));assertEquals("2*x*cos(x**2)",derivative.getString("exact"))
            val bad=client.execute(request("1/0"));assertFalse(bad.getBoolean("ok"))
            val graph=client.execute(JSONObject().put("action","graph").put("trees",org.json.JSONArray().put(JSONObject(Parser("sin(x)").parse().json()))).put("samples",100));assertTrue(graph.toString(),graph.getBoolean("ok"))
            val task=launch { client.execute(request("integrate(sin(x^x),x)")) }
            delay(100);task.cancelAndJoin();delay(500)
            val recovered=client.execute(request("2+2"));assertTrue(recovered.toString(),recovered.getBoolean("ok"));assertEquals("4",recovered.getString("exact"))
        } finally { client.close() }
    }
    @Test fun variablesSnapshotAndActivityRecreation() {
        compose.runOnIdle { model().store("A","1/3") }
        compose.waitUntil(30000) { !model().busy && model().variables.has("A") }
        compose.runOnIdle { model().edit(Editor("A+1/6"));model().calculate() }
        compose.waitUntil(30000) { !model().busy }
        compose.runOnIdle { assertEquals("1/2",model().result!!.getString("exact"));model().theme="Dark";model().save() }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("A+1/6",model().editor.source);assertEquals("Dark",model().theme);assertTrue(model().variables.has("A")) }
    }
    @Test(timeout=60000) fun systemThemeAndLandscapePreserveState() {
        // Observe the Android configuration itself. Espresso's old-root frame wait can
        // race window replacement; it is not a reliable signal for configuration completion.
        fun settle(){repeat(8){compose.mainClock.advanceTimeByFrame();Thread.sleep(30)}}
        fun nativeCapture(name:String):Int {
            lateinit var bitmap:Bitmap
            lateinit var file:File
            val completed=java.util.concurrent.CountDownLatch(1)
            val status=java.util.concurrent.atomic.AtomicInteger(-1)
            compose.activityRule.scenario.onActivity {activity->
                val view=activity.window.decorView
                bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                file=File(activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
                android.view.PixelCopy.request(activity.window,bitmap,{code->status.set(code);completed.countDown()},android.os.Handler(android.os.Looper.getMainLooper()))
                val vm=ViewModelProvider(activity)[CalculatorModel::class.java]
                assertEquals("sqrt(8)",vm.editor.source);assertEquals(5,vm.editor.cursor)
            }
            assertTrue(completed.await(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(android.view.PixelCopy.SUCCESS,status.get())
            val pixel=bitmap.getPixel(4,bitmap.height/2)
            val brightness=android.graphics.Color.red(pixel)+android.graphics.Color.green(pixel)+android.graphics.Color.blue(pixel)
            file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            return brightness
        }
        compose.runOnIdle {model().theme="System";model().mode="Scientific/CAS";model().edit(Editor("sqrt(8)",5));model().save()}
        compose.activityRule.scenario.onActivity {(it.getSystemService(Context.UI_MODE_SERVICE)as UiModeManager).setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)}
        settle();val dark=nativeCapture("system-dark")
        compose.activityRule.scenario.onActivity {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        settle();nativeCapture("landscape-dark")
        compose.activityRule.scenario.onActivity {(it.getSystemService(Context.UI_MODE_SERVICE)as UiModeManager).setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        settle();val light=nativeCapture("system-light")
        assertTrue("System theme must actually change the rendered colors",light>dark+200)
    }
    @Test fun immediateCalculationFixedKeypadAndAnswerContinuation() {
        compose.runOnIdle {model().poweredOn=true;model().mode="Scientific/CAS";model().theme="Light";model().clear()}
        val top=compose.onNodeWithContentDescription("Calculator keypad").fetchSemanticsNode().boundsInRoot.top
        val historyCount=model().history.size
        compose.onNodeWithContentDescription("2").performClick()
        compose.waitUntil(15000){model().result?.optString("exact")=="2"}
        compose.runOnIdle {assertEquals(historyCount,model().history.size)}
        compose.onNodeWithContentDescription("+").performClick()
        compose.onNodeWithContentDescription("3").performClick()
        compose.waitUntil(15000){model().result?.optString("exact")=="5"}
        assertEquals(top,compose.onNodeWithContentDescription("Calculator keypad").fetchSemanticsNode().boundsInRoot.top,.5f)
        compose.onNodeWithContentDescription("=").performClick()
        compose.waitUntil(15000){model().committed}
        compose.onNodeWithContentDescription("Answer panel").assertExists()
        compose.onNodeWithContentDescription("+").performClick()
        compose.runOnIdle {assertEquals("Ans+",model().editor.source);assertEquals("2+3",model().tape.last().source)}
        compose.onNodeWithContentDescription("Previous answer").assertExists()
        compose.onNodeWithContentDescription("4").performClick()
        compose.waitUntil(15000){model().result?.optString("exact")=="9"}
        assertEquals(top,compose.onNodeWithContentDescription("Calculator keypad").fetchSemanticsNode().boundsInRoot.top,.5f)
        capture("fixed-keypad-answer-chain")
        compose.onNodeWithContentDescription("=").performClick()
        compose.waitUntil(15000){model().committed}
        compose.onNodeWithContentDescription("7").performClick()
        compose.runOnIdle {assertEquals("7",model().editor.source)}
        compose.onNodeWithContentDescription("Calculation history, swipe vertically").performTouchInput{swipeDown()}
        assertEquals(top,compose.onNodeWithContentDescription("Calculator keypad").fetchSemanticsNode().boundsInRoot.top,.5f)
        compose.onAllNodesWithText("OFFLINE MATHEMATICS",substring=true).assertCountEquals(0)
    }
    @Test fun screenButtonExpandsDisplayAndKeepsNumericKeys() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().secondKeys=false;model().clear()}
        val originalTop=compose.onNodeWithContentDescription("Calculator keypad").fetchSemanticsNode().boundsInRoot.top
        val originalNumericHeight=compose.onNodeWithContentDescription("7").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithContentDescription("Expand calculation screen").performClick()
        compose.onNodeWithContentDescription("sin").assertDoesNotExist()
        compose.onNodeWithContentDescription("SHIFT").assertDoesNotExist()
        compose.onNodeWithContentDescription("7").assertExists()
        assertEquals(originalNumericHeight,compose.onNodeWithContentDescription("7").fetchSemanticsNode().boundsInRoot.height,.5f)
        assertTrue(compose.onNodeWithContentDescription("Calculator keypad").fetchSemanticsNode().boundsInRoot.top>originalTop)
        compose.onNodeWithContentDescription("7").performClick()
        compose.runOnIdle {assertEquals("7",model().editor.source)}
        compose.onNodeWithContentDescription("Restore full keypad").performClick()
        compose.onNodeWithContentDescription("sin").assertExists()
        assertEquals(originalTop,compose.onNodeWithContentDescription("Calculator keypad").fetchSemanticsNode().boundsInRoot.top,.5f)
    }
    @Test fun previousCalculationsRemainScrollableWithDamagedSavedTrees() {
        val damaged=HistoryEntry(1,"2+2","4","4","Scientific",inputTree="{invalid",response="{invalid").toTapeEntry()
        assertTrue(JSONObject(damaged.input).has("kind"))
        assertEquals("4",JSONObject(damaged.result).getJSONObject("tree").getString("value"))
        val entries=(0 until 25).map { index ->
            when(index) {
                14->TapeEntry("damaged", "{invalid", "{invalid", "{invalid")
                15->TapeEntry("nested", """{"kind":"list","args":["invalid",{"kind":"number","value":"2"}]}""", """{"exact":"2","tree":{"kind":"number","value":"2"}}""")
                else->HistoryEntry(index.toLong(),"$index+1",(index+1).toString(),(index+1).toString(),"Scientific").toTapeEntry()
            }
        }
        // Seed the screen tape directly; damaged entries fall back to compact text rendering.
        val setTape=CalculatorModel::class.java.getDeclaredMethod("setTape",List::class.java).apply {isAccessible=true}
        compose.runOnIdle {
            model().mode="Scientific/CAS";model().clearHistory();model().clear()
            setTape.invoke(model(),entries)
        }
        val tape=compose.onNodeWithContentDescription("Calculation history, swipe vertically")
        compose.onNodeWithContentDescription("Expand calculation screen").performClick()
        compose.onNodeWithContentDescription("Reuse calculation: 10+1").performScrollTo()
        repeat(3) {tape.performTouchInput {swipeDown()}}
        compose.onNodeWithContentDescription("Restore full keypad").performClick()
        compose.onNodeWithContentDescription("Reuse calculation: damaged").performScrollTo().performClick()
        for(label in listOf("1+1","20+1","4+1","nested","1+1"))compose.onNodeWithContentDescription("Reuse calculation: $label").performScrollTo()
        tape.assertExists()
        compose.runOnIdle {assertEquals(25,model().tape.size);assertEquals("damaged",model().editor.source)}
    }
    @Test fun selectedStructuredExpressionSurvivesHistoryScrolling() {
        val entries=List(40) {index->HistoryEntry(index.toLong(),"$index+1",(index+1).toString(),(index+1).toString(),"Scientific").toTapeEntry()}
        val setTape=CalculatorModel::class.java.getDeclaredMethod("setTape",List::class.java).apply {isAccessible=true}
        compose.runOnIdle {
            model().mode="Scientific/CAS";model().clearHistory();model().clear()
            setTape.invoke(model(),entries)
        }
        val tape=compose.onNodeWithContentDescription("Calculation history, swipe vertically")
        for(source in listOf("integrate((x^2)/(x+1),x,0,2)","nderivative(sin(x^2),x,3)","(x^2+1)/(x-1)","stats([1,2,3,4,5,6,7,8,9,10])")) {
            compose.runOnIdle {
                model().edit(Editor(source).selectRange(0,source.length))
            }
            repeat(3) {
                compose.onNodeWithContentDescription("Reuse calculation: 30+1").performScrollTo()
                compose.onNodeWithContentDescription("Answer panel").performScrollTo()
                tape.performTouchInput {swipeUp()}
                tape.performTouchInput {swipeDown()}
            }
            if(source.startsWith("integrate(")||source.startsWith("stats("))repeat(8) {
                tape.performTouchInput {swipeUp(durationMillis=80)}
                tape.performTouchInput {swipeDown(durationMillis=80)}
            }
            compose.runOnIdle {
                assertEquals(source,model().editor.source)
                assertEquals(0,model().editor.anchor)
                assertEquals(source.length,model().editor.cursor)
            }
            compose.onNodeWithContentDescription("Answer panel").performScrollTo()
            compose.onNodeWithContentDescription("After expression").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Current expression").assertIsFocused()
        }
    }
    @Test fun matrixAndStatisticsResultsScrollInCalculationTape() = runBlocking {
        val largeMatrix=List(32) {row->List(32) {column->if(row==column)"1" else "0"}.joinToString(",","[","]")}.joinToString(",","[","]")
        val sources=listOf(
            "stats(59,9)",
            "stats([1,2,3,4,5,6,7,8])",
            largeMatrix,
            "[[1,2,3,4],[5,6,7,8],[9,10,11,12],[13,14,15,16]]",
            "inverse([[1,2,3],[0,1,4],[5,6,0]])",
            "quartiles([1,2,3,4,5,6,7,8])",
            "lu([[1,2,3],[0,1,4],[5,6,0]])",
            "eigenvalues([[1,0],[0,2]])"
        )
        val client=EngineClient(compose.activity.applicationContext)
        val entries=try {
            sources.map { source ->
                val input=JSONObject(Parser(source).parse().json())
                val result=client.execute(JSONObject().put("tree",input).put("angle","RAD"))
                assertTrue("$source: $result",result.optBoolean("ok"))
                TapeEntry(source,input.toString(),result.toString())
            }
        } finally {client.close()}
        val setTape=CalculatorModel::class.java.getDeclaredMethod("setTape",List::class.java).apply {isAccessible=true}
        compose.runOnIdle {model().mode="Scientific/CAS";model().clearHistory();model().clear();setTape.invoke(model(),List(4){entries.map {entry->TapeEntry(entry.source,entry.input,entry.result,entry.answer)}}.flatten())}
        val tape=compose.onNodeWithContentDescription("Calculation history, swipe vertically")
        compose.onNodeWithContentDescription("Expand calculation screen").performClick()
        for(source in sources)compose.onAllNodesWithContentDescription("Reuse calculation: ${source.take(120)}").onFirst().performScrollTo()
        compose.onNodeWithContentDescription("Restore full keypad").performClick()
        for(source in sources.reversed())compose.onAllNodesWithContentDescription("Reuse calculation: ${source.take(120)}").onFirst().performScrollTo()
        tape.assertExists()
        Unit
    }
    @Test fun manyStatisticsEntriesScrollWithoutCrashing() = runBlocking {
        val source="stats([1,2,3,4,5,6,7,8,9,10])"
        val input=JSONObject(Parser(source).parse().json())
        val client=EngineClient(compose.activity.applicationContext)
        val result=try {client.execute(JSONObject().put("tree",input).put("angle","RAD"))} finally {client.close()}
        assertTrue(result.toString(),result.optBoolean("ok"))
        assertEquals("rows",result.getJSONObject("tree").getString("kind"))
        val entries=List(120) {TapeEntry(source,input.toString(),result.toString())}
        val setTape=CalculatorModel::class.java.getDeclaredMethod("setTape",List::class.java).apply {isAccessible=true}
        compose.runOnIdle {model().mode="Scientific/CAS";model().clearHistory();model().clear();setTape.invoke(model(),entries);model().edit(Editor(source));model().calculate()}
        compose.waitUntil(15000) {model().committed || model().error.isNotBlank()}
        compose.runOnIdle {assertEquals("",model().error)}
        val tape=compose.onNodeWithContentDescription("Calculation history, swipe vertically")
        repeat(20) {
            tape.performTouchInput {swipeUp(durationMillis=60)}
            tape.performTouchInput {swipeDown(durationMillis=60)}
        }
        compose.onNodeWithContentDescription("Expand calculation screen").performClick()
        val reuseRows=compose.onAllNodesWithContentDescription("Reuse calculation: $source")
        for(index in listOf(0,30,60,90,119))reuseRows[index].performScrollTo()
        repeat(4){tape.performTouchInput {swipeDown()}}
        compose.onNodeWithContentDescription("Restore full keypad").performClick()
        for(index in listOf(119,90,60,30,0))reuseRows[index].performScrollTo()
        reuseRows[119].performScrollTo()
        reuseRows[0].performScrollTo().performClick()
        for(index in listOf(0,5,9,3,1))reuseRows[index].performScrollTo()
        repeat(3){tape.performTouchInput {swipeDown()}}
        tape.assertExists()
        compose.runOnIdle {assertEquals(source,model().editor.source)}
        Unit
    }
    @Test fun longStatisticsHistoryCanBeReusedAndScrolledAgain() {
        val source=(1..300).joinToString(",","stats([","])")
        val input=JSONObject(Parser(source).parse().json())
        val entries=List(20) {HistoryEntry(it.toLong(),"$it+1",(it+1).toString(),(it+1).toString(),"Scientific").toTapeEntry()}+
            TapeEntry(source,input.toString(),"""{"exact":"summary"}""")
        val setTape=CalculatorModel::class.java.getDeclaredMethod("setTape",List::class.java).apply {isAccessible=true}
        compose.runOnIdle {model().mode="Scientific/CAS";model().clearHistory();model().clear();setTape.invoke(model(),entries)}
        val tape=compose.onNodeWithContentDescription("Calculation history, swipe vertically")
        compose.onNodeWithContentDescription("Reuse calculation: ${source.take(120)}").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Expression input").assertExists()
        for(label in listOf("0+1","9+1","16+1","5+1","0+1"))compose.onNodeWithContentDescription("Reuse calculation: $label").performScrollTo()
        repeat(3){tape.performTouchInput {swipeDown()}}
        compose.runOnIdle {assertEquals(source,model().editor.source)}
    }
    @Test fun editingWhileHistoryIsScrolledReturnsToTheActiveItem() {
        val entries=List(40) {index->HistoryEntry(index.toLong(),"$index+1",(index+1).toString(),(index+1).toString(),"Scientific").toTapeEntry()}
        val setTape=CalculatorModel::class.java.getDeclaredMethod("setTape",List::class.java).apply {isAccessible=true}
        compose.runOnIdle {model().mode="Scientific/CAS";model().clearHistory();model().clear();setTape.invoke(model(),entries)}
        val tape=compose.onNodeWithContentDescription("Calculation history, swipe vertically")
        // Let the automatic jump triggered by the setup settle before the swipes move the list.
        compose.runOnIdle {};compose.waitForIdle()
        val scrollValue={runCatching {tape.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()}.getOrDefault(0f)}
        var moved=false
        repeat(6) {
            tape.performTouchInput {swipeDown(durationMillis=60)}
            if(scrollValue()>0f)moved=true
        }
        assertTrue("tape did not scroll away from the active item (value=${scrollValue()})",moved)
        compose.runOnIdle {model().insert("7")}
        compose.waitUntil(8000) {scrollValue()==0f}
        compose.runOnIdle {assertEquals("7",model().editor.source)}
    }
    @Test fun symbolicRenderingCalculusAndFractionExit() {
        compose.runOnIdle {model().mode="Scientific/CAS";model().clear();model().decimal=true;model().edit(Editor("integrate(x,x)"))}
        compose.waitUntil(15000){model().result?.optString("exact")=="x**2/2 + C"}
        compose.onAllNodesWithText("integrate",substring=true).assertCountEquals(0)
        compose.onAllNodesWithText("**",substring=true).assertCountEquals(0)
        capture("symbolic-integral-decimal")
        compose.runOnIdle {model().edit(Editor("integrate(x,x,0,2)"))}
        compose.waitUntil(15000){model().result?.optString("exact")=="2"}
        compose.runOnIdle {model().edit(Editor("nderivative(x^2,x,3)"))}
        compose.waitUntil(15000){model().result?.optString("decimal")=="6"}
        compose.onAllNodesWithText("nderivative",substring=true).assertCountEquals(0)
        compose.runOnIdle {model().edit(Editor("5!"))}
        compose.waitUntil(15000){model().result?.optString("exact")=="120"}
        compose.onAllNodesWithText("factorial",substring=true).assertCountEquals(0)
        compose.runOnIdle {model().edit(Editor("(1)/(3)",5))}
        compose.onNodeWithContentDescription("After fraction").performClick()
        compose.runOnIdle {assertEquals(model().editor.source.length,model().editor.cursor);model().insert("+1")}
        compose.waitUntil(15000){model().result?.optString("exact")=="4/3"}
        val paste=compose.onNodeWithText("Paste").fetchSemanticsNode().boundsInRoot.center.y
        val keyboard=compose.onNodeWithText("Keyboard").fetchSemanticsNode().boundsInRoot.center.y
        assertEquals(paste,keyboard,1f)
    }
    @Test fun structuredCursorBaselineMemoryAndSecondKeys() {
        compose.runOnIdle{model().mode="Scientific/CAS";model().poweredOn=true;model().secondKeys=false;model().clearHistory();model().clear();model().edit(Editor("3^2+6").selectRange(2,3))}
        val expression=hasAnyAncestor(hasContentDescription("Current expression"))
        val three=compose.onNode(hasText("3") and expression,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.top
        val six=compose.onNode(hasText("6") and expression,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.top
        assertEquals(three,six,1f)
        compose.onNodeWithContentDescription("Cursor right").performClick()
        compose.runOnIdle{model().insert("+3");assertEquals("3^(2+3)+6",model().editor.source)}
        compose.waitUntil(15000){model().result?.optString("exact")=="249"}
        capture("exponent-editing")
        compose.runOnIdle{model().edit(Editor("(1)/(3)+6"))}
        compose.waitUntil(15000){model().result?.optString("exact")=="19/3"}
        capture("fraction-axis")
        compose.runOnIdle{model().edit(Editor("sin(3pi)",5))}
        compose.onAllNodes(hasText("│",substring=true) and expression,useUnmergedTree=true).assertCountEquals(1)
        compose.runOnIdle{model().clear()}
        compose.onNodeWithContentDescription("x²").performClick()
        compose.onNodeWithContentDescription("Empty expression slot").assertExists()
        compose.onNodeWithContentDescription("3").performClick()
        compose.onAllNodesWithContentDescription("Empty expression slot").assertCountEquals(0)
        compose.onAllNodes(hasText("(") and expression,useUnmergedTree=true).assertCountEquals(0)
        compose.onNodeWithContentDescription("AC").performClick()
        compose.onAllNodesWithContentDescription("Empty expression slot").assertCountEquals(0)
        compose.onNodeWithContentDescription("SHIFT").performClick();compose.onNodeWithContentDescription("√").performClick()
        compose.onAllNodes(hasText("cbrt",substring=true) and expression,useUnmergedTree=true).assertCountEquals(0)
        compose.runOnIdle{model().clear();model().removeVariable("M");model().edit(Editor("2+3"))}
        compose.waitUntil(15000){model().result?.optString("exact")=="5"}
        compose.onNodeWithContentDescription("M+").performClick()
        compose.waitUntil(15000){!model().busy&&model().variables.has("M")}
        compose.runOnIdle{assertEquals("5",model().result!!.optString("exact"));assertEquals("2+3",model().editor.source)}
        compose.onNodeWithContentDescription("M+").performClick()
        compose.waitUntil(15000){!model().busy&&model().variables.optJSONObject("M")?.optString("value")=="10"}
        compose.runOnIdle{assertEquals("5",model().result!!.optString("exact"))}
        compose.onNodeWithContentDescription("2nd").performClick()
        compose.onNodeWithContentDescription("factor").assertExists();compose.onNodeWithContentDescription("1st").performClick()
        compose.onNodeWithContentDescription("sin").assertExists()
        compose.runOnIdle{model().clear()}
        compose.onNodeWithContentDescription("ALPHA").performClick();compose.onNodeWithContentDescription("log").performClick()
        compose.runOnIdle{assertEquals("z",model().editor.source);model().clear()}
        compose.onNodeWithContentDescription("ALPHA").performClick();compose.onNodeWithContentDescription("ln").performClick()
        compose.runOnIdle{assertEquals("t",model().editor.source)}
        compose.onNodeWithContentDescription("2nd").performClick();capture("second-keypad");compose.onNodeWithContentDescription("1st").performClick()
    }
    @Test fun moneyModesGraphSelectorAndCustomPrecision() {
        compose.runOnIdle{model().mode="Graph";model().graphSource="sin(x)";model().radianAxis=false}
        compose.onNodeWithText("x: decimal").performClick()
        compose.runOnIdle{assertTrue(model().radianAxis)}
        capture("graph-radian-axis")
        compose.onNodeWithContentDescription("Choose calculation mode").performClick()
        compose.onNodeWithText("Tip").performClick()
        compose.onNodeWithText("Tip calculator").assertExists();capture("tip-calculator")
        compose.onNodeWithContentDescription("Choose calculation mode").performClick()
        compose.onNodeWithText("Currency").performClick()
        compose.onNodeWithText("Manual").performClick()
        compose.onNodeWithText("1 USD = ? KRW").performTextReplacement("1300")
        compose.onNodeWithText("≈ 130,000 KRW").assertExists();capture("currency-manual")
        compose.onNodeWithText("1 USD = ? KRW").performTextReplacement("1300.123456789")
        compose.onNodeWithText("≈ 130,012.345679 KRW").assertExists()
        compose.onNodeWithText("1 USD = 1,300.123457 KRW").assertExists()
        compose.onNodeWithText(java.util.Currency.getInstance("USD").getDisplayName(java.util.Locale.ENGLISH)).assertExists()
        compose.onNodeWithText(java.util.Currency.getInstance("KRW").getDisplayName(java.util.Locale.ENGLISH)).assertExists()
        compose.onAllNodesWithText("TRY").assertCountEquals(2)
        compose.onNodeWithText("From (ISO code)").performTextReplacement("TRY")
        compose.onNodeWithText(java.util.Currency.getInstance("TRY").getDisplayName(java.util.Locale.ENGLISH)).assertExists()
        compose.onNodeWithText("Setup").performClick()
        compose.onAllNodesWithText("Custom")[0].performScrollTo().performClick()
        compose.onNodeWithText("Custom internal precision · 3–200").performTextReplacement("42")
        compose.onNodeWithText("Apply internal precision").performClick()
        compose.runOnIdle{assertEquals(42,model().precision)}
        compose.onAllNodesWithText("Custom")[1].performScrollTo().performClick()
        compose.onNodeWithText("Custom display digits · 2–200").performTextReplacement("12")
        compose.onNodeWithText("Apply display digits").performClick()
        compose.runOnIdle{assertEquals(12,model().displayDigits)}
        compose.onNodeWithText("Done").performClick()
    }
}

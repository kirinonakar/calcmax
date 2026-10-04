package com.kirinonakar.calcmax

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kirinonakar.calcmax.calculator.*
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.math.*
import com.kirinonakar.calcmax.math.Editor
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculatorInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(compose.activity)[CalculatorModel::class.java]
    @Test fun workspaceKeyboardPolicyPreservesScientificKeypadOverlayAcrossModeChanges() {
        for(mode in listOf("Scientific/CAS","Graph","Equations","Matrix","Statistics","Python","Graph","Scientific/CAS")) {
            compose.runOnIdle {model().mode=mode}
            compose.runOnIdle {
                val expected=if(mode=="Scientific/CAS")android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING else android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                assertEquals(mode,expected,compose.activity.window.attributes.softInputMode and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
            }
        }
    }
    @Test fun latexPasteAndKeyboardInputRecognizeIndexedRootsAndFractionalPowers() {
        val latex="$$\\sqrt[3]{5} \\times 25^{\\frac{1}{3}}$$"
        val source="nthroot(5,3)*25^(((1)/(3)))"
        compose.runOnIdle {model().mode="Scientific/CAS";model().language="en";model().poweredOn=true;model().clear(recordUndo=false)}
        for(keyboard in listOf(false,true)) {
            compose.runOnIdle {model().clear(recordUndo=false)}
            if(keyboard) {
                compose.onNodeWithText("Keyboard").performClick()
                compose.onNodeWithContentDescription("Expression input").performClick().performTextInput(latex)
                compose.onNodeWithText("Math input").performClick()
            } else {
                compose.runOnIdle {
                    val clipboard=compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LaTeX",latex))
                }
                compose.onNodeWithText("Paste").performClick()
            }
            compose.runOnIdle {
                assertEquals(source,model().editor.source)
                assertEquals(source.length,model().editor.cursor)
                assertEquals("nthroot",model().editor.tree()!!.args[0].value)
                model().calculate()
            }
            compose.waitUntil(30000){!model().busy&&model().committed}
            compose.runOnIdle {assertEquals("5",model().result!!.getString("exact"))}
        }
        compose.runOnIdle {model().clear(recordUndo=false)}
    }
    @Test fun wrappedLatexKeyboardPasteReplacesSelectionInsideExpression() {
        compose.runOnIdle {
            model().mode="Scientific/CAS";model().language="en";model().poweredOn=true;model().clear(recordUndo=false)
            model().edit(Editor("1+2+3",3,2))
        }
        compose.onNodeWithText("Keyboard").performClick()
        val input=compose.onNodeWithContentDescription("Expression input")
        input.performClick()
        input.performTextInputSelection(androidx.compose.ui.text.TextRange(2,3))
        input.performTextInput("$$\\sqrt[3]{5} \\times 25^{\\frac{1}{3}}$$")
        compose.runOnIdle {
            assertEquals("1+nthroot(5,3)*25^(((1)/(3)))+3",model().editor.source)
            assertEquals(model().editor.source.length-2,model().editor.cursor)
            model().calculate()
        }
        compose.waitUntil(30000){!model().busy&&model().committed}
        compose.runOnIdle {assertEquals("9",model().result!!.getString("exact"))}
        compose.onNodeWithText("Math input").performClick()
        compose.runOnIdle {model().clear(recordUndo=false)}
    }
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
    @Test fun statisticsCellFocusSurvivesFingerRollPastTouchSlop() {
        compose.runOnIdle {model().mode="Statistics";model().language="en"}
        compose.onNodeWithText("New").performClick()
        compose.onNodeWithText("Add row").performScrollTo().performClick()
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
    @Test fun horizontalInputFollowsCursorAndWordWrapFitsTheViewport() {
        val longNumber="1234567890".repeat(7)
        compose.runOnIdle {model().mode="Scientific/CAS";model().poweredOn=true;model().wordWrap=false;model().clear(recordUndo=false);model().edit(Editor(longNumber))}
        val input=compose.onNodeWithContentDescription("Current expression")
        compose.waitUntil(5000){input.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()>0f}
        compose.runOnIdle {model().edit(Editor(longNumber,0))}
        compose.waitUntil(5000){input.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()==0f}
        compose.runOnIdle {model().wordWrap=true}
        compose.onNodeWithContentDescription("Current expression").assertExists()
        compose.onAllNodesWithContentDescription("Expression input").assertCountEquals(0)
        compose.runOnIdle {model().edit(Editor("1/2+sqrt(2)^3"))}
        compose.onNodeWithContentDescription("Current expression").assertExists()
        compose.runOnIdle {model().wordWrap=false;model().clear(recordUndo=false)}
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
    @Test fun graphTouchTracesWithoutChangingTheViewport() {
        compose.runOnIdle {
            model().language="en";model().theme="Light";model().poweredOn=true;model().mode="Graph"
            model().changeGraphKind("cartesian");model().updateGraphSource("x")
            model().xMin=-10.0;model().xMax=10.0;model().yMin=-5.0;model().yMax=5.0
            model().plot()
        }
        compose.waitUntil(30000) {!model().graphBusy && model().graphData!=null}
        val graph=compose.onNode(hasContentDescription("Graph with",substring=true))
        graph.performTouchInput {click(center)}
        compose.runOnIdle {
            assertNotNull("A graph tap must set the trace point",model().trace)
            assertEquals(0.0,model().trace!!.first,.05)
            assertEquals(model().trace!!.first,model().trace!!.second,.001)
            model().trace=null
        }
        graph.performTouchInput {
            down(center);moveTo(center+androidx.compose.ui.geometry.Offset(1f,1f));up()
        }
        compose.runOnIdle {
            assertNotNull("Small finger movement must still trace",model().trace)
            assertEquals(-10.0,model().xMin,0.0);assertEquals(10.0,model().xMax,0.0)
            assertEquals(-5.0,model().yMin,0.0);assertEquals(5.0,model().yMax,0.0)
        }
        // The finger's y is far from y=x. Tracing must keep the touched x,
        // rather than jumping sideways to the closest sampled point.
        graph.performTouchInput {click(androidx.compose.ui.geometry.Offset(width*.6f,height*.9f))}
        compose.runOnIdle {
            assertEquals(2.0,model().trace!!.first,.001)
            assertEquals(2.0,model().trace!!.second,.001)
        }
        val bitmap=graph.captureToImage().asAndroidBitmap()
        val markerRadius=5*compose.activity.resources.displayMetrics.density
        val markerX=bitmap.width*.6f
        val markerY=bitmap.height*.3f
        for(side in listOf(-1,1)) {
            assertEquals("Trace dot must stay visible at Android display density",0xFF006D5B.toInt(),
                bitmap.getPixel((markerX+side*markerRadius).toInt(),markerY.toInt()))
        }
        compose.runOnIdle {model().analyzeGraph("tangent","0","0",0,1)}
        compose.waitUntil(30000) {model().graphAnalysis?.optString("analysis")=="tangent"}
        graph.performTouchInput {click(androidx.compose.ui.geometry.Offset(width*.6f,height*.25f))}
        compose.waitUntil(30000) {
            val x=model().graphAnalysis?.optJSONArray("points")?.optJSONArray(0)?.optDouble(0)
            x!=null && kotlin.math.abs(x-2.0)<.001
        }
        compose.runOnIdle {
            assertEquals("Moving a tangent must keep the touched x",2.0,model().trace!!.first,.001)
            model().clearGraphTangent()
            model().updateGraphSource("x\n-x");model().plot()
        }
        compose.waitUntil(30000) {!model().graphBusy && model().graphData?.optJSONArray("curves")?.length()==2}
        compose.onNodeWithContentDescription("Select curve 2").performScrollTo().performClick()
        graph.performScrollTo().performTouchInput {click(androidx.compose.ui.geometry.Offset(width*.6f,height*.7f))}
        compose.runOnIdle {
            assertEquals(2.0,model().trace!!.first,.001)
            assertEquals(-2.0,model().trace!!.second,.001)
        }
        val secondBitmap=graph.captureToImage().asAndroidBitmap()
        assertEquals("Trace dot must use the selected curve's color",0xFFB7521E.toInt(),
            secondBitmap.getPixel((secondBitmap.width*.6f).toInt(),(secondBitmap.height*.7f).toInt()))
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
        compose.runOnIdle{model().clear()}
        compose.onNodeWithContentDescription("x²").performClick()
        compose.runOnIdle{assertEquals("()^2",model().editor.source)}
        compose.onNodeWithContentDescription("3").performClick()
        compose.waitUntil(15000){model().result?.optString("exact")=="9"}
        compose.runOnIdle{assertEquals("(3)^2",model().editor.source)}
        compose.onNodeWithContentDescription("AC").performClick()
        compose.runOnIdle{assertEquals("",model().editor.source)}
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
        compose.runOnIdle{assertEquals("n",model().editor.source);model().clear()}
        compose.onNodeWithContentDescription("ALPHA").performClick();compose.onNodeWithContentDescription("ln").performClick()
        compose.runOnIdle{assertEquals("t",model().editor.source)}
        compose.onNodeWithContentDescription("2nd").performClick();capture("second-keypad");compose.onNodeWithContentDescription("1st").performClick()
    }
}

/** Exercises model input paths directly, without an activity or UI automation. */
@RunWith(AndroidJUnit4::class)
class ConstantInputInstrumentedTest {
    @Test fun normalAndCalcInputKeepConstantsSeparate()=runBlocking {
        withContext(Dispatchers.Main) {
            val app=ApplicationProvider.getApplicationContext<Application>()
            val store=ViewModelStore()
            val model=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[CalculatorModel::class.java]
            try {
                model.clear(recordUndo=false)
                model.insert("Ans");model.insert("pi");model.insert("e")
                assertEquals("Ans*pi*e",model.editor.source)
                assertEquals("*",model.editor.tree()?.value)
                model.clear(recordUndo=false)
                model.edit(Editor("x+1"));model.startCalc()
                model.insertCalcValue("pi");model.insertCalcValue("e")
                assertEquals("pi*e",model.calcSession?.input?.source)
                model.cancelCalc()
                model.clear(recordUndo=false)
            } finally { store.clear() }
        }
    }
}

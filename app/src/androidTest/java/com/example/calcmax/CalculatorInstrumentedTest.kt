package com.example.calcmax

import android.graphics.Bitmap
import android.app.UiModeManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calcmax.calculator.*
import com.example.calcmax.math.*
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
    @Test fun secondPageInsertsStructuresAndGraphsCurrentExpression() {
        compose.runOnIdle {model().mode="Scientific";model().clear();model().secondKeys=true;model().edit(Editor("y=x^2+1"))}
        compose.onNodeWithContentDescription("Graph current expression").performClick()
        compose.runOnIdle {assertEquals("Graph",model().mode);assertEquals("cartesian",model().graphKind);assertEquals("x^2+1",model().graphSource)}
        compose.runOnIdle {model().mode="Scientific";model().clear();model().secondKeys=true}
        compose.onNodeWithContentDescription("Insert 2 by 2 matrix").performClick()
        compose.runOnIdle {assertEquals("[[,],[,]]",model().editor.source);assertEquals(2,model().editor.cursor)}
        compose.runOnIdle {model().clear();model().secondKeys=true}
        listOf("{","x",",","y","}").forEach {key->compose.onNodeWithContentDescription(key).performClick()}
        compose.runOnIdle {assertEquals("{x,y}",model().editor.source);assertEquals("set",model().editor.tree()?.kind)}
    }
    @Test fun tokenCursorMalformedInputAndClearAll() {
        compose.runOnIdle {model().mode="Scientific";model().clear();model().edit(Editor("1234",4,0))}
        val number=compose.onNode(hasText("1234") and hasAnyAncestor(hasContentDescription("Current expression")),useUnmergedTree=true)
        val widthWithoutCursor=number.fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle {model().edit(Editor("1234",2))}
        assertEquals(widthWithoutCursor,number.fetchSemanticsNode().boundsInRoot.width,0.1f)
        compose.runOnIdle {model().mode="Scientific";model().clear();model().edit(Editor("1234"))}
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
        compose.onNodeWithText("Done").performClick()
        var count=0
        compose.runOnIdle{count=model().history.size;model().precision=10;model().inputFont=27f;model().haptics=true;model().sound=true;model().save()}
        compose.onNodeWithContentDescription("SHIFT").performClick()
        compose.onNodeWithContentDescription("AC").performClick()
        compose.runOnIdle{assertEquals("",model().editor.source);assertEquals(0,model().variables.length());assertTrue(model().tape.isEmpty());assertEquals(count,model().history.size);assertEquals(10,model().precision);assertEquals(27f,model().inputFont);model().inputFont=25f;model().precision=30;model().sound=false;model().save()}
    }
    @Test fun equationAndCustomFunctionWorkspaces() {
        compose.runOnIdle{model().clear();model().mode="Equations"}
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
        compose.runOnIdle{model().mode="Scientific";model().clear()}
        compose.onNodeWithText("Catalog").performClick()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithText("f()").assertExists()
        compose.onNodeWithText("f()").performClick()
        compose.runOnIdle{assertEquals("f()",model().editor.source);assertEquals(2,model().editor.cursor)}
        compose.runOnIdle{model().mode="Scientific";model().clear();model().edit(Editor("f(3)+sinc(0)"));model().calculate()}
        compose.waitUntil(30000){!model().busy&&model().result!=null}
        compose.runOnIdle{assertEquals("11",model().result!!.getString("exact"))}
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
    @Test fun keyboardOverlaysWithoutMovingKeys() {
        compose.runOnIdle{model().mode="Scientific";model().clear()}
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
        compose.runOnIdle { model().clear();model().mode="Scientific";model().theme="Light" }
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
        compose.runOnIdle { model().clear();model().mode="CAS";model().theme="Dark";model().edit(Editor("integrate(x^2*exp(x),x)"));model().calculate() }
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
        compose.runOnIdle {model().mode="CAS";model().clear();model().edit(Editor("integrate(e)"))}
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
        compose.runOnIdle {model().theme="System";model().mode="Scientific";model().edit(Editor("sqrt(8)",5));model().save()}
        compose.activityRule.scenario.onActivity {(it.getSystemService(Context.UI_MODE_SERVICE)as UiModeManager).setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)}
        settle();val dark=nativeCapture("system-dark")
        compose.activityRule.scenario.onActivity {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        settle();nativeCapture("landscape-dark")
        compose.activityRule.scenario.onActivity {(it.getSystemService(Context.UI_MODE_SERVICE)as UiModeManager).setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        settle();val light=nativeCapture("system-light")
        assertTrue("System theme must actually change the rendered colors",light>dark+200)
    }
    @Test fun immediateCalculationFixedKeypadAndAnswerContinuation() {
        compose.runOnIdle {model().poweredOn=true;model().mode="Scientific";model().theme="Light";model().clear()}
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
    @Test fun symbolicRenderingCalculusAndFractionExit() {
        compose.runOnIdle {model().mode="Scientific";model().clear();model().decimal=true;model().edit(Editor("integrate(x,x)"))}
        compose.waitUntil(15000){model().result?.optString("exact")=="C + x**2/2"}
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
        compose.runOnIdle{model().mode="Scientific";model().poweredOn=true;model().secondKeys=false;model().clearHistory();model().clear();model().edit(Editor("3^2+6").selectRange(2,3))}
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
        compose.onNodeWithText("Setup").performClick()
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNodeWithText("Custom precision · 3–200").performTextReplacement("42")
        compose.onNodeWithText("Apply precision").performClick()
        compose.runOnIdle{assertEquals(42,model().precision)}
        compose.onNodeWithText("Done").performClick()
    }
}

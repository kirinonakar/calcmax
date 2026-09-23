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
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
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
        compose.runOnIdle {model().theme="System";model().mode="Scientific";model().edit(Editor("sqrt(8)",5));model().save()}
        val uiMode=compose.activity.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        compose.runOnIdle {uiMode.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)}
        compose.waitForIdle()
        compose.runOnIdle {assertEquals("sqrt(8)",model().editor.source);assertEquals(5,model().editor.cursor)}
        capture("system-dark")
        compose.runOnIdle {compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        compose.waitForIdle();compose.onNodeWithContentDescription("=").assertIsDisplayed();capture("landscape-dark")
        compose.runOnIdle {assertEquals("sqrt(8)",model().editor.source);uiMode.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        compose.waitForIdle();capture("system-light")
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
}

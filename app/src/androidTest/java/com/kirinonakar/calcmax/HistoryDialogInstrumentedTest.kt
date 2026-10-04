package com.kirinonakar.calcmax

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.calculator.HistoryEntry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDialogInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(compose.activity)[CalculatorModel::class.java]

    private fun openHistory(entries: List<HistoryEntry>) {
        compose.runOnIdle {
            model().language = "en"
            model().mode = "Scientific/CAS"
            CalculatorModel::class.java.getDeclaredMethod("setHistory", List::class.java).apply {
                isAccessible = true
            }.invoke(model(), entries)
        }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Calculation history").assertIsDisplayed()
    }

    @Test fun damagedAndOversizedRecordsRemainReusable() {
        val large = "9".repeat(10_000)
        val entries = listOf(
            HistoryEntry(4, "sqrt(2)", "sqrt(2)", "1.4142135624", "Scientific/CAS", inputTree = "{invalid", response = "{invalid"),
            HistoryEntry(3, "1/2", "1/2", "0.5", "Scientific/CAS", response = """{"exact":"1/2","tree":{"kind":"sum","args":[1,null]}}"""),
            HistoryEntry(2, large, large, large, "Scientific/CAS", response = "{\"exact\":\"${large.repeat(3)}\"}"),
            HistoryEntry(1, "2+2", "4", "4", "Scientific/CAS")
        )
        openHistory(entries)
        compose.onNodeWithTag("history-list").performScrollToIndex(2)
        compose.onAllNodesWithText("9".repeat(49) + "…").onFirst().assertIsDisplayed()
        compose.onNodeWithTag("history-list").performScrollToIndex(0)
        compose.onAllNodesWithText("Reuse").onFirst().performClick()
        compose.runOnIdle { assertEquals("sqrt(2)", model().editor.source) }
    }

    @Test fun manyRecordsSurviveFlingFilteringAndDecimalMode() {
        val entries = List(500) { index ->
            HistoryEntry((500 - index).toLong(), "($index+1)/2", "${index + 1}/2", ((index + 1) / 2.0).toString(), "Scientific/CAS")
        }
        compose.runOnIdle { model().decimal = true }
        openHistory(entries)
        val history = compose.onNodeWithTag("history-list")
        repeat(3) {
            history.performTouchInput { swipeUp(durationMillis = 80) }
            history.performTouchInput { swipeDown(durationMillis = 80) }
        }
        history.performScrollToIndex(499)
        compose.onNodeWithText("Search history").performTextInput("(0+1)/2")
        compose.onNodeWithText("Reuse").assertIsDisplayed()
        compose.onNodeWithText("Favorites").performClick()
        compose.onNodeWithText("No calculations match \"(0+1)/2\".").assertIsDisplayed()
        compose.onNodeWithText("All", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { model().decimal = false }
    }
}

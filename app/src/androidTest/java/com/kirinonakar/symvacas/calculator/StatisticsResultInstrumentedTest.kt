package com.kirinonakar.symvacas.calculator

import android.app.Application
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.ui.statisticsReportFor
import com.kirinonakar.symvacas.ui.statisticsCellText
import com.kirinonakar.symvacas.ui.statisticsTestAnalyses
import com.kirinonakar.symvacas.ui.ResultDisplayFormat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real statistics calculation/engine path, without driving the UI. */
@RunWith(AndroidJUnit4::class)
class StatisticsResultInstrumentedTest {
    @Test fun calculationsKeepStructuredResultsAndPersistTheirSource() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val application=instrumentation.targetContext.applicationContext as Application
        val prefs=application.getSharedPreferences("calculator",0)
        val original=prefs.all.toMap()
        val store=ViewModelStore()
        lateinit var model:CalculatorModel
        try {
            prefs.edit().clear().commit()
            instrumentation.runOnMainSync {
                model=CalculatorModel(application)
                store.put("statistics",model)
                model.mode="Statistics"
            }
            val schema=application.assets.open("advanced_statistics.json").bufferedReader().use {JSONArray(it.readText())}
            val advanced=listOf("gee","padjust","pca").map {id->id to (0 until schema.length()).map {schema.getJSONObject(it)}.first {it.getString("id")==id}.getString("example")}
            val tests=listOf("ttest" to "ttest(0,[1,2,4,5])","ttest2" to "ttest2(0,[1,2,4,5],[3,4,6,7])",
                "ttestpaired" to "ttestpaired(0,[1,2,4,5],[3,3,6,8])","ztest" to "ztest(0,2,[1,2,4,5])","anova" to "anova([1,2,3],[3,4,6],[2,5,7])")
            for((id,source) in advanced+tests) {
                instrumentation.runOnMainSync {model.edit(Editor(source));model.calculate()}
                val deadline=SystemClock.elapsedRealtime()+60_000
                var finished=false
                while(SystemClock.elapsedRealtime()<deadline) {
                    instrumentation.runOnMainSync {finished=!model.busy&&(model.result!=null||model.error.isNotBlank())}
                    if(finished)break
                    SystemClock.sleep(50)
                }
                assertTrue("$id calculation timed out",finished)
                instrumentation.runOnMainSync {
                    assertEquals("$id engine error", "",model.error)
                    val result=requireNotNull(model.result)
                    assertTrue(result.optBoolean("ok"))
                    assertTrue("Original Output must remain available",result.optString("exact").isNotBlank())
                    assertEquals(source,model.resultSource)
                    val analyses=when {
                        id.startsWith("ttest")->statisticsTestAnalyses("t test")
                        id=="ztest"->statisticsTestAnalyses("z test")
                        id=="anova"->statisticsTestAnalyses("ANOVA")
                        else->setOf(id)
                    }
                    val report=requireNotNull(statisticsReportFor(result,model.resultSource,analyses))
                    assertEquals(id,report.getString("analysis"))
                    assertTrue(report.getJSONArray("sections").length()>0)
                    assertEquals(source,prefs.getString("resultSource",""))
                }
            }
            instrumentation.runOnMainSync {
                model.decimal=false;model.displayDigits=4
                val cell=JSONObject().put("exact","7/3").put("decimal","2.333333333333333")
                    .put("tree",JSONObject("""{"kind":"fraction","args":[{"kind":"number","value":"7"},{"kind":"number","value":"3"}]}"""))
                    .put("decimalTree",JSONObject("""{"kind":"number","value":"2.333333333333333"}"""))
                assertEquals("2.3333",statisticsCellText(model,cell))
                assertEquals("7/3",ResultDisplayFormat.resultText(cell,false,false,model.resultDisplayMode,false,false,0,false,false,4))
                model.decimal=true
                assertEquals("2.3333",statisticsCellText(model,cell))
            }
        } finally {
            instrumentation.runOnMainSync {store.clear()}
            restore(prefs,original)
        }
    }

    private fun restore(prefs:SharedPreferences,original:Map<String,*>) {
        val edit=prefs.edit().clear()
        original.forEach {(key,value)->when(value) {
            is String->edit.putString(key,value)
            is Boolean->edit.putBoolean(key,value)
            is Int->edit.putInt(key,value)
            is Long->edit.putLong(key,value)
            is Float->edit.putFloat(key,value)
            is Set<*>->edit.putStringSet(key,value.filterIsInstance<String>().toSet())
        }}
        edit.commit()
    }
}

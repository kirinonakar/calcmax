package com.kirinonakar.calcmax.calculator

import android.content.SharedPreferences
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject

private fun SharedPreferences.jsonObject(key:String):JSONObject =
    runCatching {JSONObject(getString(key,"{}") ?: "{}")}.getOrDefault(JSONObject())

/** Graph workspace state and its existing calculator.xml representation. */
internal class GraphState(private val prefs:SharedPreferences) {
    val sources=prefs.jsonObject("graphSources")
    var sequenceInitials by mutableStateOf(prefs.getString("sequenceInitials","0,1") ?: "0,1")
    var differentialInitials by mutableStateOf(prefs.getString("differentialInitials","1") ?: "1")
    var differentialT0 by mutableStateOf(prefs.getString("differentialT0","0") ?: "0")
    var graphKind by mutableStateOf(prefs.getString("graphKind","cartesian") ?: "cartesian")
    var graphSource by mutableStateOf(sources.optString(graphKind,prefs.getString("graphSource","sin(x)\ncos(x)") ?: "sin(x)\ncos(x)"))
    var xMin by mutableDoubleStateOf(prefs.getString("xMin","-10")?.toDoubleOrNull() ?: -10.0)
    var xMax by mutableDoubleStateOf(prefs.getString("xMax","10")?.toDoubleOrNull() ?: 10.0)
    var yMin by mutableDoubleStateOf(prefs.getString("yMin","-5")?.toDoubleOrNull() ?: -5.0)
    var yMax by mutableDoubleStateOf(prefs.getString("yMax","5")?.toDoubleOrNull() ?: 5.0)
    var zMin by mutableStateOf(prefs.getString("zMin",null)?.toDoubleOrNull())
    var zMax by mutableStateOf(prefs.getString("zMax",null)?.toDoubleOrNull())
    var graphData by mutableStateOf<JSONObject?>(null)
    var graphDerivativeSelected by mutableStateOf<Int?>(null)
    var graphAnalysis by mutableStateOf<JSONObject?>(null)
    var graphAnalysisBusy by mutableStateOf(false)
    var graphBusy by mutableStateOf(false)
    var trace by mutableStateOf<Pair<Double,Double>?>(null)
    var parameterMin by mutableDoubleStateOf(prefs.getString("parameterMin","0")?.toDoubleOrNull() ?: 0.0)
    var parameterMax by mutableDoubleStateOf(prefs.getString("parameterMax","6.283185307179586")?.toDoubleOrNull() ?: 6.283185307179586)
    var shadedInterval by mutableStateOf<Pair<Double,Double>?>(null)
    var graphParameters by mutableStateOf(loadParameters())
    var graphAnimating by mutableStateOf(false)
    var animationPhase=0.0
    var radianAxis by mutableStateOf(prefs.getBoolean("radianAxis",false))

    private fun loadParameters():Map<String,GraphParameter> = runCatching {
        val stored=prefs.jsonObject("graphParameters")
        stored.keys().asSequence().associateWith {name->
            val entry=stored.optJSONObject(name)
            val low=entry?.optDouble("min",-5.0)?.takeIf(Double::isFinite) ?: -5.0
            val high=entry?.optDouble("max",5.0)?.takeIf(Double::isFinite) ?: 5.0
            val value=entry?.optDouble("value",1.0)?.takeIf(Double::isFinite) ?: 1.0
            if(low<high)GraphParameter(value.coerceIn(low,high),low,high) else GraphParameter(1.0,-5.0,5.0)
        }
    }.getOrDefault(emptyMap())

    fun writeTo(editor:SharedPreferences.Editor) {
        val parameters=JSONObject()
        graphParameters.forEach {(name,spec)->parameters.put(name,JSONObject().put("value",spec.value).put("min",spec.min).put("max",spec.max))}
        editor.putString("sequenceInitials",sequenceInitials).putString("differentialInitials",differentialInitials).putString("differentialT0",differentialT0)
            .putString("graphSource",graphSource).putString("graphSources",sources.put(graphKind,graphSource).toString()).putString("graphKind",graphKind)
            .putString("xMin",xMin.toString()).putString("xMax",xMax.toString()).putString("yMin",yMin.toString()).putString("yMax",yMax.toString())
            .putString("zMin",zMin?.toString()).putString("zMax",zMax?.toString())
            .putString("parameterMin",parameterMin.toString()).putString("parameterMax",parameterMax.toString())
            .putBoolean("radianAxis",radianAxis).putString("graphParameters",parameters.toString())
    }
}

/** Statistics datasets and regression work share the backed-up calculator.xml file. */
internal class StatisticsState(private val prefs:SharedPreferences) {
    var dataSets by mutableStateOf(prefs.jsonObject("dataSets"))
    var regressionCurve by mutableStateOf(loadRegressionCurve())
    var regressionFit by mutableStateOf(prefs.getString("regressionFit","") ?: "")
    var regressionData by mutableStateOf(prefs.getString("regressionData","") ?: "")
    var regressionBusy by mutableStateOf(false)
    var statisticsName by mutableStateOf(prefs.getString("statisticsName","D1") ?: "D1")
    var statisticsData by mutableStateOf(prefs.getString("statisticsData","") ?: "")
    var statisticsKind by mutableStateOf(prefs.getString("statisticsKind","list") ?: "list")
    var statisticsRegression by mutableStateOf(prefs.getString("statisticsRegression","linear") ?: "linear")
    var statisticsPlot by mutableStateOf(prefs.getString("statisticsPlot","Histogram") ?: "Histogram")
    var statisticsSelected by mutableStateOf(prefs.getString("statisticsSelected","") ?: "")
    var statisticsIsNew by mutableStateOf(prefs.getBoolean("statisticsIsNew",false))
    var statisticsCsv by mutableStateOf(prefs.getBoolean("statisticsCsv",false))

    private fun loadRegressionCurve():List<Pair<Double,Double>>? = runCatching {
        val array=JSONArray(prefs.getString("regressionCurve","[]"))
        if(array.length()==0)null else (0 until array.length()).map {index->array.getJSONArray(index).let {it.getDouble(0) to it.getDouble(1)}}
    }.getOrNull()

    fun writeTo(editor:SharedPreferences.Editor) {
        editor.putString("dataSets",dataSets.toString())
            .putString("statisticsName",statisticsName).putString("statisticsData",statisticsData).putString("statisticsKind",statisticsKind)
            .putString("statisticsRegression",statisticsRegression).putString("statisticsPlot",statisticsPlot).putString("statisticsSelected",statisticsSelected)
            .putBoolean("statisticsIsNew",statisticsIsNew).putBoolean("statisticsCsv",statisticsCsv)
            .putString("regressionFit",regressionFit).putString("regressionData",regressionData)
            .putString("regressionCurve",regressionCurve?.let {list->JSONArray(list.map {point->JSONArray().put(point.first).put(point.second)}).toString()} ?: "[]")
    }
}

/** Python draft state; only the document URI is kept outside the backup. */
internal class PythonState(private val prefs:SharedPreferences,private val localPrefs:SharedPreferences) {
    var pythonSource by mutableStateOf(prefs.getString("pythonSource","") ?: "")
    var pythonSelectionStart by mutableIntStateOf(pythonSource.length)
    var pythonSelectionEnd by mutableIntStateOf(pythonSource.length)
    var pythonFileName by mutableStateOf(prefs.getString("pythonFileName","untitled.py") ?: "untitled.py")
    var pythonUri by mutableStateOf(loadUri())
    var pythonDirty by mutableStateOf(prefs.getBoolean("pythonDirty",false) || (pythonUri.isBlank() && pythonSource.isNotBlank()))
    var pythonOutput by mutableStateOf("")
    var pythonHasRun by mutableStateOf(false)
    var pythonError by mutableStateOf("")
    var pythonBusy by mutableStateOf(false)
    var pythonInputPrompt by mutableStateOf<String?>(null)
    var pythonInputSubmit:((String)->Unit)?=null

    private fun loadUri():String {
        val local=localPrefs.getString("pythonUri",null)
        val legacy=prefs.getString("pythonUri",null) ?: return local ?: ""
        if(local!=null || localPrefs.edit().putString("pythonUri",legacy).commit()) {
            prefs.edit().remove("pythonUri").commit()
            return local ?: legacy
        }
        return legacy
    }

    fun writeTo(editor:SharedPreferences.Editor) {
        editor.putString("pythonSource",pythonSource).putString("pythonFileName",pythonFileName).putBoolean("pythonDirty",pythonDirty)
        localPrefs.edit().putString("pythonUri",pythonUri).apply()
    }
}

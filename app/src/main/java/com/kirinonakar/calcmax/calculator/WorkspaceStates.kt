package com.kirinonakar.calcmax.calculator

import android.content.SharedPreferences
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

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
    var xMin by mutableDoubleStateOf(prefs.getString("xMin","-10")?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: -10.0)
    var xMax by mutableDoubleStateOf(prefs.getString("xMax","10")?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: 10.0)
    var yMin by mutableDoubleStateOf(prefs.getString("yMin","-5")?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: -5.0)
    var yMax by mutableDoubleStateOf(prefs.getString("yMax","5")?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: 5.0)
    var zMin by mutableStateOf(prefs.getString("zMin",null)?.toDoubleOrNull()?.takeIf(Double::isFinite))
    var zMax by mutableStateOf(prefs.getString("zMax",null)?.toDoubleOrNull()?.takeIf(Double::isFinite))
    var surfaceRenderMode by mutableStateOf(prefs.getString("surfaceRenderMode","wireframe")?.takeIf {it in listOf("wireframe","surface","surface-wireframe")} ?: "wireframe")
    var surfaceColor by mutableStateOf(prefs.getString("surfaceColor","#007b68")?.takeIf {it.matches(Regex("#[0-9a-fA-F]{6}"))} ?: "#007b68")
    var surfaceSamples by mutableIntStateOf(prefs.getInt("surfaceSamples",26).coerceIn(12,96))
    var surfaceAutoDensity by mutableStateOf(prefs.getBoolean("surfaceAutoDensity",true))
    var surfaceZoom by mutableFloatStateOf(prefs.getFloat("surfaceZoom",1f).takeIf {it.isFinite()}?.coerceIn(.4f,3f) ?: 1f)
    var graphData by mutableStateOf<JSONObject?>(null)
    var graphDerivativeSelected by mutableStateOf<Int?>(null)
    var graphAnalysis by mutableStateOf<JSONObject?>(null)
    var graphAnalysisBusy by mutableStateOf(false)
    var graphBusy by mutableStateOf(false)
    var trace by mutableStateOf<Pair<Double,Double>?>(null)
    var parameterMin by mutableDoubleStateOf(prefs.getString("parameterMin","0")?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: 0.0)
    var parameterMax by mutableDoubleStateOf(prefs.getString("parameterMax","6.283185307179586")?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: 6.283185307179586)
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

    fun updateSource(source:String) {
        graphSource=source
        clearAnalysis()
    }

    fun changeKind(kind:String):Boolean {
        if(kind==graphKind)return false
        sources.put(graphKind,graphSource)
        graphKind=kind
        graphSource=sources.optString(kind,when(kind){
            "implicit"->"x^2+y^2=1"
            "parametric"->"[cos(t),sin(t)]";"polar"->"2*cos(3*t)";"sequence"->"n\nu(n-1)+u(n-2)"
            "surface"->"sin(sqrt(x^2+y^2))";"differential"->"y-t";else->"sin(x)\ncos(x)"
        })
        clearAnalysis()
        graphAnimating=false
        if(kind=="sequence") {
            parameterMin=0.0;parameterMax=20.0;xMin=0.0;xMax=20.0;yMin=-2.0;yMax=20.0
        } else if(kind=="differential") {
            parameterMin=-5.0;parameterMax=5.0;xMin=-5.0;xMax=5.0;yMin=-3.0;yMax=5.0
        } else if(kind in listOf("surface","implicit")) {
            xMin=-3.0;xMax=3.0;yMin=-3.0;yMax=3.0
        } else if(kind!="cartesian" && xMin == -10.0 && xMax == 10.0 && yMin == -5.0 && yMax == 5.0) {
            xMin=-3.0;xMax=3.0;yMin=-3.0;yMax=3.0
        }
        return true
    }

    private fun clearAnalysis() {
        graphDerivativeSelected=null;graphData=null;graphAnalysis=null;trace=null;shadedInterval=null
    }

    fun parameterPayload():JSONObject = JSONObject().also {payload->graphParameters.forEach {(name,spec)->payload.put(name,spec.value)}}

    fun syncParameters(names:JSONArray?) {
        val next=(0 until (names?.length() ?: 0)).mapNotNull {names?.optString(it)}.filter(String::isNotBlank).distinct().sorted()
            .associateWith {name->graphParameters[name] ?: GraphParameter(1.0,-5.0,5.0)}
        if(next!=graphParameters)graphParameters=next
    }

    fun setParameter(name:String,value:Double) {
        val spec=graphParameters[name] ?: return
        if(!value.isFinite())return
        val clamped=value.coerceIn(spec.min,spec.max)
        if(clamped!=spec.value)graphParameters=graphParameters+(name to spec.copy(value=clamped))
    }

    fun setParameterRange(name:String,low:Double,high:Double):Boolean {
        val spec=graphParameters[name] ?: return false
        if(!low.isFinite()||!high.isFinite()||low>=high||abs(low)>1e9||abs(high)>1e9)return false
        graphParameters=graphParameters+(name to spec.copy(min=low,max=high,value=spec.value.coerceIn(low,high)))
        return true
    }

    fun resetParameters() {
        graphParameters=graphParameters.mapValues {(_,spec)->spec.copy(value=if(spec.min<=1.0&&1.0<=spec.max)1.0 else (spec.min+spec.max)/2)}
    }

    fun writeTo(editor:SharedPreferences.Editor) {
        val parameters=JSONObject()
        graphParameters.forEach {(name,spec)->parameters.put(name,JSONObject().put("value",spec.value).put("min",spec.min).put("max",spec.max))}
        editor.putString("sequenceInitials",sequenceInitials).putString("differentialInitials",differentialInitials).putString("differentialT0",differentialT0)
            .putString("graphSource",graphSource).putString("graphSources",sources.put(graphKind,graphSource).toString()).putString("graphKind",graphKind)
            .putString("xMin",xMin.toString()).putString("xMax",xMax.toString()).putString("yMin",yMin.toString()).putString("yMax",yMax.toString())
            .putString("zMin",zMin?.toString()).putString("zMax",zMax?.toString())
            .putString("surfaceRenderMode",surfaceRenderMode)
            .putString("surfaceColor",surfaceColor).putInt("surfaceSamples",surfaceSamples)
            .putBoolean("surfaceAutoDensity",surfaceAutoDensity).putFloat("surfaceZoom",surfaceZoom)
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
    var regressionMode by mutableStateOf(prefs.getString("regressionMode",prefs.getString("statisticsRegression","linear")) ?: "")
    var regressionCorrelation by mutableStateOf(prefs.getString("regressionCorrelation",null)?.toDoubleOrNull()?.takeIf(Double::isFinite))
    var regressionParameters by mutableStateOf(loadRegressionParameters())
    var regressionBusy by mutableStateOf(false)
    var statisticsName by mutableStateOf(prefs.getString("statisticsName","D1") ?: "D1")
    var statisticsData by mutableStateOf(prefs.getString("statisticsData","") ?: "")
    var statisticsKind by mutableStateOf(prefs.getString("statisticsKind","list") ?: "list")
    var statisticsRegression by mutableStateOf(prefs.getString("statisticsRegression","linear") ?: "linear")
    var statisticsCustomFormula by mutableStateOf((prefs.getString("statisticsCustomFormula","exp(-b*ADC)") ?: "exp(-b*ADC)").let {if(it=="S0*exp(-b*ADC)")"exp(-b*ADC)" else it})
    var statisticsCustomVariable by mutableStateOf(prefs.getString("statisticsCustomVariable","b") ?: "b")
    var statisticsCustomInitials by mutableStateOf(prefs.getString("statisticsCustomInitials","") ?: "")
    var statisticsPlot by mutableStateOf(prefs.getString("statisticsPlot","Histogram") ?: "Histogram")
    var statisticsSelected by mutableStateOf(prefs.getString("statisticsSelected","") ?: "")
    var statisticsIsNew by mutableStateOf(prefs.getBoolean("statisticsIsNew",false))
    var statisticsCsv by mutableStateOf(prefs.getBoolean("statisticsCsv",false))

    private fun loadRegressionCurve():List<Pair<Double,Double>>? = runCatching {
        val array=JSONArray(prefs.getString("regressionCurve","[]"))
        if(array.length()==0)null else (0 until array.length()).map {index->array.getJSONArray(index).let {it.getDouble(0) to it.getDouble(1)}}
    }.getOrNull()

    private fun loadRegressionParameters():List<Pair<String,String>> = runCatching {
        val array=JSONArray(prefs.getString("regressionParameters","[]"))
        (0 until array.length()).map {index->array.getJSONArray(index).let {it.getString(0) to it.getString(1)}}
    }.getOrDefault(emptyList())

    fun saveDataSet(name:String,csv:String,kind:String) {
        require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) {"Use a letter followed by letters, digits or underscores for the dataset name"}
        require(kind in listOf("list","xy","xyz")) {"Unknown dataset type"}
        dataSets=JSONObject(dataSets.toString()).put(name,JSONObject().put("csv",csv).put("kind",kind))
    }

    fun deleteDataSet(name:String) {dataSets=JSONObject(dataSets.toString()).apply {remove(name)}}

    fun updateSelection(name:String,data:String,kind:String,regression:String,plot:String,csv:Boolean,selected:String,isNew:Boolean,customFormula:String,customVariable:String,customInitials:String) {
        statisticsName=name;statisticsData=data;statisticsKind=kind;statisticsRegression=regression
        statisticsCustomFormula=customFormula;statisticsCustomVariable=customVariable;statisticsCustomInitials=customInitials
        statisticsPlot=plot;statisticsCsv=csv;statisticsSelected=selected;statisticsIsNew=isNew
    }

    fun saveSelection() {
        prefs.edit().putString("statisticsName",statisticsName).putString("statisticsData",statisticsData).putString("statisticsKind",statisticsKind)
            .putString("statisticsRegression",statisticsRegression).putString("statisticsPlot",statisticsPlot).putBoolean("statisticsCsv",statisticsCsv)
            .putString("statisticsCustomFormula",statisticsCustomFormula).putString("statisticsCustomVariable",statisticsCustomVariable).putString("statisticsCustomInitials",statisticsCustomInitials)
            .putString("statisticsSelected",statisticsSelected).putBoolean("statisticsIsNew",statisticsIsNew).apply()
    }

    fun clearRegression() {regressionCurve=emptyList();regressionFit="";regressionData="";regressionMode="";regressionCorrelation=null;regressionParameters=emptyList();regressionBusy=false}

    fun writeTo(editor:SharedPreferences.Editor) {
        editor.putString("dataSets",dataSets.toString())
            .putString("statisticsName",statisticsName).putString("statisticsData",statisticsData).putString("statisticsKind",statisticsKind)
            .putString("statisticsRegression",statisticsRegression).putString("statisticsPlot",statisticsPlot).putString("statisticsSelected",statisticsSelected)
            .putString("statisticsCustomFormula",statisticsCustomFormula).putString("statisticsCustomVariable",statisticsCustomVariable).putString("statisticsCustomInitials",statisticsCustomInitials)
            .putBoolean("statisticsIsNew",statisticsIsNew).putBoolean("statisticsCsv",statisticsCsv)
            .putString("regressionFit",regressionFit).putString("regressionData",regressionData)
            .putString("regressionMode",regressionMode).putString("regressionCorrelation",regressionCorrelation?.toString())
            .putString("regressionParameters",JSONArray(regressionParameters.map {JSONArray().put(it.first).put(it.second)}).toString())
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

    fun editSource(source:String,start:Int=source.length,end:Int=start):Boolean {
        val changed=source!=pythonSource
        pythonSource=source
        pythonSelectionStart=start.coerceIn(0,source.length)
        pythonSelectionEnd=end.coerceIn(0,source.length)
        if(changed)pythonDirty=true
        return changed
    }

    fun insert(snippet:String,inside:Int=snippet.length):Boolean {
        val a=minOf(pythonSelectionStart,pythonSelectionEnd).coerceIn(0,pythonSource.length)
        val b=maxOf(pythonSelectionStart,pythonSelectionEnd).coerceIn(a,pythonSource.length)
        val source=pythonSource.substring(0,a)+snippet+pythonSource.substring(b)
        val cursor=a+inside.coerceIn(0,snippet.length)
        return editSource(source,cursor,cursor)
    }

    fun newFile() {
        pythonSource="";pythonSelectionStart=0;pythonSelectionEnd=0;pythonFileName="untitled.py";pythonUri="";pythonDirty=false
        clearRun()
    }

    fun openFile(source:String,name:String,uri:String) {
        pythonSource=source;pythonSelectionStart=source.length;pythonSelectionEnd=source.length
        pythonFileName=name;pythonUri=uri;pythonDirty=false
        clearRun()
    }

    fun savedFile(name:String,uri:String) {pythonFileName=name;pythonUri=uri;pythonDirty=false}

    private fun clearRun() {
        pythonOutput="";pythonError="";pythonHasRun=false;pythonInputPrompt=null;pythonInputSubmit=null
    }

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

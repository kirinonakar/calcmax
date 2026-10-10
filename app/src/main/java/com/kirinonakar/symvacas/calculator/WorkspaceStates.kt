package com.kirinonakar.symvacas.calculator

import android.content.SharedPreferences
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sin
import kotlin.math.PI

private fun SharedPreferences.jsonObject(key:String):JSONObject =
    runCatching {JSONObject(getString(key,"{}") ?: "{}")}.getOrDefault(JSONObject())

/** Graph workspace state and its existing calculator.xml representation. */
internal class GraphState(private val prefs:SharedPreferences) {
    val sources=prefs.jsonObject("graphSources")
    var sequenceInitials by mutableStateOf(prefs.getString("sequenceInitials","0,1") ?: "0,1")
    var differentialInitials by mutableStateOf(prefs.getString("differentialInitials","1") ?: "1")
    var differentialT0 by mutableStateOf(prefs.getString("differentialT0","0") ?: "0")
    private val restoredKind=prefs.getString("graphKind","cartesian") ?: "cartesian"
    var graphKind by mutableStateOf(if(restoredKind=="implicit")"cartesian" else restoredKind)
    var graphSource by mutableStateOf(sources.optString(restoredKind,prefs.getString("graphSource","sin(x)\ncos(x)") ?: "sin(x)\ncos(x)"))
    var xMin by mutableDoubleStateOf(prefs.getString("xMin","-10")?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: -10.0)
    var xMax by mutableDoubleStateOf(prefs.getString("xMax","10")?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: 10.0)
    var yMin by mutableDoubleStateOf(prefs.getString("yMin","-5")?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: -5.0)
    var yMax by mutableDoubleStateOf(prefs.getString("yMax","5")?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: 5.0)
    var zMin by mutableStateOf(prefs.getString("zMin",null)?.toDoubleOrNull()?.takeIf(Double::isFinite))
    var zMax by mutableStateOf(prefs.getString("zMax",null)?.toDoubleOrNull()?.takeIf(Double::isFinite))
    var surfaceRenderMode by mutableStateOf(prefs.getString("surfaceRenderMode","surface")?.takeIf {it in listOf("wireframe","surface","surface-wireframe")} ?: "surface")
    var surfaceColor by mutableStateOf(prefs.getString("surfaceColor","#007b68")?.takeIf {it.matches(Regex("#[0-9a-fA-F]{6}"))} ?: "#007b68")
    var surfaceSamples by mutableIntStateOf(prefs.getInt("surfaceSamples",26).coerceIn(12,96))
    var surfaceAutoDensity by mutableStateOf(prefs.getBoolean("surfaceAutoDensity",true))
    var surfaceZoom by mutableFloatStateOf(prefs.getFloat("surfaceZoom",1f).takeIf {it.isFinite()}?.coerceIn(.4f,3f) ?: 1f)
    var graphData by mutableStateOf<JSONObject?>(null)
    var graphResultSignature:String?=null
    var graphDerivativeSelected by mutableStateOf<Int?>(null)
    var graphSecondDerivativeSelected by mutableStateOf<Int?>(null)
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
    private val animationOffsets=mutableMapOf<String,Double>()
    var radianAxis by mutableStateOf(prefs.getBoolean("radianAxis",false))
    private data class InputSnapshot(val source:String,val derivative:Int?,val secondDerivative:Int?,val parameters:Map<String,GraphParameter>)
    private val inputUndo=mutableStateMapOf<String,List<InputSnapshot>>()
    val canUndoInput get()=inputUndo[graphKind].orEmpty().isNotEmpty()

    fun rememberInput() {
        val history=inputUndo[graphKind].orEmpty()
        inputUndo[graphKind]=(history+InputSnapshot(graphSource,graphDerivativeSelected,graphSecondDerivativeSelected,graphParameters)).takeLast(100)
    }

    fun undoInput():Boolean {
        val history=inputUndo[graphKind].orEmpty()
        val previous=history.lastOrNull() ?: return false
        inputUndo[graphKind]=history.dropLast(1)
        clearAnalysis()
        graphSource=previous.source;graphDerivativeSelected=previous.derivative;graphParameters=previous.parameters
        graphSecondDerivativeSelected=previous.secondDerivative
        graphAnimating=false
        return true
    }

    private fun loadParameters():Map<String,GraphParameter> = runCatching {
        val stored=prefs.jsonObject("graphParameters")
        stored.keys().asSequence().associateWith {name->
            val entry=stored.optJSONObject(name)
            val low=entry?.optDouble("min",-5.0)?.takeIf(Double::isFinite) ?: -5.0
            val high=entry?.optDouble("max",5.0)?.takeIf(Double::isFinite) ?: 5.0
            val value=entry?.optDouble("value",1.0)?.takeIf(Double::isFinite) ?: 1.0
            val animate=entry?.optBoolean("animate",true) ?: true
            if(low<high)GraphParameter(value.coerceIn(low,high),low,high,animate) else GraphParameter(1.0,-5.0,5.0,animate)
        }
    }.getOrDefault(emptyMap())

    fun updateSource(source:String) {
        if(source==graphSource)return
        rememberInput()
        graphSource=source
        clearAnalysis(clearGraph=graphKind!="surface")
    }

    fun sourceForKind(kind:String):String = if(kind==graphKind)graphSource else sources.optString(kind,when(kind){
        "implicit"->"x^2+y^2=1"
        "parametric"->"[cos(t),sin(t)]";"polar"->"2*cos(3*t)";"sequence"->"n\nu(n-1)+u(n-2)"
        "space"->"C(t)=4*(sin(t),cos(t),0.6*sin(2*t))"
        "surface"->"sin(sqrt(x^2+y^2))";"differential"->"y-t";else->"sin(x)\ncos(x)"
    })

    fun changeKind(kind:String):Boolean {
        if(kind=="implicit")return changeKind("cartesian")
        if(kind==graphKind)return false
        sources.put(graphKind,graphSource)
        val nextSource=sourceForKind(kind)
        graphKind=kind
        graphSource=nextSource
        clearAnalysis()
        graphAnimating=false
        if(kind=="space") {
            parameterMin=0.0;parameterMax=2*PI;xMin=-5.0;xMax=5.0;yMin=-5.0;yMax=5.0;zMin=null;zMax=null
        } else if(kind=="sequence") {
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

    private fun clearAnalysis(clearGraph:Boolean=true) {
        graphDerivativeSelected=null;graphSecondDerivativeSelected=null;graphAnalysis=null;trace=null;shadedInterval=null
        if(clearGraph) {graphData=null;graphResultSignature=null}
    }

    fun applyPlotResponse(response:JSONObject,signature:String) {
        if(response.optBoolean("ok")) {
            graphData=response;graphResultSignature=signature
            syncParameters(response.optJSONArray("parameters"))
        } else if(graphKind!="surface") {
            graphData=null;graphResultSignature=null
        }
    }

    fun parameterPayload():JSONObject = JSONObject().also {payload->graphParameters.forEach {(name,spec)->payload.put(name,spec.value)}}

    fun resetSurfaceRanges() {
        val extent=if(graphKind=="space")5.0 else 3.0
        xMin=-extent;xMax=extent;yMin=-extent;yMax=extent;zMin=null;zMax=null
    }

    fun syncParameters(names:JSONArray?) {
        val next=(0 until (names?.length() ?: 0)).mapNotNull {names?.optString(it)}.filter(String::isNotBlank).distinct().sorted()
            .associateWith {name->graphParameters[name] ?: GraphParameter(1.0,-5.0,5.0)}
        if(next!=graphParameters)graphParameters=next
    }

    fun setParameter(name:String,value:Double,centerRange:Boolean=false) {
        val spec=graphParameters[name] ?: return
        if(!value.isFinite() || (centerRange && abs(value)>1e9))return
        val half=(spec.max-spec.min)/2
        val next=if(centerRange)spec.copy(value=value,min=value-half,max=value+half)
            else spec.copy(value=value.coerceIn(spec.min,spec.max))
        if(next!=spec) {
            graphParameters=graphParameters+(name to next)
            invalidateParameterAnalysis()
        }
        if(graphAnimating)alignAnimation(name)
    }

    fun setParameterRange(name:String,low:Double,high:Double):Boolean {
        val spec=graphParameters[name] ?: return false
        if(!low.isFinite()||!high.isFinite()||low>=high||abs(low)>1e9||abs(high)>1e9)return false
        graphParameters=graphParameters+(name to spec.copy(min=low,max=high,value=low+(high-low)/2))
        invalidateParameterAnalysis()
        if(graphAnimating)alignAnimation(name)
        return true
    }

    fun resetParameters(name:String?=null) {
        graphParameters=graphParameters.mapValues {(key,spec)->if(name==null||key==name)spec.copy(value=1.0,min=-5.0,max=5.0) else spec}
        invalidateParameterAnalysis()
        if(graphAnimating)graphParameters.keys.filter {name==null||it==name}.forEach(::alignAnimation)
    }
    private fun invalidateParameterAnalysis() {graphAnalysis=null;trace=null;shadedInterval=null}

    fun setParameterAnimation(name:String,enabled:Boolean) {
        val spec=graphParameters[name] ?: return
        graphParameters=graphParameters+(name to spec.copy(animate=enabled))
        if(graphAnimating&&enabled)alignAnimation(name)
    }

    private fun alignAnimation(name:String) {
        val spec=graphParameters[name] ?: return
        animationOffsets[name]=asin((2*(spec.value-spec.min)/(spec.max-spec.min)-1).coerceIn(-1.0,1.0))-animationPhase
    }

    fun beginAnimation() {
        animationPhase=0.0;animationOffsets.clear()
        graphParameters.keys.forEach(::alignAnimation)
    }

    fun advanceAnimation(seconds:Double):Boolean {
        animationPhase=(animationPhase+seconds.coerceIn(0.0,.1))%(2*PI)
        if(graphParameters.values.none {it.animate})return false
        graphParameters=graphParameters.mapValues {(name,spec)->
            if(!spec.animate)spec else {
                if(name !in animationOffsets)alignAnimation(name)
                val swing=(sin(animationPhase+animationOffsets.getValue(name))+1)/2
                spec.copy(value=spec.min+(spec.max-spec.min)*swing)
            }
        }
        return true
    }

    fun animateParameters(swing:Double):Boolean {
        if(graphParameters.values.none {it.animate})return false
        graphParameters=graphParameters.mapValues {(_,spec)->if(spec.animate)spec.copy(value=spec.min+(spec.max-spec.min)*swing) else spec}
        return true
    }

    fun writeTo(editor:SharedPreferences.Editor) {
        val parameters=JSONObject()
        graphParameters.forEach {(name,spec)->parameters.put(name,JSONObject().put("value",spec.value).put("min",spec.min).put("max",spec.max).put("animate",spec.animate))}
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
    private var sectionExpansion by mutableStateOf(prefs.jsonObject("statisticsSectionExpansion"))

    fun sectionExpanded(section:String):Boolean = sectionExpansion.opt(section) as? Boolean ?: (section=="summary")

    fun setSectionExpanded(section:String,expanded:Boolean) {
        sectionExpansion=JSONObject(sectionExpansion.toString()).put(section,expanded)
        prefs.edit().putString("statisticsSectionExpansion",sectionExpansion.toString()).apply()
    }

    fun collapseSections() {
        val next=JSONObject(sectionExpansion.toString())
        for(section in listOf("summary","visualize","regression","analysis","preparation","models","tests","advanced"))next.put(section,false)
        sectionExpansion=next
        prefs.edit().putString("statisticsSectionExpansion",next.toString()).apply()
    }

    var dataSets by mutableStateOf(prefs.jsonObject("dataSets"))
    var regressionCurve by mutableStateOf(loadRegressionCurve())
    var regressionFit by mutableStateOf(prefs.getString("regressionFit","") ?: "")
    var regressionData by mutableStateOf(prefs.getString("regressionData","") ?: "")
    var regressionMode by mutableStateOf(prefs.getString("regressionMode",prefs.getString("statisticsRegression","linear")) ?: "")
    var regressionCorrelation by mutableStateOf(prefs.getString("regressionCorrelation",null)?.toDoubleOrNull()?.takeIf(Double::isFinite))
    var regressionParameters by mutableStateOf(loadRegressionParameters())
    var regressionReport by mutableStateOf(runCatching {JSONObject(prefs.getString("regressionReport",null) ?: "null")}.getOrNull()?.takeUnless {it.optString("method")=="hmc"})
    var regressionResponseColumn by mutableStateOf(prefs.getInt("regressionResponseColumn",-1).takeIf {it>=0})
    var regressionBusy by mutableStateOf(false)
    var statisticsName by mutableStateOf(prefs.getString("statisticsName","D1") ?: "D1")
    var statisticsData by mutableStateOf(prefs.getString("statisticsData","") ?: "")
    var statisticsKind by mutableStateOf(prefs.getString("statisticsKind","list") ?: "list")
    var statisticsRegression by mutableStateOf(prefs.getString("statisticsRegression","linear") ?: "linear")
    var statisticsRegularization by mutableStateOf(prefs.getString("statisticsRegularization","none") ?: "none")
    var statisticsL1Ratio by mutableStateOf(prefs.getString("statisticsL1Ratio","0.5") ?: "0.5")
    var statisticsBayesianMethod by mutableStateOf((prefs.getString("statisticsBayesianMethod","analytic") ?: "analytic").let {if(it=="hmc")"nuts" else it})
    var statisticsNutsSamples by mutableStateOf(prefs.getString("statisticsNutsSamples",prefs.getString("statisticsHmcSamples","500")) ?: "500")
    var statisticsNutsWarmup by mutableStateOf(prefs.getString("statisticsNutsWarmup",prefs.getString("statisticsHmcWarmup","500")) ?: "500")
    // Leapfrog count has no equivalent tree depth; start NUTS at its default.
    var statisticsNutsMaxDepth by mutableStateOf(prefs.getString("statisticsNutsMaxDepth","8") ?: "8")
    var statisticsNutsSeed by mutableStateOf(prefs.getString("statisticsNutsSeed",prefs.getString("statisticsHmcSeed","0")) ?: "0")
    var statisticsNutsChains by mutableStateOf(prefs.getString("statisticsNutsChains",prefs.getString("statisticsHmcChains","2")) ?: "2")
    var statisticsBayesianPriorSD by mutableStateOf(prefs.getString("statisticsBayesianPriorSD","2.5") ?: "2.5")
    var statisticsBayesianLevel by mutableStateOf(prefs.getString("statisticsBayesianLevel","0.95") ?: "0.95")
    var statisticsBayesianShape by mutableStateOf(prefs.getString("statisticsBayesianShape","2") ?: "2")
    var statisticsBayesianScale by mutableStateOf(prefs.getString("statisticsBayesianScale","1") ?: "1")
    var statisticsLassoAlpha by mutableStateOf(prefs.getString("statisticsLassoAlpha","0.1") ?: "0.1")
    var statisticsForestTask by mutableStateOf(prefs.getString("statisticsForestTask","auto") ?: "auto")
    var statisticsForestTrees by mutableStateOf(prefs.getString("statisticsForestTrees","100") ?: "100")
    var statisticsForestDepth by mutableStateOf(prefs.getString("statisticsForestDepth","10") ?: "10")
    var statisticsForestSeed by mutableStateOf(prefs.getString("statisticsForestSeed","0") ?: "0")
    var statisticsPolynomialDegree by mutableStateOf(prefs.getString("statisticsPolynomialDegree","3") ?: "3")
    var statisticsLogisticResponse by mutableStateOf(prefs.getString("statisticsLogisticResponse","") ?: "")
    var statisticsCustomFormula by mutableStateOf((prefs.getString("statisticsCustomFormula","exp(-b*ADC)") ?: "exp(-b*ADC)").let {if(it=="S0*exp(-b*ADC)")"exp(-b*ADC)" else it})
    var statisticsCustomVariable by mutableStateOf(prefs.getString("statisticsCustomVariable","b") ?: "b")
    var statisticsCustomInitials by mutableStateOf(prefs.getString("statisticsCustomInitials","") ?: "")
    var statisticsPlotGrouping by mutableStateOf(prefs.getString("statisticsPlotGrouping","columns")?.takeIf {it in listOf("columns","first","last")} ?: "columns")
    var statisticsHeatMapMode by mutableStateOf(prefs.getString("statisticsHeatMapMode","raw")?.takeIf {it in listOf("raw","zrow","zcolumn","correlation")} ?: "raw")
    var statisticsHeatMapCorrelation by mutableStateOf(prefs.getString("statisticsHeatMapCorrelation","pearson")?.takeIf {it in listOf("pearson","spearman","kendall")} ?: "pearson")
    var statisticsHeatMapXColumns by mutableStateOf(prefs.getString("statisticsHeatMapXColumns","") ?: "")
    var statisticsHeatMapYColumns by mutableStateOf(prefs.getString("statisticsHeatMapYColumns","") ?: "")
    var statisticsHeatMapClustering by mutableStateOf(prefs.getBoolean("statisticsHeatMapClustering",false))
    var statisticsHeatMapFit by mutableStateOf(prefs.getBoolean("statisticsHeatMapFit",false))
    var statisticsPlotOrientation by mutableStateOf(prefs.getString("statisticsPlotOrientation","horizontal")?.takeIf {it in listOf("horizontal","vertical")} ?: "horizontal")
    var statisticsAutoColumns by mutableStateOf(prefs.getBoolean("statisticsAutoColumns",false))
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
        require(kind in listOf("list","xy","xyz")||Regex("columns:[0-9]+").matches(kind)&&kind.substringAfter(":").toIntOrNull() in 1..100) {"Unknown dataset type"}
        dataSets=JSONObject(dataSets.toString()).put(name,JSONObject().put("csv",csv).put("kind",kind))
    }

    fun deleteDataSet(name:String) {dataSets=JSONObject(dataSets.toString()).apply {remove(name)}}

    fun updateSelection(name:String,data:String,kind:String,regression:String,plot:String,csv:Boolean,selected:String,isNew:Boolean,customFormula:String,customVariable:String,customInitials:String,polynomialDegree:String,logisticResponse:String,lassoAlpha:String,forestTrees:String,forestDepth:String,forestSeed:String,regularization:String,l1Ratio:String,forestTask:String,bayesianPriorSD:String,bayesianLevel:String,bayesianShape:String,bayesianScale:String,bayesianMethod:String,nutsSamples:String,nutsWarmup:String,nutsMaxDepth:String,nutsSeed:String,nutsChains:String) {
        statisticsBayesianMethod=bayesianMethod
        statisticsNutsSamples=nutsSamples
        statisticsNutsWarmup=nutsWarmup
        statisticsNutsMaxDepth=nutsMaxDepth
        statisticsNutsSeed=nutsSeed
        statisticsNutsChains=nutsChains
        statisticsBayesianPriorSD=bayesianPriorSD;statisticsBayesianLevel=bayesianLevel;statisticsBayesianShape=bayesianShape;statisticsBayesianScale=bayesianScale
        statisticsForestTask=forestTask;statisticsRegularization=regularization;statisticsL1Ratio=l1Ratio;statisticsLassoAlpha=lassoAlpha;statisticsForestTrees=forestTrees;statisticsForestDepth=forestDepth;statisticsForestSeed=forestSeed
        statisticsName=name;statisticsData=data;statisticsKind=kind;statisticsRegression=regression
        statisticsLogisticResponse=logisticResponse;statisticsPolynomialDegree=polynomialDegree;statisticsCustomFormula=customFormula;statisticsCustomVariable=customVariable;statisticsCustomInitials=customInitials
        statisticsPlot=plot;statisticsCsv=csv;statisticsSelected=selected;statisticsIsNew=isNew
    }

    fun saveSelection() {
        prefs.edit().putString("statisticsName",statisticsName).putString("statisticsData",statisticsData).putString("statisticsKind",statisticsKind)
            .putString("statisticsPlotOrientation",statisticsPlotOrientation)
            .putBoolean("statisticsAutoColumns",statisticsAutoColumns)
            .putString("statisticsBayesianMethod",statisticsBayesianMethod).putString("statisticsNutsSamples",statisticsNutsSamples).putString("statisticsNutsWarmup",statisticsNutsWarmup).putString("statisticsNutsMaxDepth",statisticsNutsMaxDepth).putString("statisticsNutsSeed",statisticsNutsSeed).putString("statisticsNutsChains",statisticsNutsChains).putString("statisticsBayesianPriorSD",statisticsBayesianPriorSD).putString("statisticsBayesianLevel",statisticsBayesianLevel).putString("statisticsBayesianShape",statisticsBayesianShape).putString("statisticsBayesianScale",statisticsBayesianScale).putString("statisticsRegularization",statisticsRegularization).putString("statisticsL1Ratio",statisticsL1Ratio).putString("statisticsLassoAlpha",statisticsLassoAlpha).putString("statisticsForestTask",statisticsForestTask).putString("statisticsForestTrees",statisticsForestTrees).putString("statisticsForestDepth",statisticsForestDepth).putString("statisticsForestSeed",statisticsForestSeed).putString("statisticsLogisticResponse",statisticsLogisticResponse).putString("statisticsPolynomialDegree",statisticsPolynomialDegree).putString("statisticsRegression",statisticsRegression).putString("statisticsPlot",statisticsPlot).putString("statisticsPlotGrouping",statisticsPlotGrouping).putString("statisticsHeatMapMode",statisticsHeatMapMode).putString("statisticsHeatMapCorrelation",statisticsHeatMapCorrelation).putString("statisticsHeatMapXColumns",statisticsHeatMapXColumns).putString("statisticsHeatMapYColumns",statisticsHeatMapYColumns).putBoolean("statisticsHeatMapClustering",statisticsHeatMapClustering).putBoolean("statisticsHeatMapFit",statisticsHeatMapFit).putBoolean("statisticsCsv",statisticsCsv)
            .putString("statisticsCustomFormula",statisticsCustomFormula).putString("statisticsCustomVariable",statisticsCustomVariable).putString("statisticsCustomInitials",statisticsCustomInitials)
            .putString("statisticsSelected",statisticsSelected).putBoolean("statisticsIsNew",statisticsIsNew).apply()
    }

    fun clearRegression() {regressionCurve=emptyList();regressionFit="";regressionData="";regressionMode="";regressionCorrelation=null;regressionParameters=emptyList();regressionReport=null;regressionResponseColumn=null;regressionBusy=false}

    fun writeTo(editor:SharedPreferences.Editor) {
        editor.putString("statisticsSectionExpansion",sectionExpansion.toString()).putString("dataSets",dataSets.toString())
            .putBoolean("statisticsAutoColumns",statisticsAutoColumns)
            .putString("statisticsPlotOrientation",statisticsPlotOrientation)
            .putString("statisticsName",statisticsName).putString("statisticsData",statisticsData).putString("statisticsKind",statisticsKind)
            .putString("statisticsBayesianMethod",statisticsBayesianMethod).putString("statisticsNutsSamples",statisticsNutsSamples).putString("statisticsNutsWarmup",statisticsNutsWarmup).putString("statisticsNutsMaxDepth",statisticsNutsMaxDepth).putString("statisticsNutsSeed",statisticsNutsSeed).putString("statisticsNutsChains",statisticsNutsChains).putString("statisticsBayesianPriorSD",statisticsBayesianPriorSD).putString("statisticsBayesianLevel",statisticsBayesianLevel).putString("statisticsBayesianShape",statisticsBayesianShape).putString("statisticsBayesianScale",statisticsBayesianScale).putString("statisticsRegularization",statisticsRegularization).putString("statisticsL1Ratio",statisticsL1Ratio).putString("statisticsLassoAlpha",statisticsLassoAlpha).putString("statisticsForestTask",statisticsForestTask).putString("statisticsForestTrees",statisticsForestTrees).putString("statisticsForestDepth",statisticsForestDepth).putString("statisticsForestSeed",statisticsForestSeed).putString("statisticsLogisticResponse",statisticsLogisticResponse).putString("statisticsPolynomialDegree",statisticsPolynomialDegree).putString("statisticsRegression",statisticsRegression).putString("statisticsPlot",statisticsPlot).putString("statisticsPlotGrouping",statisticsPlotGrouping).putString("statisticsHeatMapMode",statisticsHeatMapMode).putString("statisticsHeatMapCorrelation",statisticsHeatMapCorrelation).putString("statisticsHeatMapXColumns",statisticsHeatMapXColumns).putString("statisticsHeatMapYColumns",statisticsHeatMapYColumns).putBoolean("statisticsHeatMapClustering",statisticsHeatMapClustering).putBoolean("statisticsHeatMapFit",statisticsHeatMapFit).putString("statisticsSelected",statisticsSelected)
            .putString("statisticsCustomFormula",statisticsCustomFormula).putString("statisticsCustomVariable",statisticsCustomVariable).putString("statisticsCustomInitials",statisticsCustomInitials)
            .putBoolean("statisticsIsNew",statisticsIsNew).putBoolean("statisticsCsv",statisticsCsv)
            .putString("regressionFit",regressionFit).putString("regressionData",regressionData)
            .putString("regressionMode",regressionMode).putString("regressionCorrelation",regressionCorrelation?.toString())
            .putString("regressionReport",regressionReport?.toString()).putInt("regressionResponseColumn",regressionResponseColumn ?: -1)
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

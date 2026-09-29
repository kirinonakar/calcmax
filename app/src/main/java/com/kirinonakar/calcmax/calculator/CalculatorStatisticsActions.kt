package com.kirinonakar.calcmax.calculator

import androidx.lifecycle.viewModelScope
import com.kirinonakar.calcmax.math.Parser
import kotlinx.coroutines.launch
import org.json.JSONObject

internal object CalculatorStatisticsActions {
    fun CalculatorModel.performSaveDataSet(name:String,csv:String,kind:String) {
        try {
            statisticsState.saveDataSet(name,csv,kind)
            error="";save()
        } catch(e:Exception) { error=e.message ?: "Could not save dataset" }
    }
    fun CalculatorModel.performDeleteDataSet(name:String) {
        statisticsState.deleteDataSet(name)
        save()
    }
    fun CalculatorModel.performSaveStatistics(name:String,data:String,kind:String,regression:String,plot:String,csv:Boolean,selected:String,isNew:Boolean,customFormula:String,customVariable:String,customInitials:String) {
        statisticsState.updateSelection(name,data,kind,regression,plot,csv,selected,isNew,customFormula,customVariable,customInitials)
        statisticsState.saveSelection()
    }
    fun CalculatorModel.performFitRegression(source: String, data: String) {
        regressionJob?.cancel()
        val tree=try {Parser(source).parse()} catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
        val fittedMode=tree.args.getOrNull(1)?.value ?: "linear"
        regressionJob=viewModelScope.launch {
            statisticsState.regressionBusy=true;error=""
            try {
                val response=engine.execute(request().put("tree",JSONObject(tree.json())))
                if(response.optBoolean("ok")) {
                    result=response;dmsDisplay=false;dmsConversion=false
                    val array=response.optJSONArray("curve")
                    statisticsState.regressionCurve=if(array==null)emptyList() else (0 until array.length()).mapNotNull {index->array.optJSONArray(index)?.let {pair->pair.optDouble(0) to pair.optDouble(1)}}
                    statisticsState.regressionFit=response.optString("exact");statisticsState.regressionData=data;statisticsState.regressionMode=fittedMode
                    statisticsState.regressionCorrelation=response.optDouble("correlation",Double.NaN).takeIf(Double::isFinite)
                    val parameters=response.optJSONArray("parameters")
                    statisticsState.regressionParameters=if(parameters==null)emptyList() else (0 until parameters.length()).mapNotNull {index->
                        parameters.optJSONArray(index)?.let {pair->pair.optString(0) to pair.optString(1)}
                    }
                    val next=JSONObject(variables.toString())
                    if(response.has("resultAst")) next.put("Ans",response.getJSONObject("resultAst")) else next.remove("Ans")
                    variables=next
                    appendHistory(HistoryEntry(System.currentTimeMillis(),source,response.optString("exact"),response.optString("decimal"),mode,
                        inputTree=tree.json(),response=response.toString()))
                    save()
                } else {statisticsState.regressionCurve=emptyList();statisticsState.regressionFit="";statisticsState.regressionData="";statisticsState.regressionMode="";statisticsState.regressionCorrelation=null;statisticsState.regressionParameters=emptyList();error=response.optString("error","Math ERROR")}
            } finally {statisticsState.regressionBusy=false}
        }
    }
    fun CalculatorModel.performClearRegression() {
        regressionJob?.cancel()
        statisticsState.clearRegression()
        save()
    }
}

package com.kirinonakar.symvacas.ui

internal fun statisticsRegressionColumns(kind:String):List<String> = statisticsColumnNames(kind).takeIf {it.size>1} ?: emptyList()

/** The engine takes the response last; the visible table keeps its original order. */
internal fun statisticsRegressionTable(rows:List<List<String>>,kind:String,mode:String,responseColumn:Int=statisticsRegressionColumns(kind).lastIndex):String? {
    val columns=statisticsRegressionColumns(kind)
    if(columns.isEmpty()||responseColumn !in columns.indices)return null
    val order=if(mode in listOf("multiple","logistic","polynomial","ridge","lasso","elasticnet","logisticridge","logisticlasso","logisticelasticnet","randomforest","randomforestclassifier","randomforestregressor"))columns.indices.filter {it!=responseColumn}+responseColumn else columns.indices.toList()
    val complete=rows.filter {it.size>=columns.size&&it.take(columns.size).all(String::isNotBlank)}
    if(complete.size<if(mode in listOf("multiple","logistic"))columns.size+1 else 2)return null
    return complete.joinToString(",","[","]") {row->order.joinToString(",","[","]"){row[it]}}
}

internal fun statisticsRegressionVariables(kind:String,responseColumn:Int):Map<String,String> {
    val predictors=statisticsRegressionColumns(kind).filterIndexed {index,_->index!=responseColumn}
    return predictors.mapIndexed {index,name->(if(statisticsColumnCount(kind)==2)"x" else "x${index+1}") to name}.toMap()
}

/** Coefficient IDs stay stable in saved reports; only their visible labels change. */
internal fun statisticsRegressionParameterLabels(kind:String,mode:String,responseColumn:Int,csv:String=""):Map<String,String> {
    val predictors=statisticsColumnLabels(csv,kind).filterIndexed {index,_->index!=responseColumn}
    if(predictors.isEmpty())return emptyMap()
    return when(mode) {
        "multiple","logistic","ridge","lasso","elasticnet","logisticridge","logisticlasso","logisticelasticnet","randomforest","randomforestclassifier","randomforestregressor"->mapOf("b0" to "Intercept")+predictors.mapIndexed {index,name->"b${index+1}" to name}
        "linear","quadratic","polynomial"->mapOf("b0" to "Intercept")+(1..10).associate {power->
            "b$power" to (predictors.first()+if(power==1)"" else power.toString().map {"⁰¹²³⁴⁵⁶⁷⁸⁹"[it.digitToInt()]}.joinToString(""))
        }
        else->emptyMap()
    }
}

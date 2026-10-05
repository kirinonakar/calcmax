package com.kirinonakar.symvacas.ui

internal fun statisticsRegressionColumns(kind:String):List<String> = when(kind) {
    "xy"->listOf("x","y")
    "xyz"->listOf("x","y","z")
    else->emptyList()
}

/** The engine takes the response last; the visible table keeps its original order. */
internal fun statisticsRegressionTable(rows:List<List<String>>,kind:String,mode:String,responseColumn:Int=statisticsRegressionColumns(kind).lastIndex):String? {
    val columns=statisticsRegressionColumns(kind)
    if(columns.isEmpty()||responseColumn !in columns.indices)return null
    val order=if(mode in listOf("multiple","logistic"))columns.indices.filter {it!=responseColumn}+responseColumn else columns.indices.toList()
    val complete=rows.filter {it.size>=columns.size&&it.take(columns.size).all(String::isNotBlank)}
    if(complete.size<if(mode in listOf("multiple","logistic"))columns.size+1 else 2)return null
    return complete.joinToString(",","[","]") {row->order.joinToString(",","[","]"){row[it]}}
}

internal fun statisticsRegressionVariables(kind:String,responseColumn:Int):Map<String,String> {
    val predictors=statisticsRegressionColumns(kind).filterIndexed {index,_->index!=responseColumn}
    return predictors.mapIndexed {index,name->(if(kind=="xy")"x" else "x${index+1}") to name}.toMap()
}

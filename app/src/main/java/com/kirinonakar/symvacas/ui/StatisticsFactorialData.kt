package com.kirinonakar.symvacas.ui

internal data class StatisticsFactorialData(val encoded:List<List<String>>,val labels:Map<String,String>,val categorical:List<String> = emptyList())
internal fun statisticsFactorialData(rows:List<List<String>>,opts:Map<String,String>,columns:List<String> = emptyList()):StatisticsFactorialData {
    val width=rows.maxOfOrNull {it.size} ?: 0
    fun col(key:String):Int {val raw=opts[key]?.toIntOrNull() ?: -2;val at=if(raw==-1)width-1 else raw;require(at in 0 until width) {"Choose valid data columns"};return at}
    fun multiple(key:String):List<Int> = if(opts[key]=="auto")(0 until width).filter {it!=col("response")} else opts[key].orEmpty().split(',').filter(String::isNotBlank).map {it.toIntOrNull() ?: -1}
    fun cell(row:List<String>,at:Int)=row.getOrElse(at){""}.trim()
    val labels=mutableMapOf("response" to columns.getOrElse(col("response")){"Response"});val selected:List<Int>;val categorical:List<Int>
    if(opts.containsKey("factorA")) {
        if(opts["layout"]=="columns") {
            val indices=if(opts["columns"]=="auto")(0 until width).toList() else opts["columns"].orEmpty().split(',').filter(String::isNotBlank).map {it.toIntOrNull() ?: -1}
            val b=opts["levelsB"]?.toIntOrNull() ?: 0
            require(b>=2&&indices.size%b==0&&indices.size/b>=2) {"Cell columns must contain every factor combination"}
            require(indices.distinct().size==indices.size&&indices.all {it in 0 until width}) {"Choose distinct analysis columns"}
            val encoded=indices.flatMapIndexed {index,at->rows.map {cell(it,at)}.filter(String::isNotBlank).map {listOf((index/b+1).toString(),(index%b+1).toString(),it)}}
            indices.forEachIndexed {index,at->labels["cell:${index/b+1}:${index%b+1}"]=columns.getOrElse(at){"A${index/b+1} × B${index%b+1}"}}
            repeat(indices.size/b){labels["factor:A:${it+1}"]="A${it+1}"};repeat(b){labels["factor:B:${it+1}"]="B${it+1}"}
            return StatisticsFactorialData(encoded,labels)
        }
        selected=listOf(col("factorA"),col("factorB"),col("response"));categorical=selected.take(2)
        labels["factor:A"]=columns.getOrElse(selected[0]){"Factor A"};labels["factor:B"]=columns.getOrElse(selected[1]){"Factor B"}
    }else {
        val predictors=multiple("predictors");categorical=if(opts["categorical"]=="auto")predictors else multiple("categorical");selected=predictors+col("response")
        require(predictors.isNotEmpty()&&categorical.all {it in predictors}) {"Categorical columns must be selected predictors"}
        predictors.forEachIndexed {index,at->labels["x${index+1}"]=columns.getOrElse(at){"x${index+1}"}}
    }
    require(selected.distinct().size==selected.size&&selected.all {it in 0 until width}) {"Choose distinct analysis columns"}
    require(rows.all {row->selected.all {cell(row,it).isNotBlank()}}) {"Complete selected rows required"}
    val levels=categorical.associateWith {at->rows.map {cell(it,at)}.distinct()}
    val encoded=rows.map {row->selected.map {at->if(at in categorical)(levels.getValue(at).indexOf(cell(row,at))+1).toString() else cell(row,at)}}
    categorical.forEach {at->levels.getValue(at).forEachIndexed {index,value->labels[if(opts.containsKey("factorA"))"factor:${if(at==selected[0])"A" else "B"}:${index+1}" else "level:${selected.indexOf(at)+1}:${index+1}"]=value}}
    return StatisticsFactorialData(encoded,labels,categorical.map {(selected.indexOf(it)+1).toString()})
}

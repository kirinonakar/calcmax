package com.kirinonakar.symvacas.ui

internal data class StatisticsComparisonData(val labels:List<String>,val samples:List<List<String>>,val matrix:List<List<String>>,val omitted:Int=0)

internal fun statisticsComparisonData(rows:List<List<String>>,settings:Map<String,String>,all:Boolean=false,paired:Boolean=false,single:Boolean=false,strict:Boolean=false):StatisticsComparisonData {
    val width=rows.maxOfOrNull {it.size} ?: 0
    fun cell(row:List<String>,index:Int)=row.getOrElse(index){""}.trim()
    fun column(key:String,fallback:Int):Int {val index=settings[key]?.toIntOrNull() ?: fallback;require(index in 0 until width) {"Choose valid data columns"};return index}
    if(settings["grouping"]!="groups") {
        val columns=if(all) {val raw=settings["columns"] ?: "auto";if(raw=="auto")(0 until width).toList() else raw.split(',').filter(String::isNotBlank).map {it.toIntOrNull() ?: -1}}
            else if(single)listOf(column("first",0)) else listOf(column("first",0),column("second",1))
        require(columns.distinct().size==columns.size&&columns.all {it in 0 until width}) {"Choose distinct analysis columns"}
        var matrix=rows.map {row->columns.map {cell(row,it)}}
        if(paired){require(!strict||matrix.all {row->row.all(String::isNotBlank)}) {"Complete selected rows required"};matrix=matrix.filter {row->row.all(String::isNotBlank)}}
        val samples=columns.mapIndexed {index,at->if(paired)matrix.map {it[index]} else rows.map {cell(it,at)}.filter(String::isNotBlank)}
        return StatisticsComparisonData(columns.map(Int::toString),samples,matrix,if(paired)rows.size-matrix.size else 0)
    }
    val group=column("group",0);val value=column("value",1)
    require(group!=value) {"Roles must use different columns"}
    val labels=rows.map {cell(it,group)}.filter(String::isNotBlank).distinct()
    val first=settings["firstGroup"]?.takeIf {it in labels} ?: labels.firstOrNull().orEmpty()
    val second=settings["secondGroup"]?.takeIf {it in labels&&it!=first} ?: labels.firstOrNull {it!=first}.orEmpty()
    val selected=if(all)labels else if(single)listOf(first) else listOf(first,second)
    require(selected.all(String::isNotBlank)&&selected.distinct().size==selected.size) {"Choose distinct groups"}
    val members=selected.map {label->rows.filter {cell(it,group)==label}}
    var samples=members.map {groupRows->groupRows.map {cell(it,value)}.filter(String::isNotBlank)}
    var matrix=emptyList<List<String>>();var omitted=0
    if(paired) {
        if(settings["matching"]=="subject") {
            val subject=column("subject",0);require(subject!=group&&subject!=value) {"Roles must use different columns"}
            val maps=members.map {groupRows->linkedMapOf<String,String>().apply {groupRows.forEach {row->val id=cell(row,subject);require(id.isNotBlank()) {"Subject IDs are required for paired groups"};require(!containsKey(id)) {"Each subject must occur once per group"};put(id,cell(row,value))}}}
            val ids=maps.flatMap {it.keys}.distinct();matrix=ids.filter {id->maps.all {it[id]?.isNotBlank()==true}}.map {id->maps.map {it.getValue(id)}};omitted=ids.size-matrix.size
        }else {
            require(members.all {groupRows->groupRows.all {cell(it,value).isNotBlank()}}) {"Missing paired values require subject-ID matching"}
            require(samples.map {it.size}.distinct().size==1) {"Paired groups must have equal lengths"}
            matrix=samples.first().indices.map {index->samples.map {it[index]}}
        }
        samples=selected.indices.map {index->matrix.map {it[index]}}
    }
    return StatisticsComparisonData(selected,samples,matrix,omitted)
}

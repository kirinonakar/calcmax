package com.kirinonakar.symvacas.ui

import org.json.JSONObject

/** Construct the same selected-column analysis plan used by the Web form. */
internal fun guidedStatisticsCommand(definition:JSONObject,rows:List<List<String>>,settings:JSONObject=JSONObject()):String {
    if(!definition.has("controls"))return advancedStatisticsCommand(definition,rows)
    require(rows.any {row->row.any(String::isNotBlank)}) {"Enter data first"}
    val n=rows.maxOf {it.size};val id=definition.getString("id");val controls=definition.getJSONArray("controls")
    val opts=(0 until controls.length()).associate {index->val field=controls.getJSONObject(index);val key=field.getString("key");key to settings.optString(key,field.get("default").toString())}
    for(i in 0 until controls.length()) {
        val field=controls.getJSONObject(i)
        if(field.getString("type")=="choice") {
            val choices=field.getJSONArray("choices")
            require((0 until choices.length()).any {choices.getJSONObject(it).getString("id")==opts[field.getString("key")]}) {"Invalid analysis option"}
        }
    }
    fun col(key:String):Int {val raw=opts.getValue(key).toIntOrNull();val index=if(raw==-1)n-1 else raw;require(index!=null&&index in 0 until n) {"Choose valid data columns"};return index}
    fun vector(values:List<String>)=values.joinToString(",","[","]")
    fun table(values:List<List<String>>)=values.joinToString(",","[","]",transform=::vector)
    fun values(index:Int)=rows.map {it.getOrElse(index){""}.trim()}.filter(String::isNotBlank)
    fun multiple(key:String,excluded:List<Int> = emptyList()):List<Int> {
        val raw=opts.getValue(key);val indices=if(raw=="auto")(0 until n).filter {it !in excluded} else raw.split(',').filter(String::isNotBlank).map {it.toIntOrNull() ?: -1}
        require(indices.isNotEmpty()&&indices.distinct().size==indices.size&&indices.all {it in 0 until n&&it !in excluded}) {"Choose distinct analysis columns"}
        return indices
    }
    fun distinct(indices:List<Int>) {require(indices.distinct().size==indices.size) {"Roles must use different columns"}}
    fun complete(indices:List<Int>):List<List<String>> {distinct(indices);val selected=rows.map {row->indices.map {row.getOrElse(it){""}.trim()}};require(selected.all {row->row.all(String::isNotBlank)}) {"Complete selected rows required"};return selected}
    fun eventRows(indices:List<Int>):List<List<String>> {
        val selected=complete(listOf(col("time"),col("event"))+indices);val eventValue=opts.getValue("eventValue")
        require(eventValue.isNotBlank()) {"Enter the event value"}
        val states=selected.map {it[1]}.distinct();require(states.size<=2) {"Event column must have at most two values"}
        require(states.size==1||eventValue in states) {"Event value does not occur in the selected column"}
        return selected.map {row->listOf(row[0],if(row[1]==eventValue)"1" else "0")+row.drop(2)}
    }
    return when(id) {
        "padjust"->"padjust(${vector(values(col("column")))},${opts["method"]},${opts["alpha"]})"
        "levene","bartlett"->{
            val samples=if(opts["grouping"]=="groups") {val pairs=complete(listOf(col("group"),col("value")));pairs.map {it[0]}.distinct().map {label->pairs.filter {it[0]==label}.map {it[1]}}} else multiple("columns").map(::values)
            require(samples.size>=2) {"Choose at least two groups"};"$id(${samples.joinToString(",",transform=::vector)})"
        }
        "mcnemar"->{
            val pairs=complete(listOf(col("first"),col("second")))
            val counts=if(opts["layout"]=="pairs") {
                val labels=pairs.flatten().distinct();require(labels.size==2) {"Paired observations require the same two categories"}
                val bins=Array(2){IntArray(2)};pairs.forEach {bins[labels.indexOf(it[0])][labels.indexOf(it[1])]++};bins.map {row->row.map(Int::toString)}
            } else pairs.also {require(it.size==2) {"The count table needs exactly two rows"}}
            "mcnemar(${table(counts)},${opts["method"]})"
        }
        "kaplanmeier"->"kaplanmeier(${table(eventRows(emptyList()))},${opts["level"]})"
        "logrank"->{
            val selected=eventRows(listOf(col("group")));val labels=selected.map {it[2]}.distinct()
            require(labels.size==2) {"Log-rank requires exactly two groups"}
            "logrank(${labels.joinToString(",") {label->table(selected.filter {it[2]==label}.map {it.take(2)})}})"
        }
        "cox"->"cox(${table(eventRows(multiple("predictors",listOf(col("time"),col("event")))))})"
        "repeatedanova"->{val indices=multiple("columns");require(indices.size>=2) {"Choose at least two conditions"};"repeatedanova(${table(complete(indices))})"}
        "mixedmodel","gee"->{
            val subject=col("subject");val response=col("response");distinct(listOf(subject,response))
            val selected=complete(listOf(subject)+multiple("predictors",listOf(subject,response))+response);val labels=selected.map {it[0]}.distinct()
            val mapped=selected.map {row->listOf((labels.indexOf(row[0])+1).toString())+row.drop(1)}
            "$id(${table(mapped)}${if(id=="gee")",${opts["family"]}" else ""})"
        }
        "kstest"->{
            val first=col("first")
            if(opts["mode"]=="two") {val second=col("second");distinct(listOf(first,second));"kstest(${vector(values(first))},${vector(values(second))})"}
            else "kstest(${vector(values(first))},${opts["mode"]},${opts["location"]},${opts["scale"]})"
        }
        else->error("Unknown analysis form")
    }
}

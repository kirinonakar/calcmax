package com.kirinonakar.symvacas.ui

import org.json.JSONObject

/** Construct the same selected-column analysis plan used by the Web form. */
internal fun guidedStatisticsCommand(definition:JSONObject,rows:List<List<String>>,settings:JSONObject=JSONObject(),columnLabels:List<String> = emptyList()):String {
    if(definition.getString("id")=="survivalanalysis")return survivalAnalysisPlan(rows,settings,columnLabels).command
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
        "cox"->{
            val time=col("time");val event=col("event");val entry=if(opts["truncation"]=="entry")col("entry") else null
            val reserved=listOfNotNull(time,event,entry)
            val selected=eventRows(listOfNotNull(entry)+multiple("predictors",reserved))
            "cox(${table(selected)},${opts["ties"]},${if(entry==null)-1 else 2},${if(opts["ph"]=="test")1 else 0})"
        }
        "repeatedanova"->{val indices=multiple("columns");require(indices.size>=2) {"Choose at least two conditions"};"repeatedanova(${table(complete(indices))},${opts["factor2"]})"}
        "mixedmodel","gee"->{
            val subject=col("subject");val response=col("response");distinct(listOf(subject,response))
            val selectedColumns=multiple("predictors",listOf(subject,response))
            val selected=complete(listOf(subject)+selectedColumns+response);val labels=selected.map {it[0]}.distinct()
            val mapped=selected.map {row->listOf((labels.indexOf(row[0])+1).toString())+row.drop(1)}
            if(id=="gee") {
                val pairs=interactionPairs(opts["interactions"],selectedColumns,columnLabels)
                require(pairs.distinct().size==pairs.size) {"Interaction pairs must be distinct"}
                val suffix=if(pairs.isEmpty())"" else ","+pairs.joinToString(",","[","]") {pair->"[${pair.first},${pair.second}]"}
                "gee(${table(mapped)},${opts["family"]},${opts["corr"]}$suffix)"
            } else {
                val slope=opts["slope"]?.toIntOrNull() ?: 0
                "mixedmodel(${table(mapped)},$slope)"
            }
        }
        "kstest"->{
            val first=col("first")
            if(opts["mode"]=="two") {val second=col("second");distinct(listOf(first,second));"kstest(${vector(values(first))},${vector(values(second))})"}
            else "kstest(${vector(values(first))},${opts["mode"]},${opts["location"]},${opts["scale"]})"
        }
        else->error("Unknown analysis form")
    }
}

/** Parse "age,weight", "age (y)", "y,z", "2,3" (column numbers) or "p1,p2" (predictor order) interaction pairs. */
internal fun interactionPairs(value:String?,predictors:List<Int> = emptyList(),columnLabels:List<String> = emptyList()):List<Pair<Int,Int>> {
    val text=value.orEmpty().trim()
    if(text.isEmpty())return emptyList()
    val names=columnLabels.map {it.trim().lowercase()}
    fun aliasColumn(alias:String):Int? = when(alias) {
        "x"->0
        "y"->1
        "z"->2
        else->Regex("^x(\\d+)\$").find(alias)?.groupValues?.get(1)?.toInt()?.minus(1)
    }
    fun splitLabel(label:String):Pair<String,Int?> {
        val found=Regex("^(.+?)\\s*\\(\\s*([^()]*?)\\s*\\)\$").find(label)
        if(found==null)return label to null
        return found.groupValues[1].trim() to aliasColumn(found.groupValues[2].trim())
    }
    val headers=names.map {label->val parts=splitLabel(label);if(parts.second==null)label else parts.first}
    fun positionOf(column:Int):Int {
        val position=predictors.indexOf(column)
        require(position>=0) {"Interaction columns must be among the selected predictors"}
        return position+1
    }
    fun resolve(token:String):Int {
        val raw=token.trim();val lower=raw.lowercase()
        require(raw.isNotEmpty()) {"Use interaction pairs like age,weight;2,3"}
        val labelled=splitLabel(lower);val column=labelled.second
        if(column!=null) {
            if(headers.getOrNull(column)==labelled.first||names.getOrNull(column)==labelled.first||names.getOrNull(column)==lower)return positionOf(column)
            val byHeader=headers.indexOf(labelled.first)
            if(byHeader>=0)return positionOf(byHeader)
            val byName=names.indexOf(lower)
            require(byName>=0) {"Unknown interaction column"}
            return positionOf(byName)
        }
        // Shown column letters (x, y, z, x4, x5, ...) address the Nth data column.
        val alias=aliasColumn(lower)
        if(alias!=null&&alias>=0) {
            if(alias in predictors)return positionOf(alias)
            val byName=names.indexOf(lower)
            if(byName>=0)return positionOf(byName)
            val byHeader=headers.indexOf(lower)
            if(byHeader>=0)return positionOf(byHeader)
            throw IllegalArgumentException("Interaction columns must be among the selected predictors")
        }
        val name=names.indexOf(lower)
        if(name>=0)return positionOf(name)
        val header=headers.indexOf(lower)
        if(header>=0)return positionOf(header)
        val named=Regex("^p(\\d+)\$").find(lower)
        if(named!=null) {
            val at=named.groupValues[1].toInt()
            require(at in 1..predictors.size) {"Interaction positions must be within the selected predictors"}
            return at
        }
        val spelled=Regex("^column\\s*(\\d+)\$").find(lower)?.groupValues?.get(1)
        val digits=raw.removePrefix("#").takeIf {it.isNotEmpty()&&it.all(Char::isDigit)}
        val number=(spelled?:digits)?.toInt() ?: -1
        require(number>=1) {"Unknown interaction column"}
        return positionOf(number-1)
    }
    return text.split(';').map {group->
        val tokens=group.split(',','+').filter(String::isNotBlank)
        require(tokens.size==2) {"Use interaction pairs like age,weight;2,3"}
        val first=resolve(tokens[0]);val second=resolve(tokens[1])
        if(first<=second)first to second else second to first
    }
}

internal data class SurvivalPlan(val command:String,val groups:List<String>,val predictors:List<String>)

internal fun survivalAnalysisPlan(rows:List<List<String>>,settings:JSONObject=JSONObject(),labels:List<String> = emptyList()):SurvivalPlan {
    require(rows.isNotEmpty()) {"Enter data first"}
    val n=rows.maxOf {it.size}
    fun option(key:String,default:String)=settings.optString(key,default)
    fun col(key:String,default:String):Int {val i=option(key,default).toIntOrNull();require(i!=null&&i in 0 until n) {"Choose valid data columns"};return i}
    val grouping=option("grouping","groups");val cox=option("cox","0")
    require(grouping in listOf("groups","all")&&cox in listOf("0","1")) {"Invalid analysis option"}
    val time=col("time","0");val event=col("event","1");val group=if(grouping=="groups")col("group","2") else null
    val reserved=listOfNotNull(time,event,group)
    require(reserved.distinct().size==reserved.size) {"Roles must use different columns"}
    val predictors=if(cox=="1")option("predictors","").split(',').filter(String::isNotBlank).map {it.toIntOrNull() ?: -1} else emptyList()
    require(predictors.distinct().size==predictors.size&&predictors.all {it in 0 until n&&it !in reserved}) {"Choose distinct analysis columns"}
    val selected=rows.map {row->(reserved+predictors).map {row.getOrElse(it){""}.trim()}}
    require(selected.all {row->row.all(String::isNotBlank)}) {"Complete selected rows required"}
    val eventValue=option("eventValue","1").trim();require(eventValue.isNotBlank()) {"Enter the event value"}
    val states=selected.map {it[1]}.distinct();require(states.size<=2) {"Event column must have at most two values"}
    require(states.size==1||eventValue in states) {"Event value does not occur in the selected column"}
    val groups=if(group==null)emptyList() else selected.map {it[2]}.distinct()
    val encoded=selected.map {row->listOf(row[0],if(row[1]==eventValue)"1" else "0",if(group==null)"1" else (groups.indexOf(row[2])+1).toString())+row.drop(reserved.size)}
    val table=encoded.joinToString(",","[","]") {it.joinToString(",","[","]")}
    val ties=option("ties","efron");val ph=if(option("ph","test")=="test")1 else 0
    return SurvivalPlan("survivalanalysis($table,$cox,$ties,-1,$ph)",groups,predictors.map {labels.getOrNull(it) ?: listOf("x","y","z").getOrNull(it) ?: "x${it+1}"})
}

package com.kirinonakar.symvacas.ui

import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.math.Parser
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun statisticsColumnCount(kind:String):Int = when(kind) {
    "list"->1;"xy"->2;"xyz"->3
    else->kind.removePrefix("columns:").toIntOrNull()?.coerceIn(1,100) ?: 1
}
internal fun statisticsColumnNames(kind:String):List<String> = List(statisticsColumnCount(kind)) {listOf("x","y","z").getOrNull(it) ?: "x${it+1}"}
internal fun statisticsColumnLabels(csv:String,kind:String):List<String> {
    val names=statisticsColumnNames(kind)
    val raw=csv.removePrefix("\uFEFF").replace("\r\n","\n").replace('\r','\n').split('\n').map {it.splitCsvRecord()}
    if(!statisticsHasHeader(raw))return names
    return names.mapIndexed {index,name->raw.first().getOrNull(index)?.trim()?.takeIf {it.isNotBlank()&&it!=name}?.let {"$it ($name)"} ?: name}
}
internal fun statisticsKindForColumns(count:Int):String = when(count) {1->"list";2->"xy";3->"xyz";else->"columns:${count.coerceIn(1,100)}"}

/** Group labels stay categorical, including numeric and date labels. */
internal fun statisticsPlotSeries(rows:List<List<String>>,kind:String,grouping:String="columns"):List<Pair<String,List<Double>>> {
    val names=statisticsColumnNames(kind)
    fun number(value:String?)=value?.statisticsNumericCell()?.toDoubleOrNull()?.takeIf(Double::isFinite)
    if(grouping !in listOf("first","last")||names.size<2) {
        val numeric=statisticsNumericRows(rows,if(names.size>1)statisticsDateAxis(rows) else null)
        return names.mapIndexed {index,name->(if(names.size==1)"" else name) to numeric.mapNotNull {number(it.getOrNull(index))}}
    }
    val groupColumn=if(grouping=="first")0 else names.lastIndex
    val groups=linkedMapOf<String,List<MutableList<Double>>>()
    rows.forEach {row->
        val group=row.getOrNull(groupColumn)?.trim().orEmpty()
        if(group.isNotEmpty()) {
            val samples=groups.getOrPut(group){List(names.size){mutableListOf()}}
            names.indices.filter {it!=groupColumn}.forEach {index->number(row.getOrNull(index))?.let {samples[index].add(it)}}
        }
    }
    return groups.flatMap {(group,samples)->names.indices.filter {it!=groupColumn}.map {index->
        (if(names.size==2)group else "$group · ${names[index]}") to samples[index].toList()
    }}
}

internal data class StatisticsPlotPanel(val label:String,val series:List<Pair<String,List<Double>>>)
internal fun statisticsPlotPanels(rows:List<List<String>>,kind:String,grouping:String):List<StatisticsPlotPanel> {
    val names=statisticsColumnNames(kind)
    if(grouping !in listOf("first","last")||names.size<2)return listOf(StatisticsPlotPanel("",statisticsPlotSeries(rows,kind)))
    val groupColumn=if(grouping=="first")0 else names.lastIndex
    return names.indices.filter {it!=groupColumn}.map {column->
        val pairs=rows.map {row->listOf(row.getOrNull(groupColumn).orEmpty(),row.getOrNull(column).orEmpty())}
        StatisticsPlotPanel(names[column],statisticsPlotSeries(pairs,"xy","first"))
    }
}

internal fun statisticsRows(csv:String):List<List<String>> {
    val normalized=csv.removePrefix("\uFEFF").replace("\r\n","\n").replace('\r','\n')
    val lines=mutableListOf<String>();var start=0
    normalized.forEachIndexed {index,char->if(char=='\n'){lines+=normalized.substring(start,index);start=index+1}}
    lines+=normalized.substring(start)
    val rows=lines.map {it.splitCsvRecord().map(String::trim)}
    return if(statisticsHasHeader(rows))rows.drop(1) else rows
}

internal data class StatisticsCsvImport(val rows:List<List<String>>,val hasHeader:Boolean,val columnCount:Int) {
    val labels:List<String> get()=(0 until columnCount).map {index->
        val header=if(hasHeader)rows.firstOrNull()?.getOrNull(index)?.trim().orEmpty() else ""
        if(header.isBlank())"Column ${index+1}" else "Column ${index+1}: $header"
    }
}

internal fun previewStatisticsCsv(csv:String):StatisticsCsvImport {
    val rows=csv.replace("\r\n","\n").replace('\r','\n').removePrefix("\uFEFF")
        .lineSequence().filter(String::isNotBlank).map {it.splitCsvRecord()}.toList()
    return StatisticsCsvImport(rows,statisticsHasHeader(rows),rows.maxOfOrNull(List<String>::size)?:0)
}

internal fun importStatisticsCsv(preview:StatisticsCsvImport,columns:List<Int>,skipHeader:Boolean):String {
    require(columns.isNotEmpty()&&columns.size<=100&&columns.distinct().size==columns.size)
    require(columns.all {it in 0 until preview.columnCount})
    return preview.rows.drop(if(skipHeader)1 else 0).joinToString("\n") {row->statisticsCsvLine(columns.map {index->row.getOrNull(index).orEmpty()})}
}

internal fun statisticsCsvLine(cells:List<String>):String=cells.joinToString(",") {it.csvCell()}

private fun String.csvCell():String=if(any {it==','||it=='"'||it=='\n'||it=='\r'||it=='\t'})"\"${replace("\"","\"\"")}\"" else this

private val groupedStatisticNumber=Regex("^[+-]?\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$")
internal fun String.statisticsNumericCell():String=if(groupedStatisticNumber.matches(trim()))trim().replace(",","") else trim()

internal fun statisticsNumericRows(rows:List<List<String>>,dateAxis:StatisticsDateAxis?=null):List<List<String>> =
    (dateAxis?.numericRows(rows)?:rows).map {row->row.map(String::statisticsNumericCell)}

internal data class StatisticsDateAxis(val origin:LocalDate) {
    fun x(value:String):String?=parseStatisticsDate(value)?.let {ChronoUnit.DAYS.between(origin,it).toString()}
    fun numericRows(rows:List<List<String>>):List<List<String>> = rows.map {row->
        if(row.isEmpty())row else listOf(x(row[0]).orEmpty())+row.drop(1)
    }
}

internal fun statisticsDateAxis(rows:List<List<String>>):StatisticsDateAxis? {
    val xValues=rows.mapNotNull {it.firstOrNull()?.takeIf(String::isNotBlank)}
    val dates=xValues.mapNotNull(::parseStatisticsDate)
    val numericCount=xValues.count {it.statisticsNumericCell().toDoubleOrNull()!=null}
    if(dates.isEmpty()||dates.size<numericCount)return null
    return StatisticsDateAxis(dates.min().minusDays(1))
}

internal fun parseStatisticsDate(value:String):LocalDate? {
    val match=Regex("^(\\d{4})([-/.])(\\d{1,2})\\2(\\d{1,2})$").matchEntire(value.trim())?:return null
    return runCatching {LocalDate.of(match.groupValues[1].toInt(),match.groupValues[3].toInt(),match.groupValues[4].toInt())}.getOrNull()
}

internal fun statisticsHasHeader(rows:List<List<String>>):Boolean {
    val first=rows.firstOrNull() ?: return false
    if(first.none(String::isNotBlank))return false
    if(statisticsHeader(first))return true
    fun dataCell(value:String):Boolean {
        if(value.isBlank())return false
        if(value.statisticsNumericCell().toDoubleOrNull()?.isFinite()==true||value in listOf("pi","π","e","E","tau","τ","∞")||
            value.matches(Regex("(?i)(NaN|[+-]?Infinity)"))||parseStatisticsDate(value)!=null)return true
        if(value.matches(Regex("[\\p{L}_][\\p{L}\\p{N}_]*(?:\\s+[\\p{L}_][\\p{L}\\p{N}_]*)*")))return false
        return runCatching {Parser(value).parse().kind!="symbol"}.getOrDefault(false)
    }
    return first.all(String::isNotBlank)&&first.none(::dataCell)&&rows.drop(1).any {row->row.size==first.size&&row.any(::dataCell)}
}

private fun statisticsHeader(row:List<String>):Boolean {
    val cells=row.map(String::lowercase)
    return when(cells.size) {
        1->cells[0] in listOf("x","n","value","y")
        2->cells==listOf("x","y")||cells==listOf("group","value")||cells==listOf("date","value")||cells==listOf("date","y")
        else->cells.take(3)==listOf("x","y","z")
    }
}

internal fun statisticsDataSource(csv:String,kind:String):String {
    val rawRows=statisticsRows(csv)
    val rows=statisticsNumericRows(rawRows,if(statisticsColumnCount(kind)>1)statisticsDateAxis(rawRows) else null)
    val columns=statisticsColumnCount(kind)
    return if(columns>1)rows.filter {row->(0 until columns).all {index->row.getOrNull(index)?.isNotBlank()==true}}
        .joinToString(",","[","]") {row->row.take(columns).joinToString(",","[","]")}
    else rows.mapNotNull {it.getOrNull(0)?.takeIf(String::isNotBlank)}.joinToString(",","[","]")
}

internal fun statisticsRecallSource(editor:Editor,csv:String,kind:String,committed:Boolean=false):String {
    val literal=statisticsDataSource(csv,kind)
    if(kind!="xy"||committed||literal=="[]"||editor.cursor!=editor.anchor)return literal
    val caret=editor.cursor
    if(editor.source.getOrNull(caret-1)!='['||editor.source.getOrNull(caret)!=']')return literal
    val regressionList=editor.tree()?.nodes()?.any {node->
        node.kind=="call"&&node.value=="regression"&&node.args.firstOrNull()?.let {arg->
            arg.kind=="list"&&arg.start==caret-1&&arg.end==caret+1
        }==true
    }==true
    return if(regressionList)literal.substring(1,literal.length-1) else literal
}

internal fun String.splitCsvRecord():List<String> {
    // Excel separates columns with tabs; commas inside those cells are literal.
    var inQuotes=false
    val delimiter=if(any {ch->
        if(ch=='"')inQuotes=!inQuotes
        ch=='\t'&&!inQuotes
    })'\t' else ','
    val cells=mutableListOf<String>();val current=StringBuilder();var quoted=false;var i=0
    while(i<length) {
        val ch=this[i]
        when {
            ch=='"'&&quoted&&i+1<length&&this[i+1]=='"'->{current.append('"');i++}
            ch=='"'->quoted=!quoted
            ch==delimiter&&!quoted->{cells+=current.toString().trim();current.setLength(0)}
            else->current.append(ch)
        }
        i++
    }
    cells+=current.toString().trim();return cells
}

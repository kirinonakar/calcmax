package com.kirinonakar.calcmax.ui

import com.kirinonakar.calcmax.math.Editor
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun statisticsRows(csv:String):List<List<String>> {
    val normalized=csv.replace("\r\n","\n").replace('\r','\n')
    val lines=mutableListOf<String>();var start=0
    normalized.forEachIndexed {index,char->if(char=='\n'){lines+=normalized.substring(start,index);start=index+1}}
    lines+=normalized.substring(start)
    return lines.map {it.splitCsvRecord().map(String::trim)}
        .filterIndexed {index,row->index!=0||!statisticsHeader(row)}
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
    val first=rows.firstOrNull().orEmpty()
    val knownHeader=first.isNotEmpty()&&statisticsHeader(first)
    val inferredHeader=first.any(String::isNotBlank)&&first.all {it.isBlank()||it.statisticsNumericCell().toDoubleOrNull()==null}&&
        rows.drop(1).any {row->row.size==first.size&&row.any {it.statisticsNumericCell().toDoubleOrNull()!=null}}
    return StatisticsCsvImport(rows,knownHeader||inferredHeader,rows.maxOfOrNull(List<String>::size)?:0)
}

internal fun importStatisticsCsv(preview:StatisticsCsvImport,columns:List<Int>,skipHeader:Boolean):String {
    require(columns.isNotEmpty()&&columns.size<=3&&columns.distinct().size==columns.size)
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
    val rows=statisticsNumericRows(rawRows,if(kind=="xy"||kind=="xyz")statisticsDateAxis(rawRows) else null)
    val columns=when(kind){"xy"->2;"xyz"->3;else->1}
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

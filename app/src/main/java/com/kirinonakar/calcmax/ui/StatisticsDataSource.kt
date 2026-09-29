package com.kirinonakar.calcmax.ui

import com.kirinonakar.calcmax.math.Editor

internal fun statisticsRows(csv:String):List<List<String>> {
    val normalized=csv.replace("\r\n","\n").replace('\r','\n')
    val lines=mutableListOf<String>();var start=0
    normalized.forEachIndexed {index,char->if(char=='\n'){lines+=normalized.substring(start,index);start=index+1}}
    lines+=normalized.substring(start)
    return lines.map {it.splitCsvRecord().map(String::trim)}
        .filterIndexed {index,row->index!=0||!statisticsHeader(row)}
}

private fun statisticsHeader(row:List<String>):Boolean {
    val cells=row.map(String::lowercase)
    return when(cells.size) {
        1->cells[0] in listOf("x","n","value","y")
        2->cells==listOf("x","y")||cells==listOf("group","value")
        else->cells.take(3)==listOf("x","y","z")
    }
}

internal fun statisticsDataSource(csv:String,kind:String):String {
    val rows=statisticsRows(csv)
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
    val cells=mutableListOf<String>();val current=StringBuilder();var quoted=false;var i=0
    while(i<length) {
        val ch=this[i]
        when {
            ch=='"'&&quoted&&i+1<length&&this[i+1]=='"'->{current.append('"');i++}
            ch=='"'->quoted=!quoted
            ch==','&&!quoted->{cells+=current.toString().trim();current.setLength(0)}
            else->current.append(ch)
        }
        i++
    }
    cells+=current.toString().trim();return cells
}

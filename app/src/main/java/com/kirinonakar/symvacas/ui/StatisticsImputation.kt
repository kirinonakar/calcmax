package com.kirinonakar.symvacas.ui

import org.json.JSONArray

/** Fill missing cells with full-precision engine values; keep observed cells. */
internal fun statisticsImputationCSV(source:String,filled:JSONArray,columnLimit:Int):String {
    val rows=source.removePrefix("\uFEFF").replace("\r\n","\n").replace('\r','\n').trimEnd('\n').split('\n').map {it.splitCsvRecord().map(String::trim).toMutableList()}
    val selected=rows.map {it.take(columnLimit)}
    val header=if(statisticsHasHeader(selected)&&"NA" !in selected.first())1 else 0
    val width=minOf(columnLimit,rows.maxOf {it.size})
    require(filled.length()==rows.size-header&&filled.length()>0) {"Imputation result does not match the current data"}
    for(i in 0 until filled.length()) {
        val values=filled.getJSONArray(i);require(values.length()==width) {"Imputation result does not match the current data"}
        val row=rows[i+header]
        for(j in 0 until values.length()) {
            val previous=row.getOrElse(j){""}.trim();val value=values.getString(j)
            require(value.toDoubleOrNull()?.isFinite()==true) {"Imputed values must be finite numbers"}
            if(previous.isBlank()||previous in listOf("NA","nan")){while(row.size<=j)row.add("");row[j]=value}
        }
    }
    fun quote(value:String)=if(value.any {it in ",\"\r\n"})"\"${value.replace("\"","\"\"")}"+"\"" else value
    return rows.joinToString("\n"){row->row.joinToString(",",transform=::quote)}
}

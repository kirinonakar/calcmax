package com.kirinonakar.symvacas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import org.json.JSONObject

@Composable internal fun StatisticsSelectionTitle(title:String,translate:Boolean=true) {
    Text(if(translate)tr(title) else title,fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=LocalInstrument.current.ink)
}

internal fun statisticsReportFor(result:JSONObject?,source:String,analyses:Set<String>):JSONObject? {
    val report=result?.optJSONObject("statisticsReport") ?: return null
    val analysis=report.optString("analysis").ifBlank {source.substringBefore('(').trim()}
    return report.takeIf {analysis in analyses}
}

internal fun statisticsCellText(m:CalculatorModel,cell:JSONObject)=ResultDisplayFormat.resultText(cell,true,false,
    m.resultDisplayMode,m.thousandsSeparator,m.engineeringConversion,m.engineeringShift,false,false,m.displayDigits)

internal fun statisticsTableCopyText(headers:List<String>,rows:List<List<String>>):String {
    fun cell(value:String)=if(value.any {it=='\t'||it=='\n'||it=='\r'||it=='"'})"\""+value.replace("\"","\"\"")+"\"" else value
    return (listOf(headers)+rows.map {row->headers.indices.map {row.getOrElse(it){""}}})
        .joinToString("\n") {row->row.joinToString("\t",transform=::cell)}
}

@Composable internal fun StatisticsTextTable(headers:List<String>,rows:List<List<String>>,headerSize:Int=12,cellSize:Int=13) {
    val c=LocalInstrument.current
    val clipboard=LocalClipboardManager.current
    // Intrinsic text widths keep columns aligned. A single scroll owns the table;
    // no child scroll receives unbounded width. Cap unusually long cells for readability.
    Column(Modifier.fillMaxWidth()) {SelectionContainer {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            headers.forEachIndexed {column,header->Column(Modifier.widthIn(min=64.dp,max=320.dp).width(IntrinsicSize.Max)) {
                Box(Modifier.fillMaxWidth().height(38.dp).background(c.grid).padding(horizontal=8.dp),contentAlignment=Alignment.CenterStart) {
                    Text(header,fontSize=headerSize.sp,fontWeight=FontWeight.SemiBold,color=c.ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                rows.forEach {row->
                    Box(Modifier.fillMaxWidth().height(36.dp).padding(horizontal=8.dp),contentAlignment=Alignment.CenterStart) {
                        Text(row.getOrElse(column){""},fontSize=cellSize.sp,color=c.ink,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color=c.grid)
                }
            }}
        }
    }
    TextButton(onClick={clipboard.setText(AnnotatedString(statisticsTableCopyText(headers,rows)))},modifier=Modifier.align(Alignment.End)) {
        Text(tr("Copy table"),fontSize=12.sp)
    }}
}

@Composable internal fun StatisticsResultReport(m:CalculatorModel,report:JSONObject) {
    val c=LocalInstrument.current
    val clipboard=LocalClipboardManager.current
    val sections=report.getJSONArray("sections")
    Column(Modifier.fillMaxWidth().testTag("statistics-result-report"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(tr(report.getString("title")),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=c.ink)
            TextButton(onClick={m.result?.let {clipboard.setText(AnnotatedString(statisticsCellText(m,it)))}}){Text(tr("Copy result"),fontSize=12.sp)}
        }
        for(index in 0 until sections.length()) {
            val section=sections.getJSONObject(index)
            val columns=section.getJSONArray("columns")
            val rows=section.getJSONArray("rows")
            Text(tr(section.getString("title")),fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=c.ink)
            StatisticsTextTable(List(columns.length()){tr(columns.getString(it))},List(rows.length()){rowIndex->
                val row=rows.getJSONArray(rowIndex)
                List(columns.length()){column->
                    row.optJSONObject(column)?.let {statisticsCellText(m,it)}
                        ?: if(columns.optString(column)=="Metric")tr(row.optString(column)) else row.optString(column)
                }
            })
            if(section.optInt("totalRows")>rows.length())Text("${rows.length()} / ${section.optInt("totalRows")} · ${tr("Copy result includes all rows.")}",fontSize=11.sp,color=c.muted)
        }
        m.result?.optString("note")?.takeIf(String::isNotBlank)?.let {Text(it,fontSize=12.sp,color=c.muted)}
    }
}

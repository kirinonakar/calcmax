package com.kirinonakar.symvacas.ui

import org.json.JSONObject
import com.kirinonakar.symvacas.calculator.HistoryEntry
import com.kirinonakar.symvacas.calculator.ResultDisplayMode

internal fun regressionEquationCopyText(result:JSONObject,digits:Int,variables:Map<String,String> = emptyMap(),prefix:String?=null):String? {
    val report=result.optJSONObject("regression") ?: return null
    if(report.optString("model")=="randomforest")return null
    val source=result.optString("exact").ifBlank {result.optString("decimal")}
    val tree=regressionFormulaDisplayTree(source.replace("**","^"),digits,variables) ?: return null
    val logistic=report.optString("fitScale")=="binomial"||report.optString("model").contains("logistic")
    return (prefix ?: if(logistic)"P(y = 1) = " else "y = ")+equationFormulaText(tree)
}

internal fun statisticsFormattedCopyCell(cell:JSONObject,digits:Int,mode:ResultDisplayMode=ResultDisplayMode.OFF,grouping:Boolean=false,engineeringConversion:Boolean=false,engineeringShift:Int=0):String {
    fun complex(node:JSONObject):Boolean {
        if(node.optString("kind") in setOf("sum","product","binary","function","call","root","power"))return true
        val args=node.optJSONArray("args")
        return (0 until (args?.length() ?: 0)).any {args!!.optJSONObject(it)?.let(::complex)==true}
    }
    val sourceTree=ResultDisplayFormat.resultTree(cell,true,false,engineeringConversion,false,false)
    val power=sourceTree?.optJSONArray("args")?.optJSONObject(1)
    val notation=sourceTree?.optString("kind") in listOf("product","binary")&&power?.optString("kind")=="power"&&power.optJSONArray("args")?.optJSONObject(0)?.optString("value")=="10"
    if(sourceTree!=null&&!notation&&complex(sourceTree)) {
        return equationFormulaText(ResultDisplayFormat.formatTree(sourceTree,if(engineeringConversion)ResultDisplayMode.ENGINEERING else mode,grouping,engineeringShift,engineeringConversion,digits))
    }
    val source=cell.optString("decimal").ifBlank {cell.optString("exact")}
    if(sourceTree?.optString("kind")=="text"&&source.any {it in "+*/^()"}) {
        regressionFormulaDisplayTree(source,digits)?.let {return equationFormulaText(it)}
    }
    return ResultDisplayFormat.resultText(cell,true,false,mode,grouping,engineeringConversion,engineeringShift,false,false,digits)
}

internal fun regressionResultForCopy(current:JSONObject?,history:List<HistoryEntry>,report:JSONObject):JSONObject? {
    val expected=report.toString()
    fun matches(result:JSONObject)=result.optJSONObject("regression")?.toString()==expected
    current?.takeIf(::matches)?.let {return it}
    // The fitted report can remain visible after another analysis changes the
    // main result. Copy the response that belongs to this report.
    for(entry in history) {
        if(!entry.source.trimStart().startsWith("regression("))continue
        val result=runCatching {JSONObject(entry.response)}.getOrNull() ?: continue
        if(matches(result))return result
    }
    return null
}

internal fun markdownCell(value:String):String=value.replace("\\","\\\\")
    .replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
    .replace("|","\\|").replace("*","\\*").replace("_","\\_").replace("`","\\`")
    .replace("[","\\[").replace("]","\\]")
    .replace("\r\n","\n").replace("\r","\n").replace("\n","<br>")

internal fun statisticsResultMarkdown(result:JSONObject,formatCell:(JSONObject)->String,regressionEquation:String?=null,translate:(String)->String):String {
    val report=result.optJSONObject("statisticsReport") ?: result.optJSONObject("statisticsCopyReport") ?: return formatCell(result)
    val blocks=mutableListOf("## "+markdownCell(translate(report.optString("title"))))
    if(regressionEquation!=null) {
        val fence="`".repeat(maxOf(3,(Regex("`+").findAll(regressionEquation).maxOfOrNull {it.value.length} ?: 0)+1))
        blocks.add("### "+translate("Regression equation")+"\n\n"+fence+"text\n"+regressionEquation+"\n"+fence)
    }
    val sections=report.optJSONArray("sections")
    for(index in 0 until (sections?.length() ?: 0)) {
        val section=sections!!.getJSONObject(index)
        val columns=section.getJSONArray("columns")
        val rows=section.optJSONArray("copyRows") ?: section.getJSONArray("rows")
        fun line(values:List<String>)="| "+values.joinToString(" | ")+" |"
        val table=mutableListOf(line(List(columns.length()){markdownCell(translate(columns.getString(it)))}),line(List(columns.length()){ "---" }))
        for(rowIndex in 0 until rows.length()) {
            val row=rows.getJSONArray(rowIndex)
            if(regressionEquation!=null&&columns.optString(0)=="Metric"&&row.optString(0)=="Fitted expression")continue
            table.add(line(List(columns.length()){column->
                val text=row.optJSONObject(column)?.let(formatCell) ?: row.optString(column).let {
                    if(columns.optString(column) in listOf("Metric","Check","Interpretation","Sample","Role","R²"))translate(it)
                    else if(report===result.optJSONObject("statisticsCopyReport")&&it.toBigDecimalOrNull()!=null)formatCell(JSONObject().put("decimal",it).put("exact",it)) else it
                }
                markdownCell(text)
            }))
        }
        if(table.size>2)blocks.add("### "+markdownCell(translate(section.optString("title")))+"\n\n"+table.joinToString("\n"))
    }
    report.optJSONArray("assumptions")?.takeIf {it.length()>0}?.let {assumptions->
        blocks.add("### "+translate("Assumptions")+"\n\n"+(0 until assumptions.length()).joinToString("\n\n"){markdownCell(translate(assumptions.getString(it)))})
    }
    report.optJSONArray("notes")?.let {notes->for(index in 0 until notes.length())blocks.add(markdownCell(translate(notes.getString(index))))}
    result.optString("note").takeIf {it.isNotBlank()}?.let {blocks.add(markdownCell(it))}
    result.optJSONArray("conditions")?.takeIf {it.length()>0}?.let {conditions->
        blocks.add("### "+translate("Conditions")+"\n\n"+(0 until conditions.length()).joinToString("\n"){"- "+markdownCell(conditions.getString(it))})
    }
    return blocks.joinToString("\n\n")
}

internal fun statisticsResultCopyText(m:com.kirinonakar.symvacas.calculator.CalculatorModel,result:JSONObject,language:String,regressionVariables:Map<String,String> = emptyMap(),regressionPrefix:String?=null):String {
    val dedicated=result.has("statisticsCopyReport")
    return statisticsResultMarkdown(result,{statisticsFormattedCopyCell(it,m.displayDigits,
        if(dedicated)ResultDisplayMode.OFF else m.resultDisplayMode,if(dedicated)false else m.thousandsSeparator,
        !dedicated&&m.engineeringConversion,if(dedicated)0 else m.engineeringShift)},
        regressionEquationCopyText(result,m.displayDigits,regressionVariables,regressionPrefix)){translateLabel(it,language)}
}

package com.kirinonakar.calcmax.calculator

import com.kirinonakar.calcmax.math.Editor
import com.kirinonakar.calcmax.math.Parser
import org.json.JSONObject

private var tapeEntrySequence = 0L
private fun nextTapeEntryId():Long = ++tapeEntrySequence
data class HistoryEntry(val id: Long, val source: String, val exact: String, val decimal: String, val mode: String, val favorite: Boolean = false,val inputTree:String="",val response:String="",val answer:String="")
data class TapeEntry(val source: String,val input: String,val result: String,val answer:String="",val id:Long=nextTapeEntryId())
data class CalcSession(val source:String,val names:List<String>,val index:Int=0,val input:Editor=Editor(),val accepted:Map<String,JSONObject> = emptyMap()) {
    val name get()=names[index]
}
data class GraphParameter(val value:Double,val min:Double,val max:Double)
data class DisplayShortcut(val label:String,val input:String,val source:String="keypad")
val DefaultDisplayShortcuts=listOf(
    DisplayShortcut("∫","integrate(,x)"),
    DisplayShortcut("∫ₐᵇ","integrate(,x,,)"),
    DisplayShortcut("d/dx","diff(,x)"),
    DisplayShortcut("f′(a)","nderivative(,x,)")
)

internal fun HistoryEntry.toTapeEntry():TapeEntry {
    val input=runCatching {JSONObject(inputTree).takeIf {it.has("kind")}?.toString()}.getOrNull()
        ?: runCatching {Parser(source,true).parse().json()}.getOrNull()
        ?: JSONObject().put("kind","text").put("value",source).toString()
    val legacyExpression=exact.trim().let {if(it.startsWith("Matrix(")&&it.endsWith(")"))it.substring(7,it.length-1) else it}
    val result=runCatching {JSONObject(response).takeIf {it.has("exact")||it.has("tree")}?.toString()}.getOrNull()
        ?: JSONObject().put("exact",exact).put("decimal",decimal)
            .put("tree",runCatching {JSONObject(Parser(legacyExpression,true).parse().json())}.getOrElse {JSONObject().put("kind","text").put("value",exact)}).toString()
    return TapeEntry(source,input,result,answer)
}
enum class ResultDisplayMode { OFF, ENGINEERING, SCIENTIFIC }

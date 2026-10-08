package com.kirinonakar.symvacas.ui

import com.kirinonakar.symvacas.calculator.ResultDisplayMode
import org.json.JSONArray
import org.json.JSONObject

internal fun equationStepExplanation(step:JSONObject,translate:(String)->String):String {
    val parts=step.optJSONArray("explanationParts") ?: return translate(step.optString("explanation"))
    return (0 until parts.length()).joinToString(" ") {index->
        val part=parts.getJSONObject(index)
        var text=translate(part.getString("text"))
        part.optJSONObject("values")?.let {values->values.keys().forEach {key->text=text.replace("{$key}",values.getString(key))}}
        text
    }
}

internal fun solutionStepsCopyText(report:JSONObject,digits:Int,translate:(String)->String):String {
    fun formula(value:JSONObject):String=value.optJSONObject("tree")?.let {
        equationFormulaText(ResultDisplayFormat.formatTree(it,ResultDisplayMode.OFF,false,maxFractionDigits=digits))
    }?.takeIf {it.isNotBlank()} ?: value.optString("exact")
    val blocks=mutableListOf(translate("Step-by-step solution"))
    report.optString("note").lines().filter {it.isNotBlank()}.forEach {blocks.add(translate(it))}
    report.optString("method").takeIf {it.isNotBlank()}?.let {blocks.add(translate(it))}
    fun steps(array:JSONArray?) {
        if(array==null)return
        for(index in 0 until array.length()) {
            val step=array.getJSONObject(index)
            val lines=mutableListOf("${index+1}. ${translate(step.optString("title"))}")
            equationStepExplanation(step,translate).takeIf {it.isNotBlank()}?.let(lines::add)
            step.optJSONObject("variableOrder")?.let {lines.add(formula(it))}
            step.optJSONObject("operation")?.let {lines.add(formula(JSONObject().put("tree",it)))}
            if(step.has("tree")||step.has("exact"))lines.add(formula(step))
            step.optJSONArray("equations")?.let {equations->
                for(i in 0 until equations.length())lines.add(formula(equations.getJSONObject(i)))
            }
            blocks.add(lines.filter {it.isNotBlank()}.joinToString("\n"))
        }
    }
    steps(report.optJSONArray("steps"))
    report.optJSONArray("advancedSteps")?.takeIf {it.length()>0}?.let {
        blocks.add(translate("Advanced solution · Gaussian elimination"));steps(it)
    }
    return blocks.joinToString("\n\n")
}

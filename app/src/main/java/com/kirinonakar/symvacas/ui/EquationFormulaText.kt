package com.kirinonakar.symvacas.ui

import org.json.JSONObject

/** Serialize the displayed intermediate expression, which may deliberately
 * differ from the engine's already simplified exact value. */
internal fun equationFormulaText(node:JSONObject):String {
    val kind=node.optString("kind");val value=node.optString("value")
    val array=node.optJSONArray("args")
    val args=List(array?.length() ?: 0){array!!.getJSONObject(it)}
    val parts=args.map(::equationFormulaText)
    fun part(index:Int)=parts.getOrElse(index){""}
    fun operand(index:Int)=if(args.getOrNull(index)?.optString("kind") in listOf("sum","product","unary","relation","fraction","power"))"(${part(index)})" else part(index)
    return when(kind) {
        "parentheses","group"->"(${part(0)})"
        "unary"->value+operand(0)
        "sum"->parts.mapIndexed {index,text->if(index==0||args[index].optString("kind")=="unary")text else "+$text"}.joinToString("")
        "product"->args.indices.joinToString("*"){operand(it)}
        "fraction"->operand(0)+"/"+operand(1)
        "power"->operand(0)+"^"+operand(1)
        "root"->"sqrt(${part(0)})"
        "relation"->parts.joinToString(" ${if(value=="==")"=" else value} ")
        "binary"->operand(0)+value+operand(1)
        "call","function","frozen_call"->"$value(${parts.joinToString(",")})"
        "list","matrix"->"[${parts.joinToString(",")}]"
        "tuple"->"(${parts.joinToString(",")})"
        "set"->"{${parts.joinToString(",")}}"
        "row-operation"->parts.joinToString(" → ")
        "row"->"$value: ${part(0)}"
        "rows"->parts.joinToString("\n")
        "quantity"->"${part(0)} $value"
        "restricted"->part(0)
        else->value
    }
}

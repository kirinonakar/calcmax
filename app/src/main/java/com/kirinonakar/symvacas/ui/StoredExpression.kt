package com.kirinonakar.symvacas.ui

import org.json.JSONObject

internal fun isSquareRootExponent(exponent:JSONObject?):Boolean {
    val value=if(exponent?.optString("kind")=="group")exponent.optJSONArray("args")?.optJSONObject(0) else exponent
    val parts=value?.optJSONArray("args")
    val numerator=parts?.optJSONObject(0)
    val denominator=parts?.optJSONObject(1)
    return value?.optString("kind")=="binary"&&value.optString("value")=="/"&&
        numerator?.optString("kind")=="number"&&numerator.optString("value")=="1"&&
        denominator?.optString("kind")=="number"&&denominator.optString("value")=="2"
}

// Stored values keep the engine result tree; convert it back to source text so operands can be shown as matrices.
internal fun treeSource(node:JSONObject?):String? {
    if(node==null)return null
    val args=node.optJSONArray("args")
    fun child(index:Int):String?=treeSource(args?.optJSONObject(index))
    fun children():String? {
        val count=args?.length() ?: return null
        val parts=ArrayList<String>(count)
        for(index in 0 until count)parts.add(child(index) ?: return null)
        return parts.joinToString(",")
    }
    return when(node.optString("kind")) {
        "list"->children()?.let{"[$it]"}
        "tuple"->children()?.let{"($it${if(args?.length()==1) "," else ""})"}
        "set"->children()?.let{"{"+it+"}"}
        "number","float","symbol","snapshot_symbol"->node.optString("value").takeIf{it.isNotBlank()}
        "constant"->when(node.optString("value")){"pi"->"pi";"E"->"e";"I"->"i";"oo"->"oo";"-oo"->"-oo";"True"->"true";"False"->"false";else->null}
        "group"->child(0)?.let{"($it)"}
        "binary","relation"->{
            val a=child(0) ?:return null
            if(node.optString("value")=="^"&&isSquareRootExponent(args?.optJSONObject(1)))"sqrt($a)"
            else {val b=child(1) ?:return null;"($a)${node.optString("value")}($b)"}
        }
        "unary"->{val a=child(0) ?:return null;"${node.optString("value","-")}($a)"}
        "restricted"->child(0)
        "call","frozen_call"->{val name=node.optString("value");val inner=children() ?:return null;if(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")))"$name($inner)" else null}
        else->null
    }
}

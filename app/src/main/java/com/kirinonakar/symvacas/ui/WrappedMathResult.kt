package com.kirinonakar.symvacas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** Break results at sums and collection separators, retaining fractions/powers. */
internal fun resultMathParts(tree:JSONObject):List<List<JSONObject>> {
    fun operator(value:String)=JSONObject().put("kind","text").put("value",value)
    fun split(node:JSONObject):MutableList<MutableList<JSONObject>> {
        val kind=node.optString("kind")
        val array=node.optJSONArray("args")
        val args=(0 until (array?.length() ?: 0)).map {array!!.getJSONObject(it)}
        if(kind in listOf("set","list","tuple")&&args.isNotEmpty()) {
            val parts=mutableListOf<MutableList<JSONObject>>()
            args.forEachIndexed {i,arg->
                val pieces=split(arg)
                if(i<args.lastIndex)pieces.last().add(operator(", "))
                parts.addAll(pieces)
            }
            parts.first().add(0,operator(when(kind){"set"->"{";"tuple"->"(";else->"["}))
            parts.last().add(operator(when(kind){"set"->"}";"tuple"->")";else->"]"}))
            return parts
        }
        if(kind=="sum"&&args.isNotEmpty()) {
            val parts=mutableListOf<MutableList<JSONObject>>()
            args.forEachIndexed {i,arg->
                val pieces=split(arg)
                if(i>0&&arg.optString("kind")!="unary")pieces.first().add(0,operator(" + "))
                parts.addAll(pieces)
            }
            return parts
        }
        if(kind=="relation"&&args.size==2) {
            val right=split(args[1]);right.first().add(0,operator(" ${node.optString("value")} "))
            return split(args[0]).apply {addAll(right)}
        }
        return mutableListOf(mutableListOf(node))
    }
    return split(tree)
}

@Composable internal fun WrappedMathResult(tree:JSONObject,size:Float) {
    val parts=remember(tree){resultMathParts(tree)}
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewportWidth=maxWidth
        FlowRow(Modifier.fillMaxWidth().testTag("wrapped-math-result"),horizontalArrangement=Arrangement.End,verticalArrangement=Arrangement.spacedBy(6.dp)) {
            parts.forEachIndexed {index,part->
                Row(Modifier.alignBy(MathAxis).widthIn(max=viewportWidth).horizontalScroll(rememberScrollState()).testTag("result-math-part-$index")) {
                    part.forEach {node->Box(Modifier.alignBy(MathAxis)){MathNode(node,size)}}
                }
            }
        }
    }
}

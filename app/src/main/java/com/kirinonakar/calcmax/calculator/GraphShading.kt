package com.kirinonakar.calcmax.calculator

import com.kirinonakar.calcmax.math.Expr
import com.kirinonakar.calcmax.math.Parser
import org.json.JSONArray
import org.json.JSONObject

private val shadingPrefix=Regex("^\\[(?:shade|s)\\]")
internal fun isGraphShading(source:String)=shadingPrefix.containsMatchIn(source.trim())
internal fun graphShadingBody(source:String)=source.trim().replaceFirst(shadingPrefix,"").trim()

internal fun graphShadeEntry(body:String):JSONObject {
    val parts=mutableListOf<String>();var depth=0;var start=0
    val sections=body.split(';')
    sections[0].forEachIndexed {index,ch->
        when(ch) {
            '(', '[', '{'->depth++
            ')', ']', '}'->depth--
            ','->if(depth==0){parts+=sections[0].substring(start,index).trim();start=index+1}
        }
    }
    parts+=sections[0].substring(start).trim()
    if(sections.size>1)parts+=sections.drop(1).map(String::trim)
    require(parts.all(String::isNotBlank)) {"Enter shading inequalities or one or two functions"}
    val intervals=parts.filter {it.contains("..")}
    val expressions=parts.filterNot {it.contains("..")}
    require(expressions.isNotEmpty() && intervals.size<=1) {"Enter shading inequalities or one or two functions"}
    val entry=JSONObject()
    intervals.firstOrNull()?.let {range->
        val ends=range.split("..")
        require(ends.size==2 && ends.all(String::isNotBlank)) {"Enter a..b for the shading interval"}
        entry.put("a",JSONObject(Parser(ends[0].trim()).parse().json())).put("b",JSONObject(Parser(ends[1].trim()).parse().json()))
    }
    val parsed=expressions.map {graphInputTree(it)}
    val relation=parsed[0]
    if(parsed.size==1 && relation.kind=="relation" && relation.args.all {it.kind!="relation"} && relation.args.any {it.kind=="symbol" && it.value=="y"}) {
        require(relation.value in listOf("<","<=",">",">=")) {"Enter y < f(x) or y > f(x)"}
        val left=relation.args[0];val right=relation.args[1]
        val onLeft=left.kind=="symbol" && left.value=="y"
        val boundary=if(onLeft)right else left
        require(boundary.nodes().none {it.kind=="symbol" && it.value=="y"}) {"Shading boundaries cannot depend on y"}
        val below=if(onLeft)relation.value.startsWith("<") else relation.value.startsWith(">")
        entry.put("mode","halfplane").put("side",if(below)"below" else "above").put("trees",JSONArray(listOf(JSONObject(boundary.json()))))
    } else if(parsed.any {it.kind=="relation"}) {
        val boundaries=mutableListOf<Expr>();val constraints=JSONArray()
        fun axis(node:Expr)=node.value.takeIf {node.kind=="symbol" && it in listOf("x","y")}
        fun flatten(node:Expr):List<Expr> {
            if(node.kind!="relation")return listOf(node)
            require(node.value in listOf("<","<=",">",">=")) {"Enter x or y inequalities for shading"}
            val left=flatten(node.args[0]);val right=flatten(node.args[1]);val a=left.last();val b=right.first()
            val onLeft=axis(a)!=null
            val name=axis(a) ?: axis(b) ?: error("Enter x bounds or y < f(x) inequalities")
            val boundary=if(onLeft)b else a
            require(boundary.nodes().none {it.kind=="symbol" && (it.value=="y" || name=="x" && it.value=="x")}) {"Enter x bounds or y < f(x) inequalities"}
            val upper=if(onLeft)node.value.startsWith("<") else node.value.startsWith(">")
            constraints.put(JSONObject().put("axis",name).put("side",if(upper)"upper" else "lower"))
            boundaries+=boundary
            return left+right
        }
        parsed.filter {it.kind=="relation"}.forEach {flatten(it)}
        val functions=parsed.filter {it.kind!="relation"}
        if(functions.isNotEmpty()) {
            require(functions.size<=2) {"Use one or two shading functions with x and y bounds"}
            fun bounds(axis:String)=JSONArray(boundaries.mapIndexedNotNull {index,tree->
                val constraint=constraints.getJSONObject(index)
                if(constraint.getString("axis")!=axis)null else JSONObject().put("side",constraint.getString("side")).put("tree",JSONObject(tree.json()))
            })
            entry.put("mode","band").put("trees",JSONArray(functions.map {JSONObject(it.json())})).put("xBounds",bounds("x")).put("yBounds",bounds("y"))
        } else entry.put("mode","region").put("trees",JSONArray(boundaries.map {JSONObject(it.json())})).put("constraints",constraints)
    } else {
        require(parsed.size<=2) {"Enter one or two shading functions"}
        entry.put("mode","band").put("trees",JSONArray(parsed.map {JSONObject(it.json())}))
    }
    return entry
}

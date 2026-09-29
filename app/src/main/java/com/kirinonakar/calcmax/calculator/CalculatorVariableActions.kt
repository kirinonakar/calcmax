package com.kirinonakar.calcmax.calculator

import androidx.lifecycle.viewModelScope
import com.kirinonakar.calcmax.math.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal object CalculatorVariableActions {
    fun CalculatorModel.performStore(name: String, source: String = editor.source.ifBlank { "Ans" },showResult:Boolean=true,finishInput:Boolean=false) {
        try {
            require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) { "Use a letter followed by letters, digits or underscores" }
            val tree=Parser(source).parse()
            require(name !in listOf("pi","e","i","I","oo","Ans","c0","hP","hbar","G","qe","NA","kB0","me","mp0")) { "Reserved constant or answer name" }
            if(busy) return
            val sourceTree=JSONObject(tree.json())
            val selfReference=tree.nodes().any {it.kind=="symbol"&&it.value==name}
            val answer=inputAnswer ?: variables.optJSONObject("Ans")
            fun freezeAnswer(node:JSONObject):JSONObject {
                if(node.optString("kind")=="symbol"&&node.optString("value")=="Ans"&&answer!=null)return JSONObject(answer.toString())
                val copy=JSONObject(node.toString())
                copy.optJSONArray("args")?.let {args->for(index in 0 until args.length())args.optJSONObject(index)?.let {args.put(index,freezeAnswer(it))}}
                return copy
            }
            val snapshot=selfReference || tree.kind=="number" || tree.kind=="symbol"&&tree.value=="Ans"
            job=viewModelScope.launch {
                busy=true
                try {
                    if(snapshot) {
                        val response=engine.execute(request().put("tree",sourceTree))
                        if(!response.optBoolean("ok")||!response.has("resultAst")){error=response.optString("error","This result cannot be stored");return@launch}
                        variables=JSONObject(variables.toString()).put(name,response.getJSONObject("resultAst"))
                        if(showResult){result=response.put("note","Stored in $name");dmsDisplay=false;dmsConversion=false}
                    } else {
                        val stored=freezeAnswer(sourceTree)
                        val next=JSONObject(variables.toString()).put(name,stored)
                        fun cyclic(node:JSONObject,visited:Set<String>):Boolean {
                            if(node.optString("kind")=="symbol") {
                                val ref=node.optString("value")
                                if(ref==name)return true
                                if(ref !in visited)next.optJSONObject(ref)?.let {if(cyclic(it,visited+ref))return true}
                            }
                            val args=node.optJSONArray("args") ?: return false
                            for(index in 0 until args.length())args.optJSONObject(index)?.let {if(cyclic(it,visited))return true}
                            return false
                        }
                        if(cyclic(stored,emptySet())){error="Cyclic variable definition";return@launch}
                        variables=next
                        if(showResult){
                            val message="Stored expression in $name"
                            result=JSONObject().put("ok",true).put("exact",message).put("decimal",message).put("tree",JSONObject().put("kind","text").put("value",message)).put("note",message)
                            dmsDisplay=false;dmsConversion=false
                        }
                    }
                    if(finishInput){result?.put("assignment",true);committed=true}
                    inputVersion++;resultVersion=-1;error="";save()
                } finally {busy=false}
            }
        } catch(e: Exception) { error=e.message ?: "Invalid variable" }
    }
    fun CalculatorModel.performMemory(direction:Int,source:String=editor.source) {
        if(busy)return
        val expression=source.ifBlank{"0"}
        val tree=try{calculationTree(expression)}catch(e:Exception){error=e.message ?: "Complete the expression";return}
        val revision=inputVersion
        val operandRequest=request().put("tree",JSONObject(tree.json()))
        val cached=result?.takeIf{resultSource==expression&&resultVersion==revision&&it.optBoolean("ok")}
        job=viewModelScope.launch {
            busy=true
            try {
                val operand=cached ?: engine.execute(operandRequest)
                if(!operand.optBoolean("ok")||!operand.has("resultAst")){error=operand.optString("error","This result cannot be stored in memory");return@launch}
                if(revision==inputVersion&&source==editor.source){if(!committed)commit(source,operand);busy=true}
                val previous=variables.optJSONObject("M") ?: JSONObject().put("kind","number").put("value","0")
                val addition=JSONObject().put("kind","binary").put("value",if(direction>0)"+" else "-").put("args",JSONArray().put(previous).put(operand.getJSONObject("resultAst")))
                val updated=engine.execute(request().put("tree",addition))
                if(updated.optBoolean("ok")&&updated.has("resultAst")){variables=JSONObject(variables.toString()).put("M",updated.getJSONObject("resultAst"));save()}
                else error=updated.optString("error","Memory update failed")
            }finally{busy=false}
        }
    }
    fun CalculatorModel.performDefine(name: String, parameters: String, source: String, showResult:Boolean=true,finishInput:Boolean=false) {
        try {
            val definition=FunctionTransfer.definition(name,parameters,source)
            functions=JSONObject(functions.toString()).put(definition.name,definition.json())
            error=""
            val message="${definition.name}(${definition.parameters.joinToString()}) defined"
            if(showResult){result=JSONObject().put("exact",message).put("decimal",message).put("tree",JSONObject().put("kind","text").put("value",message));dmsDisplay=false;dmsConversion=false}
            if(finishInput){result?.put("assignment",true);committed=true}
            save()
        } catch(e: Exception) { error=e.message ?: "Invalid function" }
    }
    fun CalculatorModel.performExportFunctions():String = FunctionTransfer.encode(functions)
    fun CalculatorModel.performImportFunctions(text:String):String {
        val imported=try {FunctionTransfer.decode(text)} catch(e:Exception) {error=e.message ?: "Could not import functions";return ""}
        val updated=JSONObject(functions.toString())
        var added=0;var replaced=0
        imported.definitions.forEach {definition->
            if(updated.has(definition.name))replaced++ else added++
            updated.put(definition.name,definition.json())
        }
        functions=updated;error="";save()
        val details=mutableListOf<String>()
        if(added>0)details.add("$added new")
        if(replaced>0)details.add("$replaced replaced")
        if(imported.skipped>0)details.add("${imported.skipped} skipped")
        return "Imported ${imported.definitions.size} function${if(imported.definitions.size==1)"" else "s"}: ${details.joinToString(", ")}"
    }
    fun CalculatorModel.performAssume(name: String, assumption: String) { assumptions=JSONObject(assumptions.toString()).put(name,JSONArray(if(assumption=="none") emptyList<String>() else listOf(assumption))); save() }
    fun CalculatorModel.performRemoveVariable(name: String) { variables=JSONObject(variables.toString()).apply { remove(name) }; functions=JSONObject(functions.toString()).apply { remove(name) }; save() }
    fun CalculatorModel.performDeleteAllVariables() { variables=JSONObject();lastAnswerResult=null;inputAnswer=null;answerDisplay=null;save() }
}

package com.kirinonakar.calcmax.calculator

import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.math.Expr
import org.json.JSONArray
import org.json.JSONObject

/** JSON transfer format for the saved custom function library. */
object FunctionTransfer {
    const val FORMAT="calcmax.functions"
    const val VERSION=1
    private val identifier=Regex("[A-Za-z][A-Za-z0-9_]*")
private val reserved=("sinc sin cos tan asin acos atan arcsin arccos arctan sinh cosh tanh asinh acosh atanh arcsinh arsinh arccosh arcosh arctanh artanh atan2 arctan2 sqrt cbrt nthroot abs floor ceil round roundh sign factorial gamma ln log exp erf erfc Ei Si Ci zeta re im arg conj polar rectpolar simplify expand factor collect diff integrate limit series solve nsolve sum product piecewise subs gcd lcm nCr nPr prime isprime factorint divisors percent degree quotient remainder mod divmod det inverse transpose rank trace rref ref lu eigenvalues eigenvectors norm normalize dot cross angle projection linsolve mean median variance stdev sumdata quartiles stats regression convert qty nintegrate nderivative minimum maximum rad gradian pinv ctranspose svd roots real_roots rsolve lambertw beta digamma polygamma fibonacci lucas bernoulli harmonic subfactorial totient divisor_sigma primepi nextprime prevprime besselj bessely besseli besselk normpdf normcdf invnorm tpdf tcdf invt chi2pdf chi2cdf fpdf fcdf binompdf binomcdf poissonpdf poissoncdf geometpdf geometcdf exppdf expcdf unifpdf unifcdf gammapdf gammacdf betapdf betacdf lognormpdf lognormcdf hgeompdf hgeomcdf nbinompdf nbinomcdf weibullpdf weibullcdf cauchypdf cauchycdf invcauchy ttest ttest2 ttestpaired ztest ztest2 chi2test chi2independence fisherexact anova shapiro tinterval zinterval tvmfv tvmpv tvmpmt tvmn tvmrate npv irr amort cagr normalpdf normalcdf".split(' ')).toSet()

    class Definition(val name:String,val parameters:List<String>,val source:String,val body:JSONObject,val answerSource:JSONObject?=null) {
        fun json()=JSONObject().put("parameters",JSONArray(parameters)).put("source",source).put("body",body).apply {answerSource?.let {put("answerSource",it)}}
    }
    data class Import(val definitions:List<Definition>,val skipped:Int)
    data class ResultTarget(val expression:Expr,val name:String?=null,val parameters:List<String> = emptyList())
    data class InputAssignment(val expression:Expr,val name:String,val parameters:List<String>?=null)

    fun graphExpression(tree:Expr):Expr {
        val head=tree.args.firstOrNull()
        return if(tree.kind=="relation" && tree.value in listOf("=","==") && tree.args.size==2 &&
            head?.kind=="call" && head.value !in reserved && head.args.size==1 &&
            head.args[0].kind=="symbol" && head.args[0].value=="x")tree.args[1]else tree
    }

    /** Prefer the existing leading definition; a built-in call is an expression, not a new function. */
    fun inputAssignment(tree:Expr):InputAssignment? {
        if(tree.value !in listOf("=",":=") || tree.args.size!=2)return null
        val (left,right)=tree.args
        fun functionHead(node:Expr)=node.kind=="call" && node.args.all {it.kind=="symbol"}
        val constants=setOf("pi","e","i","I","oo","Ans","c0","hP","hbar","G","qe","NA","kB0","me","mp0")
        val forward=left.kind=="symbol" || functionHead(left)&&(left.value !in reserved ||
            right.kind=="symbol"&&left.args.any {it.value==right.value})
        val target=if(forward)left else if(right.kind=="symbol"&&right.value !in constants || functionHead(right)&&right.value !in reserved)right else left
        val expression=if(target===right)left else right
        return when {
            target.kind=="symbol"->InputAssignment(expression,target.value)
            functionHead(target)->InputAssignment(expression,target.value,target.args.map {it.value})
            else->null
        }
    }

    fun resultTarget(tree:Expr):ResultTarget? {
        if(tree.value !in listOf("=",":=") || tree.args.size!=2)return null
        val (left,right)=tree.args
        if(left.kind=="call" && left.value=="Ans") {
            require(left.args.all {it.kind=="symbol"}) {"Enter distinct valid parameter names"}
            val parameters=left.args.map {it.value}
            definition("Ans",parameters,"0")
            return ResultTarget(right,parameters=parameters)
        }
        val reverse=right.kind=="call" && right.value in listOf("diff","integrate") && (left.kind=="call" || left.kind=="symbol" && left.value=="Ans")
        val expression=if(reverse)right else left
        val target=if(reverse)left else right
        val calculus=expression.kind=="call" && expression.value in listOf("diff","integrate")
        if(!(calculus || expression.kind=="symbol" && expression.value=="Ans"))return null
        if(target.kind=="symbol" && target.value=="Ans" && calculus)return ResultTarget(expression)
        if(target.kind!="call")return null
        require(target.args.all {it.kind=="symbol"}) {"Enter distinct valid parameter names"}
        val parameters=target.args.map {it.value}
        definition(target.value,parameters,"0")
        return ResultTarget(expression,target.value,parameters)
    }

    fun resultDefinition(name:String,parameters:List<String>,body:JSONObject):Definition {
        fun editable(node:JSONObject):JSONObject {
            val copy=JSONObject(node.toString())
            if(copy.optString("kind")=="snapshot_symbol" && copy.optString("value") in parameters)copy.put("kind","symbol")
            copy.optJSONArray("args")?.let {args->for(index in 0 until args.length())args.put(index,editable(args.getJSONObject(index)))}
            return copy
        }
        val stored=editable(body)
        val source=editableSource(stored)
        definition(name,parameters,source)
        return Definition(name,parameters,source,stored)
    }

    private fun answerKey(value:Any?):String = when(value) {
        is JSONObject->value.keys().asSequence().toList().sorted().joinToString(",","{","}") {key->JSONObject.quote(key)+":"+answerKey(value.opt(key))}
        is JSONArray->(0 until value.length()).joinToString(",","[","]") {answerKey(value.opt(it))}
        is String->JSONObject.quote(value)
        else->value?.toString() ?: "null"
    }

    fun removeExpiredAnswerFunctions(functions:JSONObject,answer:JSONObject?):JSONObject {
        val current=answerKey(answer)
        val expired=functions.keys().asSequence().filter {name->
            val definition=functions.optJSONObject(name)
            definition?.has("answerSource")==true && answerKey(definition.opt("answerSource"))!=current
        }.toList()
        if(expired.isEmpty())return functions
        return JSONObject(functions.toString()).apply {expired.forEach {remove(it)}}
    }

    fun definition(name:String,parameters:String,source:String):Definition = definition(name,parameters.split(',').map {it.trim()},source)

    fun definition(name:String,parameters:List<String>,source:String):Definition {
        val clean=name.trim()
        require(clean.matches(identifier))
        require(clean !in reserved) {"This function name is reserved"}
        require(parameters.isNotEmpty() && parameters.all {it.matches(identifier)} && parameters.distinct().size==parameters.size)
        return Definition(clean,parameters,source,JSONObject(Parser(source).parse().json()))
    }

    fun encode(functions:JSONObject):String {
        val content=JSONObject()
        functions.keys().asSequence().sorted().forEach {name->
            val definition=functions.optJSONObject(name) ?: return@forEach
            val source=runCatching {storedSource(definition)}.getOrDefault("")
            if(source.isBlank())return@forEach
            content.put(name,JSONObject().put("parameters",definition.optJSONArray("parameters") ?: JSONArray()).put("source",source).apply {definition.optJSONObject("answerSource")?.let {put("answerSource",it)}})
        }
        return JSONObject().put("format",FORMAT).put("version",VERSION).put("functions",content).toString(2)
    }

    fun decode(text:String):Import {
        val root=try {JSONObject(text.removePrefix("\uFEFF").trim())} catch(e:Exception) {throw IllegalArgumentException("This file is not valid JSON")}
        val format=root.optString("format")
        require(format.isEmpty()||format==FORMAT) {"Unsupported function file"}
        val entries=if(format==FORMAT)root.optJSONObject("functions") ?: JSONObject() else root
        val definitions=mutableListOf<Definition>()
        var skipped=0
        entries.keys().asSequence().sorted().forEach {key->
            val entry=entries.optJSONObject(key)?.let {value->runCatching {importDefinition(key,value)}.getOrNull()}
            if(entry==null)skipped++ else definitions.add(entry)
        }
        require(definitions.isNotEmpty()) {"No valid functions in this file"}
        return Import(definitions,skipped)
    }

    fun storedSource(definition:JSONObject):String = definition.optString("source").ifBlank {definition.optJSONObject("body")?.let(::editableSource).orEmpty()}

    fun editableSource(node:JSONObject):String {
        val args=node.optJSONArray("args")
        val parts=(0 until (args?.length() ?: 0)).map{editableSource(args!!.getJSONObject(it))}
        val value=node.optString("value")
        return when(node.optString("kind")){
            "call","frozen_call"->"$value(${parts.joinToString(",")})"
            "restricted"->parts[0]
            "constant"->when(value){"E"->"e";"I"->"i";else->value}
            "binary","relation"->"(${parts[0]}$value${parts[1]})"
            "unary"->"$value(${parts[0]})"
            "list"->"[${parts.joinToString(",")}]"
            "tuple"->"(${parts.joinToString(",")}${if(parts.size==1) "," else ""})"
            "group"->"(${parts[0]})"
            else->value
        }
    }

    private fun importDefinition(name:String,entry:JSONObject):Definition {
        val array=entry.optJSONArray("parameters")
        val parameters=when {
            array!=null->(0 until array.length()).map {array.optString(it)}
            entry.optString("parameters").isNotBlank()->entry.optString("parameters").split(',').map {it.trim()}
            else->emptyList()
        }
        val imported=definition(name,parameters,storedSource(entry))
        return Definition(imported.name,imported.parameters,imported.source,imported.body,entry.optJSONObject("answerSource"))
    }
}

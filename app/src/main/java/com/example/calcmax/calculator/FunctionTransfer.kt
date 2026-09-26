package com.example.calcmax.calculator

import com.example.calcmax.math.Parser
import org.json.JSONArray
import org.json.JSONObject

/** JSON transfer format for the saved custom function library. */
object FunctionTransfer {
    const val FORMAT="calcmax.functions"
    const val VERSION=1
    private val identifier=Regex("[A-Za-z][A-Za-z0-9_]*")
    private val reserved=("sinc sin cos tan asin acos atan arcsin arccos arctan sinh cosh tanh asinh acosh atanh arcsinh arsinh arccosh arcosh arctanh artanh atan2 arctan2 sqrt cbrt nthroot abs floor ceil round sign factorial gamma ln log exp erf erfc Ei Si Ci zeta re im arg conj polar rectpolar simplify expand factor collect diff integrate limit series solve nsolve sum product piecewise subs gcd lcm nCr nPr prime isprime factorint divisors percent degree quotient remainder det inverse transpose rank trace rref ref lu eigenvalues eigenvectors norm normalize dot cross angle projection linsolve mean median variance stdev sumdata quartiles stats regression convert qty nintegrate nderivative minimum maximum normpdf normcdf invnorm tpdf tcdf invt chi2pdf chi2cdf fpdf fcdf binompdf binomcdf poissonpdf poissoncdf geometpdf geometcdf ttest ztest chi2test anova tinterval zinterval tvmfv tvmpv tvmpmt tvmn tvmrate npv irr amort normalpdf normalcdf".split(' ')).toSet()

    class Definition(val name:String,val parameters:List<String>,val source:String,val body:JSONObject) {
        fun json()=JSONObject().put("parameters",JSONArray(parameters)).put("source",source).put("body",body)
    }
    data class Import(val definitions:List<Definition>,val skipped:Int)

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
            content.put(name,JSONObject().put("parameters",definition.optJSONArray("parameters") ?: JSONArray()).put("source",source))
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
            "call"->"$value(${parts.joinToString(",")})"
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
        return definition(name,parameters,storedSource(entry))
    }
}

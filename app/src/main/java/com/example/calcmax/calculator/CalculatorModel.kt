package com.example.calcmax.calculator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import com.example.calcmax.math.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val id: Long, val source: String, val exact: String, val decimal: String, val mode: String, val favorite: Boolean = false)
class CalculatorModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("calculator",0)
    private val engine = EngineClient(application)
    var editor by mutableStateOf(Editor(prefs.getString("expression","") ?: "",prefs.getInt("cursor",0)))
        private set
    var result by mutableStateOf<JSONObject?>(null)
        private set
    var error by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var shift by mutableStateOf(false)
    var alpha by mutableStateOf(false)
    var mode by mutableStateOf(prefs.getString("mode","Scientific") ?: "Scientific")
    var angle by mutableStateOf(prefs.getString("angle","DEG") ?: "DEG")
    var theme by mutableStateOf(prefs.getString("theme","System") ?: "System")
    var precision by mutableIntStateOf(prefs.getInt("precision",30))
    var decimal by mutableStateOf(false)
    var haptics by mutableStateOf(prefs.getBoolean("haptics",true))
    var sound by mutableStateOf(prefs.getBoolean("sound",false))
    var persistHistory by mutableStateOf(prefs.getBoolean("historyEnabled",true))
    var history by mutableStateOf(loadHistory())
        private set
    var variables by mutableStateOf(loadObject("variables"))
        private set
    var functions by mutableStateOf(loadObject("functions"))
        private set
    var assumptions by mutableStateOf(loadObject("assumptions"))
        private set
    var graphSource by mutableStateOf(prefs.getString("graphSource","sin(x)\ncos(x)") ?: "sin(x)\ncos(x)")
    var graphKind by mutableStateOf(prefs.getString("graphKind","cartesian") ?: "cartesian")
    var xMin by mutableDoubleStateOf(prefs.getString("xMin","-10")!!.toDouble())
    var xMax by mutableDoubleStateOf(prefs.getString("xMax","10")!!.toDouble())
    var yMin by mutableDoubleStateOf(prefs.getString("yMin","-5")!!.toDouble())
    var yMax by mutableDoubleStateOf(prefs.getString("yMax","5")!!.toDouble())
    var graphData by mutableStateOf<JSONObject?>(null)
    var graphBusy by mutableStateOf(false)
    var trace by mutableStateOf<Pair<Double,Double>?>(null)
    var parameterMin by mutableDoubleStateOf(prefs.getString("parameterMin","0")!!.toDouble())
    var parameterMax by mutableDoubleStateOf(prefs.getString("parameterMax","6.283185307179586")!!.toDouble())
    var shadedInterval by mutableStateOf<Pair<Double,Double>?>(null)
    var constants by mutableStateOf<JSONArray?>(null)
        private set
    private var job: Job? = null
    private var graphJob: Job? = null
    private fun loadObject(key: String) = runCatching { JSONObject(prefs.getString(key,"{}")!!) }.getOrDefault(JSONObject())
    private fun loadHistory(): List<HistoryEntry> = runCatching {
        val array = JSONArray(prefs.getString("history","[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { HistoryEntry(it.getLong("id"),it.getString("source"),it.getString("exact"),it.getString("decimal"),it.getString("mode"),it.optBoolean("favorite")) } }
    }.getOrDefault(emptyList())
    fun save() {
        prefs.edit().putString("expression",editor.source).putInt("cursor",editor.cursor).putString("mode",mode).putString("angle",angle).putString("theme",theme)
            .putInt("precision",precision).putBoolean("haptics",haptics).putBoolean("sound",sound).putBoolean("historyEnabled",persistHistory)
            .putString("variables",variables.toString()).putString("functions",functions.toString()).putString("assumptions",assumptions.toString())
            .putString("graphSource",graphSource).putString("graphKind",graphKind).putString("xMin",xMin.toString()).putString("xMax",xMax.toString()).putString("yMin",yMin.toString()).putString("yMax",yMax.toString())
            .putString("parameterMin",parameterMin.toString()).putString("parameterMax",parameterMax.toString())
            .putString("history",if(persistHistory) JSONArray(history.map { JSONObject().put("id",it.id).put("source",it.source).put("exact",it.exact).put("decimal",it.decimal).put("mode",it.mode).put("favorite",it.favorite) }).toString() else "[]").apply()
    }
    fun edit(value: Editor) { editor = value; error=""; save() }
    fun insert(text: String, inside: Int = text.length) { edit(editor.insert(text,inside)) }
    fun clear() { edit(Editor()); result=null; error=""; shift=false; alpha=false }
    fun request(action: String = "evaluate") = JSONObject().put("action",action).put("precision",precision).put("angle",angle).put("variables",variables).put("functions",functions).put("assumptions",assumptions)
    fun calculate(source: String = editor.source) {
        if(busy) return
        val tree = try { Parser(source).parse() } catch(e: Exception) { error=e.message ?: "Syntax ERROR"; return }
        if(tree.value in listOf("=",":=") && tree.args.size==2 && mode!="Equations") {
            val left=tree.args[0];val right=tree.args[1]
            if(left.kind=="symbol") {store(left.value,source.substring(right.start,right.end));return}
            if(left.kind=="call" && left.args.all {it.kind=="symbol"}) {define(left.value,left.args.joinToString(","){it.value},source.substring(right.start,right.end));return}
        }
        job = viewModelScope.launch {
            busy=true; error=""
            try {
                val response = engine.execute(request().put("tree",JSONObject(tree.json())))
                if(response.optBoolean("ok")) {
                    result=response
                    val exact=response.optString("exact"); val approx=response.optString("decimal")
                    val next=JSONObject(variables.toString())
                    if(response.has("resultAst")) next.put("Ans",response.getJSONObject("resultAst")) else next.remove("Ans")
                    variables=next
                    history=(listOf(HistoryEntry(System.currentTimeMillis(),source,exact,approx,mode))+history).take(500)
                    save()
                } else error=response.optString("error","Math ERROR")
            } finally { busy=false }
        }
    }
    fun cancel() { job?.cancel(); graphJob?.cancel(); engine.cancel(); busy=false; graphBusy=false; error="Calculation cancelled" }
    fun transform(operation: String) { val source=editor.source.ifBlank { "Ans" }; edit(Editor("$operation($source)")); calculate() }
    fun store(name: String, source: String = editor.source.ifBlank { "Ans" }) {
        try {
            require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) { "Use a letter followed by letters, digits or underscores" }
            val tree=Parser(source).parse()
            require(name !in listOf("pi","e","i","I","oo","Ans","c0","hP","hbar","G","qe","NA","kB0","me","mp0")) { "Reserved constant or answer name" }
            if(busy) return
            job=viewModelScope.launch {
                busy=true
                try {
                    val response=engine.execute(request().put("tree",JSONObject(tree.json())))
                    if(response.optBoolean("ok") && response.has("resultAst")) {
                        val stored=response.getJSONObject("resultAst")
                        variables=JSONObject(variables.toString()).put(name,stored)
                        result=response.put("note","Stored in $name")
                        error="";save()
                    } else error=response.optString("error","This result cannot be stored")
                } finally {busy=false}
            }
        } catch(e: Exception) { error=e.message ?: "Invalid variable" }
    }
    fun define(name: String, parameters: String, source: String) {
        try {
            require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]*")))
            require(name !in ("sin cos tan asin acos atan sinh cosh tanh asinh acosh atanh sqrt cbrt nthroot abs floor ceil round sign factorial gamma ln log exp erf erfc Ei Si Ci zeta re im arg conj polar rectpolar simplify expand factor collect diff integrate limit series solve nsolve sum product piecewise subs gcd lcm nCr nPr prime factorization divisors percent degree quotient remainder det inverse transpose rank trace rref ref lu eigenvalues eigenvectors norm normalize dot cross angle projection linsolve mean median variance stdev sumdata quartiles stats regression convert qty nintegrate nderivative minimum maximum".split(' '))) {"This function name is reserved"}
            val names=parameters.split(',').map { it.trim() }; require(names.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) } && names.distinct().size==names.size)
            functions=JSONObject(functions.toString()).put(name,JSONObject().put("parameters",JSONArray(names)).put("body",JSONObject(Parser(source).parse().json()))); save()
            error=""
            val message="$name(${names.joinToString()}) defined"
            result=JSONObject().put("exact",message).put("decimal",message).put("tree",JSONObject().put("kind","text").put("value",message))
        } catch(e: Exception) { error=e.message ?: "Invalid function" }
    }
    fun assume(name: String, assumption: String) { assumptions=JSONObject(assumptions.toString()).put(name,JSONArray(if(assumption=="none") emptyList<String>() else listOf(assumption))); save() }
    fun removeVariable(name: String) { variables=JSONObject(variables.toString()).apply { remove(name) }; functions=JSONObject(functions.toString()).apply { remove(name) }; save() }
    fun favorite(id: Long) { history=history.map { if(it.id==id) it.copy(favorite=!it.favorite) else it }; save() }
    fun deleteHistory(id: Long) { history=history.filter { it.id!=id }; save() }
    fun clearHistory() { history=emptyList(); save() }
    fun plot() {
        graphJob?.cancel()
        val trees=try { graphSource.lines().filter { it.isNotBlank() }.take(6).map { JSONObject(Parser(it).parse().json()) } } catch(e: Exception) { error=e.message ?: "Syntax ERROR"; return }
        if(trees.isEmpty()) { error="Enter a function"; return }
        graphJob=viewModelScope.launch {
            graphBusy=true; error=""
            try {
                val response=engine.execute(request("graph").put("angle","RAD").put("trees",JSONArray(trees)).put("graphKind",graphKind).put("variable",if(graphKind=="cartesian") "x" else "t").put("min",if(graphKind=="cartesian")xMin else parameterMin).put("max",if(graphKind=="cartesian")xMax else parameterMax).put("samples",500))
                if(response.optBoolean("ok")) graphData=response else error=response.optString("error")
                save()
            } finally { graphBusy=false }
        }
    }
    fun program(a: String,b: String,base: Int,width: Int,signed: Boolean,op: String) {
        if(busy) return
        job=viewModelScope.launch {
            busy=true; error=""
            try { val r=engine.execute(request("programmer").put("a",a).put("b",b.ifBlank { "0" }).put("base",base).put("width",width).put("signed",signed).put("op",op)); if(r.optBoolean("ok")) result=r else error=r.optString("error") }
            finally { busy=false }
        }
    }
    fun loadConstants() {
        if(constants!=null) return
        viewModelScope.launch {
            val response=engine.execute(request("constants"))
            if(response.optBoolean("ok")) constants=response.getJSONArray("constants") else error=response.optString("error")
        }
    }
    override fun onCleared() { save(); engine.close(); super.onCleared() }
}

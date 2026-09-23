package com.example.calcmax.calculator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import com.example.calcmax.math.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val id: Long, val source: String, val exact: String, val decimal: String, val mode: String, val favorite: Boolean = false,val inputTree:String="",val response:String="",val answer:String="")
data class TapeEntry(val source: String,val input: String,val result: String,val answer:String="")
class CalculatorModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("calculator",0)
    private val engine = EngineClient(application)
    private val exchangeRepository by lazy{ExchangeRepository(application)}
    var exchangeRates by mutableStateOf<RateTable?>(null)
        private set
    var exchangeBusy by mutableStateOf(false)
        private set
    var exchangeStatus by mutableStateOf("")
        private set
    var editor by mutableStateOf(Editor(prefs.getString("expression","") ?: "",prefs.getInt("cursor",0)))
        private set
    var result by mutableStateOf<JSONObject?>(loadObject("result").takeIf{it.has("exact")})
        private set
    var tape by mutableStateOf<List<TapeEntry>>(emptyList())
        private set
    var committed by mutableStateOf(prefs.getBoolean("committed",false))
        private set
    var answerDisplay by mutableStateOf<JSONObject?>(loadObject("answerDisplay").takeIf{it.has("kind")})
        private set
    var previewBusy by mutableStateOf(false)
        private set
    var inputVersion by mutableIntStateOf(0)
        private set
    var poweredOn by mutableStateOf(true)
    var hyperbolic by mutableStateOf(false)
    var overwrite by mutableStateOf(false)
    var secondKeys by mutableStateOf(false)
    var mixedNumbers by mutableStateOf(false)
    private var previewRunner: Job?=null
    private var commitRequested=false
    private var resultSource=prefs.getString("resultSource","") ?: ""
    private var resultVersion=if(resultSource==editor.source&&result!=null)0 else -1
    private var inputAnswer:JSONObject?=loadObject("inputAnswer").takeIf{it.has("kind")}
    private var lastAnswerResult:JSONObject?=loadObject("lastAnswerResult").takeIf{it.has("exact")}
    var error by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var shift by mutableStateOf(false)
    var alpha by mutableStateOf(false)
    var mode by mutableStateOf(prefs.getString("mode","Scientific") ?: "Scientific")
    var angle by mutableStateOf(prefs.getString("angle","DEG") ?: "DEG")
    var theme by mutableStateOf(prefs.getString("theme","System") ?: "System")
    var precision by mutableIntStateOf(prefs.getInt("precision",30))
    var inputFont by mutableFloatStateOf(prefs.getFloat("inputFont",25f))
    var outputFont by mutableFloatStateOf(prefs.getFloat("outputFont",28f))
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
    var radianAxis by mutableStateOf(prefs.getBoolean("radianAxis",false))
    var constants by mutableStateOf<JSONArray?>(null)
        private set
    private var job: Job? = null
    private var graphJob: Job? = null
    init {
        tape=history.filterIndexed{index,entry->entry.id>prefs.getLong("screenClearedAt",0)&&(index!=0||!committed||entry.source!=editor.source)}.take(100).asReversed().mapNotNull {entry->
            runCatching {
                val input=entry.inputTree.ifBlank {Parser(entry.source,true).parse().json()}
                val response=entry.response.ifBlank {JSONObject().put("exact",entry.exact).put("decimal",entry.decimal).put("tree",JSONObject(Parser(entry.exact,true).parse().json())).toString()}
                TapeEntry(entry.source,input,response,entry.answer)
            }.getOrNull()
        }
        if(!committed&&editor.source.isNotBlank())schedulePreview()
    }
    private fun loadObject(key: String) = runCatching { JSONObject(prefs.getString(key,"{}")!!) }.getOrDefault(JSONObject())
    private fun loadHistory(): List<HistoryEntry> = runCatching {
        val array = JSONArray(prefs.getString("history","[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { HistoryEntry(it.getLong("id"),it.getString("source"),it.getString("exact"),it.getString("decimal"),it.getString("mode"),it.optBoolean("favorite"),it.optString("inputTree"),it.optString("response"),it.optString("answer")) } }
    }.getOrDefault(emptyList())
    fun save() {
        prefs.edit().putString("expression",editor.source).putInt("cursor",editor.cursor).putString("mode",mode).putString("angle",angle).putString("theme",theme)
            .putString("result",result?.toString() ?: "{}").putString("resultSource",resultSource).putBoolean("committed",committed)
            .putString("inputAnswer",inputAnswer?.toString() ?: "{}").putString("answerDisplay",answerDisplay?.toString() ?: "{}").putString("lastAnswerResult",lastAnswerResult?.toString() ?: "{}")
            .putInt("precision",precision).putBoolean("haptics",haptics).putBoolean("sound",sound).putBoolean("historyEnabled",persistHistory)
            .putFloat("inputFont",inputFont).putFloat("outputFont",outputFont)
            .putString("variables",variables.toString()).putString("functions",functions.toString()).putString("assumptions",assumptions.toString())
            .putString("graphSource",graphSource).putString("graphKind",graphKind).putString("xMin",xMin.toString()).putString("xMax",xMax.toString()).putString("yMin",yMin.toString()).putString("yMax",yMax.toString())
            .putString("parameterMin",parameterMin.toString()).putString("parameterMax",parameterMax.toString())
            .putBoolean("radianAxis",radianAxis)
            .putString("history",if(persistHistory) JSONArray(history.map { JSONObject().put("id",it.id).put("source",it.source).put("exact",it.exact).put("decimal",it.decimal).put("mode",it.mode).put("favorite",it.favorite).put("inputTree",it.inputTree).put("response",it.response).put("answer",it.answer) }).toString() else "[]").apply()
    }
    fun inputTree(): JSONObject? {
        val tree=editor.tree() ?: return null
        fun replace(node: JSONObject):JSONObject {
            val copy=JSONObject(node.toString())
            if(copy.optString("kind")=="symbol" && copy.optString("value")=="Ans" && answerDisplay!=null) {
                copy.put("kind","answer").put("args",JSONArray().put(answerDisplay))
            } else copy.optJSONArray("args")?.let {children->for(i in 0 until children.length())children.put(i,replace(children.getJSONObject(i)))}
            return copy
        }
        return replace(JSONObject(tree.json()))
    }
    fun edit(value: Editor) {
        val changed=value.source!=editor.source
        editor=value;error="";committed=false
        if(changed) {
            inputVersion++;commitRequested=false;busy=false
            val tree=runCatching {Parser(value.source,true).parse()}.getOrNull()
            if(tree!=null&&requiresExplicitEvaluation(tree,multiArgumentUserFunctions())) {result=null;resultSource="";resultVersion=-1}
            schedulePreview()
        }
        prefs.edit().putString("expression",editor.source).putInt("cursor",editor.cursor).putBoolean("committed",committed)
            .putString("inputAnswer",inputAnswer?.toString() ?: "{}").putString("answerDisplay",answerDisplay?.toString() ?: "{}").apply()
    }
    private fun nextEntry() {
        if(!committed) return
        result?.let {tape=(tape+TapeEntry(editor.source,inputTree()?.toString() ?: "{}",it.toString(),inputAnswer?.toString() ?: "")).takeLast(300)}
        committed=false;editor=Editor();result=null;resultSource="";inputAnswer=null
    }
    fun insert(text: String, inside: Int = text.length) {
        if(!poweredOn)return
        if(committed) {
            val last=result?.optJSONObject(if(decimal)"decimalTree" else "tree") ?: result?.optJSONObject("tree")
            nextEntry();answerDisplay=last
            if((text in listOf("+","-","−","*","×","/","÷","!","%","°","∠") || text.startsWith("^")) && variables.has("Ans")) {editor=Editor("Ans");inputAnswer=variables.getJSONObject("Ans")}
        }
        if(text=="Ans") {inputAnswer=variables.optJSONObject("Ans");answerDisplay=lastAnswerResult?.optJSONObject(if(decimal)"decimalTree" else "tree") ?: inputAnswer}
        val target=if(overwrite&&editor.cursor==editor.anchor)editor.copy(anchor=(editor.cursor+text.length).coerceAtMost(editor.source.length)) else editor
        edit(target.insert(text,inside))
    }
    fun clear() {
        inputVersion++;commitRequested=false;committed=false;editor=Editor();result=null;resultSource="";error="";shift=false;alpha=false;hyperbolic=false;answerDisplay=null;inputAnswer=null;busy=false;save()
    }
    fun fresh() {nextEntry();edit(Editor())}
    fun fraction() {
        if(committed) {
            val previous=result?.optJSONObject("tree")
            nextEntry();inputAnswer=variables.optJSONObject("Ans");answerDisplay=previous
            if(inputAnswer!=null)editor=Editor("Ans")
        }
        var target=editor
        if(target.cursor==target.anchor) {
            val previous=target.tree()?.nodes()?.filter{it.end==target.cursor && it.start<it.end}?.minByOrNull{it.end-it.start}
            if(previous!=null)target=target.select(previous)
        }
        val a=minOf(target.cursor,target.anchor);val b=maxOf(target.cursor,target.anchor)
        val numerator=target.source.substring(a,b)
        val template="($numerator)/()"
        edit(target.insert(template,if(numerator.isEmpty())1 else template.length-1))
    }
    fun angleSuffix(function:String) {
        if(committed){val display=result?.optJSONObject("tree");nextEntry();inputAnswer=variables.optJSONObject("Ans");answerDisplay=display;editor=Editor("Ans")}
        var target=editor
        if(target.cursor==target.anchor)target.tree()?.nodes()?.filter{it.end==target.cursor&&it.start<it.end}?.minByOrNull{it.end-it.start}?.let{target=target.select(it)}
        val selected=target.source.substring(minOf(target.anchor,target.cursor),maxOf(target.anchor,target.cursor))
        edit(target.insert("$function($selected)"))
    }
    fun resetSetup(){angle="DEG";precision=30;decimal=false;mixedNumbers=false;overwrite=false;clear();save()}
    fun clearMemory(){variables=JSONObject();functions=JSONObject();assumptions=JSONObject();lastAnswerResult=null;clear();save()}
    fun clearAllScreen(){cancel();tape=emptyList();variables=JSONObject();lastAnswerResult=null;poweredOn=true;prefs.edit().putLong("screenClearedAt",System.currentTimeMillis()).apply();clear();save()}
    fun reuse(entry:TapeEntry) {nextEntry();inputAnswer=entry.answer.takeIf{it.isNotEmpty()}?.let(::JSONObject);answerDisplay=inputAnswer;edit(Editor(entry.source))}
    fun recalculatePreview() {inputVersion++;schedulePreview()}
    private fun multiArgumentUserFunctions():Set<String> = functions.keys().asSequence().filter {name->
        (functions.optJSONObject(name)?.optJSONArray("parameters")?.length() ?: 0)>1
    }.toSet()
    private fun schedulePreview() {
        if(mode !in listOf("Scientific","CAS","Equations") || previewRunner?.isActive==true)return
        previewRunner=viewModelScope.launch {
            try {
                delay(100)
                while(true) {
                    val revision=inputVersion;val source=editor.source
                    val tree=runCatching {calculationTree(source)}.getOrNull()
                    if(tree!=null && (commitRequested||!requiresExplicitEvaluation(tree,multiArgumentUserFunctions())) && !(tree.value in listOf("=",":=") && tree.args.firstOrNull()?.kind in listOf("symbol","call") && mode!="Equations")) {
                        previewBusy=true
                        val response=engine.execute(request().put("tree",JSONObject(tree.json())).put("budget",if(commitRequested)8 else 2))
                        if(revision==inputVersion && source==editor.source && !committed) {
                            if(response.optBoolean("ok")) {
                                result=response;resultSource=source;resultVersion=revision;error=""
                                if(commitRequested)commit(source,response)
                            } else {error=response.optString("error");resultVersion=-1;commitRequested=false;busy=false}
                        }
                    } else if(commitRequested) {error=runCatching {Parser(source).parse();"Enter a complete expression"}.exceptionOrNull()?.message ?: "Enter a complete expression";commitRequested=false;busy=false}
                    previewBusy=false
                    if(revision==inputVersion)break
                    delay(50)
                }
            } finally {previewBusy=false}
        }
    }
    private fun commit(source:String,response:JSONObject) {
        if(committed)return
        result=response;resultSource=source;committed=true;commitRequested=false;busy=false
        val next=JSONObject(variables.toString())
        if(response.has("resultAst"))next.put("Ans",response.getJSONObject("resultAst")) else next.remove("Ans")
        variables=next
        lastAnswerResult=response
        history=(listOf(HistoryEntry(System.currentTimeMillis(),source,response.optString("exact"),response.optString("decimal"),mode,inputTree=inputTree()?.toString() ?: "",response=response.toString(),answer=inputAnswer?.toString() ?: ""))+history).take(500)
        save()
    }
    fun request(action: String = "evaluate") = JSONObject().put("action",action).put("precision",precision).put("angle",angle).put("variables",JSONObject(variables.toString()).apply{inputAnswer?.let{put("Ans",it)}}).put("functions",functions).put("assumptions",assumptions)
    private fun calculationTree(source:String):Expr {
        val tree=Parser(source,true).parse()
        if(tree.nodes().any {it.kind=="hole"})throw SyntaxException("Complete the empty expression slots",source.length)
        return tree
    }
    fun calculate(source: String = editor.source) {
        if(committed && source==editor.source)return
        val tree = try { calculationTree(source) } catch(e: Exception) { error=e.message ?: "Syntax ERROR"; return }
        if(tree.value in listOf("=",":=") && tree.args.size==2 && mode!="Equations") {
            val left=tree.args[0];val right=tree.args[1]
            if(left.kind=="symbol") {store(left.value,source.substring(right.start,right.end));return}
            if(left.kind=="call" && left.args.all {it.kind=="symbol"}) {define(left.value,left.args.joinToString(","){it.value},source.substring(right.start,right.end));return}
        }
        if(mode in listOf("Scientific","CAS","Equations") && source==editor.source) {
            if(resultSource==source && resultVersion==inputVersion && result?.optBoolean("ok")==true) commit(source,result!!)
            else {commitRequested=true;busy=true;schedulePreview()}
            return
        }
        if(busy)return
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
    fun cancel() { inputVersion++;commitRequested=false;previewRunner?.cancel();job?.cancel(); graphJob?.cancel(); engine.cancel(); busy=false; graphBusy=false;previewBusy=false;error="Calculation cancelled" }
    fun transform(operation: String) { val source=editor.source.ifBlank { "Ans" }; edit(Editor("$operation($source)")); calculate() }
    fun store(name: String, source: String = editor.source.ifBlank { "Ans" },showResult:Boolean=true) {
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
                        inputVersion++;resultVersion=-1
                        if(showResult)result=response.put("note","Stored in $name")
                        error="";save()
                    } else error=response.optString("error","This result cannot be stored")
                } finally {busy=false}
            }
        } catch(e: Exception) { error=e.message ?: "Invalid variable" }
    }
    fun memory(direction:Int,source:String=editor.source) {
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
    fun define(name: String, parameters: String, source: String, showResult:Boolean=true) {
        try {
            require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]*")))
            require(name !in ("sinc sin cos tan asin acos atan sinh cosh tanh asinh acosh atanh sqrt cbrt nthroot abs floor ceil round sign factorial gamma ln log exp erf erfc Ei Si Ci zeta re im arg conj polar rectpolar simplify expand factor collect diff integrate limit series solve nsolve sum product piecewise subs gcd lcm nCr nPr prime factorization divisors percent degree quotient remainder det inverse transpose rank trace rref ref lu eigenvalues eigenvectors norm normalize dot cross angle projection linsolve mean median variance stdev sumdata quartiles stats regression convert qty nintegrate nderivative minimum maximum".split(' '))) {"This function name is reserved"}
            val names=parameters.split(',').map { it.trim() }; require(names.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) } && names.distinct().size==names.size)
            functions=JSONObject(functions.toString()).put(name,JSONObject().put("parameters",JSONArray(names)).put("source",source).put("body",JSONObject(Parser(source).parse().json()))); save()
            error=""
            val message="$name(${names.joinToString()}) defined"
            if(showResult)result=JSONObject().put("exact",message).put("decimal",message).put("tree",JSONObject().put("kind","text").put("value",message))
        } catch(e: Exception) { error=e.message ?: "Invalid function" }
    }
    fun assume(name: String, assumption: String) { assumptions=JSONObject(assumptions.toString()).put(name,JSONArray(if(assumption=="none") emptyList<String>() else listOf(assumption))); save() }
    fun removeVariable(name: String) { variables=JSONObject(variables.toString()).apply { remove(name) }; functions=JSONObject(functions.toString()).apply { remove(name) }; save() }
    fun favorite(id: Long) { history=history.map { if(it.id==id) it.copy(favorite=!it.favorite) else it }; save() }
    fun deleteHistory(id: Long) { history=history.filter { it.id!=id }; save() }
    fun clearHistory() { history=emptyList();tape=emptyList();save() }
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
    fun loadExchangeRates() {
        if(exchangeBusy)return
        exchangeRates=exchangeRepository.cached()
        exchangeBusy=true
        viewModelScope.launch {
            try {exchangeRates=exchangeRepository.refresh();exchangeStatus="Cached reference rates · updated at most once every 24 hours"}
            catch(_:Exception){exchangeStatus=if(exchangeRates!=null)"Offline · showing saved reference rates" else "Online rates unavailable · use a manual rate"}
            finally{exchangeBusy=false}
        }
    }
    fun powerTemplate(suffix:String) {
        if(editor.source.isBlank()||editor.source.lastOrNull() in listOf('+','-','−','×','*','÷','/','('))insert("()$suffix",1)
        else insert(suffix,if(suffix=="^()")2 else suffix.length)
    }
    override fun onCleared() { save(); engine.close(); super.onCleared() }
}

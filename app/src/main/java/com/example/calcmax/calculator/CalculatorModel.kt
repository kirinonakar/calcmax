package com.example.calcmax.calculator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import com.example.calcmax.math.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

private const val maxTapeEntries = 10
data class HistoryEntry(val id: Long, val source: String, val exact: String, val decimal: String, val mode: String, val favorite: Boolean = false,val inputTree:String="",val response:String="",val answer:String="")
data class TapeEntry(val source: String,val input: String,val result: String,val answer:String="")
data class CalcSession(val source:String,val names:List<String>,val index:Int=0,val input:Editor=Editor(),val accepted:Map<String,JSONObject> = emptyMap()) {
    val name get()=names[index]
}

internal fun HistoryEntry.toTapeEntry():TapeEntry {
    val input=runCatching {JSONObject(inputTree).takeIf {it.has("kind")}?.toString()}.getOrNull()
        ?: runCatching {Parser(source,true).parse().json()}.getOrNull()
        ?: JSONObject().put("kind","text").put("value",source).toString()
    val result=runCatching {JSONObject(response).takeIf {it.has("exact")||it.has("tree")}?.toString()}.getOrNull()
        ?: JSONObject().put("exact",exact).put("decimal",decimal)
            .put("tree",runCatching {JSONObject(Parser(exact,true).parse().json())}.getOrElse {JSONObject().put("kind","text").put("value",exact)}).toString()
    return TapeEntry(source,input,result,answer)
}
enum class ResultDisplayMode { OFF, ENGINEERING, SCIENTIFIC }
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
    var dmsDisplay by mutableStateOf(result?.optBoolean("dms") == true)
        private set
    var dmsConversion by mutableStateOf(false)
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
    var engineeringConversion by mutableStateOf(false)
        private set
    var engineeringShift by mutableIntStateOf(0)
        private set
    private var previewRunner: Job?=null
    private var commitRequested=false
    private var resultSource=prefs.getString("resultSource","") ?: ""
    private var resultVersion=if(resultSource==editor.source&&result!=null)0 else -1
    private var inputAnswer:JSONObject?=loadObject("inputAnswer").takeIf{it.has("kind")}
    private var lastAnswerResult:JSONObject?=loadObject("lastAnswerResult").takeIf{it.has("exact")}
    var error by mutableStateOf("")
    private var undoHistory by mutableStateOf<List<Editor>>(emptyList())
    private var calcUndoHistory by mutableStateOf<List<Editor>>(emptyList())
    val canUndo get()=if(calcSession!=null)calcUndoHistory.isNotEmpty() else undoHistory.isNotEmpty()
    var calcSession by mutableStateOf<CalcSession?>(null)
        private set
    var lastCalcValues by mutableStateOf<Map<String,JSONObject>>(emptyMap())
        private set
    var lastCalcSource by mutableStateOf("")
        private set
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
    var resultDisplayMode by mutableStateOf(
        when(prefs.getString("resultDisplayMode","")) {
            "eng" -> ResultDisplayMode.ENGINEERING
            "sci" -> ResultDisplayMode.SCIENTIFIC
            else -> if(prefs.getBoolean("engineeringNotation",false)) ResultDisplayMode.ENGINEERING else ResultDisplayMode.OFF
        }
    )
    var thousandsSeparator by mutableStateOf(prefs.getBoolean("thousandsSeparator",false))
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
    private val graphSources=loadObject("graphSources")
    var dataSets by mutableStateOf(loadObject("dataSets"))
        private set
    var sequenceInitials by mutableStateOf(prefs.getString("sequenceInitials", "0,1") ?: "0,1")
    var differentialInitials by mutableStateOf(prefs.getString("differentialInitials", "1") ?: "1")
    var differentialT0 by mutableStateOf(prefs.getString("differentialT0", "0") ?: "0")
    var graphSource by mutableStateOf(graphSources.optString(prefs.getString("graphKind","cartesian"),prefs.getString("graphSource","sin(x)\ncos(x)") ?: "sin(x)\ncos(x)"))
    var equationKind by mutableStateOf(prefs.getString("equationKind","Quadratic") ?: "Quadratic")
    var equationCoefficients by mutableStateOf((loadList("equationCoefficients",listOf("1","-5","6","0"))+List(4){"0"}).take(4))
    var equationSystem by mutableStateOf(prefs.getString("equationSystem","x+y=3\nx-y=1") ?: "x+y=3\nx-y=1")
    var equationGeneral by mutableStateOf(prefs.getString("equationGeneral","sin(x)=1/2") ?: "sin(x)=1/2")
    var equationVariables by mutableStateOf(prefs.getString("equationVariables","x,y") ?: "x,y")
    var equationVariable by mutableStateOf(prefs.getString("equationVariable","x") ?: "x")
    var equationGuess by mutableStateOf(prefs.getString("equationGuess","1") ?: "1")
    var equationNumeric by mutableStateOf(prefs.getBoolean("equationNumeric",false))
    var pythonSource by mutableStateOf(prefs.getString("pythonSource","") ?: "")
        private set
    var pythonSelectionStart by mutableIntStateOf(pythonSource.length)
        private set
    var pythonSelectionEnd by mutableIntStateOf(pythonSource.length)
        private set
    var pythonFileName by mutableStateOf(prefs.getString("pythonFileName","untitled.py") ?: "untitled.py")
        private set
    var pythonUri by mutableStateOf(prefs.getString("pythonUri","") ?: "")
        private set
    var pythonDirty by mutableStateOf(prefs.getBoolean("pythonDirty",false))
        private set
    var pythonOutput by mutableStateOf("")
        private set
    var pythonHasRun by mutableStateOf(false)
        private set
    var pythonError by mutableStateOf("")
        private set
    var pythonBusy by mutableStateOf(false)
    var pythonInputPrompt by mutableStateOf<String?>(null)
    private var pythonInputSubmit: ((String) -> Unit)? = null
        private set
    var graphKind by mutableStateOf(prefs.getString("graphKind","cartesian") ?: "cartesian")
    var xMin by mutableDoubleStateOf(prefs.getString("xMin","-10")!!.toDouble())
    var xMax by mutableDoubleStateOf(prefs.getString("xMax","10")!!.toDouble())
    var yMin by mutableDoubleStateOf(prefs.getString("yMin","-5")!!.toDouble())
    var yMax by mutableDoubleStateOf(prefs.getString("yMax","5")!!.toDouble())
    var graphData by mutableStateOf<JSONObject?>(null)
    var graphAnalysis by mutableStateOf<JSONObject?>(null)
        private set
    var graphAnalysisBusy by mutableStateOf(false)
        private set
    var graphBusy by mutableStateOf(false)
    var trace by mutableStateOf<Pair<Double,Double>?>(null)
    var parameterMin by mutableDoubleStateOf(prefs.getString("parameterMin","0")!!.toDouble())
    var parameterMax by mutableDoubleStateOf(prefs.getString("parameterMax","6.283185307179586")!!.toDouble())
    var shadedInterval by mutableStateOf<Pair<Double,Double>?>(null)
    var regressionCurve by mutableStateOf(loadRegressionCurve())
        private set
    var regressionFit by mutableStateOf(prefs.getString("regressionFit","") ?: "")
        private set
    var regressionData by mutableStateOf(prefs.getString("regressionData","") ?: "")
        private set
    var regressionBusy by mutableStateOf(false)
        private set
    var radianAxis by mutableStateOf(prefs.getBoolean("radianAxis",false))
    var statisticsName by mutableStateOf(prefs.getString("statisticsName","D1") ?: "D1")
    var statisticsData by mutableStateOf(prefs.getString("statisticsData","1\n2\n3\n4") ?: "1\n2\n3\n4")
    var statisticsKind by mutableStateOf(prefs.getString("statisticsKind","list") ?: "list")
    var statisticsRegression by mutableStateOf(prefs.getString("statisticsRegression","linear") ?: "linear")
    var statisticsPlot by mutableStateOf(prefs.getString("statisticsPlot","Histogram") ?: "Histogram")
    var statisticsSelected by mutableStateOf(prefs.getString("statisticsSelected","") ?: "")
    var statisticsIsNew by mutableStateOf(prefs.getBoolean("statisticsIsNew",false))
    var statisticsCsv by mutableStateOf(prefs.getBoolean("statisticsCsv",false))
    var constants by mutableStateOf<JSONArray?>(null)
        private set
    private var job: Job? = null
    private var graphJob: Job? = null
    private var analysisJob: Job? = null
    private var regressionJob: Job? = null
    private var pythonJob: Job? = null
    init {
        tape=history.filterIndexed{index,entry->entry.id>prefs.getLong("screenClearedAt",0)&&(index!=0||!committed||entry.source!=editor.source)}.take(maxTapeEntries).asReversed().map(HistoryEntry::toTapeEntry)
        if(!committed&&editor.source.isNotBlank())schedulePreview()
    }
    private fun loadObject(key: String) = runCatching { JSONObject(prefs.getString(key,"{}")!!) }.getOrDefault(JSONObject())
    private fun loadList(key:String,default:List<String>):List<String> = runCatching {val array=JSONArray(prefs.getString(key,"[]"));List(array.length()){array.getString(it)}}.getOrDefault(emptyList()).ifEmpty {default}
    private fun loadHistory(): List<HistoryEntry> = runCatching {
        val array = JSONArray(prefs.getString("history","[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { HistoryEntry(it.getLong("id"),it.getString("source"),it.getString("exact"),it.getString("decimal"),it.getString("mode"),it.optBoolean("favorite"),it.optString("inputTree"),it.optString("response"),it.optString("answer")) } }
    }.getOrDefault(emptyList())
    private fun loadRegressionCurve():List<Pair<Double,Double>>? = runCatching {
        val array=JSONArray(prefs.getString("regressionCurve","[]"))
        if(array.length()==0)null else (0 until array.length()).map {i->val pair=array.getJSONArray(i);pair.getDouble(0) to pair.getDouble(1)}
    }.getOrNull()
    fun save() {
        prefs.edit().putString("expression",editor.source).putInt("cursor",editor.cursor).putString("mode",mode).putString("angle",angle).putString("theme",theme)
            .putString("result",result?.toString() ?: "{}").putString("resultSource",resultSource).putBoolean("committed",committed)
            .putString("inputAnswer",inputAnswer?.toString() ?: "{}").putString("answerDisplay",answerDisplay?.toString() ?: "{}").putString("lastAnswerResult",lastAnswerResult?.toString() ?: "{}")
            .putString("resultDisplayMode",resultDisplayMode.name.lowercase()).putBoolean("thousandsSeparator",thousandsSeparator)
            .putInt("precision",precision).putBoolean("haptics",haptics).putBoolean("sound",sound).putBoolean("historyEnabled",persistHistory)
            .putFloat("inputFont",inputFont).putFloat("outputFont",outputFont)
            .putString("variables",variables.toString()).putString("functions",functions.toString()).putString("assumptions",assumptions.toString())
            .putString("dataSets",dataSets.toString()).putString("sequenceInitials",sequenceInitials).putString("differentialInitials",differentialInitials).putString("differentialT0",differentialT0)
            .putString("graphSource",graphSource).putString("graphSources",graphSources.put(graphKind,graphSource).toString()).putString("graphKind",graphKind).putString("xMin",xMin.toString()).putString("xMax",xMax.toString()).putString("yMin",yMin.toString()).putString("yMax",yMax.toString())
            .putString("pythonSource",pythonSource).putString("pythonFileName",pythonFileName).putString("pythonUri",pythonUri).putBoolean("pythonDirty",pythonDirty)
            .putString("parameterMin",parameterMin.toString()).putString("parameterMax",parameterMax.toString())
            .putString("equationKind",equationKind).putString("equationCoefficients",JSONArray(equationCoefficients).toString())
            .putString("equationSystem",equationSystem).putString("equationGeneral",equationGeneral).putString("equationVariables",equationVariables)
            .putString("equationVariable",equationVariable).putString("equationGuess",equationGuess).putBoolean("equationNumeric",equationNumeric)
            .putBoolean("radianAxis",radianAxis)
            .putString("statisticsName",statisticsName).putString("statisticsData",statisticsData).putString("statisticsKind",statisticsKind)
            .putString("statisticsRegression",statisticsRegression).putString("statisticsPlot",statisticsPlot).putString("statisticsSelected",statisticsSelected)
            .putBoolean("statisticsIsNew",statisticsIsNew).putBoolean("statisticsCsv",statisticsCsv)
            .putString("regressionFit",regressionFit).putString("regressionData",regressionData)
            .putString("regressionCurve",regressionCurve?.let {list->JSONArray(list.map {point->JSONArray().put(point.first).put(point.second)}).toString()} ?: "[]")
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
    private fun rememberUndo(value:Editor) {undoHistory=(undoHistory+value).takeLast(100)}
    fun undo() {
        val session=calcSession
        if(session!=null) {
            if(calcUndoHistory.isEmpty())return
            calcSession=session.copy(input=calcUndoHistory.last())
            calcUndoHistory=calcUndoHistory.dropLast(1)
            error=""
            return
        }
        if(undoHistory.isEmpty())return
        val previous=undoHistory.last()
        undoHistory=undoHistory.dropLast(1)
        edit(previous,recordUndo=false)
    }
    fun edit(value: Editor,recordUndo:Boolean=true) {
        val changed=value.source!=editor.source
        if(changed&&recordUndo)rememberUndo(editor)
        if(changed && calcSession!=null){job?.cancel();busy=false;calcSession=null}
        if(changed){lastCalcValues=emptyMap();lastCalcSource=""}
        if(changed)exitEngineering()
        editor=value;error="";committed=false
        if(changed) {
            dmsDisplay=false
            dmsConversion=false
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
        exitEngineering()
        result?.let {tape=(tape+TapeEntry(editor.source,inputTree()?.toString() ?: "{}",it.toString(),inputAnswer?.toString() ?: "")).takeLast(maxTapeEntries)}
        committed=false;editor=Editor();result=null;dmsDisplay=false;dmsConversion=false;resultSource="";inputAnswer=null
    }
    fun insert(text: String, inside: Int = text.length) {
        if(!poweredOn)return
        var recordInEdit=true
        if(committed) {
            rememberUndo(editor);recordInEdit=false
            val last=result?.optJSONObject(if(decimal)"decimalTree" else "tree") ?: result?.optJSONObject("tree")
            nextEntry();answerDisplay=last
            if((text in listOf("+","-","−","*","×","/","÷","!","%","°","∠") || text.startsWith("^")) && variables.has("Ans")) {editor=Editor("Ans");inputAnswer=variables.getJSONObject("Ans")}
        }
        if(text=="Ans") {inputAnswer=variables.optJSONObject("Ans");answerDisplay=lastAnswerResult?.optJSONObject(if(decimal)"decimalTree" else "tree") ?: inputAnswer}
        val target=if(overwrite&&editor.cursor==editor.anchor)editor.copy(anchor=(editor.cursor+text.length).coerceAtMost(editor.source.length)) else editor
        edit(target.insert(text,inside),recordUndo=recordInEdit)
    }
    fun insertDmsSymbol() {
        if(!poweredOn)return
        val position=minOf(editor.cursor,editor.anchor).coerceIn(0,editor.source.length)
        val prefix=editor.source.substring(0,position)
        val markerIndex=prefix.lastIndexOfAny(charArrayOf('°','′','″'))
        val marker=prefix.getOrNull(markerIndex)
        val afterMarker=if(markerIndex<0)"" else prefix.substring(markerIndex+1)
        val activeField=afterMarker.isBlank()||afterMarker.toBigDecimalOrNull()!=null
        val symbol=when {
            !activeField||marker==null||marker=='″' -> "°"
            marker=='°' -> "′"
            else -> "″"
        }
        insert(symbol)
    }
    fun toggleDms() {
        val current=result ?: return
        if(current.optString("decimal").toBigDecimalOrNull()==null) {
            error="DMS conversion requires a numeric result"
            return
        }
        dmsDisplay=!dmsDisplay
        dmsConversion=true
    }
    fun clear(recordUndo:Boolean=true) {
        if(recordUndo&&editor.source.isNotEmpty())rememberUndo(editor)
        if(!recordUndo)undoHistory=emptyList()
        if(calcSession!=null)job?.cancel()
        calcSession=null;calcUndoHistory=emptyList();lastCalcValues=emptyMap();lastCalcSource="";inputVersion++;commitRequested=false;committed=false;editor=Editor();result=null;dmsDisplay=false;dmsConversion=false;resultSource="";error="";shift=false;alpha=false;hyperbolic=false;answerDisplay=null;inputAnswer=null;busy=false;exitEngineering();save()
    }
    fun fresh(value:Editor=Editor()) {if(editor.source.isNotEmpty())rememberUndo(editor);nextEntry();edit(value,recordUndo=false)}
    fun fraction() {
        var recordInEdit=true
        if(committed) {
            rememberUndo(editor);recordInEdit=false
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
        edit(target.insert(template,if(numerator.isEmpty())1 else template.length-1),recordUndo=recordInEdit)
    }
    fun angleSuffix(function:String) {
        var recordInEdit=true
        if(committed){rememberUndo(editor);recordInEdit=false;val display=result?.optJSONObject("tree");nextEntry();inputAnswer=variables.optJSONObject("Ans");answerDisplay=display;editor=Editor("Ans")}
        var target=editor
        if(target.cursor==target.anchor)target.tree()?.nodes()?.filter{it.end==target.cursor&&it.start<it.end}?.minByOrNull{it.end-it.start}?.let{target=target.select(it)}
        val selected=target.source.substring(minOf(target.anchor,target.cursor),maxOf(target.anchor,target.cursor))
        edit(target.insert("$function($selected)"),recordUndo=recordInEdit)
    }
    fun resetSetup(){angle="DEG";precision=30;decimal=false;resultDisplayMode=ResultDisplayMode.OFF;thousandsSeparator=false;mixedNumbers=false;overwrite=false;clear(recordUndo=false);save()}
    fun cycleResultDisplayMode(){resultDisplayMode=when(resultDisplayMode){ResultDisplayMode.OFF->ResultDisplayMode.ENGINEERING;ResultDisplayMode.ENGINEERING->ResultDisplayMode.SCIENTIFIC;ResultDisplayMode.SCIENTIFIC->ResultDisplayMode.OFF};save()}
    fun clearMemory(){variables=JSONObject();functions=JSONObject();assumptions=JSONObject();lastAnswerResult=null;clear(recordUndo=false);save()}
    fun clearAllScreen(){cancel();tape=emptyList();variables=JSONObject();lastAnswerResult=null;poweredOn=true;prefs.edit().putLong("screenClearedAt",System.currentTimeMillis()).apply();clear(recordUndo=false);save()}
    fun reuse(entry:TapeEntry) {
        val savedAnswer=entry.answer.takeIf(String::isNotBlank)?.let {raw->
            runCatching {JSONObject(raw).takeIf {it.has("kind")}}.getOrNull()
        }
        nextEntry()
        inputAnswer=savedAnswer
        answerDisplay=savedAnswer
        edit(Editor(entry.source))
    }
    fun recalculatePreview() {inputVersion++;schedulePreview()}
    private fun multiArgumentUserFunctions():Set<String> = functions.keys().asSequence().filter {name->
        (functions.optJSONObject(name)?.optJSONArray("parameters")?.length() ?: 0)>1
    }.toSet()
    private fun schedulePreview() {
        if(calcSession!=null || mode !in listOf("Scientific","CAS","Equations") || previewRunner?.isActive==true)return
        previewRunner=viewModelScope.launch {
            try {
                delay(100)
                while(true) {
                    if(calcSession!=null)break
                    val revision=inputVersion;val source=editor.source
                    val tree=runCatching {calculationTree(source)}.getOrNull()
                    if(tree!=null && (commitRequested||!requiresExplicitEvaluation(tree,multiArgumentUserFunctions())) && !(tree.value in listOf("=",":=") && tree.args.firstOrNull()?.kind in listOf("symbol","call") && mode!="Equations")) {
                        previewBusy=true
                        val response=engine.execute(request().put("tree",JSONObject(tree.json())).put("budget",if(commitRequested)8 else 2))
                        if(revision==inputVersion && source==editor.source && !committed && calcSession==null) {
                            if(response.optBoolean("ok")) {
                                result=response;dmsDisplay=response.optBoolean("dms");dmsConversion=false;resultSource=source;resultVersion=revision;error=""
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
        exitEngineering()
        result=response;dmsDisplay=response.optBoolean("dms");dmsConversion=false;resultSource=source;committed=true;commitRequested=false;busy=false
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
    fun startCalc() {
        if(busy)return
        val source=editor.source
        val tree=try {calculationTree(source)} catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
        val constants=setOf("pi","e","i","I","oo","true","false","Ans","c0","hP","hbar","G","qe","NA","kB0","me","mp0")
        val names=linkedSetOf<String>()
        fun collect(node:Expr,bound:Set<String>) {
            if(node.kind=="symbol" && node.value !in constants && node.value !in bound)names+=node.value
            val binder=if(node.kind=="call" && node.value in setOf("integrate","diff","nderivative","limit","sum","product","solve","nsolve","nintegrate","series"))
                node.args.getOrNull(1)?.takeIf {it.kind=="symbol"}?.value else null
            node.args.forEachIndexed {index,arg->
                if(index!=1 || binder==null)collect(arg,if(index==0 && binder!=null)bound+binder else bound)
            }
        }
        collect(tree,emptySet())
        if(names.isEmpty()){calculate();return}
        previewRunner?.cancel();previewRunner=null;inputVersion++;commitRequested=false;busy=false;committed=false
        result=null;resultSource="";resultVersion=-1;error=""
        calcUndoHistory=emptyList()
        calcSession=CalcSession(source,names.toList())
    }
    fun editCalcValue(value:Editor) {if(busy)return;calcSession?.let{session->if(value.source!=session.input.source)calcUndoHistory=(calcUndoHistory+session.input).takeLast(100);calcSession=session.copy(input=value)};error=""}
    fun insertCalcValue(text:String,inside:Int=text.length) {
        val session=calcSession ?: return
        editCalcValue(session.input.insert(text,inside))
    }
    fun cancelCalc() {job?.cancel();busy=false;calcSession=null;calcUndoHistory=emptyList();error="";schedulePreview()}
    fun submitCalcValue() {
        val session=calcSession ?: return
        if(busy)return
        val input=session.input.source
        val tree=if(input.isBlank())null else try {calculationTree(input)} catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
        val existing=variables.optJSONObject(session.name)
        job=viewModelScope.launch {
            busy=true;error=""
            try {
                val value=if(tree==null)existing ?: JSONObject().put("kind","number").put("value","0") else {
                    val response=engine.execute(request().put("tree",JSONObject(tree.json())))
                    if(!response.optBoolean("ok")){error=response.optString("error","Math ERROR");return@launch}
                    if(response.optBoolean("symbolic") || !response.has("resultAst")){error="Enter a numeric value";return@launch}
                    response.getJSONObject("resultAst")
                }
                val updated=JSONObject(variables.toString()).put(session.name,value)
                variables=updated;save()
                val accepted=session.accepted+(session.name to JSONObject(value.toString()))
                if(session.index<session.names.lastIndex) {
                    calcUndoHistory=emptyList()
                    calcSession=session.copy(index=session.index+1,input=Editor(),accepted=accepted)
                } else {
                    val expression=calculationTree(session.source)
                    val response=engine.execute(request().put("tree",JSONObject(expression.json())))
                    if(response.optBoolean("ok")) {
                        lastCalcValues=accepted;lastCalcSource=session.source
                        calcSession=null;calcUndoHistory=emptyList()
                        commit(session.source,response)
                    } else error=response.optString("error","Math ERROR")
                }
            } finally {busy=false}
        }
    }
    fun calculate(source: String = editor.source) {
        if(calcSession!=null){submitCalcValue();return}
        if(engineeringConversion) {
            exitEngineering()
            return
        }
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
                    result=response;dmsDisplay=response.optBoolean("dms");dmsConversion=false
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
    fun cancel() { inputVersion++;commitRequested=false;previewRunner?.cancel();job?.cancel(); graphJob?.cancel();analysisJob?.cancel();regressionJob?.cancel();pythonJob?.cancel(); engine.cancel(); busy=false; graphBusy=false;regressionBusy=false;pythonBusy=false;pythonInputPrompt=null;pythonInputSubmit=null;previewBusy=false;error="Calculation cancelled" }
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
                        if(showResult){result=response.put("note","Stored in $name");dmsDisplay=false;dmsConversion=false}
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
            require(name !in ("sinc sin cos tan asin acos atan sinh cosh tanh asinh acosh atanh sqrt cbrt nthroot abs floor ceil round sign factorial gamma ln log exp erf erfc Ei Si Ci zeta re im arg conj polar rectpolar simplify expand factor collect diff integrate limit series solve nsolve sum product piecewise subs gcd lcm nCr nPr prime isprime factorint divisors percent degree quotient remainder det inverse transpose rank trace rref ref lu eigenvalues eigenvectors norm normalize dot cross angle projection linsolve mean median variance stdev sumdata quartiles stats regression convert qty nintegrate nderivative minimum maximum".split(' '))) {"This function name is reserved"}
            val names=parameters.split(',').map { it.trim() }; require(names.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) } && names.distinct().size==names.size)
            functions=JSONObject(functions.toString()).put(name,JSONObject().put("parameters",JSONArray(names)).put("source",source).put("body",JSONObject(Parser(source).parse().json()))); save()
            error=""
            val message="$name(${names.joinToString()}) defined"
            if(showResult){result=JSONObject().put("exact",message).put("decimal",message).put("tree",JSONObject().put("kind","text").put("value",message));dmsDisplay=false;dmsConversion=false}
        } catch(e: Exception) { error=e.message ?: "Invalid function" }
    }
    fun assume(name: String, assumption: String) { assumptions=JSONObject(assumptions.toString()).put(name,JSONArray(if(assumption=="none") emptyList<String>() else listOf(assumption))); save() }
    fun removeVariable(name: String) { variables=JSONObject(variables.toString()).apply { remove(name) }; functions=JSONObject(functions.toString()).apply { remove(name) }; save() }
    fun favorite(id: Long) { history=history.map { if(it.id==id) it.copy(favorite=!it.favorite) else it }; save() }
    fun deleteHistory(id: Long) { history=history.filter { it.id!=id }; save() }
    fun clearHistory() { history=emptyList();tape=emptyList();save() }
    fun saveDataSet(name:String,csv:String,kind:String) {
        try {
            require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) { "Use a letter followed by letters, digits or underscores for the dataset name" }
            require(kind in listOf("list", "xy")) { "Unknown dataset type" }
            dataSets=JSONObject(dataSets.toString()).put(name,JSONObject().put("csv",csv).put("kind",kind))
            error="";save()
        } catch(e:Exception) { error=e.message ?: "Could not save dataset" }
    }
    fun deleteDataSet(name:String) {
        dataSets=JSONObject(dataSets.toString()).apply { remove(name) }
        save()
    }
    fun saveStatistics(name:String,data:String,kind:String,regression:String,plot:String,csv:Boolean,selected:String,isNew:Boolean) {
        statisticsName=name;statisticsData=data;statisticsKind=kind;statisticsRegression=regression
        statisticsPlot=plot;statisticsCsv=csv;statisticsSelected=selected;statisticsIsNew=isNew
        prefs.edit().putString("statisticsName",name).putString("statisticsData",data).putString("statisticsKind",kind)
            .putString("statisticsRegression",regression).putString("statisticsPlot",plot).putBoolean("statisticsCsv",csv)
            .putString("statisticsSelected",selected).putBoolean("statisticsIsNew",isNew).apply()
    }
    fun fitRegression(source: String, data: String) {
        regressionJob?.cancel()
        val tree=try {Parser(source).parse()} catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
        regressionJob=viewModelScope.launch {
            regressionBusy=true;error=""
            try {
                val response=engine.execute(request().put("tree",JSONObject(tree.json())))
                if(response.optBoolean("ok")) {
                    result=response;dmsDisplay=false;dmsConversion=false
                    val array=response.optJSONArray("curve")
                    regressionCurve=if(array==null)emptyList() else (0 until array.length()).mapNotNull {index->array.optJSONArray(index)?.let {pair->pair.optDouble(0) to pair.optDouble(1)}}
                    regressionFit=response.optString("exact");regressionData=data
                    val next=JSONObject(variables.toString())
                    if(response.has("resultAst")) next.put("Ans",response.getJSONObject("resultAst")) else next.remove("Ans")
                    variables=next
                    history=(listOf(HistoryEntry(System.currentTimeMillis(),source,response.optString("exact"),response.optString("decimal"),mode))+history).take(500)
                    save()
                } else {regressionCurve=emptyList();regressionFit="";regressionData="";error=response.optString("error","Math ERROR")}
            } finally {regressionBusy=false}
        }
    }
    fun plot() {
        graphJob?.cancel()
        val limit=if(graphKind in listOf("surface","differential")) 1 else 6
        val trees=try { graphSource.lines().filter { it.isNotBlank() }.take(limit).map { JSONObject(Parser(it).parse().json()) } } catch(e: Exception) { error=e.message ?: "Syntax ERROR"; return }
        if(trees.isEmpty()) { error="Enter a function"; return }
        val source=graphSource;val kind=graphKind;val min=if(kind in listOf("cartesian","surface"))xMin else parameterMin;val max=if(kind in listOf("cartesian","surface"))xMax else parameterMax
        val request=request("graph").put("angle","RAD").put("trees",JSONArray(trees)).put("graphKind",kind)
            .put("variable",when(kind){"cartesian"->"x";"sequence"->"n";"surface"->"x";else->"t"})
            .put("min",min).put("max",max).put("samples",500).put("yMin",yMin).put("yMax",yMax)
        if(kind=="surface")request.put("surfaceYMin",yMin).put("surfaceYMax",yMax)
        if(kind=="sequence") {
            try {
                val seeds=sequenceInitials.split(',').map(String::trim).filter(String::isNotEmpty).map { JSONObject(Parser(it).parse().json()) }
                require(seeds.isNotEmpty()) { "Enter at least one initial sequence value" }
                request.put("initialTrees",JSONArray(seeds))
            } catch(e:Exception) { error=e.message ?: "Invalid initial sequence values";return }
        }
        if(kind=="differential") {
            val t0=differentialT0.trim().toDoubleOrNull()
            val initials=differentialInitials.split(',').map(String::trim).filter(String::isNotEmpty).mapNotNull(String::toDoubleOrNull)
            if(t0==null || !t0.isFinite() || initials.isEmpty() || initials.size>6 || differentialInitials.split(',').map(String::trim).filter(String::isNotEmpty).size!=initials.size) {
                error="Enter t₀ and one to six finite initial y values";return
            }
            request.put("t0",t0).put("initialValues",JSONArray(initials))
        }
        graphJob=viewModelScope.launch {
            graphBusy=true; error=""
            try {
                val response=engine.execute(request)
                if(source==graphSource && kind==graphKind && min==(if(kind in listOf("cartesian","surface"))xMin else parameterMin) && max==(if(kind in listOf("cartesian","surface"))xMax else parameterMax)) {
                    if(response.optBoolean("ok")) graphData=response else {graphData=null;error=response.optString("error")}
                }
                save()
            } finally { graphBusy=false }
        }
    }
    fun editPython(source:String,start:Int=source.length,end:Int=start) {
        val changed=source!=pythonSource
        pythonSource=source
        pythonSelectionStart=start.coerceIn(0,source.length)
        pythonSelectionEnd=end.coerceIn(0,source.length)
        if(changed) {pythonDirty=true;save()}
    }
    fun insertPython(snippet:String,inside:Int=snippet.length) {
        val a=minOf(pythonSelectionStart,pythonSelectionEnd).coerceIn(0,pythonSource.length)
        val b=maxOf(pythonSelectionStart,pythonSelectionEnd).coerceIn(a,pythonSource.length)
        val source=pythonSource.substring(0,a)+snippet+pythonSource.substring(b)
        val cursor=a+inside.coerceIn(0,snippet.length)
        editPython(source,cursor,cursor)
    }
    fun newPythonFile() {pythonSource="";pythonSelectionStart=0;pythonSelectionEnd=0;pythonFileName="untitled.py";pythonUri="";pythonDirty=false;pythonOutput="";pythonError="";pythonHasRun=false;pythonInputPrompt=null;pythonInputSubmit=null;save()}
    fun openPythonFile(source:String,name:String,uri:String) {pythonSource=source;pythonSelectionStart=source.length;pythonSelectionEnd=source.length;pythonFileName=name;pythonUri=uri;pythonDirty=false;pythonOutput="";pythonError="";pythonHasRun=false;pythonInputPrompt=null;pythonInputSubmit=null;save()}
    fun savedPythonFile(name:String,uri:String) {pythonFileName=name;pythonUri=uri;pythonDirty=false;save()}
    fun pythonFileError(message:String) {pythonError=message}
    fun runPython() {
        if(pythonBusy)return
        val source=pythonSource;val filename=pythonFileName
        pythonJob=viewModelScope.launch {
            pythonBusy=true;pythonOutput="";pythonError="";pythonHasRun=false;pythonInputPrompt=null;pythonInputSubmit=null
            try {
                val response=engine.execute(JSONObject().put("action","python").put("source",source).put("filename",filename).put("functions",functions).put("variables",variables).put("assumptions",assumptions)) { prompt, output, submit ->
                    pythonOutput=output;pythonInputPrompt=prompt;pythonInputSubmit=submit
                }
                pythonOutput=response.optString("output","")
                pythonError=if(response.optBoolean("ok"))"" else response.optString("error","Python execution failed")
                pythonHasRun=true
            } finally {pythonBusy=false;pythonInputPrompt=null;pythonInputSubmit=null}
        }
    }
    fun submitPythonInput(value:String) {pythonInputSubmit?.invoke(value);pythonInputSubmit=null;pythonInputPrompt=null}
    fun stopPython() {pythonJob?.cancel();engine.cancel();pythonBusy=false;pythonInputPrompt=null;pythonInputSubmit=null;pythonHasRun=true;pythonError="Execution stopped"}
    fun updateGraphSource(source:String) {
        graphSource=source
        graphData=null;graphAnalysis=null;trace=null;shadedInterval=null
        save()
    }
    fun sendExpressionToGraph() {
        val source=editor.source.trim()
        if(source.isEmpty()) {error="Enter an expression to graph";return}
        val tree=try {Parser(source).parse()} catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
        val expression=if(tree.kind=="relation" && tree.value=="=" && tree.args.firstOrNull()?.kind=="symbol" && tree.args.first().value=="y") {
            source.substring(tree.args[1].start,tree.args[1].end)
        } else {
            if(tree.kind=="relation") {error="Graph an expression or y = f(x)";return}
            source
        }
        changeGraphKind("cartesian")
        updateGraphSource(expression)
        error="";mode="Graph"
    }
    fun changeGraphKind(kind:String) {
        if(kind==graphKind)return
        graphSources.put(graphKind,graphSource)
        graphKind=kind
        graphSource=graphSources.optString(kind,when(kind){"parametric"->"[cos(t),sin(t)]";"polar"->"2*cos(3*t)";"sequence"->"n\nu(n-1)+u(n-2)";"surface"->"sin(sqrt(x^2+y^2))";"differential"->"y-t";else->"sin(x)\ncos(x)"})
        graphData=null;graphAnalysis=null;trace=null;shadedInterval=null
        if(kind=="sequence") {
            parameterMin=0.0;parameterMax=20.0;xMin=0.0;xMax=20.0;yMin=-2.0;yMax=20.0
        } else if(kind=="differential") {
            parameterMin=-5.0;parameterMax=5.0;xMin=-5.0;xMax=5.0;yMin=-3.0;yMax=5.0
        } else if(kind=="surface") {
            xMin=-3.0;xMax=3.0;yMin=-3.0;yMax=3.0
        } else if(kind!="cartesian" && xMin == -10.0 && xMax == 10.0 && yMin == -5.0 && yMax == 5.0) {
            xMin=-3.0;xMax=3.0;yMin=-3.0;yMax=3.0
        }
        save()
    }
    fun analyzeGraph(action:String,first:String,second:String,selected:Int,other:Int) {
        if(graphKind!="cartesian") {error="Analysis requires a Cartesian graph";return}
        val a=first.toDoubleOrNull();val b=if(action=="derivative")a else second.toDoubleOrNull()
        if(a==null || !a.isFinite() || b==null || !b.isFinite() || (action!="derivative" && a>=b)) {error="Enter finite values with a < b";return}
        val sources=graphSource.lines().filter {it.isNotBlank()}.take(6)
        if(sources.isEmpty() || selected !in sources.indices || action=="intersection" && (other !in sources.indices || other==selected)) {error="Select two different functions";return}
        val trees=try {JSONArray(sources.map {JSONObject(Parser(it).parse().json())})} catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
        analysisJob?.cancel()
        val source=graphSource
        analysisJob=viewModelScope.launch {
            graphAnalysisBusy=true;error="";graphAnalysis=null
            try {
                val response=engine.execute(request("graphAnalysis").put("angle","RAD").put("trees",trees).put("analysis",action).put("a",a).put("b",b).put("selected",selected).put("other",other))
                if(source==graphSource && graphKind=="cartesian") {
                    if(response.optBoolean("ok")) {
                        graphAnalysis=response
                        shadedInterval=if(action=="integral")a to b else null
                        response.optJSONArray("points")?.optJSONArray(0)?.let {trace=it.getDouble(0) to it.getDouble(1)}
                    } else error=response.optString("error","Analysis failed")
                }
            } finally {graphAnalysisBusy=false}
        }
    }
    fun program(a: String,b: String,base: Int,width: Int,signed: Boolean,op: String) {
        if(busy) return
        job=viewModelScope.launch {
            busy=true; error=""
            try { val r=engine.execute(request("programmer").put("a",a).put("b",b.ifBlank { "0" }).put("base",base).put("width",width).put("signed",signed).put("op",op)); if(r.optBoolean("ok")){result=r;dmsDisplay=false;dmsConversion=false} else error=r.optString("error") }
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
    fun enterEngineering() { if(!poweredOn||result==null)return;engineeringConversion=true;engineeringShift=0 }
    fun shiftEngineering(delta:Int) { if(engineeringConversion)engineeringShift=(engineeringShift+delta).coerceIn(-40_000,40_000) }
    fun exitEngineering() { engineeringConversion=false;engineeringShift=0 }
    override fun onCleared() { save(); engine.close(); super.onCleared() }
}

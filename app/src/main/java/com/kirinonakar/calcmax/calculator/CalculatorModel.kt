package com.kirinonakar.calcmax.calculator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import com.kirinonakar.calcmax.math.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

private const val maxTapeEntries = 10
private const val maxHistoryEntries = 500
class CalculatorModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("calculator",0)
    // Content URIs refer to grants on this device and must not follow a restored draft.
    private val localPrefs = application.getSharedPreferences("calculator-local",0)
    internal val engine = EngineClient(application)
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
        internal set
    var dmsDisplay by mutableStateOf(result?.optBoolean("dms") == true)
        internal set
    var dmsConversion by mutableStateOf(false)
        internal set
    var tape by mutableStateOf<List<TapeEntry>>(emptyList())
        private set
    var committed by mutableStateOf(prefs.getBoolean("committed",false))
        internal set
    var answerDisplay by mutableStateOf<JSONObject?>(loadObject("answerDisplay").takeIf{it.has("kind")})
        internal set
    var previewBusy by mutableStateOf(false)
        private set
    var inputVersion by mutableIntStateOf(0)
        internal set
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
    internal var resultSource=prefs.getString("resultSource","") ?: ""
    internal var resultVersion=if(resultSource==editor.source&&result!=null)0 else -1
    internal var inputAnswer:JSONObject?=loadObject("inputAnswer").takeIf{it.has("kind")}
    internal var lastAnswerResult:JSONObject?=loadObject("lastAnswerResult").takeIf{it.has("exact")}
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
        internal set
    var shift by mutableStateOf(false)
    var alpha by mutableStateOf(false)
    var mode by mutableStateOf(prefs.getString("mode","Scientific/CAS")?.let{if(it=="Scientific"||it=="CAS")"Scientific/CAS" else it} ?: "Scientific/CAS")
    var angle by mutableStateOf(prefs.getString("angle","DEG") ?: "DEG")
    var theme by mutableStateOf(prefs.getString("theme","System") ?: "System")
    var language by mutableStateOf(prefs.getString("language","en")?.takeIf { it in setOf("en", "ko") } ?: "en")
    var precision by mutableIntStateOf(prefs.getInt("precision",30))
    var displayDigits by mutableIntStateOf(prefs.getInt("displayDigits",10))
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
    var displayShortcuts by mutableStateOf(loadDisplayShortcuts())
        private set
    var catalogRecent by mutableStateOf(loadCatalogList("catalogRecent").take(10))
        private set
    var catalogFavorites by mutableStateOf(loadCatalogList("catalogFavorites"))
        private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics",true))
    var sound by mutableStateOf(prefs.getBoolean("sound",false))
    var persistHistory by mutableStateOf(prefs.getBoolean("historyEnabled",true))
    var autoCloseBrackets by mutableStateOf(prefs.getBoolean("autoCloseBrackets",false))
    var wordWrap by mutableStateOf(prefs.getBoolean("wordWrap",false))
    var typedParens by mutableStateOf<List<IntRange>>(emptyList())
        private set
    var history by mutableStateOf(loadHistory())
        private set
    var variables by mutableStateOf(loadObject("variables"))
        internal set
    var functions by mutableStateOf(loadObject("functions"))
        internal set
    var assumptions by mutableStateOf(loadObject("assumptions"))
        internal set
    internal val graphState=GraphState(prefs)
    internal val statisticsState=StatisticsState(prefs)
    internal val pythonState=PythonState(prefs,localPrefs)
    var dataSets
        get()=statisticsState.dataSets
        private set(value) {statisticsState.dataSets=value}
    var sequenceInitials
        get()=graphState.sequenceInitials
        set(value) {graphState.sequenceInitials=value}
    var differentialInitials
        get()=graphState.differentialInitials
        set(value) {graphState.differentialInitials=value}
    var differentialT0
        get()=graphState.differentialT0
        set(value) {graphState.differentialT0=value}
    var graphSource
        get()=graphState.graphSource
        set(value) {graphState.graphSource=value}
    var equationKind by mutableStateOf(prefs.getString("equationKind","Quadratic") ?: "Quadratic")
    var equationCoefficients by mutableStateOf((loadList("equationCoefficients",listOf("1","-5","6","0"))+List(4){"0"}).take(4))
    var equationSystem by mutableStateOf(prefs.getString("equationSystem","x+y=3\nx-y=1") ?: "x+y=3\nx-y=1")
    var equationGeneral by mutableStateOf(prefs.getString("equationGeneral","sin(x)=1/2") ?: "sin(x)=1/2")
    var equationVariables by mutableStateOf(prefs.getString("equationVariables","x,y") ?: "x,y")
    var equationVariable by mutableStateOf(prefs.getString("equationVariable","x") ?: "x")
    var equationGuess by mutableStateOf(prefs.getString("equationGuess","1") ?: "1")
    var equationNumeric by mutableStateOf(prefs.getBoolean("equationNumeric",false))
    var equationOde by mutableStateOf(prefs.getString("equationOde","diff(y(t),t)=y(t)") ?: "diff(y(t),t)=y(t)")
    var equationOdeFunction by mutableStateOf(prefs.getString("equationOdeFunction","y(t)") ?: "y(t)")
    var equationOdeVariable by mutableStateOf(prefs.getString("equationOdeVariable","t") ?: "t")
    var equationOdeInitial by mutableStateOf(prefs.getString("equationOdeInitial","") ?: "")
    var equationPde by mutableStateOf(prefs.getString("equationPde","diff(u(x,y),x)+diff(u(x,y),y)=0") ?: "diff(u(x,y),x)+diff(u(x,y),y)=0")
    var equationPdeFunction by mutableStateOf(prefs.getString("equationPdeFunction","u(x,y)") ?: "u(x,y)")
    var equationPdeHint by mutableStateOf(prefs.getString("equationPdeHint","") ?: "")
    var pythonSource
        get()=pythonState.pythonSource
        private set(value) {pythonState.pythonSource=value}
    var pythonSelectionStart
        get()=pythonState.pythonSelectionStart
        private set(value) {pythonState.pythonSelectionStart=value}
    var pythonSelectionEnd
        get()=pythonState.pythonSelectionEnd
        private set(value) {pythonState.pythonSelectionEnd=value}
    var pythonFileName
        get()=pythonState.pythonFileName
        private set(value) {pythonState.pythonFileName=value}
    var pythonUri
        get()=pythonState.pythonUri
        private set(value) {pythonState.pythonUri=value}
    var pythonDirty
        get()=pythonState.pythonDirty
        private set(value) {pythonState.pythonDirty=value}
    var pythonOutput
        get()=pythonState.pythonOutput
        private set(value) {pythonState.pythonOutput=value}
    var pythonHasRun
        get()=pythonState.pythonHasRun
        private set(value) {pythonState.pythonHasRun=value}
    var pythonError
        get()=pythonState.pythonError
        private set(value) {pythonState.pythonError=value}
    var pythonBusy
        get()=pythonState.pythonBusy
        set(value) {pythonState.pythonBusy=value}
    var pythonInputPrompt
        get()=pythonState.pythonInputPrompt
        set(value) {pythonState.pythonInputPrompt=value}
    internal var pythonInputSubmit
        get()=pythonState.pythonInputSubmit
        set(value) {pythonState.pythonInputSubmit=value}
    var graphKind
        get()=graphState.graphKind
        set(value) {graphState.graphKind=value}
    var xMin
        get()=graphState.xMin
        set(value) {graphState.xMin=value}
    var xMax
        get()=graphState.xMax
        set(value) {graphState.xMax=value}
    var yMin
        get()=graphState.yMin
        set(value) {graphState.yMin=value}
    var yMax
        get()=graphState.yMax
        set(value) {graphState.yMax=value}
    var zMin
        get()=graphState.zMin
        set(value) {graphState.zMin=value}
    var zMax
        get()=graphState.zMax
        set(value) {graphState.zMax=value}
    var graphData
        get()=graphState.graphData
        set(value) {graphState.graphData=value}
    var graphDerivativeSelected
        get()=graphState.graphDerivativeSelected
        set(value) {graphState.graphDerivativeSelected=value}
    var graphAnalysis
        get()=graphState.graphAnalysis
        private set(value) {graphState.graphAnalysis=value}
    var graphAnalysisBusy
        get()=graphState.graphAnalysisBusy
        private set(value) {graphState.graphAnalysisBusy=value}
    var graphBusy
        get()=graphState.graphBusy
        set(value) {graphState.graphBusy=value}
    var trace
        get()=graphState.trace
        set(value) {graphState.trace=value}
    var parameterMin
        get()=graphState.parameterMin
        set(value) {graphState.parameterMin=value}
    var parameterMax
        get()=graphState.parameterMax
        set(value) {graphState.parameterMax=value}
    var shadedInterval
        get()=graphState.shadedInterval
        set(value) {graphState.shadedInterval=value}
    var graphParameters
        get()=graphState.graphParameters
        private set(value) {graphState.graphParameters=value}
    var graphAnimating
        get()=graphState.graphAnimating
        private set(value) {graphState.graphAnimating=value}
    internal var animationPhase
        get()=graphState.animationPhase
        set(value) {graphState.animationPhase=value}
    var radianAxis
        get()=graphState.radianAxis
        set(value) {graphState.radianAxis=value}
    var regressionCurve
        get()=statisticsState.regressionCurve
        private set(value) {statisticsState.regressionCurve=value}
    var regressionFit
        get()=statisticsState.regressionFit
        private set(value) {statisticsState.regressionFit=value}
    var regressionData
        get()=statisticsState.regressionData
        private set(value) {statisticsState.regressionData=value}
    var regressionMode
        get()=statisticsState.regressionMode
        private set(value) {statisticsState.regressionMode=value}
    var regressionCorrelation
        get()=statisticsState.regressionCorrelation
        private set(value) {statisticsState.regressionCorrelation=value}
    var regressionParameters
        get()=statisticsState.regressionParameters
        private set(value) {statisticsState.regressionParameters=value}
    var regressionBusy
        get()=statisticsState.regressionBusy
        private set(value) {statisticsState.regressionBusy=value}
    var statisticsName
        get()=statisticsState.statisticsName
        set(value) {statisticsState.statisticsName=value}
    var statisticsData
        get()=statisticsState.statisticsData
        set(value) {statisticsState.statisticsData=value}
    var statisticsKind
        get()=statisticsState.statisticsKind
        set(value) {statisticsState.statisticsKind=value}
    var statisticsRegression
        get()=statisticsState.statisticsRegression
        set(value) {statisticsState.statisticsRegression=value}
    var statisticsCustomFormula
        get()=statisticsState.statisticsCustomFormula
        set(value) {statisticsState.statisticsCustomFormula=value}
    var statisticsCustomVariable
        get()=statisticsState.statisticsCustomVariable
        set(value) {statisticsState.statisticsCustomVariable=value}
    var statisticsCustomInitials
        get()=statisticsState.statisticsCustomInitials
        set(value) {statisticsState.statisticsCustomInitials=value}
    var statisticsPlot
        get()=statisticsState.statisticsPlot
        set(value) {statisticsState.statisticsPlot=value}
    var statisticsSelected
        get()=statisticsState.statisticsSelected
        set(value) {statisticsState.statisticsSelected=value}
    var statisticsIsNew
        get()=statisticsState.statisticsIsNew
        set(value) {statisticsState.statisticsIsNew=value}
    var statisticsCsv
        get()=statisticsState.statisticsCsv
        set(value) {statisticsState.statisticsCsv=value}
    var constants by mutableStateOf<JSONArray?>(null)
        private set
    internal var job: Job? = null
    internal var graphJob: Job? = null
    internal var graphRequestSignature: String? = null
    internal var analysisJob: Job? = null
    internal var regressionJob: Job? = null
    internal var pythonJob: Job? = null
    internal var animationJob: Job? = null
    init {
        tape=history.filterIndexed{index,entry->entry.id>prefs.getLong("screenClearedAt",0)&&(index!=0||!committed||entry.source!=editor.source)}.take(maxTapeEntries).asReversed().map(HistoryEntry::toTapeEntry)
        if(!committed&&editor.source.isNotBlank())schedulePreview()
    }
    private fun loadObject(key: String) = runCatching { JSONObject(prefs.getString(key,"{}")!!) }.getOrDefault(JSONObject())
    private fun loadList(key:String,default:List<String>):List<String> = runCatching {val array=JSONArray(prefs.getString(key,"[]"));List(array.length()){array.getString(it)}}.getOrDefault(emptyList()).ifEmpty {default}
    private fun loadCatalogList(key:String):List<String> = runCatching {
        val array=JSONArray(prefs.getString(key,"[]"))
        (0 until array.length().coerceAtMost(500)).mapNotNull {array.optString(it).takeIf(String::isNotBlank)}.distinct()
    }.getOrDefault(emptyList())
    fun recordCatalogUse(source:String) {
        catalogRecent=(listOf(source)+catalogRecent.filterNot {it==source}).take(10)
        save()
    }
    fun toggleCatalogFavorite(source:String) {
        catalogFavorites=if(source in catalogFavorites)catalogFavorites.filterNot {it==source} else catalogFavorites+source
        save()
    }
    private fun loadHistory(): List<HistoryEntry> = runCatching {
        val array = JSONArray(prefs.getString("history","[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { HistoryEntry(it.getLong("id"),it.getString("source"),it.getString("exact"),it.getString("decimal"),it.getString("mode"),it.optBoolean("favorite"),it.optString("inputTree"),it.optString("response"),it.optString("answer")) } }
    }.getOrDefault(emptyList())
    private fun trimHistory(all: List<HistoryEntry>): List<HistoryEntry> {
        if(all.size<=maxHistoryEntries) return all
        val favoriteCount=all.count { it.favorite }
        if(favoriteCount>=maxHistoryEntries) return all.filter { it.favorite }.take(maxHistoryEntries)
        val nonFavoriteLimit=maxHistoryEntries-favoriteCount
        var seen=0
        return all.filter { if(it.favorite) true else if(seen<nonFavoriteLimit) { seen++; true } else false }
    }
    internal fun appendHistory(entry: HistoryEntry) { history=trimHistory(listOf(entry)+history) }
    private fun loadDisplayShortcuts():List<DisplayShortcut> = runCatching {
        if(!prefs.contains("displayShortcuts"))return@runCatching DefaultDisplayShortcuts
        val array=JSONArray(prefs.getString("displayShortcuts","[]"))
        (0 until array.length().coerceAtMost(6)).mapNotNull {index->
            array.optJSONObject(index)?.let {item->
                val label=item.optString("label")
                val input=item.optString("input")
                val source=item.optString("source")
                if(label.isNotBlank()&&input.isNotBlank()&&source in setOf("keypad","catalog"))DisplayShortcut(label,input,source) else null
            }
        }
    }.getOrDefault(DefaultDisplayShortcuts)
    fun setDisplayShortcut(index:Int,shortcut:DisplayShortcut) {
        if(index !in 0..displayShortcuts.size || index==6 || shortcut.label.isBlank() || shortcut.input.isBlank())return
        displayShortcuts=displayShortcuts.toMutableList().apply {if(index==size)add(shortcut) else set(index,shortcut)}
        save()
    }
    fun removeDisplayShortcut(index:Int) {
        if(index !in displayShortcuts.indices)return
        displayShortcuts=displayShortcuts.toMutableList().apply {removeAt(index)}
        save()
    }
    fun resetDisplayShortcuts() {
        displayShortcuts=DefaultDisplayShortcuts
        save()
    }
    fun save() {
        val editor=prefs.edit()
        editor.putString("expression",this.editor.source).putInt("cursor",this.editor.cursor).putString("mode",mode).putString("angle",angle).putString("theme",theme)
            .putString("result",result?.toString() ?: "{}").putString("resultSource",resultSource).putBoolean("committed",committed)
            .putString("inputAnswer",inputAnswer?.toString() ?: "{}").putString("answerDisplay",answerDisplay?.toString() ?: "{}").putString("lastAnswerResult",lastAnswerResult?.toString() ?: "{}")
            .putString("resultDisplayMode",resultDisplayMode.name.lowercase()).putBoolean("thousandsSeparator",thousandsSeparator)
            .putString("displayShortcuts",JSONArray(displayShortcuts.map {JSONObject().put("label",it.label).put("input",it.input).put("source",it.source)}).toString())
            .putString("catalogRecent",JSONArray(catalogRecent).toString()).putString("catalogFavorites",JSONArray(catalogFavorites).toString())
            .putInt("precision",precision).putInt("displayDigits",displayDigits).putBoolean("haptics",haptics).putBoolean("sound",sound).putBoolean("historyEnabled",persistHistory).putBoolean("autoCloseBrackets",autoCloseBrackets)
            .putString("language",language)
            .putFloat("inputFont",inputFont).putFloat("outputFont",outputFont).putBoolean("wordWrap",wordWrap)
            .putString("variables",variables.toString()).putString("functions",functions.toString()).putString("assumptions",assumptions.toString())
            .putString("equationKind",equationKind).putString("equationCoefficients",JSONArray(equationCoefficients).toString())
            .putString("equationSystem",equationSystem).putString("equationGeneral",equationGeneral).putString("equationVariables",equationVariables)
            .putString("equationVariable",equationVariable).putString("equationGuess",equationGuess).putBoolean("equationNumeric",equationNumeric)
            .putString("equationOde",equationOde).putString("equationOdeFunction",equationOdeFunction).putString("equationOdeVariable",equationOdeVariable)
            .putString("equationOdeInitial",equationOdeInitial).putString("equationPde",equationPde).putString("equationPdeFunction",equationPdeFunction).putString("equationPdeHint",equationPdeHint)
            .putString("history",if(persistHistory) JSONArray(history.map { JSONObject().put("id",it.id).put("source",it.source).put("exact",it.exact).put("decimal",it.decimal).put("mode",it.mode).put("favorite",it.favorite).put("inputTree",it.inputTree).put("response",it.response).put("answer",it.answer) }).toString() else "[]")
        graphState.writeTo(editor)
        statisticsState.writeTo(editor)
        pythonState.writeTo(editor)
        editor.apply()
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
        if(changed)typedParens=TypedParens.shift(typedParens,editor.source,value.source)
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
        val converted=LatexInput.convert(text)
        var value=converted ?: text
        val insertionCursor=if(converted!=null)converted.length else inside
        if(committed) {
            rememberUndo(editor);recordInEdit=false
            val assignment=result?.optBoolean("assignment")==true
            val last=if(assignment)null else (result?.optJSONObject(if(decimal)"decimalTree" else "tree") ?: result?.optJSONObject("tree"))
            nextEntry();answerDisplay=last
            if(!assignment&&(text in listOf("+","-","−","*","×","/","÷","!","%","°","∠") || text.startsWith("^")) && variables.has("Ans")) {editor=Editor("Ans");inputAnswer=variables.getJSONObject("Ans")}
        }
        val inFunctionArgument=text.length==1 && text[0] in "([{)]}" && editor.inCallArgument()
        if((autoCloseBrackets||inFunctionArgument)&&!overwrite&&text.length==1&&editor.cursor==editor.anchor&&editor.exponent==null) {
            val typed=text[0]
            val closer=when(typed){'('->')';'['->']';'{'->'}';else->null}
            if(closer!=null)value=text+closer
            else if(typed in ")]}" && editor.source.getOrNull(editor.cursor)==typed) {
                edit(Editor(editor.source,editor.cursor+1,editor.cursor+1),recordUndo=recordInEdit)
                return
            }
        }
        if(text=="Ans"||text=="*Ans") {inputAnswer=variables.optJSONObject("Ans");answerDisplay=lastAnswerResult?.optJSONObject(if(decimal)"decimalTree" else "tree") ?: inputAnswer}
        if(!overwrite&&(value.firstOrNull()?.let{it.isLetterOrDigit()||it=='.'}==true||value=="()")) {
            val slot=editor.emptyProductSlot()
            if(slot!=null&&typedParens.none {it.first==slot.first&&it.last==slot.last}) {
                // Filling an editor-created slot with a plain operand drops its parentheses.
                edit(editor.replaceSlot(slot,value,insertionCursor),recordUndo=recordInEdit)
                if(value=="()")markTypedParens(editor.cursor)
                return
            }
        }
        val target=if(overwrite&&editor.cursor==editor.anchor)editor.copy(anchor=(editor.cursor+text.length).coerceAtMost(editor.source.length)) else editor
        edit(target.insertOperand(value,insertionCursor),recordUndo=recordInEdit)
        if(value=="()"||value==")"||value=="(")markTypedParens(editor.cursor)
    }
    fun markTypedParens(cursor:Int) {typedParens=TypedParens.mark(typedParens,editor.source,cursor)}
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
    /** AC in the next-input state: the finished calculation moves up to the tape and a fresh input line appears. */
    fun ac() {
        if(committed&&result!=null&&editor.source.isNotBlank()) {
            rememberUndo(editor)
            nextEntry()
            answerDisplay=null
            lastCalcValues=emptyMap();lastCalcSource=""
            error="";shift=false;alpha=false;hyperbolic=false
            inputVersion++
            save()
            return
        }
        clear()
    }
    fun fresh(value:Editor=Editor()) {if(editor.source.isNotEmpty())rememberUndo(editor);nextEntry();edit(value,recordUndo=false)}
    fun fraction() {
        var recordInEdit=true
        if(committed) {
            rememberUndo(editor);recordInEdit=false
            val assignment=result?.optBoolean("assignment")==true
            val previous=if(assignment)null else result?.optJSONObject("tree")
            nextEntry();inputAnswer=if(assignment)null else variables.optJSONObject("Ans");answerDisplay=previous
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
    fun resetSetup(){angle="DEG";precision=30;displayDigits=10;decimal=false;resultDisplayMode=ResultDisplayMode.OFF;thousandsSeparator=false;mixedNumbers=false;overwrite=false;clear(recordUndo=false);save()}
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
        if(calcSession!=null || mode !in listOf("Scientific/CAS","Equations") || previewRunner?.isActive==true)return
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
    internal fun commit(source:String,response:JSONObject) {
        if(committed)return
        exitEngineering()
        result=response;dmsDisplay=response.optBoolean("dms");dmsConversion=false;resultSource=source;committed=true;commitRequested=false;busy=false
        val next=JSONObject(variables.toString())
        if(response.has("resultAst"))next.put("Ans",response.getJSONObject("resultAst")) else next.remove("Ans")
        variables=next
        lastAnswerResult=response
        appendHistory(HistoryEntry(System.currentTimeMillis(),source,response.optString("exact"),response.optString("decimal"),mode,inputTree=inputTree()?.toString() ?: "",response=response.toString(),answer=inputAnswer?.toString() ?: ""))
        save()
    }
    fun request(action: String = "evaluate") = JSONObject().put("action",action).put("precision",precision).put("displayDigits",displayDigits).put("angle",angle).put("variables",JSONObject(variables.toString()).apply{inputAnswer?.let{put("Ans",it)}}).put("functions",functions).put("assumptions",assumptions)
    internal fun calculationTree(source:String):Expr {
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
        fun collect(node:JSONObject,bound:Set<String>,expanding:Set<String>):Boolean {
            val kind=node.optString("kind")
            val value=node.optString("value")
            if(kind=="symbol") {
                if(value in constants || value in bound)return false
                val definition=variables.optJSONObject(value)
                if(definition?.has("start")==true && value !in expanding && collect(definition,bound,expanding+value))return true
                names+=value
                return true
            }
            val args=node.optJSONArray("args") ?: return false
            val binder=if(kind=="call" && value in setOf("integrate","diff","nderivative","limit","sum","product","solve","nsolve","nintegrate","series"))
                args.optJSONObject(1)?.takeIf {it.optString("kind")=="symbol"}?.optString("value") else null
            var found=false
            for(index in 0 until args.length()) {
                if(index==1 && binder!=null)continue
                val child=args.optJSONObject(index) ?: continue
                if(collect(child,if(index==0 && binder!=null)bound+binder else bound,expanding))found=true
            }
            return found
        }
        collect(JSONObject(tree.json()),emptySet(),emptySet())
        if(names.isEmpty()){calculate();return}
        previewRunner?.cancel();previewRunner=null;inputVersion++;commitRequested=false;busy=false;committed=false
        result=null;resultSource="";resultVersion=-1;error=""
        calcUndoHistory=emptyList()
        calcSession=CalcSession(source,names.toList())
    }
    fun editCalcValue(value:Editor) {if(busy)return;calcSession?.let{session->if(value.source!=session.input.source)calcUndoHistory=(calcUndoHistory+session.input).takeLast(100);calcSession=session.copy(input=value)};error=""}
    fun insertCalcValue(text:String,inside:Int=text.length) {
        val session=calcSession ?: return
        editCalcValue(session.input.insertOperand(text,inside))
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
                val value=if(tree==null&&existing==null)JSONObject().put("kind","number").put("value","0") else {
                    val valueTree=tree?.let {JSONObject(it.json())} ?: JSONObject().put("kind","symbol").put("value",session.name)
                    val response=engine.execute(request().put("tree",valueTree))
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
            if(left.kind=="symbol") {store(left.value,source.substring(right.start,right.end),finishInput=source==editor.source);return}
            if(left.kind=="call" && left.args.all {it.kind=="symbol"}) {define(left.value,left.args.joinToString(","){it.value},source.substring(right.start,right.end),finishInput=source==editor.source);return}
        }
        if(mode in listOf("Scientific/CAS","Equations") && source==editor.source) {
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
                    appendHistory(HistoryEntry(System.currentTimeMillis(),source,exact,approx,mode,
                        inputTree=tree.json(),response=response.toString()))
                    save()
                } else error=response.optString("error","Math ERROR")
            } finally { busy=false }
        }
    }
    fun cancel() { inputVersion++;commitRequested=false;previewRunner?.cancel();job?.cancel(); graphJob?.cancel();animationJob?.cancel();graphAnimating=false;analysisJob?.cancel();regressionJob?.cancel();pythonJob?.cancel(); engine.cancel(); busy=false; graphBusy=false;regressionBusy=false;pythonBusy=false;pythonInputPrompt=null;pythonInputSubmit=null;previewBusy=false;error="Calculation cancelled" }
    fun transform(operation: String) { val source=editor.source.ifBlank { "Ans" }; edit(Editor("$operation($source)")); calculate() }
    fun store(name:String,source:String=editor.source.ifBlank { "Ans" },showResult:Boolean=true,finishInput:Boolean=false) =
        with(CalculatorVariableActions) { performStore(name,source,showResult,finishInput) }
    fun memory(direction:Int,source:String=editor.source) = with(CalculatorVariableActions) { performMemory(direction,source) }
    fun define(name:String,parameters:String,source:String,showResult:Boolean=true,finishInput:Boolean=false) =
        with(CalculatorVariableActions) { performDefine(name,parameters,source,showResult,finishInput) }
    fun exportFunctions():String = with(CalculatorVariableActions) { performExportFunctions() }
    fun importFunctions(text:String):String = with(CalculatorVariableActions) { performImportFunctions(text) }
    fun assume(name:String,assumption:String) = with(CalculatorVariableActions) { performAssume(name,assumption) }
    fun removeVariable(name:String) = with(CalculatorVariableActions) { performRemoveVariable(name) }
    fun deleteAllVariables() = with(CalculatorVariableActions) { performDeleteAllVariables() }
    fun favorite(id: Long) { history=history.map { if(it.id==id) it.copy(favorite=!it.favorite) else it }; save() }
    fun deleteHistory(id: Long) { history=history.filter { it.id!=id }; save() }
    fun clearHistory() { history=history.filter { it.favorite };tape=emptyList();save() }
    fun saveDataSet(name:String,csv:String,kind:String) = with(CalculatorStatisticsActions) { performSaveDataSet(name,csv,kind) }
    fun deleteDataSet(name:String) = with(CalculatorStatisticsActions) { performDeleteDataSet(name) }
    fun saveStatistics(name:String,data:String,kind:String,regression:String,plot:String,csv:Boolean,selected:String,isNew:Boolean,customFormula:String,customVariable:String,customInitials:String) =
        with(CalculatorStatisticsActions) { performSaveStatistics(name,data,kind,regression,plot,csv,selected,isNew,customFormula,customVariable,customInitials) }
    fun fitRegression(source:String,data:String) = with(CalculatorStatisticsActions) { performFitRegression(source,data) }
    fun clearRegression() = with(CalculatorStatisticsActions) { performClearRegression() }
    fun cancelRegression() = with(CalculatorStatisticsActions) { performCancelRegression() }
    fun plot(auto: Boolean = false) = with(CalculatorGraphActions) { performPlot(auto) }
    fun setGraphParameter(name:String,value:Double) = with(CalculatorGraphActions) { performSetGraphParameter(name,value) }
    fun setGraphParameterRange(name:String,low:Double,high:Double) = with(CalculatorGraphActions) { performSetGraphParameterRange(name,low,high) }
    fun resetGraphParameters() = with(CalculatorGraphActions) { performResetGraphParameters() }
    fun toggleGraphAnimation() = with(CalculatorGraphActions) { performToggleGraphAnimation() }
    fun editPython(source:String,start:Int=source.length,end:Int=start) = with(CalculatorPythonActions) { performEditPython(source,start,end) }
    fun insertPython(snippet:String,inside:Int=snippet.length) = with(CalculatorPythonActions) { performInsertPython(snippet,inside) }
    fun newPythonFile() = with(CalculatorPythonActions) { performNewPythonFile() }
    fun openPythonFile(source:String,name:String,uri:String) = with(CalculatorPythonActions) { performOpenPythonFile(source,name,uri) }
    fun savedPythonFile(name:String,uri:String) = with(CalculatorPythonActions) { performSavedPythonFile(name,uri) }
    fun pythonFileError(message:String) = with(CalculatorPythonActions) { performPythonFileError(message) }
    fun runPython() = with(CalculatorPythonActions) { performRunPython() }
    fun submitPythonInput(value:String) = with(CalculatorPythonActions) { performSubmitPythonInput(value) }
    fun stopPython() = with(CalculatorPythonActions) { performStopPython() }
    fun updateGraphSource(source:String) = with(CalculatorGraphActions) { performUpdateGraphSource(source) }
    fun toggleGraphDerivative(selected:Int) = with(CalculatorGraphActions) { performToggleGraphDerivative(selected) }
    fun sendExpressionToGraph() = with(CalculatorGraphActions) { performSendExpressionToGraph() }
    fun changeGraphKind(kind:String) = with(CalculatorGraphActions) { performChangeGraphKind(kind) }
    fun analyzeGraph(action:String,first:String,second:String,selected:Int,other:Int) =
        with(CalculatorGraphActions) { performAnalyzeGraph(action,first,second,selected,other) }
    fun clearGraphTangent() = with(CalculatorGraphActions) { performClearGraphTangent() }
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
        val beforeCursor=editor.source.getOrNull(minOf(editor.cursor,editor.anchor)-1)
        if(editor.source.isBlank()||committed&&result?.optBoolean("assignment")==true||
            editor.cursor==editor.anchor && (beforeCursor==null||beforeCursor in listOf('+','-','−','×','*','÷','/','(', '[', ',', '=')))insert("()$suffix",1)
        else insert(suffix,if(suffix=="^()")2 else suffix.length)
    }
    fun enterEngineering() { if(!poweredOn||result==null)return;engineeringConversion=true;engineeringShift=0 }
    fun shiftEngineering(delta:Int) { if(engineeringConversion)engineeringShift=(engineeringShift+delta).coerceIn(-40_000,40_000) }
    fun exitEngineering() { engineeringConversion=false;engineeringShift=0 }
    override fun onCleared() { save(); engine.close(); super.onCleared() }
}

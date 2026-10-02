package com.kirinonakar.calcmax.calculator

import androidx.lifecycle.viewModelScope
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import com.kirinonakar.calcmax.math.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

internal fun graphExpressionTarget(source:String):Pair<String,String> {
    val tree=Parser(source).parse()
    if(tree.kind=="relation") {
        require(tree.value in listOf("=","==")) {"Graph an expression, y = f(x), or an equation F(x,y)=0"}
        if(tree.args[0].kind=="symbol" && tree.args[0].value=="z") {
            val rhs=tree.args[1]
            return "surface" to source.substring(rhs.start,rhs.end)
        }
    }
    return "cartesian" to source
}

internal fun appendGraphSource(existing:String,source:String,kind:String="cartesian"):String {
    val lines=existing.lines().filter(String::isNotBlank)
    val limit=if(kind in listOf("surface","differential"))1 else 6
    if(lines.any {it.trim()==source.trim()})return existing
    require(lines.size<8 && lines.count {!it.trim().startsWith("[shade]")}<limit) {"Graph limit reached. Remove a function before adding another."}
    return existing.trimEnd()+(if(lines.isEmpty())"" else "\n")+source
}

internal object CalculatorGraphActions {
    private suspend fun nextAnimationFrame():Long = suspendCancellableCoroutine {continuation->
        val clock=Choreographer.getInstance()
        val callback=Choreographer.FrameCallback {time->if(continuation.isActive)continuation.resume(time)}
        clock.postFrameCallback(callback)
        continuation.invokeOnCancellation {Handler(Looper.getMainLooper()).post {clock.removeFrameCallback(callback)}}
    }
    fun CalculatorModel.performPlot(auto: Boolean = false) {
        val limit=if(graphKind in listOf("surface","differential")) 1 else 6
        // Coalesce animation ticks before reparsing or allocating another request.
        if(graphAnimating && graphJob?.isActive==true) {graphPendingPlot={performPlot(auto)};return}
        val trees=mutableListOf<JSONObject>()
        val shadings=JSONArray()
        try {
            graphSource.lines().filter { it.isNotBlank() }.take(if(graphKind in listOf("surface","differential")) 1 else 8).forEach { raw->
                val line=raw.trim()
                if(line.startsWith("[shade]")) {
                    if(graphKind!="cartesian")throw SyntaxException("Shading is available on Cartesian graphs",0)
                    if(shadings.length()<4)shadings.put(shadeEntry(line.removePrefix("[shade]").trim()))
                    return@forEach
                }
                if(trees.size<limit) trees+=JSONObject(Parser(line).parse().json())
            }
        } catch(e:Exception) { error=e.message ?: "Syntax ERROR"; return }
        if(trees.isEmpty() && shadings.length()==0) { error="Enter a function"; return }
        val derivativeSelected=graphDerivativeSelected?.takeIf {graphKind=="cartesian" && it in trees.indices}
        val source=graphSource;val kind=graphKind;val min=if(kind in listOf("cartesian","implicit","surface"))xMin else parameterMin;val max=if(kind in listOf("cartesian","implicit","surface"))xMax else parameterMax
        val viewYMin=yMin;val viewYMax=yMax;val parameters=graphState.parameterPayload()
        val request=request("graph").put("angle","RAD").put("trees",JSONArray(trees)).put("graphKind",kind)
            .put("variable",when(kind){"cartesian","implicit","surface"->"x";"sequence"->"n";else->"t"})
            .put("min",min).put("max",max).put("samples",if(graphAnimating)200 else 500).put("xMin",xMin).put("xMax",xMax).put("yMin",viewYMin).put("yMax",viewYMax)
            .put("parameters",parameters)
        if(derivativeSelected!=null)request.put("derivativeSelected",derivativeSelected)
        if(shadings.length()>0)request.put("shadings",shadings)
        if(kind=="surface") {
            val density=SurfaceMesh.sampleCount(xMin,xMax,yMin,yMax,surfaceSamples,surfaceAutoDensity,surfaceZoom.toDouble())
            request.put("surfaceYMin",yMin).put("surfaceYMax",yMax).put("surfaceSamples",if(graphAnimating)minOf(density,32) else density)
        }
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
        val signature=request.toString()
        // The screen's delayed auto-plot can repeat a transfer or explicit Plot request.
        // Cancelling an active engine call restarts its process, so reuse that request.
        if(graphRequestSignature==signature && graphJob?.isActive==true || auto && graphState.graphResultSignature==signature && graphData!=null) return
        // Conflate updates while a request is running. Cancelling each frame
        // restarts the Python process and discards its compiled function cache.
        if(graphJob?.isActive==true) {graphPendingPlot={performPlot(auto)};return}
        graphRequestSignature=signature
        graphJob=viewModelScope.launch {
            graphBusy=true; error=""
            try {
                val response=engine.execute(request)
                if(source==graphSource && kind==graphKind && derivativeSelected==graphDerivativeSelected && min==(if(kind in listOf("cartesian","implicit","surface"))xMin else parameterMin) && max==(if(kind in listOf("cartesian","implicit","surface"))xMax else parameterMax) && viewYMin==yMin && viewYMax==yMax && (graphAnimating || parameters.toString()==graphState.parameterPayload().toString())) {
                    if(response.optBoolean("ok")) {
                    } else error=response.optString("error")
                    graphState.applyPlotResponse(response,signature)
                }
                if(!graphState.graphAnimating)save()
            } finally {
                graphBusy=false
                graphJob=null
                val pending=graphPendingPlot;graphPendingPlot=null
                if(isActive)pending?.invoke()
            }
        }
    }
    fun CalculatorModel.performSetGraphParameter(name:String,value:Double,expandRange:Boolean=false) {
        graphState.setParameter(name,value,expandRange)
    }
    fun CalculatorModel.performSetGraphParameterRange(name:String,low:Double,high:Double) {
        if(name !in graphState.graphParameters)return
        if(!graphState.setParameterRange(name,low,high)) {error="Enter finite values with minimum < maximum";return}
        error="";save()
    }
    fun CalculatorModel.performResetGraphParameters() {
        graphState.resetParameters()
        save()
    }
    fun CalculatorModel.performToggleGraphAnimation() {
        if(graphState.graphAnimating) {
            graphState.graphAnimating=false
            animationJob?.cancel()
            animationJob=null
            save()
            plot()
            return
        }
        if(graphState.graphParameters.isEmpty())return
        animationJob?.cancel()
        graphState.graphAnimating=true
        graphState.beginAnimation()
        animationJob=viewModelScope.launch {
            var previous=0L
            while(isActive&&graphState.graphAnimating) {
                val now=nextAnimationFrame()
                if(mode!="Graph") {graphState.graphAnimating=false;animationJob=null;save();break}
                // Match display frames, capped at 60 updates on high-refresh screens.
                if(previous!=0L&&now-previous<16_666_666L)continue
                val seconds=if(previous==0L)0.0 else (now-previous)/1_000_000_000.0
                previous=now
                if(graphState.advanceAnimation(seconds))plot()
            }
        }
    }
    private fun splitTopLevel(text:String):List<String> {
        val parts=mutableListOf<String>();var depth=0;var start=0
        text.forEachIndexed { index,ch->
            when(ch) {
                '(', '[', '{'->depth++
                ')', ']', '}'->if(depth>0)depth--
                ','->if(depth==0) {parts+=text.substring(start,index);start=index+1}
            }
        }
        parts+=text.substring(start)
        return parts.map(String::trim).filter(String::isNotEmpty)
    }
    /** [shade] y<f(x) shades a region; [shade] f or [shade] f, g shades the area to the axis or between the curves, with an optional a..b interval. */
    private fun shadeEntry(body:String):JSONObject {
        val items=splitTopLevel(body)
        if(items.isEmpty())throw SyntaxException("[shade] needs an inequality or one or two functions",0)
        var range:Pair<String,String>?=null
        val expressions=mutableListOf<String>()
        items.forEach { item->
            val pieces=item.split("..")
            if(pieces.size==2&&pieces[0].isNotBlank()&&pieces[1].isNotBlank()&&range==null)range=pieces[0].trim() to pieces[1].trim()
            else expressions+=item
        }
        if(expressions.isEmpty()||expressions.size>2)throw SyntaxException("[shade] takes one or two functions",0)
        val entry=JSONObject()
        range?.let {entry.put("a",JSONObject(Parser(it.first).parse().json())).put("b",JSONObject(Parser(it.second).parse().json()))}
        val parsed=expressions.map {Parser(it).parse()}
        if(parsed.size==1&&parsed[0].kind=="relation") {
            val tree=parsed[0]
            val left=tree.args.getOrNull(0);val right=tree.args.getOrNull(1)
            if(left==null||right==null||tree.value !in listOf("<","<=",">",">="))throw SyntaxException("[shade] needs y < f(x) or y > f(x)",tree.start)
            val boundary=when {
                left.kind=="symbol"&&left.value=="y"->right
                right.kind=="symbol"&&right.value=="y"->left
                else->throw SyntaxException("[shade] needs y < f(x) or y > f(x)",tree.start)
            }
            val below=if(left.kind=="symbol"&&left.value=="y")tree.value.startsWith("<") else tree.value.startsWith(">")
            entry.put("mode","halfplane").put("side",if(below)"below" else "above").put("trees",JSONArray(listOf(JSONObject(boundary.json()))))
        } else entry.put("mode","band").put("trees",JSONArray(parsed.map {JSONObject(it.json())}))
        return entry
    }
    fun CalculatorModel.performUpdateGraphSource(source:String) {
        graphState.updateSource(source)
        save()
    }
    fun CalculatorModel.performToggleGraphDerivative(selected:Int) {
        if(graphKind!="cartesian")return
        if(graphDerivativeSelected!=null) {graphDerivativeSelected=null;return}
        val sources=graphSource.lines().filter(String::isNotBlank).take(8).map(String::trim).filter {!it.startsWith("[shade]")}.take(6)
        if(selected !in sources.indices) {error="Select a function";return}
        graphDerivativeSelected=selected
    }
    fun CalculatorModel.performSendExpressionToGraph() {
        val source=editor.source.trim()
        if(source.isEmpty()) {error="Enter an expression to graph";return}
        val target=try {graphExpressionTarget(source)} catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
        val next=try {appendGraphSource(graphState.sourceForKind(target.first),target.second,target.first)} catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
        changeGraphKind(target.first)
        updateGraphSource(next)
        error="";mode="Graph"
    }
    fun CalculatorModel.performChangeGraphKind(kind:String) {
        if(!graphState.changeKind(kind))return
        animationJob?.cancel()
        save()
    }
    fun CalculatorModel.performAnalyzeGraph(action:String,first:String,second:String,selected:Int,other:Int) {
        if(graphKind !in listOf("cartesian","parametric","polar")) {error="Analysis requires a Cartesian, parametric or polar graph";return}
        val singled=action in listOf("derivative","tangent")
        val a=first.toDoubleOrNull();val b=if(singled)a else second.toDoubleOrNull()
        if(a==null || !a.isFinite() || b==null || !b.isFinite() || (!singled && a>=b)) {error="Enter finite values with a < b";return}
        val sources=graphSource.lines().filter {it.isNotBlank()}.take(8).filter {graphKind!="cartesian" || !it.trim().startsWith("[shade]")}.take(6)
        if(sources.isEmpty() || selected !in sources.indices || action=="intersection" && (other !in sources.indices || other==selected)) {error="Select two different functions";return}
        val trees=try {JSONArray(sources.map {JSONObject(Parser(it).parse().json())})} catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
        analysisJob?.cancel()
        val source=graphSource
        val kind=graphKind
        val parameters=graphState.parameterPayload()
        val analysisRequest=request("graphAnalysis").put("angle","RAD").put("trees",trees).put("graphKind",kind).put("analysis",action).put("a",a).put("b",b).put("selected",selected).put("other",other).put("variable",if(kind=="cartesian")"x" else "t").put("parameters",parameters).put("xMin",xMin).put("xMax",xMax).put("yMin",yMin).put("yMax",yMax)
        trace?.let {analysisRequest.put("tracePoint",JSONArray(listOf(it.first,it.second)))}
        analysisJob=viewModelScope.launch {
            graphState.graphAnalysisBusy=true;error="";graphState.graphAnalysis=null
            try {
                val response=engine.execute(analysisRequest)
                if(source==graphSource && kind==graphKind && parameters.toString()==graphState.parameterPayload().toString()) {
                    if(response.optBoolean("ok")) {
                        graphState.graphAnalysis=response
                        shadedInterval=if(action=="integral" && graphKind=="cartesian")a to b else null
                        response.optJSONArray("points")?.optJSONArray(0)?.let {trace=it.getDouble(0) to it.getDouble(1)}
                    } else error=response.optString("error","Analysis failed")
                }
            } finally {graphState.graphAnalysisBusy=false}
        }
    }
    fun CalculatorModel.performClearGraphTangent() {
        analysisJob?.cancel()
        analysisJob=null
        graphState.graphAnalysisBusy=false
        graphState.graphAnalysis=null
        trace=null
        shadedInterval=null
    }
}

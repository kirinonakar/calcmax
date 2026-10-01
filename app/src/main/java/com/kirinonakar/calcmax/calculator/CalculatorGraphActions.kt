package com.kirinonakar.calcmax.calculator

import androidx.lifecycle.viewModelScope
import com.kirinonakar.calcmax.math.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.sin

internal object CalculatorGraphActions {
    fun CalculatorModel.performPlot(auto: Boolean = false) {
        val limit=if(graphKind in listOf("surface","differential")) 1 else 6
        val trees=mutableListOf<JSONObject>()
        val curveSources=mutableListOf<String>()
        val shadings=JSONArray()
        try {
            graphSource.lines().filter { it.isNotBlank() }.take(if(graphKind in listOf("surface","differential")) 1 else 8).forEach { raw->
                val line=raw.trim()
                if(line.startsWith("[shade]")) {
                    if(graphKind!="cartesian")throw SyntaxException("Shading is available on Cartesian graphs",0)
                    if(shadings.length()<4)shadings.put(shadeEntry(line.removePrefix("[shade]").trim()))
                    return@forEach
                }
                if(trees.size<limit) {trees+=JSONObject(Parser(line).parse().json());curveSources+=line}
            }
        } catch(e:Exception) { error=e.message ?: "Syntax ERROR"; return }
        if(trees.isEmpty() && shadings.length()==0) { error="Enter a function"; return }
        val derivativeSelected=graphDerivativeSelected?.takeIf {graphKind=="cartesian" && it in trees.indices}
        if(derivativeSelected!=null) {
            val source=curveSources.getOrNull(derivativeSelected)
            if(source!=null) {
                try {trees+=JSONObject(Parser("diff(($source),x)").parse().json())}
                catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
            }
        }
        val source=graphSource;val kind=graphKind;val min=if(kind in listOf("cartesian","implicit","surface"))xMin else parameterMin;val max=if(kind in listOf("cartesian","implicit","surface"))xMax else parameterMax
        val viewYMin=yMin;val viewYMax=yMax;val parameters=graphState.parameterPayload()
        val request=request("graph").put("angle","RAD").put("trees",JSONArray(trees)).put("graphKind",kind)
            .put("variable",when(kind){"cartesian","implicit","surface"->"x";"sequence"->"n";else->"t"})
            .put("min",min).put("max",max).put("samples",500).put("xMin",xMin).put("xMax",xMax).put("yMin",viewYMin).put("yMax",viewYMax)
            .put("parameters",parameters)
        if(derivativeSelected!=null)request.put("derivativeCurveIndex",trees.lastIndex)
        if(shadings.length()>0)request.put("shadings",shadings)
        if(kind=="surface")request.put("surfaceYMin",yMin).put("surfaceYMax",yMax).put("surfaceSamples",SurfaceMesh.sampleCount(xMin,xMax,yMin,yMax,surfaceSamples,surfaceAutoDensity,surfaceZoom.toDouble()))
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
                        if(derivativeSelected!=null)response.put("derivativeSelected",derivativeSelected).put("derivativeCurveIndex",trees.lastIndex)
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
    fun CalculatorModel.performSetGraphParameter(name:String,value:Double) {
        graphState.setParameter(name,value)
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
            return
        }
        if(graphState.graphParameters.isEmpty())return
        animationJob?.cancel()
        graphState.graphAnimating=true
        animationPhase=0.0
        var ticks=0
        animationJob=viewModelScope.launch {
            while(isActive&&graphState.graphAnimating) {
                delay(50)
                animationPhase+=0.05
                if(animationPhase>2*PI)animationPhase-=2*PI
                val swing=(sin(animationPhase)+1.0)/2.0
                if(graphState.animateParameters(swing)&&++ticks>=4) {ticks=0;plot()}
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
        val tree=try {Parser(source).parse()} catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
        val implicit=tree.kind=="relation" && tree.value in listOf("=","==") &&
            !(tree.args[0].kind=="symbol" && tree.args[0].value=="z") &&
            !(tree.args[0].kind=="symbol" && tree.args[0].value=="y" && tree.args[1].nodes().none {it.kind=="symbol" && it.value=="y"})
        if(implicit) {
            changeGraphKind("implicit")
            updateGraphSource(source)
            error="";mode="Graph"
            return
        }
        var lhsVar:String?=null
        var rhsTree=tree
        var rhsSource=source
        if(tree.kind=="relation" && tree.value=="=" && tree.args.size==2 && tree.args[0].kind=="symbol" && tree.args[0].value in listOf("y","z")) {
            lhsVar=tree.args[0].value
            rhsTree=tree.args[1]
            rhsSource=source.substring(rhsTree.start,rhsTree.end)
        } else if(tree.kind=="relation") {error="Graph an expression, y = f(x), or z = f(x,y)";return}
        val symbols=rhsTree.nodes().filter {it.kind=="symbol"}.map {it.value}.toSet()
        val wantSurface=(lhsVar=="z")||("x" in symbols && "y" in symbols)
        if(wantSurface) {
            try {Parser(rhsSource).parse()} catch(e:Exception) {error=e.message ?: "Syntax ERROR";return}
            changeGraphKind("surface")
            updateGraphSource(rhsSource)
        } else {
            changeGraphKind("cartesian")
            updateGraphSource(rhsSource)
        }
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
        analysisJob=viewModelScope.launch {
            graphState.graphAnalysisBusy=true;error="";graphState.graphAnalysis=null
            try {
                val response=engine.execute(request("graphAnalysis").put("angle","RAD").put("trees",trees).put("graphKind",graphKind).put("analysis",action).put("a",a).put("b",b).put("selected",selected).put("other",other).put("variable",if(graphKind=="cartesian")"x" else "t").put("parameters",graphState.parameterPayload()).put("xMin",xMin).put("xMax",xMax).put("yMin",yMin).put("yMax",yMax))
                if(source==graphSource && graphKind in listOf("cartesian","parametric","polar")) {
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

package com.kirinonakar.calcmax.ui

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.math.PiAxis
import com.kirinonakar.calcmax.math.GraphZoom
import com.kirinonakar.calcmax.math.SurfaceBounds
import com.kirinonakar.calcmax.math.SurfaceMesh
import com.kirinonakar.calcmax.math.SurfaceProjection
import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.*

@Composable fun GraphScreen(m: CalculatorModel) {
    val c=LocalInstrument.current
    val focusManager=LocalFocusManager.current
    var rangeDialog by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf(false) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    var parametersOpen by rememberSaveable { mutableStateOf(true) }
    var rangeParameter by remember { mutableStateOf<String?>(null) }
    var surfaceRotation by rememberSaveable { mutableFloatStateOf(35f) }
    var surfaceElevation by rememberSaveable { mutableFloatStateOf(32f) }
    var halfGraphHeight by rememberSaveable { mutableStateOf(false) }
    var first by rememberSaveable { mutableStateOf(m.xMin.toString()) }; var second by rememberSaveable { mutableStateOf(m.xMax.toString()) }
    var pointAt by rememberSaveable { mutableStateOf(((m.xMin+m.xMax)/2).toString()) }
    var tangentPositionOpen by rememberSaveable { mutableStateOf(false) }
    var scrollToSection by remember { mutableStateOf<String?>(null) }
    val graphScrollState=rememberScrollState()
    val tableRequester=remember {BringIntoViewRequester()}
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var other by rememberSaveable { mutableIntStateOf(1) }
    LaunchedEffect(m.graphKind){
        selected=0;other=1;tangentPositionOpen=false
        val low=if(m.graphKind=="cartesian")m.xMin else m.parameterMin
        val high=if(m.graphKind=="cartesian")m.xMax else m.parameterMax
        first=low.toString();second=high.toString();pointAt=((low+high)/2).toString()
    }
    LaunchedEffect(m.graphSource) {tangentPositionOpen=false}
    LaunchedEffect(scrollToSection,analysis,showTable) {
        delay(100)
        when(scrollToSection) {
            "analysis" -> if(analysis)graphScrollState.animateScrollTo(graphScrollState.maxValue)
            "table" -> if(showTable)tableRequester.bringIntoView()
        }
        scrollToSection=null
    }
    val parameterSignature=m.graphParameters.entries.joinToString(","){"${it.key}=${it.value.value}"}
    LaunchedEffect(m.graphSource,m.graphDerivativeSelected,m.xMin,m.xMax,m.yMin,m.yMax,m.graphKind,m.parameterMin,m.parameterMax,m.sequenceInitials,m.differentialInitials,m.differentialT0,if(m.graphAnimating)"animation" else parameterSignature,m.surfaceSamples,m.surfaceAutoDensity,if(m.surfaceAutoDensity)m.surfaceZoom else 1f) { if(!m.graphAnimating){delay(350);m.plot(auto=true)} }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Cartesian and every other graph use the same viewport height. Expression
    // rows, sliders, and settings scroll with the plot instead of resizing it.
    val graphHeight=if(maxWidth.value.isFinite())maxWidth else 360.dp
    val plotHeight=if(halfGraphHeight)graphHeight*0.5f else graphHeight
    Column(Modifier.fillMaxSize().verticalScroll(graphScrollState)) {
    Column(Modifier.fillMaxWidth().zIndex(1f)) {
        OutlinedTextField(m.graphSource,{m.updateGraphSource(it)},Modifier.fillMaxWidth().padding(start=10.dp,end=10.dp,top=8.dp),label={Text(tr(when(m.graphKind){"parametric"->"One [x(t),y(t)] pair per line";"polar"->"r(t) · radians · one curve per line";"sequence"->"u(n) · use u(n−1) for recurrences";"surface"->"z = f(x,y)";"differential"->"dy/dt = f(t,y)";else->"Function / y=f(x) · Implicit / F(x,y)=0"}))},placeholder={if(m.graphKind=="cartesian")Text("x+1\ny=x+1\ny^2+x^2=1")},minLines=if(m.graphKind in listOf("surface","differential"))1 else 2,maxLines=4)
        if(m.graphKind=="cartesian")Text(tr("One curve per line · [shade] y<f(x) · between functions: [shade] f, g"),Modifier.padding(horizontal=14.dp,vertical=3.dp),fontSize=11.sp,color=c.muted)
        Column(Modifier.fillMaxWidth().zIndex(1f).background(c.body)) {
            Row(Modifier.fillMaxWidth().zIndex(2f).padding(top=2.dp,bottom=1.dp).horizontalScroll(rememberScrollState()).semantics { contentDescription="Graph types" },horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("cartesian" to "Cartesian","parametric" to "Parametric","polar" to "Polar","sequence" to "Sequence","surface" to "3D surface","differential" to "Diff eq").forEach {(kind,label)->
                    SmallAction(label,active=if(m.graphKind==kind)true else null,shaded=m.graphKind==kind) {m.changeGraphKind(kind)}
                }
            }
            if(m.graphKind=="sequence") Field(m.sequenceInitials,"Initial values at n=0 · comma separated",Modifier.fillMaxWidth()) {m.sequenceInitials=it;m.save()}
            if(m.graphKind=="differential") {
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {GraphNumberField(m.differentialT0,"Initial time t₀",m.displayDigits,Modifier.weight(1f)){m.differentialT0=it;m.save()};Field(m.differentialInitials,"Initial y values · comma separated",Modifier.weight(2f)){m.differentialInitials=it;m.save()} }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                SmallAction("Plot") {focusManager.clearFocus();m.plot()};SmallAction("Range") {rangeDialog=true}
                if(m.graphKind in listOf("cartesian","parametric","polar"))SmallAction("Analyze",active=if(analysis)true else null,shaded=analysis) {analysis=!analysis;if(analysis)scrollToSection="analysis"}
                if(m.graphKind!="surface")SmallAction("Table",active=if(showTable)true else null,shaded=showTable) {showTable=!showTable;if(showTable)scrollToSection="table"}
                if(m.graphKind in listOf("cartesian","parametric","polar"))SmallAction(if(m.radianAxis)"x: π rad" else "x: decimal"){m.radianAxis=!m.radianAxis;m.save()}
            }
            if(m.graphParameters.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=10.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                    SmallAction(if(parametersOpen)"Parameters ▾" else "Parameters ▸"){focusManager.clearFocus();parametersOpen=!parametersOpen}
                    SmallAction(if(m.graphAnimating)"Stop" else "Animate",active=if(m.graphAnimating)true else null,shaded=m.graphAnimating){focusManager.clearFocus();m.toggleGraphAnimation()}
                    SmallAction("Reset sliders"){focusManager.clearFocus();m.resetGraphParameters()}
                }
                if(parametersOpen)Column(Modifier.fillMaxWidth().heightIn(max=150.dp).verticalScroll(rememberScrollState())) {
                    m.graphParameters.entries.forEach { (name,spec)->
                        key(name) {
                            val animateLabel="${tr("Animate")}: $name"
                            val low=spec.min.toFloat();val high=spec.max.toFloat()
                            Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(spec.animate,{m.setGraphParameterAnimation(name,it)},Modifier.size(32.dp).semantics {contentDescription=animateLabel})
                                Text(name,Modifier.width(24.dp),fontSize=13.sp,color=c.accent,fontWeight=FontWeight.SemiBold)
                                CompactSlider(spec.value.toFloat(),{focusManager.clearFocus();m.setGraphParameter(name,it.toDouble())},Modifier.weight(1f),valueRange=(if(low<high)low else low-1f)..(if(high>low)high else low+1f))
                                GraphParameterValueField(name,spec.value,m.displayDigits,{value->m.setGraphParameter(name,value,expandRange=true);m.error="";m.save()},{m.error=it})
                                Box(Modifier.size(30.dp).background(c.scientific,RoundedCornerShape(8.dp)).clickable{focusManager.clearFocus();rangeParameter=name}.semantics {contentDescription="Set $name slider range"},contentAlignment=Alignment.Center) {
                                    Text("±",fontSize=16.sp,color=c.accent)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        val allCurves=remember(m.graphData) {
            val array=m.graphData?.optJSONArray("curves")
            (0 until (array?.length() ?: 0)).map { ci->val curve=array!!.getJSONArray(ci); (0 until curve.length()).map { k->curve.optJSONArray(k)?.let { it.getDouble(0) to it.getDouble(1) } } }
        }
        val derivativeIndex=m.graphData?.optInt("derivativeCurveIndex",-1) ?: -1
        val curves=allCurves.filterIndexed {index,_->index!=derivativeIndex}
        val derivativeSelected=m.graphDerivativeSelected
        val derivativeCurve=allCurves.getOrNull(derivativeIndex)?.takeIf {derivativeSelected==m.graphData?.optInt("derivativeSelected",-1)}
        val derivativeExpression=if(derivativeCurve!=null)m.graphData?.optString("derivativeExpression").orEmpty() else ""
        val latestCurves by rememberUpdatedState(curves)
        val integralFill=remember(m.graphAnalysis) {
            val polygons=m.graphAnalysis?.optJSONArray("integralFill")
            (0 until (polygons?.length() ?: 0)).map {index->
                val polygon=polygons!!.getJSONArray(index)
                (0 until polygon.length()).mapNotNull {i->polygon.optJSONArray(i)?.let {it.getDouble(0) to it.getDouble(1)}}
            }
        }
        val shadings=remember(m.graphData) {
            val array=m.graphData?.optJSONArray("shadings")
            (0 until (array?.length() ?: 0)).mapNotNull { index->
                val entry=array?.optJSONObject(index) ?: return@mapNotNull null
                val fillArray=entry.optJSONArray("fill")
                val fill=(0 until (fillArray?.length() ?: 0)).map { fi->
                    val polygon=fillArray!!.optJSONArray(fi)
                    (0 until (polygon?.length() ?: 0)).mapNotNull { k->polygon?.optJSONArray(k)?.let {it.getDouble(0) to it.getDouble(1)} }
                }
                val boundaryArray=entry.optJSONArray("boundary")
                val boundary=(0 until (boundaryArray?.length() ?: 0)).map { bi->
                    val line=boundaryArray!!.optJSONArray(bi)
                    (0 until (line?.length() ?: 0)).map { k->line?.optJSONArray(k)?.let {it.getDouble(0) to it.getDouble(1)} }
                }
                fill to boundary
            }
        }
        val tangent=remember(m.graphAnalysis) {
            val line=m.graphAnalysis?.optJSONArray("line")
            if(line==null||line.length()!=2)null else listOfNotNull(line.optJSONArray(0)?.let {it.getDouble(0) to it.getDouble(1)},line.optJSONArray(1)?.let {it.getDouble(0) to it.getDouble(1)}).takeIf {it.size==2}
        }
        val visibleSources=m.graphSource.lines().filter(String::isNotBlank).take(if(m.graphKind in listOf("surface","differential"))1 else 8)
        val sources=visibleSources.filter {!(m.graphKind=="cartesian" && it.trim().startsWith("[shade]"))}.take(if(m.graphKind in listOf("surface","differential"))1 else 6)
        val shadeSources=visibleSources.map(String::trim).filter {m.graphKind=="cartesian" && it.startsWith("[shade]")}.take(4)
        val markers=remember(m.graphAnalysis) {
            val array=m.graphAnalysis?.optJSONArray("points")
            (0 until (array?.length() ?: 0)).mapNotNull {i->array?.optJSONArray(i)?.let {it.getDouble(0) to it.getDouble(1)}}
        }
        val transform=Modifier.pointerInput(m.graphKind) {awaitEachGesture {
            awaitFirstDown(requireUnconsumed=false)
            var axis:String?=null
            var totalPan=Offset.Zero
            var dragging=false
            do {
                val event=awaitPointerEvent()
                val fingers=event.changes.filter{it.pressed&&it.previousPressed}
                if(fingers.isNotEmpty()) {
                    val current=fingers.fold(Offset.Zero){a,p->a+p.position}/fingers.size.toFloat()
                    val previous=fingers.fold(Offset.Zero){a,p->a+p.previousPosition}/fingers.size.toFloat()
                    val pan=current-previous
                    totalPan+=pan
                    if(totalPan.getDistance()>viewConfiguration.touchSlop||fingers.size>=2)dragging=true
                    var zx=1.0;var zy=1.0
                    if(fingers.size>=2) {
                        val old=fingers[1].previousPosition-fingers[0].previousPosition
                        val now=fingers[1].position-fingers[0].position
                        if(axis==null)axis=GraphZoom.axis(old.x,old.y)
                        val factors=GraphZoom.factors(axis!!,old.x,old.y,now.x,now.y);zx=factors.first;zy=factors.second
                    }else axis=null
                    if(dragging) {
                        val w=m.xMax-m.xMin;val h=m.yMax-m.yMin
                        val anchorX=m.xMin+w*previous.x/size.width;val anchorY=m.yMax-h*previous.y/size.height
                        val newW=(w/zx).coerceIn(2e-7,2e8);val newH=(h/zy).coerceIn(2e-7,2e8)
                        m.xMin=anchorX-newW*current.x/size.width;m.xMax=m.xMin+newW
                        m.yMax=anchorY+newH*current.y/size.height;m.yMin=m.yMax-newH
                        event.changes.forEach{it.consume()}
                    }
                }
            }while(event.changes.any{it.pressed})
        }}
        if(m.graphKind=="surface") {
            Box(Modifier.fillMaxWidth().height(plotHeight).clipToBounds()) {
            SurfaceGraph(m,surfaceRotation,surfaceElevation,m.surfaceZoom,m.surfaceRenderMode,m.surfaceColor,Modifier.fillMaxSize().clipToBounds().pointerInput(m.graphKind) {
                detectTransformGestures { _,pan,zoom,_->
                    surfaceRotation=((surfaceRotation+pan.x*.7f)%360f+360f)%360f
                    surfaceElevation=(surfaceElevation+pan.y*.5f).coerceIn(-90f,90f)
                    m.surfaceZoom=(m.surfaceZoom*zoom).coerceIn(.4f,3f)
                }
            })
            GraphHeightToggle(halfGraphHeight,{halfGraphHeight=!halfGraphHeight},Modifier.align(Alignment.TopEnd))
            }
            val surfaceZRange=m.graphData?.let {SurfaceMesh.zRange(m.zMin ?: it.optDouble("zMin",-1.0),m.zMax ?: it.optDouble("zMax",1.0))}
            Column(Modifier.fillMaxWidth()) {
            GraphFormulas(m.graphKind,sources,0,null,"",shadeSources,null,{},m.displayDigits)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(isKorean())"렌더링" else "Rendering",fontSize=11.sp,color=c.muted)
                listOf("wireframe" to (if(isKorean())"와이어프레임" else "Wireframe"),"surface" to (if(isKorean())"표면" else "Surface"),"surface-wireframe" to (if(isKorean())"표면+격자" else "Surface + mesh")).forEach { (mode,label)->
                    SmallAction(label,active=m.surfaceRenderMode==mode,shaded=m.surfaceRenderMode==mode) {m.surfaceRenderMode=mode;m.save()}
                }
            }
            SurfaceAppearanceControls(m)
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("x: ${graphDisplayNumber(m.xMin,m.displayDigits)} ~ ${graphDisplayNumber(m.xMax,m.displayDigits)}   y: ${graphDisplayNumber(m.yMin,m.displayDigits)} ~ ${graphDisplayNumber(m.yMax,m.displayDigits)}"+(surfaceZRange?.let {"   z: ${graphDisplayNumber(it.first,m.displayDigits)} ~ ${graphDisplayNumber(it.second,m.displayDigits)}"} ?: ""),Modifier.weight(1f),fontSize=11.sp,color=c.muted)
            }
            Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Rotate"),fontSize=11.sp,color=c.muted);CompactSlider(surfaceRotation,{surfaceRotation=it},Modifier.weight(1f),valueRange=0f..360f);Text("${graphDisplayNumber(surfaceRotation.toString(),m.displayDigits)}°",fontSize=11.sp,color=c.muted)
            }
            Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Tilt"),fontSize=11.sp,color=c.muted);CompactSlider(surfaceElevation,{surfaceElevation=it},Modifier.weight(1f),valueRange=-90f..90f);Text("${graphDisplayNumber(surfaceElevation.toString(),m.displayDigits)}°",fontSize=11.sp,color=c.muted)
            }
            Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Zoom"),fontSize=11.sp,color=c.muted)
                CompactSlider(m.surfaceZoom,{m.surfaceZoom=it},Modifier.weight(1f),valueRange=.4f..3f,onValueChangeFinished={m.save()})
                Text("${graphDisplayNumber((m.surfaceZoom*100).toString(),m.displayDigits)}%",fontSize=11.sp,color=c.muted)
                Text(tr("Reset"),Modifier.padding(start=4.dp).background(c.scientific,RoundedCornerShape(8.dp)).clickable {
                    m.xMin=-3.0;m.xMax=3.0;m.yMin=-3.0;m.yMax=3.0;m.zMin=null;m.zMax=null
                    surfaceRotation=35f;surfaceElevation=32f;m.surfaceZoom=1f;m.save();m.plot()
                }.padding(horizontal=8.dp,vertical=7.dp),fontSize=11.sp,color=c.accent)
            }
            Text(if(isKorean())"드래그하여 회전 · 손가락 두 개로 확대/축소" else "Drag to rotate freely · Pinch to zoom",Modifier.padding(horizontal=14.dp,vertical=2.dp),fontSize=11.sp,color=c.muted)
            if(m.error.isNotBlank())Text(m.error,Modifier.padding(horizontal=14.dp,vertical=2.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.error)
            }
        } else Box(Modifier.fillMaxWidth().height(plotHeight).clipToBounds()) {
        val derivativeDash=remember {PathEffect.dashPathEffect(floatArrayOf(10f,6f))}
        Canvas(Modifier.fillMaxSize().clipToBounds().background(c.display).then(transform).pointerInput(m.graphKind,selected) { detectTapGestures { p ->
            val target=m.xMin+(m.xMax-m.xMin)*p.x/size.width
            val targetY=m.yMax-(m.yMax-m.yMin)*p.y/size.height
            val xSpan=m.xMax-m.xMin;val ySpan=m.yMax-m.yMin
            if(xSpan<=0.0 || ySpan<=0.0)return@detectTapGestures
            if(m.graphAnalysis?.optString("analysis")=="tangent" && m.graphAnalysis?.optJSONArray("line")!=null) {
                var nearestCurve=-1;var nearestIndex=-1;var nearestDistance=Double.POSITIVE_INFINITY
                latestCurves.forEachIndexed {curveIndex,curve->curve.forEachIndexed {pointIndex,point->
                    if(point!=null) {
                        val dx=(point.first-target)/xSpan*size.width
                        val dy=(point.second-targetY)/ySpan*size.height
                        val distance=hypot(dx,dy)
                        if(distance<nearestDistance) {nearestCurve=curveIndex;nearestIndex=pointIndex;nearestDistance=distance}
                    }
                }}
                if(nearestCurve>=0 && nearestDistance<=40.dp.toPx()) {
                    val point=latestCurves[nearestCurve][nearestIndex]!!
                    val at=if(m.graphKind=="cartesian")point.first else m.graphData?.optJSONArray("curveParameters")?.optJSONArray(nearestCurve)?.optDouble(nearestIndex,Double.NaN)
                    if(at!=null && at.isFinite()) {
                        selected=nearestCurve
                        if(other==selected)other=(selected+1)%latestCurves.size
                        pointAt=at.toString();m.trace=point
                        m.analyzeGraph("tangent",pointAt,pointAt,nearestCurve,other)
                        return@detectTapGestures
                    }
                }
            }
            m.trace=latestCurves.getOrNull(selected)?.filterNotNull()?.minByOrNull { ((it.first-target)/xSpan).pow(2)+((it.second-targetY)/ySpan).pow(2) }
        } }.semantics { contentDescription="Graph with ${curves.size} curves. Pinch to zoom, drag to pan, tap to trace${if(m.graphAnalysis?.optString("analysis")=="tangent")" or move the tangent" else ""}. Use Range and Analyze for accessible controls." }) {
            val xlo=m.xMin;val xhi=m.xMax
            fun px(x:Double)=((x-xlo)/(xhi-xlo)*size.width).toFloat()
            fun py(y:Double)=(size.height-(y-m.yMin)/(m.yMax-m.yMin)*size.height).toFloat()
            fun curvePath(points:List<Pair<Double,Double>?>):Path {
                val path=Path();var previous:Offset?=null
                points.forEach {point->
                    val current=point?.let {Offset(px(it.first),py(it.second))}?.takeIf {it.x.isFinite()&&it.y.isFinite()}
                    if(current!=null) {
                        if(previous!=null)path.lineTo(current.x,current.y)
                        else path.moveTo(current.x,current.y)
                    }
                    previous=current
                }
                return path
            }
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=c.muted.toArgb();textSize=11.sp.toPx() }
            fun step(range:Double):Double { val raw=range/7;val p=10.0.pow(floor(log10(raw)));val v=raw/p;return p*(if(v>5)10 else if(v>2)5 else if(v>1)2 else 1) }
            val sx=if(m.radianAxis)PiAxis.step(xhi-xlo) else step(xhi-xlo);val sy=step(m.yMax-m.yMin)
            clipRect {
                val firstX=ceil(xlo/sx)*sx
                for(index in 0..64) {
                    val x=firstX+index*sx
                    if(!x.isFinite() || x>xhi)break
                    drawLine(c.grid,Offset(px(x),0f),Offset(px(x),size.height))
                    drawContext.canvas.nativeCanvas.drawText(if(m.radianAxis)PiAxis.label(x){graphDisplayNumber(it,m.displayDigits)} else graphDisplayNumber(x,m.displayDigits),px(x)+3,size.height-8,paint)
                }
                val firstY=ceil(m.yMin/sy)*sy
                for(index in 0..64) {
                    val y=firstY+index*sy
                    if(!y.isFinite() || y>m.yMax)break
                    drawLine(c.grid,Offset(0f,py(y)),Offset(size.width,py(y)))
                    drawContext.canvas.nativeCanvas.drawText(graphDisplayNumber(y,m.displayDigits),4f,py(y)-3,paint)
                }
                drawLine(c.muted,Offset(px(0.0),0f),Offset(px(0.0),size.height),2f)
                drawLine(c.muted,Offset(0f,py(0.0)),Offset(size.width,py(0.0)),2f)
                shadings.forEachIndexed { si,shading->
                    val shadeColor=c.curves[si%c.curves.size]
                    shading.first.forEach { polygon->
                        if(polygon.size>=3) {
                            val path=Path()
                            polygon.forEachIndexed { pointIndex,point->if(pointIndex==0)path.moveTo(px(point.first),py(point.second)) else path.lineTo(px(point.first),py(point.second)) }
                            path.close()
                            drawPath(path,shadeColor.copy(alpha=.15f))
                        }
                    }
                    shading.second.forEach { line->
                        drawPath(curvePath(line),shadeColor.copy(alpha=.85f),style=Stroke(1.6.dp.toPx()))
                    }
                }
                integralFill.forEach {polygon->
                    if(polygon.size>=3) {
                        val path=Path()
                        polygon.forEachIndexed {i,point->if(i==0)path.moveTo(px(point.first),py(point.second)) else path.lineTo(px(point.first),py(point.second))}
                        path.close()
                        drawPath(path,c.curves[selected%c.curves.size].copy(alpha=.18f))
                    }
                }
                curves.forEachIndexed { ci,points ->
                    val color=c.curves[ci%c.curves.size]
                    drawPath(curvePath(points),color,style=Stroke(if(ci==selected)4.dp.toPx() else 1.5.dp.toPx()))
                }
                derivativeCurve?.let {points->
                    drawPath(curvePath(points),c.accent,style=Stroke(2.5.dp.toPx(),pathEffect=derivativeDash))
                }
                if(m.graphKind=="differential") {
                    val fieldData=m.graphData?.optJSONArray("fields")
                    val dx=(xhi-xlo)/70.0
                    for(index in 0 until (fieldData?.length() ?: 0)) {
                        val field=fieldData?.optJSONArray(index) ?: continue
                        val x=field.optDouble(0);val y=field.optDouble(1);val slope=field.optDouble(2)
                        if(!x.isFinite()||!y.isFinite()||!slope.isFinite())continue
                        val dy=(slope*dx).coerceIn(-(m.yMax-m.yMin)/14,(m.yMax-m.yMin)/14)
                        drawLine(c.muted.copy(alpha=.55f),Offset(px(x-dx/2),py(y-dy/2)),Offset(px(x+dx/2),py(y+dy/2)),1.dp.toPx())
                    }
                }
                markers.forEachIndexed {index,point ->
                    val at=Offset(px(point.first),py(point.second))
                    if(at.x in 0f..size.width && at.y in 0f..size.height) {
                        drawCircle(c.display,9.dp.toPx(),at)
                        drawCircle(c.accent,5.dp.toPx(),at)
                        drawContext.canvas.nativeCanvas.drawText("${index+1}",at.x+9.dp.toPx(),at.y-9.dp.toPx(),paint)
                    }
                }
                tangent?.let { line->
                    drawLine(c.accent.copy(alpha=.75f),Offset(px(line[0].first),py(line[0].second)),Offset(px(line[1].first),py(line[1].second)),1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(16f,8f)))
                }
                m.trace?.let { p -> val at=Offset(px(p.first),py(p.second));drawLine(c.muted,Offset(at.x,0f),Offset(at.x,size.height),1f);drawCircle(c.accent,6f,at) }
            }
        }
        GraphHeightToggle(halfGraphHeight,{halfGraphHeight=!halfGraphHeight},Modifier.align(Alignment.TopEnd))
        }
        Column(Modifier.fillMaxWidth()) {
        if(m.graphKind!="surface")GraphFormulas(m.graphKind,sources,selected,derivativeSelected,derivativeExpression,shadeSources,{i->m.clearGraphTangent();selected=i;if(other==selected)other=(i+1)%sources.size},{m.toggleGraphDerivative(selected)},m.displayDigits)
        if(m.graphKind!="surface" && (curves.isNotEmpty()||shadeSources.isNotEmpty()))Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
            SmallAction("−") { val cx=(m.xMin+m.xMax)/2;val cy=(m.yMin+m.yMax)/2;val halfX=(m.xMax-m.xMin);val halfY=(m.yMax-m.yMin);m.xMin=cx-halfX;m.xMax=cx+halfX;m.yMin=cy-halfY;m.yMax=cy+halfY }
            SmallAction("+") { val cx=(m.xMin+m.xMax)/2;val cy=(m.yMin+m.yMax)/2;val halfX=(m.xMax-m.xMin)/4;val halfY=(m.yMax-m.yMin)/4;m.xMin=cx-halfX;m.xMax=cx+halfX;m.yMin=cy-halfY;m.yMax=cy+halfY }
            SmallAction("Fit Y") {val ys=curves.flatMap {it.filterNotNull()}.filter {it.first in m.xMin..m.xMax && it.second.isFinite()}.map {it.second};if(ys.isNotEmpty()){val lo=ys.min();val hi=ys.max();val pad=max((hi-lo)*.12,if(hi==lo)1.0 else 1e-6);m.yMin=lo-pad;m.yMax=hi+pad;m.save()} }
            SmallAction("Reset") {
                when(m.graphKind) {
                    "sequence"->{m.xMin=0.0;m.xMax=20.0;m.yMin=-2.0;m.yMax=20.0;m.parameterMin=0.0;m.parameterMax=20.0}
                    "implicit"->{m.xMin=-3.0;m.xMax=3.0;m.yMin=-3.0;m.yMax=3.0}
                    "differential"->{m.xMin=-5.0;m.xMax=5.0;m.yMin=-3.0;m.yMax=5.0;m.parameterMin=-5.0;m.parameterMax=5.0}
                    else->{m.xMin=-10.0;m.xMax=10.0;m.yMin=-5.0;m.yMax=5.0}
                }
                m.trace=null;m.save()
            }
        }
        if(m.graphKind!="surface")Text(if(m.graphBusy&&!m.graphAnimating) {if(isKorean())"그래프 계산 중…" else "Sampling locally…"} else m.trace?.let { "Trace ≈ x: ${graphDisplayNumber(it.first,m.displayDigits)}   y: ${graphDisplayNumber(it.second,m.displayDigits)}" } ?: if(isKorean()) {if(m.graphKind=="differential")"방향장 · 해를 눌러 추적 · 드래그/확대로 탐색" else "눌러 추적 · 드래그하여 이동 · 두 손가락으로 확대"} else if(m.graphKind=="differential")"Direction field · tap a solution to trace · drag/pinch to explore" else "Tap to trace · drag to pan · pinch to zoom",Modifier.padding(horizontal=14.dp,vertical=5.dp),fontSize=11.sp,color=c.muted)
        }
        if(showTable && m.graphKind!="surface") Box(Modifier.bringIntoViewRequester(tableRequester)) {GraphValueTable(curves,selected,{m.trace=it},m.graphKind,m.displayDigits)}
        if(analysis && m.graphKind in listOf("cartesian","parametric","polar")) Column(Modifier.padding(horizontal=10.dp)) {
            Text(if(isKorean())"${if(m.graphKind=="cartesian")"직교좌표 곡선" else "선택한 곡선"} 분석 · ${if(m.graphKind=="cartesian")"x" else "t"} 구간" else "Analyze ${if(m.graphKind=="cartesian")"Cartesian curves" else "the selected curve"} · ${if(m.graphKind=="cartesian")"x" else "t"} interval",fontSize=12.sp,color=c.muted)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { GraphNumberField(first,"a",m.displayDigits,Modifier.weight(1f)) {first=it};GraphNumberField(second,"b",m.displayDigits,Modifier.weight(1f)) {second=it} }
            val sliderMin=if(m.graphKind=="cartesian")m.xMin else m.parameterMin
            val sliderMax=if(m.graphKind=="cartesian")m.xMax else m.parameterMax
            val sliderSpan=sliderMax-sliderMin
            if(sliderMin.isFinite() && sliderMax.isFinite() && sliderSpan.isFinite() && sliderSpan>0.0) {
                val low=((first.toDoubleOrNull()?.takeIf(Double::isFinite) ?: sliderMin)-sliderMin).div(sliderSpan).coerceIn(0.0,1.0).toFloat()
                val high=((second.toDoubleOrNull()?.takeIf(Double::isFinite) ?: sliderMax)-sliderMin).div(sliderSpan).coerceIn(0.0,1.0).toFloat()
                CompactRangeSlider(low.coerceAtMost(high)..high.coerceAtLeast(low),{range->
                    first=(sliderMin+range.start*sliderSpan).toString()
                    second=(sliderMin+range.endInclusive*sliderSpan).toString()
                },Modifier.fillMaxWidth())
            }
            SmallAction("Use visible ${if(m.graphKind=="cartesian")"x" else "t"} range") {if(m.graphKind=="cartesian"){first=m.xMin.toString();second=m.xMax.toString()}else{first=m.parameterMin.toString();second=m.parameterMax.toString()}}
            if(m.graphKind=="cartesian"&&sources.size>1) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Intersection: selected f${selected+1} with",fontSize=12.sp,color=c.muted)
                    Spacer(Modifier.width(6.dp))
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        sources.indices.filter {it!=selected}.forEach {i->SmallAction("f${i+1}",i==other) {other=i}}
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                val actions=if(m.graphKind=="cartesian")listOf("Root","Intersection","Minimum","Maximum","Inflection","Derivative","Tangent","Integral","Arc length") else listOf("Root","Minimum","Maximum","Inflection","Derivative","Tangent","Integral","Arc length")
                actions.forEach { action->
                    val key=action.lowercase().replace(" ","")
                    val isTangent=key=="tangent"
                    SmallAction(action,active=if(isTangent&&tangentPositionOpen)true else null,shaded=isTangent&&tangentPositionOpen) {
                        focusManager.clearFocus()
                        if(isTangent&&tangentPositionOpen) {
                            tangentPositionOpen=false
                            m.clearGraphTangent()
                        } else {
                            tangentPositionOpen=isTangent
                            m.analyzeGraph(key,if(isTangent)pointAt else first,second,selected,other)
                        }
                    }
                }
            }
            if(tangentPositionOpen) {
                Text(if(isKorean())"접선 위치 (${if(m.graphKind=="cartesian")"x" else "t"})" else "Tangent position (${if(m.graphKind=="cartesian")"x" else "t"})",fontSize=12.sp,color=c.muted)
                GraphNumberField(pointAt,if(m.graphKind=="cartesian")"x" else "t",m.displayDigits,Modifier.fillMaxWidth()) {pointAt=it}
                if(sliderMin.isFinite() && sliderMax.isFinite() && sliderSpan.isFinite() && sliderSpan>0.0) {
                    val position=((pointAt.toDoubleOrNull()?.takeIf(Double::isFinite) ?: sliderMin)-sliderMin).div(sliderSpan).coerceIn(0.0,1.0).toFloat()
                    CompactSlider(position,{pointAt=(sliderMin+it*sliderSpan).toString()},Modifier.fillMaxWidth(),onValueChangeFinished={
                        m.analyzeGraph("tangent",pointAt,pointAt,selected,other)
                    })
                }
            }
            if(m.graphKind=="cartesian")SmallAction(if(isKorean())"도함수 그래프 f${(m.graphDerivativeSelected ?: selected)+1}′" else "Derivative curve f${(m.graphDerivativeSelected ?: selected)+1}′",active=if(m.graphDerivativeSelected!=null)true else null,shaded=m.graphDerivativeSelected!=null) {m.toggleGraphDerivative(selected)}
            if(m.graphAnalysisBusy)Text(if(isKorean())"분석 중…" else "Analyzing…",fontSize=12.sp,color=c.muted)
            m.graphAnalysis?.let {result->
                val name=when(result.optString("analysis")){"arclength"->"Arc length";"inflection"->"Inflection";"tangent"->"Tangent slope";"intersection"->"Intersection";"minimum"->"Minimum";"maximum"->"Maximum";"integral"->"Integral";else->result.optString("analysis").replaceFirstChar {it.uppercase()}}
                if(result.has("value"))Text("$name = ${graphDisplayNumber(result.optDouble("value"),m.displayDigits)}",fontSize=16.sp)
                else if(result.optBoolean("vertical"))Text("$name · vertical tangent",fontSize=13.sp)
                else Text("$name · ${result.optInt("count")} point(s)${if(result.optBoolean("truncated"))" · first 80 shown" else ""}",fontSize=13.sp)
                markers.forEachIndexed {i,p->SmallAction("${i+1}. (${graphDisplayNumber(p.first,m.displayDigits)}, ${graphDisplayNumber(p.second,m.displayDigits)})") {
                    m.trace=p
                    if(p.first !in m.xMin..m.xMax) {val half=(m.xMax-m.xMin)/2;m.xMin=p.first-half;m.xMax=p.first+half}
                    if(p.second !in m.yMin..m.yMax) {val half=(m.yMax-m.yMin)/2;m.yMin=p.second-half;m.yMax=p.second+half}
                } }
            }
        }
    }
    }
    if(rangeDialog) {
        var xmin by remember {mutableStateOf(m.xMin.toString())};var xmax by remember {mutableStateOf(m.xMax.toString())};var ymin by remember {mutableStateOf(m.yMin.toString())};var ymax by remember {mutableStateOf(m.yMax.toString())}
        var tmin by remember {mutableStateOf(m.parameterMin.toString())};var tmax by remember {mutableStateOf(m.parameterMax.toString())}
        val sampledMin=m.graphData?.optDouble("zMin",-1.0)?.takeIf(Double::isFinite) ?: -1.0
        val sampledMax=m.graphData?.optDouble("zMax",1.0)?.takeIf(Double::isFinite) ?: 1.0
        val sampledRange=SurfaceMesh.zRange(sampledMin,sampledMax)
        var zmin by remember {mutableStateOf((m.zMin ?: sampledRange.first).toString())}
        var zmax by remember {mutableStateOf((m.zMax ?: sampledRange.second).toString())}
        var autoZ by remember {mutableStateOf(m.zMin==null || m.zMax==null)}
        AlertDialog(onDismissRequest={rangeDialog=false},title={Text(tr("Graph range"))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            RangeAxisEditor(if(m.graphKind=="sequence")"n" else "x",xmin,xmax,m.xMin,m.xMax,m.displayDigits,{xmin=it},{xmax=it})
            RangeAxisEditor("y",ymin,ymax,m.yMin,m.yMax,m.displayDigits,{ymin=it},{ymax=it})
            if(m.graphKind=="surface") {
                SmallAction("Reset ranges") {
                    xmin="-3.0";xmax="3.0";ymin="-3.0";ymax="3.0";autoZ=true
                    m.resetSurfaceRanges()
                }
                Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(autoZ,{autoZ=it});Text(tr("Automatic z range"))}
                if(!autoZ)RangeAxisEditor("z",zmin,zmax,zmin.toDoubleOrNull() ?: -1.0,zmax.toDoubleOrNull() ?: 1.0,m.displayDigits,{zmin=it},{zmax=it})
            }
            if(m.graphKind in listOf("parametric","polar","differential"))RangeAxisEditor("t",tmin,tmax,m.parameterMin,m.parameterMax,m.displayDigits,{tmin=it},{tmax=it})
        }},confirmButton={TextButton(onClick={
            val values=listOf(xmin,xmax,ymin,ymax).map {it.toDoubleOrNull()}
            val ta=tmin.toDoubleOrNull();val tb=tmax.toDoubleOrNull()
            val za=zmin.toDoubleOrNull();val zb=zmax.toDoubleOrNull()
            val usesT=m.graphKind in listOf("parametric","polar","differential")
            val usesZ=m.graphKind=="surface"&&!autoZ
            if(values.any {it==null||!it.isFinite()} || values[0]!!>=values[1]!! || values[2]!!>=values[3]!! || (usesT&&(ta==null||tb==null||!ta.isFinite()||!tb.isFinite()||ta>=tb)) || (usesZ&&(za==null||zb==null||!za.isFinite()||!zb.isFinite()||za>=zb))) m.error="Enter finite increasing ranges" else {
                m.xMin=values[0]!!;m.xMax=values[1]!!;m.yMin=values[2]!!;m.yMax=values[3]!!
                if(m.graphKind=="surface"){m.zMin=if(autoZ)null else za;m.zMax=if(autoZ)null else zb}
                if(m.graphKind=="sequence"){m.parameterMin=values[0]!!;m.parameterMax=values[1]!!}
                else if(usesT){m.parameterMin=ta!!;m.parameterMax=tb!!}
                m.save();rangeDialog=false;m.plot()
            }
        }) {Text(tr("Apply"))} },dismissButton={TextButton(onClick={rangeDialog=false}) {Text(tr("Cancel"))} })
    }
    rangeParameter?.let { name->
        val spec=m.graphParameters[name]
        if(spec!=null) {
            var low by remember(name){mutableStateOf(spec.min.toString())}
            var high by remember(name){mutableStateOf(spec.max.toString())}
            AlertDialog(onDismissRequest={rangeParameter=null},title={Text(if(isKorean())"$name 슬라이더 범위" else "$name slider range")},text={Column{
                RangeAxisEditor(name,low,high,spec.min,spec.max,m.displayDigits,{low=it},{high=it})
            }},confirmButton={TextButton(onClick={
                val start=low.toDoubleOrNull();val end=high.toDoubleOrNull()
                if(start==null||end==null||!start.isFinite()||!end.isFinite()||start>=end||abs(start)>1e9||abs(end)>1e9)m.error="Enter finite values with minimum < maximum"
                else {m.setGraphParameterRange(name,start,end);rangeParameter=null}
            }) {Text(tr("Apply"))}},dismissButton={TextButton(onClick={rangeParameter=null}) {Text(tr("Cancel"))} })
        }
    }
}

internal fun graphEquationTree(kind:String,source:String,index:Int,displayDigits:Int?=null):JSONObject? {
    val left=when(kind) {
        "parametric"->"[x(t),y(t)]"
        "polar"->"r(t)"
        "sequence"->"u(n)"
        "surface"->"z"
        "differential"->"dy/dt"
        else->"f${index+1}(x)"
    }
    val parsed=runCatching {Parser(source).parse()}.getOrNull()
    val equation=if(parsed?.kind=="relation")source else if(kind=="implicit" || kind=="cartesian" && parsed?.nodes()?.any {it.kind=="symbol" && it.value=="y"}==true)"$source=0" else "$left=$source"
    return if(displayDigits==null)decimalFractionFormulaTree(equation) else regressionFormulaDisplayTree(equation,displayDigits)
}

private fun splitGraphFormulaParts(source:String):List<String> {
    val result=mutableListOf<String>()
    var depth=0
    var start=0
    source.forEachIndexed {index,char->
        when(char) {
            '(','[','{'->depth++
            ')',']','}'->depth--
            ','->if(depth==0) {result+=source.substring(start,index).trim();start=index+1}
        }
    }
    result+=source.substring(start).trim()
    return result.filter(String::isNotEmpty)
}

internal data class GraphShadeFormula(val expressions:List<JSONObject>,val range:Pair<JSONObject,JSONObject>?)

internal fun graphShadeFormula(source:String,displayDigits:Int?=null):GraphShadeFormula? = runCatching {
    val parts=splitGraphFormulaParts(source.removePrefix("[shade]").trim())
    val expressions=mutableListOf<JSONObject>()
    var range:Pair<JSONObject,JSONObject>?=null
    parts.forEach {part->
        val ends=part.split("..")
        if(ends.size==2 && range==null) {
            range=((if(displayDigits==null)decimalFractionFormulaTree(ends[0].trim()) else regressionFormulaDisplayTree(ends[0].trim(),displayDigits)) ?: error("Invalid shade range")) to
                ((if(displayDigits==null)decimalFractionFormulaTree(ends[1].trim()) else regressionFormulaDisplayTree(ends[1].trim(),displayDigits)) ?: error("Invalid shade range"))
        } else expressions+=(if(displayDigits==null)decimalFractionFormulaTree(part) else regressionFormulaDisplayTree(part,displayDigits)) ?: error("Invalid shade expression")
    }
    GraphShadeFormula(expressions,range).takeIf {it.expressions.isNotEmpty()}
}.getOrNull()

@Composable private fun GraphFormulas(kind:String,sources:List<String>,selected:Int,derivativeSelected:Int?,derivativeExpression:String,shadeSources:List<String>,onSelect:((Int)->Unit)?,onDerivative:()->Unit,displayDigits:Int) {
    val c=LocalInstrument.current
    val equations=remember(kind,sources,displayDigits) {sources.mapIndexed {index,source->graphEquationTree(kind,source,index,displayDigits)}}
    val derivative=remember(derivativeSelected,derivativeExpression,displayDigits) {
        if(derivativeSelected==null || derivativeExpression.isBlank())null
        else regressionFormulaDisplayTree("diff(f${derivativeSelected+1}(x),x)=$derivativeExpression",displayDigits)
    }
    val shades=remember(shadeSources,displayDigits) {shadeSources.mapNotNull {graphShadeFormula(it,displayDigits)}}
    if(equations.all {it==null} && derivative==null && shades.isEmpty())return
    CompositionLocalProvider(LocalMathMinimumSize provides 8f) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        equations.forEachIndexed {index,tree->
            if(tree!=null)Row(Modifier.background(if(index==selected)c.accent.copy(alpha=.16f) else c.scientific,RoundedCornerShape(8.dp))
                .then(if(onSelect==null)Modifier else Modifier.clickable {onSelect(index)}.semantics {contentDescription="Select curve ${index+1}"})
                .padding(horizontal=7.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                MathText(if(index==selected)"●" else "○",11f,Modifier.alignBy(MathAxis),tint=c.curves[index%c.curves.size])
                Box(Modifier.alignBy(MathAxis)){MathNode(tree,12f)}
            }
        }
        if(derivative!=null)Row(Modifier.background(c.accent.copy(alpha=.16f),RoundedCornerShape(8.dp)).clickable(onClick=onDerivative)
            .padding(horizontal=7.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            MathText("●",11f,Modifier.alignBy(MathAxis),tint=c.accent)
            Box(Modifier.alignBy(MathAxis)){MathNode(derivative,12f)}
        }
        shades.forEach {shade->
            Row(Modifier.background(c.scientific,RoundedCornerShape(8.dp)).padding(horizontal=7.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                MathText("▨",11f,Modifier.alignBy(MathAxis),tint=c.muted)
                shade.expressions.forEachIndexed {index,tree->
                    if(index>0)MathText(",",12f,Modifier.alignBy(MathAxis))
                    Box(Modifier.alignBy(MathAxis)){MathNode(tree,12f)}
                }
                shade.range?.let {(low,high)->
                    MathText("  (",12f,Modifier.alignBy(MathAxis))
                    Box(Modifier.alignBy(MathAxis)){MathNode(low,12f)}
                    MathText(" ≤ x ≤ ",12f,Modifier.alignBy(MathAxis))
                    Box(Modifier.alignBy(MathAxis)){MathNode(high,12f)}
                    MathText(")",12f,Modifier.alignBy(MathAxis))
                }
            }
        }
    }
    }
}

@Composable private fun GraphHeightToggle(halfHeight:Boolean,onToggle:()->Unit,modifier:Modifier=Modifier) {
    val c=LocalInstrument.current
    val label=if(halfHeight) {if(isKorean())"전체 높이" else "Full height"} else {if(isKorean())"높이 ½" else "Half height"}
    Box(modifier.padding(6.dp).size(36.dp).clickable(onClick=onToggle).semantics {contentDescription=label},contentAlignment=Alignment.Center) {
        Box(Modifier.size(30.dp).background(c.body.copy(alpha=.94f),RoundedCornerShape(7.dp)).border(1.dp,c.grid,RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center) {
            Canvas(Modifier.size(18.dp)) {
                val unit=size.width/18f
                val color=if(halfHeight)c.accent else c.ink
                fun at(x:Float,y:Float)=Offset(x*unit,y*unit)
                fun line(x1:Float,y1:Float,x2:Float,y2:Float)=drawLine(color,at(x1,y1),at(x2,y2),1.7f*unit)
                line(3f,2f,15f,2f);line(3f,16f,15f,16f)
                if(halfHeight) {
                    line(9f,8f,9f,5f);line(6.5f,7.5f,9f,5f);line(11.5f,7.5f,9f,5f)
                    line(9f,10f,9f,13f);line(6.5f,10.5f,9f,13f);line(11.5f,10.5f,9f,13f)
                } else {
                    line(9f,5f,9f,8f);line(6.5f,5.5f,9f,8f);line(11.5f,5.5f,9f,8f)
                    line(9f,13f,9f,10f);line(6.5f,12.5f,9f,10f);line(11.5f,12.5f,9f,10f)
                }
            }
        }
    }
}

@Composable private fun GraphParameterValueField(name:String,value:Double,displayDigits:Int,onApply:(Double)->Unit,onError:(String)->Unit) {
    val c=LocalInstrument.current
    val korean=isKorean()
    val focusManager=LocalFocusManager.current
    var draft by rememberSaveable(name) {mutableStateOf(value.toString())}
    var focused by remember {mutableStateOf(false)}
    var invalid by remember {mutableStateOf(false)}
    LaunchedEffect(value) {if(!focused){draft=value.toString();invalid=false}}
    val shape=RoundedCornerShape(6.dp)
    BasicTextField(if(focused)draft else graphDisplayNumber(draft,displayDigits),{draft=it;invalid=false},Modifier.width(82.dp).height(32.dp).padding(horizontal=3.dp)
        .background(c.display,shape).border(1.dp,if(invalid)MaterialTheme.colorScheme.error else c.grid,shape)
        .onFocusChanged {state->
            val wasFocused=focused;focused=state.isFocused
            if(wasFocused&&!focused) {
                val number=draft.trim().toDoubleOrNull()
                if(number==null||!number.isFinite()||abs(number)>1e9) {
                    invalid=true
                    onError(if(korean)"-1e9부터 1e9 사이의 유한한 값을 입력하세요." else "Enter a finite value between -1e9 and 1e9")
                } else {invalid=false;onApply(number)}
            }
        }.semantics {contentDescription=if(korean)"매개변수 값: $name" else "Parameter value: $name"},
        textStyle=MaterialTheme.typography.bodySmall.copy(fontSize=12.sp,color=c.ink),singleLine=true,cursorBrush=SolidColor(c.accent),
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focusManager.clearFocus()}),
        decorationBox={inner->Box(Modifier.fillMaxSize().padding(horizontal=5.dp),contentAlignment=Alignment.CenterStart){inner()}})
}

@Composable private fun GraphNumberField(value:String,label:String,displayDigits:Int,modifier:Modifier=Modifier,onValue:(String)->Unit) {
    val c=LocalInstrument.current
    val shape=RoundedCornerShape(7.dp)
    var focused by remember {mutableStateOf(false)}
    BasicTextField(if(focused)value else graphDisplayNumber(value,displayDigits),onValue,modifier.height(40.dp).background(c.display,shape).border(1.dp,c.grid,shape).onFocusChanged {focused=it.isFocused}.semantics {contentDescription=label},
        textStyle=MaterialTheme.typography.bodyMedium.copy(fontSize=14.sp,color=c.ink),singleLine=true,cursorBrush=SolidColor(c.accent),
        decorationBox={inner->Row(Modifier.fillMaxSize().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(label,fontSize=11.sp,color=c.muted)
            Spacer(Modifier.width(7.dp))
            Box(Modifier.weight(1f)){inner()}
        }})
}

@Composable private fun RangeAxisEditor(axis:String,minimum:String,maximum:String,initialMin:Double,initialMax:Double,displayDigits:Int,onMin:(String)->Unit,onMax:(String)->Unit) {
    val bounds=remember(axis) {
        val span=(initialMax-initialMin).takeIf {it.isFinite()&&it>0.0} ?: 2.0
        val start=initialMin-2*span;val end=initialMax+2*span
        if(start.isFinite()&&end.isFinite()&&(end-start).isFinite()&&end>start)start to end else -10.0 to 10.0
    }
    val range=bounds.second-bounds.first
    Text("$axis range",fontSize=12.sp,color=LocalInstrument.current.muted)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        GraphNumberField(minimum,"$axis minimum",displayDigits,Modifier.weight(1f)){onMin(it)}
        GraphNumberField(maximum,"$axis maximum",displayDigits,Modifier.weight(1f)){onMax(it)}
    }
    val low=(((minimum.toDoubleOrNull()?.takeIf(Double::isFinite) ?: bounds.first)-bounds.first)/range).coerceIn(0.0,1.0).toFloat()
    val high=(((maximum.toDoubleOrNull()?.takeIf(Double::isFinite) ?: bounds.second)-bounds.first)/range).coerceIn(0.0,1.0).toFloat()
    CompactRangeSlider(value=low.coerceAtMost(high)..high.coerceAtLeast(low),onValueChange={selected->
        onMin((bounds.first+selected.start*range).toString())
        onMax((bounds.first+selected.endInclusive*range).toString())
    },modifier=Modifier.fillMaxWidth())
}

@Composable private fun GraphValueTable(
    curves:List<List<Pair<Double,Double>?>>,
    selected:Int,
    onTrace:(Pair<Double,Double>)->Unit,
    kind:String,
    displayDigits:Int
) {
    val c=LocalInstrument.current
    val count=curves.maxOfOrNull { it.size } ?: 0
    val stride=max(1,ceil(count/28.0).toInt())
    Column(Modifier.fillMaxWidth().heightIn(max=190.dp).verticalScroll(rememberScrollState()).padding(horizontal=10.dp)) {
        Text(if(kind=="sequence")"Sequence table · tap a row to trace" else "Visible graph values · tap a row to trace",fontSize=12.sp,color=c.muted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=4.dp)) {
            Text(if(kind=="sequence")"n" else "x",Modifier.width(72.dp),fontSize=11.sp,color=c.accent)
            curves.indices.forEach { Text("${if(kind=="sequence")"u" else "f"}${it+1}",Modifier.width(92.dp),fontSize=11.sp,color=c.accent) }
        }
        for(index in 0 until count step stride) {
            val anchor=curves.getOrNull(selected)?.getOrNull(index)
                ?: curves.firstNotNullOfOrNull { it.getOrNull(index) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clickable(enabled=anchor!=null) { anchor?.let(onTrace) }.padding(vertical=3.dp)) {
                Text(anchor?.first?.let { graphDisplayNumber(it,displayDigits) } ?: "—",Modifier.width(72.dp),fontSize=11.sp,color=c.ink)
                curves.forEach { curve->Text(curve.getOrNull(index)?.second?.let { graphDisplayNumber(it,displayDigits) } ?: "—",Modifier.width(92.dp),fontSize=11.sp,color=c.ink) }
            }
            HorizontalDivider(color=c.grid)
        }
    }
}

@Composable private fun SurfaceAppearanceControls(m:CalculatorModel) {
    val c=LocalInstrument.current
    val korean=isKorean()
    var colorPickerOpen by rememberSaveable {mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=2.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
        listOf("#007b68","#3b70bd","#a04c75","#b17d00","#ff0000").forEach { color->
            Box(Modifier.size(28.dp).background(Color(android.graphics.Color.parseColor(color)),RoundedCornerShape(6.dp))
                .border(if(m.surfaceColor.equals(color,true))2.dp else 0.dp,c.ink,RoundedCornerShape(6.dp))
                .clickable {m.surfaceColor=color;m.save()}.semantics {contentDescription=(if(korean)"표면 색상 " else "Surface color ")+color;selected=m.surfaceColor.equals(color,true)})
        }
        TextButton(onClick={colorPickerOpen=true}) {
            Box(Modifier.size(18.dp).background(Color(android.graphics.Color.parseColor(m.surfaceColor)),RoundedCornerShape(4.dp)).border(1.dp,c.grid,RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(6.dp))
            Text(if(korean)"커스텀" else "Custom")
        }
    }
    if(colorPickerOpen)SurfaceColorPickerDialog(m.surfaceColor,onDismiss={colorPickerOpen=false},onConfirm={color->
        m.surfaceColor=color;m.save();colorPickerOpen=false
    })
    Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(if(isKorean())"격자 밀도" else "Mesh density",fontSize=11.sp,color=c.muted)
        val count=SurfaceMesh.sampleCount(m.xMin,m.xMax,m.yMin,m.yMax,m.surfaceSamples,m.surfaceAutoDensity,m.surfaceZoom.toDouble())
        CompactSlider(count.toFloat(),{m.surfaceSamples=it.roundToInt()},Modifier.weight(1f),valueRange=12f..96f,steps=83,onValueChangeFinished={m.save()},enabled=!m.surfaceAutoDensity)
        Text("$count × $count",fontSize=11.sp,color=c.muted)
        Checkbox(m.surfaceAutoDensity,{m.surfaceAutoDensity=it;m.save()})
        Text(if(korean)"자동" else "Auto",fontSize=11.sp,color=c.muted)
    }
}

@Composable private fun SurfaceGraph(m:CalculatorModel,rotation:Float,elevationDeg:Float,zoom:Float,renderMode:String,colorHex:String,modifier:Modifier=Modifier) {
    val c=LocalInstrument.current
    val mesh=remember(m.graphData) {
        val rows=m.graphData?.optJSONArray("surface")
        (0 until (rows?.length() ?: 0)).map { ri->
            val row=rows!!.optJSONArray(ri)
            (0 until (row?.length() ?: 0)).map { ci->
                val point=row?.optJSONArray(ci)
                if(point==null || point.isNull(2)) null else doubleArrayOf(point.optDouble(0),point.optDouble(1),point.optDouble(2)).takeIf {it.all(Double::isFinite)}
            }
        }
    }
    val xmin=m.xMin;val xmax=m.xMax;val ymin=m.yMin;val ymax=m.yMax
    val (zmin,zmax)=SurfaceMesh.zRange(m.zMin ?: m.graphData?.optDouble("zMin",-1.0) ?: -1.0,m.zMax ?: m.graphData?.optDouble("zMax",1.0) ?: 1.0)
    val bounds=SurfaceBounds(xmin,xmax,ymin,ymax,zmin,zmax)
    val projection=remember(bounds,rotation,elevationDeg) {SurfaceProjection(bounds,rotation.toDouble(),elevationDeg.toDouble())}
    val faces=remember(mesh,projection,renderMode) {if(renderMode=="wireframe")emptyList() else SurfaceMesh.faces(mesh,projection)}
    val surfaceColor=remember(colorHex) {runCatching {Color(android.graphics.Color.parseColor(colorHex))}.getOrDefault(Color(0xFF007B68))}
    Canvas(modifier.background(c.display).semantics { contentDescription="Three dimensional surface. Drag to rotate freely, pinch to zoom, and adjust x, y and z ranges." }) {
        if(mesh.isEmpty())return@Canvas
        val scale=min(size.width,size.height)*.34f*zoom
        fun project(point:DoubleArray):Offset {
            val p=projection.project(point)
            return Offset(size.width/2+p[0].toFloat()*scale,size.height/2+p[1].toFloat()*scale)
        }
        fun niceStep(range:Double):Double {
            if(!range.isFinite()||range<=0.0)return 1.0
            val raw=range/4;val p=10.0.pow(floor(log10(raw)));val v=raw/p
            return p*(if(v>5)10 else if(v>2)5 else if(v>1)2 else 1)
        }
        val axisPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=c.muted.toArgb();textSize=11.sp.toPx() }
        val labelPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=c.ink.toArgb();textSize=12.sp.toPx();isFakeBoldText=true }
        fun surfaceSegment(path:Path,a:DoubleArray?,b:DoubleArray?) {
            val segment=SurfaceMesh.clipSegment(a,b,bounds) ?: return
            val from=project(segment.first);val to=project(segment.second)
            path.moveTo(from.x,from.y);path.lineTo(to.x,to.y)
        }
        clipRect {
            for(face in faces) {
                val path=Path().apply {
                    face.points.forEachIndexed { index,point->val p=project(point);if(index==0)moveTo(p.x,p.y)else lineTo(p.x,p.y) };close()
                }
                val color=lerp(Color.Black,surfaceColor,((.65+.35*face.height)*face.light).toFloat())
                drawPath(path,color)
                drawPath(path,if(renderMode=="surface-wireframe")c.muted else color,style=Stroke(if(renderMode=="surface-wireframe").65.dp.toPx() else .35.dp.toPx()))
            }
            if(renderMode=="wireframe") {
            val rows=Path();val columns=Path()
            mesh.forEach {row->
                for(ci in 0 until row.lastIndex) {
                    val a=row[ci];val b=row[ci+1]
                    surfaceSegment(rows,a,b)
                }
            }
            if(mesh.isNotEmpty())for(ci in mesh.first().indices) {
                for(ri in 0 until mesh.lastIndex) {
                    val a=mesh[ri].getOrNull(ci);val b=mesh[ri+1].getOrNull(ci)
                    surfaceSegment(columns,a,b)
                }
            }
            drawPath(rows,surfaceColor.copy(alpha=.78f),style=Stroke(1.15.dp.toPx()))
            drawPath(columns,surfaceColor.copy(alpha=.78f),style=Stroke(1.dp.toPx()))
            }
            val x0=doubleArrayOf(xmin,ymin,zmin)
            val x1=doubleArrayOf(xmax,ymin,zmin)
            val y1=doubleArrayOf(xmin,ymax,zmin)
            val z1=doubleArrayOf(xmin,ymin,zmax)
            drawLine(c.muted,project(x0),project(x1),2.dp.toPx())
            drawLine(c.muted,project(x0),project(y1),2.dp.toPx())
            drawLine(c.muted,project(x0),project(z1),2.dp.toPx())
            val xStep=niceStep(xmax-xmin);val yStep=niceStep(ymax-ymin);val zStep=niceStep(zmax-zmin)
            var tick=ceil(xmin/xStep)*xStep
            var guard=0
            while(tick<=xmax+1e-12&&guard++<12) {
                val p=project(doubleArrayOf(tick,ymin,zmin))
                drawCircle(c.muted,3.dp.toPx()/2,p)
                drawContext.canvas.nativeCanvas.drawText(graphDisplayNumber(tick,m.displayDigits),p.x+3f,p.y+12.sp.toPx(),axisPaint)
                tick+=xStep
            }
            tick=ceil(ymin/yStep)*yStep;guard=0
            while(tick<=ymax+1e-12&&guard++<12) {
                val p=project(doubleArrayOf(xmin,tick,zmin))
                drawCircle(c.muted,3.dp.toPx()/2,p)
                drawContext.canvas.nativeCanvas.drawText(graphDisplayNumber(tick,m.displayDigits),p.x+3f,p.y+12.sp.toPx(),axisPaint)
                tick+=yStep
            }
            tick=ceil(zmin/zStep)*zStep;guard=0
            while(tick<=zmax+1e-12&&guard++<12) {
                val p=project(doubleArrayOf(xmin,ymin,tick))
                drawCircle(c.muted,3.dp.toPx()/2,p)
                drawContext.canvas.nativeCanvas.drawText(graphDisplayNumber(tick,m.displayDigits),p.x+4f,p.y-4f,axisPaint)
                tick+=zStep
            }
            drawContext.canvas.nativeCanvas.drawText("x",project(x1).x+6f,project(x1).y+4f,labelPaint)
            drawContext.canvas.nativeCanvas.drawText("y",project(y1).x+6f,project(y1).y+4f,labelPaint)
            drawContext.canvas.nativeCanvas.drawText("z",project(z1).x+6f,project(z1).y+4f,labelPaint)
        }
    }
}

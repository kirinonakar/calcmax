package com.example.calcmax.ui

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.math.Editor
import com.example.calcmax.math.PiAxis
import com.example.calcmax.math.GraphZoom
import com.example.calcmax.ui.theme.LocalInstrument
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable fun GraphScreen(m: CalculatorModel) {
    val c=LocalInstrument.current
    var rangeDialog by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf(false) }
    var first by rememberSaveable { mutableStateOf("0") }; var second by rememberSaveable { mutableStateOf("1") }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(m.xMin,m.xMax,m.graphKind,m.parameterMin,m.parameterMax) { delay(300);m.plot() }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(m.graphSource,{m.graphSource=it},Modifier.fillMaxWidth().padding(start=10.dp,end=10.dp,top=8.dp),label={Text(if(m.graphKind=="parametric") "One [x(t),y(t)] pair per line" else if(m.graphKind=="polar") "r(t) · radians · one curve per line" else "f(x) · one function per line · up to six")},maxLines=3)
        Column {
            Choices(listOf("cartesian","parametric","polar"),m.graphKind,{m.graphKind=it;m.graphSource=when(it) { "parametric"->"[cos(t),sin(t)]"; "polar"->"2*cos(3*t)"; else->"sin(x)\ncos(x)" }; if(it!="cartesian") {m.parameterMin=0.0;m.parameterMax=2*PI;m.xMin=-3.0;m.xMax=3.0;m.yMin=-3.0;m.yMax=3.0} })
            Row(Modifier.horizontalScroll(rememberScrollState())) { SmallAction("Plot") {m.plot()};SmallAction("Range") {rangeDialog=true};SmallAction("Analyze") {analysis=!analysis};SmallAction(if(m.radianAxis)"x: π rad" else "x: decimal"){m.radianAxis=!m.radianAxis;m.save()} }
        }
        val curves=remember(m.graphData) {
            val array=m.graphData?.optJSONArray("curves")
            (0 until (array?.length() ?: 0)).map { ci->val curve=array!!.getJSONArray(ci); (0 until curve.length()).map { k->curve.optJSONArray(k)?.let { it.getDouble(0) to it.getDouble(1) } } }
        }
        val transform=Modifier.pointerInput(Unit) {awaitEachGesture {
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
        Canvas(Modifier.fillMaxWidth().weight(1f).heightIn(min=180.dp).background(c.display).then(transform).pointerInput(curves,selected) { detectTapGestures { p ->
            val target=m.xMin+(m.xMax-m.xMin)*p.x/size.width
            val targetY=m.yMax-(m.yMax-m.yMin)*p.y/size.height
            m.trace=curves.getOrNull(selected)?.filterNotNull()?.minByOrNull { ((it.first-target)/(m.xMax-m.xMin)).pow(2)+((it.second-targetY)/(m.yMax-m.yMin)).pow(2) }
        } }.semantics { contentDescription="Graph with ${curves.size} curves. Pinch to zoom, drag to pan, tap to trace. Use Range and Analyze for accessible controls." }) {
            val xlo=m.xMin;val xhi=m.xMax
            fun px(x:Double)=((x-xlo)/(xhi-xlo)*size.width).toFloat()
            fun py(y:Double)=(size.height-(y-m.yMin)/(m.yMax-m.yMin)*size.height).toFloat()
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=c.muted.toArgb();textSize=11.sp.toPx() }
            fun step(range:Double):Double { val raw=range/7;val p=10.0.pow(floor(log10(raw)));val v=raw/p;return p*(if(v>5)10 else if(v>2)5 else if(v>1)2 else 1) }
            val sx=if(m.radianAxis)PiAxis.step(xhi-xlo) else step(xhi-xlo);val sy=step(m.yMax-m.yMin)
            clipRect {
                var x=ceil(xlo/sx)*sx
                while(x<=xhi) { drawLine(c.grid,Offset(px(x),0f),Offset(px(x),size.height));drawContext.canvas.nativeCanvas.drawText(if(m.radianAxis)PiAxis.label(x) else "%.3g".format(x),px(x)+3,size.height-8,paint);x+=sx }
                var y=ceil(m.yMin/sy)*sy
                while(y<=m.yMax) { drawLine(c.grid,Offset(0f,py(y)),Offset(size.width,py(y)));drawContext.canvas.nativeCanvas.drawText("%.3g".format(y),4f,py(y)-3,paint);y+=sy }
                drawLine(c.muted,Offset(px(0.0),0f),Offset(px(0.0),size.height),2f)
                drawLine(c.muted,Offset(0f,py(0.0)),Offset(size.width,py(0.0)),2f)
                curves.forEachIndexed { ci,points ->
                    var previous: Offset?=null
                    points.forEach { point ->
                        val current=point?.let { Offset(px(it.first),py(it.second)) }
                        if(ci==selected && point!=null && m.shadedInterval?.let {point.first in min(it.first,it.second)..max(it.first,it.second)}==true) drawLine(c.curves[ci%c.curves.size].copy(alpha=.2f),Offset(current!!.x,py(0.0)),current,3f)
                        if(current!=null && previous!=null && abs(current.y-previous!!.y)<size.height*.65f) drawLine(c.curves[ci%c.curves.size],previous!!,current,if(ci==selected)3f else 2f,pathEffect=if(ci%2==1)PathEffect.dashPathEffect(floatArrayOf(12f,5f)) else null)
                        previous=current
                    }
                }
                m.trace?.let { p -> val at=Offset(px(p.first),py(p.second));drawLine(c.muted,Offset(at.x,0f),Offset(at.x,size.height),1f);drawCircle(c.accent,6f,at) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
            curves.forEachIndexed { i,_->SmallAction("${if(i==selected)"●" else "○"} f${i+1}") {selected=i} }
            SmallAction("−") { val half=(m.xMax-m.xMin)/2;m.xMin-=half;m.xMax+=half;m.yMin*=2;m.yMax*=2 }
            SmallAction("+") { val quarter=(m.xMax-m.xMin)/4;m.xMin+=quarter;m.xMax-=quarter;m.yMin/=2;m.yMax/=2 }
            SmallAction("Reset") {m.xMin=-10.0;m.xMax=10.0;m.yMin=-5.0;m.yMax=5.0;m.trace=null}
        }
        Text(if(m.graphBusy) "Sampling locally…" else m.trace?.let { "Trace ≈ x: %.7g   y: %.7g".format(it.first,it.second) } ?: "RADIANS · tap to trace · drag to pan · pinch to zoom",Modifier.padding(horizontal=14.dp,vertical=5.dp),fontSize=11.sp,color=c.muted)
        if(analysis) Column(Modifier.heightIn(max=250.dp).verticalScroll(rememberScrollState()).padding(horizontal=10.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { Field(first,"a / x",Modifier.weight(1f)) {first=it};Field(second,"b",Modifier.weight(1f)) {second=it} }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("Root","Intersection","Minimum","Maximum","Derivative","Integral").forEach { action->SmallAction(action) {
                    val functions=m.graphSource.lines().filter {it.isNotBlank()};val f=functions.getOrElse(selected){"x"}
                    val expression=when(action) { "Root"->"nsolve($f,x,$first,$second)";"Intersection"->"nsolve(($f)-(${functions.getOrElse((selected+1)%functions.size){"0"}}),x,$first,$second)";"Minimum"->"minimum($f,x,$first,$second)";"Maximum"->"maximum($f,x,$first,$second)";"Derivative"->"nderivative($f,x,$first)";else->"nintegrate($f,x,$first,$second)" }
                    if(m.graphKind!="cartesian") m.error="Analysis currently requires a Cartesian graph" else {
                        m.shadedInterval=if(action=="Integral") first.toDoubleOrNull()?.let {a->second.toDoubleOrNull()?.let {b->a to b}} else null
                        m.edit(Editor(expression));m.calculate()
                    }
                } }
            }
            m.result?.let { Text(it.optString("exact"),fontSize=18.sp) }
            Row {SmallAction("Trace x") {first.toDoubleOrNull()?.let {x->m.trace=curves.getOrNull(selected)?.filterNotNull()?.minByOrNull {abs(it.first-x)}}};if(m.busy)SmallAction("Cancel") {m.cancel()}}
        }
    }
    if(rangeDialog) {
        var xmin by remember {mutableStateOf(m.xMin.toString())};var xmax by remember {mutableStateOf(m.xMax.toString())};var ymin by remember {mutableStateOf(m.yMin.toString())};var ymax by remember {mutableStateOf(m.yMax.toString())}
        var tmin by remember {mutableStateOf(m.parameterMin.toString())};var tmax by remember {mutableStateOf(m.parameterMax.toString())}
        AlertDialog(onDismissRequest={rangeDialog=false},title={Text("Graph range")},text={Column(Modifier.verticalScroll(rememberScrollState())) { Field(xmin,"x minimum") {xmin=it};Field(xmax,"x maximum") {xmax=it};Field(ymin,"y minimum") {ymin=it};Field(ymax,"y maximum") {ymax=it};if(m.graphKind!="cartesian"){Field(tmin,"t minimum") {tmin=it};Field(tmax,"t maximum") {tmax=it}} }},confirmButton={TextButton(onClick={
            val values=listOf(xmin,xmax,ymin,ymax).map {it.toDoubleOrNull()}
            val ta=tmin.toDoubleOrNull();val tb=tmax.toDoubleOrNull()
            if(values.any {it==null||!it.isFinite()} || values[0]!!>=values[1]!! || values[2]!!>=values[3]!! || ta==null || tb==null || !ta.isFinite() || !tb.isFinite() || ta>=tb) m.error="Enter finite increasing ranges" else {m.xMin=values[0]!!;m.xMax=values[1]!!;m.yMin=values[2]!!;m.yMax=values[3]!!;m.parameterMin=ta;m.parameterMax=tb;m.save();rangeDialog=false;m.plot()}
        }) {Text("Apply")} },dismissButton={TextButton(onClick={rangeDialog=false}) {Text("Cancel")} })
    }
}

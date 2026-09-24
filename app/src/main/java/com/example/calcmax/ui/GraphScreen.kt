package com.example.calcmax.ui

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.math.PiAxis
import com.example.calcmax.math.GraphZoom
import com.example.calcmax.ui.theme.LocalInstrument
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable fun GraphScreen(m: CalculatorModel) {
    val c=LocalInstrument.current
    var rangeDialog by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf(false) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    var surfaceRotation by rememberSaveable { mutableFloatStateOf(35f) }
    var first by rememberSaveable { mutableStateOf(m.xMin.toString()) }; var second by rememberSaveable { mutableStateOf(m.xMax.toString()) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var other by rememberSaveable { mutableIntStateOf(1) }
    LaunchedEffect(m.graphKind){selected=0;other=1}
    LaunchedEffect(m.graphSource,m.xMin,m.xMax,m.yMin,m.yMax,m.graphKind,m.parameterMin,m.parameterMax,m.sequenceInitials,m.differentialInitials,m.differentialT0) { delay(350);m.plot() }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(m.graphSource,{m.updateGraphSource(it)},Modifier.fillMaxWidth().padding(start=10.dp,end=10.dp,top=8.dp),label={Text(when(m.graphKind){"parametric"->"One [x(t),y(t)] pair per line";"polar"->"r(t) · radians · one curve per line";"sequence"->"u(n) · use u(n−1) for recurrences";"surface"->"z = f(x,y)";"differential"->"dy/dt = f(t,y)";else->"f(x) · one function per line · up to six"})},minLines=if(m.graphKind in listOf("surface","differential"))1 else 2,maxLines=4)
        Column(Modifier.fillMaxWidth().zIndex(1f).background(c.body)) {
            Row(Modifier.fillMaxWidth().zIndex(2f).padding(vertical=8.dp).horizontalScroll(rememberScrollState()).semantics { contentDescription="Graph types" },horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("cartesian" to "Cartesian","parametric" to "Parametric","polar" to "Polar","sequence" to "Sequence","surface" to "3D surface","differential" to "Diff eq").forEach {(kind,label)->
                    SmallAction(label,active=m.graphKind==kind,shaded=m.graphKind==kind) {m.changeGraphKind(kind)}
                }
            }
            if(m.graphKind=="sequence") Field(m.sequenceInitials,"Initial values at n=0 · comma separated",Modifier.fillMaxWidth()) {m.sequenceInitials=it;m.save()}
            if(m.graphKind=="differential") {
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {Field(m.differentialT0,"Initial time t₀",Modifier.weight(1f)){m.differentialT0=it;m.save()};Field(m.differentialInitials,"Initial y values · comma separated",Modifier.weight(2f)){m.differentialInitials=it;m.save()} }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                SmallAction("Plot") {m.plot()};SmallAction("Range") {rangeDialog=true}
                if(m.graphKind=="cartesian")SmallAction("Analyze",active=if(analysis)true else null,shaded=analysis) {analysis=!analysis}
                if(m.graphKind!="surface")SmallAction("Table",active=if(showTable)true else null,shaded=showTable) {showTable=!showTable}
                if(m.graphKind in listOf("cartesian","parametric","polar"))SmallAction(if(m.radianAxis)"x: π rad" else "x: decimal"){m.radianAxis=!m.radianAxis;m.save()}
            }
        }
        val curves=remember(m.graphData) {
            val array=m.graphData?.optJSONArray("curves")
            (0 until (array?.length() ?: 0)).map { ci->val curve=array!!.getJSONArray(ci); (0 until curve.length()).map { k->curve.optJSONArray(k)?.let { it.getDouble(0) to it.getDouble(1) } } }
        }
        val latestCurves by rememberUpdatedState(curves)
        val sources=m.graphSource.lines().filter {it.isNotBlank()}.take(if(m.graphKind in listOf("surface","differential"))1 else 6)
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
            SurfaceGraph(m,surfaceRotation,Modifier.fillMaxWidth().weight(1f).clipToBounds().pointerInput(m.graphKind) {
                detectDragGestures {change,dragAmount->
                    surfaceRotation=((surfaceRotation+dragAmount.x*.7f)%360f+360f)%360f
                    change.consume()
                }
            })
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Rotate",fontSize=11.sp,color=c.muted);Slider(surfaceRotation,{surfaceRotation=it},Modifier.weight(1f),valueRange=0f..360f);Text("${surfaceRotation.toInt()}°",fontSize=11.sp,color=c.muted);SmallAction("Reset"){m.xMin=-3.0;m.xMax=3.0;m.yMin=-3.0;m.yMax=3.0;m.save();m.plot()}
            }
        } else Canvas(Modifier.fillMaxWidth().weight(1f).clipToBounds().background(c.display).then(transform).pointerInput(m.graphKind,selected) { detectTapGestures { p ->
            val target=m.xMin+(m.xMax-m.xMin)*p.x/size.width
            val targetY=m.yMax-(m.yMax-m.yMin)*p.y/size.height
            m.trace=latestCurves.getOrNull(selected)?.filterNotNull()?.minByOrNull { ((it.first-target)/(m.xMax-m.xMin)).pow(2)+((it.second-targetY)/(m.yMax-m.yMin)).pow(2) }
        } }.semantics { contentDescription="Graph with ${curves.size} curves. Pinch to zoom, drag to pan, tap to trace. Use Range and Analyze for accessible controls." }) {
            val xlo=m.xMin;val xhi=m.xMax
            fun px(x:Double)=((x-xlo)/(xhi-xlo)*size.width).toFloat()
            fun py(y:Double)=(size.height-(y-m.yMin)/(m.yMax-m.yMin)*size.height).toFloat()
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=c.muted.toArgb();textSize=11.sp.toPx() }
            fun step(range:Double):Double { val raw=range/7;val p=10.0.pow(floor(log10(raw)));val v=raw/p;return p*(if(v>5)10 else if(v>2)5 else if(v>1)2 else 1) }
            val sx=if(m.radianAxis)PiAxis.step(xhi-xlo) else step(xhi-xlo);val sy=step(m.yMax-m.yMin)
            clipRect {
                val firstX=ceil(xlo/sx)*sx
                for(index in 0..64) {
                    val x=firstX+index*sx
                    if(!x.isFinite() || x>xhi)break
                    drawLine(c.grid,Offset(px(x),0f),Offset(px(x),size.height))
                    drawContext.canvas.nativeCanvas.drawText(if(m.radianAxis)PiAxis.label(x) else "%.3g".format(x),px(x)+3,size.height-8,paint)
                }
                val firstY=ceil(m.yMin/sy)*sy
                for(index in 0..64) {
                    val y=firstY+index*sy
                    if(!y.isFinite() || y>m.yMax)break
                    drawLine(c.grid,Offset(0f,py(y)),Offset(size.width,py(y)))
                    drawContext.canvas.nativeCanvas.drawText("%.3g".format(y),4f,py(y)-3,paint)
                }
                drawLine(c.muted,Offset(px(0.0),0f),Offset(px(0.0),size.height),2f)
                drawLine(c.muted,Offset(0f,py(0.0)),Offset(size.width,py(0.0)),2f)
                curves.forEachIndexed { ci,points ->
                    var previous: Offset?=null
                    points.forEach { point ->
                        val current=point?.let { Offset(px(it.first),py(it.second)) }
                        if(ci==selected && point!=null && m.shadedInterval?.let {point.first in min(it.first,it.second)..max(it.first,it.second)}==true) drawLine(c.curves[ci%c.curves.size].copy(alpha=.2f),Offset(current!!.x,py(0.0)),current,3f)
                        if(current!=null && previous!=null && abs(current.y-previous!!.y)<size.height*.65f) drawLine(c.curves[ci%c.curves.size],previous!!,current,if(ci==selected)4.dp.toPx() else 1.5.dp.toPx(),pathEffect=if(ci%2==1)PathEffect.dashPathEffect(floatArrayOf(12f,5f)) else null)
                        previous=current
                    }
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
                m.trace?.let { p -> val at=Offset(px(p.first),py(p.second));drawLine(c.muted,Offset(at.x,0f),Offset(at.x,size.height),1f);drawCircle(c.accent,6f,at) }
            }
        }
        if(m.graphKind!="surface" && curves.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
            curves.forEachIndexed { i,_->
                val prefix=when(m.graphKind){"sequence"->"u";"differential"->"y";else->"f"}
                SmallAction("${if(i==selected)"●" else "○"} $prefix${i+1}: ${sources.getOrElse(i){""}.take(14)}") {selected=i;if(other==selected)other=(i+1)%curves.size}
            }
            SmallAction("−") { val cx=(m.xMin+m.xMax)/2;val cy=(m.yMin+m.yMax)/2;val halfX=(m.xMax-m.xMin);val halfY=(m.yMax-m.yMin);m.xMin=cx-halfX;m.xMax=cx+halfX;m.yMin=cy-halfY;m.yMax=cy+halfY }
            SmallAction("+") { val cx=(m.xMin+m.xMax)/2;val cy=(m.yMin+m.yMax)/2;val halfX=(m.xMax-m.xMin)/4;val halfY=(m.yMax-m.yMin)/4;m.xMin=cx-halfX;m.xMax=cx+halfX;m.yMin=cy-halfY;m.yMax=cy+halfY }
            SmallAction("Fit Y") {val ys=curves.flatMap {it.filterNotNull()}.filter {it.first in m.xMin..m.xMax && it.second.isFinite()}.map {it.second};if(ys.isNotEmpty()){val lo=ys.min();val hi=ys.max();val pad=max((hi-lo)*.12,if(hi==lo)1.0 else 1e-6);m.yMin=lo-pad;m.yMax=hi+pad;m.save()} }
            SmallAction("Reset") {
                when(m.graphKind) {
                    "sequence"->{m.xMin=0.0;m.xMax=20.0;m.yMin=-2.0;m.yMax=20.0;m.parameterMin=0.0;m.parameterMax=20.0}
                    "differential"->{m.xMin=-5.0;m.xMax=5.0;m.yMin=-3.0;m.yMax=5.0;m.parameterMin=-5.0;m.parameterMax=5.0}
                    else->{m.xMin=-10.0;m.xMax=10.0;m.yMin=-5.0;m.yMax=5.0}
                }
                m.trace=null;m.save()
            }
        }
        if(m.graphKind!="surface")Text(if(m.graphBusy) "Sampling locally…" else m.trace?.let { "Trace ≈ x: %.7g   y: %.7g".format(it.first,it.second) } ?: if(m.graphKind=="differential")"Direction field · tap a solution to trace · drag/pinch to explore" else "Tap to trace · drag to pan · pinch to zoom",Modifier.padding(horizontal=14.dp,vertical=5.dp),fontSize=11.sp,color=c.muted)
        if(showTable && m.graphKind!="surface") GraphValueTable(curves,selected,{m.trace=it},m.graphKind)
        if(analysis && m.graphKind=="cartesian") Column(Modifier.heightIn(max=290.dp).verticalScroll(rememberScrollState()).padding(horizontal=10.dp)) {
            Text("Analyze ${if(m.graphKind=="cartesian")"Cartesian curves" else "Choose Cartesian to analyze"}",fontSize=12.sp,color=c.muted)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { Field(first,"a / x",Modifier.weight(1f)) {first=it};Field(second,"b",Modifier.weight(1f)) {second=it} }
            SmallAction("Use visible x range") {first=m.xMin.toString();second=m.xMax.toString()}
            if(sources.size>1) {
                Text("Intersection: selected f${selected+1} with",fontSize=12.sp,color=c.muted)
                Row(Modifier.horizontalScroll(rememberScrollState())) {sources.indices.filter {it!=selected}.forEach {i->SmallAction("f${i+1}",i==other) {other=i}}}
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("Root","Intersection","Minimum","Maximum","Derivative","Integral").forEach { action->SmallAction(action) {m.analyzeGraph(action.lowercase(),first,second,selected,other)} }
            }
            if(m.graphAnalysisBusy)Text("Analyzing…",fontSize=12.sp,color=c.muted)
            m.graphAnalysis?.let {result->
                val action=result.optString("analysis")
                if(result.has("value"))Text("${action.replaceFirstChar {it.uppercase()}} = %.9g".format(result.optDouble("value")),fontSize=16.sp)
                else Text("${action.replaceFirstChar {it.uppercase()}} · ${result.optInt("count")} point(s)${if(result.optBoolean("truncated"))" · first 80 shown" else ""}",fontSize=13.sp)
                markers.forEachIndexed {i,p->SmallAction("${i+1}. (%.7g, %.7g)".format(p.first,p.second)) {
                    m.trace=p
                    if(p.first !in m.xMin..m.xMax) {val half=(m.xMax-m.xMin)/2;m.xMin=p.first-half;m.xMax=p.first+half}
                    if(p.second !in m.yMin..m.yMax) {val half=(m.yMax-m.yMin)/2;m.yMin=p.second-half;m.yMax=p.second+half}
                } }
            }
            SmallAction("Trace x") {first.toDoubleOrNull()?.let {x->m.trace=curves.getOrNull(selected)?.filterNotNull()?.minByOrNull {abs(it.first-x)}}}
        }
    }
    if(rangeDialog) {
        var xmin by remember {mutableStateOf(m.xMin.toString())};var xmax by remember {mutableStateOf(m.xMax.toString())};var ymin by remember {mutableStateOf(m.yMin.toString())};var ymax by remember {mutableStateOf(m.yMax.toString())}
        var tmin by remember {mutableStateOf(m.parameterMin.toString())};var tmax by remember {mutableStateOf(m.parameterMax.toString())}
        AlertDialog(onDismissRequest={rangeDialog=false},title={Text("Graph range")},text={Column(Modifier.verticalScroll(rememberScrollState())) { Field(xmin,if(m.graphKind=="sequence")"n minimum" else "x minimum") {xmin=it};Field(xmax,if(m.graphKind=="sequence")"n maximum" else "x maximum") {xmax=it};Field(ymin,if(m.graphKind=="surface")"y domain minimum" else "y minimum") {ymin=it};Field(ymax,if(m.graphKind=="surface")"y domain maximum" else "y maximum") {ymax=it};if(m.graphKind in listOf("parametric","polar","differential")){Field(tmin,"t minimum") {tmin=it};Field(tmax,"t maximum") {tmax=it}} }},confirmButton={TextButton(onClick={
            val values=listOf(xmin,xmax,ymin,ymax).map {it.toDoubleOrNull()}
            val ta=tmin.toDoubleOrNull();val tb=tmax.toDoubleOrNull()
            val usesT=m.graphKind in listOf("parametric","polar","differential")
            if(values.any {it==null||!it.isFinite()} || values[0]!!>=values[1]!! || values[2]!!>=values[3]!! || (usesT&&(ta==null||tb==null||!ta.isFinite()||!tb.isFinite()||ta>=tb))) m.error="Enter finite increasing ranges" else {
                m.xMin=values[0]!!;m.xMax=values[1]!!;m.yMin=values[2]!!;m.yMax=values[3]!!
                if(m.graphKind=="sequence"){m.parameterMin=values[0]!!;m.parameterMax=values[1]!!}
                else if(usesT){m.parameterMin=ta!!;m.parameterMax=tb!!}
                m.save();rangeDialog=false;m.plot()
            }
        }) {Text("Apply")} },dismissButton={TextButton(onClick={rangeDialog=false}) {Text("Cancel")} })
    }
}

@Composable private fun GraphValueTable(
    curves:List<List<Pair<Double,Double>?>>,
    selected:Int,
    onTrace:(Pair<Double,Double>)->Unit,
    kind:String
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
                Text(anchor?.first?.let { "%.5g".format(it) } ?: "—",Modifier.width(72.dp),fontSize=11.sp,color=c.ink)
                curves.forEach { curve->Text(curve.getOrNull(index)?.second?.let { "%.6g".format(it) } ?: "—",Modifier.width(92.dp),fontSize=11.sp,color=c.ink) }
            }
            HorizontalDivider(color=c.grid)
        }
    }
}

@Composable private fun SurfaceGraph(m:CalculatorModel,rotation:Float,modifier:Modifier=Modifier) {
    val c=LocalInstrument.current
    val mesh=remember(m.graphData) {
        val rows=m.graphData?.optJSONArray("surface")
        (0 until (rows?.length() ?: 0)).map { ri->
            val row=rows!!.optJSONArray(ri)
            (0 until (row?.length() ?: 0)).map { ci->
                val point=row?.optJSONArray(ci)
                if(point==null || point.isNull(2)) null else doubleArrayOf(point.optDouble(0),point.optDouble(1),point.optDouble(2))
            }
        }
    }
    Canvas(modifier.background(c.display).semantics { contentDescription="Three dimensional surface. Rotate with the slider and adjust x and y ranges." }) {
        if(mesh.isEmpty())return@Canvas
        val xmin=m.xMin;val xmax=m.xMax;val ymin=m.yMin;val ymax=m.yMax
        val zmin=m.graphData?.optDouble("zMin",-1.0) ?: -1.0
        val zmax=m.graphData?.optDouble("zMax",1.0) ?: 1.0
        val zspan=(zmax-zmin).takeIf { it>1e-12 } ?: 1.0
        val theta=Math.toRadians(rotation.toDouble());val elevation=.62
        val scale=min(size.width,size.height)*.39f
        fun project(point:DoubleArray):Offset {
            val xx=2*(point[0]-(xmin+xmax)/2)/(xmax-xmin)
            val yy=2*(point[1]-(ymin+ymax)/2)/(ymax-ymin)
            val zz=2*(point[2]-zmin)/zspan-1
            val horizontal=xx*cos(theta)-yy*sin(theta)
            val depth=xx*sin(theta)+yy*cos(theta)
            val vertical=depth*sin(elevation)+zz*cos(elevation)
            return Offset(size.width/2+horizontal.toFloat()*scale,size.height/2-vertical.toFloat()*scale)
        }
        clipRect {
            mesh.forEachIndexed { ri,row->
                for(ci in 0 until row.lastIndex) {
                    val a=row[ci];val b=row[ci+1]
                    if(a!=null&&b!=null)drawLine(c.curves[ri%c.curves.size].copy(alpha=.78f),project(a),project(b),1.15.dp.toPx())
                }
            }
            if(mesh.isNotEmpty())for(ci in mesh.first().indices) {
                for(ri in 0 until mesh.lastIndex) {
                    val a=mesh[ri].getOrNull(ci);val b=mesh[ri+1].getOrNull(ci)
                    if(a!=null&&b!=null)drawLine(c.accent.copy(alpha=.62f),project(a),project(b),1.dp.toPx())
                }
            }
        }
    }
}

package com.kirinonakar.symvacas.ui

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.*
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.abs

@Composable internal fun StatisticsPlot(type:String,points:List<Pair<Double,Double>>,values:List<Double>,secondary:List<Double> = emptyList(),curve:List<Pair<Double,Double>> = emptyList(),fitLabel:String="",displayDigits:Int=10,showCorrelation:Boolean=false,correlation:Double?=null,tertiary:List<Double> = emptyList(),xDateOrigin:LocalDate?=null,xAxisLabel:String="x",yAxisLabel:String="y",fitPrefix:String="y ≈ ",fitVariables:Map<String,String> = emptyMap(),allColumns:List<Pair<String,List<Double>>>? = null,orientation:String="horizontal") {
    val c=LocalInstrument.current
    val exports=remember {GraphExportState()}
    val series=if(allColumns!=null)allColumns.mapIndexed {index,(name,observations)->Triple(name,observations,c.curves[index%c.curves.size])}.filter {it.second.isNotEmpty()} else (if(secondary.isEmpty()&&tertiary.isEmpty())listOf(Triple("",values,c.accent)) else listOf(Triple("x",values,c.accent),Triple("y",secondary,c.danger),Triple("z",tertiary,c.curves[2]))).filter {it.second.isNotEmpty()}
    val exportLabels=if(type=="Histogram")series.map {"${it.first.ifBlank {"value"}} (n=${it.second.size})" to it.third}
        else if(type=="Scatter"&&fitLabel.isNotBlank())listOf((fitPrefix+fitLabel) to c.danger) else emptyList()
    val fitEquation=remember(fitLabel,displayDigits,fitVariables) {if(fitLabel.isBlank())null else regressionFormulaDisplayTree(fitLabel,displayDigits,fitVariables)}
    val densities=remember(type,series.map {it.second}) {if(type=="Violin + points")series.map {violinDensity(it.second)} else emptyList()}
    val distribution=type in listOf("Box plot","Violin + points")
    val vertical=distribution&&orientation=="vertical"
    val swarmCache=remember(series.map {it.second},vertical) {mutableMapOf<Int,Pair<Triple<List<Double>,Double,Double>,StatisticsBeeswarm>>()}
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val chartWidth=if(vertical)maxOf(maxWidth,(series.size*70+82).dp) else maxWidth
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
    ExportableGraphCanvas(Modifier.width(chartWidth).height(if(vertical)300.dp else if(distribution)(series.size*70+70).coerceAtLeast(220).dp else 220.dp).background(c.display),c.display,exports,
        exportFooterHeight=if(exportLabels.isEmpty())0.dp else (exportLabels.size*18+6).dp,exportFooter={
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=11.sp.toPx()}
            exportLabels.forEachIndexed {index,(label,color)->paint.color=color.toArgb();drawContext.canvas.nativeCanvas.drawText(label,8.dp.toPx(),(index*18+16).dp.toPx(),paint)}
        }) {
        val left=(if(vertical)70 else 38).dp.toPx();val right=12.dp.toPx();val top=14.dp.toPx();val bottom=(if(vertical)48 else 28).dp.toPx()
        val width=size.width-left-right;val height=size.height-top-bottom
        val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=c.muted.toArgb();textSize=10.sp.toPx()}
        drawLine(c.grid,Offset(left,top+height),Offset(left+width,top+height),1.dp.toPx())
        drawLine(c.grid,Offset(left,top),Offset(left,top+height),1.dp.toPx())
        if(type=="Scatter") {
            if(points.isEmpty())return@ExportableGraphCanvas
            val range=if(curve.isEmpty())points else points+curve
            var x0=range.minOf {it.first};var x1=range.maxOf {it.first};var y0=range.minOf {it.second};var y1=range.maxOf {it.second}
            if(x0==x1){x0-=1;x1+=1};if(y0==y1){y0-=1;y1+=1}
            fun px(x:Double)=left+((x-x0)/(x1-x0)).toFloat()*width
            fun py(y:Double)=top+height-((y-y0)/(y1-y0)).toFloat()*height
            if(curve.size>1) {
                val path=androidx.compose.ui.graphics.Path()
                curve.forEachIndexed {index,point->val at=Offset(px(point.first),py(point.second));if(index==0)path.moveTo(at.x,at.y) else path.lineTo(at.x,at.y)}
                drawPath(path,c.danger,style=Stroke(2.dp.toPx()))
            }
            points.forEach {drawCircle(c.accent,4.dp.toPx(),Offset(px(it.first),py(it.second)))}
            if(xDateOrigin==null)drawContext.canvas.nativeCanvas.drawText(xAxisLabel,left+width-4,top+height+20.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText(yAxisLabel,5.dp.toPx(),top+12.dp.toPx(),text)
            val firstTick=xDateOrigin?.let {origin->runCatching {origin.plusDays(x0.roundToLong()).toString()}.getOrNull()} ?: "%.4g".format(x0)
            val lastTick=xDateOrigin?.let {origin->runCatching {origin.plusDays(x1.roundToLong()).toString()}.getOrNull()} ?: "%.4g".format(x1)
            drawContext.canvas.nativeCanvas.drawText(firstTick,left,top+height+16.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText(lastTick,left+width-text.measureText(lastTick),top+height+16.dp.toPx(),text)
        } else if(series.isEmpty()) {
            drawContext.canvas.nativeCanvas.drawText("Add finite numeric observations to plot",left,top+20.dp.toPx(),text)
        } else if(type=="Histogram") {
            val all=series.flatMap {it.second}
            var lo=all.min();var hi=all.max();if(lo==hi){lo-=.5;hi+=.5}
            val bins=ceil(1+ln(all.size.coerceAtLeast(2).toDouble())/ln(2.0)).toInt().coerceIn(3,14)
            val counts=series.map {entry->IntArray(bins).also {buckets->entry.second.forEach {v->buckets[((v-lo)/(hi-lo)*bins).toInt().coerceIn(0,bins-1)]++}}}
            val peak=counts.maxOf {it.maxOrNull() ?: 0}.coerceAtLeast(1)
            val bar=width/bins;val lane=(bar-2).coerceAtLeast(1f)/series.size
            counts.forEachIndexed {seriesIndex,buckets->buckets.forEachIndexed {index,count->
                val h=height*count/peak
                drawRect(series[seriesIndex].third.copy(alpha=.78f),Offset(left+index*bar+1+lane*seriesIndex,top+height-h),androidx.compose.ui.geometry.Size((lane-1).coerceAtLeast(1f),h))
            }}
            drawContext.canvas.nativeCanvas.drawText("${all.size} values · $bins bins",left,top+11.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText("%.4g".format(lo),left,top+height+16.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText("%.4g".format(hi),left+width-34.dp.toPx(),top+height+16.dp.toPx(),text)
        } else if(distribution) {
            val all=series.flatMap {it.second};val scale=all.maxOf {abs(it)}.takeIf {it>0} ?: 1.0
            val lo=all.min();val hi=all.max();val span=hi/scale-lo/scale
            fun fraction(value:Double)=if(span==0.0).5f else ((value/scale-lo/scale)/span).toFloat()
            fun position(value:Double,index:Int,offset:Float=0f):Offset=if(vertical)Offset(left+width*(index+.5f)/series.size+offset,top+height*(1-fraction(value))) else Offset(left+width*fraction(value),top+height*(index+.5f)/series.size+offset)
            fun quantile(data:List<Double>,p:Double):Double {val at=(data.size-1)*p;val low=floor(at).toInt();val high=ceil(at).toInt();val f=at-low;return data[low]*(1-f)+data[high]*f}
            series.forEachIndexed {index,entry->
                val half=minOf(23.dp.toPx(),(if(vertical)width else height)/series.size*.35f)
                val density=if(type=="Violin + points")densities[index] else emptyList()
                if(type=="Box plot") {
                    val sorted=entry.second.sorted();val low=sorted.first();val q1=quantile(sorted,.25);val median=quantile(sorted,.5);val q3=quantile(sorted,.75);val high=sorted.last()
                    drawLine(c.muted,position(low,index),position(high,index),2.dp.toPx())
                    for(value in listOf(low,high))drawLine(c.muted,position(value,index,-half*.6f),position(value,index,half*.6f),2.dp.toPx())
                    val a=position(q1,index,-half);val b=position(q3,index,half)
                    val origin=Offset(minOf(a.x,b.x),minOf(a.y,b.y));val boxSize=androidx.compose.ui.geometry.Size(abs(b.x-a.x).coerceAtLeast(1f),abs(b.y-a.y).coerceAtLeast(1f))
                    drawRect(entry.third.copy(alpha=.24f),origin,boxSize);drawRect(entry.third,origin,boxSize,style=Stroke(1.5.dp.toPx()))
                    drawLine(c.danger,position(median,index,-half),position(median,index,half),2.dp.toPx())
                    val at=position(median,index,half+12.dp.toPx());val label="%.4g".format(java.util.Locale.US,median)
                    drawContext.canvas.nativeCanvas.drawText(label,if(vertical)at.x else at.x-text.measureText(label)/2,at.y+3.dp.toPx(),text)
                } else if(density.isNotEmpty()) {
                    val path=androidx.compose.ui.graphics.Path()
                    density.forEachIndexed {i,(value,fraction)->val at=position(value,index,-fraction.toFloat()*half);if(i==0)path.moveTo(at.x,at.y) else path.lineTo(at.x,at.y)}
                    density.asReversed().forEach {(value,fraction)->val at=position(value,index,fraction.toFloat()*half);path.lineTo(at.x,at.y)}
                    path.close();drawPath(path,entry.third.copy(alpha=.24f));drawPath(path,entry.third,style=Stroke(1.dp.toPx()))
                }
                if(type=="Violin + points") {
                    val axes=entry.second.map {value->val at=position(value,index);(if(vertical)at.y else at.x).toDouble()}
                    val key=Triple(axes,2.5.dp.toPx().toDouble(),half.toDouble())
                    val cached=swarmCache[index]
                    val swarm=if(cached?.first==key)cached.second else beeswarmLayout(axes,key.second,key.third).also {swarmCache[index]=key to it}
                    entry.second.forEachIndexed {i,value->
                        drawCircle(entry.third.copy(alpha=.75f),swarm.radius.toFloat(),position(value,index,swarm.offsets[i].toFloat()))
                    }
                }
                val label="${entry.first.ifBlank {"value"}} (n=${entry.second.size})"
                if(vertical) {
                    val center=left+width*(index+.5f)/series.size
                    var shown=entry.first.ifBlank {"value"};val available=width/series.size-4.dp.toPx()
                    while(shown.length>1&&text.measureText(shown)>available)shown=shown.dropLast(1)
                    if(shown!=entry.first.ifBlank {"value"})shown=shown.dropLast(1)+"…"
                    drawContext.canvas.nativeCanvas.drawText(shown,center-text.measureText(shown)/2,top+height+17.dp.toPx(),text)
                    val count="n=${entry.second.size}";drawContext.canvas.nativeCanvas.drawText(count,center-text.measureText(count)/2,top+height+32.dp.toPx(),text)
                } else drawContext.canvas.nativeCanvas.drawText(label,left,top+height*(index+.5f)/series.size-half-4.dp.toPx(),text)
            }
            for(index in 0..(if(span==0.0)0 else 4)) {
                val fraction=if(span==0.0).5 else index/4.0
                val value=(lo/scale*(1-fraction)+hi/scale*fraction)*scale
                val label="%.4g".format(java.util.Locale.US,value)
                val at=(left+fraction.toFloat()*width-text.measureText(label)/2).coerceIn(0f,(size.width-text.measureText(label)).coerceAtLeast(0f))
                drawContext.canvas.nativeCanvas.drawText(label,if(vertical)left-text.measureText(label)-6.dp.toPx() else at,if(vertical)top+height*(1-fraction.toFloat())+3.dp.toPx() else top+height+16.dp.toPx(),text)
            }
        }
    }
    }
    }
    PlotExportActions(exports,"symvacas-statistics-plot")
    if(type=="Box plot")series.forEach {entry->
        val summary=remember(entry.second) {
            val sorted=entry.second.sorted()
            fun q(p:Double):Double {val at=(sorted.size-1)*p;val low=floor(at).toInt();val high=ceil(at).toInt();val f=at-low;return sorted[low]*(1-f)+sorted[high]*f}
            "min %.4g   Q1 %.4g   median %.4g   Q3 %.4g   max %.4g".format(java.util.Locale.US,sorted.first(),q(.25),q(.5),q(.75),sorted.last())
        }
        Text(entry.first.takeIf {it.isNotBlank()}?.let {"$it  $summary"} ?: summary,Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),fontSize=10.sp,color=entry.third,maxLines=1)
    }
    if(type=="Histogram"&&series.isNotEmpty())Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        series.forEach {entry->Text("${entry.first.ifBlank {"value"}} (n=${entry.second.size})",fontSize=11.sp,color=entry.third,maxLines=1)}
    }
    if(type=="Scatter" && fitEquation!=null)CompositionLocalProvider(LocalMathMinimumSize provides 8f) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            MathText("●",11f,Modifier.alignBy(MathAxis),tint=c.danger)
            MathText(fitPrefix,12f,Modifier.alignBy(MathAxis))
            Box(Modifier.alignBy(MathAxis)){MathNode(fitEquation,12f)}
            if(showCorrelation) {
                MathText("    r = ",12f,Modifier.alignBy(MathAxis))
                MathText(correlation?.let {"%.3f".format(java.util.Locale.US,it)} ?: "—",12f,Modifier.alignBy(MathAxis))
            }
        }
    }
}

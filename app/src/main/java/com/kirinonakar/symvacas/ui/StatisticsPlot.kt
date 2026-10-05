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

@Composable internal fun StatisticsPlot(type:String,points:List<Pair<Double,Double>>,values:List<Double>,secondary:List<Double> = emptyList(),curve:List<Pair<Double,Double>> = emptyList(),fitLabel:String="",displayDigits:Int=10,showCorrelation:Boolean=false,correlation:Double?=null,tertiary:List<Double> = emptyList(),xDateOrigin:LocalDate?=null,xAxisLabel:String="x",yAxisLabel:String="y",fitPrefix:String="y ≈ ",fitVariables:Map<String,String> = emptyMap(),allColumns:List<Pair<String,List<Double>>>? = null) {
    val c=LocalInstrument.current
    val series=if(allColumns!=null)allColumns.mapIndexed {index,(name,observations)->Triple(name,observations,c.curves[index%c.curves.size])}.filter {it.second.isNotEmpty()} else (if(secondary.isEmpty()&&tertiary.isEmpty())listOf(Triple("",values,c.accent)) else listOf(Triple("x",values,c.accent),Triple("y",secondary,c.danger),Triple("z",tertiary,c.curves[2]))).filter {it.second.isNotEmpty()}
    val fitEquation=remember(fitLabel,displayDigits,fitVariables) {if(fitLabel.isBlank())null else regressionFormulaDisplayTree(fitLabel,displayDigits,fitVariables)}
    Canvas(Modifier.fillMaxWidth().height(if(type=="Box plot")(series.size*70+70).coerceAtLeast(220).dp else 220.dp).background(c.display)) {
        val left=38.dp.toPx();val right=12.dp.toPx();val top=14.dp.toPx();val bottom=28.dp.toPx()
        val width=size.width-left-right;val height=size.height-top-bottom
        val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=c.muted.toArgb();textSize=10.sp.toPx()}
        drawLine(c.grid,Offset(left,top+height),Offset(left+width,top+height),1.dp.toPx())
        drawLine(c.grid,Offset(left,top),Offset(left,top+height),1.dp.toPx())
        if(type=="Scatter") {
            if(points.isEmpty())return@Canvas
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
        } else {
            fun quantile(data:List<Double>,p:Double):Double {val position=(data.size-1)*p;val low=floor(position).toInt();val high=ceil(position).toInt();return data[low]+(data[high]-data[low])*(position-low)}
            val all=series.flatMap {it.second}
            var minValue=all.min();var maxValue=all.max();if(minValue==maxValue){minValue-=.5;maxValue+=.5}
            fun px(value:Double)=left+((value-minValue)/(maxValue-minValue)).toFloat()*width
            series.forEachIndexed {index,entry->
                val sorted=entry.second.sorted()
                val lo=sorted.first();val q1=quantile(sorted,.25);val median=quantile(sorted,.5);val q3=quantile(sorted,.75);val hi=sorted.last()
                val single=series.size==1
                val y=if(single)top+height/2 else top+height*(index+.5f)/series.size
                val half=if(single)20.dp.toPx() else 15.dp.toPx()
                drawLine(c.muted,Offset(px(lo),y),Offset(px(hi),y),2.dp.toPx())
                drawLine(c.muted,Offset(px(lo),y-9.dp.toPx()),Offset(px(lo),y+9.dp.toPx()),2.dp.toPx())
                drawLine(c.muted,Offset(px(hi),y-9.dp.toPx()),Offset(px(hi),y+9.dp.toPx()),2.dp.toPx())
                drawRect(entry.third.copy(alpha=.24f),Offset(px(q1),y-half),androidx.compose.ui.geometry.Size((px(q3)-px(q1)).coerceAtLeast(1f),half*2))
                drawRect(entry.third,Offset(px(q1),y-half),androidx.compose.ui.geometry.Size((px(q3)-px(q1)).coerceAtLeast(1f),half*2),style=Stroke(1.5.dp.toPx()))
                drawLine(c.danger,Offset(px(median),y-half),Offset(px(median),y+half),2.dp.toPx())
                val prefix=if(entry.first.isEmpty())"" else entry.first+"  "
                val paint=if(entry.first.isEmpty())text else Paint(Paint.ANTI_ALIAS_FLAG).apply {color=entry.third.toArgb();textSize=10.sp.toPx()}
                val summary="min %.4g   Q1 %.4g   median %.4g   Q3 %.4g   max %.4g".format(lo,q1,median,q3,hi)
                drawContext.canvas.nativeCanvas.drawText(prefix+summary,left,top+(13+index*14).dp.toPx(),paint)
            }
        }
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

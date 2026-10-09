package com.kirinonakar.symvacas.ui

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.*

private fun JSONArray.numbers()=List(length()){getDouble(it)}
private fun plotNumber(value:Double)=String.format(Locale.ROOT,"%.4g",value)

@Composable internal fun StatisticsVisualizations(plots:JSONArray?) {
    for(index in 0 until (plots?.length() ?: 0))StatisticsVisualization(plots!!.getJSONObject(index))
}

@Composable private fun StatisticsVisualization(plot:JSONObject) {
    val c=LocalInstrument.current
    val title=tr(plot.getString("title"));val kind=plot.getString("kind")
    val ratios=plot.optJSONArray("ratios")?.numbers().orEmpty()
    val matrix=plot.optJSONArray("points")
    val points=List(matrix?.length() ?: 0){matrix!!.getJSONArray(it).numbers()}
    val dimensions=points.firstOrNull()?.size ?: 0
    var xAxis by remember(plot) {mutableIntStateOf(0)}
    var yAxis by remember(plot) {mutableIntStateOf(if(dimensions>1)1 else -1)}
    val names=ratios.mapIndexed {i,value->"PC${i+1} (${plotNumber(100*value)}%)"}
    val exports=remember {GraphExportState()}
    val componentLabel=tr("Component");val drawsLabel=tr("Draws")
    val xlabel=when(kind){"scree"->componentLabel;"histogram"->tr(plot.getString("statistic"));else->names.getOrElse(xAxis){""}}
    val ylabel=when(kind){"scree"->"%";"histogram"->drawsLabel;else->names.getOrElse(yAxis){""}}
    val histogramLow=if(kind=="histogram")(plot.getJSONArray("edges").numbers()+plot.getJSONArray("interval").numbers()+plot.getDouble("estimate")).minOrNull()!! else 0.0
    val histogramHigh=if(kind=="histogram")(plot.getJSONArray("edges").numbers()+plot.getJSONArray("interval").numbers()+plot.getDouble("estimate")).maxOrNull()!! else 1.0
    val histogramPadding=(histogramHigh-histogramLow)*.025
    val zeroReference=kind=="histogram"&&plot.has("groupLabels")&&histogramLow-histogramPadding<=0&&histogramHigh+histogramPadding>=0
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,fontSize=14.sp,color=c.ink)
        if(dimensions>1) {
            StatisticsSelectionTitle(tr("X axis"),translate=false)
            Choices(names,names[xAxis],{selected->val next=names.indexOf(selected);if(next==yAxis)yAxis=xAxis;xAxis=next},translate=false)
            StatisticsSelectionTitle(tr("Y axis"),translate=false)
            Choices(names,names[yAxis],{selected->val next=names.indexOf(selected);if(next==xAxis)xAxis=yAxis;yAxis=next},translate=false)
        }
        ExportableGraphCanvas(Modifier.fillMaxWidth().height(280.dp).background(c.display).semantics {contentDescription=title},c.display,exports) {
            val left=54.dp.toPx();val top=22.dp.toPx();val w=size.width-left-18.dp.toPx();val h=size.height-top-54.dp.toPx()
            val counts=plot.optJSONArray("counts")?.numbers().orEmpty()
            val edges=plot.optJSONArray("edges")?.numbers().orEmpty()
            val xy=points.map {it[xAxis] to if(yAxis<0)0.0 else it[yAxis]}
            var xmin=0.0;var xmax=1.0;var ymin=0.0;var ymax=1.0
            when(kind) {
                "scree"->{xmin=.5;xmax=ratios.size+.5;ymax=100.0}
                "histogram"->{xmin=histogramLow-histogramPadding;xmax=histogramHigh+histogramPadding;ymax=max(1.0,counts.maxOrNull() ?: 1.0)*1.1}
                "loadings"->{xmin=-1.2;xmax=1.2;ymin=-1.2;ymax=1.2}
                else->{
                    xmin=min(0.0,xy.minOfOrNull {it.first} ?: 0.0);xmax=max(0.0,xy.maxOfOrNull {it.first} ?: 0.0)
                    ymin=min(0.0,xy.minOfOrNull {it.second} ?: 0.0);ymax=max(0.0,xy.maxOfOrNull {it.second} ?: 0.0)
                    val dx=(xmax-xmin).takeIf {it>0}?.times(.14) ?: .14;val dy=(ymax-ymin).takeIf {it>0}?.times(.14) ?: .14
                    xmin-=dx;xmax+=dx;ymin-=dy;ymax+=dy
                }
            }
            fun px(v:Double)=left+((v-xmin)/(xmax-xmin)).toFloat()*w
            fun py(v:Double)=top+h-((v-ymin)/(ymax-ymin)).toFloat()*h
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=c.muted.toArgb();textSize=10.sp.toPx()}
            fun label(x:Float,y:Float,value:String,align:Paint.Align=Paint.Align.CENTER){paint.textAlign=align;drawContext.canvas.nativeCanvas.drawText(value,x,y,paint)}
            for(i in 0..4) {
                val y=ymin+(ymax-ymin)*i/4;drawLine(c.grid,Offset(left,py(y)),Offset(left+w,py(y)));label(left-5.dp.toPx(),py(y)+4.dp.toPx(),plotNumber(y),Paint.Align.RIGHT)
                if(kind!="scree"){val x=xmin+(xmax-xmin)*i/4;label(px(x),top+h+17.dp.toPx(),plotNumber(x))}
            }
            when(kind) {
                "scree"->{
                    val line=Path();var cumulative=0.0
                    ratios.forEachIndexed {i,value->
                        val x=i+1.0;drawRect(c.accent.copy(alpha=.72f),Offset(px(x-.325),py(value*100)),Size(w*.65f/ratios.size,(py(0.0)-py(value*100)).coerceAtLeast(0f)))
                        cumulative+=value
                        if(i==0)line.moveTo(px(x),py(cumulative*100)) else line.lineTo(px(x),py(cumulative*100))
                        label(px(x),top+h+17.dp.toPx(),"PC${i+1}")
                    }
                    drawPath(line,c.danger,style=Stroke(2.dp.toPx()))
                }
                "histogram"->{
                    counts.forEachIndexed {i,value->drawRect(c.accent.copy(alpha=.72f),Offset(px(edges[i]),py(value)),Size((px(edges[i+1])-px(edges[i])-1).coerceAtLeast(.5f),(py(0.0)-py(value)).coerceAtLeast(0f)))}
                    (plot.getJSONArray("interval").numbers()+plot.getDouble("estimate")).forEachIndexed {i,value->drawLine(if(i==2)c.danger else c.ink,Offset(px(value),top),Offset(px(value),top+h),1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(if(i==2)floatArrayOf(3.dp.toPx(),3.dp.toPx()) else floatArrayOf(6.dp.toPx(),4.dp.toPx())))}
                    if(zeroReference)drawLine(c.muted,Offset(px(0.0),top),Offset(px(0.0),top+h),1.dp.toPx())
                }
                else->{
                    drawLine(c.grid,Offset(px(0.0),top),Offset(px(0.0),top+h));drawLine(c.grid,Offset(left,py(0.0)),Offset(left+w,py(0.0)))
                    xy.forEachIndexed {i,(x,y)->
                        val color=if(kind=="loadings")c.curves[i%c.curves.size] else c.accent
                        val end=Offset(px(x),py(y))
                        if(kind=="loadings") {
                            val origin=Offset(px(0.0),py(0.0));drawLine(color,origin,end,2.dp.toPx())
                            val angle=atan2((end.y-origin.y).toDouble(),(end.x-origin.x).toDouble())
                            for(delta in listOf(-.45,.45))drawLine(color,end,Offset(end.x-(8.dp.toPx()*cos(angle+delta)).toFloat(),end.y-(8.dp.toPx()*sin(angle+delta)).toFloat()),2.dp.toPx())
                            label(end.x+5.dp.toPx(),end.y-6.dp.toPx(),"${i+1}",Paint.Align.LEFT)
                        }
                        drawCircle(color.copy(alpha=.8f),if(kind=="loadings")2.dp.toPx() else 3.dp.toPx(),end)
                    }
                }
            }
            label(left+w/2,size.height-8.dp.toPx(),xlabel);label(left,14.dp.toPx(),ylabel,Paint.Align.LEFT)
        }
        if(kind=="scree")Text(tr("Bars: explained variance · line: cumulative variance"),fontSize=11.sp,color=c.muted)
        if(kind=="histogram")Text("${plotNumber(plot.getDouble("level")*100)}% ${tr("credible interval")}: ${plot.getJSONArray("interval").numbers().joinToString(" – ",transform=::plotNumber)} · ${tr("estimate")}: ${plotNumber(plot.getDouble("estimate"))}",fontSize=11.sp,color=c.muted)
        if(kind=="histogram")Text(tr("Dashed: credible bounds · dotted: estimate")+if(zeroReference)" · ${tr("Solid: zero difference")}" else "",fontSize=11.sp,color=c.muted)
        plot.optJSONArray("groupLabels")?.let {labels->Text("A: ${labels.getString(0)} · B: ${labels.getString(1)} · ${tr("Difference (B − A)")}",fontSize=11.sp,color=c.muted)}
        if(kind=="loadings")plot.optJSONArray("labels")?.let {labels->Text(List(labels.length()){"${it+1}: ${labels.getString(it)}"}.joinToString(" · "),fontSize=11.sp,color=c.muted)}
        PlotExportActions(exports,"symvacas-$kind")
    }
}

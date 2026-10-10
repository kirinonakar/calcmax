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
    val series=plot.optJSONArray("series")
    if(series!=null&&series.length()>0) {
        var selected by remember(plot) {mutableIntStateOf(0)}
        val names=List(series.length()){i->val item=series.getJSONObject(i);"${tr(item.getString("label"))} (n=${item.getInt("n")})"}
        StatisticsSelectionTitle("Sample")
        Choices(names,names[selected],{selected=names.indexOf(it)},translate=false)
        val merged=JSONObject(plot.toString()).apply {remove("series");val item=series.getJSONObject(selected);item.keys().forEach {key->put(key,item.get(key))}}
        StatisticsVisualizationBody(merged)
    } else StatisticsVisualizationBody(plot)
}

@Composable private fun StatisticsVisualizationBody(plot:JSONObject) {
    val c=LocalInstrument.current
    val title=tr(plot.getString("title"));val kind=plot.getString("kind")
    if(kind in listOf("intervals","bars")){StatisticsComparisonPlot(plot);return}
    val ratios=plot.optJSONArray("ratios")?.numbers().orEmpty()
    val matrix=plot.optJSONArray("points")
    val points=List(matrix?.length() ?: 0){matrix!!.getJSONArray(it).numbers()}
    val dimensions=points.firstOrNull()?.size ?: 0
    var xAxis by remember(plot) {mutableIntStateOf(0)}
    var yAxis by remember(plot) {mutableIntStateOf(if(dimensions>1)1 else -1)}
    val featureLabel=tr("Feature")
    val names=if(kind in listOf("clusters","interaction"))List(dimensions){val name=plot.optJSONArray("features")?.optString(it) ?: "Feature ${it+1}";name.replace(Regex("^Feature (\\d+)$"),"$featureLabel $1")} else ratios.mapIndexed {i,value->"PC${i+1} (${plotNumber(100*value)}%)"}
    val exports=remember {GraphExportState()}
    val componentLabel=tr("Component");val drawsLabel=tr("Draws")
    val xlabel=when(kind){"scree"->componentLabel;"histogram"->tr(plot.getString("statistic"));"distribution"->tr("Value");"qq"->tr("Theoretical normal quantiles");else->names.getOrElse(xAxis){""}}
    val ylabel=when(kind){"scree"->"%";"histogram"->drawsLabel;"distribution"->tr("Count");"qq"->tr("Ordered sample values");else->names.getOrElse(yAxis){""}}
    val histogram=kind in listOf("histogram","distribution")
    val markers=if(kind=="histogram")plot.getJSONArray("interval").numbers()+plot.getDouble("estimate") else emptyList()
    val histogramLow=if(histogram)(plot.getJSONArray("edges").numbers()+markers).minOrNull()!! else 0.0
    val histogramHigh=if(histogram)(plot.getJSONArray("edges").numbers()+markers).maxOrNull()!! else 1.0
    val histogramPadding=(histogramHigh-histogramLow).takeIf {it>0}?.times(.025) ?: .025
    val zeroReference=kind=="histogram"&&plot.has("groupLabels")&&histogramLow-histogramPadding<=0&&histogramHigh+histogramPadding>=0
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,fontSize=14.sp,color=c.ink)
        if(dimensions>1&&kind !in listOf("qq","interaction")) {
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
                "histogram","distribution"->{xmin=histogramLow-histogramPadding;xmax=histogramHigh+histogramPadding;ymax=max(1.0,counts.maxOrNull() ?: 1.0)*1.1}
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
                if(kind !in listOf("scree","interaction")){val x=xmin+(xmax-xmin)*i/4;label(px(x),top+h+17.dp.toPx(),plotNumber(x))}
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
                "histogram","distribution"->{
                    counts.forEachIndexed {i,value->drawRect(c.accent.copy(alpha=.72f),Offset(px(edges[i]),py(value)),Size((px(edges[i+1])-px(edges[i])-1).coerceAtLeast(.5f),(py(0.0)-py(value)).coerceAtLeast(0f)))}
                    markers.forEachIndexed {i,value->drawLine(if(i==2)c.danger else c.ink,Offset(px(value),top),Offset(px(value),top+h),1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(if(i==2)floatArrayOf(3.dp.toPx(),3.dp.toPx()) else floatArrayOf(6.dp.toPx(),4.dp.toPx())))}
                    if(zeroReference)drawLine(c.muted,Offset(px(0.0),top),Offset(px(0.0),top+h),1.dp.toPx())
                }
                else->{
                    drawLine(c.grid,Offset(px(0.0),top),Offset(px(0.0),top+h));drawLine(c.grid,Offset(left,py(0.0)),Offset(left+w,py(0.0)))
                    xy.forEachIndexed {i,(x,y)->
                        val cluster=plot.optJSONArray("assignments")?.optInt(i,1) ?: 1
                        val color=if(kind=="loadings")c.curves[i%c.curves.size] else if(kind in listOf("clusters","interaction"))c.curves[(cluster-1)%c.curves.size] else c.accent
                        val end=Offset(px(x),py(y))
                        if(kind=="loadings") {
                            val origin=Offset(px(0.0),py(0.0));drawLine(color,origin,end,2.dp.toPx())
                            val angle=atan2((end.y-origin.y).toDouble(),(end.x-origin.x).toDouble())
                            for(delta in listOf(-.45,.45))drawLine(color,end,Offset(end.x-(8.dp.toPx()*cos(angle+delta)).toFloat(),end.y-(8.dp.toPx()*sin(angle+delta)).toFloat()),2.dp.toPx())
                            label(end.x+5.dp.toPx(),end.y-6.dp.toPx(),"${i+1}",Paint.Align.LEFT)
                        }
                        drawCircle(color.copy(alpha=.8f),if(kind=="loadings")2.dp.toPx() else 3.dp.toPx(),end)
                    }
                    if(kind=="interaction") {
                        val lines=plot.getJSONArray("lineGroups");val xlabels=plot.getJSONArray("xLabels")
                        for(i in 0 until xlabels.length())label(px(i+1.0),top+h+17.dp.toPx(),xlabels.getString(i))
                        for(i in 0 until lines.length()) {
                            val line=lines.getJSONArray(i);val path=Path()
                            for(j in 0 until line.length()){val point=line.getJSONArray(j);if(j==0)path.moveTo(px(point.getDouble(0)),py(point.getDouble(1))) else path.lineTo(px(point.getDouble(0)),py(point.getDouble(1)))}
                            drawPath(path,c.curves[i%c.curves.size],style=Stroke(2.dp.toPx()))
                        }
                    }
                    plot.optJSONArray("centroids")?.let {centroids->for(i in 0 until centroids.length()){
                        val row=centroids.getJSONArray(i);val x=px(row.getDouble(xAxis));val y=py(if(yAxis<0)0.0 else row.getDouble(yAxis));val r=6.dp.toPx()
                        drawLine(c.ink,Offset(x-r,y),Offset(x+r,y),2.dp.toPx());drawLine(c.ink,Offset(x,y-r),Offset(x,y+r),2.dp.toPx())
                    }}
                    plot.optJSONArray("referenceLine")?.let {line->if(line.length()==2){val a=line.getJSONArray(0);val b=line.getJSONArray(1);drawLine(c.danger,Offset(px(a.getDouble(0)),py(a.getDouble(1))),Offset(px(b.getDouble(0)),py(b.getDouble(1))),2.dp.toPx())}}
                }
            }
            label(left+w/2,size.height-8.dp.toPx(),xlabel);label(left,14.dp.toPx(),ylabel,Paint.Align.LEFT)
        }
        if(kind=="scree")Text(tr("Bars: explained variance · line: cumulative variance"),fontSize=11.sp,color=c.muted)
        if(kind=="histogram")Text("${plotNumber(plot.getDouble("level")*100)}% ${tr("credible interval")}: ${plot.getJSONArray("interval").numbers().joinToString(" – ",transform=::plotNumber)} · ${tr("estimate")}: ${plotNumber(plot.getDouble("estimate"))}",fontSize=11.sp,color=c.muted)
        if(kind=="histogram")Text(tr("Dashed: credible bounds · dotted: estimate")+if(zeroReference)" · ${tr("Solid: zero difference")}" else "",fontSize=11.sp,color=c.muted)
        plot.optJSONArray("groupLabels")?.let {labels->Text("A: ${labels.getString(0)} · B: ${labels.getString(1)} · ${tr("Difference (B − A)")}",fontSize=11.sp,color=c.muted)}
        if(kind=="loadings")plot.optJSONArray("labels")?.let {labels->Text(List(labels.length()){"${it+1}: ${labels.getString(it)}"}.joinToString(" · "),fontSize=11.sp,color=c.muted)}
        if(kind=="qq")Text(tr("Reference line passes through the first and third quartiles. Curvature or tail departures suggest non-normality; up to 200 ordered points are shown."),fontSize=11.sp,color=c.muted)
        if(kind=="interaction") {
            val labels=plot.getJSONArray("lineLabels")
            Column {for(i in 0 until labels.length())Text("● "+labels.getString(i),fontSize=11.sp,color=c.curves[i%c.curves.size])}
            Text(tr("Lines connect fitted cell means; nonparallel lines suggest interaction. Intervals are shown separately. Interpret with the interaction F test."),fontSize=11.sp,color=c.muted)
        }
        if(kind=="clusters")Text(tr("Colors: cluster membership · crosses: centroids. Axes show original feature values."),fontSize=11.sp,color=c.muted)
        if(kind=="clusters")Column {for(i in 0 until plot.getJSONArray("centroids").length())Text("● ${tr("Cluster")} ${i+1}",fontSize=11.sp,color=c.curves[i%c.curves.size])}
        PlotExportActions(exports,"symvacas-$kind")
    }
}

@Composable private fun StatisticsComparisonPlot(plot:JSONObject) {
    val c=LocalInstrument.current;val title=tr(plot.getString("title"));val intervals=plot.getString("kind")=="intervals"
    val xlabel=tr(if(intervals)"Estimate & interval" else plot.optString("ylabel"))
    val rows=plot.optJSONArray("rows");val values=plot.optJSONArray("values")?.numbers().orEmpty()
    val count=if(intervals)rows!!.length() else values.size
    val exports=remember {GraphExportState()}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,fontSize=14.sp,color=c.ink)
        ExportableGraphCanvas(Modifier.fillMaxWidth().height(if(intervals)max(220,count*34+70).dp else 280.dp).background(c.display).semantics {contentDescription=title},c.display,exports) {
            val left=(if(intervals)108 else 54).dp.toPx();val top=22.dp.toPx();val w=size.width-left-18.dp.toPx();val h=size.height-top-54.dp.toPx()
            val reference=plot.optDouble("reference",Double.NaN)
            val range=if(intervals)(0 until count).flatMap {i->val row=rows!!.getJSONArray(i);listOf(row.getDouble(1),row.getDouble(2),row.getDouble(3))}+listOfNotNull(reference.takeIf(Double::isFinite)) else values+(plot.optJSONArray("secondary")?.numbers().orEmpty())
            val lo=if(intervals)range.minOrNull() ?: 0.0 else 0.0
            val hi=if(intervals)range.maxOrNull() ?: 1.0 else plot.optDouble("maximum",(range.maxOrNull() ?: 1.0).coerceAtLeast(1e-12)*1.15)
            val pad=if(intervals)(hi-lo).takeIf {it>0}?.times(.1) ?: .1 else 0.0
            fun px(v:Double)=left+((v-lo+pad)/(hi-lo+2*pad)).toFloat()*w
            fun py(v:Double)=top+h-(v/hi).toFloat()*h
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=c.muted.toArgb();textSize=10.sp.toPx()}
            fun label(x:Float,y:Float,text:String,align:Paint.Align=Paint.Align.CENTER){paint.textAlign=align;drawContext.canvas.nativeCanvas.drawText(text,x,y,paint)}
            if(intervals){
                for(i in 0..4){val v=lo-pad+(hi-lo+2*pad)*i/4;drawLine(c.grid,Offset(px(v),top),Offset(px(v),top+h));label(px(v),top+h+17.dp.toPx(),plotNumber(v))}
                if(reference.isFinite())drawLine(c.muted,Offset(px(reference),top),Offset(px(reference),top+h),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),4.dp.toPx())))
                for(i in 0 until count){val row=rows!!.getJSONArray(i);val y=top+h*(i+1)/(count+1);val low=px(row.getDouble(2));val high=px(row.getDouble(3));val text=row.getString(0)
                    label(left-8.dp.toPx(),y+4.dp.toPx(),if(text.length>15)text.take(14)+"…" else text,Paint.Align.RIGHT)
                    drawLine(c.accent,Offset(low,y),Offset(high,y),3.dp.toPx());for(x in listOf(low,high))drawLine(c.accent,Offset(x,y-5.dp.toPx()),Offset(x,y+5.dp.toPx()),2.dp.toPx());drawCircle(c.danger,4.dp.toPx(),Offset(px(row.getDouble(1)),y))
                }
            }else {
                for(i in 0..4){val v=hi*i/4;drawLine(c.grid,Offset(left,py(v)),Offset(left+w,py(v)));label(left-5.dp.toPx(),py(v)+4.dp.toPx(),plotNumber(v),Paint.Align.RIGHT)}
                values.forEachIndexed {i,value->val x=left+w*(i+.5f)/count;drawRect(c.accent.copy(alpha=.72f),Offset(x-w*.325f/count,py(value)),Size(w*.65f/count,(py(0.0)-py(value)).coerceAtLeast(0f)));label(x,top+h+17.dp.toPx(),plot.optJSONArray("labels")?.optString(i) ?: "${i+1}")
                    plot.optJSONArray("secondary")?.let {drawCircle(c.danger,4.dp.toPx(),Offset(x,py(it.getDouble(i))))}
                }
                if(reference.isFinite())drawLine(c.muted,Offset(left,py(reference)),Offset(left+w,py(reference)),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),4.dp.toPx())))
            }
            label(left+w/2,size.height-8.dp.toPx(),xlabel)
        }
        if(plot.has("secondary"))Text(tr("Bars: adjusted p · dots: raw p · dashed line: α"),fontSize=11.sp,color=c.muted)
        PlotExportActions(exports,"symvacas-${plot.getString("kind")}")
    }
}

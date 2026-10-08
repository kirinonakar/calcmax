package com.kirinonakar.symvacas.ui

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import java.util.Locale

private const val heatMapCellWidth=76
private const val heatMapCellHeight=30
private const val heatMapMaxViewportHeight=420

private fun heatColor(value:Double,lo:Double,hi:Double):Color {
    val fraction=heatMapFraction(value,lo,hi).toFloat()
    val low=Color(59,112,189);val middle=Color(241,241,235);val high=Color(180,55,72)
    return if(fraction<=.5f)lerp(low,middle,fraction*2) else lerp(middle,high,(fraction-.5f)*2)
}

private fun DrawScope.drawHeatMapCells(data:StatisticsHeatMapData,ink:Color,muted:Color,display:Color,lo:Double,hi:Double,formatted:(Double)->String,left:Float,top:Float,cellWidth:Float,cellHeight:Float) {
    val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=ink.toArgb();textSize=11.sp.toPx()}
    fun label(value:String,x:Float,y:Float,maxWidth:Float,center:Boolean=false) {
        var shown=value
        while(shown.length>1&&text.measureText(shown)>maxWidth)shown=shown.dropLast(1)
        if(shown!=value)shown=shown.dropLast(1)+"…"
        drawContext.canvas.nativeCanvas.drawText(shown,if(center)x-text.measureText(shown)/2 else x,y,text)
    }
    if(data.clustered) {
        val rowPeak=data.rowLinks.maxOfOrNull {it.height}?.takeIf {it>0} ?: 1.0
        val columnPeak=data.columnLinks.maxOfOrNull {it.height}?.takeIf {it>0} ?: 1.0
        data.rowLinks.forEach {link->
            val a=top+(link.left.toFloat()+.5f)*cellHeight;val b=top+(link.right.toFloat()+.5f)*cellHeight
            fun x(height:Double)=78.dp.toPx()-(height/rowPeak).toFloat()*70.dp.toPx()
            drawLine(muted,Offset(x(link.leftHeight),a),Offset(x(link.height),a),1.dp.toPx())
            drawLine(muted,Offset(x(link.height),a),Offset(x(link.height),b),1.dp.toPx())
            drawLine(muted,Offset(x(link.height),b),Offset(x(link.rightHeight),b),1.dp.toPx())
        }
        data.columnLinks.forEach {link->
            val a=left+(link.left.toFloat()+.5f)*cellWidth;val b=left+(link.right.toFloat()+.5f)*cellWidth
            fun y(height:Double)=85.dp.toPx()-(height/columnPeak).toFloat()*75.dp.toPx()
            drawLine(muted,Offset(a,y(link.leftHeight)),Offset(a,y(link.height)),1.dp.toPx())
            drawLine(muted,Offset(a,y(link.height)),Offset(b,y(link.height)),1.dp.toPx())
            drawLine(muted,Offset(b,y(link.height)),Offset(b,y(link.rightHeight)),1.dp.toPx())
        }
    }
    data.columns.forEachIndexed {index,name->label(name,left+(index+.5f)*cellWidth,top-15.dp.toPx(),cellWidth-8.dp.toPx(),true)}
    data.rows.forEachIndexed {rowIndex,row->
        text.color=ink.toArgb();label(row.label,(if(data.clustered)85 else 5).dp.toPx(),top+(rowIndex+.65f)*cellHeight,78.dp.toPx())
        row.values.forEachIndexed {column,value->
            drawRect(value?.let {heatColor(it,lo,hi)} ?: display,Offset(left+column*cellWidth,top+rowIndex*cellHeight),Size(cellWidth-1.dp.toPx(),cellHeight-1.dp.toPx()))
            text.color=if(value==null)muted.toArgb() else if(heatColor(value,lo,hi).luminance()<.18f)Color.White.toArgb() else Color(23,35,44).toArgb()
            label(value?.let(formatted) ?: "—",left+(column+.5f)*cellWidth,top+(rowIndex+.65f)*cellHeight,cellWidth-8.dp.toPx(),true)
        }
    }
}

@Composable internal fun StatisticsHeatMap(data:StatisticsHeatMapData,displayDigits:Int,fitToScreen:Boolean=false) {
    val c=LocalInstrument.current
    val exports=remember {GraphExportState()}
    val finite=data.rows.flatMap {it.values}.filterNotNull()
    if(data.rows.isEmpty()||data.columns.isEmpty()||finite.isEmpty()&&!data.correlation)return
    val lo=if(data.correlation)-1.0 else finite.min();val hi=if(data.correlation)1.0 else finite.max()
    fun formatted(value:Double)=String.format(Locale.US,"%.${displayDigits.coerceIn(1,6)}g",value)
    val captionKey=if(data.correlation)when(data.correlationMethod) {
        "spearman"->"Spearman correlation · pairwise complete observations"
        "kendall"->"Kendall correlation · pairwise complete observations"
        else->"Pearson correlation · pairwise complete observations"
    } else when(data.mode) {
        "zrow"->"Row z-scores · color = z-score"
        "zcolumn"->"Column z-scores · color = z-score"
        else->"Raw values · rows × columns"
    }
    val caption=tr(captionKey)+(if(data.clustered)" · ${tr("Hierarchical clustering")}" else "")
    val exportLegend:DrawScope.()->Unit={
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=c.muted.toArgb();textSize=11.sp.toPx()}
        drawContext.canvas.nativeCanvas.drawText(caption,8.dp.toPx(),16.dp.toPx(),paint)
        val start=8.dp.toPx();val width=(size.width-16.dp.toPx()).coerceAtLeast(1f)
        for(index in 0 until 100) {
            val value=lo*(1-index/99.0)+hi*index/99.0
            drawRect(heatColor(value,lo,hi),Offset(start+index*width/100,25.dp.toPx()),Size(width/100+1,12.dp.toPx()))
        }
        drawContext.canvas.nativeCanvas.drawText(formatted(lo),start,52.dp.toPx(),paint)
        val maximum=formatted(hi)
        drawContext.canvas.nativeCanvas.drawText(maximum,start+width-paint.measureText(maximum),52.dp.toPx(),paint)
    }
    Text(caption,fontSize=11.sp,color=c.muted)
    val description=buildString {
        append(caption)
        data.rows.forEach {row->row.values.forEachIndexed {index,value->
            append("; ${row.label} · ${data.columns[index]}: ${value?.let(::formatted) ?: "—"}")
            row.counts?.let {append(" (n=${it[index]})")}
        }}
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val leftDp=if(data.clustered)170 else 90
        val topDp=if(data.clustered)120 else 40
        val designWidthDp=(if(data.clustered)180 else 100)+data.columns.size*heatMapCellWidth
        val designHeightDp=(if(data.clustered)120 else 40)+data.rows.size*heatMapCellHeight
        if(fitToScreen) {
            val viewportWidth=maxWidth.value
            val viewportHeight=if(maxHeight.value.isFinite())minOf(maxHeight.value,heatMapMaxViewportHeight.toFloat()) else heatMapMaxViewportHeight.toFloat()
            val fit=minOf(viewportWidth/designWidthDp,viewportHeight/designHeightDp,1f)
            ExportableGraphCanvas(Modifier.width((designWidthDp*fit).dp.coerceAtLeast(1.dp)).height((designHeightDp*fit).dp.coerceAtLeast(1.dp)).background(c.display).semantics {contentDescription=description},c.display,exports,60.dp,exportLegend) {
                scale(fit,fit,pivot=Offset.Zero) {
                    drawHeatMapCells(data,c.ink,c.muted,c.display,lo,hi,::formatted,leftDp.dp.toPx(),topDp.dp.toPx(),heatMapCellWidth.dp.toPx(),heatMapCellHeight.dp.toPx())
                }
            }
        } else {
            val chartWidth=maxOf(maxWidth,designWidthDp.dp)
            Box(Modifier.fillMaxWidth().heightIn(max=heatMapMaxViewportHeight.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                ExportableGraphCanvas(Modifier.width(chartWidth).height(designHeightDp.dp).background(c.display).semantics {contentDescription=description},c.display,exports,60.dp,exportLegend) {
                    val left=leftDp.dp.toPx()
                    drawHeatMapCells(data,c.ink,c.muted,c.display,lo,hi,::formatted,left,topDp.dp.toPx(),(size.width-left-10.dp.toPx())/data.columns.size,heatMapCellHeight.dp.toPx())
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(formatted(lo),fontSize=11.sp,color=c.muted)
        Canvas(Modifier.weight(1f).height(12.dp)) {
            for(index in 0 until 100) {
                val value=lo*(1-index/99.0)+hi*index/99.0
                drawRect(heatColor(value,lo,hi),Offset(index*size.width/100,0f),Size(size.width/100+1,size.height))
            }
        }
        Text(formatted(hi),fontSize=11.sp,color=c.muted)
    }
    PlotExportActions(exports,"symvacas-heatmap")
}

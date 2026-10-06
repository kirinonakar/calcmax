package com.kirinonakar.symvacas.ui

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import java.util.Locale

private fun heatColor(value:Double,lo:Double,hi:Double):Color {
    val fraction=heatMapFraction(value,lo,hi).toFloat()
    val low=Color(59,112,189);val middle=Color(241,241,235);val high=Color(180,55,72)
    return if(fraction<=.5f)lerp(low,middle,fraction*2) else lerp(middle,high,(fraction-.5f)*2)
}

@Composable internal fun StatisticsHeatMap(data:StatisticsHeatMapData,displayDigits:Int) {
    val c=LocalInstrument.current
    val finite=data.rows.flatMap {it.values}.filterNotNull()
    if(data.rows.isEmpty()||data.columns.isEmpty()||finite.isEmpty()&&!data.correlation)return
    val lo=if(data.correlation)-1.0 else finite.min();val hi=if(data.correlation)1.0 else finite.max()
    fun formatted(value:Double)=String.format(Locale.US,"%.${displayDigits.coerceIn(1,6)}g",value)
    val caption=tr(if(data.correlation)"Pearson r · pairwise complete observations" else "Rows × columns · color = value")
    Text(caption,fontSize=11.sp,color=c.muted)
    val description=buildString {
        append(caption)
        data.rows.forEach {row->row.values.forEachIndexed {index,value->
            append("; ${row.label} · ${data.columns[index]}: ${value?.let(::formatted) ?: "—"}")
            row.counts?.let {append(" (n=${it[index]})")}
        }}
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chartWidth=maxOf(maxWidth,(100+data.columns.size*76).dp)
        Box(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(chartWidth).height((40+data.rows.size*30).dp).background(c.display).semantics {contentDescription=description}) {
                val left=90.dp.toPx();val top=40.dp.toPx();val cellWidth=(size.width-left-10.dp.toPx())/data.columns.size;val cellHeight=30.dp.toPx()
                val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=c.ink.toArgb();textSize=11.sp.toPx()}
                fun label(value:String,x:Float,y:Float,maxWidth:Float,center:Boolean=false) {
                    var shown=value
                    while(shown.length>1&&text.measureText(shown)>maxWidth)shown=shown.dropLast(1)
                    if(shown!=value)shown=shown.dropLast(1)+"…"
                    drawContext.canvas.nativeCanvas.drawText(shown,if(center)x-text.measureText(shown)/2 else x,y,text)
                }
                data.columns.forEachIndexed {index,name->label(name,left+(index+.5f)*cellWidth,25.dp.toPx(),cellWidth-8.dp.toPx(),true)}
                data.rows.forEachIndexed {rowIndex,row->
                    text.color=c.ink.toArgb();label(row.label,5.dp.toPx(),top+(rowIndex+.65f)*cellHeight,left-12.dp.toPx())
                    row.values.forEachIndexed {column,value->
                        drawRect(value?.let {heatColor(it,lo,hi)} ?: c.display,Offset(left+column*cellWidth,top+rowIndex*cellHeight),Size(cellWidth-1.dp.toPx(),cellHeight-1.dp.toPx()))
                        text.color=if(value==null)c.muted.toArgb() else if(heatColor(value,lo,hi).luminance()<.18f)Color.White.toArgb() else Color(23,35,44).toArgb()
                        label(value?.let(::formatted) ?: "—",left+(column+.5f)*cellWidth,top+(rowIndex+.65f)*cellHeight,cellWidth-8.dp.toPx(),true)
                    }
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
}

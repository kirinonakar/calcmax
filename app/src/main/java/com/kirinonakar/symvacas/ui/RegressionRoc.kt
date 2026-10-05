package com.kirinonakar.symvacas.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirinonakar.symvacas.calculator.ResultDisplayMode
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import org.json.JSONObject
import kotlin.math.min

@Composable internal fun RegressionRoc(report:JSONObject,digits:Int,oob:Boolean=false) {
    val points=remember(report) {
        report.optJSONArray("roc")?.let {array->(0 until array.length()).mapNotNull {index->
            array.optJSONArray(index)?.let {point->
                val x=point.optDouble(0,Double.NaN);val y=point.optDouble(1,Double.NaN)
                if(x.isFinite()&&y.isFinite()&&x in 0.0..1.0&&y in 0.0..1.0)x to y else null
            }
        }}.orEmpty()
    }
    if(points.size<2)return
    val colors=LocalInstrument.current
    val auc=ResultDisplayFormat.formatText(report.optString("auc"),ResultDisplayMode.OFF,false,maxFractionDigits=digits)
    val title="${tr(if(oob)"OOB ROC curve" else "ROC curve")} · AUC=$auc"
    val xLabel=tr("False positive rate (FPR)");val yLabel=tr("Sensitivity (TPR)")
    Text(title,fontSize=12.sp)
    Text(tr(if(oob)"ROC/AUC uses OOB predictions; positive class = 1." else "ROC/AUC uses fitted data; positive class = 1."),fontSize=11.sp,color=colors.muted)
    Canvas(Modifier.widthIn(max=360.dp).fillMaxWidth().aspectRatio(1f).testTag(if(oob)"statistics-oob-roc" else "statistics-roc").semantics {contentDescription="$title · $xLabel · $yLabel"}) {
        val left=40.dp.toPx();val top=12.dp.toPx()
        val side=min(size.width-left-12.dp.toPx(),size.height-top-40.dp.toPx()).coerceAtLeast(1f)
        fun x(value:Double)=left+side*value.toFloat()
        fun y(value:Double)=top+side*(1-value.toFloat())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=colors.muted.toArgb();textSize=10.sp.toPx()}
        listOf(0.0,.25,.5,.75,1.0).forEach {value->
            drawLine(colors.grid,Offset(x(value),y(0.0)),Offset(x(value),y(1.0)))
            drawLine(colors.grid,Offset(x(0.0),y(value)),Offset(x(1.0),y(value)))
            val label=value.toString().removeSuffix(".0")
            drawContext.canvas.nativeCanvas.drawText(label,x(value)-paint.measureText(label)/2,y(0.0)+15.dp.toPx(),paint)
            drawContext.canvas.nativeCanvas.drawText(label,left-6.dp.toPx()-paint.measureText(label),y(value)+3.dp.toPx(),paint)
        }
        drawLine(colors.muted,Offset(x(0.0),y(0.0)),Offset(x(1.0),y(1.0)),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),5.dp.toPx())))
        val path=Path()
        points.forEachIndexed {index,point->if(index==0)path.moveTo(x(point.first),y(point.second)) else path.lineTo(x(point.first),y(point.second))}
        drawPath(path,colors.accent,style=Stroke(2.dp.toPx()))
        drawContext.canvas.nativeCanvas.drawText(xLabel,left+side/2-paint.measureText(xLabel)/2,y(0.0)+34.dp.toPx(),paint)
        val native=drawContext.canvas.nativeCanvas
        native.save();native.rotate(-90f,12.dp.toPx(),top+side/2)
        native.drawText(yLabel,12.dp.toPx()-paint.measureText(yLabel)/2,top+side/2,paint);native.restore()
    }
}

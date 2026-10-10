package com.kirinonakar.symvacas.ui

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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

@Composable internal fun StatisticsSemDiagram(plot:JSONObject) {
    val c=LocalInstrument.current;val title=tr(plot.getString("title"));val factor=tr("Factor");val feature=tr("Feature");val exogenous=tr("Exogenous")
    val exports=remember {GraphExportState()}
    var fitToScreen by remember {mutableStateOf(false)}
    val screenHeight=LocalConfiguration.current.screenHeightDp
    val legend=tr("Ellipses: latent factors · rectangles: indicators · arrows: standardized coefficients [95% CI] · dashed double arrows: exogenous correlations.")
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,fontSize=14.sp,color=c.ink)
        TextButton(onClick={fitToScreen=!fitToScreen}) {Text(tr(if(fitToScreen)"Original size" else "Fit diagram to screen"))}
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val designWidth=plot.getDouble("width").toFloat();val designHeight=plot.getDouble("height").toFloat()
            val viewportHeight=minOf(if(maxHeight.value.isFinite())maxHeight.value else Float.POSITIVE_INFINITY,screenHeight*.65f)
            val fit=if(fitToScreen)minOf(1f,maxWidth.value/designWidth,viewportHeight/designHeight) else 1f
            Box(Modifier.fillMaxWidth().then(if(fitToScreen)Modifier else Modifier.horizontalScroll(rememberScrollState()))) {
            ExportableGraphCanvas(Modifier.width((designWidth*fit).dp).height((designHeight*fit).dp).background(c.display).semantics {contentDescription=title},c.display,exports) {
                val scale=1.dp.toPx()*fit
                fun point(array:JSONArray)=Offset(array.getDouble(0).toFloat()*scale,array.getDouble(1).toFloat()*scale)
                fun num(value:Double)=String.format(Locale.ROOT,"%.3g",value)
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {textAlign=Paint.Align.CENTER}
                fun label(at:Offset,text:String,muted:Boolean=false,small:Boolean=false) {
                    paint.color=(if(muted)c.muted else c.ink).toArgb();paint.textSize=(if(small)11 else 12).sp.toPx()*fit
                    drawContext.canvas.nativeCanvas.drawText(text,at.x,at.y,paint)
                }
                fun arrow(tip:Offset,control:Offset,color:androidx.compose.ui.graphics.Color) {
                    val angle=atan2(tip.y-control.y,tip.x-control.x)
                    val path=Path().apply {moveTo(tip.x,tip.y);lineTo(tip.x-9*scale*cos(angle-.4f),tip.y-9*scale*sin(angle-.4f));lineTo(tip.x-9*scale*cos(angle+.4f),tip.y-9*scale*sin(angle+.4f));close()}
                    drawPath(path,color)
                }
                val edges=plot.getJSONArray("edges")
                for(i in 0 until edges.length()) {
                    val edge=edges.getJSONObject(i);val start=point(edge.getJSONArray("start"));val end=point(edge.getJSONArray("end"));val controls=edge.getJSONArray("controls")
                    val first=point(controls.getJSONArray(0));val second=point(controls.getJSONArray(1))
                    val covariance=edge.getString("kind")=="covariance";val color=if(edge.getString("kind")=="loading")c.accent else c.ink
                    val path=Path().apply {moveTo(start.x,start.y);cubicTo(first.x,first.y,second.x,second.y,end.x,end.y)}
                    drawPath(path,color,style=Stroke(1.8f*scale,pathEffect=if(covariance)PathEffect.dashPathEffect(floatArrayOf(5*scale,3*scale)) else null))
                    arrow(end,second,color);if(covariance)arrow(start,first,color)
                }
                for(i in 0 until edges.length()) {
                    val edge=edges.getJSONObject(i);val at=point(edge.getJSONArray("labelPosition"));val interval=edge.optJSONArray("interval")
                    drawRect(c.display,at-Offset(59*scale,22*scale),Size(118*scale,(if(interval==null)24 else 38)*scale))
                    label(at-Offset(0f,6*scale),num(edge.getDouble("estimate")))
                    if(interval!=null)label(at+Offset(0f,10*scale),"[${num(interval.getDouble(0))}, ${num(interval.getDouble(1))}]",muted=true,small=true)
                }
                val nodes=plot.getJSONArray("nodes")
                for(i in 0 until nodes.length()) {
                    val node=nodes.getJSONObject(i);val at=Offset(node.getDouble("x").toFloat()*scale,node.getDouble("y").toFloat()*scale);val latent=node.getString("kind")=="latent"
                    if(latent) {
                        val corner=at-Offset(70*scale,37*scale);val size=Size(140*scale,74*scale)
                        drawOval(c.display,corner,size);drawOval(c.ink,corner,size,style=Stroke(1.6f*scale))
                    } else {
                        val corner=at-Offset(70*scale,25*scale);val size=Size(140*scale,50*scale)
                        drawRect(c.display,corner,size);drawRect(c.ink,corner,size,style=Stroke(1.6f*scale))
                    }
                    val name=node.getString("label").replace(Regex("^Factor "),"$factor ").replace(Regex("^Feature "),"$feature ")
                    val r2=node.optDouble("r2",Double.NaN)
                    label(at+Offset(0f,(if(latent||r2.isFinite())-6 else 4)*scale),if(name.length>18)name.take(17)+"…" else name)
                    if(latent||r2.isFinite())label(at+Offset(0f,13*scale),if(r2.isFinite())"R² = ${num(r2)}" else exogenous,muted=true,small=true)
                }
            }
            }
        }
        Text(legend,fontSize=11.sp,color=c.muted)
        PlotExportActions(exports,"symvacas-sem-diagram")
    }
}

package com.kirinonakar.symvacas.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Picture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.sqrt

/** Capture on the UI thread before opening the picker; write the snapshot on IO. */
internal class GraphExportState {
    var capture:((String)->GraphExportImage)?=null
}

internal class GraphExportImage(private val svg:String?,private val picture:Picture?,private val width:Int,private val height:Int) {
    fun writeTo(output:OutputStream) {
        if(svg!=null)output.write(svg.toByteArray(Charsets.UTF_8))
        else {
            val ratio=minOf(1.0,16384.0/width,16384.0/height,sqrt(16_000_000.0/(width.toDouble()*height)))
            val bitmap=Bitmap.createBitmap((width*ratio).toInt().coerceAtLeast(1),(height*ratio).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
            try {
                val canvas=android.graphics.Canvas(bitmap);canvas.scale(ratio.toFloat(),ratio.toFloat())
                picture!!.draw(canvas)
                check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)){"Could not encode the graph"}
            } finally {bitmap.recycle()}
        }
    }
}

@Composable internal fun ExportableGraphCanvas(modifier:Modifier,background:Color,exports:GraphExportState,exportFooterHeight:Dp=0.dp,exportFooter:(DrawScope.()->Unit)?=null,onDraw:DrawScope.()->Unit) {
    Canvas(modifier) {
        val density=Density(this.density,fontScale)
        val direction=layoutDirection
        val dimensions=size
        exports.capture={format->
            val width=dimensions.width.toInt().coerceAtLeast(1)
            val chartHeight=dimensions.height.toInt().coerceAtLeast(1)
            val footerHeight=with(density){exportFooterHeight.toPx()}.toInt()
            val height=chartHeight+footerHeight
            fun render(canvas:android.graphics.Canvas) {
                CanvasDrawScope().draw(density,direction,androidx.compose.ui.graphics.Canvas(canvas),Size(width.toFloat(),chartHeight.toFloat())) {
                    drawRect(background)
                    onDraw()
                }
                if(footerHeight>0&&exportFooter!=null) {
                    canvas.save();canvas.translate(0f,chartHeight.toFloat())
                    try {CanvasDrawScope().draw(density,direction,androidx.compose.ui.graphics.Canvas(canvas),Size(width.toFloat(),footerHeight.toFloat())) {
                        drawRect(background);exportFooter()
                    }} finally {canvas.restore()}
                }
            }
            if(format=="svg") {
                val canvas=GraphSvgCanvas(width,height)
                render(canvas)
                GraphExportImage(canvas.document(),null,width,height)
            } else {
                val picture=Picture()
                try {render(picture.beginRecording(width,height))} finally {picture.endRecording()}
                GraphExportImage(null,picture,width,height)
            }
        }
        onDraw()
    }
}

@Composable internal fun PlotExportActions(exports:GraphExportState,name:String) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var image by remember {mutableStateOf<GraphExportImage?>(null)}
    var busy by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    fun save(uri:android.net.Uri?) {
        val snapshot=image;image=null
        if(uri==null||snapshot==null){busy=false;return}
        scope.launch {
            try {
                withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")?.use(snapshot::writeTo) ?: error("Could not open the selected file for writing")}
                message="Graph saved"
            } catch(e:Exception) {error=e.message.orEmpty()}
            finally {busy=false}
        }
    }
    val svg=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml"),::save)
    val png=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"),::save)
    fun capture(format:String) {
        try {
            image=exports.capture?.invoke(format) ?: return
            busy=true;message="";error=""
            if(format=="svg")svg.launch("$name.svg")else png.launch("$name.png")
        } catch(e:Exception) {busy=false;image=null;error=e.message.orEmpty()}
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
        SmallAction("Save SVG",enabled=!busy){capture("svg")}
        SmallAction("Save PNG",enabled=!busy){capture("png")}
    }
    if(message.isNotBlank())Text(tr(message),style=MaterialTheme.typography.bodySmall)
    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
}

/** The graph uses paths, lines, circles and native text. Record those same calls
 * as vectors. Paint.getFillPath preserves dashed strokes, caps and joins. */
internal fun drawGraphGradientPath(canvas:android.graphics.Canvas,path:android.graphics.Path,start:Offset,end:Offset,low:Int,high:Int,edgeWidth:Float) {
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=low
        style=if(edgeWidth>0)Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
        strokeWidth=edgeWidth
        strokeJoin=Paint.Join.ROUND
    }
    if(canvas is GraphSvgCanvas)canvas.drawGradientPath(path,paint,start,end,low,high)
    else {
        paint.shader=android.graphics.LinearGradient(start.x,start.y,end.x,end.y,low,high,android.graphics.Shader.TileMode.CLAMP)
        canvas.drawPath(path,paint)
    }
}

private class GraphSvgCanvas(private val width:Int,private val height:Int):android.graphics.Canvas() {
    private val elements=StringBuilder()
    private val gradients=StringBuilder()
    private var gradientCount=0
    private var gradientFill:String?=null
    fun drawGradientPath(path:android.graphics.Path,paint:Paint,start:Offset,end:Offset,low:Int,high:Int) {
        val id="surface-gradient-${gradientCount++}"
        fun rgb(value:Int)="#"+Integer.toHexString(value and 0xffffff).padStart(6,'0')
        gradients.append("<linearGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" x1=\"${start.x}\" y1=\"${start.y}\" x2=\"${end.x}\" y2=\"${end.y}\"><stop offset=\"0\" stop-color=\"${rgb(low)}\"/><stop offset=\"1\" stop-color=\"${rgb(high)}\"/></linearGradient>")
        gradientFill="url(#$id)"
        try {drawPath(path,paint)} finally {gradientFill=null}
    }
    private fun escape(text:String)=text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun color(paint:Paint)="#"+Integer.toHexString(paint.color and 0xffffff).padStart(6,'0')
    private fun opacity(paint:Paint)=paint.alpha/255f
    @Suppress("DEPRECATION")
    private fun transform():String {
        val values=FloatArray(9);val matrix=android.graphics.Matrix();getMatrix(matrix);matrix.getValues(values)
        return "matrix(${values[0]} ${values[3]} ${values[1]} ${values[4]} ${values[2]} ${values[5]})"
    }
    override fun drawPath(path:android.graphics.Path,paint:Paint) {
        val geometry=android.graphics.Path()
        val filled=paint.getFillPath(path,geometry)
        val points=geometry.approximate(.25f)
        if(points.isEmpty())return
        val data=StringBuilder()
        var previousFraction=-1f
        for(i in points.indices step 3) {
            val fraction=points[i];val x=points[i+1];val y=points[i+2]
            if(!x.isFinite()||!y.isFinite())continue
            data.append(if(i==0||fraction==previousFraction)"M" else "L").append(x).append(' ').append(y).append(' ')
            previousFraction=fraction
        }
        val style=if(filled)"fill=\"${gradientFill ?: color(paint)}\"" else "fill=\"none\" stroke=\"${color(paint)}\" stroke-width=\"1\""
        val rule=if(geometry.fillType==android.graphics.Path.FillType.EVEN_ODD)"evenodd" else "nonzero"
        elements.append("<path d=\"$data\" $style fill-rule=\"$rule\" opacity=\"${opacity(paint)}\" transform=\"${transform()}\"/>")
    }
    override fun drawLine(startX:Float,startY:Float,stopX:Float,stopY:Float,paint:Paint) {
        drawPath(android.graphics.Path().apply {moveTo(startX,startY);lineTo(stopX,stopY)},paint)
    }
    override fun drawRect(left:Float,top:Float,right:Float,bottom:Float,paint:Paint) {
        drawPath(android.graphics.Path().apply {addRect(left,top,right,bottom,android.graphics.Path.Direction.CW)},paint)
    }
    override fun drawCircle(cx:Float,cy:Float,radius:Float,paint:Paint) {
        drawPath(android.graphics.Path().apply {addCircle(cx,cy,radius,android.graphics.Path.Direction.CW)},paint)
    }
    override fun drawText(text:String,x:Float,y:Float,paint:Paint) {
        val anchor=when(paint.textAlign){Paint.Align.CENTER->"middle";Paint.Align.RIGHT->"end";else->"start"}
        elements.append("<text x=\"$x\" y=\"$y\" fill=\"${color(paint)}\" opacity=\"${opacity(paint)}\" font-family=\"sans-serif\" font-size=\"${paint.textSize}\" text-anchor=\"$anchor\" font-weight=\"${if(paint.isFakeBoldText)"bold" else "normal"}\" transform=\"${transform()}\">${escape(text)}</text>")
    }
    fun document()="<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\"><defs><clipPath id=\"viewport\"><rect width=\"$width\" height=\"$height\"/></clipPath>$gradients</defs><g clip-path=\"url(#viewport)\">$elements</g></svg>"
}

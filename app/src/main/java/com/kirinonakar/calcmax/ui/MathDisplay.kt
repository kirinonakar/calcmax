package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

val MathAxis=HorizontalAlignmentLine(::minOf)
val LocalMathCursorTarget=staticCompositionLocalOf<IntRange?>{null}
val LocalMathAfter=staticCompositionLocalOf<((Int,Int)->Unit)?>{null}
val LocalCaretVisible=staticCompositionLocalOf{true}
val LocalActiveToken=staticCompositionLocalOf<IntRange?>{null}
val LocalPlaceCursor=staticCompositionLocalOf<((Int,Int,Int)->Unit)?>{null}
val LocalTypedParens=staticCompositionLocalOf<List<IntRange>>{emptyList()}

private fun Placeable.axis():Int = this[MathAxis].let{if(it==AlignmentLine.Unspecified)height/2 else it}
@Composable internal fun MathText(text:String,size:Float,modifier:Modifier=Modifier,blink:Boolean=false,onLayout:(TextLayoutResult)->Unit={},hide:Boolean=false,tint:Color?=null) {
    val color=tint ?: LocalInstrument.current.ink
    val visible=!hide&&(!blink||LocalCaretVisible.current)
    val styled=buildAnnotatedString {append(text);if(!visible)text.forEachIndexed{i,ch->if(ch=='│')addStyle(SpanStyle(color=Color.Transparent),i,i+1)}}
    Text(styled,fontFamily=FontFamily.Serif,fontSize=size.sp,lineHeight=(size*1.18f).sp,color=color,softWrap=false,onTextLayout=onLayout,
        modifier=modifier.layout {measurable,constraints->
            val p=measurable.measure(constraints);val baseline=p[FirstBaseline]
            val axis=if(baseline==AlignmentLine.Unspecified)p.height/2 else baseline-(size.sp.toPx()*.3f).toInt()
            layout(p.width,p.height,mapOf(MathAxis to axis)){p.place(0,0)}
        })
}

/** Every sibling aligns to a mathematical axis, not the center of its bounding rectangle. */
@Composable private fun MathRow(gap:Dp=0.dp,content:@Composable ()->Unit) {
    Layout(content=content){measurables,constraints->
        val children=measurables.map{it.measure(constraints.copy(minWidth=0,minHeight=0))}
        val axis=children.maxOfOrNull{it.axis()} ?: 0
        val below=children.maxOfOrNull{it.height-it.axis()} ?: 0
        val spacing=gap.roundToPx()
        val width=children.sumOf{it.width}+spacing*(children.size-1).coerceAtLeast(0)
        layout(width,axis+below,mapOf(MathAxis to axis)){var x=0;children.forEach{it.place(x,axis-it.axis());x+=it.width+spacing}}
    }
}
@Composable private fun MathStack(axisChild:Int,content:@Composable ()->Unit) {
    Layout(content=content){measurables,constraints->
        val children=measurables.map{it.measure(constraints.copy(minWidth=0,minHeight=0))}
        val width=children.maxOfOrNull{it.width} ?: 0
        val height=children.sumOf{it.height}
        val axis=children.take(axisChild).sumOf{it.height}+(children.getOrNull(axisChild)?.axis() ?: 0)
        layout(width,height,mapOf(MathAxis to axis)){var y=0;children.forEach{it.place((width-it.width)/2,y);y+=it.height}}
    }
}
@Composable private fun FractionLayout(numerator:@Composable ()->Unit,denominator:@Composable ()->Unit) {
    val ink=LocalInstrument.current.ink
    Layout(content={numerator();denominator();Box(Modifier.background(ink))}){ms,constraints->
        val inner=constraints.copy(minWidth=0,minHeight=0)
        val n=ms[0].measure(inner);val d=ms[1].measure(inner)
        val gap=3.dp.roundToPx();val padding=4.dp.roundToPx();val stroke=max(1,1.dp.roundToPx())
        val width=max(n.width,d.width)+2*padding;val barY=n.height+gap
        val bar=ms[2].measure(Constraints.fixed(width,stroke))
        val height=barY+stroke+gap+d.height
        layout(width,height,mapOf(MathAxis to barY+stroke/2)){n.place((width-n.width)/2,0);bar.place(0,barY);d.place((width-d.width)/2,barY+stroke+gap)}
    }
}
@Composable private fun PowerLayout(base:@Composable ()->Unit,power:@Composable ()->Unit) {
    Layout(content={base();power()}){ms,constraints->
        val inner=constraints.copy(minWidth=0,minHeight=0)
        val b=ms[0].measure(inner);val e=ms[1].measure(inner)
        val baseY=max(0,e.height-(b.height*.25f).toInt())
        val gap=1.dp.roundToPx()
        layout(b.width+e.width+gap,max(b.height+baseY,e.height),mapOf(MathAxis to baseY+b.axis())){b.place(0,baseY);e.place(b.width+gap,0)}
    }
}


/** Radical sign drawn to the radicand's height so the roof always meets the diagonal, even when the radicand contains an exponent. */
@Composable private fun RadicalSign(content:@Composable ()->Unit) {
    val ink=LocalInstrument.current.ink
    Layout(content={
        Box(Modifier.drawBehind {
            val stroke=1.dp.toPx()
            val downStroke=1.5.dp.toPx()
            val roofY=stroke/2
            val bottomY=size.height-downStroke/2
            val signW=(size.height*.45f).coerceIn(10.dp.toPx(),22.dp.toPx())
            val hook=Offset(signW*.16f,size.height*.51f)
            val bottom=Offset(signW*.38f,bottomY)
            drawPath(Path().apply {
                moveTo(stroke/2,size.height*.55f)
                lineTo(hook.x,hook.y)
                lineTo(bottom.x,bottom.y)
                lineTo(signW*.70f,roofY)
                lineTo(signW,roofY)
                lineTo(size.width,roofY)
            },ink,style=Stroke(stroke,cap=StrokeCap.Round,join=StrokeJoin.Round))
            drawLine(ink,hook,bottom,downStroke,cap=StrokeCap.Round)
        })
        content()
    }){ms,constraints->
        val c=ms[1].measure(constraints.copy(minWidth=0,minHeight=0))
        val padTop=2.dp.roundToPx();val padBottom=2.dp.roundToPx()
        val height=c.height+padTop+padBottom
        val signW=(height*.45f).coerceIn(10.dp.toPx(),22.dp.toPx()).toInt()
        val width=signW+c.width
        val bar=ms[0].measure(Constraints.fixed(width,height))
        layout(width,height,mapOf(MathAxis to padTop+c.axis())){bar.place(0,0);c.place(signW,padTop)}
    }
}

/** Positions every root index against the radical, including when the radicand makes it taller. */
@Composable private fun IndexedRadical(size:Float,index:@Composable ()->Unit,content:@Composable ()->Unit) {
    Layout(content={index();RadicalSign(content)}){ms,constraints->
        val inner=constraints.copy(minWidth=0,minHeight=0)
        val i=ms[0].measure(inner);val radical=ms[1].measure(inner)
        val indexX=(size*.24f).dp.roundToPx()
        val ordinaryHeight=(size.sp.toPx()*1.18f).roundToInt()+4.dp.roundToPx()
        val extraHeight=(radical.height-ordinaryHeight).coerceAtLeast(0)
        val indexY=(radical.axis()-i.axis()-(size*.16f).dp.roundToPx()-extraHeight*.8f).roundToInt().coerceAtLeast(0)
        layout(i.width+radical.width,max(radical.height,indexY+i.height),mapOf(MathAxis to radical.axis())){
            i.place(indexX,indexY)
            radical.place(i.width,0)
        }
    }
}

@Composable private fun RootIndexSlot(size:Float,active:Boolean) {
    val c=LocalInstrument.current
    Layout(content={
        MathText("⁴",size)
        Box(Modifier.border(1.dp,if(active)c.accent else c.muted,RoundedCornerShape(1.dp))
            .then(if(active)Modifier else Modifier.semantics{contentDescription="Empty expression slot"}),contentAlignment=Alignment.Center){
            if(active)MathText("│",size*.7f,blink=true)
        }
    }){ms,constraints->
        val glyph=ms[0].measure(constraints.copy(minWidth=0,minHeight=0))
        val boxWidth=glyph.width.coerceAtLeast(9.dp.roundToPx())
        val boxHeight=(glyph.height*.72f).roundToInt().coerceAtLeast(12.dp.roundToPx())
        val box=ms[1].measure(Constraints.fixed(boxWidth,boxHeight))
        layout(boxWidth,glyph.height,mapOf(MathAxis to glyph.axis())){box.place(0,(glyph.height-boxHeight)/2)}
    }
}
/** Compact empty slot for a logarithm base so the box matches the small subscript glyph. */
@Composable private fun LogBaseSlot(size:Float,active:Boolean) {
    val c=LocalInstrument.current
    Layout(content={
        MathText("a",size)
        Box(Modifier.border(1.dp,if(active)c.accent else c.muted,RoundedCornerShape(1.dp))
            .then(if(active)Modifier else Modifier.semantics{contentDescription="Empty expression slot"}),contentAlignment=Alignment.Center){
            if(active)MathText("│",size*.7f,blink=true)
        }
    }){ms,constraints->
        val glyph=ms[0].measure(constraints.copy(minWidth=0,minHeight=0))
        val boxWidth=glyph.width.coerceAtLeast(9.dp.roundToPx())
        val boxHeight=glyph.height
        val box=ms[1].measure(Constraints.fixed(boxWidth,boxHeight))
        layout(boxWidth,glyph.height,mapOf(MathAxis to glyph.axis())){box.place(0,0)}
    }
}
/** Compact empty slot for a power exponent so the box matches the small superscript font. */
@Composable private fun ExponentSlot(size:Float,active:Boolean) {
    val c=LocalInstrument.current
    Layout(content={
        MathText("8",size)
        Box(Modifier.border(1.dp,if(active)c.accent else c.muted,RoundedCornerShape(1.dp))
            .then(if(active)Modifier else Modifier.semantics{contentDescription="Empty expression slot"}),contentAlignment=Alignment.Center){
            if(active)MathText("│",size*.7f,blink=true)
        }
    }){ms,constraints->
        val glyph=ms[0].measure(constraints.copy(minWidth=0,minHeight=0))
        val boxWidth=glyph.width.coerceAtLeast(9.dp.roundToPx())
        val boxHeight=(glyph.height*.72f).roundToInt().coerceAtLeast(12.dp.roundToPx())
        val box=ms[1].measure(Constraints.fixed(boxWidth,boxHeight))
        layout(boxWidth,glyph.height,mapOf(MathAxis to glyph.axis())){box.place(0,(glyph.height-boxHeight)/2)}
    }
}
/** Places a logarithm base as a subscript under the operator's baseline. */
@Composable private fun LogBaseLayout(head:@Composable ()->Unit,base:@Composable ()->Unit) {
    Layout(content={head();base()}){ms,constraints->
        val inner=constraints.copy(minWidth=0,minHeight=0)
        val h=ms[0].measure(inner);val b=ms[1].measure(inner)
        val gap=1.dp.roundToPx()
        val drop=(h.height*.3f).roundToInt()
        val baseTop=(h.axis()+drop-b.axis()).coerceAtLeast(0)
        layout(h.width+gap+b.width,max(h.height,baseTop+b.height),mapOf(MathAxis to h.axis())){
            h.place(0,0)
            b.place(h.width+gap,baseTop)
        }
    }
}
@Composable private fun SquareBrackets(close:Boolean=true,content:@Composable ()->Unit) {
    val ink=LocalInstrument.current.ink
    Box(Modifier.drawBehind {
        val stroke=1.5.dp.toPx();val arm=7.dp.toPx();val left=stroke/2;val right=this.size.width-stroke/2
        drawLine(ink,Offset(left,0f),Offset(left,this.size.height),stroke)
        drawLine(ink,Offset(left,0f),Offset(left+arm,0f),stroke)
        drawLine(ink,Offset(left,this.size.height),Offset(left+arm,this.size.height),stroke)
        if(close) {
            drawLine(ink,Offset(right,0f),Offset(right,this.size.height),stroke)
            drawLine(ink,Offset(right-arm,0f),Offset(right,0f),stroke)
            drawLine(ink,Offset(right-arm,this.size.height),Offset(right,this.size.height),stroke)
        }
    }.padding(start=12.dp,end=if(close)12.dp else 4.dp,top=3.dp,bottom=3.dp)) {content()}
}

@Composable fun MathNode(node:JSONObject,size:Float=25f,select:((Int,Int)->Unit)?=null,selection:IntRange?=null,depth:Int=0,hideGroup:Boolean=false,compactRootIndexHole:Boolean=false,compactLogBaseHole:Boolean=false,compactExponentHole:Boolean=false,operandHole:Boolean=false) {
    if(depth>36){MathText("…",size);return}
    val c=LocalInstrument.current
    val raw=node.optString("kind")
    val kind=when(raw){"snapshot_symbol"->"symbol";"constant","float"->"text";"frozen_call"->"function";else->raw}
    val value=node.optString("value")
    val array=node.optJSONArray("args")
    val children=(0 until (array?.length() ?: 0)).mapNotNull{array?.optJSONObject(it)}
    val integrationTuple=if(kind=="call"&&value=="integrate"&&children.size==2&&children[1].optString("kind")=="tuple")children[1].optJSONArray("args")else null
    val integrationArity=integrationTuple?.length()?.plus(1) ?: children.size
    val coefficient=kind=="binary"&&value=="*"&&children.size==2&&children[0].optString("kind")=="number"&&children[1].optString("kind")=="symbol"
    val start=node.optInt("start",-1);val end=node.optInt("end",-1)
    val range=start..end
    val target=LocalMathCursorTarget.current
    val cursor=selection?.first ?: -1
    val caret=select!=null&&selection?.last==cursor&&target==range
    val selected=select!=null&&selection==range&&start!=end
    val activeHole=kind=="hole"&&caret
    val atomic=kind in listOf("number","symbol","text","hole")
    val emptyContainer=kind in listOf("list","set")&&children.isEmpty()&&end-start>=2
    val placeCursor=LocalPlaceCursor.current
    val typedParens=LocalTypedParens.current
    fun typedParen(node:JSONObject)=typedParens.any {it.first==node.optInt("start",-1)&&it.last==node.optInt("end",-1)}
    val touch=Modifier.then(if(selected||(activeHole&&!operandHole))Modifier.background(c.accent.copy(alpha=.17f),RoundedCornerShape(2.dp))else Modifier)
        .then(if(emptyContainer&&select!=null)Modifier.semantics(mergeDescendants=true){contentDescription=if(kind=="list")"Empty list; tap to enter values" else "Empty set; tap to enter values"}else Modifier)
        .then(if(select!=null&&start>=0)Modifier.clickable{if(emptyContainer){if(placeCursor!=null)placeCursor(start,end,start+1)else select(start+1,start+1)}else select(start,end)}else Modifier)
    @Composable fun child(i:Int,scale:Float=1f,hidden:Boolean=false,compactExponentHole:Boolean=false,operandHole:Boolean=false) {children.getOrNull(i)?.let{MathNode(it,(size*scale).coerceAtLeast(11f),select,selection,depth+1,hidden,compactExponentHole=compactExponentHole,operandHole=operandHole)}}
    @Composable fun integralPart(i:Int,scale:Float=1f) {
        if(i==0||integrationTuple==null)child(i,scale)
        else integrationTuple.optJSONObject(i-1)?.let{MathNode(it,(size*scale).coerceAtLeast(11f),select,selection,depth+1)}
    }
    @Composable fun label(text:String,scale:Float=1f){MathText(text,size*scale)}
    @Composable fun opLabel(index:Int,text:String,scale:Float=1f) {
        if(text.isEmpty())return
        val pick=select
        val from=if(index>0)children.getOrNull(index-1)?.optInt("end",-1) ?: -1 else -1
        val to=children.getOrNull(index)?.optInt("start",-1) ?: -1
        if(pick!=null&&(kind=="binary"||kind=="relation")&&from>=0&&to>from) {
            val on=selection!=null&&selection.first==from&&selection.last==to
            Box(Modifier.then(if(on)Modifier.background(c.accent.copy(alpha=.17f),RoundedCornerShape(2.dp))else Modifier).clickable{pick(from,to)}){label(text,scale)}
        } else label(text,scale)
    }
    @Composable fun wrapped(i:Int,scale:Float=1f){MathRow{label("(",scale);child(i,scale);label(")",scale)}}
    val fraction=kind=="fraction"||kind=="binary"&&value=="/"&&node.optString("displayOperator")!="÷"
    val power=kind=="power"||kind=="binary"&&value=="^"
    Box(touch) {MathRow {
        if(caret&&!atomic&&cursor<=start)MathText("│",size,blink=true)
        when {
            fraction->Box {
                FractionLayout({child(0,.9f,true)},{child(1,.9f,true)})
                if(select!=null){val after=LocalMathAfter.current;Box(Modifier.matchParentSize()){Box(Modifier.align(Alignment.CenterEnd).width(7.dp).fillMaxHeight().clickable{if(after!=null)after(start,end)else select(end,end)}.semantics{contentDescription="After fraction"})}}
            }
            power->PowerLayout({
                val base=children.firstOrNull()
                val groupInner=base?.optJSONArray("args")?.optJSONObject(0)
                val hide=base?.optString("kind")=="group"&&groupInner?.optString("kind") in listOf("number","symbol","hole","call")
                if(base?.optString("kind") in listOf("sum","product","unary"))wrapped(0)else child(0,hidden=hide)
            },{child(1,.67f,true,compactExponentHole=true)})
            kind=="root"||kind=="call"&&value in listOf("sqrt","cbrt","nthroot")->
                if(value=="cbrt"||value=="nthroot")IndexedRadical(size,
                    index={
                        if(value=="cbrt")label("³",.7f)
                        else {
                            val numericIndex=children.getOrNull(1)?.takeIf {
                                it.optString("kind")=="number"&&it.optString("value").let {digits->digits.isNotEmpty()&&digits.all {digit->digit in '0'..'9'}}
                            }
                            if(numericIndex!=null){
                                val superscript=numericIndex.optString("value").map {"⁰¹²³⁴⁵⁶⁷⁸⁹"[it-'0']}.joinToString("")
                                MathNode(JSONObject(numericIndex.toString()).put("value",superscript),(size*.7f).coerceAtLeast(11f),select,selection,depth+1)
                            } else if(children.getOrNull(1)?.optString("kind")=="hole") {
                                MathNode(children[1],(size*.7f).coerceAtLeast(11f),select,selection,depth+1,compactRootIndexHole=true)
                            } else child(1,.55f)
                        }
                    },
                    content={child(0,hidden=true)})
                else RadicalSign{child(0,hidden=true)}
            kind=="matrix"||kind=="list"&&children.isNotEmpty()&&children.all{it.optString("kind")=="list"}->SquareBrackets(close=value!="open") {
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)){children.forEach{row->MathRow(10.dp){val cells=row.optJSONArray("args");for(i in 0 until(cells?.length() ?: 0))cells?.optJSONObject(i)?.let{MathNode(it,size*.85f,select,selection,depth+1)}}}}
            }
            kind=="list"&&children.isNotEmpty()->SquareBrackets(close=value!="open") {MathRow(2.dp){children.indices.forEach {i->if(i>0)label(", ");child(i)}}}
            emptyContainer->MathRow {label(if(kind=="list")"[" else "{");if(caret&&cursor==start+1)MathText("│",size,blink=true);label(if(kind=="list")"]" else "}")}
            kind=="rows"->Column{children.forEach{row->MathRow{label(row.optString("value")+": ",.65f);row.optJSONArray("args")?.optJSONObject(0)?.let{MathNode(it,size*.8f,depth=depth+1)}}}}
            kind=="hole"->if(compactRootIndexHole)RootIndexSlot(size,activeHole)else if(compactLogBaseHole)LogBaseSlot(size,activeHole)else if(compactExponentHole)ExponentSlot(size,activeHole)else if(operandHole){
                if(caret)MathText("│",size,blink=true)
            }else
                Box(Modifier.width((size*.72f).dp).height((size*1.04f).dp).border(1.dp,if(activeHole)c.accent else c.muted,RoundedCornerShape(1.dp)).then(if(activeHole)Modifier else Modifier.semantics{contentDescription="Empty expression slot"}),contentAlignment=Alignment.Center){
                    // Keep the caret node laid out in both states; it defines the slot's MathAxis, so idle boxes no longer sit lower.
                    MathText("│",size,modifier=if(activeHole)Modifier else Modifier.clearAndSetSemantics{},blink=true,hide=!activeHole)
                }
            kind=="answer"->Box(Modifier.border(1.dp,c.muted,RoundedCornerShape(4.dp)).padding(horizontal=5.dp,vertical=2.dp).semantics{contentDescription="Previous answer"}){children.firstOrNull()?.let{MathNode(it,size*.9f,depth=depth+1)}}
            kind=="restricted"->child(0)
            kind=="quantity"->MathRow{child(0);label(" $value",.7f)}
            kind=="dms"||kind=="sexagesimal"->MathRow {
                child(0);label("°")
                val showMinuteMarker=value!="pending-minute"
                if(children.size>1){child(1);if(showMinuteMarker)label("′")}
                val showSecondField=value==""||value=="pending-final"
                if(children.size>2&&showSecondField)child(2)
                if(value=="")label("″")
            }
            // Empty parentheses stay visible; the hole between them shows only a blinking caret.
            kind=="group"->if(hideGroup)child(0,compactExponentHole=compactExponentHole)else MathRow{label("(");val inner=children.getOrNull(0);if(inner?.optString("kind")=="hole")child(0,operandHole=true)else if(inner!=null)child(0);if(value!="open")label(")")}
            kind=="call"&&value=="mixed"->MathRow(3.dp){child(0);FractionLayout({child(1,.85f)},{child(2,.85f)})}
            kind=="call"&&value=="eng"->child(0)
            kind in listOf("number","symbol","text")-> {
                val shown=when(value){"pi"->"π";"oo"->"∞";"I"->"i";"E"->"e";else->value}
                if(select==null) MathText(shown,size)
                else {
                    val at=if(end==start)0 else ((cursor-start)*shown.length/(end-start)).coerceIn(0,shown.length)
                    var textLayout by remember(shown){mutableStateOf<TextLayoutResult?>(null)}
                    val place=LocalPlaceCursor.current
                    val active=LocalActiveToken.current==range
                    val caretVisible=LocalCaretVisible.current
                    val cursorLine=Modifier.drawWithContent {
                        drawContent()
                        if(caret&&caretVisible)textLayout?.takeIf{it.layoutInput.text.text==shown}?.getCursorRect(at)?.let {rect->
                            drawLine(c.ink,Offset(rect.left,rect.top),Offset(rect.left,rect.bottom),1.dp.toPx())
                        }
                    }
                    MathText(shown,size,cursorLine.pointerInput(shown,selected,active,place){detectTapGestures{offset->
                        if(selected||active){val index=(textLayout?.takeIf{it.layoutInput.text.text==shown}?.getOffsetForPosition(offset) ?: 0).coerceIn(0,shown.length);place?.invoke(start,end,start+(index.toFloat()/shown.length.coerceAtLeast(1)*(end-start)).toInt())}
                        else select(start,end)
                    }},onLayout={textLayout=it})
                }
            }
            kind=="unary"->MathRow{
                val pick=select
                val gapTo=children.getOrNull(0)?.optInt("start",-1) ?: -1
                if(pick!=null&&gapTo>start) {
                    val on=selection!=null&&selection.first==start&&selection.last==gapTo
                    Box(Modifier.then(if(on)Modifier.background(c.accent.copy(alpha=.17f),RoundedCornerShape(2.dp))else Modifier).clickable{pick(start,gapTo)}){label(if(value=="-")"−" else value)}
                } else label(if(value=="-")"−" else value)
                child(0,operandHole=true)
            }
            kind in listOf("call","function")&&value=="factorial"->MathRow{child(0);label("!")}
            kind in listOf("call","function")&&value in listOf("degree","rad","gradian","percent")->MathRow{child(0);label(when(value){"degree"->"°";"rad"->"ʳ";"gradian"->"ᵍ";else->"%"})}
            kind in listOf("call","function")&&value in listOf("abs","Abs")->MathRow{label("│");child(0);label("│")}
            kind in listOf("call","function")&&value in listOf("exp","Exp")&&children.size==1->PowerLayout({label("e")},{child(0,.67f,true,compactExponentHole=true)})
            kind=="call"&&value=="log"&&children.size>1->MathRow{LogBaseLayout({label("log")},{MathNode(children[1],(size*.6f).coerceAtLeast(11f),select,selection,depth+1,compactLogBaseHole=true)});wrapped(0)}
            kind=="call"&&value in listOf("diff","nderivative")->MathRow(3.dp){
                FractionLayout({if(value=="diff"&&children.size>2)PowerLayout({label("d",.8f)},{child(2,.5f)})else label("d",.8f)},
                    {MathRow{label("d",.8f);if(value=="diff"&&children.size>2)PowerLayout({child(1,.8f)},{child(2,.5f)})else child(1,.8f)}})
                wrapped(0,.95f)
                if(value=="nderivative"){label("│",1.4f);Box(Modifier.padding(top=16.dp)){MathRow{child(1,.6f);label("=",.6f);child(2,.6f)}}}
            }
            kind=="call"&&value in listOf("integrate","sum","product")->MathRow(3.dp){
                MathStack(if(integrationArity>3)1 else 0){
                    if(integrationArity>3)integralPart(3,.55f)
                    label(when(value){"integrate"->"∫";"sum"->"Σ";else->"Π"},1.4f)
                    if(integrationArity>2)MathRow{if(value!="integrate"){child(1,.55f);label("=",.55f)};integralPart(2,.55f)}
                }
                child(0)
                if(value=="integrate"){label("d");integralPart(1)}
            }
            kind=="call"&&value=="limit"->MathRow(4.dp){MathStack(0){label("lim");MathRow{child(1,.55f);if(children.size>2){label("→",.55f);child(2,.55f)}}};child(0)}
            kind in listOf("call","function")&&value in listOf("piecewise","Piecewise")->MathRow{label("{",1.7f);Column{children.indices.forEach{child(it,.85f)}}}
            else->MathRow(if(coefficient)0.dp else 2.dp){
                val wrap=kind in listOf("call","function","list","tuple","set")
                val openContainer=value=="open"&&kind in listOf("list","set")
                val operandHoles=kind=="binary"||kind=="relation"
                if(kind in listOf("call","function"))label(value,.9f)
                if(wrap)label(when(kind){"list"->"[";"set"->"{";else->"("})
                children.forEachIndexed{i,n->
                    val negativePart=if(kind=="sum"&&n.optString("kind")=="unary"&&n.optString("value")=="-")n.optJSONArray("args")?.optJSONObject(0)else null
                    if(negativePart!=null){label(if(i==0)"−" else " − ");MathNode(negativePart,size,select,selection,depth+1)}
                    else {
                        val adjacentCoefficient=kind=="product"&&i>0&&children[i-1].optString("kind")=="number"&&n.optString("kind")=="symbol"
                        if(i>0&&!coefficient&&!adjacentCoefficient)opLabel(i,when(kind){"sum"->" + ";"product"->" · ";"binary","relation"->when(value){"*"->if(node.optString("displayOperator")=="∘")"" else " × ";"/"->" ÷ ";"-"->" − ";"!="->" ≠ ";"<="->" ≤ ";">="->" ≥ ";else->" $value "};else->", "})
                        if(kind=="product"&&n.optString("kind")=="sum")wrapped(i)
                        // Slots keep their box; directly typed parentheses stay visible.
                        else child(i,hidden=kind=="binary"&&value=="*"&&n.optString("kind")=="group"&&n.optJSONArray("args")?.optJSONObject(0)?.optString("kind")=="hole"&&n.optString("value")!="open"&&!typedParen(n),operandHole=operandHoles)
                    }
                }
                if(kind=="tuple"&&children.size==1)label(",")
                if(wrap&&!openContainer)label(when(kind){"list"->"]";"set"->"}";else->")"})
                if(children.isEmpty())label(value)
            }
        }
        if(caret&&!atomic&&cursor>start&&!(emptyContainer&&cursor==start+1))MathText("│",size,blink=true)
    }}
}

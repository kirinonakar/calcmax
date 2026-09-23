package com.example.calcmax.ui

import android.media.AudioManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.*
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.math.Editor
import com.example.calcmax.ui.theme.LocalInstrument
import kotlin.random.Random

data class KeySpec(val title:String,val input:String=title,val secondary:String="",val alternate:String="",val alpha:String="",val type:String="scientific")
private val ScientificKeys=listOf(
    listOf(KeySpec("a/b","()/()","mixed","mixed(,,)"),KeySpec("√","sqrt()","³√","cbrt()"),KeySpec("x²","^2","x³","^3"),KeySpec("x□","^()","ⁿ√","nthroot(,)"),KeySpec("log","log()","10ˣ","10^()"),KeySpec("ln","ln()","eˣ","exp()")),
    listOf(KeySpec("(−)","NEG","∠","∠","A"),KeySpec("°′″","°","←","DMS","B"),KeySpec("hyp","HYP","Abs","abs()","C"),KeySpec("sin","sin()","sin⁻¹","asin()","D"),KeySpec("cos","cos()","cos⁻¹","acos()","E"),KeySpec("tan","tan()","tan⁻¹","atan()","F")),
    listOf(KeySpec("RCL",secondary="STO",alternate="STO"),KeySpec("ENG",secondary="←",alternate="ENG−",alpha="i"),KeySpec("(",secondary="%",alternate="%"),KeySpec(")",secondary=",",alternate=",",alpha="x"),KeySpec("S⇔D",secondary="a b/c ⇔ d/c",alternate="MIXED",alpha="y"),KeySpec("M+",secondary="M−",alternate="M−",alpha="M"))
)
private val NumericKeys=listOf(
    listOf(KeySpec("7",secondary="CONST",alternate="Constants"),KeySpec("8",secondary="CONV",alternate="Units"),KeySpec("9",secondary="CLR",alternate="Clear"),KeySpec("DEL",secondary="INS",alternate="INS",type="danger"),KeySpec("AC",secondary="OFF",alternate="OFF",type="danger")),
    listOf(KeySpec("4",secondary="MATRIX",alternate="Matrix"),KeySpec("5",secondary="VECTOR",alternate="Vector"),KeySpec("6"),KeySpec("×",secondary="nPr",alternate="nPr(,)"),KeySpec("÷",secondary="nCr",alternate="nCr(,)")),
    listOf(KeySpec("1",secondary="STAT",alternate="Statistics"),KeySpec("2",secondary="CMPLX",alternate="Complex"),KeySpec("3",secondary="BASE",alternate="Programmer"),KeySpec("+",secondary="Pol",alternate="pol(,)"),KeySpec("−",secondary="Rec",alternate="rec(,)")),
    listOf(KeySpec("0",secondary="Rnd",alternate="rnd()"),KeySpec(".",secondary="Ran#",alternate="RANDOM",alpha="randInt(,)"),KeySpec("×10ˣ","*10^()","π","pi","e"),KeySpec("Ans",secondary="DRG▶",alternate="ANGLE"),KeySpec("="))
)

@Composable fun Keypad(m:CalculatorModel,modifier:Modifier=Modifier,open:(String)->Unit) {
    val c=LocalInstrument.current
    val haptic=LocalHapticFeedback.current
    val context=LocalContext.current
    var engineering by remember {mutableIntStateOf(0)}
    fun press(key:KeySpec) {
        if(!m.poweredOn && key.title!="ON")return
        if(m.haptics)haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if(m.sound)(context.getSystemService(android.content.Context.AUDIO_SERVICE)as AudioManager).playSoundEffect(AudioManager.FX_KEY_CLICK)
        if(key.title=="SHIFT"){m.shift=!m.shift;m.alpha=false;return}
        if(key.title=="ALPHA"){m.alpha=!m.alpha;m.shift=false;return}
        var value=if(m.alpha&&key.alpha.isNotEmpty())key.alpha else if(m.shift&&key.alternate.isNotEmpty())key.alternate else key.input
        if(key.title=="CALC"&&m.alpha)value="RELATION"
        if(m.hyperbolic && value in listOf("sin()","cos()","tan()","asin()","acos()","atan()"))value=value.substringBefore('(')+"h()"
        when(value) {
            "ON"->{m.poweredOn=true;m.clear()}
            "OFF"->{m.cancel();m.error="";m.poweredOn=false}
            "MODE"->open("Mode");"SETUP"->open("Settings")
            "CALC","="->m.calculate()
            "RELATION"->m.insert("=")
            "()/()"->m.fraction()
            "SOLVE"->{m.edit(Editor("solve(${m.editor.source.ifBlank{"x"}},x)"));m.calculate()}
            "LEFT"->m.edit(m.editor.move(-1));"RIGHT"->m.edit(m.editor.move(1));"UP"->m.edit(m.editor.parent());"DOWN"->m.edit(m.editor.child())
            "RCL","STO","Clear"->open(value)
            "Constants","Units","Matrix","Vector","Statistics","Programmer"->{m.mode=value}
            "Complex"->{m.mode="Scientific";open("Catalog")}
            "HYP"->{m.hyperbolic=!m.hyperbolic}
            "S⇔D"->m.decimal=!m.decimal
            "MIXED"->{m.mixedNumbers=!m.mixedNumbers;m.decimal=false}
            "AC"->m.clear();"DEL"->m.edit(m.editor.delete());"INS"->m.overwrite=!m.overwrite
            "M+","M−"->{val previous=if(m.variables.has("M"))"M" else "0";m.store("M","$previous${if(value=="M+")"+" else "-"}(${m.editor.source.ifBlank{"0"}})")}
            "NEG"->{if(m.committed)m.fresh();m.insert("-")}
            "ANGLE"->open("Angle")
            "RANDOM"->m.insert("0."+Random.nextInt(1000).toString().padStart(3,'0'))
            "ENG","ENG−"->{val exponent=engineering;engineering+=if(value=="ENG")3 else -3;m.edit(Editor("eng(${m.editor.source.ifBlank{"0"}},$exponent)"))}
            "DMS"->m.edit(Editor("dms(${m.editor.source.ifBlank{"0"}})"))
            else->{val at=when {value=="()/()"->1;value.contains('(')->value.indexOf('(')+1;else->value.length};m.insert(value,at)}
        }
        if(value!="HYP")m.hyperbolic=false
        m.shift=false;m.alpha=false
    }
    Column(modifier.background(c.body).padding(horizontal=8.dp,vertical=4.dp).semantics {contentDescription="Calculator keypad"},verticalArrangement=Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(2f)) {
            val column=maxWidth/6
            val row=maxHeight/2
            val top=listOf(KeySpec("SHIFT",type="round"),KeySpec("ALPHA",type="round"),KeySpec("MODE",secondary="SETUP",alternate="SETUP",type="round"),KeySpec("ON",type="round"))
            top.forEachIndexed {i,k->val col=if(i<2)i else i+2;Keycap(k,Modifier.offset(x=column*col).width(column-4.dp).height(row),m.shift&&k.title=="SHIFT"||m.alpha&&k.title=="ALPHA"){press(k)}}
            val bottom=listOf(KeySpec("CALC",secondary="SOLVE",alternate="SOLVE",alpha="="),KeySpec("∫","integrate(,x,,)","d/dx","nderivative(,x,)",":"),KeySpec("x⁻¹","^(-1)","x!","!"),KeySpec("logₐ□","log(,)","Σ","sum(,x,,)"))
            bottom.forEachIndexed {i,k->val col=if(i<2)i else i+2;Keycap(k,Modifier.offset(x=column*col,y=row).width(column-4.dp).height(row)){press(k)}}
            Box(Modifier.offset(x=column*2).width(column*2-4.dp).fillMaxHeight(),contentAlignment=Alignment.Center) {
                Box(Modifier.fillMaxSize(.88f).clip(CircleShape).background(c.scientific).border(1.dp,c.muted.copy(alpha=.3f),CircleShape))
                DirectionKey("▲","Cursor up",Modifier.align(Alignment.TopCenter).fillMaxWidth(.3f).fillMaxHeight(.34f)){press(KeySpec("UP"))}
                DirectionKey("◀","Cursor left",Modifier.align(Alignment.CenterStart).fillMaxWidth(.36f).fillMaxHeight(.32f)){press(KeySpec("LEFT"))}
                DirectionKey("▶","Cursor right",Modifier.align(Alignment.CenterEnd).fillMaxWidth(.36f).fillMaxHeight(.32f)){press(KeySpec("RIGHT"))}
                DirectionKey("▼","Cursor down",Modifier.align(Alignment.BottomCenter).fillMaxWidth(.3f).fillMaxHeight(.34f)){press(KeySpec("DOWN"))}
            }
        }
        ScientificKeys.forEach {row->Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(5.dp)){row.forEach {key->Keycap(key,Modifier.weight(1f).fillMaxHeight()){press(key)}}}}
        NumericKeys.forEach {row->Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(6.dp)){row.forEach {key->Keycap(if(key.type=="danger")key else key.copy(type="numeric"),Modifier.weight(1f).fillMaxHeight()){press(key)}}}}
    }
}

@Composable private fun DirectionKey(label:String,description:String,modifier:Modifier,onClick:()->Unit) {
    val c=LocalInstrument.current
    Box(modifier.clip(RoundedCornerShape(35)).background(Brush.verticalGradient(listOf(c.numeric,c.scientific))).border(1.dp,c.muted.copy(alpha=.3f),RoundedCornerShape(35)).clickable(onClick=onClick).semantics{contentDescription=description},contentAlignment=Alignment.Center){Text(label,fontSize=12.sp,color=c.ink)}
}
@Composable private fun Keycap(key:KeySpec,modifier:Modifier,active:Boolean=false,onClick:()->Unit) {
    val c=LocalInstrument.current
    val bg=when(key.type){"numeric"->c.numeric;"danger"->c.clearKey;else->c.scientific}
    val ink=if(key.type=="danger")c.clearInk else c.ink
    BoxWithConstraints(modifier.clickable(onClick=onClick).semantics(mergeDescendants=true){contentDescription=key.title;stateDescription=if(active)"Active" else listOf(key.secondary,key.alpha).filter{it.isNotBlank()}.joinToString()}) {
        val labelHeight=(maxHeight*.25f).coerceAtMost(15.dp)
        val keyFont=(maxHeight.value*(if(key.type in listOf("numeric","danger")) .44f else .32f)).coerceIn(10f,24f).sp
        val smallFont=(labelHeight.value*.66f).coerceIn(6f,if(key.secondary.length+key.alpha.length>10)7.5f else 10f).sp
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height(labelHeight),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                Text(if(key.type=="round")key.title else key.secondary,color=if(key.title=="ALPHA")c.alpha else c.shift,fontSize=smallFont,lineHeight=smallFont,maxLines=1)
                if(key.alpha.isNotBlank())Text("  "+when(key.alpha){"x"->"X";"y"->"Y";else->key.alpha},color=c.alpha,fontSize=smallFont,lineHeight=smallFont,maxLines=1)
                if(key.type=="round"&&key.secondary.isNotBlank())Text(" "+key.secondary,color=c.shift,fontSize=7.sp,lineHeight=7.sp)
            }
            val shape=if(key.type=="round")CircleShape else RoundedCornerShape(topStart=7.dp,topEnd=7.dp,bottomStart=4.dp,bottomEnd=4.dp)
            Box(Modifier.then(if(key.type=="round")Modifier.aspectRatio(1f).weight(1f,false) else Modifier.fillMaxWidth().weight(1f)).clip(shape).background(Brush.verticalGradient(listOf(bg,bg.copy(alpha=.85f)))).border(if(active)2.dp else 1.dp,if(active)c.accent else c.muted.copy(alpha=.26f),shape),contentAlignment=Alignment.Center){
                if(key.title=="a/b")Column(horizontalAlignment=Alignment.CenterHorizontally){Text("□",color=ink,fontSize=9.sp,lineHeight=10.sp);Box(Modifier.width(15.dp).height(1.dp).background(ink));Text("□",color=ink,fontSize=9.sp,lineHeight=10.sp)}
                else if(key.title=="x□")Text(buildAnnotatedString{append("x");withStyle(SpanStyle(baselineShift=BaselineShift.Superscript,fontSize=(keyFont.value*.65f).sp)){append("□")}},color=ink,fontSize=keyFont)
                else if(key.type!="round")Text(key.title,color=ink,fontSize=keyFont,fontWeight=FontWeight.Medium,maxLines=1)
                else Box(Modifier.fillMaxSize(.55f).border(1.dp,c.muted.copy(alpha=.25f),CircleShape))
            }
        }
    }
}

package com.kirinonakar.symvacas.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.*
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.calculator.DisplayShortcut
import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import kotlin.random.Random

data class KeySpec(val title:String,val input:String=title,val secondary:String="",val alternate:String="",val alpha:String="",val type:String="scientific")
private val ScientificKeys=listOf(
    listOf(KeySpec("a/b","()/()","mixed","mixed(,,)"),KeySpec("√","sqrt()","³√","cbrt()"),KeySpec("x²","^2","x³","^3"),KeySpec("x□","^()","ⁿ√","nthroot(,)"),KeySpec("log","log()","10ˣ","10^()","n"),KeySpec("ln","ln()","eˣ","e^()","t")),
    listOf(KeySpec("(−)","NEG","∠","∠","A"),KeySpec("°′″","DMS_INPUT","←","DMS","B"),KeySpec("hyp","HYP","Abs","abs()","C"),KeySpec("sin","sin()","sin⁻¹","asin()","D"),KeySpec("cos","cos()","cos⁻¹","acos()","r"),KeySpec("tan","tan()","tan⁻¹","atan()","F")),
    listOf(KeySpec("RCL",secondary="STO",alternate="STO"),KeySpec("ENG",secondary="←",alternate="ENG−",alpha="i"),KeySpec("(",secondary="%",alternate="%",alpha="z"),KeySpec(")",secondary=",",alternate=",",alpha="x"),KeySpec("S⇔D",secondary="a b/c ⇔ d/c",alternate="MIXED",alpha="y"),KeySpec("M+",secondary="M−",alternate="M−",alpha="M"))
)
private val SecondKeys=listOf(
    listOf(KeySpec("simp","simplify()"),KeySpec("factor","factor()","factorint","factorint()"),KeySpec("expand","expand()"),KeySpec("x", "x", "xʸ", "^()"),KeySpec("y",secondary="x²",alternate="^2"),KeySpec("z",secondary="=",alternate="RELATION")),
    listOf(KeySpec("⌊x⌋","floor()","mod","mod(,)","a"),KeySpec("⌈x⌉","ceil()","divmod","divmod(,)","b"),KeySpec("∞","oo","sign","sign()","c"),KeySpec(",",secondary="√",alternate="sqrt()",alpha="d"),KeySpec("{",secondary="[",alternate="["),KeySpec("}",secondary="]",alternate="]")),
    listOf(KeySpec("MATRIX","MATRIX_INPUT",secondary="n×m",alternate="MATRIX_SIZE",type="action"),KeySpec("det","det()",secondary="Pol",alternate="pol(,)"),KeySpec("inv","inverse()",secondary="Rec",alternate="rec(,)"),KeySpec("T","transpose()"),KeySpec("‖v‖","norm()"),KeySpec("GRAPH","TO_GRAPH",secondary="MODE",alternate="Graph",type="action"))
)
private val NumericKeys=listOf(
    listOf(KeySpec("7",secondary="CONST",alternate="Constants"),KeySpec("8",secondary="CONV",alternate="Units"),KeySpec("9",secondary="CLR",alternate="Clear"),KeySpec("DEL",secondary="INS",alternate="INS",type="danger"),KeySpec("AC",secondary="CLR ALL",alternate="CLR ALL",type="danger")),
    listOf(KeySpec("4",secondary="MATRIX",alternate="Matrix"),KeySpec("5",secondary="VECTOR",alternate="Vector"),KeySpec("6",secondary="EQN",alternate="Equations"),KeySpec("×",secondary="nPr",alternate="nPr(,)"),KeySpec("÷",secondary="nCr",alternate="nCr(,)")),
    listOf(KeySpec("1",secondary="STAT",alternate="Statistics"),KeySpec("2",secondary="PY",alternate="Python"),KeySpec("3",secondary="BASE",alternate="Programmer"),KeySpec("+",secondary="π",alternate="pi"),KeySpec("−",secondary="𝑒",alternate="e")),
    listOf(KeySpec("0",secondary="Ran#",alternate="RANDOM"),KeySpec(".",alpha="randInt(,)"),KeySpec("×10ˣ","*10^()"),KeySpec("Ans"),KeySpec("=",secondary="GRAPH",alternate="Graph"))
)

internal val KeypadShortcutGroups:Map<String,List<DisplayShortcut>> by lazy {
    fun choices(keys:List<KeySpec>):List<DisplayShortcut> = keys.flatMap {key->
        buildList {
            add(DisplayShortcut(key.title,key.input))
            if(key.alternate.isNotBlank())add(DisplayShortcut(key.secondary.ifBlank { key.alternate },key.alternate))
            if(key.alpha.isNotBlank())add(DisplayShortcut(key.alpha,if(key.title=="CALC")"RELATION" else key.alpha))
        }
    }
    linkedMapOf(
        "Main keys" to choices(listOf(KeySpec("SHIFT"),KeySpec("ALPHA"),KeySpec("MODE",alternate="Scientific/CAS"),KeySpec("2nd","SECOND"),KeySpec("CALC",secondary="SOLVE",alternate="SOLVE",alpha="="),KeySpec("∫","integrate(,x,,)","d/dx","nderivative(,x,)",":"),KeySpec("x⁻¹","^(-1)","x!","!"),KeySpec("logₐ□","log(,)","Σ","sum(,x,,)"),KeySpec("▲","UP"),KeySpec("◀","LEFT"),KeySpec("▶","RIGHT"),KeySpec("▼","DOWN"))+ScientificKeys.flatten()),
        "2nd keys" to choices(listOf(KeySpec("d/dx","diff(,x)","∫","integrate(,x)"),KeySpec("lim","limit(,x,)"),KeySpec("sinc","sinc()"),KeySpec("Π","product(,x,,)"))+SecondKeys.flatten()),
        "Number keys" to choices(NumericKeys.flatten()),
        "ALPHA" to (('A'..'Z')+('a'..'z')).map {letter->DisplayShortcut(letter.toString(),letter.toString())}
    )
}

private fun pressedShade(base:Color)=if(base.luminance()>.45f)Color.Black.copy(alpha=.18f) else Color.White.copy(alpha=.24f)
private val backspaceShape by lazy {
    PathParser().parsePathString("M6.1 1.4H20.2C21.2 1.4 22 2.2 22 3.2V14.8C22 15.8 21.2 16.6 20.2 16.6H6.1C5.5 16.6 5.1 16.4 4.8 15.9L1.8 9.8C1.5 9.3 1.5 8.7 1.8 8.2L4.8 2.1C5.1 1.6 5.5 1.4 6.1 1.4Z").toPath()
}
private val backspaceCross by lazy {PathParser().parsePathString("M9.9 5.8L16.3 12.2M16.3 5.8L9.9 12.2").toPath()}

private fun keypadOperandInput(value:String,editor:Editor,startingFresh:Boolean):String {
    if(startingFresh||editor.cursor!=editor.anchor)return value
    val before=editor.source.getOrNull(editor.cursor-1) ?: return value
    val first=value.firstOrNull()
    val startsFactor=value=="10^()"||first?.isDigit()==true&&(before.isLetter()||before in ")]}")
    return if((before.isLetterOrDigit()||before in ")]}")&&startsFactor)"*$value" else value
}

internal fun performKeypadInput(m:CalculatorModel,requestedValue:String,open:(String)->Unit) {
    if(!m.poweredOn && requestedValue!="SECOND")return
    if(requestedValue=="SHIFT"){m.shift=!m.shift;m.alpha=false;return}
    if(requestedValue=="ALPHA"){m.alpha=!m.alpha;m.shift=false;return}
    if(requestedValue=="SECOND"){m.poweredOn=true;m.secondKeys=!m.secondKeys;m.shift=false;m.alpha=false;return}
    var value=requestedValue
    if(m.calcSession!=null) {
        when(value) {
            "AC","ON"->m.cancelCalc()
            "=","CALC"->m.submitCalcValue()
            "DEL"->m.editCalcValue(m.calcSession!!.input.delete())
            "LEFT"->m.editCalcValue(m.calcSession!!.input.move(-1))
            "RIGHT"->m.editCalcValue(m.calcSession!!.input.move(1))
            "NEG"->m.insertCalcValue("-")
            "RCL","STO","Clear","CLR ALL","MODE","SETUP","ENG","ENG−","S⇔D","MIXED","M+","M−","SOLVE","RELATION","Graph","Equations","Scientific/CAS","Python","TO_GRAPH","MATRIX_INPUT","MATRIX_SIZE"->Unit
            else->{val input=keypadOperandInput(value,m.calcSession!!.input,false);val at=if(input.contains('('))input.indexOf('(')+1 else input.length;m.insertCalcValue(input,at)}
        }
        m.shift=false;m.alpha=false
        return
    }
    if(m.hyperbolic && value in listOf("sin()","cos()","tan()","asin()","acos()","atan()"))value=value.substringBefore('(')+"h()"
    if(m.engineeringConversion && value !in setOf("ENG","ENG−","LEFT","RIGHT","=","CALC","AC","ON","CLR ALL"))m.exitEngineering()
    when(value) {
        "ON"->{m.poweredOn=true;m.clear()}
        "CLR ALL"->m.clearAllScreen()
        "MODE"->open("Mode");"SETUP"->open("Settings")
        "CALC"->if(m.engineeringConversion)m.exitEngineering()else m.startCalc()
        "="->if(m.engineeringConversion)m.exitEngineering()else m.calculate()
        "RELATION"->m.insert("=")
        "()/()"->m.fraction()
        "^2","^3","^()","^(-1)"->m.powerTemplate(value)
        "SOLVE"->{m.edit(Editor("solve(${m.editor.source.ifBlank{"x"}},x)"));m.calculate()}
        "LEFT"->if(m.engineeringConversion)m.shiftEngineering(1)else m.edit(m.editor.moveMatrix(0,-1) ?: m.editor.move(-1))
        "RIGHT"->if(m.engineeringConversion)m.shiftEngineering(-1)else m.edit(m.editor.moveMatrix(0,1) ?: m.editor.move(1))
        "UP"->m.edit(m.editor.moveMatrix(-1,0) ?: m.editor.parent());"DOWN"->m.edit(m.editor.moveMatrix(1,0) ?: m.editor.child())
        "RCL","STO","Clear"->open(value)
        "Constants","Units","Matrix","Vector","Statistics","Programmer","Graph","Equations","Scientific/CAS","Python"->{m.mode=value}
        "Complex"->{m.mode="Scientific/CAS";open("Catalog")}
        "HYP"->{m.hyperbolic=!m.hyperbolic}
        "S⇔D"->m.decimal=!m.decimal
        "MIXED"->{m.mixedNumbers=!m.mixedNumbers;m.decimal=false}
        "AC"->m.ac();"DEL"->m.edit(m.editor.delete());"INS"->m.overwrite=!m.overwrite
        "M+","M−"->m.memory(if(value=="M+")1 else -1)
        "NEG"->{if(m.committed)m.fresh(Editor("-"))else m.insert("-")}
        "RANDOM"->m.insert("0."+Random.nextInt(1000).toString().padStart(3,'0'))
        "ENG"->m.enterEngineering()
        "ENG−"->{m.enterEngineering();m.shiftEngineering(3)}
        "DMS_INPUT"->m.insertDmsSymbol()
        "DMS"->m.toggleDms()
        "TO_GRAPH"->m.sendExpressionToGraph()
        "MATRIX_INPUT"->m.insert(matrixTemplate(2,2),2)
        "MATRIX_SIZE"->open("MatrixSize")
        "*10^()"->{val text=if(m.editor.source.isBlank()||m.committed)"1$value" else value;m.insert(text,text.indexOf('(')+1)}
        else->{val input=keypadOperandInput(value,m.editor,m.committed);val at=when {input=="()/()"->1;input.contains('(')->input.indexOf('(')+1;else->input.length};m.insert(input,at)}
    }
    if(value!="HYP")m.hyperbolic=false
    m.shift=false;m.alpha=false
}

@Composable fun Keypad(m:CalculatorModel,modifier:Modifier,numericOnly:Boolean,numericRowHeight:Dp,open:(String)->Unit) {
    val c=LocalInstrument.current
    val context=LocalContext.current
    @Suppress("DEPRECATION")
    val vibrator=remember(context) {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
    }
    val tone=remember {ToneGenerator(AudioManager.STREAM_MUSIC,65)}
    DisposableEffect(tone) {onDispose {tone.release()}}
    fun press(key:KeySpec) {
        if(!m.poweredOn && key.input!="SECOND")return
        if(m.haptics&&vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createOneShot(18,VibrationEffect.DEFAULT_AMPLITUDE))
        if(m.sound)tone.startTone(ToneGenerator.TONE_PROP_BEEP,35)
        var value=if(m.alpha&&key.alpha.isNotEmpty())key.alpha else if(m.shift&&key.alternate.isNotEmpty())key.alternate else key.input
        if(key.title=="CALC"&&m.alpha)value="RELATION"
        performKeypadInput(m,value,open)
    }
    fun pressLong(key:KeySpec) {
        if(key.title=="SHIFT"||key.title=="ALPHA"||key.input=="SECOND"){press(key);return}
        if(!m.poweredOn && key.input!="SECOND")return
        if(key.alternate.isNotBlank()){
            m.shift=true;m.alpha=false
            press(key)
            // press()가 shift/alpha를 초기화하므로 추가 처리 불필요.
            // 롱터치가 일반 입력으로 폴백되는 것을 막기 위해 여기서 종료.
            return
        }
        press(key)
    }
    Column(modifier.background(c.body).padding(horizontal=8.dp,vertical=4.dp).semantics {contentDescription="Calculator keypad"},verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(!numericOnly) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(2f)) {
                val gap=5.dp
                val keyWidth=(maxWidth-gap*5)/6
                val column=keyWidth+gap
                val row=maxHeight/2
                val top=listOf(KeySpec("SHIFT",type="utility"),KeySpec("ALPHA",type="utility"),KeySpec("MODE",alternate="Scientific/CAS",type="utility"),KeySpec(if(m.secondKeys)"1st" else "2nd","SECOND",type="utility"))
                top.forEachIndexed {i,k->val col=if(i<2)i else i+2;Keycap(k,Modifier.offset(x=column*col).width(keyWidth).height(row),m.shift&&k.title=="SHIFT"||m.alpha&&k.title=="ALPHA",onClick={press(k)},onLongClick={pressLong(k)})}
                val bottom=if(m.secondKeys)listOf(KeySpec("d/dx","diff(,x)","∫","integrate(,x)"),KeySpec("lim","limit(,x,)"),KeySpec("sinc","sinc()"),KeySpec("Π","product(,x,,)")) else listOf(KeySpec("CALC",secondary="SOLVE",alternate="SOLVE",alpha="="),KeySpec("∫","integrate(,x,,)","d/dx","nderivative(,x,)",":"),KeySpec("x⁻¹","^(-1)","x!","!"),KeySpec("logₐ□","log(,)","Σ","sum(,x,,)"))
                bottom.forEachIndexed {i,k->val col=if(i<2)i else i+2;Keycap(k,Modifier.offset(x=column*col,y=row).width(keyWidth).height(row),onClick={press(k)},onLongClick={pressLong(k)})}
                Box(Modifier.offset(x=column*2).width(keyWidth*2+gap).fillMaxHeight(),contentAlignment=Alignment.Center) {
                    Box(Modifier.fillMaxSize(.88f).clip(CircleShape).background(c.scientific).border(1.dp,c.muted.copy(alpha=.3f),CircleShape))
                    DirectionKey("▲","Cursor up",Modifier.align(Alignment.TopCenter).fillMaxWidth(.3f).fillMaxHeight(.34f)){press(KeySpec("UP"))}
                    DirectionKey("◀","Cursor left",Modifier.align(Alignment.CenterStart).fillMaxWidth(.36f).fillMaxHeight(.32f)){press(KeySpec("LEFT"))}
                    DirectionKey("▶","Cursor right",Modifier.align(Alignment.CenterEnd).fillMaxWidth(.36f).fillMaxHeight(.32f)){press(KeySpec("RIGHT"))}
                    DirectionKey("▼","Cursor down",Modifier.align(Alignment.BottomCenter).fillMaxWidth(.3f).fillMaxHeight(.34f)){press(KeySpec("DOWN"))}
                }
            }
            (if(m.secondKeys)SecondKeys else ScientificKeys).forEach {row->Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(5.dp)){row.forEach {key->Keycap(key,Modifier.weight(1f).fillMaxHeight(),active=key.title=="ENG"&&m.engineeringConversion,shifted=m.shift,onClick={press(key)},onLongClick={pressLong(key)})}}}
        }
        NumericKeys.forEach {row->Row(Modifier.fillMaxWidth().height(numericRowHeight),horizontalArrangement=Arrangement.spacedBy(6.dp)){row.forEach {key->Keycap(if(key.type=="danger")key else key.copy(type="numeric"),Modifier.weight(1f).fillMaxHeight(),onClick={press(key)},onLongClick={pressLong(key.copy(type=if(key.type=="danger")key.type else "numeric") )})}}}
    }
}

@Composable private fun DirectionKey(label:String,description:String,modifier:Modifier,onClick:()->Unit) {
    val c=LocalInstrument.current
    val interaction=remember{MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    Box(modifier.clip(RoundedCornerShape(35)).background(Brush.verticalGradient(listOf(c.numeric,c.scientific))).background(if(pressed)pressedShade(c.numeric) else Color.Transparent).border(1.dp,c.muted.copy(alpha=.3f),RoundedCornerShape(35)).clickable(interactionSource=interaction,indication=LocalIndication.current,onClick=onClick).semantics{contentDescription=description},contentAlignment=Alignment.Center){Text(label,fontSize=12.sp,color=c.ink)}
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun Keycap(key:KeySpec,modifier:Modifier,active:Boolean=false,shifted:Boolean=false,onClick:()->Unit,onLongClick:(()->Unit)?=null) {
    val c=LocalInstrument.current
    val interaction=remember{MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    if(key.type=="utility") {
        val bg=when(key.title){"SHIFT"->c.shift;"ALPHA"->c.alpha;"MODE"->c.accent;else->c.operator}
        val ink=if(key.title=="2nd"||key.title=="1st")c.ink else if(bg.luminance()>.45f)c.clearInk else Color.White
        val shape=RoundedCornerShape(8.dp)
        Box(modifier.padding(top=3.dp,bottom=3.dp).clip(shape).background(bg).background(if(pressed)pressedShade(bg) else Color.Transparent).border(if(active)2.dp else 1.dp,if(active)c.ink else c.muted.copy(alpha=.25f),shape).combinedClickable(interactionSource=interaction,indication=LocalIndication.current,onClick=onClick,onLongClick=onLongClick).semantics{contentDescription=key.title;stateDescription=if(active)"Active" else ""},contentAlignment=Alignment.Center) {
            Text(key.title,color=ink,fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=1)
        }
        return
    }
    val bg=when(key.type){"numeric"->c.numeric;"danger"->c.clearKey;"action"->c.operator;else->c.scientific}
    val ink=if(key.type=="danger")c.clearInk else c.ink
    val shiftedSymbol=if(shifted)when(key.alternate){"^()"->"xʸ";"^2"->"x²";"sqrt()"->"√";else->null} else null
    BoxWithConstraints(modifier.combinedClickable(interactionSource=interaction,indication=null,onClick=onClick,onLongClick=onLongClick).semantics(mergeDescendants=true){contentDescription=when(key.input){"TO_GRAPH"->"Graph current expression";"MATRIX_INPUT"->if(shifted)"Insert matrix, choose size" else "Insert 2 by 2 matrix";else->shiftedSymbol ?: if(shifted&&key.alternate.isNotBlank())if(key.alternate=="RELATION")"Insert equals" else key.alternate else key.title};stateDescription=if(active)"Active" else listOf(key.secondary,key.alpha).filter{it.isNotBlank()}.joinToString()}) {
        val labelHeight=(maxHeight*.25f).coerceAtMost(15.dp)
        val keyFont=(maxHeight.value*(when(key.type){"numeric","danger"->.44f;"action"->.24f;else->.32f})).coerceIn(10f,24f).sp
        val smallFont=(labelHeight.value*.66f).coerceIn(6f,if(key.secondary.length+key.alpha.length>10)7.5f else 10f).sp
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height(labelHeight),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                Text(if(key.type=="round")key.title else key.secondary,color=if(key.title=="ALPHA")c.alpha else c.shift,fontSize=smallFont,lineHeight=smallFont,maxLines=1)
                if(key.alpha.isNotBlank())Text("  "+when(key.alpha){"i"->"𝑖";"e"->"𝑒";else->key.alpha},color=c.alpha,fontSize=smallFont,lineHeight=smallFont,maxLines=1)
                if(key.type=="round"&&key.secondary.isNotBlank())Text(" "+key.secondary,color=c.shift,fontSize=7.sp,lineHeight=7.sp)
            }
            val shape=if(key.type=="round")CircleShape else RoundedCornerShape(topStart=7.dp,topEnd=7.dp,bottomStart=4.dp,bottomEnd=4.dp)
            Box(Modifier.then(if(key.type=="round")Modifier.aspectRatio(1f).weight(1f,false) else Modifier.fillMaxWidth().weight(1f)).clip(shape).background(Brush.verticalGradient(listOf(bg,bg.copy(alpha=.85f)))).background(if(pressed)pressedShade(bg) else Color.Transparent).border(if(active)2.dp else 1.dp,if(active)c.accent else c.muted.copy(alpha=.26f),shape),contentAlignment=Alignment.Center){
                if(key.title=="a/b")Column(horizontalAlignment=Alignment.CenterHorizontally){Text("□",color=ink,fontSize=9.sp,lineHeight=10.sp);Box(Modifier.width(15.dp).height(1.dp).background(ink));Text("□",color=ink,fontSize=9.sp,lineHeight=10.sp)}
                else if(key.input=="DEL") {
                    val iconSize=with(LocalDensity.current){keyFont.toDp()}
                    Canvas(Modifier.size(width=iconSize*4f/3f,height=iconSize)) {
                        scale(size.width/24f,size.height/18f,pivot=Offset.Zero){
                            drawPath(backspaceShape,ink,style=Stroke(width=1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                            drawPath(backspaceCross,ink,style=Stroke(width=2.2f,cap=StrokeCap.Round))
                        }
                    }
                }
                else if(key.title=="x□")Text(buildAnnotatedString{append("x");withStyle(SpanStyle(baselineShift=BaselineShift.Superscript,fontSize=(keyFont.value*.65f).sp)){append("□")}},color=ink,fontSize=keyFont)
                else if(key.title=="∫"){
                    val limitFont=(keyFont.value*.52f).coerceAtLeast(6f).sp
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("∫",color=ink,fontSize=keyFont,fontWeight=FontWeight.Medium)
                        Column(Modifier.padding(start=1.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("b",color=ink,fontSize=limitFont,lineHeight=limitFont)
                            Text("a",color=ink,fontSize=limitFont,lineHeight=limitFont)
                        }
                    }
                }
                else if(key.type!="round")Text(key.title,color=ink,fontSize=keyFont,fontWeight=FontWeight.Medium,maxLines=1)
                else Box(Modifier.fillMaxSize(.55f).border(1.dp,c.muted.copy(alpha=.25f),CircleShape))
            }
        }
    }
}

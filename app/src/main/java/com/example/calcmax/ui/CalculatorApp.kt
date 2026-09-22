package com.example.calcmax.ui

import android.content.Intent
import android.media.AudioManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.math.Editor
import com.example.calcmax.ui.theme.LocalInstrument
import org.json.JSONObject

val Modes=listOf("Scientific","CAS","Graph","Equations","Matrix","Vector","Statistics","Programmer","Units","Constants")

@Composable fun CalculatorApp(m: CalculatorModel) {
    val c=LocalInstrument.current
    var overlay by rememberSaveable { mutableStateOf("") }
    val workspaceState=rememberSaveableStateHolder()
    LaunchedEffect(m.mode) {m.save()}
    Column(Modifier.fillMaxSize().background(c.body).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("CALC MAX",fontWeight=FontWeight.ExtraBold,letterSpacing=3.sp,fontSize=19.sp,color=c.ink); Text("SCIENTIFIC  /  GRAPHING  /  CAS",fontSize=9.sp,letterSpacing=1.sp,color=c.muted) }
            TextButton(onClick={overlay="History"}) { Text("History") }
            TextButton(onClick={overlay="Settings"}) { Text("Setup") }
        }
        Row(Modifier.fillMaxWidth().background(c.scientific).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={overlay="Mode"}) { Text("${m.mode.uppercase()}  ▾",fontSize=12.sp) }
            Spacer(Modifier.weight(1f))
            Text(if(m.shift) "SHIFT ON  " else if(m.alpha) "ALPHA ON  " else "",color=if(m.shift)c.shift else c.alpha,fontSize=10.sp)
            TextButton(onClick={m.angle=when(m.angle) { "DEG"->"RAD"; "RAD"->"GRAD"; else->"DEG" };m.save()}) { Text(m.angle,fontSize=12.sp) }
            Text("${m.precision} digits",fontSize=10.sp,color=c.muted)
        }
        Box(Modifier.weight(1f)) {
            workspaceState.SaveableStateProvider(m.mode) {
            when(m.mode) {
                "Graph" -> GraphScreen(m)
                "Matrix","Vector" -> MatrixScreen(m)
                "Statistics" -> StatisticsScreen(m)
                "Programmer" -> ProgrammerScreen(m)
                "Units" -> UnitsScreen(m)
                "Constants" -> ConstantsScreen(m)
                else -> BoxWithConstraints {
                    if(maxWidth>650.dp && maxHeight<650.dp) Row {
                        Column(Modifier.weight(.6f).verticalScroll(rememberScrollState())) { Display(m); if(m.mode!="Scientific") Templates(m) }
                        Keypad(m,Modifier.weight(1.4f).verticalScroll(rememberScrollState()),{overlay=it},compact=true)
                    }
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Display(m)
                        if(m.mode!="Scientific") Templates(m)
                        Spacer(Modifier.height(6.dp))
                        Keypad(m,Modifier.fillMaxWidth(),{overlay=it})
                    }
                }
            }
            }
        }
        if(m.error.isNotBlank()) Row(Modifier.fillMaxWidth().background(c.danger.copy(alpha=.12f)).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(m.error,Modifier.weight(1f).semantics { liveRegion=LiveRegionMode.Assertive },color=c.danger,fontSize=12.sp)
            TextButton(onClick={m.error=""}) { Text("Dismiss") }
        }
    }
    when(overlay) {
        "Mode" -> AlertDialog(onDismissRequest={overlay=""},title={Text("Calculation mode")},text={Column(Modifier.verticalScroll(rememberScrollState())) { Modes.chunked(2).forEach { row -> Row { row.forEach { name -> TextButton(onClick={m.mode=name;overlay=""},modifier=Modifier.weight(1f)) { Text(name) } } } } }},confirmButton={TextButton(onClick={overlay=""}) { Text("Close") }})
        "Settings" -> SettingsDialog(m) { overlay="" }
        "History" -> HistoryDialog(m) { overlay="" }
        "Variables","STO","RCL" -> VariablesDialog(m,overlay) { overlay="" }
        "Catalog" -> CatalogDialog(m) {overlay=""}
    }
}

@Composable fun Display(m: CalculatorModel) {
    val c=LocalInstrument.current
    val clipboard=LocalClipboardManager.current
    val context=LocalContext.current
    var typing by rememberSaveable { mutableStateOf(false) }
    val keyboardFocus=remember { FocusRequester() }
    LaunchedEffect(typing) { if(!typing) keyboardFocus.requestFocus() }
    Column(Modifier.fillMaxWidth().background(c.display).padding(14.dp)) {
        Row { Text("MATH",fontSize=10.sp,color=c.muted,letterSpacing=2.sp); Spacer(Modifier.weight(1f)); SmallAction("Paste") { clipboard.getText()?.text?.let { m.insert(it) } }; TextButton(onClick={typing=!typing},modifier=Modifier.height(32.dp)) { Text(if(typing) "Keypad" else "Type / paste",fontSize=11.sp) } }
        if(typing) BasicTextField(
            value=TextFieldValue(m.editor.source,TextRange(m.editor.anchor.coerceIn(0,m.editor.source.length),m.editor.cursor.coerceIn(0,m.editor.source.length))),
            onValueChange={m.edit(Editor(it.text,it.selection.end,it.selection.start))},
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).onPreviewKeyEvent { if(it.type==KeyEventType.KeyUp && it.key==Key.Enter) { m.calculate();true } else false }.semantics { contentDescription="Expression input" },
            textStyle=TextStyle(color=c.ink,fontSize=22.sp,fontFamily=FontFamily.Monospace))
        else {
            val tree=remember(m.editor.source) { m.editor.tree() }
            Box(Modifier.fillMaxWidth().heightIn(min=56.dp).focusRequester(keyboardFocus).onKeyEvent { event ->
                if(event.type!=KeyEventType.KeyDown) false else when(event.key) {
                    Key.Enter,Key.NumPadEnter->{m.calculate();true}
                    Key.Backspace->{m.edit(m.editor.delete());true}
                    Key.Delete->{val e=m.editor; m.edit(if(e.cursor<e.source.length)e.copy(anchor=e.cursor+1).insert("") else e);true}
                    Key.DirectionLeft->{m.edit(m.editor.move(-1));true};Key.DirectionRight->{m.edit(m.editor.move(1));true}
                    Key.DirectionUp->{m.edit(m.editor.parent());true};Key.DirectionDown->{m.edit(m.editor.child());true}
                    else->{val ch=event.nativeKeyEvent.unicodeChar; if(ch>=32 && ch!=127) {m.insert(ch.toChar().toString());true} else false}
                }
            }.focusable().horizontalScroll(rememberScrollState()),contentAlignment=Alignment.CenterStart) {
                if(tree!=null) MathNode(JSONObject(tree.json()),24f,select={a,b->m.edit(Editor(m.editor.source,b,a))},selection=minOf(m.editor.anchor,m.editor.cursor)..maxOf(m.editor.anchor,m.editor.cursor))
                else Text(m.editor.source.ifBlank { "0" },fontSize=27.sp,fontFamily=FontFamily.Monospace,color=c.ink)
            }
            val pos=m.editor.cursor.coerceIn(0,m.editor.source.length)
            Text(m.editor.source.take(pos)+"│"+m.editor.source.drop(pos),Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription="Cursor at ${pos+1}" },fontSize=11.sp,color=c.muted,fontFamily=FontFamily.Monospace,maxLines=1)
        }
        HorizontalDivider(Modifier.padding(vertical=10.dp),color=c.grid)
        if(m.busy) Row(verticalAlignment=Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp); Text("  Computing locally…",Modifier.weight(1f),fontSize=12.sp); TextButton(onClick={m.cancel()}) { Text("Cancel") } }
        else {
            val r=m.result
            Box(Modifier.fillMaxWidth().heightIn(min=42.dp).horizontalScroll(rememberScrollState()).semantics(mergeDescendants=true) {liveRegion=LiveRegionMode.Polite;contentDescription=if(r==null)"Ready" else "Result: "+r.optString(if(m.decimal)"decimal" else "exact")},contentAlignment=Alignment.CenterEnd) {
                if(r==null) Text("Ready",fontSize=17.sp,color=c.muted)
                else if(!m.decimal && r.optJSONObject("tree")!=null) MathNode(r.getJSONObject("tree"),28f)
                else Text(r.optString(if(m.decimal) "decimal" else "exact"),fontSize=24.sp,color=c.ink,fontFamily=FontFamily.Serif)
            }
            if(r!=null) {
                val note=r.optString("note"); if(note.isNotBlank()) Text(note,color=c.muted,fontSize=11.sp)
                val conditions=r.optJSONArray("conditions"); if(conditions!=null && conditions.length()>0) Text("Domain: "+(0 until conditions.length()).joinToString { conditions.getString(it) },fontSize=11.sp,color=c.muted)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    SmallAction(if(m.decimal || r.optBoolean("approximate")) "≈ DEC" else "= EXACT") { m.decimal=!m.decimal }
                    SmallAction("Copy") { clipboard.setText(AnnotatedString(r.optString(if(m.decimal) "decimal" else "exact"))) }
                    if(m.variables.has("Ans")) SmallAction("Insert") { m.insert("Ans") }
                    SmallAction("Factor") { m.transform("factor") }
                    SmallAction("Expand") { m.transform("expand") }
                    SmallAction("Simplify") { m.transform("simplify") }
                    SmallAction("Graph") { m.graphSource=m.editor.source;m.mode="Graph";m.plot() }
                    SmallAction("Share") { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,m.editor.source+" = "+r.optString("exact")),"Share calculation")) }
                }
            }
        }
    }
}

data class KeySpec(val title: String,val input: String=title,val secondary: String="",val alternate: String="",val alpha: String="",val type: String="scientific")
@Composable fun Keypad(m: CalculatorModel,modifier: Modifier=Modifier,open: (String)->Unit,compact: Boolean=false) {
    val c=LocalInstrument.current
    val haptic=LocalHapticFeedback.current
    val context=LocalContext.current
    fun feedback() { if(m.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); if(m.sound) (context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEY_CLICK) }
    fun insert(value: String) {
        val cursor=value.indexOf('(')
        if(value.endsWith("()") && cursor>=0) m.insert(value,cursor+1) else m.insert(value)
    }
    Column(modifier.padding(horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf("SHIFT","ALPHA","◀","▶","MODE","SETUP").forEach { label ->
                InstrumentKey(KeySpec(label,type="navigation"),Modifier.weight(1f),active=label=="SHIFT"&&m.shift || label=="ALPHA"&&m.alpha) { feedback(); when(label) {
                    "SHIFT"->{m.shift=!m.shift;m.alpha=false}; "ALPHA"->{m.alpha=!m.alpha;m.shift=false}; "◀"->m.edit(m.editor.move(-1)); "▶"->m.edit(m.editor.move(1)); "MODE"->open("Mode"); else->open("Settings")
                } }
            }
        }
        val scientific=listOf(
            listOf(KeySpec("a/b","()/()","mixed","mixed(,,)","A"),KeySpec("√","sqrt()","³√","cbrt()","B"),KeySpec("x²","^2","x³","^3","C"),KeySpec("xʸ","^","ⁿ√","nthroot(,)","D"),KeySpec("log","log()","10ˣ","10^","E"),KeySpec("ln","ln()","eˣ","exp()","F")),
            listOf(KeySpec("sin","sin()","asin","asin()","X"),KeySpec("cos","cos()","acos","acos()","Y"),KeySpec("tan","tan()","atan","atan()","M"),KeySpec("(",secondary="[",alternate="[",alpha="x"),KeySpec(")",secondary="]",alternate="]",alpha="y"),KeySpec(",",secondary="=",alternate="=",alpha="i")),
            listOf(KeySpec("∫","integrate(,x)","d/dx","diff(,x)","x"),KeySpec("π","pi","e","e"),KeySpec("x!","!","nCr","nCr(,)"),KeySpec("STO",secondary="RCL",alternate="RCL"),KeySpec("↑",secondary="↓",alternate="↓"),KeySpec("S⇔D",secondary="CATALOG",alternate="Catalog"))
        )
        @Composable fun ScientificRows() { scientific.forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) { row.forEach { k -> InstrumentKey(k,Modifier.weight(1f)) {
            feedback()
            val value=if(m.alpha&&k.alpha.isNotBlank()) k.alpha else if(m.shift&&k.alternate.isNotBlank()) k.alternate else k.input
            when(value) { "STO","RCL","Catalog"->open(value); "S⇔D"->m.decimal=!m.decimal; "↑"->m.edit(m.editor.parent()); "↓"->m.edit(m.editor.child()); "()/()"->m.insert(value,1); "mixed(,,)"->m.insert(value,6); "integrate(,x)"->m.insert(value,10); "diff(,x)"->m.insert(value,5); "nCr(,)"->m.insert(value,4); "nthroot(,)"->m.insert(value,8); else->insert(value) }
            m.shift=false;m.alpha=false
        } } } } }
        val numbers=listOf(listOf("7","8","9","DEL","AC"),listOf("4","5","6","×","÷"),listOf("1","2","3","+","−"),listOf("0",".","EXP","Ans","="))
        @Composable fun NumericRows() { numbers.forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { row.forEach { label ->
            InstrumentKey(KeySpec(label,type=if(label=="=")"execute" else if(label in listOf("DEL","AC"))"danger" else if(label in listOf("×","÷","+","−"))"operator" else "numeric"),Modifier.weight(1f)) {
                feedback();when(label) { "="->m.calculate(); "DEL"->m.edit(m.editor.delete()); "AC"->m.clear(); "EXP"->m.insert("*10^"); else->m.insert(label) }
            }
        } } } }
        if(compact) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1.15f),verticalArrangement=Arrangement.spacedBy(5.dp)) {ScientificRows()}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {NumericRows()}
        } else {ScientificRows();NumericRows()}
        if(!compact) Text("OFFLINE MATHEMATICS  ·  NATURAL DISPLAY",Modifier.align(Alignment.CenterHorizontally).padding(vertical=6.dp),fontSize=9.sp,letterSpacing=1.sp,color=c.muted)
    }
}

@Composable fun InstrumentKey(key: KeySpec,modifier: Modifier=Modifier,active: Boolean=false,onClick: ()->Unit) {
    val c=LocalInstrument.current
    val bg=when(key.type) { "numeric"->c.numeric; "operator"->c.operator; "execute"->c.accent; else->c.scientific }
    val fg=when { key.type=="execute"->c.display; key.type=="danger"->c.danger; key.title=="SHIFT"->c.shift; key.title=="ALPHA"->c.alpha; else->c.ink }
    Surface(onClick=onClick,modifier=modifier.heightIn(min=48.dp).semantics { contentDescription=key.title+(if(key.secondary.isNotBlank()) ", shift ${key.secondary}" else "")+(if(active) ", active" else "") },shape=RoundedCornerShape(7.dp),color=bg,shadowElevation=1.dp,border=if(active) BorderStroke(2.dp,fg) else null) {
        Column(Modifier.padding(vertical=4.dp,horizontal=2.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            if(key.secondary.isNotBlank() || key.alpha.isNotBlank()) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) { Text(key.secondary,color=c.shift,fontSize=9.sp); if(key.alpha.isNotBlank()) Text(key.alpha,color=c.alpha,fontSize=9.sp) }
            Text(key.title,color=fg,fontSize=if(key.type in listOf("numeric","operator","execute")) 23.sp else if(key.type=="navigation") 10.sp else 16.sp,fontWeight=FontWeight.Medium,maxLines=1)
        }
    }
}
@Composable fun SmallAction(text: String,action: ()->Unit) { TextButton(onClick=action,contentPadding=PaddingValues(horizontal=9.dp,vertical=0.dp)) { Text(text,fontSize=11.sp) } }
@Composable fun Templates(m: CalculatorModel) {
    val examples=if(m.mode=="Equations") listOf("Quadratic" to "solve(x^2-5x+6=0,x)","System" to "solve([x+y=3,x-y=1],[x,y])","Inequality" to "solve(x^2<4,x)","Numerical" to "nsolve(cos(x)-x,x,0,1)") else listOf("Simplify" to "simplify((x^2-1)/(x-1))","Factor" to "factor(x^4-1)","Expand" to "expand((x+1)^3)","Derivative" to "diff(sin(x^2),x)","Integral" to "integrate(x^2*exp(x),x)","Limit" to "limit(sin(x)/x,x,0)","Series" to "series(exp(x),x,0,6)","Sum" to "sum(x^2,x,1,10)")
    Row(Modifier.horizontalScroll(rememberScrollState())) { examples.forEach { (label,source)->SmallAction(label) { m.edit(Editor(source)) } } }
}

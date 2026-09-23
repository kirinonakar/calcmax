package com.example.calcmax.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.math.Editor
import com.example.calcmax.ui.theme.LocalInstrument
import org.json.JSONObject

val Modes=listOf("Scientific","CAS","Graph","Equations","Matrix","Vector","Statistics","Programmer","Units","Constants","Tip","Currency")
@Composable fun CalculatorApp(m:CalculatorModel) {
    val c=LocalInstrument.current
    var overlay by rememberSaveable {mutableStateOf("")}
    val workspaces=rememberSaveableStateHolder()
    LaunchedEffect(m.mode){m.save()}
    Column(Modifier.fillMaxSize().background(c.body).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("CALC MAX",Modifier.weight(1f),fontWeight=FontWeight.ExtraBold,letterSpacing=2.sp,fontSize=18.sp,color=c.ink)
            SmallAction("History"){overlay="History"};SmallAction("Catalog"){overlay="Catalog"};SmallAction("Setup"){overlay="Settings"}
        }
        Row(Modifier.fillMaxWidth().height(48.dp).zIndex(2f).background(c.scientific).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={overlay="Mode"},modifier=Modifier.weight(1f).fillMaxHeight().semantics{contentDescription="Choose calculation mode"},contentPadding=PaddingValues(horizontal=8.dp)) {Text(m.mode.uppercase()+" ▾",Modifier.fillMaxWidth(),fontSize=12.sp,color=c.ink)}
            if(m.variables.has("M"))Text("M  ",fontSize=10.sp,color=c.muted,modifier=Modifier.semantics{contentDescription="Stored memory"})
            Text(if(m.shift)"SHIFT  " else if(m.alpha)"ALPHA  " else if(m.hyperbolic)"HYP  " else if(m.secondKeys)"2ND  " else "",fontSize=10.sp,color=if(m.alpha)c.alpha else c.shift)
            Text(m.angle,Modifier.clickable {m.angle=when(m.angle){"DEG"->"RAD";"RAD"->"GRAD";else->"DEG"};m.recalculatePreview();m.save()}.padding(horizontal=12.dp),fontSize=11.sp,color=c.accent)
            Text("≤ ${m.precision} digits",fontSize=10.sp,color=c.muted)
        }
        Box(Modifier.weight(1f)) {workspaces.SaveableStateProvider(m.mode) {
            when(m.mode) {
                "Graph"->GraphScreen(m)
                "Matrix","Vector"->MatrixScreen(m)
                "Statistics"->StatisticsScreen(m)
                "Programmer"->ProgrammerScreen(m)
                "Units"->UnitsScreen(m)
                "Constants"->ConstantsScreen(m)
                "Tip"->TipScreen()
                "Currency"->CurrencyScreen(m)
                else->BoxWithConstraints(Modifier.fillMaxSize()) {
                    val keyboardHeight=minOf(520.dp,maxHeight*.68f)
                    if(maxWidth>650.dp && maxHeight<500.dp) Row(Modifier.fillMaxSize()) {
                        CalculationTape(m,Modifier.weight(1f))
                        Keypad(m,Modifier.weight(1f).fillMaxHeight(),{overlay=it})
                    } else Column(Modifier.fillMaxSize()) {
                        CalculationTape(m,Modifier.weight(1f))
                        Keypad(m,Modifier.fillMaxWidth().height(keyboardHeight),{overlay=it})
                    }
                }
            }
        }}
        if(m.mode !in listOf("Scientific","CAS","Equations") && m.error.isNotBlank()) Text(m.error,Modifier.fillMaxWidth().padding(8.dp),fontSize=12.sp,color=c.danger)
    }
    when(overlay) {
        "Mode"->AlertDialog(onDismissRequest={overlay=""},title={Text("Calculation mode")},text={Column {Modes.chunked(2).forEach {row->Row {row.forEach {name->TextButton(onClick={m.mode=name;overlay=""},modifier=Modifier.weight(1f)){Text(name)}}}}}},confirmButton={TextButton(onClick={overlay=""}){Text("Close")}})
        "Settings"->SettingsDialog(m){overlay=""}
        "History"->HistoryDialog(m){overlay=""}
        "Variables","STO","RCL"->VariablesDialog(m,overlay){overlay=""}
        "Catalog"->CatalogDialog(m){overlay=""}
        "Angle"->AlertDialog(onDismissRequest={overlay=""},title={Text("DRG · input angle unit")},text={Column {listOf("Degrees °" to "degree","Radians ʳ" to "rad","Gradians ᵍ" to "gradian").forEach{(label,function)->TextButton(onClick={m.angleSuffix(function);overlay=""}){Text(label)}}}},confirmButton={TextButton(onClick={overlay=""}){Text("Close")}})
        "Clear"->AlertDialog(onDismissRequest={overlay=""},title={Text("Clear")},text={Column {
            TextButton(onClick={m.resetSetup();overlay=""}){Text("1 · Setup")}
            TextButton(onClick={m.clearMemory();overlay=""}){Text("2 · Memory")}
            TextButton(onClick={m.resetSetup();m.clearMemory();m.clearHistory();overlay=""}){Text("3 · All")}
        }},confirmButton={TextButton(onClick={overlay=""}){Text("Close")}})
    }
}

@Composable fun CalculationTape(m:CalculatorModel,modifier:Modifier=Modifier) {
    val c=LocalInstrument.current
    val scroll=rememberLazyListState()
    LaunchedEffect(m.inputVersion,m.tape.size){scroll.scrollToItem(0)}
    LazyColumn(modifier.fillMaxWidth().background(c.display).semantics {contentDescription="Calculation history, swipe vertically"},state=scroll,reverseLayout=true) {
        item(key="active") {Display(m)}
        items(m.tape.asReversed()) {entry->
            Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp)) {
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clickable {m.reuse(entry)}) {MathNode(JSONObject(entry.input),21f)}
                val response=remember(entry.result){JSONObject(entry.result)}
                Box(Modifier.fillMaxWidth().padding(top=6.dp).horizontalScroll(rememberScrollState()),contentAlignment=Alignment.CenterEnd) {ResultMath(response,m.decimal,23f)}
                if(domainText(response).isNotEmpty())Text(domainText(response),fontSize=10.sp,color=c.muted)
                HorizontalDivider(Modifier.padding(top=10.dp),color=c.grid)
            }
        }
    }
}

@Composable fun Display(m:CalculatorModel) {
    val c=LocalInstrument.current
    val clipboard=LocalClipboardManager.current
    val context=LocalContext.current
    var typing by rememberSaveable {mutableStateOf(false)}
    val focus=remember {FocusRequester()}
    LaunchedEffect(typing,m.poweredOn){if(!typing&&m.poweredOn)focus.requestFocus()}
    Column(Modifier.fillMaxWidth().background(c.display).padding(horizontal=14.dp,vertical=4.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(m.committed)"=" else "MATH",fontSize=10.sp,color=c.muted,letterSpacing=1.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick={clipboard.getText()?.text?.let{m.insert(it)}},modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=8.dp)){Text("Paste",fontSize=11.sp)}
            TextButton(onClick={typing=!typing},modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=8.dp)){Text(if(typing)"Math input" else "Keyboard",fontSize=11.sp)}
        }
        if(!m.poweredOn) Box(Modifier.fillMaxWidth().height(66.dp),contentAlignment=Alignment.Center){Text("OFF · press 2nd to resume",color=c.muted)}
        else if(typing) BasicTextField(
            value=TextFieldValue(m.editor.source,TextRange(m.editor.anchor.coerceIn(0,m.editor.source.length),m.editor.cursor.coerceIn(0,m.editor.source.length))),
            onValueChange={
                if(m.committed&&it.text!=m.editor.source){
                    if(it.text.startsWith(m.editor.source))m.insert(it.text.removePrefix(m.editor.source))
                    else {m.fresh();m.edit(Editor(it.text,it.selection.end,it.selection.start))}
                }else m.edit(Editor(it.text,it.selection.end,it.selection.start))
            },
            modifier=Modifier.fillMaxWidth().heightIn(min=60.dp).onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.Enter){m.calculate();true}else false}.semantics{contentDescription="Expression input"},
            textStyle=TextStyle(color=c.ink,fontSize=22.sp,fontFamily=FontFamily.Monospace))
        else Box(Modifier.fillMaxWidth().heightIn(min=60.dp).focusRequester(focus).onKeyEvent{event->
            if(event.type!=KeyEventType.KeyDown)false else when(event.key){
                Key.Enter,Key.NumPadEnter->{m.calculate();true}
                Key.Backspace->{m.edit(m.editor.delete());true}
                Key.Delete->{val e=m.editor;m.edit(if(e.cursor<e.source.length)e.copy(anchor=e.cursor+1).insert("")else e);true}
                Key.DirectionLeft->{m.edit(m.editor.move(-1));true};Key.DirectionRight->{m.edit(m.editor.move(1));true}
                Key.DirectionUp->{m.edit(m.editor.parent());true};Key.DirectionDown->{m.edit(m.editor.child());true}
                else->{val ch=event.nativeKeyEvent.unicodeChar;if(ch>=32&&ch!=127){m.insert(ch.toChar().toString());true}else false}
            }
        }.focusable().horizontalScroll(rememberScrollState()).semantics{contentDescription="Current expression"},contentAlignment=Alignment.CenterStart){
            Row(verticalAlignment=Alignment.CenterVertically){
                val tree=remember(m.editor,m.answerDisplay){m.inputTree()}
                CompositionLocalProvider(LocalMathCursorTarget provides if(m.committed)null else m.editor.cursorTarget(),LocalMathAfter provides {a,b->m.edit(m.editor.after(a,b))}) {
                    if(m.editor.source.isBlank())Text("│",fontSize=25.sp,color=c.accent)
                    else if(tree!=null)MathNode(tree,25f,select={a,b->m.edit(m.editor.selectRange(a,b))},selection=minOf(m.editor.anchor,m.editor.cursor)..maxOf(m.editor.anchor,m.editor.cursor))
                    else Text(m.editor.source,fontSize=25.sp,color=c.ink)
                }
                Box(Modifier.width(32.dp).heightIn(min=48.dp).clickable{m.edit(Editor(m.editor.source))}.semantics{contentDescription="After expression"},contentAlignment=Alignment.CenterStart){
                    if(tree==null&&m.editor.source.isNotBlank()&&!m.committed&&m.editor.cursor==m.editor.source.length)Text("│",color=c.accent,fontSize=25.sp)
                }
            }
        }
        // This answer region is always present, including while a worker is computing.
        Box(Modifier.fillMaxWidth().heightIn(min=56.dp).horizontalScroll(rememberScrollState()).semantics(mergeDescendants=true){contentDescription="Answer panel";liveRegion=LiveRegionMode.Polite},contentAlignment=Alignment.CenterEnd){
            if(m.result!=null&&m.poweredOn)ResultMath(m.result!!,m.decimal,28f,m.mixedNumbers) else Text(" ",fontSize=28.sp)
        }
        Row(Modifier.fillMaxWidth().height(24.dp),verticalAlignment=Alignment.CenterVertically){
            Text(when{m.error.isNotBlank()->m.error;m.busy->"Computing…";m.previewBusy->"Calculating…";domainText(m.result).isNotBlank()->domainText(m.result);m.committed->"Next input starts a new calculation";else->m.result?.optString("note") ?: ""},Modifier.weight(1f),fontSize=10.sp,maxLines=1,color=if(m.error.isNotBlank())c.danger else c.muted)
            if(m.busy||m.previewBusy)Text("Cancel",Modifier.clickable{m.cancel()}.padding(start=8.dp),fontSize=10.sp,color=c.accent)
        }
        Row(Modifier.fillMaxWidth().height(36.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
            SmallAction(if(m.decimal)"≈ Decimal" else "Exact"){m.decimal=!m.decimal}
            SmallAction("Copy"){m.result?.let{clipboard.setText(AnnotatedString(it.optString(if(m.decimal)"decimal" else "exact")))}}
            SmallAction("∫"){m.insert("integrate(,x)",10)}
            SmallAction("∫ₐᵇ"){m.insert("integrate(,x,,)",10)}
            SmallAction("d/dx"){m.insert("diff(,x)",5)}
            SmallAction("f′(a)"){m.insert("nderivative(,x,)",12)}
            SmallAction("Share"){m.result?.let{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,m.editor.source+" = "+it.optString("exact")),"Share calculation"))}}
        }
    }
}

@Composable fun ResultMath(result:JSONObject,decimal:Boolean,size:Float,mixed:Boolean=false) {
    var tree=result.optJSONObject(if(decimal)"decimalTree" else "tree") ?: result.optJSONObject("tree")
    if(mixed&&!decimal&&tree?.optString("kind")=="fraction") {
        val fraction=tree
        tree=runCatching {
            val numerator=fraction!!.getJSONArray("args").getJSONObject(0).getString("value").toBigInteger()
            val denominator=fraction.getJSONArray("args").getJSONObject(1).getString("value").toBigInteger()
            val parts=numerator.abs().divideAndRemainder(denominator)
            if(parts[0].signum()==0)fraction else JSONObject().put("kind","call").put("value","mixed").put("args",org.json.JSONArray(listOf(parts[0]*numerator.signum().toBigInteger(),parts[1],denominator).map {JSONObject().put("kind","number").put("value",it.toString())}))
        }.getOrDefault(tree)
    }
    if(tree!=null)MathNode(tree,size) else Text(result.optString(if(decimal)"decimal" else "exact"),fontSize=size.sp,color=LocalInstrument.current.ink,fontFamily=FontFamily.Serif)
}
private fun domainText(result:JSONObject?):String {
    val conditions=result?.optJSONArray("conditions") ?: return ""
    return if(conditions.length()==0)"" else "Domain: "+(0 until conditions.length()).joinToString{conditions.getString(it)}
}
@Composable fun SmallAction(text:String,action:()->Unit){TextButton(onClick=action,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)){Text(text,fontSize=11.sp)}}
@Composable fun Templates(m:CalculatorModel){Row(Modifier.horizontalScroll(rememberScrollState())){listOf("Factor" to "factor(x^4-1)","Solve" to "solve(x^2-5x+6=0,x)","Derivative" to "diff(sin(x^2),x)","Integral" to "integrate(x^2*exp(x),x)").forEach{(label,source)->SmallAction(label){m.edit(Editor(source))}}}}

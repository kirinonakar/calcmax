package com.kirinonakar.calcmax.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.calculator.ResultDisplayMode
import com.kirinonakar.calcmax.calculator.TapeEntry
import com.kirinonakar.calcmax.math.Editor
import com.kirinonakar.calcmax.math.BracketAutoClose
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import org.json.JSONObject
import kotlinx.coroutines.delay
import com.kirinonakar.calcmax.math.Lexer
import com.kirinonakar.calcmax.math.Expr

val Modes=listOf("Scientific/CAS","Graph","Python","Equations","Matrix","Vector","Statistics","Programmer","Units","Constants","Tip","Currency","Functions")
private val LocalCalculatorOverlay=staticCompositionLocalOf<(String)->Unit> { {} }
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun CalculatorApp(m:CalculatorModel) {
    val c=LocalInstrument.current
    var overlay by rememberSaveable {mutableStateOf("")}
    var screenExpanded by rememberSaveable {mutableStateOf(false)}
    val workspaces=rememberSaveableStateHolder()
    LaunchedEffect(m.mode){m.save()}
    CompositionLocalProvider(LocalCalculatorOverlay provides {overlay=it}, LocalLanguage provides m.language) {
    Column(Modifier.fillMaxSize().background(c.body).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("CalcMax",Modifier.weight(1f),fontWeight=FontWeight.ExtraBold,letterSpacing=2.sp,fontSize=18.sp,color=c.ink)
            SmallAction("History"){overlay="History"};SmallAction("Catalog"){overlay="Catalog"};SmallAction("Setup"){overlay="Settings"}
        }
        Row(Modifier.fillMaxWidth().height(48.dp).zIndex(2f).background(c.scientific).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight().combinedClickable(onClick={overlay="Mode"},onLongClick={m.mode="Scientific/CAS"},onLongClickLabel="Go to Scientific/CAS mode").semantics{contentDescription="Choose calculation mode, long press for Scientific/CAS mode"},contentAlignment=Alignment.CenterStart) {
                Text(m.mode.uppercase()+" ▾",Modifier.fillMaxWidth().padding(horizontal=8.dp),fontSize=12.sp,color=c.ink)
            }
            if(m.variables.has("M"))Text("M  ",fontSize=10.sp,color=c.muted,modifier=Modifier.semantics{contentDescription="Stored memory"})
            Text(if(m.overwrite)"OVR  " else "INS  ",fontSize=10.sp,color=if(m.overwrite)c.accent else c.muted,fontWeight=if(m.overwrite)FontWeight.Bold else FontWeight.Normal,modifier=Modifier.clickable{m.overwrite=!m.overwrite}.semantics{contentDescription=if(m.overwrite)"Overwrite mode" else "Insert mode"})
            if(m.mixedNumbers)Text("mix  ",fontSize=10.sp,color=c.accent,modifier=Modifier.semantics{contentDescription="Mixed numbers"})
            Text(if(m.shift)"SHIFT  " else if(m.alpha)"ALPHA  " else if(m.hyperbolic)"HYP  " else if(m.secondKeys)"2ND  " else "",fontSize=10.sp,color=if(m.alpha)c.alpha else c.shift)
            Text(m.angle,Modifier.clickable {m.angle=when(m.angle){"DEG"->"RAD";"RAD"->"GRAD";else->"DEG"};m.recalculatePreview();m.save()}.padding(horizontal=12.dp),fontSize=11.sp,color=c.accent)
            Text("≤ ${m.displayDigits} digits",fontSize=10.sp,color=c.muted)
        }
        Box(Modifier.weight(1f)) {workspaces.SaveableStateProvider(m.mode) {
            when(m.mode) {
                "Equations"->EquationScreen(m)
                "Functions"->FunctionsScreen(m)
                "Graph"->GraphScreen(m)
                "Python"->PythonScreen(m)
                "Matrix","Vector"->MatrixScreen(m)
                "Statistics"->StatisticsScreen(m)
                "Programmer"->ProgrammerScreen(m)
                "Units"->UnitsScreen(m)
                "Constants"->ConstantsScreen(m)
                "Tip"->TipScreen()
                "Currency"->CurrencyScreen(m)
                else->BoxWithConstraints(Modifier.fillMaxSize()) {
                    val keyboardHeight=minOf(520.dp,maxHeight*.68f)
                    val landscape=maxWidth>650.dp && maxHeight<500.dp
                    val fullKeypadHeight=if(landscape)maxHeight else keyboardHeight
                    // Keep each numeric row the same height after removing the upper keypad rows.
                    val numericRowHeight=(fullKeypadHeight-36.dp)/9f
                    val numericKeypadHeight=numericRowHeight*4f+20.dp
                    if(landscape) Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.Bottom) {
                        CalculationTape(m,Modifier.weight(1f).fillMaxHeight(),screenExpanded){screenExpanded=!screenExpanded}
                        Keypad(m,Modifier.weight(if(screenExpanded) .7f else 1f).height(if(screenExpanded)numericKeypadHeight else fullKeypadHeight),screenExpanded,numericRowHeight,{overlay=it})
                    } else Column(Modifier.fillMaxSize()) {
                        CalculationTape(m,Modifier.weight(1f),screenExpanded){screenExpanded=!screenExpanded}
                        Keypad(m,Modifier.fillMaxWidth().height(if(screenExpanded)numericKeypadHeight else fullKeypadHeight),screenExpanded,numericRowHeight,{overlay=it})
                    }
                }
            }
        }}
        if(m.mode !in listOf("Scientific/CAS","Equations") && m.error.isNotBlank()) Text(m.error,Modifier.fillMaxWidth().padding(8.dp),fontSize=12.sp,color=c.danger)
    }
    when(overlay) {
        "Mode"->AlertDialog(onDismissRequest={overlay=""},title={Text(tr("Calculation mode"))},text={Column(Modifier.verticalScroll(rememberScrollState())) {Modes.chunked(2).forEach {row->Row {row.forEach {name->TextButton(onClick={m.mode=name;overlay=""},modifier=Modifier.weight(1f)){Text(name)}}}}}},confirmButton={TextButton(onClick={overlay=""}){Text(tr("Close"))}})
        "Settings"->SettingsDialog(m){overlay=""}
        "MatrixSize"->MatrixSizeDialog(m){overlay=""}
        "History"->HistoryDialog(m){overlay=""}
        "RCL"->RecallDialog(m){overlay=""}
        "Variables","STO"->VariablesDialog(m){overlay=""}
        "Catalog"->CatalogDialog(m){overlay=""}
        "Angle"->AlertDialog(onDismissRequest={overlay=""},title={Text(if(isKorean()) "DRG · 입력 각도 단위" else "DRG · input angle unit")},text={Column {listOf("Degrees °" to "degree","Radians ʳ" to "rad","Gradians ᵍ" to "gradian").forEach{(label,function)->TextButton(onClick={m.angleSuffix(function);overlay=""}){Text(label)}}}},confirmButton={TextButton(onClick={overlay=""}){Text(tr("Close"))}})
        "Clear"->AlertDialog(onDismissRequest={overlay=""},title={Text(tr("Clear"))},text={Column {
            TextButton(onClick={m.resetSetup();overlay=""}){Text("1 · Setup")}
            TextButton(onClick={m.clearMemory();overlay=""}){Text(if(isKorean()) "2 · 메모리" else "2 · Memory")}
            TextButton(onClick={m.resetSetup();m.clearMemory();m.clearHistory();overlay=""}){Text(if(isKorean()) "3 · 모두" else "3 · All")}
        }},confirmButton={TextButton(onClick={overlay=""}){Text(tr("Close"))}})
    }
    }
}

@Composable fun CalculationTape(m:CalculatorModel,modifier:Modifier=Modifier,screenExpanded:Boolean=false,onToggleScreen:(()->Unit)?=null) {
    val c=LocalInstrument.current
    // Plain scrolling is deliberate here: the tape never holds more than ten history rows, and
    // staying away from LazyColumn keeps fling-time LazyLayout item-reuse crashes out of reach.
    val scroll=rememberScrollState()
    var typing by rememberSaveable {mutableStateOf(false)}
    // The active item stays composed; focus is handed over only between gestures, so a swipe
    // never pulls the tape back to the newest line mid-drag.
    var focusPending by remember {mutableStateOf(true)}
    LaunchedEffect(m.calcSession!=null){if(m.calcSession!=null){typing=false;focusPending=true}}
    LaunchedEffect(scroll){
        // Gesture state is observed outside composition so a swipe never recomposes the tape or its items.
        snapshotFlow{scroll.isScrollInProgress}.collect{if(it)focusPending=false}
    }
    LaunchedEffect(m.inputVersion,m.tape.size){
        // Let an in-flight drag or fling finish before moving the tape programmatically.
        while(scroll.isScrollInProgress)delay(16)
        if(scroll.value!=0)scroll.scrollTo(0)
    }
    Column(modifier.fillMaxWidth().background(c.display)) {
        DisplayToolbar(m,typing){typing=!typing;if(!typing)focusPending=true}
        Column(Modifier.fillMaxWidth().weight(1f)) {
            // The spacer bottom-anchors the tape: with reverseScrolling, 0 is the newest line at the
            // bottom, so a short tape stays glued to the keypad instead of floating at the top.
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().verticalScroll(scroll,reverseScrolling=true).semantics {contentDescription="Calculation history, swipe vertically"}) {
                m.tape.forEach {entry->key(entry.id){TapeEntryRow(entry,m)}}
                key("active") {DisplayContent(m,typing,focusPending,scroll){focusPending=false}}
            }
        }
        DisplayActions(m,screenExpanded,onToggleScreen)
    }
}

@Composable private fun TapeEntryRow(entry:TapeEntry,m:CalculatorModel) {
    val c=LocalInstrument.current
    Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp)) {
        val input=remember(entry.input){runCatching {JSONObject(entry.input)}.getOrNull()}
        val response=remember(entry.result){runCatching {JSONObject(entry.result)}.getOrNull()}
        val compact=remember(entry.input,entry.result) {
            input==null||response==null||entry.source.length>800||entry.result.length>20_000||response.optString("exact").contains('\n')||
                largeHistoryTree(input)||largeHistoryTree(response.optJSONObject("tree"))||largeHistoryTree(response.optJSONObject("decimalTree"))
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clickable {m.reuse(entry)}
            .semantics {contentDescription="Reuse calculation: ${entry.source.take(120)}"}) {
            if(!compact&&input!=null)MathNode(input,m.inputFont*.84f)
            else Text(entry.source.take(800),fontSize=(m.inputFont*.84f).sp,fontFamily=FontFamily.Monospace,color=c.ink,maxLines=4,overflow=TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().padding(top=6.dp).horizontalScroll(rememberScrollState()),contentAlignment=Alignment.CenterEnd) {
            if(!compact&&response!=null)ResultMath(response,m.decimal,m.outputFont*.82f,
                displayMode=m.resultDisplayMode,thousandsSeparator=m.thousandsSeparator,dmsDisplay=response.optBoolean("dms"),displayDigits=m.displayDigits)
            else Text(response?.optString(if(m.decimal)"decimal" else "exact").orEmpty().ifBlank {entry.result}.take(1200),
                fontSize=(m.outputFont*.72f).sp,color=c.ink,maxLines=8,overflow=TextOverflow.Ellipsis)
        }
        if(response!=null&&domainText(response).isNotEmpty())Text(domainText(response),fontSize=10.sp,color=c.muted)
        HorizontalDivider(Modifier.padding(top=10.dp),color=c.grid)
    }
}

private fun largeHistoryTree(root:JSONObject?,compactStructured:Boolean=true):Boolean {
    if(root==null)return false
    val pending=ArrayDeque<JSONObject>()
    pending.add(root)
    var count=0
    while(pending.isNotEmpty()) {
        val node=pending.removeLast()
        if((compactStructured&&node.optString("kind") in setOf("matrix","rows"))||++count>120)return true
        val args=node.optJSONArray("args")
        for(index in 0 until (args?.length() ?: 0))args?.optJSONObject(index)?.let(pending::add)
    }
    return false
}

@Composable fun Display(m:CalculatorModel,screenExpanded:Boolean=false,onToggleScreen:(()->Unit)?=null,requestInitialFocus:Boolean=true) {
    val c=LocalInstrument.current
    var typing by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(m.calcSession!=null){if(m.calcSession!=null)typing=false}
    Column(Modifier.fillMaxWidth().background(c.display).padding(vertical=4.dp)) {
        DisplayToolbar(m,typing){typing=!typing}
        DisplayContent(m,typing,requestInitialFocus)
        DisplayActions(m,screenExpanded,onToggleScreen)
    }
}

@Composable fun DisplayToolbar(m:CalculatorModel,typing:Boolean,onToggleTyping:()->Unit) {
    val c=LocalInstrument.current
    val clipboard=LocalClipboardManager.current
    var copyExpression by remember{mutableStateOf(false)}
    // The Copy button cycles answer → expression; a new result or input starts over at the answer.
    LaunchedEffect(m.result,m.editor.source){copyExpression=false}
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(if(m.committed)"=" else "MATH",fontSize=10.sp,color=c.muted,letterSpacing=1.sp)
        Spacer(Modifier.weight(1f))
        TextButton(onClick={m.undo()},enabled=m.canUndo,modifier=Modifier.height(36.dp).semantics{contentDescription="Undo last input"},contentPadding=PaddingValues(horizontal=8.dp)){Text("Undo",fontSize=11.sp)}
        val selection=m.editor.source.substring(minOf(m.editor.anchor,m.editor.cursor),maxOf(m.editor.anchor,m.editor.cursor))
        val copyTarget=CopyCycle.next(m.result?.optString(if(m.decimal)"decimal" else "exact"),m.editor.source,copyExpression,selection)
        TextButton(onClick={
            val start=minOf(m.editor.anchor,m.editor.cursor)
            clipboard.setText(AnnotatedString(selection))
            m.edit(Editor(m.editor.source.removeRange(start,start+selection.length),start))
        },enabled=selection.isNotEmpty()&&m.calcSession==null,modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=8.dp)){Text("Cut",fontSize=11.sp)}
        TextButton(onClick={
            if(copyTarget.text.isNotBlank())clipboard.setText(AnnotatedString(copyTarget.text))
            if(selection.isEmpty())copyExpression=copyTarget.expressionNext
        },modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=8.dp)){Text(if(selection.isNotEmpty())"Copy" else copyTarget.label,fontSize=11.sp)}
        TextButton(onClick={clipboard.getText()?.text?.let{if(m.calcSession!=null)m.insertCalcValue(it)else m.insert(it)}},modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=8.dp)){Text("Paste",fontSize=11.sp)}
        if(m.calcSession==null)TextButton(onClick=onToggleTyping,modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=8.dp)){Text(if(typing)"Math input" else "Keyboard",fontSize=11.sp)}
    }
}

@Composable fun DisplayContent(m:CalculatorModel,typing:Boolean,requestInitialFocus:Boolean=true,scrollState:ScrollState?=null,onInitialFocus:()->Unit={}) {
    val c=LocalInstrument.current
    val inputTree=remember(m.editor,m.answerDisplay,typing) {
        if(typing||m.editor.source.length>800)null else m.inputTree()
    }
    val compactInput=m.editor.source.length>800||largeHistoryTree(inputTree,compactStructured=false)
    val focus=remember {FocusRequester()}
    val requestInputFocus:()->Boolean={try {focus.requestFocus()} catch (_:IllegalStateException) {false}}
    var caretVisible by remember{mutableStateOf(true)}
    LaunchedEffect(m.editor,m.committed){caretVisible=true;while(!m.committed){delay(500);caretVisible=!caretVisible}}
    LaunchedEffect(typing,compactInput,m.poweredOn,requestInitialFocus){
        if(requestInitialFocus&&!typing&&!compactInput&&m.poweredOn&&scrollState?.isScrollInProgress!=true&&requestInputFocus())onInitialFocus()
    }
    val calculating=m.busy||m.previewBusy
    var showCalculationStatus by remember{mutableStateOf(false)}
    LaunchedEffect(calculating,m.inputVersion){
        showCalculationStatus=false
        if(calculating){delay(1000);showCalculationStatus=true}
    }
    Column(Modifier.fillMaxWidth().padding(horizontal=14.dp)) {
        if(!m.poweredOn) Box(Modifier.fillMaxWidth().height(66.dp),contentAlignment=Alignment.Center){Text("OFF · press 2nd to resume",color=c.muted)}
        else if(typing||compactInput) BasicTextField(
            value=TextFieldValue(m.editor.source,TextRange(m.editor.anchor.coerceIn(0,m.editor.source.length),m.editor.cursor.coerceIn(0,m.editor.source.length))),
            onValueChange={
                val auto=if(!m.committed&&m.autoCloseBrackets&&m.editor.cursor==m.editor.anchor&&it.selection.collapsed)
                    BracketAutoClose.typed(m.editor.source,m.editor.cursor,it.text,it.selection.end) else null
                if(auto!=null){
                    val inserted=auto.source!=m.editor.source
                    m.edit(Editor(auto.source,auto.cursor,auto.cursor))
                    if(inserted)m.markTypedParens(auto.cursor)
                }
                else if(m.committed&&it.text!=m.editor.source){
                    if(it.text.startsWith(m.editor.source))m.insert(it.text.removePrefix(m.editor.source))
                    else m.fresh(Editor(it.text,it.selection.end,it.selection.start))
                }else{
                    m.edit(Editor(it.text,it.selection.end,it.selection.start))
                    m.markTypedParens(it.selection.end)
                }
            },
            modifier=Modifier.fillMaxWidth().heightIn(min=60.dp).onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.Enter){m.calculate();true}else false}.semantics{contentDescription="Expression input"},
            textStyle=TextStyle(color=c.ink,fontSize=m.inputFont.sp,fontFamily=FontFamily.Monospace),
            maxLines=if(compactInput)4 else Int.MAX_VALUE,
            readOnly=m.calcSession!=null)
        else Box(Modifier.fillMaxWidth().heightIn(min=60.dp).focusRequester(focus).onKeyEvent{event->
            if(event.type!=KeyEventType.KeyDown)false else if(m.calcSession!=null)when(event.key){
                Key.Enter,Key.NumPadEnter->{m.submitCalcValue();true}
                Key.Backspace->{m.editCalcValue(m.calcSession!!.input.delete());true}
                Key.Delete->{m.editCalcValue(m.calcSession!!.input.deleteForward());true}
                Key.DirectionLeft->{m.editCalcValue(m.calcSession!!.input.move(-1));true}
                Key.DirectionRight->{m.editCalcValue(m.calcSession!!.input.move(1));true}
                else->{val ch=event.nativeKeyEvent.unicodeChar;if(ch>=32&&ch!=127){m.insertCalcValue(ch.toChar().toString());true}else false}
            } else when(event.key){
                Key.Enter,Key.NumPadEnter->{m.calculate();true}
                Key.Backspace->{m.edit(m.editor.delete());true}
                Key.Delete->{m.edit(m.editor.deleteForward());true}
                Key.DirectionLeft->{if(m.engineeringConversion)m.shiftEngineering(1)else m.edit(m.editor.moveMatrix(0,-1) ?: m.editor.move(-1));true};Key.DirectionRight->{if(m.engineeringConversion)m.shiftEngineering(-1)else m.edit(m.editor.moveMatrix(0,1) ?: m.editor.move(1));true}
                Key.DirectionUp->{m.edit(m.editor.moveMatrix(-1,0) ?: m.editor.parent());true};Key.DirectionDown->{m.edit(m.editor.moveMatrix(1,0) ?: m.editor.child());true}
                else->{val ch=event.nativeKeyEvent.unicodeChar;if(ch>=32&&ch!=127){m.insert(ch.toChar().toString());true}else false}
            }
        }.focusable().horizontalScroll(rememberScrollState()).semantics{contentDescription="Current expression"},contentAlignment=Alignment.CenterStart){
            Row(verticalAlignment=Alignment.CenterVertically){
                CompositionLocalProvider(LocalMathCursorTarget provides if(m.committed)null else m.editor.cursorTarget(),LocalMathAfter provides {a,b->requestInputFocus();m.edit(m.editor.after(a,b))},LocalCaretVisible provides caretVisible,LocalActiveToken provides m.editor.activeToken,LocalTypedParens provides m.typedParens,LocalPlaceCursor provides {a,b,p->requestInputFocus();m.edit(m.editor.placeInToken(a,b,p))}) {
                    if(m.editor.source.isBlank())Text("│",fontSize=m.inputFont.sp,color=if(caretVisible)c.accent else androidx.compose.ui.graphics.Color.Transparent)
                    else if(inputTree!=null)MathNode(inputTree,m.inputFont,select={a,b->requestInputFocus();m.edit(m.editor.selectRange(a,b))},selection=minOf(m.editor.anchor,m.editor.cursor)..maxOf(m.editor.anchor,m.editor.cursor))
                    else Row {Lexer.scan(m.editor.source).filter{it.text.isNotEmpty()}.forEach{token->
                        MathNode(JSONObject(Expr("text",m.editor.source.substring(token.start,token.end),start=token.start,end=token.end).json()),m.inputFont,select={a,b->requestInputFocus();m.edit(m.editor.selectRange(a,b))},selection=minOf(m.editor.anchor,m.editor.cursor)..maxOf(m.editor.anchor,m.editor.cursor))
                    }}
                }
                Box(Modifier.width(32.dp).heightIn(min=48.dp).clickable{requestInputFocus();val editor=m.editor;m.edit(editor.tree()?.let{editor.after(it.start,it.end)} ?: Editor(editor.source))}.semantics{contentDescription="After expression"},contentAlignment=Alignment.CenterStart){
                }
            }
        }
        // This answer region is always present, including while a worker is computing.
        Box(Modifier.fillMaxWidth().heightIn(min=56.dp).semantics(mergeDescendants=true){contentDescription=if(m.calcSession!=null)"CALC variable input" else "Answer panel";liveRegion=LiveRegionMode.Polite},contentAlignment=Alignment.CenterEnd){
            val session=m.calcSession
            if(session!=null)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("${session.index+1}/${session.names.size}  ${session.name} = ",fontSize=20.sp,color=c.accent)
                BasicTextField(
                    value=TextFieldValue(session.input.source,TextRange(session.input.anchor.coerceIn(0,session.input.source.length),session.input.cursor.coerceIn(0,session.input.source.length))),
                    onValueChange={m.editCalcValue(Editor(it.text,it.selection.end,it.selection.start))},
                    modifier=Modifier.weight(1f).onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.Enter){m.submitCalcValue();true}else false}.semantics{contentDescription="Value for ${session.name}"},
                    textStyle=TextStyle(color=c.ink,fontSize=22.sp,fontFamily=FontFamily.Monospace),
                    decorationBox={inner->Box(Modifier.fillMaxWidth()) {
                        if(session.input.source.isEmpty()) {
                            val stored=m.variables.optJSONObject(session.name)
                            if(stored!=null)MathNode(stored,20f) else Text("0",fontSize=22.sp,color=c.muted)
                        }
                        inner()
                    }}
                )
            }
            else if(m.result!=null&&m.poweredOn)Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),contentAlignment=Alignment.CenterEnd){
                val currentResult=m.result!!
                val compactResult=remember(currentResult,m.decimal) {
                    currentResult.optString("exact").length>20_000||
                        largeHistoryTree(currentResult.optJSONObject(if(m.decimal)"decimalTree" else "tree"),compactStructured=false)
                }
                if(compactResult)Text(currentResult.optString(if(m.decimal)"decimal" else "exact").take(1200),fontSize=m.outputFont.sp,color=c.ink,maxLines=8,overflow=TextOverflow.Ellipsis)
                else ResultMath(currentResult,m.decimal,m.outputFont,m.mixedNumbers,
                    displayMode=m.resultDisplayMode,thousandsSeparator=m.thousandsSeparator,engineeringConversion=m.engineeringConversion,engineeringShift=m.engineeringShift,dmsDisplay=m.dmsDisplay,dmsConversion=m.dmsConversion,displayDigits=m.displayDigits)
            } else Text(" ",fontSize=28.sp)
        }
        val shownCalcValues=m.calcSession?.accepted ?: if(m.committed&&m.lastCalcSource==m.editor.source)m.lastCalcValues else emptyMap()
        val shownCalcSource=m.calcSession?.source ?: if(m.committed&&m.lastCalcSource==m.editor.source)m.lastCalcSource else ""
        val shownCalcFormula=m.variables.optJSONObject(shownCalcSource)?.takeIf {it.has("start")&&it.optString("kind")!="number"}
        if(shownCalcValues.isNotEmpty()||shownCalcFormula!=null)Row(Modifier.fillMaxWidth().heightIn(min=24.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
            MathText("CALC  ",11f,modifier=Modifier.alignBy(MathAxis),tint=c.muted)
            if(shownCalcFormula!=null){
                MathText("$shownCalcSource = ",13f,modifier=Modifier.alignBy(MathAxis),tint=c.accent)
                Box(Modifier.alignBy(MathAxis).testTag("calc-formula")){MathNode(shownCalcFormula,14f)}
                Spacer(Modifier.width(14.dp))
            }
            shownCalcValues.forEach {(name,value)->
                MathText("$name = ",13f,modifier=Modifier.alignBy(MathAxis),tint=c.accent)
                Box(Modifier.alignBy(MathAxis)){MathNode(value,14f)}
                Spacer(Modifier.width(14.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(24.dp),verticalAlignment=Alignment.CenterVertically){
            Text(when{m.engineeringConversion->if(isKorean())"ENG 모드 · ←/→로 가수 이동" else "ENG mode · ←/→ shifts mantissa";m.error.isNotBlank()->m.error;calculating&&showCalculationStatus->if(isKorean())"계산 중…" else if(m.busy)"Computing…" else "Calculating…";m.calcSession!=null->if(isKorean())"CALC · 값을 입력하고 = 누르기 · AC는 취소" else "CALC · enter a value, then press = · AC cancels";domainText(m.result).isNotBlank()->domainText(m.result);m.committed->if(isKorean())"다음 입력 시 새 계산 시작" else "Next input starts a new calculation";else->m.result?.optString("note") ?: ""},Modifier.weight(1f),fontSize=10.sp,maxLines=1,color=if(m.error.isNotBlank())c.danger else if(m.engineeringConversion)c.accent else c.muted)
            if(calculating&&showCalculationStatus)Text("Cancel",Modifier.clickable{m.cancel()}.padding(start=8.dp),fontSize=10.sp,color=c.accent)
        }
    }
}

@Composable fun DisplayActions(m:CalculatorModel,screenExpanded:Boolean=false,onToggleScreen:(()->Unit)?=null) {
    val context=LocalContext.current
    val open=LocalCalculatorOverlay.current
    var showCustom by remember {mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal=14.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
        SmallAction(if(m.decimal)"≈ Decimal" else "Exact",translate=false){m.decimal=!m.decimal}
        SmallAction(if(m.resultDisplayMode==ResultDisplayMode.SCIENTIFIC)"SCI" else "ENG",active=m.resultDisplayMode!=ResultDisplayMode.OFF,description="Result notation",translate=false){m.cycleResultDisplayMode()}
        SmallAction(",",active=m.thousandsSeparator,description="Thousands separators",translate=false){m.thousandsSeparator=!m.thousandsSeparator;m.save()}
        if(onToggleScreen!=null)SmallAction("scr",active=screenExpanded,description=if(screenExpanded)"Restore full keypad" else "Expand calculation screen",translate=false){onToggleScreen()}
        m.displayShortcuts.forEach {shortcut->SmallAction(shortcut.label,description="${shortcut.label}: ${shortcut.input}",translate=false){runDisplayShortcut(m,shortcut,open)}}
        SmallAction("Share",translate=false){m.result?.let{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,m.editor.source+" = "+it.optString("exact")),"Share calculation"))}}
        TextButton(onClick={showCustom=true},contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp),
            modifier=Modifier.semantics {contentDescription="Customize display buttons"}) {
            Icon(Icons.Default.Settings,contentDescription=null,modifier=Modifier.size(18.dp))
        }
    }
    if(showCustom)DisplayShortcutsDialog(m){showCustom=false}
}

@Composable fun ResultMath(result:JSONObject,decimal:Boolean,size:Float,mixed:Boolean=false,displayMode:ResultDisplayMode=ResultDisplayMode.OFF,thousandsSeparator:Boolean=false,engineeringConversion:Boolean=false,engineeringShift:Int=0,dmsDisplay:Boolean=false,dmsConversion:Boolean=false,displayDigits:Int=10) {
    val useDecimal=decimal||engineeringConversion
    val effectiveMode=if(engineeringConversion)ResultDisplayMode.ENGINEERING else displayMode
    val wantDms=dmsDisplay&&!engineeringConversion
    var tree=when {
        wantDms&&result.optBoolean("dms")->result.optJSONObject(if(decimal)"decimalTree" else "tree")
        wantDms->ResultDisplayFormat.dmsTree(result.optString("decimal"))
        result.optBoolean("dms")->result.optJSONObject("numericDecimalTree")
            ?: result.optJSONObject("numericTree")
            ?: result.optJSONObject(if(decimal)"decimalTree" else "tree")
        dmsConversion->result.optJSONObject("decimalTree") ?: result.optJSONObject("tree")
        else->result.optJSONObject(if(useDecimal)"decimalTree" else "tree") ?: result.optJSONObject("tree")
    }
    if(mixed&&!useDecimal&&tree?.optString("kind")=="fraction") {
        val fraction=tree
        tree=runCatching {
            val numerator=fraction!!.getJSONArray("args").getJSONObject(0).getString("value").toBigInteger()
            val denominator=fraction.getJSONArray("args").getJSONObject(1).getString("value").toBigInteger()
            val parts=numerator.abs().divideAndRemainder(denominator)
            if(parts[0].signum()==0)fraction else JSONObject().put("kind","call").put("value","mixed").put("args",org.json.JSONArray(listOf(parts[0]*numerator.signum().toBigInteger(),parts[1],denominator).map {JSONObject().put("kind","number").put("value",it.toString())}))
        }.getOrDefault(tree)
    }
    val shift=if(engineeringConversion)engineeringShift else 0
    val displayTree=tree?.let{ResultDisplayFormat.formatTree(it,effectiveMode,thousandsSeparator,shift,engineeringConversion,displayDigits)}
    if(displayTree!=null)MathNode(displayTree,size) else Text(ResultDisplayFormat.formatText(result.optString(if(useDecimal)"decimal" else "exact"),effectiveMode,thousandsSeparator,shift,engineeringConversion,displayDigits),fontSize=size.sp,color=LocalInstrument.current.ink,fontFamily=FontFamily.Serif)
}
private fun domainText(result:JSONObject?):String {
    val conditions=result?.optJSONArray("conditions") ?: return ""
    return if(conditions.length()==0)"" else "Domain: "+(0 until conditions.length()).joinToString{conditions.optString(it)}
}
@Composable fun SmallAction(text:String,active:Boolean?=null,description:String?=null,shaded:Boolean=false,fontSize:TextUnit=11.sp,translate:Boolean=true,action:()->Unit){
    val c=LocalInstrument.current
    val color=when(active){true->c.accent;false->c.muted.copy(alpha=.45f);null->MaterialTheme.colorScheme.onSurface}
    val modifier=description?.let{value->Modifier.semantics{contentDescription=value}} ?: Modifier
    TextButton(onClick=action,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp),modifier=modifier,
        colors=ButtonDefaults.textButtonColors(containerColor=if(shaded)c.accent.copy(alpha=.22f) else androidx.compose.ui.graphics.Color.Transparent)){
        Text(if(translate)tr(text) else text,fontSize=fontSize,color=color,fontWeight=if(active==true)FontWeight.SemiBold else FontWeight.Normal)
    }
}
@Composable fun Templates(m:CalculatorModel){Row(Modifier.horizontalScroll(rememberScrollState())){listOf("Factor" to "factor(x^4-1)","Solve" to "solve(x^2-5x+6=0,x)","Derivative" to "diff(sin(x^2),x)","Integral" to "integrate(x^2*exp(x),x)").forEach{(label,source)->SmallAction(label,translate=false){m.edit(Editor(source))}}}}

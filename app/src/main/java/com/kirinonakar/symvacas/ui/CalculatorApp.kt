package com.kirinonakar.symvacas.ui

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
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
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.calculator.ResultDisplayMode
import com.kirinonakar.symvacas.calculator.TapeEntry
import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.math.LatexInput
import com.kirinonakar.symvacas.math.BracketAutoClose
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import org.json.JSONObject
import kotlinx.coroutines.delay

val Modes=listOf("Scientific/CAS","Graph","Python","Equations","Matrix","Vector","Statistics","Probability","Programmer","Units","Constants","Tip","Currency","Functions")
private fun handleMathInputKey(m:CalculatorModel,event:KeyEvent):Boolean {
    if(event.type!=KeyEventType.KeyDown)return false
    val session=m.calcSession
    if(event.key==Key.MoveHome||event.key==Key.MoveEnd) {
        val editor=session?.input ?: m.editor
        val position=if(event.key==Key.MoveHome)0 else editor.source.length
        val next=Editor(editor.source,position,if(event.isShiftPressed)editor.anchor else position)
        if(session!=null)m.editCalcValue(next)else m.edit(next)
        return true
    }
    if(session!=null)return when(event.key){
        Key.Enter,Key.NumPadEnter->{m.submitCalcValue();true}
        Key.Backspace->{m.editCalcValue(session.input.delete());true}
        Key.Delete->{m.editCalcValue(session.input.deleteForward());true}
        Key.DirectionLeft->{m.editCalcValue(session.input.move(-1));true}
        Key.DirectionRight->{m.editCalcValue(session.input.move(1));true}
        else->{val ch=event.nativeKeyEvent.unicodeChar;if(ch>=32&&ch!=127){m.insertCalcValue(ch.toChar().toString(),operand=false);true}else false}
    }
    return when(event.key){
        Key.Enter,Key.NumPadEnter->{m.calculate();true}
        Key.Backspace->{m.edit(m.editor.delete());true}
        Key.Delete->{m.edit(m.editor.deleteForward());true}
        Key.DirectionLeft->{if(m.engineeringConversion)m.shiftEngineering(1)else m.edit(m.editor.moveMatrix(0,-1) ?: m.editor.move(-1));true}
        Key.DirectionRight->{if(m.engineeringConversion)m.shiftEngineering(-1)else m.edit(m.editor.moveMatrix(0,1) ?: m.editor.move(1));true}
        Key.DirectionUp->{m.edit(m.editor.moveMatrix(-1,0) ?: m.editor.parent());true}
        Key.DirectionDown->{m.edit(m.editor.moveMatrix(1,0) ?: m.editor.child());true}
        else->{val ch=event.nativeKeyEvent.unicodeChar;if(ch>=32&&ch!=127){m.insert(ch.toChar().toString(),operand=false);true}else false}
    }
}
private val LocalCalculatorOverlay=staticCompositionLocalOf<(String)->Unit> { {} }
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun CalculatorApp(m:CalculatorModel) {
    val c=LocalInstrument.current
    var overlay by rememberSaveable {mutableStateOf("")}
    var screenExpanded by rememberSaveable {mutableStateOf(false)}
    val workspaces=rememberSaveableStateHolder()
    val context=LocalContext.current
    val avoidKeyboard=m.mode!="Scientific/CAS"
    DisposableEffect(context) {
        val window=(context as? Activity)?.window
        val previous=window?.attributes?.softInputMode
        if(previous!=null)window?.setSoftInputMode((previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {if(previous!=null)window?.setSoftInputMode(previous)}
    }
    LaunchedEffect(m.mode){m.save()}
    CompositionLocalProvider(LocalCalculatorOverlay provides {overlay=it}, LocalLanguage provides m.language) {
    Column(Modifier.fillMaxSize().background(c.body).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
        .then(if(avoidKeyboard)Modifier.imePadding()else Modifier)) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("SymvaCAS",Modifier.clickable {overlay="About"}.semantics {contentDescription="About SymvaCAS"},fontWeight=FontWeight.ExtraBold,letterSpacing=2.sp,fontSize=18.sp,color=c.ink)
            Spacer(Modifier.weight(1f))
            SmallAction("History"){overlay="History"};SmallAction("Catalog"){overlay="Catalog"};SmallAction("Setup"){overlay="Settings"}
        }
        Row(Modifier.fillMaxWidth().height(48.dp).zIndex(2f).background(c.scientific).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight().combinedClickable(onClick={overlay="Mode"},onLongClick={m.mode="Scientific/CAS"},onLongClickLabel="Go to Scientific/CAS mode").semantics{contentDescription="Choose calculation mode, long press for Scientific/CAS mode"},contentAlignment=Alignment.CenterStart) {
                Text(m.mode.uppercase()+" ▾",Modifier.fillMaxWidth().padding(horizontal=8.dp),fontSize=12.sp,color=c.ink)
            }
            if(m.variables.has("M"))Text("M  ",fontSize=10.sp,color=c.muted,modifier=Modifier.semantics{contentDescription="Stored memory"})
            Text(if(m.shift)"SHIFT  " else if(m.alpha)"ALPHA  " else if(m.hyperbolic)"HYP  " else if(m.secondKeys)"2ND  " else "",fontSize=10.sp,color=if(m.alpha)c.alpha else c.shift)
            Text(if(m.overwrite)"OVR  " else "INS  ",fontSize=10.sp,color=if(m.overwrite)c.accent else c.muted,fontWeight=if(m.overwrite)FontWeight.Bold else FontWeight.Normal,modifier=Modifier.clickable{m.overwrite=!m.overwrite}.semantics{contentDescription=if(m.overwrite)"Overwrite mode" else "Insert mode"})
            if(m.mixedNumbers)Text("mix  ",fontSize=10.sp,color=c.accent,modifier=Modifier.semantics{contentDescription="Mixed numbers"})
            Text(m.angle,Modifier.clickable {m.angle=when(m.angle){"DEG"->"RAD";"RAD"->"GRAD";else->"DEG"};m.recalculatePreview();m.save()}.padding(horizontal=12.dp),fontSize=11.sp,color=c.accent)
            val decimalPlacesLabel=tr("Cycle display decimal places")
            Text("≤${m.displayDigits} decimals",Modifier.clickable {
                m.displayDigits=when(m.displayDigits){2->3;3->5;5->10;else->2}
                m.recalculatePreview();m.save()
            }.padding(vertical=12.dp).semantics {contentDescription=decimalPlacesLabel},fontSize=10.sp,color=c.muted)
        }
        Box(Modifier.weight(1f)) {workspaces.SaveableStateProvider(m.mode) {
            when(m.mode) {
                "Equations"->EquationScreen(m)
                "Functions"->FunctionsScreen(m)
                "Graph"->GraphScreen(m)
                "Python"->PythonScreen(m)
                "Matrix","Vector"->MatrixScreen(m)
                "Statistics"->StatisticsScreen(m)
                "Probability"->ProbabilityScreen(m)
                "Programmer"->ProgrammerScreen(m)
                "Units"->UnitsScreen(m)
                "Constants"->ConstantsScreen(m)
                "Tip"->TipScreen()
                "Currency"->CurrencyScreen(m)
                else->ScientificWorkspace(m,screenExpanded,{screenExpanded=!screenExpanded},{overlay=it})
            }
        }}
        if(m.mode !in listOf("Scientific/CAS","Equations","Probability") && m.error.isNotBlank()) Text(m.error,Modifier.fillMaxWidth().padding(8.dp),fontSize=12.sp,color=c.danger)
    }
    when(overlay) {
        "About"->AboutDialog {overlay=""}
        "Mode"->AlertDialog(onDismissRequest={overlay=""},title={Text(tr("Calculation mode"))},text={Column(Modifier.verticalScroll(rememberScrollState())) {Modes.chunked(2).forEach {row->Row {row.forEach {name->TextButton(onClick={m.mode=name;overlay=""},modifier=Modifier.weight(1f)){Text(name)}}}}}},confirmButton={TextButton(onClick={overlay=""}){Text(tr("Close"))}})
        "Settings"->SettingsDialog(m){overlay=""}
        "MatrixSize"->MatrixSizeDialog(m){overlay=""}
        "History"->HistoryDialog(m){overlay=""}
        "RCL"->RecallDialog(m){overlay=""}
        "Variables","STO"->VariablesDialog(m){overlay=""}
        "Catalog"->CatalogDialog(m){overlay=""}
        "Clear"->AlertDialog(onDismissRequest={overlay=""},title={Text(tr("Clear"))},text={Column {
            TextButton(onClick={m.resetSetup();overlay=""}){Text("1 · Setup")}
            TextButton(onClick={m.clearMemory();overlay=""}){Text(if(isKorean()) "2 · 메모리" else "2 · Memory")}
            TextButton(onClick={m.resetSetup();m.clearMemory();m.clearHistory();overlay=""}){Text(if(isKorean()) "3 · 모두" else "3 · All")}
        }},confirmButton={TextButton(onClick={overlay=""}){Text(tr("Close"))}})
    }
    }
}

@Composable internal fun ScientificWorkspace(m:CalculatorModel,screenExpanded:Boolean,onToggleScreen:()->Unit,onOverlay:(String)->Unit,
    imeInsets:WindowInsets=WindowInsets.ime) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val keyboardHeight=minOf(520.dp,maxHeight*.68f)
        val landscape=maxWidth>650.dp && maxHeight<500.dp
        val fullKeypadHeight=if(landscape)maxHeight else keyboardHeight
        // Keep each numeric row the same height after removing the upper keypad rows.
        val numericRowHeight=(fullKeypadHeight-36.dp)/9f
        val numericKeypadHeight=numericRowHeight*4f+20.dp
        val keypadHeight=if(screenExpanded)numericKeypadHeight else fullKeypadHeight
        // The root already consumes system bars. Let the IME cover the keypad, and reserve
        // only its remaining overlap inside the tape; neither keypad height nor position changes.
        val remainingIme=imeInsets.exclude(WindowInsets.systemBars.union(WindowInsets.displayCutout))
        val imeHeight=with(LocalDensity.current){remainingIme.getBottom(this).toDp()}
        val tapeBottomPadding=(imeHeight-if(landscape)0.dp else keypadHeight).coerceAtLeast(0.dp)
        if(landscape) Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.Bottom) {
            CalculationTape(m,Modifier.weight(1f).fillMaxHeight().padding(bottom=tapeBottomPadding),screenExpanded,onToggleScreen)
            Keypad(m,Modifier.weight(if(screenExpanded) .7f else 1f).height(keypadHeight),screenExpanded,numericRowHeight,onOverlay)
        } else Column(Modifier.fillMaxSize()) {
            CalculationTape(m,Modifier.weight(1f).padding(bottom=tapeBottomPadding),screenExpanded,onToggleScreen)
            Keypad(m,Modifier.fillMaxWidth().height(keypadHeight),screenExpanded,numericRowHeight,onOverlay)
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
        val compactInput=remember(entry.input) {input==null||entry.source.length>800||largeHistoryTree(input)}
        val compactResult=remember(entry.result,m.decimal) {
            response==null||entry.result.length>20_000||
                !safeHistoryResponse(response)
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clickable {m.reuse(entry)}
            .semantics {contentDescription="Reuse calculation: ${entry.source.take(120)}"}) {
            if(!compactInput&&input!=null)MathNode(input,m.inputFont*.84f)
            else Text(entry.source.take(800),fontSize=(m.inputFont*.84f).sp,fontFamily=FontFamily.Monospace,color=c.ink,maxLines=4,overflow=TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().padding(top=6.dp),contentAlignment=Alignment.CenterEnd) {
            if(!compactResult&&response!=null)ResultMath(response,m.decimal,m.outputFont*.82f,
                displayMode=m.resultDisplayMode,thousandsSeparator=m.thousandsSeparator,dmsDisplay=response.optBoolean("dms"),displayDigits=m.displayDigits)
            else Text(ResultDisplayFormat.formatText(response?.optString(if(m.decimal)"decimal" else "exact").orEmpty().ifBlank {entry.result},m.resultDisplayMode,m.thousandsSeparator,maxFractionDigits=m.displayDigits).take(1200),
                fontSize=(m.outputFont*.72f).sp,color=c.ink,maxLines=8,overflow=TextOverflow.Ellipsis)
        }
        if(response!=null&&domainText(response).isNotEmpty())Text(domainText(response),fontSize=10.sp,color=c.muted)
        HorizontalDivider(Modifier.padding(top=10.dp),color=c.grid)
    }
}

internal fun largeHistoryTree(root:JSONObject?):Boolean {
    return root!=null&&!safeHistoryTree(root)
}

@Composable fun Display(m:CalculatorModel,screenExpanded:Boolean=false,onToggleScreen:(()->Unit)?=null,requestInitialFocus:Boolean=true,showInput:Boolean=true) {
    val c=LocalInstrument.current
    var typing by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(m.calcSession!=null){if(m.calcSession!=null)typing=false}
    Column(Modifier.fillMaxWidth().background(c.display).padding(vertical=4.dp)) {
        DisplayToolbar(m,typing,showInput){typing=!typing}
        DisplayContent(m,typing,requestInitialFocus,showInput=showInput)
        DisplayActions(m,screenExpanded,onToggleScreen)
    }
}

@Composable fun DisplayToolbar(m:CalculatorModel,typing:Boolean,showInput:Boolean=true,onToggleTyping:()->Unit) {
    val c=LocalInstrument.current
    val clipboard=LocalClipboardManager.current
    var copyExpression by remember{mutableStateOf(false)}
    // The Copy button cycles answer → expression; a new result or input starts over at the answer.
    LaunchedEffect(m.result,m.editor.source){copyExpression=false}
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(if(m.committed)"=" else "MATH",fontSize=10.sp,color=c.muted,letterSpacing=1.sp)
        Spacer(Modifier.weight(1f))
        if(showInput)TextButton(onClick={m.undo()},enabled=m.canUndo,modifier=Modifier.height(36.dp).semantics{contentDescription="Undo last input"},contentPadding=PaddingValues(horizontal=8.dp)){Text("Undo",fontSize=11.sp)}
        val selection=if(showInput)m.editor.source.substring(minOf(m.editor.anchor,m.editor.cursor),maxOf(m.editor.anchor,m.editor.cursor)) else ""
        val copyAnswer=remember(m.result,m.decimal,m.mixedNumbers,m.resultDisplayMode,m.thousandsSeparator,m.engineeringConversion,m.engineeringShift,m.dmsDisplay,m.dmsConversion,m.displayDigits) {
            m.result?.let {result->ResultDisplayFormat.resultText(result,m.decimal,m.mixedNumbers,m.resultDisplayMode,m.thousandsSeparator,m.engineeringConversion,m.engineeringShift,m.dmsDisplay,m.dmsConversion,m.displayDigits)}
        }
        val copyTarget=if(showInput)CopyCycle.next(copyAnswer,m.editor.source,copyExpression,selection) else CopyTarget(copyAnswer.orEmpty(),false,false)
        if(showInput)TextButton(onClick={
            val start=minOf(m.editor.anchor,m.editor.cursor)
            clipboard.setText(AnnotatedString(selection))
            m.edit(Editor(m.editor.source.removeRange(start,start+selection.length),start))
        },enabled=selection.isNotEmpty()&&m.calcSession==null,modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=8.dp)){Text("Cut",fontSize=11.sp)}
        TextButton(onClick={
            if(copyTarget.text.isNotBlank())clipboard.setText(AnnotatedString(copyTarget.text))
            if(selection.isEmpty())copyExpression=copyTarget.expressionNext
        },enabled=showInput||copyTarget.text.isNotBlank(),modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=8.dp)){Text(if(!showInput)if(isKorean())"결과 복사" else "Copy result" else if(selection.isNotEmpty())"Copy" else copyTarget.label,fontSize=11.sp)}
        if(showInput)TextButton(onClick={clipboard.getText()?.text?.let{if(m.calcSession!=null)m.insertCalcValue(it)else m.insert(it)}},modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=8.dp)){Text("Paste",fontSize=11.sp)}
        if(showInput&&m.calcSession==null)TextButton(onClick=onToggleTyping,modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=8.dp)){Text(if(typing)"Math input" else "Keyboard",fontSize=11.sp)}
    }
}

@Composable fun DisplayContent(m:CalculatorModel,typing:Boolean,requestInitialFocus:Boolean=true,scrollState:ScrollState?=null,showInput:Boolean=true,onInitialFocus:()->Unit={}) {
    val c=LocalInstrument.current
    val inputTree=remember(m.editor.source,m.answerDisplay,typing,showInput) {
        if(typing||!showInput)null else m.inputTree()
    }
    val focus=remember {FocusRequester()}
    var inputFocused by remember {mutableStateOf(false)}
    val requestInputFocus:()->Boolean={try {focus.requestFocus()} catch (_:IllegalStateException) {false}}
    var caretVisible by remember{mutableStateOf(true)}
    LaunchedEffect(m.editor,m.committed,showInput){caretVisible=true;while(showInput&&!m.committed){delay(500);caretVisible=!caretVisible}}
    LaunchedEffect(typing,m.wordWrap,m.poweredOn,requestInitialFocus,showInput){
        if(showInput&&requestInitialFocus&&!typing&&m.poweredOn&&scrollState?.isScrollInProgress!=true&&requestInputFocus())onInitialFocus()
    }
    val calculating=m.busy||m.previewBusy
    var showCalculationStatus by remember{mutableStateOf(false)}
    LaunchedEffect(calculating,m.inputVersion){
        showCalculationStatus=false
        if(calculating){delay(1000);showCalculationStatus=true}
    }
    Column(Modifier.fillMaxWidth().padding(horizontal=14.dp)) {
        if(showInput&&!m.poweredOn) Box(Modifier.fillMaxWidth().height(66.dp),contentAlignment=Alignment.Center){Text("OFF · press 2nd to resume",color=c.muted)}
        else if(showInput&&typing) BasicTextField(
            value=TextFieldValue(m.editor.source,TextRange(m.editor.anchor.coerceIn(0,m.editor.source.length),m.editor.cursor.coerceIn(0,m.editor.source.length))),
            onValueChange={
                val relation=if(!m.committed&&it.selection.collapsed&&it.composition==null)m.editor.typedRelation(it.text,it.selection.end) else null
                val symbolDeleted=m.editor.atomicSymbolDeletion(it.text)
                val latex=LatexInput.convertEdit(m.editor,it.text)
                if(relation!=null) {
                    m.edit(relation)
                } else if(symbolDeleted!=null) {
                    if(m.committed)m.fresh(symbolDeleted) else m.edit(symbolDeleted)
                } else if(latex!=null) {
                    if(m.committed)m.fresh(latex) else m.edit(latex)
                } else {
                val structuralBracket=m.editor.inCallArgument() && it.text.getOrNull(m.editor.cursor) in listOf('(',')','[',']','{','}')
                val auto=if(!m.committed&&(m.autoCloseBrackets||structuralBracket)&&m.editor.cursor==m.editor.anchor&&it.selection.collapsed)
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
                }
            },
            modifier=Modifier.fillMaxWidth().heightIn(min=60.dp).focusRequester(focus).onPreviewKeyEvent{if(it.key in listOf(Key.MoveHome,Key.MoveEnd))handleMathInputKey(m,it)else if(it.type==KeyEventType.KeyDown&&it.key in listOf(Key.Enter,Key.NumPadEnter)){m.calculate();true}else false}.semantics{contentDescription="Expression input"},
            textStyle=TextStyle(color=c.ink,fontSize=m.inputFont.sp,fontFamily=FontFamily.Monospace),
            singleLine=!m.wordWrap,
            maxLines=if(m.wordWrap)4 else 1,
            readOnly=m.calcSession!=null)
        else if(showInput) Box(Modifier.fillMaxWidth().heightIn(min=60.dp,max=if(m.wordWrap)180.dp else androidx.compose.ui.unit.Dp.Infinity).focusRequester(focus).onFocusChanged{inputFocused=it.isFocused}.onKeyEvent{handleMathInputKey(m,it)}.focusable().then(if(m.wordWrap)Modifier.verticalScroll(rememberScrollState())else Modifier.horizontalScroll(rememberScrollState())).semantics{contentDescription="Current expression"},contentAlignment=Alignment.CenterStart){
            // In scrollable workspaces, merely composing this display must not reveal its caret.
            CompositionLocalProvider(LocalMathInputRevision provides if(m.committed||!inputFocused)null else m.editor,LocalMathCursorTarget provides if(m.committed)null else m.editor.cursorTarget(),LocalMathAfter provides {a,b->requestInputFocus();m.edit(m.editor.after(a,b))},LocalCaretVisible provides caretVisible,LocalActiveToken provides m.editor.activeToken,LocalTypedParens provides m.typedParens,LocalPlaceCursor provides {a,b,p->requestInputFocus();m.edit(m.editor.placeInToken(a,b,p))}) {
                MathInputLayout(inputTree,m.editor.source,m.inputFont,m.editor.cursor,if(m.committed)null else m.editor.cursorTarget(),select={a,b->requestInputFocus();m.edit(m.editor.selectRange(a,b))},selection=minOf(m.editor.anchor,m.editor.cursor)..maxOf(m.editor.anchor,m.editor.cursor),after={requestInputFocus();m.edit(Editor(m.editor.source))})
            }
        }
        // This answer region is always present, including while a worker is computing.
        Box(Modifier.fillMaxWidth().heightIn(min=56.dp).semantics(mergeDescendants=true){contentDescription=if(m.calcSession!=null)"CALC variable input" else "Answer panel";liveRegion=LiveRegionMode.Polite},contentAlignment=Alignment.CenterEnd){
            val session=m.calcSession
            if(session!=null)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("${session.index+1}/${session.names.size}  ${session.name} = ",fontSize=20.sp,color=c.accent)
                BasicTextField(
                    value=TextFieldValue(session.input.source,TextRange(session.input.anchor.coerceIn(0,session.input.source.length),session.input.cursor.coerceIn(0,session.input.source.length))),
                    onValueChange={m.editCalcValue(session.input.atomicSymbolDeletion(it.text) ?: Editor(it.text,it.selection.end,it.selection.start))},
                    modifier=Modifier.weight(1f).onPreviewKeyEvent{if(it.key in listOf(Key.MoveHome,Key.MoveEnd))handleMathInputKey(m,it)else if(it.type==KeyEventType.KeyDown&&it.key==Key.Enter){m.submitCalcValue();true}else false}.semantics{contentDescription="Value for ${session.name}"},
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
            else if(m.result!=null&&m.poweredOn)Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.CenterEnd){
                val currentResult=m.result!!
                val compactResult=remember(currentResult,m.decimal) {
                    currentResult.optString("exact").length>20_000||
                        largeHistoryTree(currentResult.optJSONObject(if(m.decimal)"decimalTree" else "tree"))
                }
                if(compactResult)Text(ResultDisplayFormat.formatText(currentResult.optString(if(m.decimal)"decimal" else "exact"),if(m.engineeringConversion)ResultDisplayMode.ENGINEERING else m.resultDisplayMode,m.thousandsSeparator,if(m.engineeringConversion)m.engineeringShift else 0,m.engineeringConversion,m.displayDigits).take(1200),fontSize=m.outputFont.sp,color=c.ink,maxLines=8,overflow=TextOverflow.Ellipsis)
                else ResultMath(currentResult,m.decimal,m.outputFont,m.mixedNumbers,
                    displayMode=m.resultDisplayMode,thousandsSeparator=m.thousandsSeparator,engineeringConversion=m.engineeringConversion,engineeringShift=m.engineeringShift,dmsDisplay=m.dmsDisplay,dmsConversion=m.dmsConversion,displayDigits=m.displayDigits)
            } else Text(" ",fontSize=28.sp)
        }
        val shownCalcValues=m.calcSession?.accepted ?: if(m.committed&&m.lastCalcSource==m.editor.source)m.lastCalcValues else emptyMap()
        val shownCalcSource=m.calcSession?.source ?: if(m.committed&&m.lastCalcSource==m.editor.source)m.lastCalcSource else ""
        val shownCalcFormula=m.variables.optJSONObject(shownCalcSource)?.takeIf {it.has("start")&&it.optString("kind")!="number"}
        if(showInput&&(shownCalcValues.isNotEmpty()||shownCalcFormula!=null))Row(Modifier.fillMaxWidth().heightIn(min=24.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
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
        if(m.mode=="Scientific/CAS"&&m.committed) {
            ResultGuidance(m)
            SolutionSteps(m,m.result?.optJSONObject("solutionSteps"))
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
    val guidance=result.optJSONObject("guidance")
    if(guidance?.optString("status")=="unresolved_equation") {
        Column {
            Text(tr(guidance.optString("message")),style=MaterialTheme.typography.bodyMedium)
            guidance.optJSONObject("knownRoots")?.let {roots->
                Text(tr("Known real roots (partial)"),style=MaterialTheme.typography.bodySmall)
                ResultMath(roots,false,size,displayDigits=displayDigits)
            }
        }
        return
    }
    val useDecimal=decimal||engineeringConversion
    val effectiveMode=if(engineeringConversion)ResultDisplayMode.ENGINEERING else displayMode
    val tree=ResultDisplayFormat.resultTree(result,decimal,mixed,engineeringConversion,dmsDisplay,dmsConversion)
    val shift=if(engineeringConversion)engineeringShift else 0
    val displayTree=tree?.let{ResultDisplayFormat.formatTree(it,effectiveMode,thousandsSeparator,shift,engineeringConversion,displayDigits)}
    if(displayTree!=null)WrappedMathResult(displayTree,size) else Text(ResultDisplayFormat.formatText(result.optString(if(useDecimal)"decimal" else "exact"),effectiveMode,thousandsSeparator,shift,engineeringConversion,displayDigits),fontSize=size.sp,color=LocalInstrument.current.ink,fontFamily=FontFamily.Serif)
}
private fun domainText(result:JSONObject?):String {
    val conditions=result?.optJSONArray("conditions") ?: return ""
    return if(conditions.length()==0)"" else "Domain: "+(0 until conditions.length()).joinToString{conditions.optString(it)}
}
@Composable fun SmallAction(text:String,active:Boolean?=null,description:String?=null,shaded:Boolean=false,fontSize:TextUnit=11.sp,translate:Boolean=true,modifier:Modifier=Modifier,action:()->Unit){
    val c=LocalInstrument.current
    val color=when(active){true->c.accent;false->c.muted.copy(alpha=.45f);null->MaterialTheme.colorScheme.onSurface}
    val actionModifier=description?.let{value->modifier.semantics{contentDescription=value}} ?: modifier
    TextButton(onClick=action,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp),modifier=actionModifier,
        colors=ButtonDefaults.textButtonColors(containerColor=if(shaded)c.accent.copy(alpha=.22f) else androidx.compose.ui.graphics.Color.Transparent)){
        Text(if(translate)tr(text) else text,fontSize=fontSize,color=color,fontWeight=if(active==true)FontWeight.SemiBold else FontWeight.Normal)
    }
}
@Composable fun Templates(m:CalculatorModel){Row(Modifier.horizontalScroll(rememberScrollState())){listOf("Factor" to "factor(x^4-1)","Solve" to "solve(x^2-5x+6=0,x)","Derivative" to "diff(sin(x^2),x)","Integral" to "integrate(x^2*exp(x),x)").forEach{(label,source)->SmallAction(label,translate=false){m.edit(Editor(source))}}}}

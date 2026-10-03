package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.math.Editor
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import kotlin.math.max

/** Arrow-key navigation shared by the grid cells: null keeps the tapped cursor position,
 *  true places the cursor at the end of the newly focused cell, false at its start. */
private class MatrixNav{var cursorEnd:Boolean?=null}
@Composable private fun MatrixCell(value:String,modifier:Modifier,focus:FocusRequester,tag:String,nav:MatrixNav,onMove:(Int,Int,Boolean)->Boolean,onValue:(String)->Unit) {
    val c=LocalInstrument.current
    var focused by remember {mutableStateOf(false)}
    var fieldValue by remember {mutableStateOf(TextFieldValue(value,TextRange(value.length)))}
    LaunchedEffect(value){if(fieldValue.text!=value)fieldValue=TextFieldValue(value,TextRange(value.length))}
    Box(modifier.fillMaxHeight().background(if(focused)c.accent.copy(alpha=.12f) else c.display).then(statCellTouch(focus))) {
        BasicTextField(fieldValue,{fieldValue=it;onValue(it.text)},
            Modifier.fillMaxSize().keepInputVisible().focusRequester(focus)
                .onFocusChanged {state->
                    focused=state.isFocused
                    val cursorEnd=if(state.isFocused)nav.cursorEnd else null
                    if(cursorEnd!=null) {
                        nav.cursorEnd=null
                        val text=fieldValue.text
                        fieldValue=TextFieldValue(text,if(cursorEnd)TextRange(text.length) else TextRange(0))
                    }
                }
                .onPreviewKeyEvent {event->
                    if(event.type!=KeyEventType.KeyDown)false else when(event.key) {
                        Key.DirectionRight->if(fieldValue.selection.max>=fieldValue.text.length)onMove(0,1,true) else false
                        Key.DirectionLeft->if(fieldValue.selection.min<=0)onMove(0,-1,false) else false
                        Key.DirectionUp->onMove(-1,0,false)
                        Key.DirectionDown->onMove(1,0,true)
                        else->false
                    }
                }
                .testTag(tag),
            textStyle=MaterialTheme.typography.bodyMedium.copy(fontSize=13.sp,color=c.ink,fontFamily=FontFamily.Monospace,textAlign=TextAlign.Center),singleLine=true,cursorBrush=SolidColor(c.accent),
            decorationBox={inner->Box(Modifier.fillMaxSize().padding(horizontal=6.dp),contentAlignment=Alignment.Center){inner()}})
    }
}
@Composable private fun MatrixGrid(rows:Int,cols:Int,cells:List<String>,tag:String,onCell:(Int,Int,String)->Unit) {
    val c=LocalInstrument.current
    val grid=c.grid
    val focuses=remember(rows,cols){List(rows*cols){FocusRequester()}}
    val nav=remember{MatrixNav()}
    fun moveFocus(fromRow:Int,fromColumn:Int,dRow:Int,dColumn:Int,atEnd:Boolean):Boolean {
        val (targetRow,targetColumn)=when {
            dColumn>0->if(fromColumn+1<cols)Pair(fromRow,fromColumn+1) else Pair(fromRow+1,0)
            dColumn<0->if(fromColumn-1>=0)Pair(fromRow,fromColumn-1) else Pair(fromRow-1,cols-1)
            else->Pair(fromRow+dRow,fromColumn)
        }
        if(targetRow in 0 until rows&&targetColumn in 0 until cols) {
            nav.cursorEnd=atEnd
            runCatching {focuses[targetRow*cols+targetColumn].requestFocus()}.onFailure {nav.cursorEnd=null}
        }
        return true
    }
    Column(Modifier.fillMaxWidth().border(1.dp,grid).testTag(tag)) {
        Row(Modifier.fillMaxWidth().height(26.dp).background(c.scientific)) {
            Box(Modifier.width(32.dp).fillMaxHeight())
            VerticalDivider(color=grid,thickness=1.dp)
            repeat(cols) {column->
                Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.Center){Text("${column+1}",fontSize=11.sp,color=c.muted,fontWeight=FontWeight.SemiBold)}
                VerticalDivider(color=grid,thickness=1.dp)
            }
        }
        HorizontalDivider(color=grid,thickness=1.dp)
        repeat(rows) {row->
            Row(Modifier.fillMaxWidth().height(46.dp)) {
                Box(Modifier.width(32.dp).fillMaxHeight().background(c.scientific),contentAlignment=Alignment.Center){Text("${row+1}",fontSize=11.sp,color=c.muted,fontWeight=FontWeight.SemiBold)}
                VerticalDivider(color=grid,thickness=1.dp)
                repeat(cols) {column->
                    MatrixCell(cells[row*9+column],Modifier.weight(1f),focuses[row*cols+column],tag+"-cell-$row-$column",nav,{dRow,dColumn,atEnd->moveFocus(row,column,dRow,dColumn,atEnd)}) {text->onCell(row,column,text)}
                    VerticalDivider(color=grid,thickness=1.dp)
                }
            }
            if(row<rows-1)HorizontalDivider(color=grid,thickness=1.dp)
        }
    }
}
@Composable private fun OpChips(ops:List<String>,run:(String)->Unit) {
    ops.chunked(3).forEach {row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){row.forEach {op->SmallAction(op){run(op)}}}}
}

@Composable fun MatrixScreen(m: CalculatorModel) {
    val c=LocalInstrument.current
    val vector=m.mode=="Vector"
    var rows by rememberSaveable { mutableIntStateOf(if(vector)3 else 2) }
    var columns by rememberSaveable { mutableIntStateOf(2) }
    var cells by rememberSaveable { mutableStateOf(List(81) {if(it==0||it==10)"1" else "0"}) }
    var name by rememberSaveable { mutableStateOf("A") }
    var other by rememberSaveable { mutableStateOf("B") }
    val cols=if(vector)1 else columns
    fun source()=(0 until rows).joinToString(",","[","]") {r->(0 until cols).joinToString(",","[","]") {column->cells[r*9+column].ifBlank {"0"} }}
    fun resolved(text:String):String {
        val key=text.trim().trim('[',']').trim()
        if(!key.matches(Regex("[A-Za-z][A-Za-z0-9_]*")))return text
        return treeSource(m.variables.optJSONObject(key)) ?: text
    }
    fun applyOp(op:String) {
        val left=source()
        val right=resolved(other)
        val expression=when(op) {
            "A+B"->"$left+$right"
            "A−B"->"$left−$right"
            "A×B"->"$left×$right"
            else->"$op($left${if(op in listOf("dot","cross","angle","projection","linsolve")) ",$right" else ""})"
        }
        m.edit(Editor(expression));m.calculate()
    }
    Panel(if(vector)"Vector workspace" else "Matrix workspace","") {
        Choices(listOf("Matrix","Vector"),m.mode,{m.mode=it})
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(vector)DimStepper("Components",rows,1..9){rows=it} else {DimStepper("Rows",rows,1..9){rows=it};DimStepper("Columns",columns,1..9){columns=it}}
        }
        MatrixGrid(rows,cols,cells,"matrix-grid") {row,column,text->cells=cells.toMutableList().also {it[row*9+column]=text}}
        Text((if(isKorean())"수식  " else "Expression  ")+source()+(if(other.isBlank())"" else ", "+resolved(other)),fontFamily=FontFamily.Monospace,fontSize=11.sp,color=c.muted)
        Choices(listOf("A","B","C"),name,{name=it})
        val storedTree=m.variables.optJSONObject(name)
        if(storedTree!=null) Row(verticalAlignment=Alignment.CenterVertically) {
            Text("$name = ",fontSize=15.sp,color=c.muted)
            Box(Modifier.horizontalScroll(rememberScrollState())){MathNode(storedTree,m.outputFont*.75f)}
        } else Text(if(isKorean())"${name}에 저장된 값이 없습니다" else "Nothing stored in $name",fontSize=11.sp,color=c.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
            Button(onClick={m.store(name,source())}) {Text(if(isKorean())"${name}에 저장" else "Store as $name")}
            SmallAction("Insert into calculator") {m.edit(Editor(source()));m.mode="Scientific/CAS"}
            SmallAction("Clear grid") {cells=List(81){"0"}}
        }
        Text(tr("Operations"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        OpChips(if(vector)listOf("norm","normalize") else listOf("det","inverse","transpose","rank","trace","ref","rref","lu","eigenvalues","eigenvectors")){applyOp(it)}
        if(vector)Text(if(isKorean())"cross는 성분 3개가 필요합니다. dot, angle, projection은 두 벡터의 길이가 같아야 합니다." else "cross needs 3 components; dot, angle and projection need matching lengths.",fontSize=11.sp,color=c.muted)
        else if(rows!=cols)Text(if(isKorean())"det, inverse, rank, trace, LU, eigenvalues에는 정사각행렬이 필요합니다." else "det, inverse, rank, trace, LU and eigenvalues need a square matrix.",fontSize=11.sp,color=c.muted)
        Text(tr("Operations with the second operand"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        Field(other,"Variable name or literal such as [[4,5,6]]") {other=it}
        val referenced=other.trim().trim('[',']').trim()
        val referencedTree=m.variables.optJSONObject(referenced)
        if(referencedTree!=null) Row(verticalAlignment=Alignment.CenterVertically) {
            Text("$referenced = ",fontSize=15.sp,color=c.muted)
            Box(Modifier.horizontalScroll(rememberScrollState())){MathNode(referencedTree,m.outputFont*.75f)}
        }
        OpChips(if(vector)listOf("dot","cross","angle","projection") else listOf("A+B","A−B","A×B","linsolve")){applyOp(it)}
        val stored=remember(m.variables) {m.variables.keys().asSequence().toList().sorted()}
        if(stored.isNotEmpty()) {
            Text(tr("Stored values · tap to use as the second operand"),fontSize=11.sp,color=c.muted)
            Row(Modifier.horizontalScroll(rememberScrollState())) {stored.forEach {key->SmallAction(key){other=key}}}
        }
        // The workspace panel scrolls: an initial focus request would pull it down to the display.
        Display(m,requestInitialFocus=false)
        Text(if(vector)"The grid holds up to 9 components and expressions support larger vectors. The second operand may be a stored variable or a literal." else "The grid holds up to 9 × 9 and expressions support matrices up to 32 × 32. LU returns L, U and row permutations.",fontSize=11.sp,color=c.muted)
    }
}

@Composable fun MatrixSizeDialog(m:CalculatorModel,close:()->Unit) {
    val c=LocalInstrument.current
    var rows by rememberSaveable {mutableIntStateOf(2)}
    var columns by rememberSaveable {mutableIntStateOf(2)}
    AlertDialog(onDismissRequest=close,title={Text(tr("Matrix size"))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val density=LocalDensity.current
        val measurer=rememberTextMeasurer()
        val labelStyle=LocalTextStyle.current.copy(fontSize=11.sp)
        val labelWidth=remember(measurer,density,labelStyle){with(density){listOf("Rows","Columns").maxOf{measurer.measure(AnnotatedString(it),labelStyle).size.width}.toDp()}}
        DimStepper("Rows",rows,1..9,labelWidth){rows=it}
        DimStepper("Columns",columns,1..9,labelWidth){columns=it}
        Text("$rows × $columns matrix",fontSize=11.sp,color=c.muted)
    }},confirmButton={TextButton(onClick={m.insert(matrixTemplate(rows,columns),2);close()}){Text(tr("Insert"))}},dismissButton={TextButton(onClick=close){Text(tr("Cancel"))}})
}
internal fun matrixTemplate(rows:Int,columns:Int)=List(rows){"["+",".repeat(columns-1)+"]"}.joinToString(",","[","]")

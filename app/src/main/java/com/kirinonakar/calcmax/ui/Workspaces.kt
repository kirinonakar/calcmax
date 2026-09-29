package com.kirinonakar.calcmax.ui

import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.calculator.toTapeEntry
import com.kirinonakar.calcmax.math.Editor
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

@Composable fun Panel(title: String,subtitle: String,scrollState: ScrollState?=null,content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(scrollState ?: rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(tr(title),style=MaterialTheme.typography.titleLarge); if(subtitle.isNotBlank())Text(tr(subtitle),color=LocalInstrument.current.muted,fontSize=12.sp); content()
    }
}
@Composable fun Choices(values: List<String>,selected: String,choose: (String)->Unit,translate:Boolean=true) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { values.forEach { value->FilterChip(selected==value,onClick={choose(value)},label={Text(if(translate)tr(value) else value,fontSize=12.sp)}) } }
}
@Composable fun Field(value: String,label: String,modifier: Modifier=Modifier,enabled: Boolean=true,translate:Boolean=true,onValue: (String)->Unit) { OutlinedTextField(value,onValue,modifier=modifier,label={Text(if(translate)tr(label) else label)},singleLine=true,enabled=enabled) }
@Composable private fun StatHeader(text:String,modifier:Modifier) { val c=LocalInstrument.current; Box(modifier.fillMaxHeight(),contentAlignment=Alignment.Center){Text(text,fontSize=11.sp,color=c.muted,fontWeight=FontWeight.SemiBold)} }
/** Whole-cell activation for the statistics grid. The inner text field consumes pointer events and its own
 *  tap handling is cancelled once a scrolling parent claims the gesture, so watch the cell container instead:
 *  only a genuine drag (moving past twice the touch slop, or a second finger) counts as a scroll. */
@Composable private fun statCellTouch(focus:FocusRequester):Modifier {
    val keyboard by rememberUpdatedState(LocalSoftwareKeyboardController.current)
    return Modifier.pointerInput(focus) {
        val slop=viewConfiguration.touchSlop*2f
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val start=down.position
            var dragged=false
            while(true) {
                val event=awaitPointerEvent()
                if(event.changes.count {it.pressed}>1){dragged=true;break}
                val change=event.changes.firstOrNull {it.id==down.id} ?: break
                if((change.position-start).getDistance()>slop){dragged=true;break}
                if(!change.pressed)break
            }
            if(!dragged) {
                runCatching {focus.requestFocus()}
                keyboard?.show()
            }
        }
    }
}
@Composable private fun StatCell(value:String,modifier:Modifier,focus:FocusRequester,tag:String,onValue:(String)->Unit) {
    val c=LocalInstrument.current
    var focused by remember {mutableStateOf(false)}
    Box(modifier.fillMaxHeight().background(if(focused)c.accent.copy(alpha=.12f) else c.display).then(statCellTouch(focus))) {
        BasicTextField(value,onValue,Modifier.fillMaxSize().focusRequester(focus).onFocusChanged {focused=it.isFocused}.testTag(tag),
            textStyle=MaterialTheme.typography.bodyMedium.copy(fontSize=12.sp,color=c.ink),singleLine=true,cursorBrush=SolidColor(c.accent),
            decorationBox={innerTextField->Box(Modifier.fillMaxSize().padding(horizontal=8.dp),contentAlignment=Alignment.CenterStart){innerTextField()}})
    }
}

@Composable private fun StepKey(label:String,description:String,onClick:()->Unit) {
    val c=LocalInstrument.current
    Box(Modifier.size(32.dp).border(1.dp,c.muted.copy(alpha=.4f)).clickable(onClick=onClick).semantics(mergeDescendants=true){contentDescription=description},contentAlignment=Alignment.Center){Text(label,fontSize=15.sp,color=c.ink)}
}
@Composable private fun DimStepper(label:String,value:Int,range:IntRange,labelWidth:Dp?=null,onValue:(Int)->Unit) {
    val c=LocalInstrument.current
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,if(labelWidth==null)Modifier else Modifier.width(labelWidth),fontSize=11.sp,color=c.muted)
        StepKey("−","Decrease $label"){onValue((value-1).coerceIn(range))}
        Text("$value",Modifier.widthIn(min=20.dp),textAlign=TextAlign.Center,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        StepKey("+","Increase $label"){onValue((value+1).coerceIn(range))}
    }
}
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
            Modifier.fillMaxSize().focusRequester(focus)
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
private fun isSquareRootExponent(exponent:JSONObject?):Boolean {
    val value=if(exponent?.optString("kind")=="group")exponent.optJSONArray("args")?.optJSONObject(0) else exponent
    val parts=value?.optJSONArray("args")
    val numerator=parts?.optJSONObject(0)
    val denominator=parts?.optJSONObject(1)
    return value?.optString("kind")=="binary"&&value.optString("value")=="/"&&
        numerator?.optString("kind")=="number"&&numerator.optString("value")=="1"&&
        denominator?.optString("kind")=="number"&&denominator.optString("value")=="2"
}

// Stored values keep the engine result tree; convert it back to source text so operands can be shown as matrices.
private fun treeSource(node:JSONObject?):String? {
    if(node==null)return null
    val args=node.optJSONArray("args")
    fun child(index:Int):String?=treeSource(args?.optJSONObject(index))
    fun children():String? {
        val count=args?.length() ?: return null
        val parts=ArrayList<String>(count)
        for(index in 0 until count)parts.add(child(index) ?: return null)
        return parts.joinToString(",")
    }
    return when(node.optString("kind")) {
        "list"->children()?.let{"[$it]"}
        "tuple"->children()?.let{"($it${if(args?.length()==1) "," else ""})"}
        "set"->children()?.let{"{"+it+"}"}
        "number","float","symbol","snapshot_symbol"->node.optString("value").takeIf{it.isNotBlank()}
        "constant"->when(node.optString("value")){"pi"->"pi";"E"->"e";"I"->"i";"oo"->"oo";"-oo"->"-oo";"True"->"true";"False"->"false";else->null}
        "group"->child(0)?.let{"($it)"}
        "binary","relation"->{
            val a=child(0) ?:return null
            if(node.optString("value")=="^"&&isSquareRootExponent(args?.optJSONObject(1)))"sqrt($a)"
            else {val b=child(1) ?:return null;"($a)${node.optString("value")}($b)"}
        }
        "unary"->{val a=child(0) ?:return null;"${node.optString("value","-")}($a)"}
        "restricted"->child(0)
        "call","frozen_call"->{val name=node.optString("value");val inner=children() ?:return null;if(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")))"$name($inner)" else null}
        else->null
    }
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

@Composable fun StatisticsScreen(m: CalculatorModel) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    val scope=rememberCoroutineScope()
    val panelScroll=rememberScrollState()
    var summaryResultPending by remember {mutableStateOf(false)}
    LaunchedEffect(m.result) {
        if(summaryResultPending&&m.result!=null){panelScroll.animateScrollTo(panelScroll.maxValue);summaryResultPending=false}
    }
    val names=remember(m.dataSets) {m.dataSets.keys().asSequence().toList().sorted()}
    var selected by rememberSaveable {mutableStateOf(m.statisticsSelected)}
    var isNew by rememberSaveable {mutableStateOf(m.statisticsIsNew)}
    val activeName=if(isNew)"" else selected.ifBlank {names.firstOrNull().orEmpty()}
    var datasetName by rememberSaveable {mutableStateOf(m.statisticsName)}
    var data by rememberSaveable {mutableStateOf(m.statisticsData)}
    var dataKind by rememberSaveable {mutableStateOf(m.statisticsKind)}
    var regression by rememberSaveable {mutableStateOf(m.statisticsRegression)}
    var customFormula by rememberSaveable {mutableStateOf(m.statisticsCustomFormula)}
    var customVariable by rememberSaveable {mutableStateOf(m.statisticsCustomVariable)}
    var customInitials by rememberSaveable {mutableStateOf(m.statisticsCustomInitials)}
    var plotType by rememberSaveable {mutableStateOf(m.statisticsPlot)}
    var csv by rememberSaveable {mutableStateOf(m.statisticsCsv)}
    LaunchedEffect(data,datasetName,dataKind,regression,plotType,csv,selected,isNew,customFormula,customVariable,customInitials) {m.saveStatistics(datasetName,data,dataKind,regression,plotType,csv,selected,isNew,customFormula,customVariable,customInitials)}
    val importCsv=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)scope.launch {
            val content=withContext(Dispatchers.IO) {runCatching {context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {it.readText()}}.getOrNull()}
            if(content!=null) {
                data=content.trimEnd('\r','\n')
                val first=data.lineSequence().firstOrNull {it.isNotBlank()}.orEmpty().splitCsvRecord()
                dataKind=if(first.size>=2)"xy" else "list"
                datasetName=datasetName.ifBlank {activeName.ifBlank {"D1"}}
                m.saveDataSet(datasetName,data,dataKind);selected=datasetName;isNew=false
            } else m.error="Could not read the selected CSV file"
        }
    }
    val exportCsv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {uri->
        if(uri!=null)scope.launch {
            val success=withContext(Dispatchers.IO) {runCatching {val stream=context.contentResolver.openOutputStream(uri)?:error("No output stream");stream.bufferedWriter().use {it.write(data)};true}.getOrDefault(false)}
            if(!success)m.error="Could not write the CSV file"
        }
    }
    fun rows():List<List<String>> {
        val normalized=data.replace("\r\n","\n").replace('\r','\n')
        val lines=mutableListOf<String>();var start=0
        normalized.forEachIndexed {index,char->if(char=='\n'){lines+=normalized.substring(start,index);start=index+1}}
        lines+=normalized.substring(start)
        return lines.map {it.splitCsvRecord().map(String::trim)}
            .filterIndexed {index,row->!(index==0&&row.firstOrNull()?.lowercase() in listOf("x","n","value","y"))}
    }
    fun vector(column:Int)=rows().mapNotNull {it.getOrNull(column)?.takeIf(String::isNotBlank)}.joinToString(",","[","]")
    fun variableSource():String=if(dataKind=="list")vector(0) else rows().filter {row->row.getOrNull(0).orEmpty().isNotBlank()&&row.getOrNull(1).orEmpty().isNotBlank()}.joinToString(",","[","]") {row->"[${row[0]},${row[1]}]"}
    fun startNew() {
        var index=1;val existing=names.toSet();while("D$index" in existing)index++
        datasetName="D$index";data=if(dataKind=="xy")"," else "";isNew=true;selected=""
    }
    val parsedRows=rows()
    val xValues=parsedRows.mapNotNull {it.getOrNull(0)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}}
    val yValues=if(dataKind=="xy")parsedRows.mapNotNull {it.getOrNull(1)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}} else emptyList()
    val paired=parsedRows.mapNotNull {row->val x=row.getOrNull(0)?.toDoubleOrNull();val y=row.getOrNull(1)?.toDoubleOrNull();if(x!=null&&y!=null&&x.isFinite()&&y.isFinite())x to y else null}
    var section by rememberSaveable {mutableStateOf("Data")}
    if(section=="Data") Panel("Data & statistics","Enter values once, then summarize, test, or plot the current dataset.",panelScroll) {
        Choices(listOf("Data & analysis","Distributions"),"Data & analysis",{section=if(it=="Data & analysis")"Data" else it})
        if(names.isNotEmpty())Choices(names,activeName,{name->selected=name;isNew=false;m.dataSets.optJSONObject(name)?.let {item->datasetName=name;data=item.optString("csv");dataKind=item.optString("kind","list");plotType=if(dataKind=="xy")"Scatter" else "Histogram"}})
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            Field(datasetName,"Dataset name",Modifier.weight(1f)){datasetName=it}
            SmallAction("New"){startNew()}
            SmallAction("Save"){m.saveDataSet(datasetName,data,dataKind);selected=datasetName;isNew=false}
            SmallAction("Delete"){if(activeName.isNotBlank()){m.deleteDataSet(activeName);selected="";isNew=true;startNew()}}
        }
        Choices(listOf("List","x,y data"),if(dataKind=="xy")"x,y data" else "List",{dataKind=if(it=="x,y data")"xy" else "list";plotType=if(dataKind=="xy")"Scatter" else "Histogram"})
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            SmallAction("Import CSV"){importCsv.launch(arrayOf("text/csv","text/comma-separated-values","text/plain","application/vnd.ms-excel"))}
            SmallAction("Export CSV"){exportCsv.launch("${datasetName.ifBlank {"dataset"}}.csv")}
            SmallAction("Store as $datasetName"){if(datasetName.matches(Regex("[A-Za-z][A-Za-z0-9_]*")))m.store(datasetName,variableSource(),false)else m.error="Dataset name must be a valid variable name"}
            SmallAction(if(csv)"Table editor" else "Direct input"){csv=!csv}
            SmallAction("Add row"){if(parsedRows.size<999)data+=if(dataKind=="xy")"\n," else "\n"}
        }
        if(csv)OutlinedTextField(data,{data=it},Modifier.fillMaxWidth().height(180.dp),label={Text(if(dataKind=="xy") {if(isKorean())"x, y 값" else "x, y values"} else tr("One value per line"))},textStyle=MaterialTheme.typography.bodyLarge.copy(fontFamily=FontFamily.Monospace))
        else {
            val grid=LocalInstrument.current.grid
            val tableColumns=if(dataKind=="xy")listOf("x","y") else listOf("value")
            Column(Modifier.fillMaxWidth().border(1.dp,grid).heightIn(max=300.dp).verticalScroll(rememberScrollState()).testTag("statistics-table")) {
                Row(Modifier.fillMaxWidth().height(30.dp).background(LocalInstrument.current.scientific)) {
                    StatHeader("#",Modifier.width(30.dp)); VerticalDivider(color=grid,thickness=1.dp)
                    tableColumns.forEach {name->StatHeader(name,Modifier.weight(1f));VerticalDivider(color=grid,thickness=1.dp)}
                    StatHeader("",Modifier.width(48.dp))
                }
                HorizontalDivider(color=grid,thickness=1.dp)
                parsedRows.forEachIndexed {index,row->
                    val cellFocus=remember(index,tableColumns.size) {List(tableColumns.size){FocusRequester()} }
                    Row(Modifier.fillMaxWidth().height(44.dp)) {
                        Box(Modifier.width(30.dp).fillMaxHeight().then(statCellTouch(cellFocus.first())),contentAlignment=Alignment.Center){Text("${index+1}",fontSize=12.sp,color=LocalInstrument.current.muted)}
                        VerticalDivider(color=grid,thickness=1.dp)
                        repeat(tableColumns.size) {column->
                            StatCell(row.getOrElse(column){""},Modifier.weight(1f),cellFocus[column],"statistics-cell-$index-$column") {text->
                                val next=parsedRows.map {it.toMutableList().apply {while(size<tableColumns.size)add("")}}.toMutableList();next[index][column]=text;data=next.joinToString("\n"){it.joinToString(",")}
                            }
                            VerticalDivider(color=grid,thickness=1.dp)
                        }
                        Box(Modifier.width(48.dp).fillMaxHeight(),contentAlignment=Alignment.Center){SmallAction("−"){data=parsedRows.filterIndexed {i,_->i!=index}.joinToString("\n"){it.joinToString(",")}}}
                    }
                    if(index<parsedRows.lastIndex)HorizontalDivider(color=grid,thickness=1.dp)
                }
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(tr("Quick summaries"),style=MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                fun summarize(command:String) {summaryResultPending=true;m.edit(Editor(command));m.calculate();scope.launch {panelScroll.animateScrollTo(panelScroll.maxValue)}}
                SmallAction(if(dataKind=="xy")"x" else "List",translate=false){val values=vector(0);if(values!="[]")summarize("stats($values)")}
                if(dataKind=="xy")SmallAction("y",translate=false){val values=vector(1);if(values!="[]")summarize("stats($values)")}
                val correlationCommand=statisticsCorrelationCommand(parsedRows,dataKind)
                if(dataKind=="xy")SmallAction("correlation",active=if(correlationCommand==null)false else null,translate=false,modifier=Modifier.testTag("statistics-correlation")){correlationCommand?.let {summarize(it)}}
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Visualize"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                if(dataKind=="xy")SmallAction("Clear regression"){m.clearRegression()}
            }
            val activeRegression=if(m.regressionFit.isNotBlank()&&m.regressionData==data)m.regressionMode else ""
            if(dataKind=="xy")Choices(listOf("linear","quadratic","logarithmic","exponential","power","custom"),if(regression=="custom")"custom" else activeRegression,{selectedMode->
                regression=selectedMode;plotType="Scatter"
                if(selectedMode=="custom")m.clearRegression()
                if(selectedMode!="custom") {
                    val table=parsedRows.filter {it.size>=2&&it[0].isNotBlank()&&it[1].isNotBlank()}.joinToString(",","[","]"){it.take(2).joinToString(",","[","]")}
                    m.fitRegression("regression($table,$selectedMode)",data)
                }
            })
            if(dataKind=="xy"&&regression=="custom") {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    SmallAction("ADC example"){m.clearRegression();customFormula="exp(-b*ADC)";customVariable="b";customInitials=""}
                    SmallAction("IVIM example"){m.clearRegression();customFormula="(1-f)*exp(-b*D)+f*exp(-b*Dstar)";customVariable="b";customInitials="[[f,0.2,0,1],[D,0.001,0],[Dstar,0.01,0]]"}
                    SmallAction("Exponential decay example"){m.clearRegression();customFormula="A*exp(-k*x)+C";customVariable="x";customInitials=""}
                }
                Field(customFormula,"Model y =",Modifier.fillMaxWidth()){m.clearRegression();customFormula=it}
                Field(customVariable,"Independent variable",Modifier.fillMaxWidth()){m.clearRegression();customVariable=it}
                Field(customInitials,"Initial values and bounds (optional)",Modifier.fillMaxWidth()){m.clearRegression();customInitials=it}
                Text(if(isKorean())"형식: [[매개변수1, 시작값, 하한, 상한], [매개변수2, 시작값, 하한, 상한]]; 상한은 생략할 수 있습니다."
                    else "Format: [[parameter1, initial, lower, upper], [parameter2, initial, lower, upper]]; upper bound can be omitted.",fontSize=11.sp,color=LocalInstrument.current.muted)
                Button(onClick={
                    val table=parsedRows.filter {it.size>=2&&it[0].isNotBlank()&&it[1].isNotBlank()}.joinToString(",","[","]"){it.take(2).joinToString(",","[","]")}
                    val guesses=customInitials.trim().takeIf(String::isNotEmpty)?.let {",$it"}.orEmpty()
                    m.fitRegression("regression($table,custom,$customFormula,$customVariable$guesses)",data)
                },enabled=customFormula.isNotBlank()&&customVariable.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))&&paired.size>=2&&!m.regressionBusy){Text(tr("Fit custom model"))}
            }
            Choices(if(dataKind=="xy")listOf("Scatter","Histogram","Box plot") else listOf("Histogram","Box plot"),plotType,{plotType=it})
        }
        val fitVisible=dataKind=="xy"&&plotType=="Scatter"&&m.regressionData==data&&m.regressionFit.isNotBlank()
        StatisticsPlot(plotType,if(plotType=="Scatter")paired else xValues.mapIndexed {i,v->i.toDouble() to v},xValues,yValues,if(fitVisible)m.regressionCurve.orEmpty() else emptyList(),if(fitVisible)m.regressionFit else "",m.displayDigits,fitVisible&&m.regressionMode=="linear",m.regressionCorrelation)
        if(m.regressionBusy)Text(if(isKorean())"회귀 적합 중…" else "Fitting regression…",fontSize=11.sp,color=LocalInstrument.current.muted)
        if(dataKind=="xy"&&m.regressionData==data&&m.regressionFit.isNotBlank()&&m.regressionParameters.isNotEmpty()) {
            Text(tr("Fitted parameters"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                m.regressionParameters.sortedWith(compareBy({listOf("f","ADC","D","Dstar").indexOf(it.first).let {index->if(index<0)Int.MAX_VALUE else index}},{it.first})).forEach {(name,value)->
                    val label=if(name=="Dstar")"D*" else name
                    val displayedValue=ResultDisplayFormat.formatText(value,m.resultDisplayMode,m.thousandsSeparator,maxFractionDigits=m.displayDigits)
                    val korean=isKorean()
                    TextButton(onClick={
                        clipboard.setText(AnnotatedString(value))
                        android.widget.Toast.makeText(context,if(korean)"$label 값 복사됨" else "$label copied",android.widget.Toast.LENGTH_SHORT).show()
                    },contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp),
                        modifier=Modifier.semantics {contentDescription=if(korean)"$label 값 복사" else "Copy $label value"}) {
                        Text("$label = $displayedValue  ⧉",fontSize=11.sp,fontFamily=FontFamily.Monospace)
                    }
                }
            }
        }
        if(dataKind=="xy"&&m.regressionData==data&&m.regressionFit.isNotBlank())SmallAction("Graph fitted expression"){
            val fit=if(m.regressionMode=="custom")m.regressionFit.replace(Regex("(?<![A-Za-z0-9_])${Regex.escape(customVariable)}(?![A-Za-z0-9_])"),"x") else m.regressionFit
            m.changeGraphKind("cartesian");m.updateGraphSource(fit.replace("**","^"));m.mode="Graph";m.plot()
        }
        StatisticsAnalysis(m,parsedRows,dataKind)
        Display(m,requestInitialFocus=false)
    } else {
        Panel(section,"") {
            Choices(listOf("Data & analysis","Distributions"),section,{section=if(it=="Data & analysis")"Data" else it})
            DistributionSection(m)
        }
    }
}

private fun String.splitCsvRecord():List<String> {
    val cells=mutableListOf<String>();val current=StringBuilder();var quoted=false;var i=0
    while(i<length) {
        val ch=this[i]
        when {
            ch=='"'&&quoted&&i+1<length&&this[i+1]=='"'->{current.append('"');i++}
            ch=='"'->quoted=!quoted
            ch==','&&!quoted->{cells+=current.toString().trim();current.setLength(0)}
            else->current.append(ch)
        }
        i++
    }
    cells+=current.toString().trim();return cells
}

@Composable private fun StatisticsPlot(type:String,points:List<Pair<Double,Double>>,values:List<Double>,secondary:List<Double> = emptyList(),curve:List<Pair<Double,Double>> = emptyList(),fitLabel:String="",displayDigits:Int=10,showCorrelation:Boolean=false,correlation:Double?=null) {
    val c=LocalInstrument.current
    val fitEquation=remember(fitLabel,displayDigits) {if(fitLabel.isBlank())null else regressionFormulaDisplayTree(fitLabel,displayDigits)}
    Canvas(Modifier.fillMaxWidth().height(220.dp).background(c.display)) {
        val left=38.dp.toPx();val right=12.dp.toPx();val top=14.dp.toPx();val bottom=28.dp.toPx()
        val width=size.width-left-right;val height=size.height-top-bottom
        val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=c.muted.toArgb();textSize=10.sp.toPx()}
        drawLine(c.grid,Offset(left,top+height),Offset(left+width,top+height),1.dp.toPx())
        drawLine(c.grid,Offset(left,top),Offset(left,top+height),1.dp.toPx())
        if(type=="Scatter") {
            if(points.isEmpty())return@Canvas
            val range=if(curve.isEmpty())points else points+curve
            var x0=range.minOf {it.first};var x1=range.maxOf {it.first};var y0=range.minOf {it.second};var y1=range.maxOf {it.second}
            if(x0==x1){x0-=1;x1+=1};if(y0==y1){y0-=1;y1+=1}
            fun px(x:Double)=left+((x-x0)/(x1-x0)).toFloat()*width
            fun py(y:Double)=top+height-((y-y0)/(y1-y0)).toFloat()*height
            if(curve.size>1) {
                val path=androidx.compose.ui.graphics.Path()
                curve.forEachIndexed {index,point->val at=Offset(px(point.first),py(point.second));if(index==0)path.moveTo(at.x,at.y) else path.lineTo(at.x,at.y)}
                drawPath(path,c.danger,style=Stroke(2.dp.toPx()))
            }
            points.forEach {drawCircle(c.accent,4.dp.toPx(),Offset(px(it.first),py(it.second)))}
            drawContext.canvas.nativeCanvas.drawText("x",left+width-4,top+height+20.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText("y",5.dp.toPx(),top+12.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText("%.4g".format(x0),left,top+height+16.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText("%.4g".format(x1),left+width-34.dp.toPx(),top+height+16.dp.toPx(),text)
        } else if(values.isEmpty()&&secondary.isEmpty()) {
            drawContext.canvas.nativeCanvas.drawText("Add finite numeric observations to plot",left,top+20.dp.toPx(),text)
        } else if(type=="Histogram") {
            val series=if(secondary.isEmpty())listOf(Triple("",values,c.accent)) else listOf(Triple("x",values,c.accent),Triple("y",secondary,c.danger)).filter {it.second.isNotEmpty()}
            val all=series.flatMap {it.second}
            var lo=all.min();var hi=all.max();if(lo==hi){lo-=.5;hi+=.5}
            val bins=ceil(1+ln(all.size.coerceAtLeast(2).toDouble())/ln(2.0)).toInt().coerceIn(3,14)
            val counts=series.map {entry->IntArray(bins).also {buckets->entry.second.forEach {v->buckets[((v-lo)/(hi-lo)*bins).toInt().coerceIn(0,bins-1)]++}}}
            val peak=counts.maxOf {it.maxOrNull() ?: 0}.coerceAtLeast(1)
            val bar=width/bins;val lane=(bar-2).coerceAtLeast(1f)/series.size
            counts.forEachIndexed {seriesIndex,buckets->buckets.forEachIndexed {index,count->
                val h=height*count/peak
                drawRect(series[seriesIndex].third.copy(alpha=.78f),Offset(left+index*bar+1+lane*seriesIndex,top+height-h),androidx.compose.ui.geometry.Size((lane-1).coerceAtLeast(1f),h))
            }}
            if(series.size==1&&series[0].first.isEmpty())drawContext.canvas.nativeCanvas.drawText("${values.size} values · $bins bins",left,top+11.dp.toPx(),text)
            else {
                var cursor=left
                series.forEach {entry->val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=entry.third.toArgb();textSize=10.sp.toPx()};val label="${entry.first} ${entry.second.size}";drawContext.canvas.nativeCanvas.drawText(label,cursor,top+11.dp.toPx(),paint);cursor+=paint.measureText(label)+8.dp.toPx()}
                drawContext.canvas.nativeCanvas.drawText("· $bins bins",cursor,top+11.dp.toPx(),text)
            }
            drawContext.canvas.nativeCanvas.drawText("%.4g".format(lo),left,top+height+16.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText("%.4g".format(hi),left+width-34.dp.toPx(),top+height+16.dp.toPx(),text)
        } else {
            val series=if(secondary.isEmpty())listOf(Triple("",values,c.accent)) else listOf(Triple("x",values,c.accent),Triple("y",secondary,c.danger)).filter {it.second.isNotEmpty()}
            fun quantile(data:List<Double>,p:Double):Double {val position=(data.size-1)*p;val low=floor(position).toInt();val high=ceil(position).toInt();return data[low]+(data[high]-data[low])*(position-low)}
            val all=series.flatMap {it.second}
            var minValue=all.min();var maxValue=all.max();if(minValue==maxValue){minValue-=.5;maxValue+=.5}
            fun px(value:Double)=left+((value-minValue)/(maxValue-minValue)).toFloat()*width
            series.forEachIndexed {index,entry->
                val sorted=entry.second.sorted()
                val lo=sorted.first();val q1=quantile(sorted,.25);val median=quantile(sorted,.5);val q3=quantile(sorted,.75);val hi=sorted.last()
                val single=series.size==1
                val y=if(single)top+height/2 else top+height*(index+.5f)/series.size
                val half=if(single)20.dp.toPx() else 15.dp.toPx()
                drawLine(c.muted,Offset(px(lo),y),Offset(px(hi),y),2.dp.toPx())
                drawLine(c.muted,Offset(px(lo),y-9.dp.toPx()),Offset(px(lo),y+9.dp.toPx()),2.dp.toPx())
                drawLine(c.muted,Offset(px(hi),y-9.dp.toPx()),Offset(px(hi),y+9.dp.toPx()),2.dp.toPx())
                drawRect(entry.third.copy(alpha=.24f),Offset(px(q1),y-half),androidx.compose.ui.geometry.Size((px(q3)-px(q1)).coerceAtLeast(1f),half*2))
                drawRect(entry.third,Offset(px(q1),y-half),androidx.compose.ui.geometry.Size((px(q3)-px(q1)).coerceAtLeast(1f),half*2),style=Stroke(1.5.dp.toPx()))
                drawLine(c.danger,Offset(px(median),y-half),Offset(px(median),y+half),2.dp.toPx())
                val prefix=if(entry.first.isEmpty())"" else entry.first+"  "
                val paint=if(entry.first.isEmpty())text else Paint(Paint.ANTI_ALIAS_FLAG).apply {color=entry.third.toArgb();textSize=10.sp.toPx()}
                val summary="min %.4g   Q1 %.4g   median %.4g   Q3 %.4g   max %.4g".format(lo,q1,median,q3,hi)
                drawContext.canvas.nativeCanvas.drawText(prefix+summary,left,top+(13+index*14).dp.toPx(),paint)
            }
        }
    }
    if(type=="Scatter" && fitEquation!=null)CompositionLocalProvider(LocalMathMinimumSize provides 8f) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            MathText("●",11f,Modifier.alignBy(MathAxis),tint=c.danger)
            MathText("y ≈ ",12f,Modifier.alignBy(MathAxis))
            Box(Modifier.alignBy(MathAxis)){MathNode(fitEquation,12f)}
            if(showCorrelation) {
                MathText("    r = ",12f,Modifier.alignBy(MathAxis))
                MathText(correlation?.let {"%.3f".format(java.util.Locale.US,it)} ?: "—",12f,Modifier.alignBy(MathAxis))
            }
        }
    }
}

@Composable private fun DistributionSection(m: CalculatorModel) {
    val c=LocalInstrument.current
    var family by rememberSaveable {mutableStateOf("Normal")}
    var query by rememberSaveable {mutableStateOf("Cumulative P(X ≤ x)")}
    var mu by rememberSaveable {mutableStateOf("0")}
    var sigma by rememberSaveable {mutableStateOf("1")}
    var df by rememberSaveable {mutableStateOf("10")}
    var df2 by rememberSaveable {mutableStateOf("10")}
    var trials by rememberSaveable {mutableStateOf("10")}
    var success by rememberSaveable {mutableStateOf("0.5")}
    var poissonMean by rememberSaveable {mutableStateOf("2")}
    var x by rememberSaveable {mutableStateOf("1")}
    var low by rememberSaveable {mutableStateOf("-1.96")}
    var high by rememberSaveable {mutableStateOf("1.96")}
    var probability by rememberSaveable {mutableStateOf("0.975")}
    var k by rememberSaveable {mutableStateOf("3")}
    val queries=when(family) {
        "Normal","Student t" -> listOf("Density f(x)","Cumulative P(X ≤ x)","Interval P(a ≤ X ≤ b)","Quantile")
        "χ²","F" -> listOf("Density f(x)","Cumulative P(X ≤ x)","Interval P(a ≤ X ≤ b)")
        "Binomial" -> listOf("P(X = k)","P(X ≤ k)","List P(X = k)","List P(X ≤ k)")
        else -> listOf("P(X = k)","P(X ≤ k)")
    }
    val active=query.takeIf {it in queries} ?: queries[1]
    fun expression():String=when(family) {
        "Normal" -> when(active) {
            "Density f(x)" -> "normpdf($x,$mu,$sigma)"
            "Interval P(a ≤ X ≤ b)" -> "normcdf($low,$high,$mu,$sigma)"
            "Quantile" -> "invnorm($probability,$mu,$sigma)"
            else -> "normcdf(-oo,$x,$mu,$sigma)"
        }
        "Student t" -> when(active) {
            "Density f(x)" -> "tpdf($x,$df)"
            "Interval P(a ≤ X ≤ b)" -> "tcdf($low,$high,$df)"
            "Quantile" -> "invt($probability,$df)"
            else -> "tcdf($x,$df)"
        }
        "χ²" -> when(active) {
            "Density f(x)" -> "chi2pdf($x,$df)"
            "Interval P(a ≤ X ≤ b)" -> "chi2cdf($low,$high,$df)"
            else -> "chi2cdf($x,$df)"
        }
        "F" -> when(active) {
            "Density f(x)" -> "fpdf($x,$df,$df2)"
            "Interval P(a ≤ X ≤ b)" -> "fcdf($low,$high,$df,$df2)"
            else -> "fcdf($x,$df,$df2)"
        }
        "Binomial" -> when(active) {
            "P(X = k)" -> "binompdf($trials,$success,$k)"
            "P(X ≤ k)" -> "binomcdf($trials,$success,$k)"
            "List P(X = k)" -> "binompdf($trials,$success)"
            else -> "binomcdf($trials,$success)"
        }
        "Poisson" -> if(active=="P(X = k)")"poissonpdf($poissonMean,$k)" else "poissoncdf($poissonMean,$k)"
        else -> if(active=="P(X = k)")"geometpdf($success,$k)" else "geometcdf($success,$k)"
    }
    fun ready():Boolean {
        val used=when(family) {
            "Normal" -> when(active) {
                "Interval P(a ≤ X ≤ b)" -> listOf(low,high,mu,sigma)
                "Quantile" -> listOf(probability,mu,sigma)
                else -> listOf(x,mu,sigma)
            }
            "Student t" -> when(active) {
                "Interval P(a ≤ X ≤ b)" -> listOf(low,high,df)
                "Quantile" -> listOf(probability,df)
                else -> listOf(x,df)
            }
            "χ²" -> if(active=="Interval P(a ≤ X ≤ b)")listOf(low,high,df) else listOf(x,df)
            "F" -> if(active=="Interval P(a ≤ X ≤ b)")listOf(low,high,df,df2) else listOf(x,df,df2)
            "Binomial" -> if(active.startsWith("List"))listOf(trials,success) else listOf(trials,success,k)
            "Poisson" -> listOf(poissonMean,k)
            else -> listOf(success,k)
        }
        return used.all {it.isNotBlank()}
    }
    Text(tr("Distribution"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
    Choices(listOf("Normal","Student t","χ²","F","Binomial","Poisson","Geometric"),family,{family=it})
    Text(tr("Query"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
    Choices(queries,active,{query=it})
    Text(tr("Parameters"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
    when(family) {
        "Student t","χ²" -> Field(df,"Degrees of freedom",Modifier.fillMaxWidth()){df=it}
        "F" -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Field(df,"df₁",Modifier.weight(1f)){df=it}
            Field(df2,"df₂",Modifier.weight(1f)){df2=it}
        }
        "Binomial" -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Field(trials,"Trials n",Modifier.weight(1f)){trials=it}
            Field(success,"Success probability p",Modifier.weight(1f)){success=it}
        }
        "Poisson" -> Field(poissonMean,"Mean λ",Modifier.fillMaxWidth()){poissonMean=it}
        "Geometric" -> Field(success,"Success probability p",Modifier.fillMaxWidth()){success=it}
        else -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Field(mu,"Mean μ",Modifier.weight(1f)){mu=it}
            Field(sigma,"Standard deviation σ",Modifier.weight(1f)){sigma=it}
        }
    }
    Text(tr("Input"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
    when(active) {
        "Interval P(a ≤ X ≤ b)" -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Field(low,"a (lower bound)",Modifier.weight(1f)){low=it}
            Field(high,"b (upper bound)",Modifier.weight(1f)){high=it}
        }
        "Quantile" -> Field(probability,"Probability p (0–1)",Modifier.fillMaxWidth()){probability=it}
        "P(X = k)","P(X ≤ k)" -> Field(k,"k",Modifier.fillMaxWidth()){k=it}
        "List P(X = k)","List P(X ≤ k)" -> Text(if(isKorean())"단일 입력 없음: k=0부터 n까지의 값이 나열됩니다." else "No single input: every k from 0 to n is listed.",fontSize=11.sp,color=c.muted)
        else -> Field(x,"x",Modifier.fillMaxWidth()){x=it}
    }
    Text((if(isKorean())"수식  " else "Expression  ")+expression(),fontFamily=FontFamily.Monospace,fontSize=11.sp,color=c.muted)
    Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
        Button(onClick={m.edit(Editor(expression()));m.calculate()},enabled=ready()){Text(tr("Compute"))}
        SmallAction("Insert into calculator"){m.edit(Editor(expression()));m.mode="Scientific/CAS"}
    }
    Display(m,requestInitialFocus=false)
    Text(if(isKorean())"닫힌 형태가 있으면 정확값으로 표시합니다. 나머지 확률은 내부 정밀도를 사용합니다. 이항분포 목록은 n ≤ 100이어야 하며, 정규 누적확률에서 μ와 σ를 적용하려면 하한으로 -oo를 사용합니다." else "Closed forms stay exact where the engine has one; other probabilities use the internal precision. Binomial list forms need n ≤ 100, and the normal cumulative uses -oo as its lower bound so μ and σ apply.",fontSize=11.sp,color=c.muted)
}

@Composable private fun StatisticsAnalysis(m: CalculatorModel,rows:List<List<String>>,kind:String) {
    val c=LocalInstrument.current
    var test by rememberSaveable {mutableStateOf("t test")}
    var column by rememberSaveable {mutableStateOf("x")}
    var tail by rememberSaveable {mutableStateOf("Two-sided")}
    var mu0 by rememberSaveable {mutableStateOf("0")}
    var sigma by rememberSaveable {mutableStateOf("2")}
    var sigmaY by rememberSaveable {mutableStateOf("2")}
    var level by rememberSaveable {mutableStateOf("95")}
    val columnOptions=when {
        kind!="xy"->listOf("x")
        test=="t test"->listOf("x","y","x-y","paired")
        test=="z test"->listOf("x","y","x-y")
        else->listOf("x","y")
    }
    val activeColumn=column.takeIf {it in columnOptions} ?: "x"
    fun values(index:Int)=rows.mapNotNull {it.getOrNull(index)?.trim()?.takeIf(String::isNotBlank)}
    val x=values(0)
    val y=if(kind=="xy")values(1) else null
    val sample=if(kind=="xy"&&activeColumn=="y")y else x
    val pairCount=if(kind=="xy")rows.count {it.getOrNull(0)?.isNotBlank()==true&&it.getOrNull(1)?.isNotBlank()==true} else 0
    val twoColumnTest=test=="χ² test"||test=="Fisher exact"||test=="ANOVA"
    val twoSample=kind=="xy"&&activeColumn=="x-y"&&(test=="t test"||test=="z test")
    val pairedTest=kind=="xy"&&activeColumn=="paired"&&test=="t test"
    val command=statisticsTestCommand(test,rows,kind,activeColumn,tail,mu0,sigma,level,sigmaY)
    HorizontalDivider()
    Text(tr("Analyze current data"),style=MaterialTheme.typography.titleMedium)
    Text(if(kind=="xy")"Blank cells are omitted. Paired, χ², and Fisher tests use rows with both values; independent tests use each column separately. Fisher requires exactly two categories per column." else "Blank cells are omitted from tests. Choose x,y data for two-column tests.",fontSize=12.sp,color=c.muted)
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Choices(listOf("t test","z test","χ² test","Fisher exact","ANOVA","Shapiro–Wilk","t interval","z interval"),test,{test=it})
        if(kind=="xy"&&!twoColumnTest)Choices(columnOptions,activeColumn,{column=it})
        if(twoSample||pairedTest)Text(if(pairedTest)"Paired t test uses x−y for rows with both values." else "Independent samples compare the means of x and y.",fontSize=11.sp,color=c.muted)
    }
    if(!twoColumnTest&&test!="Shapiro–Wilk") {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(test=="t test"||test=="z test") {
                    Field(mu0,if(twoSample||pairedTest)"Hypothesized difference Δ₀" else "Hypothesized mean μ₀",Modifier.weight(1f)){mu0=it}
                } else {
                    Field(level,"Confidence level (0–1 or %)",Modifier.weight(1f)){level=it}
                }
                if(test=="z test"||test=="z interval")Field(sigma,if(twoSample)"Known σx" else "Known σ",Modifier.weight(1f)){sigma=it}
            }
            if(test=="z test"&&twoSample)Field(sigmaY,"Known σy",Modifier.fillMaxWidth()){sigmaY=it}
            if(test=="t test"||test=="z test") {
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(tr("Alternative hypothesis"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    Choices(listOf("Two-sided","Left","Right"),tail,{tail=it})
                }
            }
    }
    if(test=="Fisher exact") {
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(tr("Alternative odds ratio (ordered categories)"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            Choices(listOf("Two-sided","Left","Right"),tail,{tail=it})
        }
    }
    val dataStatus=when {
        x.isEmpty()&&(y==null||y.isEmpty())->"Add values to the table to run this analysis."
        twoColumnTest&&kind!="xy"->"Switch to x,y data and enter both columns."
        (pairedTest||test=="χ² test"||test=="Fisher exact")&&pairCount<2->"Enter at least two complete x,y rows."
        (test=="ANOVA"||twoSample&&test=="t test")&&(x.size<2||y==null||y.size<2)->"Enter at least two values in each column."
        twoSample&&(x.isEmpty()||y.isNullOrEmpty())->"Enter values in both columns."
        test=="Shapiro–Wilk"&&(sample?.size ?: 0)<3->"Enter at least three values in the selected column."
        !twoColumnTest&&sample?.isEmpty()==true->"Enter values in the selected column."
        else->"Check the required values and sample size."
    }
    if(command==null)Text(dataStatus,fontSize=12.sp,color=c.muted)
    else Text("${if(pairedTest||test=="χ² test"||test=="Fisher exact")"$pairCount pairs" else if(twoColumnTest||twoSample)"${x.size} x, ${y?.size ?: 0} y" else "${sample?.size ?: 0} values"} ready",fontSize=12.sp,color=c.muted)
    Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
        Button(onClick={command?.let {m.edit(Editor(it));m.calculate()}},enabled=command!=null,modifier=Modifier.testTag("statistics-run-test")){Text(if(test.endsWith("interval"))"Compute interval" else "Run test")}
        SmallAction("Insert expression"){command?.let {m.edit(Editor(it));m.mode="Scientific/CAS"}}
    }
    Text(if(isKorean())"검정의 기본 대립가설은 양측입니다. 신뢰수준은 0.95 또는 95로 입력할 수 있습니다." else "Tests use a two-sided alternative by default. Confidence levels accept 0.95 or 95.",fontSize=11.sp,color=c.muted)
}

@Composable fun ProgrammerScreen(m: CalculatorModel) {
    var a by rememberSaveable { mutableStateOf("255") }; var b by rememberSaveable { mutableStateOf("15") }
    var base by rememberSaveable { mutableIntStateOf(10) }; var width by rememberSaveable { mutableIntStateOf(32) }; var signed by rememberSaveable { mutableStateOf(false) }
    Panel("Programmer","Fixed-width integers · two’s complement · exact bit operations") {
        Choices(listOf("2","8","10","16"),base.toString(),{base=it.toInt()})
        Choices(listOf("8","16","32","64"),width.toString(),{width=it.toInt()})
        Row(verticalAlignment=Alignment.CenterVertically) { Switch(signed,{signed=it}); Text("  "+tr("Signed interpretation")) }
        Field(a,"A · base $base",Modifier.fillMaxWidth()) { a=it }
        Field(b,"B / shift count · base $base",Modifier.fillMaxWidth()) { b=it }
        listOf("","AND","OR","XOR","NOT","NAND","NOR","<<",">>").chunked(3).forEach { row->Row { row.forEach { op->SmallAction(op.ifBlank { "Convert" }) { m.program(a,b,base,width,signed,op) } } } }
        m.result?.optJSONObject("bases")?.let { bases -> listOf("BIN","OCT","DEC","HEX").forEach { key->Text(key,color=LocalInstrument.current.muted,fontSize=11.sp); Text(bases.optString(key),fontFamily=FontFamily.Monospace,fontSize=18.sp) } }
        if(m.busy) Text(if(isKorean())"계산 중…" else "Computing…")
    }
}

val UnitGroups=linkedMapOf(
    "Length" to listOf("m","km","cm","mm","in","ft","yd","mi"),
    "Area" to listOf("m2","cm2","km2","ha","acre"),
    "Volume" to listOf("L","mL","m3","galUS"),
    "Mass" to listOf("kg","g","mg","lb","oz"),
    "Temperature" to listOf("degC","degF","K"),
    "Speed" to listOf("mps","kph","mph","knot"),
    "Acceleration" to listOf("mps2","g0"),
    "Pressure" to listOf("Pa","kPa","bar","atm"),
    "Force" to listOf("N","kN","lbf"),
    "Energy" to listOf("J","kJ","cal","kWh","eV"),
    "Power" to listOf("W","kW"),
    "Time" to listOf("s","min","h","day","ms"),
    "Frequency" to listOf("Hz","kHz","MHz"),
    "Angle" to listOf("rad","deg","grad"),
    "Data" to listOf("bit","byte","kB","KiB","MB","MiB","GB"),
    "Current" to listOf("A","mA","uA"),
    "Charge" to listOf("C","mC","uC"),
    "Voltage" to listOf("V","mV","kV"),
    "Resistance" to listOf("ohm","kohm","Mohm","Ω"),
    "Conductance" to listOf("S","mS"),
    "Capacitance" to listOf("F","uF","nF","pF"),
    "Inductance" to listOf("H","mH","uH"),
    "Magnetic flux" to listOf("Wb","Vs"),
    "Magnetic flux density" to listOf("T","mT","uT"),
    "Amount" to listOf("mol","mmol","umol")
)
@Composable fun UnitsScreen(m: CalculatorModel) {
    var group by rememberSaveable { mutableStateOf("Length") }; var from by rememberSaveable { mutableStateOf("m") }; var to by rememberSaveable { mutableStateOf("ft") }; var value by rememberSaveable { mutableStateOf("1") }
    Panel("Unit conversion","") {
        Choices(UnitGroups.keys.toList(),group,{group=it;from=UnitGroups[it]!![0];to=UnitGroups[it]!![1]},translate=false)
        Field(value,"Value or expression",Modifier.fillMaxWidth()) { value=it }
        Text("From"); Choices(UnitGroups[group]!!,from,{from=it},translate=false)
        Text("To"); Choices(UnitGroups[group]!!,to,{to=it},translate=false)
        Row { Button(onClick={m.edit(Editor("convert($value,$from,$to)"));m.calculate()}) { Text("Convert") }; SmallAction("Swap",translate=false) { val temp=from;from=to;to=temp } }
        Display(m)
        Text("Units in expressions: qty(2,m) + qty(30,cm). Convert a derived quantity with convert(qty(1,kg)*qty(2,mps2),N).",fontSize=11.sp)
    }
}

data class ConstantEntry(val symbol: String,val name: String,val value: String,val unit: String,val exact: Boolean=true)
@Composable fun ConstantsScreen(m: CalculatorModel) {
    var search by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {m.loadConstants()}
    val constants=remember(m.constants) {(0 until (m.constants?.length() ?: 0)).map {i->m.constants!!.getJSONObject(i).let {ConstantEntry(it.getString("symbol"),it.getString("name"),it.getString("value"),it.getString("unit"),it.getBoolean("exact"))}}}
    Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(tr("Scientific constants"),style=MaterialTheme.typography.titleLarge)
        Text(if(isKorean())"SI 정의 상수 및 CODATA 2022 참조값입니다. 누르면 수식에 넣습니다." else "SI defining constants and CODATA 2022 reference values. Tap to insert.",color=LocalInstrument.current.muted,fontSize=12.sp)
        OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth(),label={Text(tr("Search"))},singleLine=true,
            trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""}){Text("\u2715",fontSize=15.sp)}})
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(m.constants==null) Text(if(isKorean())"상수를 불러오는 중…" else "Loading local constants…")
            constants.filter { it.name.contains(search,true)||it.symbol.contains(search,true) }.forEach { item ->
                Column(Modifier.fillMaxWidth().clickable { m.insert(item.symbol);m.mode="Scientific/CAS" }.padding(vertical=8.dp)) {
                    Text("${item.symbol}  ·  ${item.name}",style=MaterialTheme.typography.titleSmall)
                    Text("${item.value} ${item.unit}",fontSize=14.sp)
                    Text(if(item.exact) {if(isKorean())"정확한 정의값" else "Exact definition"} else {if(isKorean())"측정값 · CODATA 2022" else "Measured · CODATA 2022"},fontSize=11.sp,color=LocalInstrument.current.muted)
                }; HorizontalDivider()
            }
            Text(if(isKorean())"출처: NIST physics.nist.gov/cuu/Constants · 측정값은 발표된 정밀도를 유지하며 정확한 물리량이 아닙니다." else "Source: NIST physics.nist.gov/cuu/Constants · Measured values retain their published precision; they are not exact physical quantities.",fontSize=11.sp)
        }
    }
}

@Composable fun SettingsDialog(m: CalculatorModel,close: ()->Unit) {
    val precisionChoices=listOf("10","15","30","50","100","200")
    val displayChoices=listOf("2","3","5","8","10","12","15")
    var customPrecision by rememberSaveable{mutableStateOf(m.precision.toString())}
    var customPrecisionVisible by rememberSaveable{mutableStateOf(m.precision.toString() !in precisionChoices)}
    var customDisplay by rememberSaveable{mutableStateOf(m.displayDigits.toString())}
    var customDisplayVisible by rememberSaveable{mutableStateOf(m.displayDigits.toString() !in displayChoices)}
    AlertDialog(onDismissRequest=close,title={Text(tr("Instrument setup"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(tr("Language")); Choices(listOf("English","한국어"),if(m.language=="ko")"한국어" else "English",{m.language=if(it=="한국어")"ko" else "en";m.save()})
        Text(tr("Appearance")); Choices(listOf("System","Light","Dark"),m.theme,{m.theme=it;m.save()})
        Text(tr("Angle unit")); Choices(listOf("DEG","RAD","GRAD"),m.angle,{m.angle=it;m.recalculatePreview();m.save()})
        Text(tr("Internal precision · numeric algorithms")); Choices(precisionChoices+"Custom",if(customPrecisionVisible)"Custom" else m.precision.toString(),{if(it=="Custom")customPrecisionVisible=true else {customPrecisionVisible=false;m.precision=it.toInt();m.recalculatePreview();m.save()}})
        if(customPrecisionVisible) {Field(customPrecision,"Custom internal precision · 3–200",Modifier.fillMaxWidth()){customPrecision=it};TextButton(onClick={m.precision=customPrecision.toInt();m.recalculatePreview();m.save()},enabled=customPrecision.toIntOrNull() in 3..200){Text(tr("Apply internal precision"))}}
        Text(tr("Display digits · decimal places shown")); Choices(displayChoices+"Custom",if(customDisplayVisible)"Custom" else m.displayDigits.toString(),{if(it=="Custom")customDisplayVisible=true else {customDisplayVisible=false;m.displayDigits=it.toInt();m.recalculatePreview();m.save()}})
        if(customDisplayVisible) {Field(customDisplay,"Custom display digits · 2–200",Modifier.fillMaxWidth()){customDisplay=it};TextButton(onClick={m.displayDigits=customDisplay.toInt();m.recalculatePreview();m.save()},enabled=customDisplay.toIntOrNull() in 2..200){Text(tr("Apply display digits"))}}
        Text(if(isKorean())"내부 정밀도는 적분과 방정식 풀이 같은 수치 계산에 적용됩니다. 표시 자릿수는 결과에 보이는 자릿수만 제한하며 내부 정밀도를 넘지 않습니다. 정확한 정수, 분수, 기호식은 반올림하지 않습니다." else "Internal precision governs numeric algorithms such as integration and solving. Display digits limit the digits a result shows; they never exceed internal precision, and exact integers, fractions and symbolic forms are not rounded.",fontSize=11.sp,color=LocalInstrument.current.muted)
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text("${tr("Input font")} · ${m.inputFont.toInt()} sp")
            CompactSlider(m.inputFont,{m.inputFont=it},Modifier.fillMaxWidth(),valueRange=10f..42f,steps=31,onValueChangeFinished={m.save()})
        }
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text("${tr("Output font")} · ${m.outputFont.toInt()} sp")
            CompactSlider(m.outputFont,{m.outputFont=it},Modifier.fillMaxWidth(),valueRange=10f..48f,steps=37,onValueChangeFinished={m.save()})
        }
        Row(verticalAlignment=Alignment.CenterVertically) { Text(tr("Key vibration"),Modifier.weight(1f)); Switch(m.haptics,{m.haptics=it;m.save()}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text(tr("Key sound"),Modifier.weight(1f)); Switch(m.sound,{m.sound=it;m.save()}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text(tr("Save history locally"),Modifier.weight(1f)); Switch(m.persistHistory,{m.persistHistory=it;m.save()}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text(tr("Bracket auto-close"),Modifier.weight(1f)); Switch(m.autoCloseBrackets,{m.autoCloseBrackets=it;m.save()}) }
    }},confirmButton={TextButton(onClick=close) { Text(tr("Done")) }})
}

@Composable fun HistoryDialog(m: CalculatorModel,close: ()->Unit) {
    val clipboard=LocalClipboardManager.current
    var favorites by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val needle=search.trim()
    val entries=m.history.filter { entry->(!favorites||entry.favorite)&&(needle.isBlank()||entry.source.contains(needle,true)||entry.exact.contains(needle,true)) }
    AlertDialog(onDismissRequest=close,title={Text(tr("Calculation history"))},text={Column(Modifier.fillMaxWidth().heightIn(max=480.dp)) {
        OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth(),label={Text(tr("Search history"))},singleLine=true,
            trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""}){Text("\u2715",fontSize=15.sp)}})
        Choices(listOf("All","Favorites"),if(favorites)"Favorites" else "All",{favorites=it=="Favorites"})
        LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false)) {
            if(entries.isEmpty())item {Text(if(isKorean()) {if(needle.isNotBlank())"\"$needle\"에 맞는 계산이 없습니다." else if(favorites)"즐겨찾기가 없습니다." else "계산 기록이 여기에 표시됩니다."} else if(needle.isNotBlank())"No calculations match \"$needle\"." else if(favorites)"No favorites yet." else "Your calculations will appear here.")}
            items(entries) { entry ->
                val saved=remember(entry) {entry.toTapeEntry()}
                val input=remember(saved.input) {runCatching {JSONObject(saved.input)}.getOrNull()}
                val response=remember(saved.result) {runCatching {JSONObject(saved.result)}.getOrNull()}
                Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        if(input!=null&&entry.source.length<=800&&!largeHistoryTree(input))MathNode(input,13f)
                        else Text(entry.source.historyPreview(),fontFamily=FontFamily.Monospace,fontSize=13.sp)
                    }
                    Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        MathText("= ",20f)
                        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                            if(response!=null&&saved.result.length<=20_000&&!largeHistoryTree(response.optJSONObject(if(m.decimal)"decimalTree" else "tree")))
                                ResultMath(response,m.decimal,20f,displayMode=m.resultDisplayMode,thousandsSeparator=m.thousandsSeparator,displayDigits=m.displayDigits)
                            else Text(entry.exact.historyPreview(),fontFamily=FontFamily.Serif,fontSize=20.sp)
                        }
                    }
                    Text("${entry.mode} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.id))}",fontSize=10.sp,color=LocalInstrument.current.muted)
                    Row { SmallAction("Reuse") { m.edit(Editor(entry.source));m.mode="Scientific/CAS";close() }; SmallAction(if(entry.favorite)"★" else "☆") { m.favorite(entry.id) }; SmallAction("Copy") { clipboard.setText(AnnotatedString(entry.exact)) }; SmallAction("Delete") { m.deleteHistory(entry.id) } }
                }
                HorizontalDivider()
            }
        }
    }},confirmButton={TextButton(onClick=close) { Text(tr("Done")) }},dismissButton={TextButton(onClick={m.clearHistory()}) { Text(tr("Clear all")) }})
}

private fun String.historyPreview(): String =
    if(codePointCount(0,length)>50) substring(0,offsetByCodePoints(0,49))+"…" else this

private fun storedVariableDisplayTree(node:JSONObject):JSONObject {
    val display=JSONObject(node.toString())
    val args=display.optJSONArray("args")
    if(args!=null)for(index in 0 until args.length())args.optJSONObject(index)?.let {args.put(index,storedVariableDisplayTree(it))}
    val radicand=args?.optJSONObject(0)
    return if(display.optString("kind")=="binary"&&display.optString("value")=="^"&&radicand!=null&&isSquareRootExponent(args.optJSONObject(1)))
        JSONObject().put("kind","root").put("args",JSONArray().put(radicand))
    else display
}

@Composable private fun StoredVariableRow(name:String,stored:JSONObject,selected:Boolean,labelSize:Float,valueSize:Float,onSelect:()->Unit) {
    val display=remember(stored){storedVariableDisplayTree(stored)}
    Row(Modifier.fillMaxWidth().clickable(onClick=onSelect).testTag("stored-variable-$name").padding(vertical=4.dp)) {
        MathText("$name = ",labelSize,modifier=Modifier.alignBy(MathAxis),tint=if(selected)LocalInstrument.current.accent else LocalInstrument.current.ink)
        Box(Modifier.alignBy(MathAxis).horizontalScroll(rememberScrollState())) {
            MathNode(display,valueSize)
        }
    }
}

private fun storedVariableNames(variables:JSONObject):List<String> =
    variables.keys().asSequence().toList().sortedWith(compareBy<String> {when(it){"Ans"->0;"M"->1;else->2}}.thenBy {it})

@Composable fun RecallDialog(m: CalculatorModel,close: ()->Unit) {
    val names=storedVariableNames(m.variables)
    AlertDialog(onDismissRequest=close,title={Text(tr("Recall variable"))},text={Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState())) {
        if(names.isEmpty())Text(tr("No stored variables"))
        names.forEach {name->
            m.variables.optJSONObject(name)?.let {stored->
                StoredVariableRow(name,stored,false,16f,m.outputFont*.72f){m.insert(name);close()}
            }
        }
    }},confirmButton={TextButton(onClick=close){Text(tr("Close"))}},dismissButton={if(names.isNotEmpty())TextButton(onClick={m.deleteAllVariables();close()}){Text(tr("Delete all"))}})
}

@Composable fun VariablesDialog(m: CalculatorModel,close: ()->Unit) {
    var name by rememberSaveable { mutableStateOf("A") }; var value by rememberSaveable { mutableStateOf(m.editor.source.ifBlank { "0" }) }; var parameters by rememberSaveable { mutableStateOf("x") }; var function by rememberSaveable { mutableStateOf(false) }
    fun selectVariable(key:String) {
        name=key
        function=false
        m.variables.optJSONObject(key)?.let {stored->value=treeSource(stored) ?: ""}
    }
    AlertDialog(onDismissRequest=close,title={Text(tr("Variables & functions"))},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Choices(listOf("A","B","C","D","F","x","y","z","t","n","r","M"),name,{selectVariable(it)})
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(tr("Stored values · tap to select"),fontSize=12.sp,color=LocalInstrument.current.muted)
        if(m.variables.length()==0&&m.functions.length()==0)Text(tr("No stored variables"))
        storedVariableNames(m.variables).forEach{key->
            m.variables.optJSONObject(key)?.let {stored->
                StoredVariableRow(key,stored,name==key&&!function,12f,m.outputFont*.55f){selectVariable(key)}
            }
        }
        m.functions.keys().asSequence().toList().sorted().forEach {key->
            Text("$key(…) = ${m.functions.optJSONObject(key)?.optString("source").orEmpty()}",Modifier.fillMaxWidth().clickable {
                name=key;function=true
                m.functions.optJSONObject(key)?.let {definition->
                    value=definition.optString("source")
                    parameters=definition.optJSONArray("parameters")?.let {args->(0 until args.length()).joinToString(","){args.optString(it)}} ?: "x"
                }
            }.padding(vertical=4.dp),fontSize=12.sp,color=if(name==key&&function)LocalInstrument.current.accent else LocalInstrument.current.ink)
        }
        Field(name,"Name",Modifier.fillMaxWidth()) { name=it }
        OutlinedTextField(value,{value=it},modifier=Modifier.fillMaxWidth().testTag("stored-variable-value"),label={Text(tr("Value / expression"))},singleLine=true,
            trailingIcon={TextButton(onClick={value=m.editor.source},enabled=m.editor.source.isNotBlank(),contentPadding=PaddingValues(horizontal=4.dp),modifier=Modifier.semantics{contentDescription="Paste current expression"}){Text(tr("Paste"),fontSize=11.sp)}})
        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(function,{function=it});Text(tr("User function")) }
        if(function) Field(parameters,"Parameters (comma separated)",Modifier.fillMaxWidth()) { parameters=it }
        Row { SmallAction("Store") { if(function)m.define(name,parameters,value) else m.store(name,value) }; SmallAction("Recall") { m.insert(if(function) "$name()" else name);close() }; SmallAction("Delete") { m.removeVariable(name) } }
        Text(if(isKorean())"${name}에 대한 가정" else "Assumption for $name",fontSize=12.sp)
        Choices(listOf("none","real","positive","integer","nonzero"),m.assumptions.optJSONArray(name)?.optString(0) ?: "none",{m.assume(name,it)})
        Text((if(isKorean())"저장값: " else "Stored: ")+m.variables.keys().asSequence().toList().joinToString()+(if(isKorean())"\n함수: " else "\nFunctions: ")+m.functions.keys().asSequence().toList().joinToString(),fontSize=12.sp)
        if(m.error.isNotBlank()) Text(m.error,color=LocalInstrument.current.danger)
        }
    }},confirmButton={TextButton(onClick=close) { Text(tr("Done")) }},dismissButton={if(m.variables.length()>0)TextButton(onClick={m.deleteAllVariables()}){Text(tr("Delete all"))}})
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
private fun matrixTemplate(rows:Int,columns:Int)=List(rows){"["+",".repeat(columns-1)+"]"}.joinToString(",","[","]")

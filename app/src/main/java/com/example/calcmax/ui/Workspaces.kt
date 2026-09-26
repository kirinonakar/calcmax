package com.example.calcmax.ui

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
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.math.Editor
import com.example.calcmax.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import org.json.JSONObject

@Composable fun Panel(title: String,subtitle: String,content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title,style=MaterialTheme.typography.titleLarge); if(subtitle.isNotBlank())Text(subtitle,color=LocalInstrument.current.muted,fontSize=12.sp); content()
    }
}
@Composable fun Choices(values: List<String>,selected: String,choose: (String)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { values.forEach { value->FilterChip(selected==value,onClick={choose(value)},label={Text(value,fontSize=12.sp)}) } }
}
@Composable fun Field(value: String,label: String,modifier: Modifier=Modifier,enabled: Boolean=true,onValue: (String)->Unit) { OutlinedTextField(value,onValue,modifier=modifier,label={Text(label)},singleLine=true,enabled=enabled) }
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
        "set"->children()?.let{"{"+it+"}"}
        "number","float","snapshot_symbol"->node.optString("value").takeIf{it.isNotBlank()}
        "constant"->when(node.optString("value")){"pi"->"pi";"E"->"e";"I"->"i";"oo"->"oo";"-oo"->"-oo";else->null}
        "binary"->{val a=child(0) ?:return null;val b=child(1) ?:return null;"($a)${node.optString("value")}($b)"}
        "unary"->{val a=child(0) ?:return null;"${node.optString("value","-")}($a)"}
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
        Text("Expression  "+source()+(if(other.isBlank())"" else ", "+resolved(other)),fontFamily=FontFamily.Monospace,fontSize=11.sp,color=c.muted)
        Choices(listOf("A","B","C"),name,{name=it})
        val storedTree=m.variables.optJSONObject(name)
        if(storedTree!=null) Row(verticalAlignment=Alignment.CenterVertically) {
            Text("$name = ",fontSize=15.sp,color=c.muted)
            Box(Modifier.horizontalScroll(rememberScrollState())){MathNode(storedTree,m.outputFont*.75f)}
        } else Text("Nothing stored in $name",fontSize=11.sp,color=c.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
            Button(onClick={m.store(name,source())}) {Text("Store as $name")}
            SmallAction("Insert into calculator") {m.edit(Editor(source()));m.mode="Scientific/CAS"}
            SmallAction("Clear grid") {cells=List(81){"0"}}
        }
        Text("Operations",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        OpChips(if(vector)listOf("norm","normalize") else listOf("det","inverse","transpose","rank","trace","ref","rref","lu","eigenvalues","eigenvectors")){applyOp(it)}
        if(vector)Text("cross needs 3 components; dot, angle and projection need matching lengths.",fontSize=11.sp,color=c.muted)
        else if(rows!=cols)Text("det, inverse, rank, trace, LU and eigenvalues need a square matrix.",fontSize=11.sp,color=c.muted)
        Text("Operations with the second operand",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
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
            Text("Stored values · tap to use as the second operand",fontSize=11.sp,color=c.muted)
            Row(Modifier.horizontalScroll(rememberScrollState())) {stored.forEach {key->SmallAction(key){other=key}}}
        }
        // The workspace panel scrolls: an initial focus request would pull it down to the display.
        Display(m,requestInitialFocus=false)
        Text(if(vector)"The grid holds up to 9 components and expressions support larger vectors. The second operand may be a stored variable or a literal." else "The grid holds up to 9 × 9 and expressions support matrices up to 32 × 32. LU returns L, U and row permutations.",fontSize=11.sp,color=c.muted)
    }
}

@Composable fun StatisticsScreen(m: CalculatorModel) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val names=remember(m.dataSets) {m.dataSets.keys().asSequence().toList().sorted()}
    var selected by rememberSaveable {mutableStateOf(m.statisticsSelected)}
    var isNew by rememberSaveable {mutableStateOf(m.statisticsIsNew)}
    val activeName=if(isNew)"" else selected.ifBlank {names.firstOrNull().orEmpty()}
    var datasetName by rememberSaveable {mutableStateOf(m.statisticsName)}
    var data by rememberSaveable {mutableStateOf(m.statisticsData)}
    var dataKind by rememberSaveable {mutableStateOf(m.statisticsKind)}
    var regression by rememberSaveable {mutableStateOf(m.statisticsRegression)}
    var plotType by rememberSaveable {mutableStateOf(m.statisticsPlot)}
    var csv by rememberSaveable {mutableStateOf(m.statisticsCsv)}
    LaunchedEffect(data,datasetName,dataKind,regression,plotType,csv,selected,isNew) {m.saveStatistics(datasetName,data,dataKind,regression,plotType,csv,selected,isNew)}
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
        datasetName="D$index";data=if(dataKind=="xy")"0,0\n1,1" else "1\n2\n3";isNew=true;selected=""
    }
    val parsedRows=rows()
    val xValues=parsedRows.mapNotNull {it.getOrNull(0)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}}
    val yValues=if(dataKind=="xy")parsedRows.mapNotNull {it.getOrNull(1)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}} else emptyList()
    val paired=parsedRows.mapNotNull {row->val x=row.getOrNull(0)?.toDoubleOrNull();val y=row.getOrNull(1)?.toDoubleOrNull();if(x!=null&&y!=null&&x.isFinite()&&y.isFinite())x to y else null}
    Panel("Data & statistics","") {
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
            SmallAction(if(csv)"Table editor" else "Paste CSV"){csv=!csv}
            SmallAction("Add row"){if(parsedRows.size<999)data+=if(data.isBlank())if(dataKind=="xy")"0,0" else "0" else if(dataKind=="xy")"\n0,0" else "\n0"}
        }
        if(csv)OutlinedTextField(data,{data=it},Modifier.fillMaxWidth().height(180.dp),label={Text(if(dataKind=="xy")"x, y values" else "One value per line")},textStyle=MaterialTheme.typography.bodyLarge.copy(fontFamily=FontFamily.Monospace))
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
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Button(onClick={val values=vector(0);if(values!="[]"){m.edit(Editor("stats($values)"));m.calculate()}}){Text(if(dataKind=="xy")"Summarize x" else "Summarize list")}
            if(dataKind=="xy")SmallAction("Summarize y"){val values=vector(1);if(values!="[]"){m.edit(Editor("stats($values)"));m.calculate()}}
            if(dataKind=="xy")SmallAction("Fit regression"){val table=parsedRows.filter {it.size>=2&&it[0].isNotBlank()&&it[1].isNotBlank()}.joinToString(",","[","]"){it.take(2).joinToString(",","[","]")};m.fitRegression("regression($table,$regression)",data)}
        }
        if(dataKind=="xy")Choices(listOf("linear","quadratic","logarithmic","exponential","power"),regression,{regression=it})
        Choices(if(dataKind=="xy")listOf("Scatter","Histogram","Box plot") else listOf("Histogram","Box plot"),plotType,{plotType=it})
        val fitVisible=dataKind=="xy"&&plotType=="Scatter"&&m.regressionData==data
        StatisticsPlot(plotType,if(plotType=="Scatter")paired else xValues.mapIndexed {i,v->i.toDouble() to v},xValues,yValues,if(fitVisible)m.regressionCurve.orEmpty() else emptyList(),if(fitVisible)m.regressionFit else "")
        if(m.regressionBusy)Text("Fitting regression…",fontSize=11.sp,color=LocalInstrument.current.muted)
        // The workspace panel scrolls: an initial focus request would pull it down to the display.
        Display(m,requestInitialFocus=false)
        if(dataKind=="xy")SmallAction("Graph fitted expression"){val exact=m.result?.optString("exact");if(!exact.isNullOrBlank()){m.changeGraphKind("cartesian");m.updateGraphSource(exact.replace("**","^"));m.mode="Graph";m.plot()}}
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

@Composable private fun StatisticsPlot(type:String,points:List<Pair<Double,Double>>,values:List<Double>,secondary:List<Double> = emptyList(),curve:List<Pair<Double,Double>> = emptyList(),fitLabel:String="") {
    val c=LocalInstrument.current
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
            if(fitLabel.isNotBlank()) {
                val fitText=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=c.danger.toArgb();textSize=10.sp.toPx()}
                drawContext.canvas.nativeCanvas.drawText("y ≈ "+fitLabel.take(54),left,top+11.dp.toPx(),fitText)
            }
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
}

@Composable fun ProgrammerScreen(m: CalculatorModel) {
    var a by rememberSaveable { mutableStateOf("255") }; var b by rememberSaveable { mutableStateOf("15") }
    var base by rememberSaveable { mutableIntStateOf(10) }; var width by rememberSaveable { mutableIntStateOf(32) }; var signed by rememberSaveable { mutableStateOf(false) }
    Panel("Programmer","Fixed-width integers · two’s complement · exact bit operations") {
        Choices(listOf("2","8","10","16"),base.toString(),{base=it.toInt()})
        Choices(listOf("8","16","32","64"),width.toString(),{width=it.toInt()})
        Row(verticalAlignment=Alignment.CenterVertically) { Switch(signed,{signed=it}); Text("  Signed interpretation") }
        Field(a,"A · base $base",Modifier.fillMaxWidth()) { a=it }
        Field(b,"B / shift count · base $base",Modifier.fillMaxWidth()) { b=it }
        listOf("","AND","OR","XOR","NOT","NAND","NOR","<<",">>").chunked(3).forEach { row->Row { row.forEach { op->SmallAction(op.ifBlank { "Convert" }) { m.program(a,b,base,width,signed,op) } } } }
        m.result?.optJSONObject("bases")?.let { bases -> listOf("BIN","OCT","DEC","HEX").forEach { key->Text(key,color=LocalInstrument.current.muted,fontSize=11.sp); Text(bases.optString(key),fontFamily=FontFamily.Monospace,fontSize=18.sp) } }
        if(m.busy) Text("Computing…")
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
        Choices(UnitGroups.keys.toList(),group,{group=it;from=UnitGroups[it]!![0];to=UnitGroups[it]!![1]})
        Field(value,"Value or expression",Modifier.fillMaxWidth()) { value=it }
        Text("From"); Choices(UnitGroups[group]!!,from,{from=it})
        Text("To"); Choices(UnitGroups[group]!!,to,{to=it})
        Row { Button(onClick={m.edit(Editor("convert($value,$from,$to)"));m.calculate()}) { Text("Convert") }; SmallAction("Swap") { val temp=from;from=to;to=temp } }
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
        Text("Scientific constants",style=MaterialTheme.typography.titleLarge)
        Text("SI defining constants and CODATA 2022 reference values. Tap to insert.",color=LocalInstrument.current.muted,fontSize=12.sp)
        OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth(),label={Text("Search")},singleLine=true,
            trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""}){Text("\u2715",fontSize=15.sp)}})
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(m.constants==null) Text("Loading local constants…")
            constants.filter { it.name.contains(search,true)||it.symbol.contains(search,true) }.forEach { item ->
                Column(Modifier.fillMaxWidth().clickable { m.insert(item.symbol);m.mode="Scientific/CAS" }.padding(vertical=8.dp)) {
                    Text("${item.symbol}  ·  ${item.name}",style=MaterialTheme.typography.titleSmall)
                    Text("${item.value} ${item.unit}",fontSize=14.sp)
                    Text(if(item.exact) "Exact definition" else "Measured · CODATA 2022",fontSize=11.sp,color=LocalInstrument.current.muted)
                }; HorizontalDivider()
            }
            Text("Source: NIST physics.nist.gov/cuu/Constants · Measured values retain their published precision; they are not exact physical quantities.",fontSize=11.sp)
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
    AlertDialog(onDismissRequest=close,title={Text("Instrument setup")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Appearance"); Choices(listOf("System","Light","Dark"),m.theme,{m.theme=it;m.save()})
        Text("Angle unit"); Choices(listOf("DEG","RAD","GRAD"),m.angle,{m.angle=it;m.recalculatePreview();m.save()})
        Text("Internal precision · numeric algorithms"); Choices(precisionChoices+"Custom",if(customPrecisionVisible)"Custom" else m.precision.toString(),{if(it=="Custom")customPrecisionVisible=true else {customPrecisionVisible=false;m.precision=it.toInt();m.recalculatePreview();m.save()}})
        if(customPrecisionVisible) {Field(customPrecision,"Custom internal precision · 3–200",Modifier.fillMaxWidth()){customPrecision=it};TextButton(onClick={m.precision=customPrecision.toInt();m.recalculatePreview();m.save()},enabled=customPrecision.toIntOrNull() in 3..200){Text("Apply internal precision")}}
        Text("Display digits · result digits shown"); Choices(displayChoices+"Custom",if(customDisplayVisible)"Custom" else m.displayDigits.toString(),{if(it=="Custom")customDisplayVisible=true else {customDisplayVisible=false;m.displayDigits=it.toInt();m.recalculatePreview();m.save()}})
        if(customDisplayVisible) {Field(customDisplay,"Custom display digits · 2–200",Modifier.fillMaxWidth()){customDisplay=it};TextButton(onClick={m.displayDigits=customDisplay.toInt();m.recalculatePreview();m.save()},enabled=customDisplay.toIntOrNull() in 2..200){Text("Apply display digits")}}
        Text("Internal precision governs numeric algorithms such as integration and solving. Display digits limit the digits a result shows; they never exceed internal precision, and exact integers, fractions and symbolic forms are not rounded.",fontSize=11.sp,color=LocalInstrument.current.muted)
        Text("Input font · ${m.inputFont.toInt()} sp")
        Slider(m.inputFont,{m.inputFont=it},valueRange=10f..42f,steps=31,onValueChangeFinished={m.save()})
        Text("Output font · ${m.outputFont.toInt()} sp")
        Slider(m.outputFont,{m.outputFont=it},valueRange=10f..48f,steps=37,onValueChangeFinished={m.save()})
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Key vibration",Modifier.weight(1f)); Switch(m.haptics,{m.haptics=it;m.save()}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Key sound",Modifier.weight(1f)); Switch(m.sound,{m.sound=it;m.save()}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Save history locally",Modifier.weight(1f)); Switch(m.persistHistory,{m.persistHistory=it;m.save()}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Bracket auto-close",Modifier.weight(1f)); Switch(m.autoCloseBrackets,{m.autoCloseBrackets=it;m.save()}) }
        Text("Bracket auto-close pairs ( { [ with the matching ) } ] and keeps the cursor between them.",fontSize=11.sp,color=LocalInstrument.current.muted)
    }},confirmButton={TextButton(onClick=close) { Text("Done") }})
}

@Composable fun HistoryDialog(m: CalculatorModel,close: ()->Unit) {
    val clipboard=LocalClipboardManager.current
    var favorites by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val needle=search.trim()
    val entries=m.history.filter { entry->(!favorites||entry.favorite)&&(needle.isBlank()||entry.source.contains(needle,true)||entry.exact.contains(needle,true)) }
    AlertDialog(onDismissRequest=close,title={Text("Calculation history")},text={Column(Modifier.fillMaxWidth().heightIn(max=480.dp)) {
        OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth(),label={Text("Search history")},singleLine=true,
            trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""}){Text("\u2715",fontSize=15.sp)}})
        Choices(listOf("All","Favorites"),if(favorites)"Favorites" else "All",{favorites=it=="Favorites"})
        LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false)) {
            if(entries.isEmpty())item {Text(if(needle.isNotBlank())"No calculations match \"$needle\"." else if(favorites)"No favorites yet." else "Your calculations will appear here.")}
            items(entries) { entry ->
                Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                    Text(entry.source.historyPreview(),fontFamily=FontFamily.Monospace,fontSize=13.sp)
                    Text("= ${entry.exact.historyPreview()}",fontFamily=FontFamily.Serif,fontSize=20.sp)
                    Text("${entry.mode} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.id))}",fontSize=10.sp,color=LocalInstrument.current.muted)
                    Row { SmallAction("Reuse") { m.edit(Editor(entry.source));m.mode="Scientific/CAS";close() }; SmallAction(if(entry.favorite)"★" else "☆") { m.favorite(entry.id) }; SmallAction("Copy") { clipboard.setText(AnnotatedString(entry.exact)) }; SmallAction("Delete") { m.deleteHistory(entry.id) } }
                }
                HorizontalDivider()
            }
        }
    }},confirmButton={TextButton(onClick=close) { Text("Done") }},dismissButton={TextButton(onClick={m.clearHistory()}) { Text("Clear all") }})
}

private fun String.historyPreview(): String =
    if(codePointCount(0,length)>50) substring(0,offsetByCodePoints(0,49))+"…" else this

@Composable fun VariablesDialog(m: CalculatorModel,operation: String,close: ()->Unit) {
    var name by rememberSaveable { mutableStateOf("A") }; var value by rememberSaveable { mutableStateOf(m.editor.source.ifBlank { "0" }) }; var parameters by rememberSaveable { mutableStateOf("x") }; var function by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest=close,title={Text("Variables & functions")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Choices(listOf("A","B","C","D","E","F","X","Y","M"),name,{name=it})
        if(operation=="RCL") {
            Text("Stored values · tap to recall")
            if(m.variables.length()==0)Text("No stored variables")
            m.variables.keys().asSequence().toList().sorted().forEach{key->
                Row(Modifier.fillMaxWidth().clickable{m.insert(key);close()}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                    Text("$key = ",fontSize=16.sp)
                    Box(Modifier.horizontalScroll(rememberScrollState())){MathNode(m.variables.getJSONObject(key),m.outputFont*.75f)}
                }
            }
        }
        Field(name,"Name",Modifier.fillMaxWidth()) { name=it }
        Field(value,"Value / expression",Modifier.fillMaxWidth()) { value=it }
        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(function,{function=it});Text("User function") }
        if(function) Field(parameters,"Parameters (comma separated)",Modifier.fillMaxWidth()) { parameters=it }
        Row { SmallAction("Store") { if(function)m.define(name,parameters,value) else m.store(name,value) }; SmallAction("Recall") { m.insert(if(function) "$name()" else name);close() }; SmallAction("Delete") { m.removeVariable(name) } }
        Row { SmallAction("M+") { m.memory(1,value) }; SmallAction("M−") { m.memory(-1,value) } }
        Text("Assumption for $name",fontSize=12.sp)
        Choices(listOf("none","real","positive","integer","nonzero"),m.assumptions.optJSONArray(name)?.optString(0) ?: "none",{m.assume(name,it)})
        Text("Stored: "+m.variables.keys().asSequence().toList().joinToString()+"\nFunctions: "+m.functions.keys().asSequence().toList().joinToString(),fontSize=12.sp)
        if(m.error.isNotBlank()) Text(m.error,color=LocalInstrument.current.danger)
    }},confirmButton={TextButton(onClick=close) { Text("Done") }})
}

@Composable fun MatrixSizeDialog(m:CalculatorModel,close:()->Unit) {
    val c=LocalInstrument.current
    var rows by rememberSaveable {mutableIntStateOf(2)}
    var columns by rememberSaveable {mutableIntStateOf(2)}
    AlertDialog(onDismissRequest=close,title={Text("Matrix size")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val density=LocalDensity.current
        val measurer=rememberTextMeasurer()
        val labelStyle=LocalTextStyle.current.copy(fontSize=11.sp)
        val labelWidth=remember(measurer,density,labelStyle){with(density){listOf("Rows","Columns").maxOf{measurer.measure(AnnotatedString(it),labelStyle).size.width}.toDp()}}
        DimStepper("Rows",rows,1..9,labelWidth){rows=it}
        DimStepper("Columns",columns,1..9,labelWidth){columns=it}
        Text("$rows × $columns matrix",fontSize=11.sp,color=c.muted)
    }},confirmButton={TextButton(onClick={m.insert(matrixTemplate(rows,columns),2);close()}){Text("Insert")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
}
private fun matrixTemplate(rows:Int,columns:Int)=List(rows){"["+",".repeat(columns-1)+"]"}.joinToString(",","[","]")
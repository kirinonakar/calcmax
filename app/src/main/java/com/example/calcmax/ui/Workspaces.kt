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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

@Composable fun Panel(title: String,subtitle: String,content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title,style=MaterialTheme.typography.titleLarge); Text(subtitle,color=LocalInstrument.current.muted,fontSize=12.sp); content()
    }
}
@Composable fun Choices(values: List<String>,selected: String,choose: (String)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { values.forEach { value->FilterChip(selected==value,onClick={choose(value)},label={Text(value,fontSize=12.sp)}) } }
}
@Composable fun Field(value: String,label: String,modifier: Modifier=Modifier,onValue: (String)->Unit) { OutlinedTextField(value,onValue,modifier=modifier,label={Text(label)},singleLine=true) }
@Composable private fun StatHeader(text:String,modifier:Modifier) { val c=LocalInstrument.current; Box(modifier.fillMaxHeight(),contentAlignment=Alignment.Center){Text(text,fontSize=11.sp,color=c.muted,fontWeight=FontWeight.SemiBold)} }
@Composable private fun StatCell(value:String,modifier:Modifier,focus:FocusRequester,tag:String,onValue:(String)->Unit) {
    val c=LocalInstrument.current
    val keyboard=LocalSoftwareKeyboardController.current
    Box(modifier.fillMaxHeight().background(c.display).pointerInput(focus,keyboard) {
            val tolerance=viewConfiguration.touchSlop
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                var moved=false
                while(true) {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    val change=event.changes.firstOrNull {it.id==down.id}
                    if(change==null) {if(event.changes.none {it.pressed})break;continue}
                    if((change.position-down.position).getDistance()>tolerance)moved=true
                    if(!change.pressed) {
                        if(!moved){focus.requestFocus();keyboard?.show()}
                        break
                    }
                }
            }
        }) {
        BasicTextField(value,onValue,Modifier.fillMaxSize().focusRequester(focus).testTag(tag),
            textStyle=MaterialTheme.typography.bodyMedium.copy(fontSize=12.sp,color=c.ink),singleLine=true,cursorBrush=SolidColor(c.accent),
            decorationBox={innerTextField->Box(Modifier.fillMaxSize().padding(horizontal=8.dp),contentAlignment=Alignment.CenterStart){innerTextField()}})
    }
}

@Composable fun MatrixScreen(m: CalculatorModel) {
    var rows by rememberSaveable { mutableIntStateOf(2) }; var cols by rememberSaveable { mutableIntStateOf(if(m.mode=="Vector")1 else 2) }
    var cells by rememberSaveable { mutableStateOf(List(16) { if(it==0 || it==5) "1" else "0" }) }
    var name by rememberSaveable { mutableStateOf("A") }
    var other by rememberSaveable { mutableStateOf("B") }
    fun source()=(0 until rows).joinToString(",","[","]") { r->(0 until cols).joinToString(",","[","]") { c->cells[r*4+c].ifBlank { "0" } } }
    Panel(if(m.mode=="Vector") "Vector workspace" else "Matrix workspace","Edit exact values, then calculate or store for reuse.") {
        Choices(listOf("A","B","C"),name,{name=it})
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Rows: $rows",Modifier.weight(1f)); SmallAction("−") { rows=(rows-1).coerceAtLeast(1) }; SmallAction("+") { rows=(rows+1).coerceAtMost(4) }; Text("Cols: $cols"); SmallAction("−") { cols=(cols-1).coerceAtLeast(1) }; SmallAction("+") { cols=(cols+1).coerceAtMost(4) } }
        repeat(rows) { r -> Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) { repeat(cols) { c -> Field(cells[r*4+c],"${r+1},${c+1}",Modifier.weight(1f)) { text->cells=cells.toMutableList().also { it[r*4+c]=text } } } } }
        Row { Button(onClick={m.store(name,source())}) { Text("Store $name") }; SmallAction("Insert into calculator") { m.edit(Editor(source()));m.mode="Scientific" } }
        Field(other,"Second matrix / vector") { other=it }
        val ops=if(m.mode=="Vector") listOf("norm","normalize","dot","cross","angle","projection") else listOf("det","inverse","transpose","rank","trace","ref","rref","lu","eigenvalues","eigenvectors","linsolve")
        ops.chunked(3).forEach { row -> Row { row.forEach { op -> SmallAction(op) { val expression="$op(${source()}${if(op in listOf("dot","cross","angle","projection","linsolve")) ",$other" else ""})"; m.edit(Editor(expression));m.calculate() } } } }
        Display(m)
        Text("The grid supports up to 4 × 4. Expressions support matrices up to 32 × 32. LU returns L, U and row permutations.",fontSize=11.sp,color=LocalInstrument.current.muted)
    }
}

@Composable fun StatisticsScreen(m: CalculatorModel) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val names=remember(m.dataSets) {m.dataSets.keys().asSequence().toList().sorted()}
    var selected by rememberSaveable {mutableStateOf("")}
    var isNew by rememberSaveable {mutableStateOf(false)}
    val activeName=if(isNew)"" else selected.ifBlank {names.firstOrNull().orEmpty()}
    var datasetName by rememberSaveable {mutableStateOf("D1")}
    var data by rememberSaveable {mutableStateOf("1\n2\n3\n4")}
    var dataKind by rememberSaveable {mutableStateOf("list")}
    var regression by rememberSaveable {mutableStateOf("linear")}
    var plotType by rememberSaveable {mutableStateOf("Histogram")}
    var csv by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(activeName) {
        if(activeName.isNotBlank())m.dataSets.optJSONObject(activeName)?.let {item->
            datasetName=activeName;data=item.optString("csv");dataKind=item.optString("kind","list")
            plotType=if(dataKind=="xy")"Scatter" else "Histogram"
        }
    }
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
    val paired=parsedRows.mapNotNull {row->val x=row.getOrNull(0)?.toDoubleOrNull();val y=row.getOrNull(1)?.toDoubleOrNull();if(x!=null&&y!=null&&x.isFinite()&&y.isFinite())x to y else null}
    Panel("Data & statistics","Save named lists or paired x,y datasets, import/export CSV, calculate summaries and view statistical plots.") {
        if(names.isNotEmpty())Choices(names,activeName,{selected=it;isNew=false})
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
                    Row(Modifier.fillMaxWidth().height(56.dp)) {
                        Box(Modifier.width(30.dp).fillMaxHeight().clickable {cellFocus.first().requestFocus()},contentAlignment=Alignment.Center){Text("${index+1}",fontSize=12.sp,color=LocalInstrument.current.muted)}
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
            if(dataKind=="xy")SmallAction("Fit regression"){val table=parsedRows.filter {it.size>=2&&it[0].isNotBlank()&&it[1].isNotBlank()}.joinToString(",","[","]"){it.take(2).joinToString(",","[","]")};m.edit(Editor("regression($table,$regression)"));m.calculate()}
        }
        if(dataKind=="xy")Choices(listOf("linear","quadratic","logarithmic","exponential","power"),regression,{regression=it})
        Choices(if(dataKind=="xy")listOf("Scatter","Histogram","Box plot") else listOf("Histogram","Box plot"),plotType,{plotType=it})
        StatisticsPlot(plotType,if(plotType=="Scatter")paired else xValues.mapIndexed {i,v->i.toDouble() to v},xValues)
        Display(m)
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

@Composable private fun StatisticsPlot(type:String,points:List<Pair<Double,Double>>,values:List<Double>) {
    val c=LocalInstrument.current
    Canvas(Modifier.fillMaxWidth().height(220.dp).background(c.display)) {
        val left=38.dp.toPx();val right=12.dp.toPx();val top=14.dp.toPx();val bottom=28.dp.toPx()
        val width=size.width-left-right;val height=size.height-top-bottom
        val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=c.muted.toArgb();textSize=10.sp.toPx()}
        drawLine(c.grid,Offset(left,top+height),Offset(left+width,top+height),1.dp.toPx())
        drawLine(c.grid,Offset(left,top),Offset(left,top+height),1.dp.toPx())
        if(type=="Scatter") {
            if(points.isEmpty())return@Canvas
            var x0=points.minOf {it.first};var x1=points.maxOf {it.first};var y0=points.minOf {it.second};var y1=points.maxOf {it.second}
            if(x0==x1){x0-=1;x1+=1};if(y0==y1){y0-=1;y1+=1}
            fun px(x:Double)=left+((x-x0)/(x1-x0)).toFloat()*width
            fun py(y:Double)=top+height-((y-y0)/(y1-y0)).toFloat()*height
            points.forEach {drawCircle(c.accent,4.dp.toPx(),Offset(px(it.first),py(it.second)))}
            drawContext.canvas.nativeCanvas.drawText("x",left+width-4,top+height+20.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText("y",5.dp.toPx(),top+12.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText("%.4g".format(x0),left,top+height+16.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText("%.4g".format(x1),left+width-34.dp.toPx(),top+height+16.dp.toPx(),text)
        } else if(values.isEmpty()) {
            drawContext.canvas.nativeCanvas.drawText("Add finite numeric observations to plot",left,top+20.dp.toPx(),text)
        } else if(type=="Histogram") {
            var lo=values.min();var hi=values.max();if(lo==hi){lo-=.5;hi+=.5}
            val bins=ceil(1+ln(values.size.coerceAtLeast(2).toDouble())/ln(2.0)).toInt().coerceIn(3,14)
            val counts=IntArray(bins);values.forEach {v->counts[((v-lo)/(hi-lo)*bins).toInt().coerceIn(0,bins-1)]++}
            val peak=counts.maxOrNull()?.coerceAtLeast(1) ?: 1;val bar=width/bins
            counts.forEachIndexed {i,count->val h=height*count/peak;drawRect(c.accent.copy(alpha=.78f),Offset(left+i*bar+1,top+height-h),androidx.compose.ui.geometry.Size((bar-2).coerceAtLeast(1f),h))}
            drawContext.canvas.nativeCanvas.drawText("${values.size} values · $bins bins",left,top+11.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText("%.4g".format(lo),left,top+height+16.dp.toPx(),text)
            drawContext.canvas.nativeCanvas.drawText("%.4g".format(hi),left+width-34.dp.toPx(),top+height+16.dp.toPx(),text)
        } else {
            val sorted=values.sorted()
            fun quantile(p:Double):Double {val position=(sorted.size-1)*p;val low=floor(position).toInt();val high=ceil(position).toInt();return sorted[low]+(sorted[high]-sorted[low])*(position-low)}
            val lo=sorted.first();val q1=quantile(.25);val median=quantile(.5);val q3=quantile(.75);val hi=sorted.last()
            var minValue=lo;var maxValue=hi;if(minValue==maxValue){minValue-=.5;maxValue+=.5}
            fun px(value:Double)=left+((value-minValue)/(maxValue-minValue)).toFloat()*width
            val y=top+height/2
            drawLine(c.muted,Offset(px(lo),y),Offset(px(hi),y),2.dp.toPx())
            drawLine(c.muted,Offset(px(lo),y-9.dp.toPx()),Offset(px(lo),y+9.dp.toPx()),2.dp.toPx())
            drawLine(c.muted,Offset(px(hi),y-9.dp.toPx()),Offset(px(hi),y+9.dp.toPx()),2.dp.toPx())
            drawRect(c.accent.copy(alpha=.24f),Offset(px(q1),y-20.dp.toPx()),androidx.compose.ui.geometry.Size((px(q3)-px(q1)).coerceAtLeast(1f),40.dp.toPx()))
            drawRect(c.accent,Offset(px(q1),y-20.dp.toPx()),androidx.compose.ui.geometry.Size((px(q3)-px(q1)).coerceAtLeast(1f),40.dp.toPx()),style=Stroke(1.5.dp.toPx()))
            drawLine(c.danger,Offset(px(median),y-20.dp.toPx()),Offset(px(median),y+20.dp.toPx()),2.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("min %.4g   Q1 %.4g   median %.4g   Q3 %.4g   max %.4g".format(lo,q1,median,q3,hi),left,top+13.dp.toPx(),text)
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
    Panel("Unit conversion","Dimension-checked conversions with exact factors and temperature offsets.") {
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
    Panel("Scientific constants","SI defining constants and CODATA 2022 reference values. Tap to insert.") {
        Field(search,"Search",Modifier.fillMaxWidth()) { search=it }
        if(m.constants==null) Text("Loading local constants…")
        constants.filter { it.name.contains(search,true)||it.symbol.contains(search,true) }.forEach { item ->
            Column(Modifier.fillMaxWidth().clickable { m.insert(item.symbol);m.mode="Scientific" }.padding(vertical=8.dp)) {
                Text("${item.symbol}  ·  ${item.name}",style=MaterialTheme.typography.titleSmall)
                Text("${item.value} ${item.unit}",fontSize=14.sp)
                Text(if(item.exact) "Exact definition" else "Measured · CODATA 2022",fontSize=11.sp,color=LocalInstrument.current.muted)
            }; HorizontalDivider()
        }
        Text("Source: NIST physics.nist.gov/cuu/Constants · Measured values retain their published precision; they are not exact physical quantities.",fontSize=11.sp)
    }
}

@Composable fun SettingsDialog(m: CalculatorModel,close: ()->Unit) {
    val digits=listOf("3","10","15","30","50","100","200")
    var custom by rememberSaveable{mutableStateOf(m.precision.toString())}
    var customVisible by rememberSaveable{mutableStateOf(m.precision.toString() !in digits)}
    AlertDialog(onDismissRequest=close,title={Text("Instrument setup")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Appearance"); Choices(listOf("System","Light","Dark"),m.theme,{m.theme=it;m.save()})
        Text("Angle unit"); Choices(listOf("DEG","RAD","GRAD"),m.angle,{m.angle=it;m.recalculatePreview();m.save()})
        Text("Maximum significant digits"); Choices(digits+"Custom",if(customVisible)"Custom" else m.precision.toString(),{if(it=="Custom")customVisible=true else {customVisible=false;m.precision=it.toInt();m.recalculatePreview();m.save()}})
        if(customVisible) {Field(custom,"Custom precision · 3–200",Modifier.fillMaxWidth()){custom=it};TextButton(onClick={m.precision=custom.toInt();m.recalculatePreview();m.save()},enabled=custom.toIntOrNull() in 3..200){Text("Apply precision")}}
        Text("Input font · ${m.inputFont.toInt()} sp")
        Slider(m.inputFont,{m.inputFont=it},valueRange=16f..42f,steps=25,onValueChangeFinished={m.save()})
        Text("Output font · ${m.outputFont.toInt()} sp")
        Slider(m.outputFont,{m.outputFont=it},valueRange=16f..48f,steps=31,onValueChangeFinished={m.save()})
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Key vibration",Modifier.weight(1f)); Switch(m.haptics,{m.haptics=it;m.save()}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Key sound",Modifier.weight(1f)); Switch(m.sound,{m.sound=it;m.save()}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Save history locally",Modifier.weight(1f)); Switch(m.persistHistory,{m.persistHistory=it;m.save()}) }
    }},confirmButton={TextButton(onClick=close) { Text("Done") }})
}

@Composable fun HistoryDialog(m: CalculatorModel,close: ()->Unit) {
    val clipboard=LocalClipboardManager.current
    var favorites by remember { mutableStateOf(false) }
    val entries=m.history.filter { !favorites||it.favorite }
    AlertDialog(onDismissRequest=close,title={Text("Calculation history")},text={Column(Modifier.fillMaxWidth().heightIn(max=480.dp)) {
        Choices(listOf("All","Favorites"),if(favorites)"Favorites" else "All",{favorites=it=="Favorites"})
        LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false)) {
            if(entries.isEmpty())item {Text(if(favorites)"No favorites yet." else "Your calculations will appear here.")}
            items(entries) { entry ->
                Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                    Text(entry.source.historyPreview(),fontFamily=FontFamily.Monospace,fontSize=13.sp)
                    Text("= ${entry.exact.historyPreview()}",fontFamily=FontFamily.Serif,fontSize=20.sp)
                    Text("${entry.mode} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.id))}",fontSize=10.sp,color=LocalInstrument.current.muted)
                    Row { SmallAction("Reuse") { m.edit(Editor(entry.source));m.mode="Scientific";close() }; SmallAction(if(entry.favorite)"★" else "☆") { m.favorite(entry.id) }; SmallAction("Copy") { clipboard.setText(AnnotatedString(entry.exact)) }; SmallAction("Delete") { m.deleteHistory(entry.id) } }
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

package com.example.calcmax.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.math.Editor
import com.example.calcmax.ui.theme.LocalInstrument
import java.text.DateFormat
import java.util.Date

@Composable fun Panel(title: String,subtitle: String,content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title,style=MaterialTheme.typography.titleLarge); Text(subtitle,color=LocalInstrument.current.muted,fontSize=12.sp); content()
    }
}
@Composable fun Choices(values: List<String>,selected: String,choose: (String)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { values.forEach { value->FilterChip(selected==value,onClick={choose(value)},label={Text(value,fontSize=12.sp)}) } }
}
@Composable fun Field(value: String,label: String,modifier: Modifier=Modifier,onValue: (String)->Unit) { OutlinedTextField(value,onValue,modifier=modifier,label={Text(label)},singleLine=true) }

@Composable fun MatrixScreen(m: CalculatorModel) {
    var rows by rememberSaveable { mutableIntStateOf(2) }; var cols by rememberSaveable { mutableIntStateOf(if(m.mode=="Vector")1 else 2) }
    var cells by rememberSaveable { mutableStateOf(List(16) { if(it==0 || it==5) "1" else "0" }) }
    var name by rememberSaveable { mutableStateOf("A") }
    var other by rememberSaveable { mutableStateOf("B") }
    fun source()=(0 until rows).joinToString(",","[","]") { r->(0 until cols).joinToString(",","[","]") { c->cells[r*4+c].ifBlank { "0" } } }
    Panel(if(m.mode=="Vector") "Vector workspace" else "Matrix workspace","Edit exact values, then calculate or store for reuse.") {
        Choices(listOf("A","B","C"),name,{name=it})
        Row { Text("Rows: $rows",Modifier.weight(1f)); SmallAction("−") { rows=(rows-1).coerceAtLeast(1) }; SmallAction("+") { rows=(rows+1).coerceAtMost(4) }; Text("Cols: $cols"); SmallAction("−") { cols=(cols-1).coerceAtLeast(1) }; SmallAction("+") { cols=(cols+1).coerceAtMost(4) } }
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
    var data by rememberSaveable { mutableStateOf("1,2\n2,4\n3,5\n4,8") }
    var regression by rememberSaveable { mutableStateOf("linear") }
    var csv by rememberSaveable { mutableStateOf(false) }
    fun rows()=data.lines().filter { it.isNotBlank() }.map { it.split(',').map(String::trim) }
    fun vector(column: Int)=rows().joinToString(",","[","]") { it.getOrElse(column) { "0" } }
    Panel("Data & statistics","Enter one observation per row. Use x,y columns for paired data.") {
        Row { SmallAction(if(csv)"Table editor" else "Paste CSV") {csv=!csv};SmallAction("Add row") { if(rows().size<100)data+="\n0,0" } }
        if(csv) OutlinedTextField(data,{data=it},Modifier.fillMaxWidth().height(180.dp),label={Text("x, y table")},textStyle=MaterialTheme.typography.bodyLarge.copy(fontFamily=FontFamily.Monospace))
        else Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState())) {
            rows().forEachIndexed { index,row->Row(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${index+1}",fontSize=11.sp,modifier=Modifier.width(20.dp))
                repeat(2) { column->Field(row.getOrElse(column){""},if(column==0)"x" else "y",Modifier.weight(1f)) { text->
                    val next=rows().map { it.toMutableList().apply {while(size<2)add("")} }.toMutableList();next[index][column]=text;data=next.joinToString("\n") {it.joinToString(",")}
                } }
                SmallAction("−") {data=rows().filterIndexed {i,_->i!=index}.joinToString("\n") {it.joinToString(",")}}
            } }
        }
        Row { Button(onClick={val expression="stats(${vector(0)})";m.edit(Editor(expression));m.calculate()}) { Text("Summarize x") }; SmallAction("Summarize y") { val expression="stats(${vector(1)})";m.edit(Editor(expression));m.calculate() } }
        Choices(listOf("linear","quadratic","logarithmic","exponential","power"),regression,{regression=it})
        Button(onClick={val table=rows().joinToString(",","[","]") { it.joinToString(",","[","]") };m.edit(Editor("regression($table,$regression)"));m.calculate()}) { Text("Fit regression") }
        Display(m)
        SmallAction("Graph fitted expression") { val exact=m.result?.optString("exact"); if(!exact.isNullOrBlank()) { m.graphSource=exact.replace("**","^");m.mode="Graph";m.plot() } }
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

val UnitGroups=linkedMapOf("Length" to listOf("m","km","cm","mm","in","ft","yd","mi"),"Area" to listOf("m2","cm2","km2","ha","acre"),"Volume" to listOf("L","mL","m3","galUS"),"Mass" to listOf("kg","g","mg","lb","oz"),"Temperature" to listOf("degC","degF","K"),"Speed" to listOf("mps","kph","mph","knot"),"Acceleration" to listOf("mps2","g0"),"Pressure" to listOf("Pa","kPa","bar","atm"),"Force" to listOf("N","kN","lbf"),"Energy" to listOf("J","kJ","cal","kWh","eV"),"Power" to listOf("W","kW"),"Time" to listOf("s","min","h","day","ms"),"Frequency" to listOf("Hz","kHz","MHz"),"Angle" to listOf("rad","deg","grad"),"Data" to listOf("bit","byte","kB","KiB","MB","MiB","GB"))
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
    AlertDialog(onDismissRequest=close,title={Text("Instrument setup")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Appearance"); Choices(listOf("System","Light","Dark"),m.theme,{m.theme=it;m.save()})
        Text("Angle unit"); Choices(listOf("DEG","RAD","GRAD"),m.angle,{m.angle=it;m.save()})
        Text("Decimal precision"); Choices(listOf("15","30","50","100","200"),m.precision.toString(),{m.precision=it.toInt();m.save()})
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Key vibration",Modifier.weight(1f)); Switch(m.haptics,{m.haptics=it;m.save()}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Key sound",Modifier.weight(1f)); Switch(m.sound,{m.sound=it;m.save()}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Save history locally",Modifier.weight(1f)); Switch(m.persistHistory,{m.persistHistory=it;m.save()}) }
        Text("Turning history off removes its saved copy. The current session remains visible until you clear it.",fontSize=11.sp)
        Text("Symbolic calculus uses radians. Numeric trig follows the selected angle unit; explicit π and ° override it.",fontSize=12.sp)
        Text("CalcMax 1.0 · Offline engine: SymPy 1.14 (BSD), mpmath 1.3 (BSD), Chaquopy 17 (MIT). No network permission.",fontSize=11.sp)
    }},confirmButton={TextButton(onClick=close) { Text("Done") }})
}

@Composable fun HistoryDialog(m: CalculatorModel,close: ()->Unit) {
    val clipboard=LocalClipboardManager.current
    var favorites by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=close,title={Text("Calculation history")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Choices(listOf("All","Favorites"),if(favorites)"Favorites" else "All",{favorites=it=="Favorites"})
        if(m.history.isEmpty()) Text("Your calculations will appear here.")
        m.history.filter { !favorites||it.favorite }.forEach { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                Text(entry.source,fontFamily=FontFamily.Monospace,fontSize=13.sp)
                Text("= ${entry.exact}",fontFamily=FontFamily.Serif,fontSize=20.sp)
                Text("${entry.mode} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.id))}",fontSize=10.sp,color=LocalInstrument.current.muted)
                Row { SmallAction("Reuse") { m.edit(Editor(entry.source));m.mode="Scientific";close() }; SmallAction(if(entry.favorite)"★" else "☆") { m.favorite(entry.id) }; SmallAction("Copy") { clipboard.setText(AnnotatedString(entry.exact)) }; SmallAction("Delete") { m.deleteHistory(entry.id) } }
            };HorizontalDivider()
        }
    }},confirmButton={TextButton(onClick=close) { Text("Done") }},dismissButton={TextButton(onClick={m.clearHistory()}) { Text("Clear all") }})
}

@Composable fun VariablesDialog(m: CalculatorModel,operation: String,close: ()->Unit) {
    var name by rememberSaveable { mutableStateOf("A") }; var value by rememberSaveable { mutableStateOf(m.editor.source.ifBlank { "0" }) }; var parameters by rememberSaveable { mutableStateOf("x") }; var function by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest=close,title={Text("Variables & functions")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Choices(listOf("A","B","C","D","E","F","X","Y","M"),name,{name=it})
        Field(name,"Name",Modifier.fillMaxWidth()) { name=it }
        Field(value,"Value / expression",Modifier.fillMaxWidth()) { value=it }
        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(function,{function=it});Text("User function") }
        if(function) Field(parameters,"Parameters (comma separated)",Modifier.fillMaxWidth()) { parameters=it }
        Row { SmallAction("Store") { if(function)m.define(name,parameters,value) else m.store(name,value) }; SmallAction("Recall") { m.insert(if(function) "$name()" else name);close() }; SmallAction("Delete") { m.removeVariable(name) } }
        Row { SmallAction("M+") { m.store("M","${if(m.variables.has("M")) "M" else "0"}+($value)") }; SmallAction("M−") { m.store("M","${if(m.variables.has("M")) "M" else "0"}-($value)") } }
        Text("Assumption for $name",fontSize=12.sp)
        Choices(listOf("none","real","positive","integer","nonzero"),m.assumptions.optJSONArray(name)?.optString(0) ?: "none",{m.assume(name,it)})
        Text("Stored: "+m.variables.keys().asSequence().toList().joinToString()+"\nFunctions: "+m.functions.keys().asSequence().toList().joinToString(),fontSize=12.sp)
        if(m.error.isNotBlank()) Text(m.error,color=LocalInstrument.current.danger)
    }},confirmButton={TextButton(onClick=close) { Text("Done") }})
}

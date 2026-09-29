package com.kirinonakar.calcmax.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.math.Editor
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable private fun StatHeader(text:String,modifier:Modifier) { val c=LocalInstrument.current; Box(modifier.fillMaxHeight(),contentAlignment=Alignment.Center){Text(text,fontSize=11.sp,color=c.muted,fontWeight=FontWeight.SemiBold)} }

@Composable private fun StatCell(value:String,modifier:Modifier,focus:FocusRequester,tag:String,onValue:(String)->Unit) {
    val c=LocalInstrument.current
    var focused by remember {mutableStateOf(false)}
    Box(modifier.fillMaxHeight().background(if(focused)c.accent.copy(alpha=.12f) else c.display).then(statCellTouch(focus))) {
        BasicTextField(value,onValue,Modifier.fillMaxSize().focusRequester(focus).onFocusChanged {focused=it.isFocused}.testTag(tag),
            textStyle=MaterialTheme.typography.bodyMedium.copy(fontSize=12.sp,color=c.ink),singleLine=true,cursorBrush=SolidColor(c.accent),
            decorationBox={innerTextField->Box(Modifier.fillMaxSize().padding(horizontal=8.dp),contentAlignment=Alignment.CenterStart){innerTextField()}})
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
                dataKind=when {first.size>=3->"xyz";first.size>=2->"xy";else->"list"}
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
    fun rows():List<List<String>> = statisticsRows(data)
    fun vector(column:Int)=rows().mapNotNull {it.getOrNull(column)?.takeIf(String::isNotBlank)}.joinToString(",","[","]")
    fun variableSource():String=statisticsDataSource(data,dataKind)
    fun startNew() {
        var index=1;val existing=names.toSet();while("D$index" in existing)index++
        datasetName="D$index";data=when(dataKind){"xy"->",";"xyz"->",,";else->""};isNew=true;selected=""
    }
    val parsedRows=rows()
    val xValues=parsedRows.mapNotNull {it.getOrNull(0)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}}
    val yValues=if(dataKind!="list")parsedRows.mapNotNull {it.getOrNull(1)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}} else emptyList()
    val zValues=if(dataKind=="xyz")parsedRows.mapNotNull {it.getOrNull(2)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}} else emptyList()
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
        Choices(listOf("List","x,y data","x,y,z data"),when(dataKind){"xy"->"x,y data";"xyz"->"x,y,z data";else->"List"},{dataKind=when(it){"x,y data"->"xy";"x,y,z data"->"xyz";else->"list"};plotType=if(dataKind=="xy")"Scatter" else "Histogram"})
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            SmallAction("Import CSV"){importCsv.launch(arrayOf("text/csv","text/comma-separated-values","text/plain","application/vnd.ms-excel"))}
            SmallAction("Export CSV"){exportCsv.launch("${datasetName.ifBlank {"dataset"}}.csv")}
            SmallAction("Store as $datasetName"){if(datasetName.matches(Regex("[A-Za-z][A-Za-z0-9_]*")))m.store(datasetName,variableSource(),false)else m.error="Dataset name must be a valid variable name"}
            SmallAction(if(csv)"Table editor" else "Direct input"){csv=!csv}
            SmallAction("Add row"){if(parsedRows.size<999)data+=when(dataKind){"xy"->"\n,";"xyz"->"\n,,";else->"\n"}}
        }
        if(csv)OutlinedTextField(data,{data=it},Modifier.fillMaxWidth().height(180.dp),label={Text(when(dataKind){"xy"->if(isKorean())"x, y 값" else "x, y values";"xyz"->if(isKorean())"x, y, z 값" else "x, y, z values";else->tr("One value per line")})},textStyle=MaterialTheme.typography.bodyLarge.copy(fontFamily=FontFamily.Monospace))
        else {
            val grid=LocalInstrument.current.grid
            val tableColumns=when(dataKind){"xy"->listOf("x","y");"xyz"->listOf("x","y","z");else->listOf("value")}
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
                SmallAction(if(dataKind=="list")"List" else "x",translate=false){val values=vector(0);if(values!="[]")summarize("stats($values)")}
                if(dataKind!="list")SmallAction("y",translate=false){val values=vector(1);if(values!="[]")summarize("stats($values)")}
                if(dataKind=="xyz")SmallAction("z",translate=false){val values=vector(2);if(values!="[]")summarize("stats($values)")}
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
        StatisticsPlot(plotType,if(plotType=="Scatter")paired else xValues.mapIndexed {i,v->i.toDouble() to v},xValues,yValues,if(fitVisible)m.regressionCurve.orEmpty() else emptyList(),if(fitVisible)m.regressionFit else "",m.displayDigits,fitVisible&&m.regressionMode=="linear",m.regressionCorrelation,tertiary=zValues)
        if(m.regressionBusy)Text(if(isKorean())"회귀 적합 중…" else "Fitting regression…",fontSize=11.sp,color=LocalInstrument.current.muted)
        if(dataKind=="xy"&&m.regressionData==data&&m.regressionFit.isNotBlank()) {
            Column(verticalArrangement=Arrangement.spacedBy(0.dp)) {
                if(m.regressionParameters.isNotEmpty()) {
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
                SmallAction("Graph fitted expression"){
                    val fit=if(m.regressionMode=="custom")m.regressionFit.replace(Regex("(?<![A-Za-z0-9_])${Regex.escape(customVariable)}(?![A-Za-z0-9_])"),"x") else m.regressionFit
                    val graphSource=regressionFormulaGraphSource(fit,m.displayDigits)
                    if(graphSource==null)m.error="Could not format fitted expression"
                    else {m.changeGraphKind("cartesian");m.updateGraphSource(graphSource);m.mode="Graph";m.plot()}
                }
            }
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

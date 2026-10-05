package com.kirinonakar.symvacas.ui

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
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable private fun StatHeader(text:String,modifier:Modifier) { val c=LocalInstrument.current; Box(modifier.fillMaxHeight(),contentAlignment=Alignment.Center){Text(text,fontSize=11.sp,color=c.muted,fontWeight=FontWeight.SemiBold)} }

@Composable private fun StatCell(value:String,modifier:Modifier,focus:FocusRequester,tag:String,onValue:(String)->Unit) {
    val c=LocalInstrument.current
    var focused by remember {mutableStateOf(false)}
    Box(modifier.fillMaxHeight().background(if(focused)c.accent.copy(alpha=.12f) else c.display).then(statCellTouch(focus))) {
        BasicTextField(value,onValue,Modifier.fillMaxSize().keepInputVisible().focusRequester(focus).onFocusChanged {focused=it.isFocused}.testTag(tag),
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
    var columnCount by rememberSaveable {mutableStateOf(if(dataKind.startsWith("columns:"))statisticsColumnCount(dataKind).toString() else "4")}
    val dataColumns=statisticsColumnNames(dataKind)
    var regression by rememberSaveable {mutableStateOf(m.statisticsRegression)}
    var polynomialDegree by rememberSaveable {mutableStateOf(m.statisticsPolynomialDegree)}
    var logisticResponse by rememberSaveable {mutableStateOf(m.statisticsLogisticResponse)}
    val regressionColumns=statisticsRegressionColumns(dataKind)
    val responseColumn=if(regression in listOf("polynomial","logistic")) {if(logisticResponse=="0")0 else regressionColumns.lastIndex} else logisticResponse.toIntOrNull()?.takeIf {it in regressionColumns.indices} ?: regressionColumns.lastIndex
    LaunchedEffect(dataKind) {if(regressionColumns.isNotEmpty()&&logisticResponse.isNotBlank()&&logisticResponse.toIntOrNull() !in regressionColumns.indices)logisticResponse=regressionColumns.lastIndex.toString()}
    var customFormula by rememberSaveable {mutableStateOf(m.statisticsCustomFormula)}
    var customVariable by rememberSaveable {mutableStateOf(m.statisticsCustomVariable)}
    var customInitials by rememberSaveable {mutableStateOf(m.statisticsCustomInitials)}
    var plotType by rememberSaveable {mutableStateOf(m.statisticsPlot)}
    var csv by rememberSaveable {mutableStateOf(m.statisticsCsv)}
    var importPreview by remember {mutableStateOf<StatisticsCsvImport?>(null)}
    LaunchedEffect(data,datasetName,dataKind,regression,plotType,csv,selected,isNew,customFormula,customVariable,customInitials,polynomialDegree,logisticResponse) {m.saveStatistics(datasetName,data,dataKind,regression,plotType,csv,selected,isNew,customFormula,customVariable,customInitials,polynomialDegree,logisticResponse)}
    val importCsv=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)scope.launch {
            val content=withContext(Dispatchers.IO) {runCatching {context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {it.readText()}}.getOrNull()}
            if(content!=null) {
                val preview=previewStatisticsCsv(content)
                if(preview.columnCount==0)m.error="The selected CSV file is empty"
                else importPreview=preview
            } else m.error="Could not read the selected CSV file"
        }
    }
    importPreview?.let {preview->StatisticsCsvImportDialog(preview,onDismiss={importPreview=null}) {columns,skipHeader->
        m.clearRegression();data=importStatisticsCsv(preview,columns,skipHeader)
        dataKind=statisticsKindForColumns(columns.size);columnCount=columns.size.toString()
        plotType=if(dataKind=="xy")"Scatter" else "Histogram"
        datasetName=datasetName.ifBlank {activeName.ifBlank {"D1"}}
        m.saveDataSet(datasetName,data,dataKind);selected=datasetName;isNew=false
        importPreview=null
    }}
    val exportCsv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {uri->
        if(uri!=null)scope.launch {
            val success=withContext(Dispatchers.IO) {runCatching {val stream=context.contentResolver.openOutputStream(uri)?:error("No output stream");stream.bufferedWriter().use {it.write(data)};true}.getOrDefault(false)}
            if(!success)m.error="Could not write the CSV file"
        }
    }
    fun rows():List<List<String>> = statisticsRows(data)
    fun variableSource():String=statisticsDataSource(data,dataKind)
    fun startNew() {
        var index=1;val existing=names.toSet();while("D$index" in existing)index++
        datasetName="D$index";data=",".repeat(dataColumns.size-1);isNew=true;selected=""
    }
    val parsedRows=rows()
    val dateAxis=if(dataColumns.size>1)statisticsDateAxis(parsedRows) else null
    val numericRows=statisticsNumericRows(parsedRows,dateAxis)
    fun vector(column:Int)=numericRows.mapNotNull {it.getOrNull(column)?.takeIf(String::isNotBlank)}.joinToString(",","[","]")
    val xValues=numericRows.mapNotNull {it.getOrNull(0)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}}
    val yValues=if(dataKind!="list")numericRows.mapNotNull {it.getOrNull(1)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}} else emptyList()
    val zValues=if(dataColumns.size>=3)numericRows.mapNotNull {it.getOrNull(2)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}} else emptyList()
    val paired=numericRows.mapNotNull {row->val x=row.getOrNull(0)?.toDoubleOrNull();val y=row.getOrNull(1)?.toDoubleOrNull();if(x!=null&&y!=null&&x.isFinite()&&y.isFinite())x to y else null}
    var section by rememberSaveable {mutableStateOf("Data")}
    if(section=="Data") Panel("Data & statistics","Enter values once, then summarize, test, or plot the current dataset.",panelScroll) {
        Choices(listOf("Data & analysis","Distributions"),"Data & analysis",{section=if(it=="Data & analysis")"Data" else it})
        if(names.isNotEmpty())Choices(names,activeName,{name->m.clearRegression();selected=name;isNew=false;m.dataSets.optJSONObject(name)?.let {item->datasetName=name;data=item.optString("csv");dataKind=item.optString("kind","list");columnCount=if(dataKind.startsWith("columns:"))statisticsColumnCount(dataKind).toString() else "4";plotType=if(dataKind=="xy")"Scatter" else "Histogram"}})
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            Field(datasetName,"Dataset name",Modifier.weight(1f)){datasetName=it}
            SmallAction("New"){startNew()}
            SmallAction("Save"){m.saveDataSet(datasetName,data,dataKind);selected=datasetName;isNew=false}
            SmallAction("Delete"){if(activeName.isNotBlank()){m.deleteDataSet(activeName);selected="";isNew=true;startNew()}}
        }
        Choices(listOf("List","x,y data","x,y,z data","n columns"),when(dataKind){"xy"->"x,y data";"xyz"->"x,y,z data";"list"->"List";else->"n columns"},{m.clearRegression();dataKind=when(it){"x,y data"->"xy";"x,y,z data"->"xyz";"n columns"->"columns:${columnCount.toIntOrNull()?.coerceIn(1,100) ?: 4}";else->"list"};plotType=if(dataKind=="xy")"Scatter" else "Histogram"})
        if(dataKind.startsWith("columns:"))Field(columnCount,"Column count (1–100)",Modifier.width(170.dp).testTag("statistics-columns")) {text->
            columnCount=text
            text.toIntOrNull()?.takeIf {it in 1..100}?.let {m.clearRegression();dataKind="columns:$it"}
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            SmallAction("Import CSV"){importCsv.launch(arrayOf("text/csv","text/comma-separated-values","text/plain","application/vnd.ms-excel"))}
            SmallAction("Export CSV"){exportCsv.launch("${datasetName.ifBlank {"dataset"}}.csv")}
            SmallAction("Store as $datasetName"){if(datasetName.matches(Regex("[A-Za-z][A-Za-z0-9_]*")))m.store(datasetName,variableSource(),false)else m.error="Dataset name must be a valid variable name"}
            SmallAction(if(csv)"Table editor" else "Direct input"){csv=!csv}
            SmallAction("Add row"){if(parsedRows.size<999)data+="\n"+",".repeat(dataColumns.size-1)}
        }
        if(csv)OutlinedTextField(data,{data=it},Modifier.fillMaxWidth().height(180.dp).keepInputVisible(),label={Text(if(dataKind.startsWith("columns:"))dataColumns.joinToString(", ") else when(dataKind){"xy"->if(isKorean())"x, y 값" else "x, y values";"xyz"->if(isKorean())"x, y, z 값" else "x, y, z values";else->tr("One value per line")})},textStyle=MaterialTheme.typography.bodyLarge.copy(fontFamily=FontFamily.Monospace))
        else {
            val grid=LocalInstrument.current.grid
            val tableColumns=if(dataKind=="list")listOf("value") else dataColumns
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tableWidth=maxOf(maxWidth,if(tableColumns.size>3)(tableColumns.size*90+78).dp else 0.dp)
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(tableWidth).border(1.dp,grid).heightIn(max=300.dp).verticalScroll(rememberScrollState()).testTag("statistics-table")) {
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
                                        val next=parsedRows.map {it.toMutableList().apply {while(size<tableColumns.size)add("")}}.toMutableList();next[index][column]=text;data=next.joinToString("\n",transform=::statisticsCsvLine)
                                    }
                                    VerticalDivider(color=grid,thickness=1.dp)
                                }
                                Box(Modifier.width(48.dp).fillMaxHeight(),contentAlignment=Alignment.Center){SmallAction("−"){data=parsedRows.filterIndexed {i,_->i!=index}.joinToString("\n",transform=::statisticsCsvLine)}}
                            }
                            if(index<parsedRows.lastIndex)HorizontalDivider(color=grid,thickness=1.dp)
                        }
                    }
                }
            }
        }
        if(dataKind!="list")Text(if(isKorean())"x 날짜 형식: YYYY-MM-DD, YYYY/MM/DD, YYYY.MM.DD" else "x date formats: YYYY-MM-DD, YYYY/MM/DD, YYYY.MM.DD",fontSize=11.sp,color=LocalInstrument.current.muted)
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(tr("Quick summaries"),style=MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                fun summarize(command:String) {summaryResultPending=true;m.edit(Editor(command));m.calculate();scope.launch {panelScroll.animateScrollTo(panelScroll.maxValue)}}
                SmallAction(if(dataKind=="list")"List" else "x",translate=false){val values=vector(0);if(values!="[]")summarize("stats($values)")}
                dataColumns.drop(1).forEachIndexed {index,name->SmallAction(name,translate=false){val values=vector(index+1);if(values!="[]")summarize("stats($values)")}}
                val correlationCommand=statisticsCorrelationCommand(numericRows,dataKind)
                if(dataKind=="xy")SmallAction("correlation",active=if(correlationCommand==null)false else null,translate=false,modifier=Modifier.testTag("statistics-correlation")){correlationCommand?.let {summarize(it)}}
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Visualize & regression"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                if(dataKind!="list")SmallAction("Clear regression"){m.clearRegression()}
            }
            val activeRegression=if(m.regressionFit.isNotBlank()&&m.regressionData==data)m.regressionMode else ""
            if(dataColumns.size>1)Choices(if(dataKind=="xyz"||dataKind.startsWith("columns:"))listOf("multiple","logistic") else listOf("linear","quadratic","polynomial","logarithmic","exponential","power","logistic","custom"),if(regression in listOf("custom","polynomial"))regression else activeRegression,{selectedMode->
                regression=selectedMode;plotType=if(dataKind=="xy")"Scatter" else "Histogram"
                val selectedResponse=if(selectedMode in listOf("polynomial","logistic")) {if(logisticResponse=="0")0 else regressionColumns.lastIndex} else responseColumn
                if(selectedMode in listOf("polynomial","logistic")&&logisticResponse!="0")logisticResponse=""
                if(selectedMode in listOf("custom","polynomial"))m.clearRegression()
                if(selectedMode !in listOf("custom","polynomial")) {
                    val table=statisticsRegressionTable(numericRows,dataKind,selectedMode,selectedResponse)
                    if(table!=null)m.fitRegression("regression($table,$selectedMode)",data,if(selectedMode in listOf("multiple","logistic"))selectedResponse else null)
                    else m.error="Add more data points than fit parameters"
                }
            })
            if(dataKind=="xy"&&regression=="polynomial") {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Field(polynomialDegree,"Polynomial degree (1–10)",Modifier.weight(1f)){m.clearRegression();polynomialDegree=it}
                    Button(onClick={
                        val table=statisticsRegressionTable(numericRows,dataKind,"polynomial",responseColumn)
                        if(table!=null)m.fitRegression("regression($table,polynomial,$polynomialDegree)",data,responseColumn)
                    },enabled=(polynomialDegree.toIntOrNull() ?: 0) in 1..10&&!m.regressionBusy){Text(tr("Analyze"))}
                }
            }
            if(dataKind!="list"&&regression in listOf("multiple","logistic"))Text(tr(if(regression=="logistic")"Selected column is response; others are predictors. Logistic response: 0 or 1." else "Selected column is response; others are predictors."),fontSize=11.sp,color=LocalInstrument.current.muted)
            if(dataColumns.size>1&&regression in listOf("multiple","logistic","polynomial")) {
                Text(tr("Dependent variable"),fontSize=11.sp,color=LocalInstrument.current.muted)
                if(regression in listOf("polynomial","logistic"))Choices(listOf("first","last"),if(responseColumn==0)"first" else "last",{position->m.clearRegression();logisticResponse=if(position=="first")"0" else ""})
                else Choices(regressionColumns,regressionColumns.getOrNull(responseColumn).orEmpty(),{name->m.clearRegression();logisticResponse=regressionColumns.indexOf(name).toString()},translate=false)
                val table=statisticsRegressionTable(numericRows,dataKind,regression,responseColumn)
                if(regression!="polynomial")Button(onClick={table?.let {m.fitRegression("regression($it,$regression)",data,responseColumn)}},enabled=table!=null&&!m.regressionBusy){Text(tr("Analyze"))}
            }
            if(m.regressionBusy)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(if(isKorean())"회귀 적합 중…" else "Fitting regression…",Modifier.weight(1f),fontSize=11.sp,color=LocalInstrument.current.muted)
                SmallAction("Cancel",modifier=Modifier.testTag("statistics-regression-cancel")){m.cancelRegression()}
            }
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
                    val table=numericRows.filter {it.size>=2&&it[0].isNotBlank()&&it[1].isNotBlank()}.joinToString(",","[","]"){it.take(2).joinToString(",","[","]")}
                    val guesses=customInitials.trim().takeIf(String::isNotEmpty)?.let {",$it"}.orEmpty()
                    m.fitRegression("regression($table,custom,$customFormula,$customVariable$guesses)",data)
                },enabled=customFormula.isNotBlank()&&customVariable.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))&&paired.size>=2&&!m.regressionBusy){Text(tr("Fit custom model"))}
            }
            Choices(if(dataKind=="xy")listOf("Scatter","Histogram","Box plot") else listOf("Histogram","Box plot"),plotType,{plotType=it})
        }
        val fittedResponse=if(m.regressionMode in listOf("multiple","logistic","polynomial"))m.regressionResponseColumn?.takeIf {it in regressionColumns.indices} ?: regressionColumns.lastIndex else regressionColumns.lastIndex
        val fittedVariables=statisticsRegressionVariables(dataKind,fittedResponse)
        val fittedResponseName=regressionColumns.getOrNull(fittedResponse).orEmpty()
        val fitVisible=dataKind=="xy"&&plotType=="Scatter"&&m.regressionData==data&&m.regressionFit.isNotBlank()
        val plotPairs=if(fitVisible&&m.regressionMode in listOf("logistic","polynomial")&&fittedResponse==0)paired.map {(x,y)->y to x} else paired
        StatisticsPlot(plotType,if(plotType=="Scatter")plotPairs else xValues.mapIndexed {i,v->i.toDouble() to v},xValues,yValues,if(fitVisible)m.regressionCurve.orEmpty() else emptyList(),if(fitVisible)m.regressionFit else "",m.displayDigits,fitVisible&&m.regressionMode=="linear",m.regressionCorrelation,tertiary=zValues,allColumns=dataColumns.mapIndexed {index,name->name to numericRows.mapNotNull {it.getOrNull(index)?.toDoubleOrNull()?.takeIf(Double::isFinite)}},xDateOrigin=if(fitVisible&&fittedResponse==0)null else dateAxis?.origin,
            xAxisLabel=if(fitVisible)fittedVariables["x"] ?: "x" else "x",yAxisLabel=if(fitVisible)fittedResponseName else "y",
            fitPrefix=if(fitVisible&&m.regressionMode=="logistic")"P($fittedResponseName = 1) = " else if(fitVisible)"$fittedResponseName ≈ " else "y ≈ ",fitVariables=if(fitVisible&&m.regressionMode in listOf("logistic","polynomial"))fittedVariables else emptyMap())
        if(dateAxis!=null&&plotType=="Scatter")Text((if(isKorean())"회귀식의 x: ${dateAxis.origin.plusDays(1)} = 1일째" else "Regression x: ${dateAxis.origin.plusDays(1)} = day 1"),fontSize=11.sp,color=LocalInstrument.current.muted)
        if(dataKind!="list"&&m.regressionData==data&&m.regressionFit.isNotBlank()) {
            Column(verticalArrangement=Arrangement.spacedBy(0.dp)) {
                if(dataKind=="xyz"||dataKind.startsWith("columns:")) {
                    val equation=remember(m.regressionFit,m.displayDigits,fittedVariables) {regressionFormulaDisplayTree(m.regressionFit,m.displayDigits,fittedVariables)}
                    CompositionLocalProvider(LocalMathMinimumSize provides 8f) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=3.dp).testTag("statistics-regression-equation"),
                            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            MathText(if(m.regressionMode=="logistic")"P($fittedResponseName = 1) = " else "$fittedResponseName = ",12f,Modifier.alignBy(MathAxis))
                            Box(Modifier.alignBy(MathAxis)) {
                                if(equation!=null)MathNode(equation,12f)
                                else Text(m.regressionFit,fontSize=12.sp,fontFamily=FontFamily.Monospace)
                            }
                        }
                    }
                }
                m.regressionReport?.let {RegressionInference(it,m.displayDigits)}
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
                if(dataKind=="xy")SmallAction("Graph fitted expression"){
                    val fit=if(m.regressionMode=="custom")m.regressionFit.replace(Regex("(?<![A-Za-z0-9_])${Regex.escape(customVariable)}(?![A-Za-z0-9_])"),"x") else m.regressionFit
                    val graphSource=regressionFormulaGraphSource(fit,m.displayDigits)
                    if(graphSource==null)m.error="Could not format fitted expression"
                    else {m.changeGraphKind("cartesian");m.updateGraphSource(graphSource);m.mode="Graph";m.plot()}
                }
            }
        }
        StatisticsAnalysis(m,numericRows,if(dataColumns.size==1)"list" else dataKind)
        Display(m,requestInitialFocus=false)
    } else {
        Panel(section,"") {
            Choices(listOf("Data & analysis","Distributions"),section,{section=if(it=="Data & analysis")"Data" else it})
            DistributionSection(m)
        }
    }
}

@Composable private fun StatisticsCsvImportDialog(preview:StatisticsCsvImport,onDismiss:()->Unit,onImport:(List<Int>,Boolean)->Unit) {
    var skipHeader by remember(preview) {mutableStateOf(preview.hasHeader)}
    var columnCount by remember(preview) {mutableIntStateOf(minOf(3,preview.columnCount))}
    var columns by remember(preview) {mutableStateOf((0 until minOf(100,preview.columnCount)).toList())}
    val names=List(minOf(100,preview.columnCount)){listOf("x","y","z").getOrNull(it) ?: "x${it+1}"}
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr("Import CSV"))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(skipHeader,{skipHeader=it})
                Text(tr("First row is a header"))
            }
            Text(if(preview.hasHeader)tr("Header detected automatically") else tr("No header detected"),style=MaterialTheme.typography.bodySmall)
            Text(tr("Import as"),style=MaterialTheme.typography.titleSmall)
            Field(columnCount.toString(),"Column count (1–100)",Modifier.fillMaxWidth()){text->text.toIntOrNull()?.takeIf {it in 1..minOf(100,preview.columnCount)}?.let {columnCount=it}}
            Text(tr("Choose a CSV column for each variable"),style=MaterialTheme.typography.bodySmall)
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                repeat(columnCount) {index->
                    var expanded by remember(preview,index) {mutableStateOf(false)}
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(names[index],Modifier.width(20.dp),fontWeight=FontWeight.SemiBold)
                        Box {
                            OutlinedButton(onClick={expanded=true}) {Text(preview.labels[columns[index]])}
                            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                                preview.labels.forEachIndexed {source,label->
                                    DropdownMenuItem(text={Text(label)},onClick={
                                        val next=columns.toMutableList()
                                        val duplicate=next.indexOf(source)
                                        if(duplicate>=0&&duplicate!=index)next[duplicate]=next[index]
                                        next[index]=source;columns=next;expanded=false
                                    })
                                }
                            }
                        }
                    }
                }
            }
            Text(tr("Preview"),style=MaterialTheme.typography.titleSmall)
            preview.rows.drop(if(skipHeader)1 else 0).take(3).forEach {row->
                Text(columns.take(columnCount).joinToString("  |  ") {row.getOrNull(it).orEmpty()},fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
            }
        }
    },confirmButton={TextButton(onClick={onImport(columns.take(columnCount),skipHeader)},enabled=preview.rows.size>(if(skipHeader)1 else 0)){Text(tr("Import"))}},dismissButton={TextButton(onClick=onDismiss){Text(tr("Cancel"))}})
}

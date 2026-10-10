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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.max

@Composable private fun StatHeader(text:String,modifier:Modifier) { val c=LocalInstrument.current; Box(modifier.fillMaxHeight(),contentAlignment=Alignment.Center){Text(text,Modifier.padding(horizontal=4.dp),fontSize=11.sp,color=c.muted,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)} }
@Composable private fun HeatMapAxisPicker(title:String,columns:List<Pair<Int,String>>,selected:Set<Int>,onToggle:(Int)->Unit) {
    StatisticsSelectionTitle(title)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        columns.forEach {(index,name)->FilterChip(index in selected,onClick={onToggle(index)},label={Text(name,fontSize=12.sp)})}
    }
}
private fun parseHeatMapSelection(value:String,count:Int,defaults:Set<Int>):Set<Int> = when {
    value.isBlank()->defaults
    value=="-"->emptySet()
    else->value.split(',').mapNotNull {it.toIntOrNull()?.takeIf {index->index in 0 until count}}.toSet().ifEmpty {defaults}
}
private fun encodeHeatMapSelection(selection:Set<Int>)=selection.sorted().joinToString(",").ifEmpty {"-"}

internal val LocalStatisticsCollapseRequest=staticCompositionLocalOf {0}

@Composable internal fun StatisticsSectionToggle(title:String,expanded:Boolean,tag:String,depth:Int=0,onClick:()->Unit) {
    val description=tr(if(expanded)"Expanded" else "Collapsed")
    TextButton(onClick=onClick,modifier=Modifier.fillMaxWidth().testTag(tag).semantics {stateDescription=description},
        contentPadding=PaddingValues(horizontal=0.dp,vertical=8.dp)) {
        Text((if(expanded)"▾ " else "▸ ")+tr(title),Modifier.weight(1f),fontSize=if(depth==0)16.sp else 14.sp,fontWeight=if(depth==0)FontWeight.Bold else FontWeight.SemiBold,color=if(depth==0)LocalInstrument.current.accent else LocalInstrument.current.ink)
    }
}

@Composable private fun StatCell(value:String,modifier:Modifier,focus:FocusRequester,tag:String,onValue:(String)->Unit) {
    val c=LocalInstrument.current
    var focused by remember {mutableStateOf(false)}
    Box(modifier.fillMaxHeight().background(if(focused)c.accent.copy(alpha=.12f) else c.display).then(statCellTouch(focus))) {
        BasicTextField(value,onValue,Modifier.fillMaxSize().keepInputVisible().focusRequester(focus).onFocusChanged {focused=it.isFocused}.testTag(tag),
            textStyle=MaterialTheme.typography.bodyMedium.copy(fontSize=12.sp,color=c.ink),singleLine=true,cursorBrush=SolidColor(c.accent),
            decorationBox={innerTextField->Box(Modifier.fillMaxSize().padding(horizontal=8.dp),contentAlignment=Alignment.CenterStart){innerTextField()}})
    }
}

@Composable private fun StatDirectInput(value:String,onValue:(String)->Unit,label:String,expanded:Boolean) {
    val c=LocalInstrument.current
    val vertical=rememberScrollState();val horizontal=rememberScrollState()
    val fieldFocus=remember {FocusRequester()}
    var focused by remember {mutableStateOf(false)}
    val lineHeight=24.sp
    // Keep the first/last line leading and font metrics identical in both columns.
    val style=MaterialTheme.typography.bodyLarge.copy(fontFamily=FontFamily.Monospace,lineHeight=lineHeight,color=c.ink,
        platformStyle=PlatformTextStyle(includeFontPadding=false),
        lineHeightStyle=LineHeightStyle(LineHeightStyle.Alignment.Center,LineHeightStyle.Trim.None))
    val lineCount=value.count {it=='\n'}+1
    Column(Modifier.fillMaxWidth()) {
        Text(label,fontSize=11.sp,color=c.muted)
        Row(Modifier.fillMaxWidth().height(if(expanded)360.dp else 180.dp).border(1.dp,if(focused)c.accent else c.grid)) {
            Box(Modifier.width(38.dp).fillMaxHeight().background(c.scientific).verticalScroll(vertical).then(statCellTouch(fieldFocus))) {
                Text((1..lineCount).joinToString("\n"),Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=10.dp),style=style.copy(color=c.muted),textAlign=TextAlign.End,softWrap=false)
            }
            VerticalDivider(color=c.grid,thickness=1.dp)
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().then(statCellTouch(fieldFocus))) {
                val minFieldWidth=(maxWidth-16.dp).coerceAtLeast(0.dp)
                Box(Modifier.fillMaxSize().verticalScroll(vertical)) {
                    Box(Modifier.horizontalScroll(horizontal)) {
                        BasicTextField(value,onValue,Modifier.widthIn(min=minFieldWidth).keepInputVisible().focusRequester(fieldFocus).onFocusChanged {focused=it.isFocused}.testTag("statistics-direct-input"),
                            textStyle=style,
                            cursorBrush=SolidColor(c.accent),
                            decorationBox={inner->Box(Modifier.padding(horizontal=8.dp,vertical=10.dp)){inner()}})
                    }
                }
            }
        }
    }
}

@Composable private fun StatColumnAction(label:String,enabled:Boolean,description:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    val c=LocalInstrument.current
    Box(modifier.fillMaxHeight().clickable(enabled=enabled,onClick=onClick).semantics {contentDescription=description},contentAlignment=Alignment.Center) {
        Text(label,fontSize=13.sp,color=if(enabled)c.ink else c.muted.copy(alpha=.35f))
    }
}

@Composable fun StatisticsScreen(m: CalculatorModel) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    val scope=rememberCoroutineScope()
    val panelScroll=rememberScrollState()
    var collapseRequest by rememberSaveable {mutableIntStateOf(0)}
    val summaryExpanded=m.statisticsSectionExpanded("summary")
    val visualizeExpanded=m.statisticsSectionExpanded("visualize")
    val regressionExpanded=m.statisticsSectionExpanded("regression")
    val names=remember(m.dataSets) {m.dataSets.keys().asSequence().toList().sorted()}
    var selected by rememberSaveable {mutableStateOf(m.statisticsSelected)}
    var isNew by rememberSaveable {mutableStateOf(m.statisticsIsNew)}
    val activeName=if(isNew)"" else selected.ifBlank {names.firstOrNull().orEmpty()}
    var datasetName by rememberSaveable {mutableStateOf(m.statisticsName)}
    var data by rememberSaveable {mutableStateOf(m.statisticsData)}
    var selectedDataKind by rememberSaveable {mutableStateOf(m.statisticsKind)}
    var columnCount by rememberSaveable {mutableStateOf(if(selectedDataKind.startsWith("columns:"))statisticsColumnCount(selectedDataKind).toString() else "4")}
    var autoColumns by rememberSaveable {mutableStateOf(m.statisticsAutoColumns)}
    val detectedColumns=remember(data) {runCatching {statisticsDetectedColumns(data)}}
    val dataKind=statisticsEffectiveKind(selectedDataKind,if(autoColumns&&data.isNotBlank())detectedColumns.getOrDefault(statisticsColumnCount(selectedDataKind)) else statisticsColumnCount(selectedDataKind))
    val dataColumns=statisticsColumnNames(dataKind)
    var regression by rememberSaveable {mutableStateOf(m.statisticsRegression)}
    var regularization by rememberSaveable {mutableStateOf(m.statisticsRegularization)}
    var l1Ratio by rememberSaveable {mutableStateOf(m.statisticsL1Ratio)}
    var lassoAlpha by rememberSaveable {mutableStateOf(m.statisticsLassoAlpha)}
    var lassoAlphaCv by rememberSaveable {mutableStateOf(false)}
    var bayesianMethod by rememberSaveable {mutableStateOf(m.statisticsBayesianMethod)}
    var nutsSamples by rememberSaveable {mutableStateOf(m.statisticsNutsSamples)}
    var nutsWarmup by rememberSaveable {mutableStateOf(m.statisticsNutsWarmup)}
    var nutsMaxDepth by rememberSaveable {mutableStateOf(m.statisticsNutsMaxDepth)}
    var nutsSeed by rememberSaveable {mutableStateOf(m.statisticsNutsSeed)}
    var nutsChains by rememberSaveable {mutableStateOf(m.statisticsNutsChains)}
    var bayesianPriorSD by rememberSaveable {mutableStateOf(m.statisticsBayesianPriorSD)}
    var bayesianLevel by rememberSaveable {mutableStateOf(m.statisticsBayesianLevel)}
    var bayesianShape by rememberSaveable {mutableStateOf(m.statisticsBayesianShape)}
    var bayesianScale by rememberSaveable {mutableStateOf(m.statisticsBayesianScale)}
    val bayesian=regression in listOf("bayeslinear","bayeslogistic")
    var firthMode by rememberSaveable {mutableStateOf("auto")}
    var forestTask by rememberSaveable {mutableStateOf(m.statisticsForestTask)}
    var forestTrees by rememberSaveable {mutableStateOf(m.statisticsForestTrees)}
    var forestDepth by rememberSaveable {mutableStateOf(m.statisticsForestDepth)}
    var forestSeed by rememberSaveable {mutableStateOf(m.statisticsForestSeed)}
    var polynomialDegree by rememberSaveable {mutableStateOf(m.statisticsPolynomialDegree)}
    var logisticResponse by rememberSaveable {mutableStateOf(m.statisticsLogisticResponse)}
    val regularized=regression in listOf("linear","multiple","logistic")&&regularization!="none"
    val fitMode=if(regularized)(if(regression=="logistic")"logistic" else "")+regularization else if(regression=="randomforest")when(forestTask){"classification"->"randomforestclassifier";"regression"->"randomforestregressor";else->"randomforest"} else regression
    val regressionColumns=statisticsRegressionColumns(dataKind)
    val responseColumn=logisticResponse.toIntOrNull()?.takeIf {it in regressionColumns.indices} ?: regressionColumns.lastIndex
    LaunchedEffect(dataKind) {if(regressionColumns.isNotEmpty()&&logisticResponse.isNotBlank()&&logisticResponse.toIntOrNull() !in regressionColumns.indices)logisticResponse=regressionColumns.lastIndex.toString()}
    var customFormula by rememberSaveable {mutableStateOf(m.statisticsCustomFormula)}
    var customVariable by rememberSaveable {mutableStateOf(m.statisticsCustomVariable)}
    var customInitials by rememberSaveable {mutableStateOf(m.statisticsCustomInitials)}
    var plotGrouping by rememberSaveable {mutableStateOf(m.statisticsPlotGrouping)}
    var plotOrientation by rememberSaveable {mutableStateOf(m.statisticsPlotOrientation)}
    var plotType by rememberSaveable {mutableStateOf(if(m.statisticsPlot in listOf("Clustered heatmap","Correlation heat map"))"Heat map" else m.statisticsPlot)}
    var heatMapMode by rememberSaveable {mutableStateOf(if(m.statisticsPlot=="Correlation heat map")"correlation" else m.statisticsHeatMapMode)}
    var heatMapCorrelation by rememberSaveable {mutableStateOf(m.statisticsHeatMapCorrelation)}
    var heatMapXColumns by rememberSaveable {mutableStateOf(m.statisticsHeatMapXColumns)}
    var heatMapYColumns by rememberSaveable {mutableStateOf(m.statisticsHeatMapYColumns)}
    var heatMapClustering by rememberSaveable {mutableStateOf(m.statisticsHeatMapClustering||m.statisticsPlot=="Clustered heatmap")}
    var heatMapFit by rememberSaveable {mutableStateOf(m.statisticsHeatMapFit)}
    var heatMapLinkage by rememberSaveable {mutableStateOf("average")}
    var heatMapMetric by rememberSaveable {mutableStateOf("euclidean")}
    var csv by rememberSaveable {mutableStateOf(m.statisticsCsv)}
    val editorExpanded=m.statisticsSectionExpanded("editor")
    var importPreview by remember {mutableStateOf<StatisticsCsvImport?>(null)}
    var importSheets by remember {mutableStateOf<List<StatisticsXlsxSheet>?>(null)}
    val importCsv=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)scope.launch {
            val result=withContext(Dispatchers.IO) {runCatching {
                val bytes=context.contentResolver.openInputStream(uri)?.use {stream->
                    val output=ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0L
                    while(true){val count=stream.read(buffer);if(count<0)break;total+=count;if(total>64L*1024*1024)error("Import file is too large");output.write(buffer,0,count)}
                    output.toByteArray()
                }?:error("Could not read the selected file")
                val mime=context.contentResolver.getType(uri).orEmpty()
                val xlsx=mime.contains("spreadsheetml.sheet")||bytes.size>=4&&bytes[0]==0x50.toByte()&&bytes[1]==0x4b.toByte()&&bytes[2]==0x03.toByte()&&bytes[3]==0x04.toByte()
                if(xlsx)previewStatisticsXlsx(bytes,m.removeComputationLimit) else previewStatisticsCsv(String(bytes,StandardCharsets.UTF_8))
            }}
            result.onSuccess {parsed->when(parsed) {
                is StatisticsXlsxWorkbook->{val sheets=parsed.sheets.filter {it.preview.columnCount>0};if(sheets.size==1)importPreview=sheets.single().preview else importSheets=sheets}
                is StatisticsCsvImport->if(parsed.columnCount==0)m.error="The selected file is empty" else importPreview=parsed
            }}
                .onFailure {exception->m.error=exception.message?:"Could not read the selected file"}
        }
    }
    importSheets?.let {sheets->StatisticsXlsxSheetDialog(sheets,onDismiss={importSheets=null}) {preview->importSheets=null;importPreview=preview}}
    importPreview?.let {preview->StatisticsCsvImportDialog(preview,onDismiss={importPreview=null}) {columns,skipHeader->
        m.clearRegression();data=importStatisticsCsv(preview,columns,skipHeader)
        selectedDataKind=statisticsKindForColumns(columns.size);columnCount=columns.size.toString()
        plotType=if(selectedDataKind=="xy")"Scatter" else "Histogram"
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
    fun startNew() {
        var index=1;val existing=names.toSet();while("D$index" in existing)index++
        m.clearRegression();selectedDataKind=dataKind;columnCount=dataColumns.size.toString()
        datasetName="D$index";data="";isNew=true;selected=""
    }
    val parsedRows=rows()
    val heatMapColumnNames=remember(data,dataKind) {statisticsHeatMapColumnNames(data,dataKind)}
    val heatMapAxisIndices=remember(parsedRows,dataKind) {dataColumns.indices.filter {column->parsedRows.any {statisticsPlotNumber(it.getOrNull(column))!=null}}}
    val validHeatMapAxes=heatMapAxisIndices.toSet()
    val heatMapAxisSplit=(heatMapAxisIndices.size+1)/2
    val defaultHeatMapX=heatMapAxisIndices.take(heatMapAxisSplit).toSet()
    val defaultHeatMapY=heatMapAxisIndices.drop(heatMapAxisSplit).toSet()
    val heatMapXSelection=parseHeatMapSelection(heatMapXColumns,dataColumns.size,defaultHeatMapX).intersect(validHeatMapAxes)
    val heatMapYSelection=parseHeatMapSelection(heatMapYColumns,dataColumns.size,defaultHeatMapY).intersect(validHeatMapAxes)-heatMapXSelection
    LaunchedEffect(data,datasetName,dataKind,regression,plotType,plotGrouping,plotOrientation,heatMapMode,heatMapCorrelation,heatMapXColumns,heatMapYColumns,heatMapClustering,heatMapFit,autoColumns,csv,selected,isNew,customFormula,customVariable,customInitials,polynomialDegree,logisticResponse,lassoAlpha,forestTrees,forestDepth,forestSeed,regularization,l1Ratio,forestTask,bayesianPriorSD,bayesianLevel,bayesianShape,bayesianScale,bayesianMethod,nutsSamples,nutsWarmup,nutsMaxDepth,nutsSeed,nutsChains) {m.statisticsAutoColumns=autoColumns;m.statisticsPlotGrouping=plotGrouping;m.statisticsPlotOrientation=plotOrientation;m.statisticsHeatMapMode=heatMapMode;m.statisticsHeatMapCorrelation=heatMapCorrelation;m.statisticsHeatMapXColumns=if(heatMapAxisIndices.size<2)"" else encodeHeatMapSelection(heatMapXSelection);m.statisticsHeatMapYColumns=if(heatMapAxisIndices.size<2)"" else encodeHeatMapSelection(heatMapYSelection);m.statisticsHeatMapClustering=heatMapClustering;m.statisticsHeatMapFit=heatMapFit;m.saveStatistics(datasetName,data,dataKind,regression,plotType,csv,selected,isNew,customFormula,customVariable,customInitials,polynomialDegree,logisticResponse,lassoAlpha,forestTrees,forestDepth,forestSeed,regularization,l1Ratio,forestTask,bayesianPriorSD,bayesianLevel,bayesianShape,bayesianScale,bayesianMethod,nutsSamples,nutsWarmup,nutsMaxDepth,nutsSeed,nutsChains)}
    val dateAxis=if(dataColumns.size>1)statisticsDateAxis(parsedRows) else null
    val numericRows=statisticsNumericRows(parsedRows,dateAxis)
    fun vector(column:Int)=numericRows.mapNotNull {it.getOrNull(column)?.takeIf(String::isNotBlank)}.joinToString(",","[","]")
    val xValues=numericRows.mapNotNull {it.getOrNull(0)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}}
    val yValues=if(dataKind!="list")numericRows.mapNotNull {it.getOrNull(1)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}} else emptyList()
    val zValues=if(dataColumns.size>=3)numericRows.mapNotNull {it.getOrNull(2)?.toDoubleOrNull()?.takeIf {v->v.isFinite()}} else emptyList()
    val paired=numericRows.mapNotNull {row->val x=row.getOrNull(0)?.toDoubleOrNull();val y=row.getOrNull(1)?.toDoubleOrNull();if(x!=null&&y!=null&&x.isFinite()&&y.isFinite())x to y else null}
    Box(Modifier.fillMaxSize()) {
    Panel("Data & statistics","",panelScroll) {
        CompositionLocalProvider(LocalStatisticsCollapseRequest provides collapseRequest) {
        if(names.isNotEmpty())Choices(names,activeName,{name->m.clearRegression();selected=name;isNew=false;m.dataSets.optJSONObject(name)?.let {item->datasetName=name;data=item.optString("csv");selectedDataKind=item.optString("kind","list");columnCount=if(selectedDataKind.startsWith("columns:"))statisticsColumnCount(selectedDataKind).toString() else "4";plotType=if(selectedDataKind=="xy")"Scatter" else "Histogram"}})
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            Field(datasetName,"Dataset name",Modifier.weight(1f)){datasetName=it}
            SmallAction("New"){startNew()}
            SmallAction("Save"){m.saveDataSet(datasetName,data,dataKind);selected=datasetName;isNew=false}
            SmallAction("Delete"){if(activeName.isNotBlank()){m.deleteDataSet(activeName);selected="";isNew=true;startNew()}}
        }
        Choices(listOf("List","x,y data","x,y,z data","n columns"),when(dataKind){"xy"->"x,y data";"xyz"->"x,y,z data";"list"->"List";else->"n columns"},{m.clearRegression();selectedDataKind=when(it){"x,y data"->"xy";"x,y,z data"->"xyz";"n columns"->"columns:${columnCount.toIntOrNull()?.coerceIn(1,100) ?: 4}";else->"list"};plotType=if(selectedDataKind=="xy")"Scatter" else "Histogram"})
        if(selectedDataKind.startsWith("columns:")) {
            Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Field(if(autoColumns)statisticsColumnCount(dataKind).toString() else columnCount,"Column count (1–100)",Modifier.width(170.dp).testTag("statistics-columns"),enabled=!autoColumns) {text->
                    columnCount=text
                    text.toIntOrNull()?.takeIf {it in 1..100}?.let {m.clearRegression();selectedDataKind="columns:$it"}
                }
                Checkbox(autoColumns,{m.clearRegression();if(autoColumns){columnCount=statisticsColumnCount(dataKind).toString();selectedDataKind="columns:${statisticsColumnCount(dataKind)}"};autoColumns=it},Modifier.testTag("statistics-columns-auto"))
                Text(tr("Auto columns"),fontSize=12.sp)
            }
            if(autoColumns&&detectedColumns.isFailure)Text(tr("Column count must be between 1 and 100"),fontSize=11.sp,color=LocalInstrument.current.danger)
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            SmallAction("Import CSV/XLSX"){importCsv.launch(arrayOf("text/csv","text/comma-separated-values","text/plain","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))}
            SmallAction("Export CSV"){exportCsv.launch("${datasetName.ifBlank {"dataset"}}.csv")}
            SmallAction(if(csv)"Table editor" else "Direct input"){csv=!csv}
            SmallAction("Add row"){if(parsedRows.size<999)data+="\n"+",".repeat(dataColumns.size-1)}
            val editorStateDescription=tr(if(editorExpanded)"Expanded" else "Collapsed")
            SmallAction(if(editorExpanded)"Collapse" else "Expand",modifier=Modifier.testTag("statistics-editor-expand").semantics {stateDescription=editorStateDescription}) {m.setStatisticsSectionExpanded("editor",!editorExpanded)}
        }
        if(csv)StatDirectInput(data,{updated->data=normalizeStatisticsMarkdownPaste(data,updated)?:updated},if(dataKind=="list")tr("One value per line") else statisticsTableColumnLabels(data,dataKind).joinToString(", ")+(if(dataKind=="xy"||dataKind=="xyz")(if(isKorean())" 값" else " values") else ""),editorExpanded)
        else {
            val grid=LocalInstrument.current.grid
            val tableColumns=statisticsTableColumnLabels(data,dataKind)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tableWidth=maxOf(maxWidth,if(tableColumns.size>3)(tableColumns.size*90+78).dp else 0.dp)
                val headerHeight=if(tableColumns.size>1)62.dp else 31.dp
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(tableWidth).border(1.dp,grid).testTag("statistics-table")) {
                        Row(Modifier.fillMaxWidth().height(30.dp).background(LocalInstrument.current.scientific)) {
                            StatHeader("#",Modifier.width(30.dp)); VerticalDivider(color=grid,thickness=1.dp)
                            tableColumns.forEach {name->StatHeader(name,Modifier.weight(1f));VerticalDivider(color=grid,thickness=1.dp)}
                            StatHeader("",Modifier.width(48.dp))
                        }
                        HorizontalDivider(color=grid,thickness=1.dp)
                        if(tableColumns.size>1) {
                            Row(Modifier.fillMaxWidth().height(30.dp).background(LocalInstrument.current.scientific)) {
                                Box(Modifier.width(30.dp).fillMaxHeight()); VerticalDivider(color=grid,thickness=1.dp)
                                tableColumns.forEachIndexed {column,_->
                                    Row(Modifier.weight(1f).fillMaxHeight()) {
                                        StatColumnAction("◀",column>0,tr("Move column left"),Modifier.weight(1f)) {data=statisticsMoveColumn(data,column,-1)}
                                        StatColumnAction("▶",column<tableColumns.lastIndex,tr("Move column right"),Modifier.weight(1f)) {data=statisticsMoveColumn(data,column,1)}
                                        StatColumnAction("−",true,tr("Delete column"),Modifier.weight(1f)) {
                                            val remaining=tableColumns.size-1
                                            data=statisticsRemoveColumn(data,column)
                                            m.clearRegression()
                                            if(!autoColumns||!selectedDataKind.startsWith("columns:")) {
                                                columnCount=remaining.toString();selectedDataKind=statisticsKindForColumns(remaining)
                                                if(selectedDataKind!="xy"&&plotType=="Scatter")plotType="Histogram"
                                            }
                                        }
                                    }
                                    VerticalDivider(color=grid,thickness=1.dp)
                                }
                                Box(Modifier.width(48.dp).fillMaxHeight())
                            }
                            HorizontalDivider(color=grid,thickness=1.dp)
                        }
                        Column(Modifier.fillMaxWidth().heightIn(max=(if(editorExpanded)600.dp else 300.dp)-headerHeight).verticalScroll(rememberScrollState())) {
                            parsedRows.forEachIndexed {index,row->
                                val cellFocus=remember(index,tableColumns.size) {List(tableColumns.size){FocusRequester()} }
                                Row(Modifier.fillMaxWidth().height(44.dp)) {
                                    Box(Modifier.width(30.dp).fillMaxHeight().then(statCellTouch(cellFocus.first())),contentAlignment=Alignment.Center){Text("${index+1}",fontSize=12.sp,color=LocalInstrument.current.muted)}
                                    VerticalDivider(color=grid,thickness=1.dp)
                                    repeat(tableColumns.size) {column->
                                        StatCell(row.getOrElse(column){""},Modifier.weight(1f),cellFocus[column],"statistics-cell-$index-$column") {text->
                                            val next=parsedRows.map {it.toMutableList().apply {while(size<tableColumns.size)add("")}}.toMutableList();next[index][column]=text;data=statisticsReplaceDataRows(data,next)
                                        }
                                        VerticalDivider(color=grid,thickness=1.dp)
                                    }
                                    Box(Modifier.width(48.dp).fillMaxHeight(),contentAlignment=Alignment.Center){SmallAction("−"){data=statisticsReplaceDataRows(data,parsedRows.filterIndexed {i,_->i!=index})}}
                                }
                                if(index<parsedRows.lastIndex)HorizontalDivider(color=grid,thickness=1.dp)
                            }
                        }
                    }
                }
            }
        }
        StatisticsSectionToggle("Quick summaries",summaryExpanded,"statistics-summary-toggle") {m.setStatisticsSectionExpanded("summary",!summaryExpanded)}
        if(summaryExpanded)Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                fun summarize(command:String,columnIndex:Int=0) {m.calculationAction="statistics-summary";m.edit(Editor(command));m.calculate(statisticsTermLabels=mapOf("sample:1" to statisticsColumnLabels(data,dataKind)[columnIndex]))}
                SmallAction(statisticsColumnLabels(data,dataKind)[0],translate=false){val values=vector(0);if(values!="[]")summarize("stats($values)")}
                dataColumns.drop(1).forEachIndexed {index,name->SmallAction(statisticsColumnLabels(data,dataKind)[index+1],translate=false){val values=vector(index+1);if(values!="[]")summarize("stats($values)",index+1)}}
                val correlationCommand=statisticsCorrelationCommand(numericRows,dataKind)
                if(dataKind=="xy")SmallAction("correlation",active=if(correlationCommand==null)false else null,translate=false,modifier=Modifier.testTag("statistics-correlation")){correlationCommand?.let {summarize(it)}}
            }
            statisticsReportFor(m.result,m.resultSource,setOf("stats","mean","median","variance","stdev","sumdata","quartiles","correlation","covariance"))?.let {StatisticsResultReport(m,it)}
        AdvancedStatistics(m,data,dataKind,"preparation","Data preparation",onDataApplied={updated->
            m.clearRegression();data=updated
            if(activeName.isNotBlank()&&datasetName==activeName)m.saveDataSet(activeName,updated,dataKind)
        })
        }
            val fittedResponse=if(m.regressionMode in listOf("multiple","logistic","polynomial","ridge","lasso","elasticnet","logisticridge","logisticlasso","logisticelasticnet","randomforest","randomforestclassifier","randomforestregressor","bayeslinear","bayeslogistic"))m.regressionResponseColumn?.takeIf {it in regressionColumns.indices} ?: regressionColumns.lastIndex else regressionColumns.lastIndex
            val fittedVariables=statisticsRegressionVariables(dataKind,fittedResponse)
            val parameterLabels=statisticsRegressionParameterLabels(dataKind,m.regressionMode,fittedResponse,data)
            val fittedResponseName=regressionColumns.getOrNull(fittedResponse).orEmpty()
        StatisticsSectionToggle("Visualize",visualizeExpanded,"statistics-visualize-toggle") {m.setStatisticsSectionExpanded("visualize",!visualizeExpanded)}
        if(visualizeExpanded) {
        if(dataKind!="list")Text(if(isKorean())"x 날짜 형식: YYYY-MM-DD, YYYY/MM/DD, YYYY.MM.DD" else "x date formats: YYYY-MM-DD, YYYY/MM/DD, YYYY.MM.DD",fontSize=11.sp,color=LocalInstrument.current.muted)
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Choices(if(dataKind=="xy")listOf("Scatter","Histogram","Box plot","Violin + points","Heat map") else listOf("Histogram","Box plot","Violin + points","Heat map"),plotType,{plotType=it})
                if(plotType in listOf("Box plot","Violin + points")) {
                    StatisticsSelectionTitle("Orientation")
                    Choices(listOf("Horizontal","Vertical"),if(plotOrientation=="vertical")"Vertical" else "Horizontal",{plotOrientation=if(it=="Vertical")"vertical" else "horizontal"})
                }
                if(plotType=="Heat map") {
                    StatisticsSelectionTitle("Heat map data")
                    Choices(listOf("Raw values","Z-score by row","Z-score by column","Correlation"),when(heatMapMode){"zrow"->"Z-score by row";"zcolumn"->"Z-score by column";"correlation"->"Correlation";else->"Raw values"},{heatMapMode=when(it){"Z-score by row"->"zrow";"Z-score by column"->"zcolumn";"Correlation"->"correlation";else->"raw"}})
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        Checkbox(heatMapClustering,{heatMapClustering=it},Modifier.size(38.dp))
                        Text(tr("Hierarchical clustering"),fontSize=12.sp,color=LocalInstrument.current.ink)
                    }
                    if(heatMapClustering) {
                        StatisticsSelectionTitle("Cluster linkage")
                        Choices(listOf("Single","Average","Complete","Ward"),when(heatMapLinkage){"single"->"Single";"complete"->"Complete";"ward"->"Ward";else->"Average"},{heatMapLinkage=when(it){"Single"->"single";"Complete"->"complete";"Ward"->"ward";else->"average"};if(heatMapLinkage=="ward")heatMapMetric="euclidean"})
                        StatisticsSelectionTitle("Distance metric")
                        Choices(listOf("Euclidean","Manhattan","Correlation (1 − r)"),when(heatMapMetric){"manhattan"->"Manhattan";"correlation"->"Correlation (1 − r)";else->"Euclidean"},{heatMapMetric=when(it){"Manhattan"->"manhattan";"Correlation (1 − r)"->"correlation";else->"euclidean"}},enabled=heatMapLinkage!="ward")
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        Checkbox(heatMapFit,{heatMapFit=it},Modifier.size(38.dp))
                        Text(tr("Fit to screen"),fontSize=12.sp,color=LocalInstrument.current.ink)
                    }
                    if(heatMapMode=="correlation") {
                        StatisticsSelectionTitle("Correlation method")
                        Choices(listOf("Pearson (p)","Spearman (s)","Kendall (k)"),when(heatMapCorrelation){"spearman"->"Spearman (s)";"kendall"->"Kendall (k)";else->"Pearson (p)"},{heatMapCorrelation=when(it){"Spearman (s)"->"spearman";"Kendall (k)"->"kendall";else->"pearson"}})
                        if(heatMapAxisIndices.isEmpty())Text(tr("No numeric columns"),fontSize=11.sp,color=LocalInstrument.current.muted)
                        HeatMapAxisPicker("X axis variables",heatMapAxisIndices.map {it to statisticsColumnLabels(data,dataKind)[it]},heatMapXSelection) {index->
                            val adding=index !in heatMapXSelection
                            val next=if(adding)heatMapXSelection+index else heatMapXSelection-index
                            heatMapXColumns=encodeHeatMapSelection(next)
                            heatMapYColumns=encodeHeatMapSelection(heatMapYSelection-index)
                        }
                        HeatMapAxisPicker("Y axis variables",heatMapAxisIndices.map {it to statisticsColumnLabels(data,dataKind)[it]},heatMapYSelection) {index->
                            val adding=index !in heatMapYSelection
                            val next=if(adding)heatMapYSelection+index else heatMapYSelection-index
                            heatMapYColumns=encodeHeatMapSelection(next)
                            heatMapXColumns=encodeHeatMapSelection(heatMapXSelection-index)
                        }
                    }
                }
                if(plotType!="Scatter"&&!(plotType=="Heat map"&&heatMapMode=="correlation")&&dataColumns.size>1) {
                    StatisticsSelectionTitle("Plot grouping")
                    val groupingIds=listOf("columns")+dataColumns.indices.map {"column:$it"}
                    val groupingLabels=listOf(tr("Columns"))+statisticsColumnLabels(data,dataKind)
                    val selectedGrouping=statisticsPlotGroupingColumn(plotGrouping,dataColumns.size).takeIf {it>=0}?.let {"column:$it"} ?: "columns"
                    Choices(groupingLabels,groupingLabels[groupingIds.indexOf(selectedGrouping)],{plotGrouping=groupingIds[groupingLabels.indexOf(it)]},translate=false)
                }
            }
            val fitVisible=dataKind=="xy"&&plotType=="Scatter"&&m.regressionData==data&&m.regressionFit.isNotBlank()
            val plotPairs=if(fitVisible&&m.regressionMode in listOf("logistic","polynomial","ridge","lasso","elasticnet","logisticridge","logisticlasso","logisticelasticnet","randomforest","randomforestclassifier","randomforestregressor","bayeslinear","bayeslogistic")&&fittedResponse==0)paired.map {(x,y)->y to x} else paired
            val heatMapInput=remember(parsedRows,dataKind,data,plotGrouping,plotType,heatMapMode,heatMapCorrelation,heatMapXColumns,heatMapYColumns) {
                if(plotType!="Heat map")null else {
                    if(heatMapMode=="correlation")statisticsCorrelationHeatMap(parsedRows,dataKind,heatMapCorrelation,heatMapXSelection.toList().sorted(),heatMapYSelection.toList().sorted(),heatMapColumnNames)
                    else statisticsHeatMapData(parsedRows,dataKind,plotGrouping,heatMapMode,heatMapColumnNames)
                }
            }
            val clusterRequest=heatMapInput?.takeIf {heatMapClustering}
            val clustered by produceState<Pair<StatisticsHeatMapData,StatisticsHeatMapData>?>(null,clusterRequest,heatMapLinkage,heatMapMetric) {
                value=null
                clusterRequest?.let {request->value=request to withContext(Dispatchers.Default){clusteredHeatMap(request,heatMapLinkage,heatMapMetric)}}
            }
            val heatMap=if(heatMapClustering)clustered?.takeIf {it.first==clusterRequest}?.second else heatMapInput
            if(plotType=="Heat map"&&heatMapClustering&&heatMap==null)Text(tr("Clustering…"),fontSize=12.sp,color=LocalInstrument.current.muted)
            heatMap?.let {StatisticsHeatMap(it,m.displayDigits,heatMapFit)}
            val plotPanels=if(plotType=="Heat map")emptyList() else if(plotType=="Scatter")listOf(StatisticsPlotPanel("",emptyList())) else statisticsPlotPanels(parsedRows,dataKind,plotGrouping,statisticsColumnLabels(data,dataKind))
            plotPanels.forEach {panel->
                if(panel.label.isNotBlank())Text(panel.label,style=MaterialTheme.typography.titleSmall)
                StatisticsPlot(plotType,if(plotType=="Scatter")plotPairs else xValues.mapIndexed {i,v->i.toDouble() to v},xValues,yValues,if(fitVisible)m.regressionCurve.orEmpty() else emptyList(),if(fitVisible&&!m.regressionMode.startsWith("randomforest"))m.regressionFit else "",m.displayDigits,fitVisible&&m.regressionMode=="linear",m.regressionCorrelation,tertiary=zValues,allColumns=panel.series,xDateOrigin=if(fitVisible&&fittedResponse==0)null else dateAxis?.origin,
                    xAxisLabel=if(fitVisible)fittedVariables["x"] ?: "x" else "x",yAxisLabel=if(fitVisible)fittedResponseName else "y",
                    fitPrefix=if(fitVisible&&(m.regressionMode.startsWith("logistic")||m.regressionMode=="bayeslogistic"))"P($fittedResponseName = 1) = " else if(fitVisible)"$fittedResponseName ≈ " else "y ≈ ",fitVariables=if(fitVisible&&m.regressionMode in listOf("logistic","polynomial","ridge","lasso","elasticnet","logisticridge","logisticlasso","logisticelasticnet","randomforest","randomforestclassifier","randomforestregressor","bayeslinear","bayeslogistic"))fittedVariables else emptyMap(),orientation=plotOrientation)
            }
            if(dateAxis!=null&&plotType=="Scatter")Text((if(isKorean())"회귀식의 x: ${dateAxis.origin.plusDays(1)} = 1일째" else "Regression x: ${dateAxis.origin.plusDays(1)} = day 1"),fontSize=11.sp,color=LocalInstrument.current.muted)
        }
        StatisticsSectionToggle("Regression & models",regressionExpanded,"statistics-regression-toggle") {m.setStatisticsSectionExpanded("regression",!regressionExpanded)}
        if(regressionExpanded) {
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                val activeRegression=if(m.regressionFit.isNotBlank()&&m.regressionData==data)when {m.regressionMode.startsWith("randomforest")->"randomforest";m.regressionMode.startsWith("logistic")->"logistic";m.regressionMode in listOf("ridge","lasso","elasticnet")->if(dataColumns.size>2)"multiple" else "linear";else->m.regressionMode} else ""
                if(dataColumns.size>1)Choices(if(dataKind=="xyz"||dataKind.startsWith("columns:"))listOf("multiple","logistic","randomforest","bayeslinear","bayeslogistic") else listOf("linear","quadratic","polynomial","logarithmic","exponential","power","logistic","randomforest","bayeslinear","bayeslogistic","custom"),if(regularized||regression in listOf("custom","polynomial","randomforest","bayeslinear","bayeslogistic"))regression else activeRegression,{selectedMode->
                    regression=selectedMode;plotType=if(dataKind=="xy")"Scatter" else "Histogram"
                    val selectedResponse=responseColumn
                    if(selectedMode in listOf("custom","polynomial","randomforest","bayeslinear","bayeslogistic")||regularization!="none")m.clearRegression()
                    if(selectedMode !in listOf("custom","polynomial","randomforest","bayeslinear","bayeslogistic")&&(selectedMode !in listOf("linear","multiple","logistic")||regularization=="none")) {
                        val table=statisticsRegressionTable(numericRows,dataKind,selectedMode,selectedResponse)
                        if(table!=null)m.fitRegression("regression($table,$selectedMode)",data,if(selectedMode in listOf("multiple","logistic"))selectedResponse else null)
                        else m.error="Add more data points than fit parameters"
                    }
                })
                if(dataColumns.size>1&&regression in listOf("linear","multiple","logistic")) {
                    StatisticsSelectionTitle("Regularization")
                    Choices(listOf("none","ridge","lasso","elasticnet"),regularization,{m.clearRegression();regularization=it})
                }
                if(dataColumns.size>1&&(regularized||regression=="randomforest")) {
                    if(regularized) {
                        Field(lassoAlpha,"Regularization α",Modifier.fillMaxWidth()){m.clearRegression();lassoAlpha=it}
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(lassoAlphaCv,{m.clearRegression();lassoAlphaCv=it},Modifier.size(38.dp))
                            Text(tr("Cross-validate α (5 folds)"),fontSize=12.sp,color=LocalInstrument.current.ink)
                        }
                        if(regularization=="elasticnet")Field(l1Ratio,"L1 ratio (0–1)",Modifier.fillMaxWidth()){m.clearRegression();l1Ratio=it}
                        Text(tr("Predictors standardized; coefficients in original units."),fontSize=11.sp,color=LocalInstrument.current.muted)
                    } else {
                        StatisticsSelectionTitle("Forest task")
                        val tasks=mapOf("Auto (0/1 → classification)" to "auto","Regression" to "regression","Binary classification" to "classification")
                        Choices(tasks.keys.toList(),tasks.entries.firstOrNull {it.value==forestTask}?.key ?: tasks.keys.first(),{m.clearRegression();forestTask=tasks[it] ?: "auto"})
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Field(forestTrees,"Trees (1–200)",Modifier.weight(1f)){m.clearRegression();forestTrees=it}
                            Field(forestDepth,"Max depth (1–20)",Modifier.weight(1f)){m.clearRegression();forestDepth=it}
                        }
                        Field(forestSeed,"Random seed",Modifier.fillMaxWidth()){m.clearRegression();forestSeed=it}
                    }
                }
                if(dataColumns.size>1&&bayesian) {
                    StatisticsSelectionTitle("Inference method")
                    val methods=mapOf((if(regression=="bayeslinear")"Conjugate (exact)" else "Laplace approximation") to "analytic","NUTS" to "nuts")
                    Choices(methods.keys.toList(),methods.entries.firstOrNull {it.value==bayesianMethod}?.key ?: methods.keys.first(),{m.clearRegression();bayesianMethod=methods[it] ?: "analytic"})
                    if(bayesianMethod=="nuts") {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Field(nutsSamples,"Samples per chain (100–5000)",Modifier.weight(1f)){m.clearRegression();nutsSamples=it}
                            Field(nutsWarmup,"Warmup (50–5000)",Modifier.weight(1f)){m.clearRegression();nutsWarmup=it}
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Field(nutsMaxDepth,"Max tree depth (1–10)",Modifier.weight(1f)){m.clearRegression();nutsMaxDepth=it}
                            Field(nutsChains,"Chains (2–4)",Modifier.weight(1f)){m.clearRegression();nutsChains=it}
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Field(nutsSeed,"Random seed",Modifier.weight(1f)){m.clearRegression();nutsSeed=it}
                            Spacer(Modifier.weight(1f))
                        }
                        Text(tr("NUTS adapts trajectory length; step size adapts during warmup. Check rank-normalized R-hat, bulk/tail ESS and divergences."),fontSize=11.sp,color=LocalInstrument.current.muted)
                    }

                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Field(bayesianPriorSD,"Prior SD",Modifier.weight(1f)){m.clearRegression();bayesianPriorSD=it}
                        Field(bayesianLevel,"Credible level (0–1)",Modifier.weight(1f)){m.clearRegression();bayesianLevel=it}
                    }
                    if(regression=="bayeslinear")Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Field(bayesianShape,"Variance prior shape",Modifier.weight(1f)){m.clearRegression();bayesianShape=it}
                        Field(bayesianScale,"Variance prior scale",Modifier.weight(1f)){m.clearRegression();bayesianScale=it}
                    }
                    Text(tr("Zero-mean priors include the intercept on standardized predictors; coefficients in original units."),fontSize=11.sp,color=LocalInstrument.current.muted)
                }
                if(dataKind!="list"&&regression in listOf("multiple","logistic","bayeslinear","bayeslogistic"))Text(tr(if(regression in listOf("logistic","bayeslogistic"))"Selected column is response; others are predictors. Logistic response: 0 or 1." else "Selected column is response; others are predictors."),fontSize=11.sp,color=LocalInstrument.current.muted)
                if(dataColumns.size>1&&(regularized||regression in listOf("multiple","logistic","polynomial","randomforest","bayeslinear","bayeslogistic"))) {
                    StatisticsSelectionTitle("Dependent variable")
                    val responseLabels=statisticsColumnLabels(data,dataKind)
                    Choices(responseLabels,responseLabels.getOrNull(responseColumn).orEmpty(),{name->m.clearRegression();logisticResponse=responseLabels.indexOf(name).toString()},translate=false)
                    if(regression=="logistic"&&regularization=="none") {
                        StatisticsSelectionTitle("Firth correction")
                        Choices(listOf("Auto","Always"),if(firthMode=="firth")"Always" else "Auto",{m.clearRegression();firthMode=if(it=="Always")"firth" else "auto"})
                    }
                    val table=statisticsRegressionTable(numericRows,dataKind,fitMode,responseColumn)
                    fun capacityOption(raw:String,minimum:Int,maximum:Int)=raw.toIntOrNull()?.let{it>=minimum&&(m.removeComputationLimit||it<=maximum)}==true
                    val validNuts=bayesianMethod!="nuts"||(capacityOption(nutsSamples,100,5000)&&capacityOption(nutsWarmup,50,5000)&&capacityOption(nutsMaxDepth,1,10)&&capacityOption(nutsChains,2,4)&&nutsSeed.toLongOrNull() in 0L..2147483647L)
                    val samplerOptions=if(bayesianMethod=="nuts")",[nuts,$nutsSamples,$nutsWarmup,$nutsMaxDepth,$nutsSeed,$nutsChains]" else ""
                    val validOptions=when {
                        bayesian->validNuts&&bayesianPriorSD.toDoubleOrNull()?.let {it.isFinite()&&it>0&&(m.removeComputationLimit||it in 0.000001..1000000.0)}==true&&bayesianLevel.toDoubleOrNull()?.let {it>0&&it<1}==true&&(regression!="bayeslinear"||(bayesianShape.toDoubleOrNull()?.let {it.isFinite()&&it>0}==true&&bayesianScale.toDoubleOrNull()?.let {it.isFinite()&&it>0}==true))
                        regularized->(lassoAlphaCv||lassoAlpha.toDoubleOrNull()?.let {it.isFinite()&&it>0}==true)&&(regularization!="elasticnet"||l1Ratio.toDoubleOrNull()?.let {it in 0.0..1.0}==true)
                        regression=="randomforest"->capacityOption(forestTrees,1,200)&&capacityOption(forestDepth,1,20)&&forestSeed.toLongOrNull() in 0L..2147483647L
                        else->true
                    }
                    if(regression!="polynomial")CalculationButton("Analyze",m.regressionBusy,m.regressionJob ?: m.inputVersion,
                        onCancel={m.cancelRegression()},onClick={table?.let {when {
                        bayesian->m.fitRegression("regression($it,$regression,[$bayesianPriorSD,$bayesianLevel${if(regression=="bayeslinear")",$bayesianShape,$bayesianScale" else ""}$samplerOptions])",data,responseColumn)
                        regularized->{val penalty=if(lassoAlphaCv)"cv" else lassoAlpha;m.fitRegression("regression($it,$fitMode,${if(regularization=="elasticnet")"[$penalty,$l1Ratio]" else penalty})",data,responseColumn)}
                        regression=="randomforest"->m.fitRegression("regression($it,$fitMode,[$forestTrees,$forestDepth,$forestSeed])",data,responseColumn)
                        else->m.fitRegression("regression($it,$regression${if(regression=="logistic"&&firthMode=="firth")",firth" else ""})",data,responseColumn)
                    }}},enabled=table!=null&&validOptions&&!m.busy)
                }
                if(dataKind=="xy"&&regression=="polynomial") {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Field(polynomialDegree,"Polynomial degree (1–10)",Modifier.weight(1f)){m.clearRegression();polynomialDegree=it}
                        CalculationButton("Analyze",m.regressionBusy,m.regressionJob ?: m.inputVersion,onCancel={m.cancelRegression()},onClick={
                            val table=statisticsRegressionTable(numericRows,dataKind,"polynomial",responseColumn)
                            if(table!=null)m.fitRegression("regression($table,polynomial,$polynomialDegree)",data,responseColumn)
                        },enabled=(polynomialDegree.toIntOrNull() ?: 0) in 1..10&&!m.busy)
                    }
                }
                if(dataColumns.size>1&&!regularized&&regression in listOf("linear","quadratic","logarithmic","exponential","power"))
                    CalculationButton("Analyze",m.regressionBusy,m.regressionJob ?: m.inputVersion,onCancel={m.cancelRegression()},onClick={
                        val table=statisticsRegressionTable(numericRows,dataKind,regression,responseColumn)
                        if(table!=null)m.fitRegression("regression($table,$regression)",data)
                    },enabled=statisticsRegressionTable(numericRows,dataKind,regression,responseColumn)!=null&&!m.busy)
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
                    CalculationButton("Fit custom model",m.regressionBusy,m.regressionJob ?: m.inputVersion,onCancel={m.cancelRegression()},onClick={
                        val table=numericRows.filter {it.size>=2&&it[0].isNotBlank()&&it[1].isNotBlank()}.joinToString(",","[","]"){it.take(2).joinToString(",","[","]")}
                        val guesses=customInitials.trim().takeIf(String::isNotEmpty)?.let {",$it"}.orEmpty()
                        m.fitRegression("regression($table,custom,$customFormula,$customVariable$guesses)",data)
                    },enabled=customFormula.isNotBlank()&&customVariable.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))&&paired.size>=2&&!m.busy)
                }
                AdvancedStatistics(m,data,dataKind,"models","Models")
            }
            if(dataKind!="list"&&m.regressionData==data&&m.regressionFit.isNotBlank()) {
                Column(verticalArrangement=Arrangement.spacedBy(0.dp)) {
                    if(!m.regressionMode.startsWith("randomforest")&&(dataKind=="xyz"||dataKind.startsWith("columns:"))) {
                        val equation=remember(m.regressionFit,m.displayDigits,fittedVariables) {regressionFormulaDisplayTree(m.regressionFit,m.displayDigits,fittedVariables)}
                        CompositionLocalProvider(LocalMathMinimumSize provides 8f) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=3.dp).testTag("statistics-regression-equation"),
                                horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                MathText(if((m.regressionMode.startsWith("logistic")||m.regressionMode=="bayeslogistic"))"P($fittedResponseName = 1) = " else "$fittedResponseName = ",12f,Modifier.alignBy(MathAxis))
                                Box(Modifier.alignBy(MathAxis)) {
                                    if(equation!=null)MathNode(equation,12f)
                                    else Text(m.regressionFit,fontSize=12.sp,fontFamily=FontFamily.Monospace)
                                }
                            }
                        }
                    }
                    m.regressionReport?.let {report->
                        val language=LocalLanguage.current
                        val result=remember(report,m.result,m.history){regressionResultForCopy(m.result,m.history,report)}
                        RegressionInference(report,m.displayDigits,parameterLabels,onClear={
                            if(result!=null)m.clearStatisticsResult(result) else m.clearRegression()
                        },clearEnabled=!m.busy&&!m.regressionBusy,onCopy=result?.let {snapshot->{
                            val prefix=if(m.regressionMode.startsWith("logistic")||m.regressionMode=="bayeslogistic")"P($fittedResponseName = 1) = " else "$fittedResponseName = "
                            clipboard.setText(AnnotatedString(statisticsResultCopyText(m,snapshot,language,fittedVariables,prefix)))
                        }})
                    }
                    if(m.regressionParameters.isNotEmpty()) {
                        Text(tr("Fitted parameters"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            m.regressionParameters.sortedWith(compareBy({listOf("f","ADC","D","Dstar").indexOf(it.first).let {index->if(index<0)Int.MAX_VALUE else index}},{it.first})).forEach {(name,value)->
                                val label=parameterLabels[name]?.let {if(it=="Intercept")tr(it) else it} ?: if(name=="Dstar")"D*" else name
                                val displayedValue=ResultDisplayFormat.formatText(value,m.resultDisplayMode,m.thousandsSeparator,maxFractionDigits=m.displayDigits)
                                val korean=isKorean()
                                TextButton(onClick={
                                    clipboard.setText(AnnotatedString(displayedValue))
                                    android.widget.Toast.makeText(context,if(korean)"$label 값 복사됨" else "$label copied",android.widget.Toast.LENGTH_SHORT).show()
                                },contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp),
                                    modifier=Modifier.semantics {contentDescription=if(korean)"$label 값 복사" else "Copy $label value"}) {
                                    Text("$label = $displayedValue  ⧉",fontSize=11.sp,fontFamily=FontFamily.Monospace)
                                }
                            }
                        }
                    }
                    if(dataKind=="xy"&&!m.regressionMode.startsWith("randomforest"))SmallAction("Graph fitted expression"){
                        val fit=if(m.regressionMode=="custom")m.regressionFit.replace(Regex("(?<![A-Za-z0-9_])${Regex.escape(customVariable)}(?![A-Za-z0-9_])"),"x") else m.regressionFit
                        val graphSource=regressionFormulaGraphSource(fit,m.displayDigits)
                        if(graphSource==null)m.error="Could not format fitted expression"
                        else {m.changeGraphKind("cartesian");m.updateGraphSource(graphSource);m.mode="Graph";m.plot()}
                    }
                }
            }
        }
        StatisticsAnalysis(m,numericRows,if(dataColumns.size==1)"list" else dataKind,data,parsedRows)
        AdvancedStatistics(m,data,dataKind)
        Display(m,requestInitialFocus=false,showInput=false)
        }
    }
    FilledTonalButton(onClick={m.collapseStatisticsSections();collapseRequest++},
        modifier=Modifier.align(Alignment.TopEnd).padding(top=8.dp,end=8.dp).testTag("statistics-collapse-all"),
        elevation=ButtonDefaults.filledTonalButtonElevation(defaultElevation=4.dp)) {Text(tr("Collapse all"),fontSize=12.sp)}
    }
}

@Composable private fun StatisticsCsvImportDialog(preview:StatisticsCsvImport,onDismiss:()->Unit,onImport:(List<Int>,Boolean)->Unit) {
    val maxColumns=minOf(100,preview.columnCount)
    var skipHeader by remember(preview) {mutableStateOf(preview.hasHeader)}
    var columnCount by remember(preview) {mutableIntStateOf(minOf(2,maxColumns))}
    var columnText by remember(preview) {mutableStateOf(minOf(2,maxColumns).toString())}
    var columns by remember(preview) {mutableStateOf((0 until maxColumns).toList())}
    val names=List(maxColumns){listOf("x","y","z").getOrNull(it) ?: "x${it+1}"}
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr("Import CSV/XLSX"))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(skipHeader,{skipHeader=it})
                Text(tr("First row is a header"))
            }
            Text(if(preview.hasHeader)tr("Header detected automatically") else tr("No header detected"),style=MaterialTheme.typography.bodySmall)
            Text(tr("Import as"),style=MaterialTheme.typography.titleSmall)
            Field(columnText,"Column count (1–100)",Modifier.fillMaxWidth()){text->
                val digits=text.filter(Char::isDigit).take(3)
                val count=digits.toIntOrNull()?.coerceIn(1,maxColumns)
                columnText=count?.toString() ?: digits
                if(count!=null)columnCount=count
            }
            Text(tr("Choose a column for each variable"),style=MaterialTheme.typography.bodySmall)
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

@Composable private fun StatisticsXlsxSheetDialog(sheets:List<StatisticsXlsxSheet>,onDismiss:()->Unit,onSelect:(StatisticsCsvImport)->Unit) {
    var selected by remember(sheets) {mutableIntStateOf(0)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr("Select sheet"))},text={
        Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            sheets.forEachIndexed {index,sheet->
                Row(Modifier.fillMaxWidth().clickable {selected=index},verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected==index,{selected=index})
                    Column {
                        Text(sheet.name,style=MaterialTheme.typography.bodyMedium)
                        Text(if(isKorean())"${sheet.preview.rows.size}행 · ${sheet.preview.columnCount}열" else "${sheet.preview.rows.size} rows · ${sheet.preview.columnCount} columns",style=MaterialTheme.typography.bodySmall,color=LocalInstrument.current.muted)
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick={onSelect(sheets[selected].preview)}){Text(tr("Continue"))}},dismissButton={TextButton(onClick=onDismiss){Text(tr("Cancel"))}})
}

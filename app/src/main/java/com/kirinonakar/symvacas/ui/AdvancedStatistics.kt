package com.kirinonakar.symvacas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import org.json.JSONArray
import org.json.JSONObject

internal fun advancedStatisticsCommand(definition:JSONObject,rows:List<List<String>>):String {
    val id=definition.getString("id")
    val shape=definition.getString("input")
    if(shape=="none")return definition.getString("example")
    require(rows.any {row->row.any(String::isNotBlank)}) {"Enter data first"}
    val cells=rows.map {row->row.map(String::trim)}
    fun vector(values:List<String>)=values.joinToString(",","[","]")
    fun table(values:List<List<String>>)=values.joinToString(",","[","]",transform=::vector)
    val data=when(shape) {
        "list"->vector(cells.map {it.firstOrNull().orEmpty()}.filter(String::isNotBlank))
        "groups"->{
            require(cells.first().size>=2) {"Enter at least two columns"}
            if(id in listOf("cohend","kstest"))require(cells.first().size==2) {"Select exactly two columns"}
            cells.first().indices.map {i->vector(cells.mapNotNull {it.getOrNull(i)?.takeIf(String::isNotBlank)})}.joinToString(",")
        }
        else->{
            require(cells.all {it.size==cells.first().size}) {"Rows must have equal column counts"}
            val complete=if(id=="impute")cells.map {row->row.map {it.ifBlank {"NA"}}} else cells.also {require(it.all {row->row.all(String::isNotBlank)}) {"Complete rows required; impute missing values first"}}
            if(shape=="survivalgroups") {
                require(complete.first().size==3) {"Columns: time, event, group"}
                val groups=complete.map {it[2]}.distinct()
                require(groups.size==2) {"Log-rank requires exactly two groups"}
                groups.joinToString(",") {group->table(complete.filter {it[2]==group}.map {it.take(2)})}
            } else table(complete)
        }
    }
    return "$id($data${definition.getString("suffix")})"
}

/** Only the columns selected on the statistics screen are analyzed; extra pasted cells are ignored. */
internal fun advancedStatisticsRows(data:String,columnLimit:Int?=null,removeComputationLimit:Boolean=false):List<List<String>> {
    val normalized=data.removePrefix("\uFEFF").replace("\r\n","\n").replace('\r','\n').trimEnd('\n')
    val rows=normalized.split('\n').map {line->line.splitCsvRecord().map(String::trim)}
    val width=rows.maxOfOrNull {it.size} ?: 0
    val columns=if(columnLimit==null)width else minOf(width,columnLimit)
    require(removeComputationLimit||(columns<=20&&rows.size<=5000)) {"Limit: 5000 rows and 20 columns"}
    val rectangular=rows.map {row->List(columns){row.getOrElse(it){""}}}
    return if(statisticsHasHeader(rectangular)&&rectangular.first().none {it=="NA"})rectangular.drop(1) else rectangular
}

@Composable internal fun AdvancedStatistics(m:CalculatorModel,data:String,kind:String) {
    val context=LocalContext.current
    val schema=remember {context.assets.open("advanced_statistics.json").bufferedReader().use {JSONArray(it.readText())}}
    val definitions=remember {List(schema.length()){schema.getJSONObject(it)}}
    val ko=isKorean()
    var expanded by rememberSaveable {mutableStateOf(true)}
    var selected by rememberSaveable {mutableStateOf(m.advancedStatisticsDraft.optString("kind","padjust"))}
    val definition=definitions.firstOrNull {it.getString("id")==selected} ?: definitions.first()
    var source by rememberSaveable {mutableStateOf(m.advancedStatisticsDraft.optString("source",definition.getString("example")))}
    var input by rememberSaveable {mutableStateOf(m.advancedStatisticsDraft.optString("input",if(definition.has("controls")&&source==definition.getString("example"))if(data.isBlank())"example" else "current" else "expression"))}
    var formsText by rememberSaveable {mutableStateOf(m.advancedStatisticsDraft.optJSONObject("forms")?.toString() ?: "{}")}
    var message by remember {mutableStateOf("")}
    var menuOpen by remember {mutableStateOf(false)}
    var band by rememberSaveable {mutableStateOf(m.advancedStatisticsDraft.optBoolean("band",true))}
    var survivalReport by remember {mutableStateOf<JSONObject?>(null)}
    var survivalCopyResult by remember {mutableStateOf<JSONObject?>(null)}
    val clipboard=LocalClipboardManager.current
    val language=LocalLanguage.current
    var reportPlan by remember {mutableStateOf<SurvivalPlan?>(null)}
    var previousResult by remember {mutableStateOf<JSONObject?>(null)}
    var pending by remember {mutableStateOf(false)}
    val forms=JSONObject(formsText)
    val settings=forms.optJSONObject(selected) ?: JSONObject()
    val columnLimit=statisticsColumnCount(kind)
    val currentRows=runCatching {advancedStatisticsRows(data,columnLimit,m.removeComputationLimit)}
    val rows=if(input=="example"&&definition.has("exampleRows"))definition.getJSONArray("exampleRows").let {array->List(array.length()){i->array.getJSONArray(i).let {row->List(row.length()){row.getString(it)}}}} else currentRows.getOrDefault(emptyList())
    val count=rows.maxOfOrNull {it.size} ?: statisticsColumnCount(kind)
    val labels=if(input=="example")statisticsColumnNames(statisticsKindForColumns(count)) else statisticsColumnLabels(data,statisticsKindForColumns(count))
    val columns=List(count){i->labels.getOrNull(i) ?: "x${i+1}"}
    val command=runCatching {if(input=="expression"||!definition.has("controls"))source else {
        if(input=="current")currentRows.getOrThrow()
        guidedStatisticsCommand(definition,rows,settings,columns,m.removeComputationLimit)
    }}
    fun setOption(key:String,value:String) {
        val next=JSONObject(settings.toString()).put(key,value)
        if(selected=="glm"&&key=="family")next.put("link","auto")
        if(key in listOf("time","event","subject","response","group","grouping","offset","adjustment")||(selected=="glmm"&&key=="family"))next.put("predictors",if(selected=="survivalanalysis")"" else "auto")
        formsText=JSONObject(formsText).put(selected,next).toString();message=""
    }
    LaunchedEffect(selected,source,input,formsText,band) {m.updateAdvancedStatisticsDraft(JSONObject().put("kind",selected).put("source",source).put("input",input).put("forms",JSONObject(formsText)).put("band",band))}
    LaunchedEffect(selected,source,input,formsText,data) {survivalReport=null;survivalCopyResult=null;pending=false}
    LaunchedEffect(m.result,m.busy) {
        if(pending&&!m.busy&&m.result!=null&&m.result!==previousResult){survivalReport=m.result?.optJSONObject("survival");survivalCopyResult=m.result?.takeIf {survivalReport!=null};pending=false}
    }
    HorizontalDivider()
    StatisticsSectionToggle("Advanced analysis",expanded,"statistics-advanced-toggle") {expanded=!expanded}
    if(expanded) {
        fun choose(next:JSONObject) {selected=next.getString("id");source=next.getString("example");input=if(next.has("controls"))if(data.isBlank())"example" else "current" else "expression";message="";menuOpen=false;survivalReport=null;pending=false}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Box {
                OutlinedButton(onClick={menuOpen=true}){Text(definition.getString(if(ko)"ko" else "label"))}
                DropdownMenu(expanded=menuOpen,onDismissRequest={menuOpen=false}) {definitions.forEach {item->DropdownMenuItem(text={Text(item.getString(if(ko)"ko" else "label"))},onClick={choose(item)})}}
            }
            if(selected!="survivalanalysis")SmallAction(if(ko)"생존분석" else "Survival analysis"){choose(definitions.first {it.getString("id")=="survivalanalysis"})}
        }
        Text(definition.getString(if(definition.has("controls")&&input!="expression")if(ko)"formHelpKo" else "formHelp" else if(ko)"helpKo" else "help"),fontSize=11.sp,color=LocalInstrument.current.muted)
        if(definition.has("controls")) {
            val ids=listOf("current","example","expression")
            val inputLabels=if(ko)listOf("현재 데이터","예제","분석 식") else listOf("Current data","Example","Expression")
            Choices(inputLabels,inputLabels[ids.indexOf(input).coerceAtLeast(0)],{label->
                val next=ids[inputLabels.indexOf(label)];if(next=="expression")command.getOrNull()?.let {source=it};input=next;message=""
            },translate=false)
            if(input!="expression") {
                StatisticsFormFields(definition,settings,columns,::setOption)
                Text(if(ko)"${rows.size}행 · ${columns.joinToString(", ")}" else "${rows.size} rows · ${columns.joinToString(", ")}",fontSize=11.sp,color=LocalInstrument.current.muted)
                if(input=="example")Text(rows.joinToString("\n"){it.joinToString(", ")},fontSize=11.sp,color=LocalInstrument.current.muted)
                command.exceptionOrNull()?.message?.let {Text(tr(it),fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
            }
        }
        if(input=="expression"||!definition.has("controls"))Field(source,if(ko)"분석 식" else "Analysis expression",Modifier.fillMaxWidth().testTag("statistics-advanced-source")){source=it;message=""}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(!definition.has("controls"))SmallAction(if(ko)"예제" else "Example"){source=definition.getString("example");message=""}
            if(definition.getString("input")!="none")SmallAction(if(ko)"현재 데이터" else "Use current data"){
                if(definition.has("controls")){input="current";message=""}
                else runCatching {advancedStatisticsCommand(definition,advancedStatisticsRows(data,columnLimit,m.removeComputationLimit))}.onSuccess {source=it;input="current";message=""}.onFailure {message=it.message.orEmpty()}
            }
            CalculationButton("Analyze",m.busy&&m.calculationAction=="statistics-advanced",m.inputVersion,
                onCancel={pending=false;m.cancel()},onClick={command.getOrNull()?.let {
                survivalReport=null;previousResult=m.result;pending=selected=="survivalanalysis"
                reportPlan=if(pending&&input!="expression")survivalAnalysisPlan(rows,settings,columns) else null
                val usesCurrentData=input=="current"&&(definition.has("controls")||it==runCatching {advancedStatisticsCommand(definition,rows)}.getOrNull())
                val termLabels=if(usesCurrentData&&statisticsHasHeader(statisticsCsvRows(data))&&statisticsCsvRows(data).first().none {cell->cell=="NA"}||selected=="bayesbootstrap"&&input!="expression")advancedStatisticsTermLabels(definition,rows,settings,columns) else emptyMap()
                m.calculationAction="statistics-advanced";m.edit(Editor(it));m.calculate(statisticsTermLabels=termLabels)
            }},enabled=command.isSuccess&&command.getOrDefault("").isNotBlank()&&!m.busy&&!m.regressionBusy,modifier=Modifier.testTag("statistics-advanced-run"))
            SmallAction(if(ko)"계산기로" else "Insert expression"){command.getOrNull()?.let {m.edit(Editor(it));m.mode="Scientific/CAS"}}
        }
        if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
        statisticsReportFor(m.result,m.resultSource.ifBlank {m.editor.source},setOf(selected))?.let {StatisticsResultReport(m,it)}
        if(selected=="survivalanalysis") {
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Checkbox(band,{band=it},modifier=Modifier.testTag("statistics-survival-band"));Text(if(ko)"95% 신뢰구간 밴드" else "95% CI band",fontSize=12.sp)}
            survivalReport?.let {report->SurvivalReport(report,reportPlan,band,onCopy=survivalCopyResult?.let {snapshot->{
                clipboard.setText(AnnotatedString(statisticsResultCopyText(m,snapshot,language)))
            }})}
        }
    }
}

@Composable private fun StatisticsFormFields(definition:JSONObject,settings:JSONObject,columns:List<String>,onChange:(String,String)->Unit) {
    val ko=isKorean();val fields=definition.getJSONArray("controls")
    fun option(key:String):String {val field=(0 until fields.length()).map {fields.getJSONObject(it)}.first {it.getString("key")==key};return settings.optString(key,field.get("default").toString())}
    val visibleFields=(0 until fields.length()).map {fields.getJSONObject(it)}.filter {field->
        val conditions=field.optJSONObject("when")
        conditions==null||conditions.keys().asSequence().all {name->val values=conditions.getJSONArray(name);(0 until values.length()).any {values.getString(it)==option(name)}}
    }
    val bayesian=definition.getString("id") in listOf("bayesproportion","bayesmean","bayesrate","bayescompare")
    var index=0
    while(index<visibleFields.size) {
        val field=visibleFields[index++];val key=field.getString("key")
        if(bayesian&&field.getString("type")=="number") {
            val pair=mutableListOf(field)
            if(index<visibleFields.size&&visibleFields[index].getString("type")=="number")pair.add(visibleFields[index++])
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                pair.forEach {item->
                    val itemKey=item.getString("key")
                    Field(option(itemKey),item.getString(if(ko)"ko" else "label"),Modifier.weight(1f).testTag("statistics-form-$itemKey")){onChange(itemKey,it)}
                }
                if(pair.size==1)Spacer(Modifier.weight(1f))
            }
            continue
        }
        val label=field.getString(if(ko)"ko" else "label");val value=option(key)
        when(field.getString("type")) {
            "number"->Field(value,label,Modifier.fillMaxWidth().testTag("statistics-form-$key")){onChange(key,it)}
            "choice"->{
                val rawChoices=field.getJSONArray("choices")
                val choices=List(rawChoices.length()){rawChoices.getJSONObject(it)}.filter {choice->val conditions=choice.optJSONObject("when");conditions==null||conditions.keys().asSequence().all {name->val values=conditions.getJSONArray(name);(0 until values.length()).any {values.getString(it)==option(name)}}}
                val ids=choices.map {it.getString("id")};val names=choices.map {it.getString(if(ko)"ko" else "label")}
                StatisticsSelectionTitle(label,translate=false)
                Choices(names,names.getOrElse(ids.indexOf(value)){""},{name->onChange(key,ids[names.indexOf(name)])},translate=false)
            }
            "column"->{
                val selected=value.toIntOrNull()?.let {if(it==-1)columns.lastIndex else it}
                StatisticsSelectionTitle(label,translate=false)
                Choices(columns,columns.getOrNull(selected ?: -1).orEmpty(),{name->onChange(key,columns.indexOf(name).toString())},translate=false)
            }
            "columns"->{
                val id=definition.getString("id")
                val roles=when(id) {
                    "cox"->listOf("time","event")
                    "survivalanalysis"->listOf("time","event")+if(option("grouping")=="groups")listOf("group") else emptyList()
                    "poissonreg","nbreg","glm"->listOf("response")
                    "ancova"->listOf("group","response")
                    else->listOf("subject","response")
                }
                val offsetRoles=if(id in listOf("poissonreg","nbreg","glmm","glm")&&option("adjustment")!="none"&&(id!="glmm"||option("family")!="binomial"))listOf("offset") else emptyList()
                val excluded=if(key=="predictors")roles+offsetRoles else emptyList()
                val reserved=excluded.mapNotNull {option(it).toIntOrNull()?.let {value->if(value==-1)columns.lastIndex else value}}
                val selected=if(value=="auto")columns.indices.filter {it !in reserved} else value.split(',').mapNotNull(String::toIntOrNull)
                StatisticsSelectionTitle(label,translate=false)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    columns.forEachIndexed {i,name->
                        if(i !in reserved)Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(i in selected,{checked->onChange(key,(if(checked)selected+i else selected-i).sorted().joinToString(","))},modifier=Modifier.testTag("statistics-form-$key-$i"))
                            Text(name,fontSize=12.sp)
                        }
                    }
                }
            }
        }
    }
}

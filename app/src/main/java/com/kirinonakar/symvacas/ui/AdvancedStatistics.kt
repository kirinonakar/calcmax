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
import androidx.compose.ui.text.font.FontWeight
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

internal fun advancedStatisticsExampleRows(definition:JSONObject,settings:JSONObject=JSONObject()):List<List<String>> {
    val measurement=definition.getString("id") in listOf("cfa","sem")
    val ordinal=measurement&&settings.optString("estimator")=="wlsmv"
    val multi=measurement&&settings.optString("groupMode")=="multi"
    val array=definition.getJSONArray("exampleRows")
    var rows=List(array.length()){i->array.getJSONArray(i).let {row->List(row.length()){row.getString(it)}}}
    if(ordinal) {
        val thresholds=definition.getJSONArray("ordinalExampleCuts")
        val cuts=List(thresholds.length()){thresholds.getDouble(it)}
        rows=rows.map {row->row.map {value->(1+cuts.count {value.toDouble()>it}).toString()}}
    }
    if(multi) {
        val groups=definition.getJSONArray("multiGroupExampleIds")
        rows=List(groups.length()){groups.getString(it)}.flatMap {group->rows.map {row->listOf(group)+row}}
    }
    return rows
}

internal fun advancedStatisticsFormSettings(analysis:String,input:String,formsText:String,exampleFormsText:String):JSONObject {
    val example=input=="example"&&analysis in listOf("cfa","sem")
    return JSONObject(if(example)exampleFormsText else formsText).optJSONObject(analysis) ?: JSONObject()
}

@Composable internal fun AdvancedStatistics(m:CalculatorModel,data:String,kind:String,section:String="advanced",title:String="Advanced analysis",onDataApplied:((String)->Unit)?=null,embedded:Boolean=false,fixedAnalysis:String?=null) {
    val context=LocalContext.current
    val schema=remember {context.assets.open("advanced_statistics.json").bufferedReader().use {JSONArray(it.readText())}}
    val definitions=remember(section) {List(schema.length()){schema.getJSONObject(it)}.filter {it.getString("section")==section}}
    val legacy=m.advancedStatisticsDraft.takeIf {draft->definitions.any {it.getString("id")==draft.optString("kind")}}
    val panels=m.advancedStatisticsDraft.optJSONObject("panels")
    val moved=panels?.optJSONObject("tests")?.takeIf {section=="general"&&it.optString("kind")=="mcnemar"}
    val savedDraft=panels?.optJSONObject(section) ?: moved ?: legacy ?: JSONObject()
    val draft=if(fixedAnalysis!=null&&savedDraft.optString("kind")!=fixedAnalysis)JSONObject().put("forms",savedDraft.optJSONObject("forms") ?: JSONObject()).put("exampleForms",savedDraft.optJSONObject("exampleForms") ?: JSONObject())
        else if(savedDraft.optString("kind").isBlank()||definitions.any {it.getString("id")==savedDraft.optString("kind")})savedDraft
        else JSONObject().put("forms",savedDraft.optJSONObject("forms") ?: JSONObject()).put("exampleForms",savedDraft.optJSONObject("exampleForms") ?: JSONObject())
    val ko=isKorean()
    val collapseRequest=LocalStatisticsCollapseRequest.current
    val nested=section!="advanced"&&!embedded
    val expanded=m.statisticsSectionExpanded(section)
    var selected by rememberSaveable {mutableStateOf(fixedAnalysis ?: draft.optString("kind",definitions.first().getString("id")))}
    val definition=definitions.firstOrNull {it.getString("id")==selected} ?: definitions.first()
    var source by rememberSaveable {mutableStateOf(draft.optString("source",definition.getString("example")))}
    var input by rememberSaveable {mutableStateOf(draft.optString("input",if(definition.has("controls")&&source==definition.getString("example"))if(data.isBlank()||definition.getString("input")=="none")"example" else "current" else "expression"))}
    var formsText by rememberSaveable {mutableStateOf(draft.optJSONObject("forms")?.toString() ?: "{}")}
    var exampleFormsText by rememberSaveable {mutableStateOf(draft.optJSONObject("exampleForms")?.toString() ?: "{}")}
    var workflowSource by rememberSaveable {mutableStateOf(draft.optString("workflowSource"))}
    var workflowLabels by rememberSaveable {mutableStateOf(draft.optJSONObject("workflowLabels")?.toString() ?: "{}")}
    var message by remember {mutableStateOf("")}
    var menuOpen by remember {mutableStateOf(false)}
    var band by rememberSaveable {mutableStateOf(draft.optBoolean("band",true))}
    var exampleExpanded by rememberSaveable {mutableStateOf(false)}
    var expressionExpanded by rememberSaveable {mutableStateOf(false)}
    var survivalReport by remember {mutableStateOf<JSONObject?>(null)}
    var survivalCopyResult by remember {mutableStateOf<JSONObject?>(null)}
    val clipboard=LocalClipboardManager.current
    val language=LocalLanguage.current
    var reportPlan by remember {mutableStateOf<SurvivalPlan?>(null)}
    var previousResult by remember {mutableStateOf<JSONObject?>(null)}
    var pending by remember {mutableStateOf(false)}
    var imputationData by remember {mutableStateOf<String?>(null)}
    var imputationExpression by remember {mutableStateOf("")}
    val exampleSettings=input=="example"&&selected in listOf("cfa","sem")
    val settings=advancedStatisticsFormSettings(selected,input,formsText,exampleFormsText)
    val columnLimit=statisticsColumnCount(kind)
    val currentRows=runCatching {advancedStatisticsRows(data,columnLimit,m.removeComputationLimit)}
    val rows=if(input=="example"&&definition.has("exampleRows"))advancedStatisticsExampleRows(definition,settings) else currentRows.getOrDefault(emptyList())
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
        if(exampleSettings&&key in listOf("groupMode","group"))next.put("columns","auto")
        if(exampleSettings)exampleFormsText=JSONObject(exampleFormsText).put(selected,next).toString()
        else formsText=JSONObject(formsText).put(selected,next).toString()
        message=""
    }
    LaunchedEffect(selected,source,input,formsText,exampleFormsText,band,workflowSource,workflowLabels) {
        val next=JSONObject(m.advancedStatisticsDraft.toString())
        val panels=next.optJSONObject("panels") ?: JSONObject()
        panels.put(section,JSONObject().put("kind",selected).put("source",source).put("input",input).put("forms",JSONObject(formsText)).put("exampleForms",JSONObject(exampleFormsText)).put("band",band).put("workflowSource",workflowSource).put("workflowLabels",JSONObject(workflowLabels)))
        m.updateAdvancedStatisticsDraft(next.put("panels",panels))
    }
    LaunchedEffect(selected,source,input,formsText,exampleFormsText,data) {survivalReport=null;survivalCopyResult=null;pending=false}
    LaunchedEffect(m.clearedStatisticsResult) {
        if(survivalCopyResult!=null&&m.clearedStatisticsResult===survivalCopyResult){survivalReport=null;survivalCopyResult=null;reportPlan=null;pending=false}
    }
    LaunchedEffect(m.result,m.busy) {
        if(pending&&!m.busy&&m.result!=null&&m.result!==previousResult){survivalReport=m.result?.optJSONObject("survival");survivalCopyResult=m.result?.takeIf {survivalReport!=null};pending=false}
    }
    LaunchedEffect(collapseRequest){if(collapseRequest>0){menuOpen=false;exampleExpanded=false;expressionExpanded=false}}
    Column(Modifier.fillMaxWidth().padding(start=if(nested)14.dp else 0.dp)) {
    if(!embedded) {
        HorizontalDivider()
        StatisticsSectionToggle(title,expanded,"statistics-$section-toggle",depth=if(nested)1 else 0) {m.setStatisticsSectionExpanded(section,!expanded)}
    }
    if(expanded||embedded) {
        fun choose(next:JSONObject) {selected=next.getString("id");source=next.getString("example");input=if(next.has("controls"))if(data.isBlank()||next.getString("input")=="none")"example" else "current" else "expression";message="";menuOpen=false;survivalReport=null;pending=false}
        if(!embedded)Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Box {
                OutlinedButton(onClick={menuOpen=true}){Text(definition.getString(if(ko)"ko" else "label"))}
                DropdownMenu(expanded=menuOpen,onDismissRequest={menuOpen=false}) {
                    definitions.groupBy {it.getString(if(ko)"groupKo" else "group")}.forEach {(group,items)->
                        Text(group,Modifier.padding(horizontal=12.dp,vertical=8.dp),fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=LocalInstrument.current.ink)
                        items.forEach {item->DropdownMenuItem(contentPadding=PaddingValues(start=24.dp,end=12.dp),text={Text(item.getString(if(ko)"ko" else "label"),fontSize=13.sp,fontWeight=FontWeight.Normal,color=LocalInstrument.current.ink)},onClick={choose(item)})}
                    }
                }
            }
            if(section=="advanced"&&selected!="survivalanalysis")SmallAction(if(ko)"생존분석" else "Survival analysis"){choose(definitions.first {it.getString("id")=="survivalanalysis"})}
        }
        StatisticsExplanation("Model details",definition.getString(if(definition.has("controls")&&input!="expression")if(ko)"formHelpKo" else "formHelp" else if(ko)"helpKo" else "help"),"statistics-$section-model-help",selected)
        if(definition.has("controls")) {
            val ids=if(definition.getString("input")=="none")listOf("example","expression") else listOf("current","example","expression")
            val inputLabels=ids.map {when(it){"current"->if(ko)"현재 데이터" else "Current data";"example"->if(ko)"예제·직접 설정" else "Example / parameters";else->if(ko)"분석 식" else "Expression"}}
            Choices(inputLabels,inputLabels[ids.indexOf(input).coerceAtLeast(0)],{label->
                val next=ids[inputLabels.indexOf(label)];if(next=="expression")command.getOrNull()?.let {source=it};input=next;message=""
            },translate=false)
            if(input!="expression") {
                StatisticsFormFields(definition,settings,columns,rows,::setOption)
                if(definition.getString("input")!="none")Text(if(ko)"${rows.size}행 · ${columns.joinToString(", ")}" else "${rows.size} rows · ${columns.joinToString(", ")}",fontSize=11.sp,color=LocalInstrument.current.muted)
                if(input=="example"&&rows.isNotEmpty()) {
                    SmallAction(if(exampleExpanded)"Hide example data" else "Show example data"){exampleExpanded=!exampleExpanded}
                    if(exampleExpanded)Text(rows.joinToString("\n"){it.joinToString(", ")},fontSize=11.sp,color=LocalInstrument.current.muted)
                }
                SmallAction(if(expressionExpanded)"Hide analysis expression" else "Show analysis expression"){expressionExpanded=!expressionExpanded}
                if(expressionExpanded)Text(command.getOrDefault(""),fontSize=11.sp,color=LocalInstrument.current.muted)
                command.exceptionOrNull()?.message?.let {Text(tr(it),fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
            }
        }
        if(input=="expression"||!definition.has("controls"))Field(source,if(ko)"분석 식" else "Analysis expression",Modifier.fillMaxWidth().testTag("statistics-$section-source")){source=it;message=""}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(!definition.has("controls"))SmallAction(if(ko)"예제" else "Example"){source=definition.getString("example");message=""}
            if(definition.getString("input")!="none")SmallAction(if(ko)"현재 데이터" else "Use current data"){
                if(definition.has("controls")){input="current";message=""}
                else runCatching {advancedStatisticsCommand(definition,advancedStatisticsRows(data,columnLimit,m.removeComputationLimit))}.onSuccess {source=it;input="current";message=""}.onFailure {message=it.message.orEmpty()}
            }
            CalculationButton("Analyze",m.busy&&m.calculationAction=="statistics-$section",m.inputVersion,
                onCancel={pending=false;m.cancel()},onClick={command.getOrNull()?.let {
                if(selected=="impute"&&input=="current"){imputationData=data;imputationExpression=it}
                survivalReport=null;previousResult=m.result;pending=selected=="survivalanalysis"
                reportPlan=if(pending&&input!="expression")survivalAnalysisPlan(rows,settings,columns) else null
                val usesCurrentData=input=="current"&&(definition.has("controls")||it==runCatching {advancedStatisticsCommand(definition,rows)}.getOrNull())
                val termLabels=if(usesCurrentData||input!="expression")advancedStatisticsTermLabels(definition,rows,settings,columns) else if(it==workflowSource)JSONObject(workflowLabels).let {labels->labels.keys().asSequence().associateWith {key->labels.getString(key)}} else emptyMap()
                m.calculationAction="statistics-$section";m.edit(Editor(it));m.calculate(statisticsTermLabels=termLabels)
            }},enabled=command.isSuccess&&command.getOrDefault("").isNotBlank()&&!m.busy&&!m.regressionBusy,modifier=Modifier.testTag("statistics-$section-run"))
            SmallAction(if(ko)"계산기로" else "Insert expression"){command.getOrNull()?.let {m.edit(Editor(it));m.mode="Scientific/CAS"}}
        }
        if(selected=="impute"&&onDataApplied!=null) {
            val values=m.result?.optJSONObject("imputation")?.optJSONArray("data")
            val appliedMessage=tr("Missing values applied to current data")
            val valid=input=="current"&&imputationData==data&&imputationExpression==command.getOrNull()&&m.resultSource==imputationExpression&&values!=null&&!m.busy&&!m.regressionBusy
            OutlinedButton(enabled=valid,modifier=Modifier.testTag("statistics-imputation-apply"),onClick={
                runCatching {statisticsImputationCSV(data,values!!,columnLimit)}.onSuccess {updated->imputationData=null;onDataApplied(updated);message=appliedMessage}.onFailure {message=it.message.orEmpty()}
            }){Text(tr("Apply to current data"))}
            Text(tr("Apply replaces only missing cells in the current data; observed values and headers are retained. Reanalyze after editing data."),fontSize=11.sp,color=LocalInstrument.current.muted)
        }
        if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
        statisticsReportFor(m.result,m.resultSource.ifBlank {m.editor.source},setOf(selected))?.let {report->StatisticsResultReport(m,report){plan->
            selected=plan.target;source=plan.expression;input="expression";workflowSource=plan.expression;workflowLabels=JSONObject(plan.termLabels).toString();message=""
            survivalReport=null;pending=false;m.calculationAction="statistics-$section";m.edit(Editor(plan.expression));m.calculate(statisticsTermLabels=plan.termLabels)
        }}
        if(selected=="survivalanalysis") {
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Checkbox(band,{band=it},modifier=Modifier.testTag("statistics-survival-band"));Text(if(ko)"95% 신뢰구간 밴드" else "95% CI band",fontSize=12.sp)}
            survivalReport?.let {report->SurvivalReport(report,reportPlan,band,onClear={m.clearStatisticsResult(survivalCopyResult)},clearEnabled=!m.busy&&!m.regressionBusy,onCopy=survivalCopyResult?.let {snapshot->{
                clipboard.setText(AnnotatedString(statisticsResultCopyText(m,snapshot,language)))
            }})}
        }
    }
    }
}

@Composable private fun StatisticsFormFields(definition:JSONObject,settings:JSONObject,columns:List<String>,rows:List<List<String>>,onChange:(String,String)->Unit) {
    val ko=isKorean();val fields=definition.getJSONArray("controls")
    fun option(key:String):String {val field=(0 until fields.length()).map {fields.getJSONObject(it)}.first {it.getString("key")==key};return settings.optString(key,field.get("default").toString())}
    val visibleFields=(0 until fields.length()).map {fields.getJSONObject(it)}.filter {field->
        val conditions=field.optJSONObject("when")
        conditions==null||conditions.keys().asSequence().all {name->val values=conditions.getJSONArray(name);(0 until values.length()).any {values.getString(it)==option(name)}}
    }
    var index=0
    while(index<visibleFields.size) {
        val field=visibleFields[index++];val key=field.getString("key")
        if(field.getString("type")=="number") {
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
            "number","text"->Field(value,label,Modifier.fillMaxWidth().testTag("statistics-form-$key")){onChange(key,it)}
            "choice"->{
                val rawChoices=field.getJSONArray("choices")
                val choices=List(rawChoices.length()){rawChoices.getJSONObject(it)}.filter {choice->val conditions=choice.optJSONObject("when");conditions==null||conditions.keys().asSequence().all {name->val values=conditions.getJSONArray(name);(0 until values.length()).any {values.getString(it)==option(name)}}}
                val ids=choices.map {it.getString("id")};val names=choices.map {it.getString(if(ko)"ko" else "label")}
                StatisticsSelectionTitle(label,translate=false)
                Choices(names,names.getOrElse(ids.indexOf(value)){""},{name->onChange(key,ids[names.indexOf(name)])},translate=false)
            }
            "group"->{
                val at=option("group").toIntOrNull() ?: 0
                val names=rows.map {it.getOrElse(at){""}.trim()}.filter(String::isNotBlank).distinct()
                val selected=if(value in names)value else names.getOrNull(if(key=="secondGroup"&&names.size>1)1 else 0).orEmpty()
                StatisticsSelectionTitle(label,translate=false);Choices(names,selected,{onChange(key,it)},translate=false)
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
                    "poissonreg","nbreg","glm","multinomial","ordinal","crossvalidate","linearmodel","discriminantanalysis","quantreg","zeroinflated","tobit"->listOf("response")
                    "manova"->listOf("group")
                    "mediation","moderation"->listOf("x","middle","response")
                    "ancova"->listOf("group","response")
                    else->listOf("subject","response")
                }
                val offsetRoles=if(id in listOf("poissonreg","nbreg","glmm","glm")&&option("adjustment")!="none"&&(id!="glmm"||option("family")!="binomial"))listOf("offset") else emptyList()
                val excluded=if(id in listOf("cfa","sem")&&key=="columns"&&option("groupMode")=="multi")listOf("group") else if(key in listOf("predictors","categorical","covariates","responses"))roles+offsetRoles else emptyList()
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
